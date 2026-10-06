package com.nayeem.habittracker.resource;

import com.nayeem.habittracker.file.StoredFile;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ResourceResponse(
        Long id,
        ResourceType type,
        String title,
        String body,
        String url,
        Long goalId,
        @Schema(description = "The uploaded file; only for a FILE resource")
        FileInfo file,
        boolean pinned,
        Instant createdAt) {

    /** What the client shows about an uploaded file; the bytes come from the download endpoint. */
    public record FileInfo(
            @Schema(example = "Week 1 plan.pdf") String name,
            @Schema(description = "Detected from the file's bytes", example = "application/pdf") String contentType,
            @Schema(example = "48213") long sizeBytes) {

        static FileInfo from(StoredFile file) {
            return file == null ? null : new FileInfo(file.getOriginalName(), file.getContentType(), file.getSizeBytes());
        }
    }

    static ResourceResponse from(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getType(), resource.getTitle(), resource.getBody(),
                resource.getUrl(), resource.getGoal() == null ? null : resource.getGoal().getId(),
                FileInfo.from(resource.getFile()), resource.isPinned(), resource.getCreatedAt());
    }
}
