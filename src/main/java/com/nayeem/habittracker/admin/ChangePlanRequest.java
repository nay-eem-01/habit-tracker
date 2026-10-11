package com.nayeem.habittracker.admin;

import com.nayeem.habittracker.user.Plan;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePlanRequest {

    @NotNull
    private Plan plan;
}
