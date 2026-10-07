package vhuwng.orderhub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import vhuwng.orderhub.middleware.exception.InvalidInvoiceFileException;
import vhuwng.orderhub.middleware.exception.InvoiceFileTooLargeException;
import vhuwng.orderhub.middleware.exception.UnsupportedInvoiceMediaException;
import vhuwng.orderhub.service.InvoiceFileValidator.ValidatedInvoiceFile;

class InvoiceFileValidatorTest {
    private final InvoiceFileValidator validator = new InvoiceFileValidator();

    @Test
    void acceptsPdfJpegAndPngWhenSignatureMatchesDeclaredType() {
        ValidatedInvoiceFile pdf = validator.validate(file("invoice.pdf", "application/pdf", pdfBytes()));
        ValidatedInvoiceFile jpeg = validator.validate(file("scan.jpg", "image/jpg", jpegBytes()));
        ValidatedInvoiceFile png = validator.validate(file("scan.png", "image/png; charset=binary", pngBytes()));

        assertEquals("application/pdf", pdf.mimeType());
        assertEquals("pdf", pdf.extension());
        assertEquals("image/jpeg", jpeg.mimeType());
        assertEquals("jpg", jpeg.extension());
        assertEquals("image/png", png.mimeType());
        assertEquals("png", png.extension());
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[0]);

        assertThrows(InvalidInvoiceFileException.class, () -> validator.validate(empty));
        assertThrows(InvalidInvoiceFileException.class, () -> validator.validate(null));
    }

    @Test
    void rejectsFileLargerThanTenMebibytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                new byte[(int) InvoiceFileValidator.MAX_BYTES + 1]
        ) {
            @Override
            public long getSize() {
                return InvoiceFileValidator.MAX_BYTES + 1;
            }
        };

        assertThrows(InvoiceFileTooLargeException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsUnknownSignatureAndMismatchedContentType() {
        MockMultipartFile text = file("notes.txt", "text/plain", "hello".getBytes());
        MockMultipartFile mismatched = file("invoice.pdf", "image/png", pdfBytes());

        assertThrows(UnsupportedInvoiceMediaException.class, () -> validator.validate(text));
        assertThrows(UnsupportedInvoiceMediaException.class, () -> validator.validate(mismatched));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] body) {
        return new MockMultipartFile("file", name, contentType, body);
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.7\n".getBytes();
    }

    private static byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }

    private static byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    }
}
