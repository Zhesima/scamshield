package com.web2health.oracle.controller;

import com.web2health.oracle.domain.enums.Verdict;
import com.web2health.oracle.dto.response.HealthScoreResponse;
import com.web2health.oracle.dto.response.ScoreMetadata;
import com.web2health.oracle.exception.ProjectNotFoundException;
import com.web2health.oracle.service.HealthScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthScoreController.class)
@DisplayName("HealthScoreController 接口测试")
class HealthScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthScoreService healthScoreService;

    @Test
    @DisplayName("GET /v1/project/{id}/health-score - 200 正常返回")
    void getHealthScore_200() throws Exception {
        HealthScoreResponse mockResponse = buildMockResponse(1L);
        when(healthScoreService.getHealthScore(eq("1"), anyBoolean())).thenReturn(mockResponse);

        mockMvc.perform(get("/v1/project/1/health-score")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.verdict").value("healthy"))
                .andExpect(jsonPath("$.metadata.project_id").value(1))
                .andExpect(jsonPath("$.metadata.cached").value(false))
                .andExpect(jsonPath("$.risk_flags").isArray())
                .andExpect(jsonPath("$.dimensions").exists());
    }

    @Test
    @DisplayName("GET /v1/project/{id}/health-score - 404 项目不存在")
    void getHealthScore_404_projectNotFound() throws Exception {
        when(healthScoreService.getHealthScore(eq("999"), anyBoolean()))
                .thenThrow(new ProjectNotFoundException("999"));

        mockMvc.perform(get("/v1/project/999/health-score")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /v1/project/{id}/health-score?realtime=true - 实时采集参数传递")
    void getHealthScore_withRealtimeParam() throws Exception {
        HealthScoreResponse mockResponse = buildMockResponse(1L);
        when(healthScoreService.getHealthScore(eq("1"), eq(true))).thenReturn(mockResponse);

        mockMvc.perform(get("/v1/project/1/health-score")
                        .param("realtime", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").exists());
    }

    @Test
    @DisplayName("GET /v1/project/{id}/health-score - 部分数据源缺失返回 206")
    void getHealthScore_206_partialData() throws Exception {
        HealthScoreResponse partialResponse = buildMockResponse(2L);
        partialResponse.getMetadata().getMissingSources().add("discord");

        when(healthScoreService.getHealthScore(eq("2"), anyBoolean())).thenReturn(partialResponse);

        mockMvc.perform(get("/v1/project/2/health-score")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.metadata.missing_sources").isArray());
    }

    private HealthScoreResponse buildMockResponse(Long projectId) {
        Instant now = Instant.now();
        return HealthScoreResponse.builder()
                .score(75)
                .verdict(Verdict.HEALTHY)
                .dimensions(HealthScoreResponse.Dimensions.builder()
                        .devActivity(null)
                        .community(null)
                        .gameTraction(null)
                        .build())
                .riskFlags(List.of())
                .metadata(ScoreMetadata.builder()
                        .projectId(projectId)
                        .collectedAt(now)
                        .cached(false)
                        .cacheExpiresAt(now.plusSeconds(3600))
                        .dataSources(List.of("github"))
                        .missingSources(new java.util.ArrayList<>())
                        .build())
                .build();
    }
}
