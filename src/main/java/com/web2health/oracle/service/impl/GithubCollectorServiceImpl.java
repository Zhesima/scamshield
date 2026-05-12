package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.web2health.oracle.domain.enums.TrendDirection;
import com.web2health.oracle.dto.collector.GithubData;
import com.web2health.oracle.exception.DataCollectionException;
import com.web2health.oracle.service.GithubCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubCollectorServiceImpl implements GithubCollectorService {

    @Qualifier("githubWebClient")
    private final WebClient githubWebClient;

    @Override
    public GithubData collect(String owner, String repo) throws DataCollectionException {
        log.info("开始采集 GitHub 数据: {}/{}", owner, repo);
        try {
            List<Integer> weeklyCommits = fetchCommitActivity(owner, repo);
            RepoInfo repoInfo = fetchRepoInfo(owner, repo);
            CommitsAndAuthors recentActivity = fetchRecentCommits30d(owner, repo);

            // commits_30d / commitsTrend 两路来源：
            //   1. /stats/commit_activity 返回 4 周分布最准（但常 202）
            //   2. /commits?since=30d 拿到的实际 commit 数兜底（trend 回退为均匀分布）
            int commits30d;
            List<Integer> commitsTrend;
            if (!weeklyCommits.isEmpty()) {
                commitsTrend = extractLast4Weeks(weeklyCommits);
                commits30d = commitsTrend.stream().mapToInt(Integer::intValue).sum();
            } else {
                commits30d = recentActivity.totalCommits;
                int weekly = commits30d / 4;
                int remainder = commits30d % 4;
                commitsTrend = List.of(weekly, weekly, weekly, weekly + remainder);
                log.info("GitHub commit_activity 不可用，用 /commits 兜底: {}/{}, total={}", owner, repo, commits30d);
            }
            TrendDirection trendDirection = calcTrendDirection(commitsTrend);
            int contributors30d = recentActivity.uniqueAuthors;

            GithubData data = GithubData.builder()
                    .commits30d(commits30d)
                    .commitsTrend(commitsTrend)
                    .trendDirection(trendDirection)
                    .contributors30d(contributors30d)
                    .openIssues(repoInfo.openIssues())
                    .lastCommitAt(repoInfo.lastCommitAt())
                    .build();

            log.info("GitHub 数据采集完成: {}/{}, commits_30d={}, contributors_30d={}",
                    owner, repo, commits30d, contributors30d);
            return data;

        } catch (DataCollectionException ex) {
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("GitHub API 调用失败: {}/{}, status={}", owner, repo, ex.getStatusCode());
            throw new DataCollectionException("github",
                    String.format("GitHub API 调用失败: %s/%s, HTTP %s", owner, repo, ex.getStatusCode()),
                    ex);
        } catch (Exception ex) {
            log.error("GitHub 数据采集异常: {}/{}", owner, repo, ex);
            throw new DataCollectionException("github",
                    String.format("GitHub 数据采集失败: %s/%s", owner, repo),
                    ex);
        }
    }

    private List<Integer> fetchCommitActivity(String owner, String repo) {
        // GitHub 可能返回 202 + 空对象（stats 还在后台计算）或 200 + 数组（计算完成）
        // 用 toEntity(JsonNode.class) 拿到 status code，避免 bodyToMono(JsonNode[].class) 解析对象时崩溃
        var response = githubWebClient.get()
                .uri("/repos/{owner}/{repo}/stats/commit_activity", owner, repo)
                .retrieve()
                .toEntity(JsonNode.class)
                .block();

        List<Integer> weeklyCommits = new ArrayList<>();
        if (response == null) return weeklyCommits;

        // 202: GitHub 后台正在计算 stats，本次没数据，下次再调即可
        if (response.getStatusCode().value() == 202) {
            log.info("GitHub stats 后台计算中（202），本次跳过 commit_activity: {}/{}", owner, repo);
            return weeklyCommits;
        }

        JsonNode body = response.getBody();
        if (body == null || !body.isArray()) {
            log.warn("GitHub commit_activity 响应非数组: {}/{}, status={}, body={}",
                    owner, repo, response.getStatusCode(), body);
            return weeklyCommits;
        }

        for (JsonNode week : body) {
            weeklyCommits.add(week.path("total").asInt(0));
        }
        return weeklyCommits;
    }

    private RepoInfo fetchRepoInfo(String owner, String repo) {
        JsonNode repoNode = githubWebClient.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (repoNode == null) {
            throw new DataCollectionException("github", "仓库信息返回为空: " + owner + "/" + repo);
        }

        int openIssues = repoNode.path("open_issues_count").asInt(0);
        String pushedAt = repoNode.path("pushed_at").asText(null);
        Instant lastCommitAt = pushedAt != null ? Instant.parse(pushedAt) : null;

        return new RepoInfo(openIssues, lastCommitAt);
    }

    /** 一次 /commits 调用同时拿到：30 天 commit 总数 + 唯一作者数（兼作 stats 兜底）*/
    private CommitsAndAuthors fetchRecentCommits30d(String owner, String repo) {
        // 秒级精度，部分代理对纳秒精度时间戳行为异常
        Instant since = Instant.now().minusSeconds(30L * 24 * 3600).truncatedTo(ChronoUnit.SECONDS);
        try {
            JsonNode body = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/commits")
                            .queryParam("since", since.toString())
                            .queryParam("per_page", 100)
                            .build(owner, repo))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (body == null || !body.isArray()) {
                log.warn("GitHub commits 响应非数组: {}/{}, body={}", owner, repo, body);
                return CommitsAndAuthors.empty();
            }

            int totalCommits = body.size();
            Set<String> authors = new HashSet<>();
            for (JsonNode commit : body) {
                JsonNode authorNode = commit.path("author");
                if (!authorNode.isMissingNode() && !authorNode.isNull()) {
                    String login = authorNode.path("login").asText(null);
                    if (login != null) {
                        authors.add(login);
                    }
                }
            }
            return new CommitsAndAuthors(totalCommits, authors.size());

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                // 空仓库返回 409 Conflict
                return CommitsAndAuthors.empty();
            }
            log.warn("获取 commits 列表失败: {}/{}, status={}", owner, repo, ex.getStatusCode());
            return CommitsAndAuthors.empty();
        }
    }

    private record CommitsAndAuthors(int totalCommits, int uniqueAuthors) {
        static CommitsAndAuthors empty() { return new CommitsAndAuthors(0, 0); }
    }

    private List<Integer> extractLast4Weeks(List<Integer> weeklyCommits) {
        if (weeklyCommits.isEmpty()) {
            return List.of(0, 0, 0, 0);
        }
        int size = weeklyCommits.size();
        int from = Math.max(0, size - 4);
        return new ArrayList<>(weeklyCommits.subList(from, size));
    }

    private TrendDirection calcTrendDirection(List<Integer> last4Weeks) {
        if (last4Weeks.size() < 2) {
            return TrendDirection.STABLE;
        }

        int total = last4Weeks.stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return TrendDirection.DEAD;
        }

        // 用前2周 vs 后2周对比趋势
        int mid = last4Weeks.size() / 2;
        int firstHalf = last4Weeks.subList(0, mid).stream().mapToInt(Integer::intValue).sum();
        int secondHalf = last4Weeks.subList(mid, last4Weeks.size()).stream().mapToInt(Integer::intValue).sum();

        if (firstHalf == 0 && secondHalf > 0) {
            return TrendDirection.RISING;
        }
        if (firstHalf == 0) {
            return TrendDirection.DEAD;
        }

        double changeRate = (double) (secondHalf - firstHalf) / firstHalf;

        if (changeRate > 0.2) return TrendDirection.RISING;
        if (changeRate < -0.2) return TrendDirection.DECLINING;
        return TrendDirection.STABLE;
    }

    private record RepoInfo(int openIssues, Instant lastCommitAt) {}
}
