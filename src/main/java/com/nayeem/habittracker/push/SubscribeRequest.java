package com.nayeem.habittracker.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** The browser's {@code PushSubscription.toJSON()}, as is. */
@Getter
@Setter
public class SubscribeRequest {

    @NotBlank
    @Size(max = 1000)
    private String endpoint;

    @Valid
    @NotNull
    private Keys keys;

    @Getter
    @Setter
    public static class Keys {
        @NotBlank
        @Size(max = 100)
        private String p256dh;

        @NotBlank
        @Size(max = 50)
        private String auth;
    }

    /** Hand-written: the endpoint is a capability URL and the keys are secrets. */
    @Override
    public String toString() {
        return "SubscribeRequest[endpoint=***, keys=***]";
    }
}
