package com.nayeem.habittracker.resource;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.file.FileProperties;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/resources/files} (roadmap R.3b). Rolled back per test, which also removes the bytes. */
@Transactional
class ResourceFileUploadIntegrationTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FileProperties fileProperties;

    @Test
    void uploadsAFileAsAResource() throws Exception {
        String token = bearerFor("upload.ok@example.com");
        Integer goal = createGoal(token);

        String body = mockMvc.perform(upload(token, pdf("../Week 1 plan.pdf"))
                        .param("title", " Week 1 ").param("body", "the plan I follow")
                        .param("goalId", goal.toString()).param("pinned", "true"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/resources/")))
                .andExpect(jsonPath("$.payload.type").value("FILE"))
                .andExpect(jsonPath("$.payload.title").value("Week 1"))
                .andExpect(jsonPath("$.payload.body").value("the plan I follow"))
                .andExpect(jsonPath("$.payload.url").doesNotExist())
                .andExpect(jsonPath("$.payload.goalId").value(goal))
                .andExpect(jsonPath("$.payload.pinned").value(true))
                .andExpect(jsonPath("$.payload.file.name").value("Week 1 plan.pdf"))
                .andExpect(jsonPath("$.payload.file.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.payload.file.sizeBytes").value(PDF.length))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.payload.id");

        mockMvc.perform(get("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.file.name").value("Week 1 plan.pdf"));
        mockMvc.perform(get("/api/resources").param("type", "FILE").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.totalElements").value(1))
                .andExpect(jsonPath("$.payload.content[0].file.contentType").value("application/pdf"));
        mockMvc.perform(get("/api/goals/{id}/resources", goal).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.payload.content[0].type").value("FILE"));
    }

    @Test
    void notesAndLinksHaveNoFile() throws Exception {
        String token = bearerFor("upload.note@example.com");

        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"NOTE\",\"title\":\"n\",\"body\":\"b\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payload.file").doesNotExist());
    }

    @Test
    void refusedUploadsCreateNothingAndWriteNothing() throws Exception {
        String alice = bearerFor("upload.alice@example.com");
        String bob = bearerFor("upload.bob@example.com");
        Integer aliceGoal = createGoal(alice);
        long filesBefore = filesOnDisk();

        mockMvc.perform(upload(bob, new MockMultipartFile("file", "page.txt", "text/plain",
                        "<!DOCTYPE html><html><script>x</script></html>".getBytes())).param("title", "x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("FILE_TYPE_NOT_ALLOWED"));
        mockMvc.perform(upload(bob, new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                        .param("title", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FILE_EMPTY"));
        mockMvc.perform(upload(bob, pdf("plan.pdf")).param("title", "x").param("goalId", aliceGoal.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GOAL_NOT_FOUND"));

        mockMvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(jsonPath("$.payload.totalElements").value(0));
        assertThat(filesOnDisk()).isEqualTo(filesBefore);
    }

    @Test
    void theFileAndTitleAreRequired() throws Exception {
        String token = bearerFor("upload.missing@example.com");

        mockMvc.perform(multipart("/api/resources/files").param("title", "no file")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
        mockMvc.perform(upload(token, pdf("plan.pdf")).param("title", " ").param("body", "b".repeat(20_001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.body").exists());
    }

    @Test
    void anonymousUploadsAreRefused() throws Exception {
        mockMvc.perform(multipart("/api/resources/files").file(pdf("plan.pdf")).param("title", "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void filesAreUploadedOnlyThroughTheirOwnEndpoint() throws Exception {
        String token = bearerFor("upload.json@example.com");

        mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"FILE\",\"title\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_INVALID"));
    }

    @Test
    void replacingAFileResourceKeepsTheFile() throws Exception {
        String token = bearerFor("upload.put@example.com");
        Integer id = JsonPath.read(mockMvc.perform(upload(token, pdf("plan.pdf")).param("title", "Plan"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.payload.id");
        Integer noteId = JsonPath.read(mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"NOTE\",\"title\":\"n\",\"body\":\"b\"}"))
                .andReturn().getResponse().getContentAsString(), "$.payload.id");

        mockMvc.perform(replace(token, id, "{\"type\":\"FILE\",\"title\":\"Plan v2\",\"body\":\"updated\",\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.title").value("Plan v2"))
                .andExpect(jsonPath("$.payload.pinned").value(true))
                .andExpect(jsonPath("$.payload.file.name").value("plan.pdf"));
        // the type can't change in either direction, and a file has no url
        mockMvc.perform(replace(token, id, "{\"type\":\"NOTE\",\"title\":\"n\",\"body\":\"b\"}"))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_INVALID"));
        mockMvc.perform(replace(token, id, "{\"type\":\"FILE\",\"title\":\"x\",\"url\":\"https://example.com\"}"))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_INVALID"));
        mockMvc.perform(replace(token, noteId, "{\"type\":\"FILE\",\"title\":\"x\"}"))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_INVALID"));
    }

    private static MockMultipartHttpServletRequestBuilder upload(String token, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/resources/files").file(file);
        request.header(HttpHeaders.AUTHORIZATION, token);
        return request;
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF);
    }

    private RequestBuilder replace(String token, Integer id, String json) {
        return put("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private Integer createGoal(String token) throws Exception {
        String body = mockMvc.perform(post("/api/goals").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Run\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }

    private long filesOnDisk() throws IOException {
        try (var files = Files.list(fileProperties.getDir())) {
            return files.count();
        }
    }
}
