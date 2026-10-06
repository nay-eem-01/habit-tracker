package com.nayeem.habittracker.file;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.support.IntegrationTest;
import com.nayeem.habittracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not {@code @Transactional}: commit and rollback are what's under test. */
class FileServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private FileService fileService;
    @Autowired
    private StoredFileRepository storedFileRepository;
    @Autowired
    private FileStorage fileStorage;
    @Autowired
    private FileProperties properties;
    @Autowired
    private UserService userService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void storesTheBytesAndTheRow() throws Exception {
        Long userId = user("files.store@example.com");
        MockMultipartFile upload = new MockMultipartFile("file", "../Week 1.pdf", "text/html", FileTypeTest.PDF);

        StoredFile file = transactionTemplate.execute(status -> fileService.store(userId, upload));

        StoredFile saved = storedFileRepository.findById(file.getId()).orElseThrow();
        assertThat(saved.getOriginalName()).isEqualTo("Week 1.pdf");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");   // detected, not the client's
        assertThat(saved.getSizeBytes()).isEqualTo(FileTypeTest.PDF.length);
        assertThat(saved.getSha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(FileTypeTest.PDF)));
        assertThat(saved.getStorageKey()).hasSize(36);
        try (InputStream in = fileStorage.open(saved.getStorageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(FileTypeTest.PDF);
        }
    }

    @Test
    void aRolledBackTransactionTakesTheBytesWithIt() {
        Long userId = user("files.rollback@example.com");
        String[] key = new String[1];
        long rowsBefore = storedFileRepository.count();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            key[0] = fileService.store(userId, png("a.png")).getStorageKey();
            throw new IllegalStateException("the feature's own insert failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(storedFileRepository.count()).isEqualTo(rowsBefore);
        assertThatThrownBy(() -> fileStorage.open(key[0])).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void refusesEmptyTooLargeAndDisallowedFilesBeforeWritingAnything() throws IOException {
        Long userId = user("files.refuse@example.com");
        long filesBefore = filesOnDisk();

        assertError(() -> fileService.store(userId, new MockMultipartFile("file", "e.txt", null, new byte[0])),
                ErrorCode.FILE_EMPTY);
        assertError(() -> fileService.store(userId, new MockMultipartFile("file", "x.html", null,
                FileTypeTest.ascii("<!DOCTYPE html><html></html>"))), ErrorCode.FILE_TYPE_NOT_ALLOWED);

        DataSize max = properties.getMaxFileSize();
        properties.setMaxFileSize(DataSize.ofBytes(FileTypeTest.PNG.length - 1));
        try {
            assertError(() -> fileService.store(userId, png("big.png")), ErrorCode.FILE_TOO_LARGE);
        } finally {
            properties.setMaxFileSize(max);
        }
        assertThat(filesOnDisk()).isEqualTo(filesBefore);
    }

    @Test
    void theQuotaCountsWhatTheUserAlreadyStores() {
        Long userId = user("files.quota@example.com");
        Long otherId = user("files.quota.other@example.com");
        DataSize quota = properties.getUserQuota();
        properties.setUserQuota(DataSize.ofBytes(FileTypeTest.PNG.length * 2L));
        try {
            transactionTemplate.execute(status -> fileService.store(userId, png("1.png")));
            transactionTemplate.execute(status -> fileService.store(userId, png("2.png")));

            assertError(() -> transactionTemplate.execute(status -> fileService.store(userId, png("3.png"))),
                    ErrorCode.FILE_QUOTA_EXCEEDED);
            // another user's files don't count against this one
            transactionTemplate.execute(status -> fileService.store(otherId, png("1.png")));
        } finally {
            properties.setUserQuota(quota);
        }
    }

    private Long user(String email) {
        return userService.createLocalUser(email, "not-a-real-hash", "Test User", null).getId();
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", FileTypeTest.PNG);
    }

    private long filesOnDisk() throws IOException {
        try (var files = Files.list(properties.getDir())) {
            return files.count();
        }
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorCode())
                .isEqualTo(code);
    }
}
