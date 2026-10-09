package vhuwng.orderhub.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vhuwng.orderhub.middleware.exception.InvoiceStorageException;
import vhuwng.orderhub.properties.SeaweedFsProperties;

@ExtendWith(MockitoExtension.class)
class SeaweedFsInvoiceStorageTest {
    @Mock
    private S3Client s3Client;

    private SeaweedFsInvoiceStorage storage;

    @BeforeEach
    void setUp() {
        storage = new SeaweedFsInvoiceStorage(s3Client, new SeaweedFsProperties(
                "http://localhost:8333",
                "us-east-1",
                "orderhub",
                "orderhub-secret",
                "orderhub",
                "orderhub/invoices"
        ));
    }

    @Test
    void storePutsObjectWhenBucketExists() throws IOException {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.store("orderhub/invoices/orders/1/invoices/a.pdf", new ByteArrayInputStream(new byte[] {1}), 1, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("orderhub", request.getValue().bucket());
        assertEquals("orderhub/invoices/orders/1/invoices/a.pdf", request.getValue().key());
        assertEquals("application/pdf", request.getValue().contentType());
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void storeCreatesBucketWhenHeadReportsItMissing() throws IOException {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("missing").statusCode(404).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.store("key", new ByteArrayInputStream(new byte[] {1}), 1, "image/png");

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void storeDeletesPartialObjectWhenPutFails() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("unavailable").statusCode(503).build());

        assertThrows(InvoiceStorageException.class, () ->
                storage.store("key", new ByteArrayInputStream(new byte[] {1}), 1, "application/pdf"));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteIgnoresMissingObjectAndWrapsOtherStorageErrors() {
        storage.delete("missing");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("gone").statusCode(404).build());
        storage.delete("gone");

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("down").statusCode(500).build());
        assertThrows(InvoiceStorageException.class, () -> storage.delete("down"));
    }
}
