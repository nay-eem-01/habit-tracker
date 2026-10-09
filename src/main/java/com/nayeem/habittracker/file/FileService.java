package com.nayeem.habittracker.file;

import com.nayeem.habittracker.common.exception.ApplicationException;
import com.nayeem.habittracker.common.exception.ErrorCode;
import com.nayeem.habittracker.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Stores, serves and deletes uploads for the other features (PLAN.md §13.3). Checks come before any byte is written:
 * size, type from the bytes, the user's quota. The bytes are written before the row, and removed
 * again if the transaction rolls back — a failed upload leaves neither a row nor a file.
 * Logs ids, sizes and types only — never the file name or content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    static final int MAX_NAME = 255;

    private final StoredFileRepository storedFileRepository;
    private final FileStorage fileStorage;
    private final FileProperties properties;
    private final UserService userService;

    /** Must run inside the caller's transaction, so the row and the feature's row commit together. */
    @Transactional
    public StoredFile store(Long userId, MultipartFile upload) {
        if (!properties.isEnabled()) {
            throw new ApplicationException(ErrorCode.FILE_UPLOADS_DISABLED);
        }
        if (upload.isEmpty()) {
            throw new ApplicationException(ErrorCode.FILE_EMPTY);
        }
        long size = upload.getSize();
        if (size > properties.getMaxFileSize().toBytes()) {
            throw new ApplicationException(ErrorCode.FILE_TOO_LARGE,
                    "The file is larger than " + properties.getMaxFileSize().toMegabytes() + " MB");
        }
        String name = cleanName(upload.getOriginalFilename());
        FileType type = FileType.detect(head(upload), name);
        if (storedFileRepository.totalSizeByUserId(userId) + size > properties.getUserQuota().toBytes()) {
            throw new ApplicationException(ErrorCode.FILE_QUOTA_EXCEEDED);
        }

        String key = UUID.randomUUID().toString();
        String sha256 = write(key, upload);
        deleteOnRollback(key);

        StoredFile file = new StoredFile();
        file.setUser(userService.getById(userId));
        file.setStorageKey(key);
        file.setOriginalName(name);
        file.setContentType(type.getContentType());
        file.setSizeBytes(size);
        file.setSha256(sha256);
        file = storedFileRepository.save(file);
        log.info("File {} stored for user {} ({} bytes, {})", file.getId(), userId, size, type);
        return file;
    }

    /**
     * Opens the bytes for a download. The caller has already checked the file is the user's.
     *
     * @throws ApplicationException {@code FILE_NOT_FOUND} when the bytes are gone from storage
     */
    public FileDownload open(StoredFile file) {
        try {
            return new FileDownload(file.getOriginalName(), file.getContentType(), file.getSizeBytes(),
                    fileStorage.open(file.getStorageKey()));
        } catch (NoSuchFileException e) {
            log.error("File {} has a row but no bytes (key {})", file.getId(), file.getStorageKey());
            throw new ApplicationException(ErrorCode.FILE_NOT_FOUND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes the row now and the bytes once the transaction commits — a rolled-back delete keeps
     * both. The caller removes whatever points at the file first.
     */
    @Transactional
    public void delete(StoredFile file) {
        storedFileRepository.delete(file);
        String key = file.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileStorage.delete(key);
                } catch (IOException | RuntimeException e) {
                    log.error("Orphaned file {} after delete: {}", key, e.getClass().getSimpleName());
                }
            }
        });
        log.info("File {} deleted", file.getId());
    }

    /**
     * For deleting an account: the user's bytes go once the transaction commits (the rows go with the
     * account, by cascade). A rolled-back delete keeps them.
     */
    @Transactional(readOnly = true)
    public void deleteAllOfUserAfterCommit(Long userId) {
        List<String> keys = storedFileRepository.findStorageKeysByUserId(userId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(FileService.this::deleteQuietly);
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            fileStorage.delete(key);
        } catch (IOException | RuntimeException e) {
            log.error("Orphaned file {} after account delete: {}", key, e.getClass().getSimpleName());
        }
    }

    /**
     * The last path segment, without control characters, at most {@value #MAX_NAME} characters
     * (keeping the extension); {@code "file"} when nothing is left.
     */
    static String cleanName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "file";
        }
        if (name.length() > MAX_NAME) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME - ext.length()) + ext;
        }
        return name;
    }

    private static byte[] head(MultipartFile upload) {
        try (InputStream in = upload.getInputStream()) {
            return in.readNBytes(FileType.HEAD_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Streams the upload into storage; returns the hex SHA-256 of what was written. */
    private String write(String key, MultipartFile upload) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(upload.getInputStream(), digest)) {
            fileStorage.put(key, in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(key);
                    } catch (IOException | RuntimeException e) {
                        log.error("Orphaned file {} after rollback: {}", key, e.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every JDK has SHA-256
        }
    }
}
