package vhuwng.orderhub.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import vhuwng.orderhub.middleware.exception.InvalidInvoiceFileException;
import vhuwng.orderhub.middleware.exception.InvoiceFileTooLargeException;
import vhuwng.orderhub.middleware.exception.UnsupportedInvoiceMediaException;

@Component
public class InvoiceFileValidator {
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    public ValidatedInvoiceFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new InvalidInvoiceFileException("Invoice file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvoiceFileTooLargeException("Invoice file exceeds the 10 MiB limit");
        }

        byte[] header = readHeader(file);
        InvoiceType detected = detect(header);
        if (detected == null) {
            throw new UnsupportedInvoiceMediaException("Invoice file type is not supported");
        }

        String declared = mediaType(file.getContentType());
        if (!detected.matches(declared)) {
            throw new UnsupportedInvoiceMediaException("Invoice file content type does not match its contents");
        }
        return new ValidatedInvoiceFile(detected.mimeType, detected.extension, file.getSize());
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(8);
        } catch (IOException ex) {
            throw new InvalidInvoiceFileException("Unable to read the invoice file");
        }
    }

    private static InvoiceType detect(byte[] header) {
        if (startsWith(header, PDF)) {
            return InvoiceType.PDF;
        }
        if (startsWith(header, JPEG)) {
            return InvoiceType.JPEG;
        }
        if (startsWith(header, PNG)) {
            return InvoiceType.PNG;
        }
        return null;
    }

    private static boolean startsWith(byte[] header, byte[] signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ValidatedInvoiceFile(String mimeType, String extension, long sizeBytes) {
    }

    private enum InvoiceType {
        PDF("application/pdf", "pdf"),
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        private final String mimeType;
        private final String extension;

        InvoiceType(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        private boolean matches(String declared) {
            if (this == JPEG) {
                return "image/jpeg".equals(declared) || "image/jpg".equals(declared);
            }
            return mimeType.equals(declared);
        }
    }
}
