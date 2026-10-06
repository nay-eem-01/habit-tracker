package com.nayeem.habittracker.resource;

import com.jayway.jsonpath.JsonPath;
import com.nayeem.habittracker.file.FileProperties;
import com.nayeem.habittracker.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/resources/{id}/file} and deleting a FILE resource (roadmap R.3c). Not
 * {@code @Transactional}: the bytes are removed only after a real commit.
 */
class ResourceFileDownloadIntegrationTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FileProperties fileProperties;

    @Test
    void downloadsTheFileAsAnAttachment() throws Exception {
        String token = bearerFor("download.ok@example.com");
        Integer id = upload(token, "Week 1 plan.pdf");

        mockMvc.perform(get("/api/resources/{id}/file", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PDF.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''Week%201%20plan.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void aNonAsciiNameIsEncodedNotBroken() throws Exception {
        String token = bearerFor("download.utf8@example.com");
        Integer id = upload(token, "পরিকল্পনা \"1\".pdf");

        mockMvc.perform(get("/api/resources/{id}/file", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("%221%22.pdf")));
    }

    @Test
    void onlyTheOwnerCanDownloadAndOnlyFilesHaveOne() throws Exception {
        String alice = bearerFor("download.alice@example.com");
        String bob = bearerFor("download.bob@example.com");
        Integer id = upload(alice, "plan.pdf");
        Integer noteId = JsonPath.read(mockMvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"NOTE\",\"title\":\"n\",\"body\":\"b\"}"))
                .andReturn().getResponse().getContentAsString(), "$.payload.id");

        mockMvc.perform(get("/api/resources/{id}/file", id).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/resources/{id}/file", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resources/{id}/file", noteId).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FILE_NOT_FOUND"));
    }

    @Test
    void bytesMissingFromStorageAre404NotA500() throws Exception {
        String token = bearerFor("download.lost@example.com");
        Integer id = upload(token, "plan.pdf");
        Files.delete(bytesOf(id));

        mockMvc.perform(get("/api/resources/{id}/file", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("FILE_NOT_FOUND"));
    }

    @Test
    void deletingAFileResourceDeletesTheFile() throws Exception {
        String alice = bearerFor("download.delete@example.com");
        String bob = bearerFor("download.delete.bob@example.com");
        Integer id = upload(alice, "plan.pdf");
        Path bytes = bytesOf(id);
        Long fileId = jdbcTemplate.queryForObject("select file_id from resources where id = ?", Long.class, id);

        mockMvc.perform(delete("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, bob))
                .andExpect(status().isNotFound());
        assertThat(bytes).exists();

        mockMvc.perform(delete("/api/resources/{id}", id).header(HttpHeaders.AUTHORIZATION, alice))
                .andExpect(status().isNoContent());
        assertThat(bytes).doesNotExist();
        assertThat(jdbcTemplate.queryForObject("select count(*) from stored_files where id = ?", Long.class, fileId))
                .isZero();
    }

    private Integer upload(String token, String name) throws Exception {
        String body = mockMvc.perform(multipart("/api/resources/files")
                        .file(new MockMultipartFile("file", name, "application/pdf", PDF))
                        .param("title", "Plan").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.payload.id");
    }

    private Path bytesOf(Integer resourceId) {
        String key = jdbcTemplate.queryForObject(
                "select f.storage_key from resources r join stored_files f on f.id = r.file_id where r.id = ?",
                String.class, resourceId);
        return fileProperties.getDir().resolve(key);
    }
}
