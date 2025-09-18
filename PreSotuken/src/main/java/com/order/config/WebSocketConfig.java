package com.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket設定を行うコンフィギュレーションクラス
 * リアルタイム通信のためのWebSocketメッセージブローカーを設定します
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * メッセージブローカーを設定します
     * SimpleBrokerとアプリケーション宛先プレフィックスを定義します
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // SimpleBrokerのプレフィックスを設定
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * STOMPエンドポイントを登録します
     * WebSocketクライアントの接続エンドポイントを定義します
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocketエンドポイント（SockJS使用）
        registry.addEndpoint("/ws-endpoint").withSockJS();
    }
}