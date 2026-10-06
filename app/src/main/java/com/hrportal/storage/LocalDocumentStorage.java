package com.hrportal.storage;

import com.hrportal.service.DocumentStorage;
import com.hrportal.service.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Documents in a folder on disk: local runs (docker compose) and tests. Not for Kubernetes. */
public class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalDocumentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        Path file = resolve(key);
        if (!Files.exists(file)) {
            throw new NotFoundException("Document " + key + " not found");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    /** Keys are built by the app, but never let one point outside the folder. */
    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid document key: " + key);
        }
        return file;
    }
}
