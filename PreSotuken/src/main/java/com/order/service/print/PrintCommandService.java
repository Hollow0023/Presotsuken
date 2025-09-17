package com.order.service.print;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * 印刷コマンド生成サービス
 * JSON形式の印刷コマンドを生成する責務を持つ
 */
@Service
@RequiredArgsConstructor
public class PrintCommandService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * テキスト印刷コマンドを生成
     */
    public ObjectNode createTextCommand(String content) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addText");
        command.put("content", content);
        return command;
    }

    /**
     * テキスト配置コマンドを生成
     */
    public ObjectNode createTextAlignCommand(String align) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addTextAlign");
        command.put("align", align);
        return command;
    }

    /**
     * テキスト倍角コマンドを生成
     */
    public ObjectNode createTextDoubleCommand(boolean dw, boolean dh) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addTextDouble");
        command.put("dw", dw);
        command.put("dh", dh);
        return command;
    }

    /**
     * 改行コマンドを生成
     */
    public ObjectNode createFeedCommand() {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addFeed");
        return command;
    }

    /**
     * 改行ユニットコマンドを生成
     */
    public ObjectNode createFeedUnitCommand(int unit) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addFeedUnit");
        command.put("unit", unit);
        return command;
    }

    /**
     * カットコマンドを生成
     */
    public ObjectNode createCutCommand(String type) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addCut");
        command.put("type", type);
        return command;
    }

    /**
     * サウンドコマンドを生成
     */
    public ObjectNode createSoundCommand(String pattern, int repeat) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("api", "addSound");
        command.put("pattern", pattern);
        command.put("repeat", repeat);
        return command;
    }

    /**
     * コマンドリストをJSON文字列に変換
     */
    public String commandsToJson(ArrayNode commands) {
        return commands.toString();
    }

    /**
     * 新しいコマンド配列を作成
     */
    public ArrayNode createCommandArray() {
        return objectMapper.createArrayNode();
    }
}