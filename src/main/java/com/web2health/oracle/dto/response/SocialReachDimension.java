package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 社交触达维度
 *
 * 主指标：Twitter 粉丝数（来自聚合器 community_data，无需 Twitter API）
 * 辅助指标：Telegram 频道用户数、Reddit 订阅人数（仅展示，不参与评分）
 *
 * 数据源：CoinGecko community_data，DefiLlama 部分协议
 * 缺点：粉丝数容易刷，应配合发推频率/互动率才能反映真实社交健康度
 *       T1 仅取粉丝数作为粗信号，T2 升级时叠加互动率指标
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SocialReachDimension {

    private int score;
    private double weight;

    @JsonProperty("twitter_followers")
    private Integer twitterFollowers;

    @JsonProperty("telegram_users")
    private Integer telegramUsers;

    @JsonProperty("reddit_subscribers")
    private Integer redditSubscribers;

    /** 数据源：coingecko / defillama / manual */
    private String source;
}
