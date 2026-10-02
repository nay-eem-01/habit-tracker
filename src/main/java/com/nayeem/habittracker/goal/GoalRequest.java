package com.nayeem.habittracker.goal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/** Body for creating a goal and for replacing one (PUT) — both need every field. */
@Getter
@Setter
@ToString
public class GoalRequest {

    @NotBlank
    @Size(max = 120)
    @Schema(example = "Run a half marathon")
    private String title;

    @Size(max = 2000)
    @Schema(example = "Finish the city half marathon in under two hours")
    private String description;

    @Schema(type = "string", format = "date", example = "2026-12-31",
            description = "Optional deadline. Omit for none.")
    private LocalDate targetDate;
}
