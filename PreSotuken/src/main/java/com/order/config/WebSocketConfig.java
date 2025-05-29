package com.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // クライアントが購読するプレフィックス（/topicなど）
        config.enableSimpleBroker("/topic");

        // コントローラーでメッセージを受け取るプレフィックス（今回は未使用）
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocketの接続エンドポイント
        registry.addEndpoint("/ws-endpoint")
                .setAllowedOriginPatterns("*")  // フロントエンドとポートが違う場合はここを調整
                .withSockJS();  // SockJSを有効にすることで、古いブラウザでも対応可能
    }
}
