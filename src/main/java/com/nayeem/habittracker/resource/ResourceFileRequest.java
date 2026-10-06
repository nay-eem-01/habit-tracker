package com.nayeem.habittracker.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Form fields sent with an uploaded file (multipart), next to the {@code file} part. */
@Getter
@Setter
@ToString(exclude = "body")   // private text: keep it out of logs
public class ResourceFileRequest {

    @NotBlank
    @Size(max = 200)
    @Schema(example = "Week 1 plan")
    private String title;

    @Size(max = ResourceService.MAX_BODY)
    @Schema(description = "An optional comment about the file (Markdown).")
    private String body;

    @Schema(description = "The goal it belongs to. Omit for a stand-alone resource.", example = "1")
    private Long goalId;

    @Schema(description = "Shown first. Defaults to false.")
    private Boolean pinned;
}
