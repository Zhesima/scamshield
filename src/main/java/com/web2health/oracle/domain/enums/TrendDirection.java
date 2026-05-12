package com.web2health.oracle.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TrendDirection {
    RISING("rising"),
    STABLE("stable"),
    DECLINING("declining"),
    DEAD("dead");

    private final String value;

    TrendDirection(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
