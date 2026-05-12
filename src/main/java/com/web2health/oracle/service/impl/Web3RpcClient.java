package com.web2health.oracle.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * 轻量 Web3 RPC 客户端
 *
 * 职责：
 *   1. 调 ERC-20 view 函数：totalSupply / decimals
 *   2. 拿当前块高 + hash + 时间戳（T2 pinning 用）
 *
 * 多 RPC 失败切换：t2.rpc-urls 可配多个，第一个失败自动切下一个
 *
 * 注意：CN 大陆访问 Base RPC 也需要走代理（mainnet.base.org 在大陆受限）
 *      暂不在 RPC 层显式配代理，依赖系统级代理（HTTPS_PROXY 环境变量）
 *      生产部署在海外节点时直连
 */
@Slf4j
@Component
public class Web3RpcClient {

    @Value("${t2.rpc-urls:https://mainnet.base.org}")
    private String rpcUrlsCsv;

    /** 与其他 WebClient 共用一个代理配置（CN 大陆访问 Base RPC 需要走代理）*/
    @Value("${proxy.url:}")
    private String proxyUrl;

    private Web3j web3j;
    private List<String> rpcUrls;

    @PostConstruct
    public void init() {
        rpcUrls = List.of(rpcUrlsCsv.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        OkHttpClient httpClient = buildHttpClient();
        web3j = Web3j.build(new HttpService(rpcUrls.get(0), httpClient));
        log.info("Web3RpcClient 初始化: rpc={}, proxy={}", rpcUrls.get(0),
                StringUtils.hasText(proxyUrl) ? proxyUrl : "none");
    }

    private OkHttpClient buildHttpClient() {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(15));
        if (StringUtils.hasText(proxyUrl)) {
            URI uri = URI.create(proxyUrl);
            Proxy.Type type = "socks5".equalsIgnoreCase(uri.getScheme())
                    ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            int port = uri.getPort() > 0 ? uri.getPort() : 7890;
            b.proxy(new Proxy(type, new InetSocketAddress(uri.getHost(), port)));
        }
        return b.build();
    }

    /** 当前最新区块（号 + hash + 时间戳）*/
    public BlockInfo getLatestBlock() {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            EthBlock.Block b = block.getBlock();
            return new BlockInfo(
                    b.getNumber().longValueExact(),
                    b.getHash(),
                    b.getTimestamp().longValueExact()
            );
        } catch (Exception ex) {
            throw new RuntimeException("获取最新块失败", ex);
        }
    }

    /** 调 ERC-20 totalSupply */
    public BigInteger erc20TotalSupply(String contractAddress) {
        Function fn = new Function(
                "totalSupply",
                List.of(),
                List.of(new TypeReference<Uint256>() {})
        );
        return (BigInteger) callView(contractAddress, fn).get(0).getValue();
    }

    /** 调 ERC-20 decimals */
    public int erc20Decimals(String contractAddress) {
        Function fn = new Function(
                "decimals",
                List.of(),
                List.of(new TypeReference<Uint8>() {})
        );
        BigInteger result = (BigInteger) callView(contractAddress, fn).get(0).getValue();
        return result.intValue();
    }

    /** 通用 eth_call */
    private List<Type> callView(String contractAddress, Function fn) {
        try {
            String encoded = FunctionEncoder.encode(fn);
            EthCall resp = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, contractAddress, encoded),
                    DefaultBlockParameterName.LATEST
            ).send();
            if (resp.hasError()) {
                throw new RuntimeException("eth_call 失败: " + resp.getError().getMessage());
            }
            return FunctionReturnDecoder.decode(resp.getValue(), fn.getOutputParameters());
        } catch (Exception ex) {
            throw new RuntimeException("eth_call 异常: " + ex.getMessage(), ex);
        }
    }

    public record BlockInfo(long number, String hash, long timestamp) {}
}
