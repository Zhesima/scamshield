package com.web2health.oracle.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiError {

    private String code;
    private String message;
}
