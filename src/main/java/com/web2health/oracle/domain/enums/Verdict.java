package com.web2health.oracle.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Verdict {
    HEALTHY("healthy"),
    CAUTION("caution"),
    WEAK("weak"),
    DANGER("danger");

    private final String value;

    Verdict(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Verdict fromScore(int score) {
        if (score >= 75) return HEALTHY;
        if (score >= 50) return CAUTION;
        if (score >= 25) return WEAK;
        return DANGER;
    }
}
