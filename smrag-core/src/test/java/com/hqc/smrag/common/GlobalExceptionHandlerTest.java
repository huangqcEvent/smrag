package com.hqc.smrag.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;
import com.hqc.smrag.exception.DomainNotFoundException;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    @Test
    void ignoresClientDisconnectInsteadOfReportingServerFailure() throws Exception {
        standaloneSetup(new PostOnlyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(get("/test/disconnected"))
            .andExpect(status().isOk());
    }

    @Test
    void preservesMethodNotAllowedStatusForUnsupportedHttpMethods() throws Exception {
        standaloneSetup(new PostOnlyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(get("/test/post-only"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void returnsBadRequestWhenMultipartFileIsMissing() throws Exception {
        standaloneSetup(new PostOnlyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(multipart("/test/upload"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void returnsBadRequestWhenMultipartContentTypeIsMissing() throws Exception {
        standaloneSetup(new PostOnlyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(post("/test/upload"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        standaloneSetup(new PostOnlyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()
            .perform(post("/test/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void preservesDomainErrorDetailsAndStatuses() throws Exception {
        var mvc = standaloneSetup(new PostOnlyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        mvc.perform(post("/test/invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("知识库编码已存在"));
        mvc.perform(post("/test/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("没有文档权限"));
        mvc.perform(post("/test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("知识库中仍有文档"));
        mvc.perform(post("/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("KNOWLEDGE_BASE_NOT_FOUND"))
            .andExpect(jsonPath("$.details.code").value("missing"));
    }

    @RestController
    @RequestMapping("/test")
    static class PostOnlyController {

        @GetMapping("/disconnected")
        Result<Void> disconnected() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("Connection reset by peer");
        }

        @PostMapping("/post-only")
        Result<Void> postOnly() {
            return Result.success();
        }

        @PostMapping("/upload")
        Result<Void> upload(@RequestPart("file") MultipartFile file) {
            return Result.success();
        }

        @PostMapping("/json")
        Result<Void> json(@RequestBody Map<String, Object> body) {
            return Result.success();
        }

        @PostMapping("/invalid")
        Result<Void> invalid() {
            throw new IllegalArgumentException("知识库编码已存在");
        }

        @PostMapping("/forbidden")
        Result<Void> forbidden() {
            throw new AccessDeniedException("没有文档权限");
        }

        @PostMapping("/conflict")
        Result<Void> conflict() {
            throw new IllegalStateException("知识库中仍有文档");
        }

        @PostMapping("/not-found")
        Result<Void> notFound() {
            throw new DomainNotFoundException(
                "KNOWLEDGE_BASE_NOT_FOUND",
                "知识库不存在",
                Map.of("code", "missing")
            );
        }
    }
}
