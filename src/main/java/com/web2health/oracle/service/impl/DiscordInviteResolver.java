package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Discord 邀请链接 → guild_id
 *
 * 输入示例：
 *   https://discord.gg/illuvium
 *   https://discord.com/invite/abc123def
 * 输出：guild_id（snowflake 数字字符串）
 *
 * 实现：调用 Discord 公开邀请 API，无需 Bot Token
 *   GET https://discord.com/api/v10/invites/{code}
 * 返回体里 guild.id 即为目标 guild_id
 *
 * 失败行为：返回 Optional.empty()，不抛异常（Discord 邀请失效是常态）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordInviteResolver {

    @Qualifier("discordWebClient")
    private final WebClient discordWebClient;

    private static final Pattern INVITE_PATTERN =
            Pattern.compile("(?:discord\\.gg|discord(?:app)?\\.com/invite)/([A-Za-z0-9-]+)");

    public Optional<String> resolveGuildId(String inviteUrl) {
        if (inviteUrl == null || inviteUrl.isBlank()) {
            return Optional.empty();
        }

        Matcher m = INVITE_PATTERN.matcher(inviteUrl);
        if (!m.find()) {
            log.debug("非 Discord 邀请链接格式: {}", inviteUrl);
            return Optional.empty();
        }
        String code = m.group(1);

        try {
            JsonNode body = discordWebClient.get()
                    .uri("/invites/{code}", code)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (body == null) {
                return Optional.empty();
            }
            JsonNode guildId = body.path("guild").path("id");
            return guildId.isTextual() ? Optional.of(guildId.asText()) : Optional.empty();
        } catch (Exception ex) {
            log.warn("解析 Discord 邀请失败: code={}, reason={}", code, ex.getMessage());
            return Optional.empty();
        }
    }
}
