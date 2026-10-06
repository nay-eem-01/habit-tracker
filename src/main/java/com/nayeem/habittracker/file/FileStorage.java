package com.nayeem.habittracker.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where uploaded bytes live, by key. Local disk for now; an S3-compatible store later (roadmap
 * R.3d) — features never see which one.
 */
public interface FileStorage {

    /** Stores the stream under {@code key}; a half-written file is never left behind. */
    void put(String key, InputStream content) throws IOException;

    /** The bytes stored under {@code key}; the caller closes the stream. */
    InputStream open(String key) throws IOException;

    /** Removes {@code key}; nothing happens when it isn't there. */
    void delete(String key) throws IOException;
}
