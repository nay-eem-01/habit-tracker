package com.nayeem.habittracker.file;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import lombok.Getter;
import org.apache.tika.Tika;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * The file types that may be uploaded (PLAN.md §13.2, Q13). The type is detected from the bytes
 * (magic numbers), never taken from the client, and the file name's extension must agree with it —
 * so an HTML page renamed to {@code .png} or {@code .txt} is refused. SVG and HTML are left out on
 * purpose: they can carry scripts.
 */
@Getter
public enum FileType {

    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg", "jpeg"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif"),
    PDF("application/pdf", "pdf"),
    TEXT("text/plain", "txt", "md");

    /** How much of the start of a file detection looks at. */
    static final int HEAD_BYTES = 64 * 1024;

    private static final Tika TIKA = new Tika();

    private final String contentType;
    private final Set<String> extensions;

    FileType(String contentType, String... extensions) {
        this.contentType = contentType;
        this.extensions = Set.of(extensions);
    }

    /**
     * @param head     the first bytes of the file (up to {@link #HEAD_BYTES})
     * @param fileName the name it was uploaded as, for the extension check
     * @throws ApplicationException {@code FILE_TYPE_NOT_ALLOWED} when the type isn't on the list or
     *                              the extension doesn't match it
     */
    static FileType detect(byte[] head, String fileName) {
        String detected = TIKA.detect(head);
        FileType type = Arrays.stream(values())
                .filter(t -> t.contentType.equals(detected))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(ErrorCode.FILE_TYPE_NOT_ALLOWED));
        if (!type.extensions.contains(extension(fileName))) {
            throw new ApplicationException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return type;
    }

    private static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
