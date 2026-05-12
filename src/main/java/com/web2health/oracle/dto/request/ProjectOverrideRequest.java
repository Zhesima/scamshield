package com.web2health.oracle.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Admin 接口请求体：人工覆盖项目元数据
 *
 * 所有字段可选，传啥就改啥，没传的字段保持现状
 * 任何被设置的字段会自动加入 manual_overrides 列表，后续 auto-refresh 跳过
 *
 * 解锁字段：unlockFields=["githubRepo"] → 移除锁定，下次 auto-refresh 可覆盖
 *
 * 删除字段值：把字段设为空字符串 ""（null 表示"不修改"，"" 表示"清空"）
 */
@Data
public class ProjectOverrideRequest {

    private String name;
    private String symbol;
    private String description;
    private String website;

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("github_owner")
    private String githubOwner;

    @JsonProperty("github_repo")
    private String githubRepo;

    @JsonProperty("discord_guild_id")
    private String discordGuildId;

    @JsonProperty("twitter_handle")
    private String twitterHandle;

    @JsonProperty("twitter_followers")
    private Integer twitterFollowers;

    @JsonProperty("telegram_users")
    private Integer telegramUsers;

    @JsonProperty("reddit_subscribers")
    private Integer redditSubscribers;

    private String category;

    /** 要解锁的字段名（移出 manual_overrides 列表，允许 auto-refresh 重新覆盖）*/
    @JsonProperty("unlock_fields")
    private java.util.List<String> unlockFields;
}
