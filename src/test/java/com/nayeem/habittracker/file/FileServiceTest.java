package com.nayeem.habittracker.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileServiceTest {

    @Test
    void cleanNameKeepsOnlyASafeLastSegment() {
        assertThat(FileService.cleanName("plan.pdf")).isEqualTo("plan.pdf");
        assertThat(FileService.cleanName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileService.cleanName("C:\\Users\\me\\run log.txt")).isEqualTo("run log.txt");
        assertThat(FileService.cleanName("  a\u0000b\nc.png ")).isEqualTo("abc.png");
        assertThat(FileService.cleanName(null)).isEqualTo("file");
        assertThat(FileService.cleanName("dir/")).isEqualTo("file");
    }

    @Test
    void cleanNameShortensLongNamesButKeepsTheExtension() {
        String name = FileService.cleanName("x".repeat(400) + ".pdf");

        assertThat(name).hasSize(FileService.MAX_NAME).endsWith(".pdf");
    }
}
