package com.nayeem.habittracker.file;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

/** {@code app.files.*} — limits from PLAN.md §13.2 (Q13). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.files")
public class FileProperties {

    /** Where local-disk storage keeps the bytes. Outside the repository. */
    private Path dir;

    /** Largest single upload. Keep {@code spring.servlet.multipart.max-file-size} in step. */
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    /** Total bytes one user may store. */
    private DataSize userQuota = DataSize.ofMegabytes(100);
}
