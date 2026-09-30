package com.nayeem.habittracker.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/** Every field is optional: an empty body means "mark today done". */
@Getter
@Setter
@ToString
public class CheckInRequest {

    @Schema(description = "The day, in your timezone. Defaults to today; at most 7 days back.", example = "2026-09-30")
    private LocalDate date;

    @Min(0)
    @Max(1000)
    @Schema(description = "The day's total (not +1). Defaults to the habit's targetCount; 0 undoes.", example = "1")
    private Integer completedCount;

    @Size(max = 500)
    @Schema(description = "Omit to keep the current note; empty string clears it.")
    private String note;
}
