package com.web2health.oracle.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    // ── GitHub ──
    @Value("${github.token:}")
    private String githubToken;

    @Value("${github.base-url:https://api.github.com}")
    private String githubBaseUrl;

    // ── Discord ──
    @Value("${discord.base-url:https://discord.com/api/v10}")
    private String discordBaseUrl;

    // ── CoinGecko ──
    @Value("${coingecko.base-url:https://api.coingecko.com/api/v3}")
    private String coingeckoBaseUrl;

    /** Demo Pro tier API key，需要走 https://api.coingecko.com，header 名 x-cg-demo-api-key */
    @Value("${coingecko.demo-key:}")
    private String coingeckoDemoKey;

    /** Pro tier API key（升级套餐用），需要切 base-url 到 pro-api.coingecko.com，header 名 x-cg-pro-api-key */
    @Value("${coingecko.pro-key:}")
    private String coingeckoProKey;

    // ── DefiLlama ──
    @Value("${defillama.coins-base-url:https://coins.llama.fi}")
    private String defillamaCoinsBaseUrl;

    // ── 全局 HTTP 代理 ──
    /** 统一代理（CN 大陆需要：CoinGecko / Discord / GitHub 都可能受限）
     *  例：http://127.0.0.1:7890 或 socks5://127.0.0.1:1080
     *  配空字符串 = 不走代理（生产环境部署在海外节点时） */
    @Value("${proxy.url:}")
    private String proxyUrl;

    @Bean("githubWebClient")
    public WebClient githubWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(githubBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClientWithProxy(10, 30)))
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "Web2HealthOracle/1.0");

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "token " + githubToken);
        }

        return builder.build();
    }

    /** Discord Widget API 公开接口 + invite 解析接口共用此 client，无需鉴权
     *  注意：discord.com 在 CN 大陆受限，本地开发必须走代理 */
    @Bean("discordWebClient")
    public WebClient discordWebClient() {
        return WebClient.builder()
                .baseUrl(discordBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClientWithProxy(10, 15)))
                .defaultHeader("User-Agent", "Web2HealthOracle/1.0")
                .build();
    }

    /**
     * CoinGecko 客户端
     * Demo Pro key 走免费域名 + x-cg-demo-api-key
     * Pro key 走 pro-api.coingecko.com + x-cg-pro-api-key（需手动配 base-url）
     */
    @Bean("coingeckoWebClient")
    public WebClient coingeckoWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(coingeckoBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClientWithProxy(10, 20)))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Web2HealthOracle/1.0");

        if (StringUtils.hasText(coingeckoProKey)) {
            builder.defaultHeader("x-cg-pro-api-key", coingeckoProKey);
        } else if (StringUtils.hasText(coingeckoDemoKey)) {
            builder.defaultHeader("x-cg-demo-api-key", coingeckoDemoKey);
        }
        return builder.build();
    }

    /** DefiLlama coins API（备援源），完全免费，无需 key，CN 大陆可直连
     *  即使配了代理也直连（DefiLlama 在大陆无访问问题，走代理反而增加延迟）*/
    @Bean("defillamaWebClient")
    public WebClient defillamaWebClient() {
        return WebClient.builder()
                .baseUrl(defillamaCoinsBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient(10, 15)))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Web2HealthOracle/1.0")
                .build();
    }

    /** DexScreener API（T2 链上 DEX 数据），免费无 key，CN 大陆通过代理 */
    @Bean("dexscreenerWebClient")
    public WebClient dexscreenerWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.dexscreener.com")
                .clientConnector(new ReactorClientHttpConnector(buildHttpClientWithProxy(10, 20)))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Web2HealthOracle/1.0")
                .build();
    }

    /** 带代理的 HttpClient（按需），proxy.url 为空时直连 */
    private HttpClient buildHttpClientWithProxy(int connectTimeoutSec, int readTimeoutSec) {
        HttpClient client = buildHttpClient(connectTimeoutSec, readTimeoutSec);
        if (StringUtils.hasText(proxyUrl)) {
            client = withProxy(client, proxyUrl);
        }
        return client;
    }

    private HttpClient buildHttpClient(int connectTimeoutSec, int readTimeoutSec) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutSec * 1000)
                .responseTimeout(Duration.ofSeconds(readTimeoutSec))
                // followRedirect: GitHub 仓库重命名 / CoinGecko 旧域名 等场景下会返回 301，必须自动跟随
                // 默认 Reactor Netty 不跟，会得到 301 + JSON 错误体（不是预期 array/object）
                .followRedirect(true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutSec, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutSec, TimeUnit.SECONDS)));
    }

    /** 把 HTTP 代理 URL（http://host:port）应用到 HttpClient */
    private HttpClient withProxy(HttpClient client, String proxyUrl) {
        URI uri = URI.create(proxyUrl);
        ProxyProvider.Proxy type = "socks5".equalsIgnoreCase(uri.getScheme())
                ? ProxyProvider.Proxy.SOCKS5
                : ProxyProvider.Proxy.HTTP;
        return client.proxy(spec -> spec
                .type(type)
                .host(uri.getHost())
                .port(uri.getPort() > 0 ? uri.getPort() : 7890));
    }
}
