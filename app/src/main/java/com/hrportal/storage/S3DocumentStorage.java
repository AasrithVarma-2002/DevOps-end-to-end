package com.hrportal.storage;

import com.hrportal.service.DocumentStorage;
import com.hrportal.service.NotFoundException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Documents in a private S3 bucket. Credentials come from the default AWS chain: in EKS that is
 * the pod's IRSA role (AWS_ROLE_ARN + web identity token injected by EKS), so there are no keys
 * in the app or its configuration. The bucket encrypts every object (SSE-S3 by default).
 */
public class S3DocumentStorage implements DocumentStorage {

    /** How long a download link works. Long enough to click, short enough that a shared link goes dead. */
    static final Duration LINK_LIFETIME = Duration.ofMinutes(5);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = s3;
        this.presigner = presigner;
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

    /**
     * A presigned GET: the URL itself carries a signature made with the pod's IRSA credentials,
     * so the browser downloads straight from S3 (HTTPS) without any AWS access of its own.
     * S3 sends the file with the type and name given here.
     */
    @Override
    public Optional<URI> downloadLink(String key, String fileName, String contentType) {
        String disposition = Dispositions.inline(fileName);
        var request = presigner.presignGetObject(p -> p
                .signatureDuration(LINK_LIFETIME)
                .getObjectRequest(g -> g.bucket(bucket).key(key)
                        .responseContentType(contentType)
                        .responseContentDisposition(disposition)));
        return Optional.of(URI.create(request.url().toString()));
    }
}
