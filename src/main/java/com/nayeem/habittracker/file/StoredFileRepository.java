package com.nayeem.habittracker.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Package-private — other features go through {@link FileService}. */
interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** Bytes the user already stores, for the quota. */
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f where f.user.id = :userId")
    long totalSizeByUserId(Long userId);

    @Query("select f.storageKey from StoredFile f where f.user.id = :userId")
    List<String> findStorageKeysByUserId(Long userId);
}
