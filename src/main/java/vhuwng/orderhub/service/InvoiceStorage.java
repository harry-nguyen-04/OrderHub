package vhuwng.orderhub.service;

import java.io.IOException;
import java.io.InputStream;

public interface InvoiceStorage {
    void store(String storageKey, InputStream content, long sizeBytes, String mimeType) throws IOException;

    void delete(String storageKey);
}
