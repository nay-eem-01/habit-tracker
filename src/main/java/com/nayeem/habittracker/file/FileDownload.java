package com.nayeem.habittracker.file;

import java.io.InputStream;

/** An open stored file for a download; the caller (the response) closes {@code content}. */
public record FileDownload(String name, String contentType, long sizeBytes, InputStream content) {
}
