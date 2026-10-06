package com.hrportal.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hrportal.storage.S3DocumentStorage;
import org.junit.jupiter.api.Test;

/** The S3 storage must start without AWS access (credentials are only resolved on first use). */
class StorageConfigTest {

    @Test
    void s3StorageStartsWithoutContactingAws() {
        assertThat(new StorageConfig().s3DocumentStorage("hr-portal-documents-123456789012", "ap-south-1"))
                .isInstanceOf(S3DocumentStorage.class);
    }

    @Test
    void s3StorageRefusesToStartWithoutABucket() {
        assertThatThrownBy(() -> new StorageConfig().s3DocumentStorage("", "ap-south-1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_DOCUMENTS_BUCKET");
    }
}
