package com.hrportal.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Presigned links are made locally (a signature, no call to AWS), so this runs offline. */
class S3DocumentStorageTest {

    @Test
    void downloadLinkIsAShortLivedSignedHttpsUrlWithTheFilesNameAndType() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDEXAMPLE", "secret"));
        var presigner = S3Presigner.builder().region(Region.AP_SOUTH_1).credentialsProvider(credentials).build();
        var s3 = S3Client.builder().region(Region.AP_SOUTH_1).credentialsProvider(credentials).build();
        var storage = new S3DocumentStorage(s3, presigner, "hr-portal-documents-123456789012");

        var link = storage.downloadLink("documents/E-1/abc.pdf", "my pan.pdf", "application/pdf").orElseThrow();
        String url = URLDecoder.decode(link.toString(), StandardCharsets.UTF_8);

        assertThat(link.getScheme()).isEqualTo("https");
        assertThat(link.getHost()).startsWith("hr-portal-documents-123456789012.s3.");
        assertThat(link.getPath()).isEqualTo("/documents/E-1/abc.pdf");
        assertThat(url).contains("X-Amz-Expires=300", "X-Amz-Signature=",
                "response-content-type=application/pdf", "response-content-disposition=inline", "my pan.pdf");
    }
}
