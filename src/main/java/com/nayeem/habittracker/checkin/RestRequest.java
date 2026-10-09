package com.nayeem.habittracker.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@ToString
public class RestRequest {

    @Schema(description = "The day, in your timezone. Defaults to today; at most 7 days back.", example = "2026-10-10")
    private LocalDate date;
}
