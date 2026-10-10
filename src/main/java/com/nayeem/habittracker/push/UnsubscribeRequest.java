package com.nayeem.habittracker.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnsubscribeRequest {

    @NotBlank
    @Size(max = 1000)
    private String endpoint;

    @Override
    public String toString() {
        return "UnsubscribeRequest[endpoint=***]";
    }
}
