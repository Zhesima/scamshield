package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.web2health.oracle.dto.collector.DiscordData;
import com.web2health.oracle.exception.DataCollectionException;
import com.web2health.oracle.service.DiscordCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordCollectorServiceImpl implements DiscordCollectorService {

    @Qualifier("discordWebClient")
    private final WebClient discordWebClient;

    @Override
    public DiscordData collect(String guildId) throws DataCollectionException {
        log.info("开始采集 Discord Widget 数据, guildId={}", guildId);
        try {
            JsonNode widget = discordWebClient.get()
                    .uri("/guilds/{guild_id}/widget.json", guildId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (widget == null) {
                throw new DataCollectionException("discord", "Discord Widget API 返回为空, guildId=" + guildId);
            }

            int presenceCount = widget.path("presence_count").asInt(0);
            log.info("Discord Widget 采集完成: guildId={}, presence={}", guildId, presenceCount);

            return DiscordData.builder()
                    .presenceCount(presenceCount)
                    .build();

        } catch (DataCollectionException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new DataCollectionException("discord",
                        "Discord 服务器 " + guildId + " 未开启 Widget，请在 Server Settings → Widget → Enable Server Widget 中开启");
            }
            log.error("Discord Widget API 调用失败: guildId={}, status={}", guildId, ex.getStatusCode());
            throw new DataCollectionException("discord",
                    "Discord Widget API 调用失败: guildId=" + guildId + ", HTTP " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            log.error("Discord 数据采集异常: guildId={}", guildId, ex);
            throw new DataCollectionException("discord", "Discord 数据采集失败: guildId=" + guildId, ex);
        }
    }
}
