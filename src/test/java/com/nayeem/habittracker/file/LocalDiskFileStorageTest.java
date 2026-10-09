package com.nayeem.habittracker.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskFileStorageTest {

    private static final String KEY = "0b7e3c1a-5d2f-4c8e-9a61-3f2b7d9e4c10";

    @TempDir
    Path dir;

    private LocalDiskFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        FileProperties properties = new FileProperties();
        properties.setEnabled(true);
        properties.setDir(dir.resolve("files"));
        storage = new LocalDiskFileStorage(properties);
        storage.init();
    }

    @Test
    void storesReadsAndDeletes() throws IOException {
        storage.put(KEY, new ByteArrayInputStream("hello".getBytes()));

        try (InputStream in = storage.open(KEY)) {
            assertThat(in.readAllBytes()).asString().isEqualTo("hello");
        }
        storage.delete(KEY);
        assertThat(dir.resolve("files")).isEmptyDirectory();
        storage.delete(KEY);   // already gone: no error
    }

    @Test
    void aFailedWriteLeavesNothingBehind() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };

        assertThatThrownBy(() -> storage.put(KEY, failing)).isInstanceOf(IOException.class);
        assertThat(dir.resolve("files")).isEmptyDirectory();
    }

    @Test
    void refusesKeysThatCouldLeaveTheDirectory() throws IOException {
        Files.writeString(dir.resolve("secret"), "x");

        for (String key : new String[]{"../secret", "a/b", "..", "", "a b"}) {
            assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
