package com.nayeem.habittracker.habit;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalTime;

/** Body for creating a habit and for replacing one (PUT) — both need every field. */
@Getter
@Setter
@ToString
public class HabitRequest {

    @NotBlank
    @Size(max = 120)
    @Schema(example = "Read 20 pages")
    private String name;

    @Size(max = 50)
    @Schema(example = "Learning")
    private String category;

    @NotNull
    @Schema(example = "SPECIFIC_DAYS")
    private FrequencyType frequencyType;

    private FrequencyConfig frequencyConfig;

    @Min(1)
    @Max(100)
    @Schema(description = "Completions a day needs to count as done. Defaults to 1.", example = "1")
    private Integer targetCount;

    @JsonFormat(pattern = "HH:mm")
    @Schema(type = "string", example = "07:30",
            description = "Reminder time of day (HH:mm) in your timezone. Omit for no reminder.")
    private LocalTime reminderTime;
}
