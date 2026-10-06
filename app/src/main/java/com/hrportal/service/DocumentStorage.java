package com.hrportal.service;

/**
 * Where generated documents (payslip PDFs) are kept. In AWS this is a private S3 bucket that
 * the pods reach with their IRSA role; locally and in tests it is a folder on disk.
 */
public interface DocumentStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);
}
