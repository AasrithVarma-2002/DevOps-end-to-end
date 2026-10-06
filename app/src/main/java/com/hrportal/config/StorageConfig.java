package com.hrportal.config;

import com.hrportal.service.DocumentStorage;
import com.hrportal.storage.LocalDocumentStorage;
import com.hrportal.storage.S3DocumentStorage;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** app.storage.type: "s3" in AWS (aws profile), "local" everywhere else. */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    DocumentStorage s3DocumentStorage(@Value("${app.storage.bucket}") String bucket,
                                      @Value("${app.storage.region}") String region) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.storage.bucket (APP_DOCUMENTS_BUCKET) must be set when app.storage.type=s3");
        }
        // Region is explicit: pods can't read it from the node's instance metadata (hop limit 1)
        Region r = Region.of(region);
        return new S3DocumentStorage(S3Client.builder().region(r).build(), S3Presigner.builder().region(r).build(), bucket);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    DocumentStorage localDocumentStorage(@Value("${app.storage.local-dir}") String dir) {
        return new LocalDocumentStorage(Path.of(dir));
    }
}
