package com.xinyu.rag.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Qdrant 客户端配置
 *
 * <p>从 xinyu.rag.qdrant-url 解析 host + port, 创建单例 {@link QdrantClient}。
 * Java SDK 走 gRPC 端口 (默认 6334, 不是 REST 的 6333)。
 *
 * <p>URL 形如 http://qdrant:6334 或 http://localhost:6334。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class QdrantConfig {

    private final RagProperties ragProperties;

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient() {
        URI uri = URI.create(ragProperties.getQdrantUrl());
        String host = uri.getHost();
        int port = uri.getPort() <= 0 ? 6334 : uri.getPort();
        log.info("初始化 Qdrant 客户端: host={}, port={}, collection={}",
                host, port, ragProperties.getQdrantCollection());
        return new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
    }
}
