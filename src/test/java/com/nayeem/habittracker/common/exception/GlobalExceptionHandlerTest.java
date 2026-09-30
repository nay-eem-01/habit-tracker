package com.nayeem.habittracker.common.exception;

import com.nayeem.habittracker.common.logging.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Error shape, with no Spring context: a throw-away controller behind the real advice and filter. */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void applicationExceptionUsesItsCodeAndStatus() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Habit not found"))
                .andExpect(jsonPath("$.payload").doesNotExist());
    }

    @Test
    void validationFailureListsTheFields() throws Exception {
        mockMvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void unreadableBodyIsMalformedRequest() throws Exception {
        mockMvc.perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    }

    @Test
    void unexpectedErrorHidesTheExceptionText() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong"))
                .andExpect(content().string(not(containsString("secret table"))));
    }

    @Test
    void correlationIdIsGeneratedAndReturnedInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void safeClientCorrelationIdIsKept() throws Exception {
        mockMvc.perform(get("/test/boom").header(CorrelationIdFilter.HEADER, "client-id-1234"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "client-id-1234"))
                .andExpect(jsonPath("$.correlationId").value("client-id-1234"));
    }

    @Test
    void unsafeClientCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get("/test/boom").header(CorrelationIdFilter.HEADER, "bad\nlog line"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new ApplicationException(ErrorCode.NOT_FOUND, "Habit not found");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret table users_private is broken");
        }

        @PostMapping("/test/validated")
        void validated(@Valid @RequestBody NameRequest request) {
        }
    }

    record NameRequest(@NotBlank String name) {
    }
}
