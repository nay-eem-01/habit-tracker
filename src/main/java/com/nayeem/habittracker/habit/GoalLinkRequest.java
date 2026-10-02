package com.nayeem.habittracker.habit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Body for linking a habit to a goal (PUT); sending it again changes the target. */
@Getter
@Setter
@ToString
public class GoalLinkRequest {

    @NotNull
    @Schema(example = "1")
    private Long goalId;

    @NotNull
    @Min(1)
    @Max(3650)
    @Schema(description = "Done days that make this habit 'built' for the goal.", example = "60")
    private Integer goalTargetDays;
}
