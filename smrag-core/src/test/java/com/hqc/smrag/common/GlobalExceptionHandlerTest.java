package com.hqc.smrag.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

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

    @RestController
    @RequestMapping("/test")
    static class PostOnlyController {

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
    }
}
