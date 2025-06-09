// src/main/java/com/order/config/WebSocketConfig.java (例: クラス名は異なるかも)

package com.order.config; // configパッケージにあることが多い

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // ★ これが付いているか確認！
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // SimpleBrokerのプレフィックスを設定（これがないとSimpMessagingTemplateが正しく動作しない可能性）
        config.enableSimpleBroker("/topic"); // ★ "/topic" など、利用するプレフィックスがあるか確認！
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocketエンドポイント
        registry.addEndpoint("/ws-endpoint").withSockJS(); // ★ これが付いているか確認！
    }
}