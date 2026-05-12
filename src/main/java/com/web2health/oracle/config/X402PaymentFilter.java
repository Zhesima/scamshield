package com.web2health.oracle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web2health.oracle.service.PaymentVerifier;
import com.web2health.oracle.service.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * x402 计费过滤器
 *
 * 流程：
 *   1. 仅拦截 x402.path-prefixes 配置的路径（默认 /v1/health/）
 *   2. 取客户端 IP（X-Forwarded-For 优先），消耗免费配额
 *   3. 配额内：直接放行，加 X-Free-Tier-Used 响应头
 *   4. 配额外 + X-PAYMENT 有效：放行
 *   5. 配额外 + 无 X-PAYMENT：返回 402 + payment-required JSON（x402 协议规范格式）
 *
 * 启用：x402.enabled=true
 * 关闭：默认 false，所有请求自由通过（不影响开发演示）
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class X402PaymentFilter {

    private final X402Properties props;
    private final RateLimiter rateLimiter;
    private final PaymentVerifier paymentVerifier;
    private final ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<X402Filter> x402FilterRegistration() {
        FilterRegistrationBean<X402Filter> reg = new FilterRegistrationBean<>(new X402Filter());
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    /** 内部 Filter 实现 */
    public class X402Filter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {

            // 总开关关闭 → 直接放行
            if (!props.isEnabled()) {
                chain.doFilter(request, response);
                return;
            }

            // 路径不匹配 → 直接放行（admin / actuator / 旧接口都不计费）
            String path = request.getRequestURI();
            if (!matchesProtectedPath(path)) {
                chain.doFilter(request, response);
                return;
            }

            String clientIp = resolveClientIp(request);

            // 配额内
            boolean withinQuota = rateLimiter.tryAcquire(clientIp);
            int used = rateLimiter.currentUsage(clientIp);
            response.setHeader("X-Free-Tier-Used", used + "/" + props.getFreeTierQuota());

            if (withinQuota) {
                log.debug("x402 免费配额放行: ip={}, used={}/{}", clientIp, used, props.getFreeTierQuota());
                chain.doFilter(request, response);
                return;
            }

            // 配额外 → 检查 X-PAYMENT
            String xPayment = request.getHeader("X-PAYMENT");
            if (StringUtils.hasText(xPayment)) {
                if (paymentVerifier.verify(xPayment, path)) {
                    log.info("x402 支付验证通过: ip={}, resource={}", clientIp, path);
                    chain.doFilter(request, response);
                    return;
                }
                // 验证失败 → 401 with detail
                writePaymentInvalid(response);
                return;
            }

            // 无支付 → 402
            write402(response, path);
        }

        private boolean matchesProtectedPath(String path) {
            String[] prefixes = props.getPathPrefixes().split(",");
            return Arrays.stream(prefixes)
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .anyMatch(path::startsWith);
        }

        private String resolveClientIp(HttpServletRequest req) {
            String fwd = req.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(fwd)) {
                return fwd.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }

        private void write402(HttpServletResponse resp, String resource) throws IOException {
            // 按 x402 spec 构造 payment-required 响应体
            ObjectNode body = objectMapper.createObjectNode();
            body.put("x402Version", 1);
            body.put("error", "Free quota exhausted, payment required");

            ArrayNode accepts = body.putArray("accepts");
            ObjectNode option = accepts.addObject();
            option.put("scheme", "exact");
            option.put("network", props.getNetwork());
            option.put("maxAmountRequired", String.valueOf(props.getAmountPerCall()));
            option.put("resource", resource);
            option.put("description", "Web2 Health Oracle - Tier 1 (Soft Score)");
            option.put("mimeType", "application/json");
            option.put("payTo", props.getPayTo());
            option.put("maxTimeoutSeconds", 60);
            option.put("asset", props.getUsdcAsset());
            ObjectNode extra = option.putObject("extra");
            extra.put("name", "USD Coin");
            extra.put("version", "2");

            resp.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.getWriter().write(objectMapper.writeValueAsString(body));
        }

        private void writePaymentInvalid(HttpServletResponse resp) throws IOException {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("x402Version", 1);
            body.put("error", "X-PAYMENT verification failed");
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.getWriter().write(objectMapper.writeValueAsString(body));
        }
    }
}
