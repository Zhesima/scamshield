package com.web2health.oracle.dto.collector;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiscordData {

    // Widget API 返回的实时在线人数，完全公开、无法造假
    private int presenceCount;
}
