package com.web2health.oracle.service;

import com.web2health.oracle.dto.collector.DiscordData;
import com.web2health.oracle.exception.DataCollectionException;

public interface DiscordCollectorService {

    /**
     * 通过 Discord Widget API 采集服务器实时在线人数
     * <p>
     * Widget API 完全公开，无需 Bot Token，无需加入服务器。
     * 前提：目标服务器需开启 Widget（Server Settings → Widget → Enable Server Widget）。
     * 未开启时返回 403，由调用方捕获并标记为 missing。
     *
     * @param guildId Discord 服务器 ID
     * @throws DataCollectionException Widget 未开启或请求失败时抛出
     */
    DiscordData collect(String guildId) throws DataCollectionException;
}
