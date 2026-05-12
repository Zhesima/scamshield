package com.web2health.oracle.service.impl;

import com.web2health.oracle.dto.aggregator.ProjectMetadata;
import com.web2health.oracle.service.MetadataAggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompositeMetadataAggregator — CG → DefiLlama 降级")
class CompositeMetadataAggregatorTest {

    private MetadataAggregatorService coingecko;
    private MetadataAggregatorService defillama;
    private CompositeMetadataAggregator composite;

    @BeforeEach
    void init() {
        coingecko = Mockito.mock(MetadataAggregatorService.class);
        defillama = Mockito.mock(MetadataAggregatorService.class);
        composite = new CompositeMetadataAggregator(coingecko, defillama);
    }

    @Test
    @DisplayName("CoinGecko 命中 → 直接返回 CG 数据，不调用 DefiLlama")
    void coingeckoHit_skipsDefiLlama() {
        ProjectMetadata cg = ProjectMetadata.builder().source("coingecko").name("X").build();
        Mockito.when(coingecko.fetchByContract(1, "0xabc")).thenReturn(Optional.of(cg));

        Optional<ProjectMetadata> r = composite.fetchByContract(1, "0xabc");

        assertTrue(r.isPresent());
        assertEquals("coingecko", r.get().getSource());
        Mockito.verifyNoInteractions(defillama);
    }

    @Test
    @DisplayName("CoinGecko 抛异常（如 CN 大陆超时）→ 自动 fallback 到 DefiLlama")
    void coingeckoException_fallsBackToDefiLlama() {
        Mockito.when(coingecko.fetchByContract(1, "0xabc"))
                .thenThrow(new RuntimeException("connection timed out"));
        ProjectMetadata dl = ProjectMetadata.builder().source("defillama").name("Y").build();
        Mockito.when(defillama.fetchByContract(1, "0xabc")).thenReturn(Optional.of(dl));

        Optional<ProjectMetadata> r = composite.fetchByContract(1, "0xabc");

        assertTrue(r.isPresent());
        assertEquals("defillama", r.get().getSource());
    }

    @Test
    @DisplayName("CoinGecko empty（项目不存在）→ 仍尝试 DefiLlama 备援")
    void coingeckoEmpty_stillTriesDefiLlama() {
        Mockito.when(coingecko.fetchByContract(1, "0xabc")).thenReturn(Optional.empty());
        Mockito.when(defillama.fetchByContract(1, "0xabc"))
                .thenReturn(Optional.of(ProjectMetadata.builder().source("defillama").name("Z").build()));

        Optional<ProjectMetadata> r = composite.fetchByContract(1, "0xabc");

        assertTrue(r.isPresent());
        assertEquals("defillama", r.get().getSource());
    }

    @Test
    @DisplayName("两个源都 empty → 返回 empty（让上游抛 ProjectNotFound）")
    void bothEmpty_returnsEmpty() {
        Mockito.when(coingecko.fetchByContract(1, "0xabc")).thenReturn(Optional.empty());
        Mockito.when(defillama.fetchByContract(1, "0xabc")).thenReturn(Optional.empty());

        Optional<ProjectMetadata> r = composite.fetchByContract(1, "0xabc");

        assertTrue(r.isEmpty());
    }

    @Test
    @DisplayName("两个源都抛异常 → 不抛，返回 empty（保守降级）")
    void bothFail_returnsEmpty() {
        Mockito.when(coingecko.fetchByContract(1, "0xabc"))
                .thenThrow(new RuntimeException("CG fail"));
        Mockito.when(defillama.fetchByContract(1, "0xabc"))
                .thenThrow(new RuntimeException("DL fail"));

        Optional<ProjectMetadata> r = composite.fetchByContract(1, "0xabc");

        assertTrue(r.isEmpty());
    }
}
