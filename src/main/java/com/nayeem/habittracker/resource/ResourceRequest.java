package com.nayeem.habittracker.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Body for creating a resource and for replacing one (PUT) — both need every field. */
@Getter
@Setter
@ToString(exclude = "body")   // a note is private text: keep it out of logs
public class ResourceRequest {

    @NotNull
    @Schema(example = "LINK")
    private ResourceType type;

    @NotBlank
    @Size(max = 200)
    @Schema(example = "Couch to 5K plan")
    private String title;

    @Size(max = ResourceService.MAX_BODY)
    @Schema(description = "Markdown. Required for a NOTE; an optional comment for a LINK.")
    private String body;

    @Size(max = 2048)
    @Schema(description = "http or https address. Required for a LINK, not allowed on a NOTE.",
            example = "https://example.com/plan")
    private String url;

    @Schema(description = "The goal it belongs to. Omit for a stand-alone resource.", example = "1")
    private Long goalId;

    @Schema(description = "Shown first. Defaults to false.")
    private Boolean pinned;
}
