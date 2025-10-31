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
    
    // addTextLangコマンド生成ヘルパー
    public ObjectNode createAddTextLangCommand(String lang) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextLang");
        cmd.put("lang", lang);
        return cmd;
    }

    // addTextFontコマンド生成ヘルパー
    public ObjectNode createAddTextFontCommand(String font) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextFont");
        cmd.put("font", font);
        return cmd;
    }
    
    // addImageコマンド生成ヘルパー (Base64データを含める)
    public ObjectNode createAddImageCommand(String base64Content, int x, int y, int width, int height, String color, String mode) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addImage");
        cmd.put("base64Content", base64Content); // Base64データをそのまま渡す
        cmd.put("x", x);
        cmd.put("y", y);
        cmd.put("width", width);
        cmd.put("height", height);
        cmd.put("color", color); // "COLOR_1" など
        cmd.put("mode", mode); // "MONO", "GRAY16" など
        return cmd;
    }
    
    public ObjectNode createQRCodeCommand(String data) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addSymbol");
        cmd.put("type", "pdf417_standard"); // または "qrcode_model_2"
        cmd.put("level", "level_m");
        cmd.put("width", 3);
        cmd.put("height", 0);
        cmd.put("size", 0);
        cmd.put("data", data);
        return cmd;
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