package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiskFlag {

    private String code;
    private Severity severity;
    private String description;

    public enum Severity {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        CRITICAL("critical");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }
}
