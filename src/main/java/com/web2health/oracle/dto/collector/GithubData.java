package com.web2health.oracle.dto.collector;

import com.web2health.oracle.domain.enums.TrendDirection;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GithubData {

    private int commits30d;
    private List<Integer> commitsTrend;      // 近4周每周提交数
    private TrendDirection trendDirection;
    private int contributors30d;
    private int openIssues;
    private Instant lastCommitAt;
}
