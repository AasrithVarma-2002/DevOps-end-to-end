package com.hrportal.storage;

import com.hrportal.service.DocumentStorage;
import com.hrportal.service.NotFoundException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Documents in a private S3 bucket. Credentials come from the default AWS chain: in EKS that is
 * the pod's IRSA role (AWS_ROLE_ARN + web identity token injected by EKS), so there are no keys
 * in the app or its configuration. The bucket encrypts every object (SSE-S3 by default).
 */
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new NotFoundException("Document " + key + " not found");
        }
    }
}
