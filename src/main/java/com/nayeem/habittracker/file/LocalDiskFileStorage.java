package com.nayeem.habittracker.file;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Keeps each file as {@code <app.files.dir>/<key>}. Written to a temp file in the same directory
 * first and moved into place, so a failed upload never leaves a partial file under a real key.
 */
@Component
@RequiredArgsConstructor
class LocalDiskFileStorage implements FileStorage {

    /** Keys are UUIDs; anything else (a slash, "..") is refused before it reaches the disk. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final FileProperties properties;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        if (properties.getDir() == null) {
            throw new IllegalStateException("app.files.dir must be set");
        }
        root = properties.getDir().toAbsolutePath().normalize();
        if (properties.isEnabled()) {   // switched off, nothing is written: don't need a writable disk to start
            Files.createDirectories(root);
        }
    }

    @Override
    public void put(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Path temp = Files.createTempFile(root, ".upload-", ".tmp");
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return root.resolve(key);
    }
}
