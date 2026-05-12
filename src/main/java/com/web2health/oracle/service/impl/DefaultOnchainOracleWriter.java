package com.web2health.oracle.service.impl;

import com.web2health.oracle.config.T3Properties;
import com.web2health.oracle.dto.response.T2HealthScoreResponse;
import com.web2health.oracle.service.OnchainOracleWriter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 调 HealthOracle.publishScore 上链
 *
 * 启用条件：t3.enabled=true（避免没部署合约时空启动失败）
 * 注意：publisher 账户必须持有少量 ETH 付 gas
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "t3.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DefaultOnchainOracleWriter implements OnchainOracleWriter {

    private final T3Properties props;

    @Value("${proxy.url:}")
    private String proxyUrl;

    private Web3j web3j;
    private Credentials publisherCreds;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(props.getHealthOracleAddress())) {
            throw new IllegalStateException("t3.enabled=true 但 t3.health-oracle-address 未配置");
        }
        if (!StringUtils.hasText(props.getPublisherPrivateKey())) {
            throw new IllegalStateException("t3.enabled=true 但 t3.publisher-private-key 未配置");
        }

        OkHttpClient http = buildOkHttp();
        web3j = Web3j.build(new HttpService(props.getRpcUrl(), http));
        publisherCreds = Credentials.create(props.getPublisherPrivateKey().trim());

        log.info("OnchainOracleWriter 启用: contract={}, publisher={}, chainId={}, rpc={}",
                props.getHealthOracleAddress(), publisherCreds.getAddress(),
                props.getChainId(), props.getRpcUrl());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String publish(T2HealthScoreResponse resp) {
        try {
            // 1. 编码 publishScore(chainId, token, score, block, blockHash, timestamp, signature)
            byte[] sigBytes = Numeric.hexStringToByteArray(resp.getSignature().value());
            byte[] blockHashBytes = Numeric.hexStringToByteArray(resp.getBlockHash());
            if (blockHashBytes.length != 32) {
                throw new IllegalArgumentException("blockHash 必须为 32 字节: " + resp.getBlockHash());
            }

            Function fn = new Function(
                    "publishScore",
                    Arrays.asList(
                            new Uint256(BigInteger.valueOf(resp.getProject().getChainId())),
                            new Address(resp.getProject().getTokenAddress()),
                            new Uint8(BigInteger.valueOf(resp.getScore())),
                            new Uint64(BigInteger.valueOf(resp.getBlockNumber())),
                            new Bytes32(blockHashBytes),
                            new Uint64(BigInteger.valueOf(resp.getSignature().signedAt())),
                            new DynamicBytes(sigBytes)
                    ),
                    Collections.<TypeReference<?>>emptyList()
            );
            String data = FunctionEncoder.encode(fn);

            // 2. 拿 nonce / gas
            EthGetTransactionCount nonceResp = web3j.ethGetTransactionCount(
                    publisherCreds.getAddress(), DefaultBlockParameterName.PENDING).send();
            BigInteger nonce = nonceResp.getTransactionCount();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(props.getGasLimit());

            // 3. 构造 + 签名 + 广播
            RawTransaction tx = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, props.getHealthOracleAddress(), BigInteger.ZERO, data);
            byte[] signed = TransactionEncoder.signMessage(tx, props.getChainId(), publisherCreds);
            EthSendTransaction sendResp = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();

            if (sendResp.hasError()) {
                throw new RuntimeException("publishScore 广播失败: " + sendResp.getError().getMessage());
            }
            String txHash = sendResp.getTransactionHash();
            log.info("T3 上链成功: token={}, score={}, tx={}",
                    resp.getProject().getTokenAddress(), resp.getScore(), txHash);
            return txHash;

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("T3 publish 异常: " + ex.getMessage(), ex);
        }
    }

    private OkHttpClient buildOkHttp() {
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60))
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
}
