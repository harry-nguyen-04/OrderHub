package vhuwng.orderhub.service.impl;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vhuwng.orderhub.middleware.exception.InvoiceStorageException;
import vhuwng.orderhub.properties.SeaweedFsProperties;
import vhuwng.orderhub.service.InvoiceStorage;

@Service
public class SeaweedFsInvoiceStorage implements InvoiceStorage {
    private static final Logger log = LoggerFactory.getLogger(SeaweedFsInvoiceStorage.class);

    private final S3Client s3Client;
    private final SeaweedFsProperties properties;
    private volatile boolean bucketReady;

    public SeaweedFsInvoiceStorage(S3Client s3Client, SeaweedFsProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void store(String storageKey, InputStream content, long sizeBytes, String mimeType) throws IOException {
        byte[] contentBytes = content.readAllBytes();
        if (contentBytes.length != sizeBytes) {
            throw new IOException("Invoice content length does not match the declared file size");
        }

        try {
            ensureBucket();
        } catch (RuntimeException ex) {
            throw unavailable(ex);
        }

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .contentType(mimeType)
                    .contentLength(sizeBytes)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(contentBytes));
        } catch (RuntimeException ex) {
            deleteQuietly(storageKey);
            throw unavailable(ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .build());
        } catch (NoSuchKeyException ex) {
            log.debug("Invoice object {} is already absent", storageKey);
        } catch (RuntimeException ex) {
            throw unavailable(ex);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
            } catch (NoSuchBucketException ex) {
                createBucket();
            } catch (S3Exception ex) {
                if (ex.statusCode() != 404) {
                    throw ex;
                }
                createBucket();
            }
            bucketReady = true;
        }
    }

    private void createBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ex) {
            log.debug("Invoice bucket {} already exists", properties.bucket());
        } catch (S3Exception ex) {
            if (ex.statusCode() != 409) {
                throw ex;
            }
        }
    }

    private void deleteQuietly(String storageKey) {
        try {
            delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to delete incomplete invoice object {}", storageKey, ex);
        }
    }

    private static InvoiceStorageException unavailable(RuntimeException ex) {
        if (ex instanceof InvoiceStorageException storageException) {
            return storageException;
        }
        return new InvoiceStorageException("Invoice storage is unavailable", ex);
    }
}
