package com.nayeem.habittracker.file;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypeTest {

    static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R');
    static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00);
    static final byte[] GIF = ascii("GIF89a\u0001\u0000\u0001\u0000");
    static final byte[] WEBP = ascii("RIFF\u0024\u0000\u0000\u0000WEBPVP8 ");
    static final byte[] PDF = ascii("%PDF-1.7\n1 0 obj\n");
    static final byte[] MARKDOWN = "# Week 1\n- run 20 min\n- stretch — ব্যায়াম\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsEachAllowedTypeWithAMatchingExtension() {
        assertThat(FileType.detect(PNG, "chart.png")).isEqualTo(FileType.PNG);
        assertThat(FileType.detect(JPEG, "photo.jpg")).isEqualTo(FileType.JPEG);
        assertThat(FileType.detect(JPEG, "photo.JPEG")).isEqualTo(FileType.JPEG);
        assertThat(FileType.detect(GIF, "loop.gif")).isEqualTo(FileType.GIF);
        assertThat(FileType.detect(WEBP, "pic.webp")).isEqualTo(FileType.WEBP);
        assertThat(FileType.detect(PDF, "plan.pdf")).isEqualTo(FileType.PDF);
        assertThat(FileType.detect(MARKDOWN, "notes.md")).isEqualTo(FileType.TEXT);
        assertThat(FileType.detect(MARKDOWN, "notes.txt")).isEqualTo(FileType.TEXT);
    }

    @Test
    void theExtensionMustAgreeWithTheBytes() {
        assertNotAllowed(PNG, "chart.pdf");
        assertNotAllowed(PDF, "plan.txt");
        assertNotAllowed(PNG, "chart");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!DOCTYPE html><html><body>hi</body></html>",
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
            "<?xml version=\"1.0\"?><a/>"})
    void refusesMarkupEvenWhenNamedLikeAnAllowedType(String content) {
        assertNotAllowed(ascii(content), "innocent.txt");
        assertNotAllowed(ascii(content), "innocent.png");
    }

    @Test
    void refusesExecutablesAndArchives() {
        assertNotAllowed(bytes('M', 'Z', 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, 0xFF, 0xFF), "setup.txt");
        assertNotAllowed(bytes(0x7F, 'E', 'L', 'F', 2, 1, 1, 0), "run.txt");
        assertNotAllowed(bytes('P', 'K', 3, 4, 20, 0, 0, 0, 8, 0), "archive.pdf");
    }

    private static void assertNotAllowed(byte[] head, String name) {
        assertThatThrownBy(() -> FileType.detect(head, name))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
