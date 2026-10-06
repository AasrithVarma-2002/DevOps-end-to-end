package com.hrportal.service;

import java.net.URI;
import java.util.Optional;

/**
 * Where stored files are kept: payslip PDFs and employee documents. In AWS this is a private S3
 * bucket that the pods reach with their IRSA role; locally and in tests it is a folder on disk.
 */
public interface DocumentStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    /**
     * A short-lived link the browser can fetch the file from directly, so large files don't pass
     * through the app. Empty means the storage can't make one; the app then sends the bytes itself.
     */
    default Optional<URI> downloadLink(String key, String fileName, String contentType) {
        return Optional.empty();
    }
}
