package com.order.service.print;

import java.io.UnsupportedEncodingException;

import org.springframework.stereotype.Service;

/**
 * 印刷フォーマットサービス
 * 文字列のフォーマットや配置に関する責務を持つ
 */
@Service
public class PrintFormatService {

    // 印字幅に関する定数
    private static final int RECEIPT_TOTAL_WIDTH_HALF = 34;
    private static final int RECEIPT_ITEM_NAME_MAX_WIDTH_HALF = 18;
    private static final int RECEIPT_QUANTITY_WIDTH_HALF = 6;
    private static final int RECEIPT_PRICE_WIDTH_HALF = 10;

    /**
     * 全角文字を2バイト、半角文字を1バイトとして計算
     */
    public int calculateEpsonPrintByteLength(String s) {
        int length = 0;
        if (s == null) return 0;
        for (char c : s.toCharArray()) {
            try {
                if (String.valueOf(c).getBytes("Shift_JIS").length == 2) {
                    length += 2;
                } else {
                    length += 1;
                }
            } catch (UnsupportedEncodingException e) {
                length += 1;
            }
        }
        return length;
    }

    /**
     * 指定された半角幅になるように文字列の右側を空白で埋める
     */
    public String padRightHalfWidth(String s, int targetHalfWidth) {
        String safeS = (s != null) ? s : "";
        int currentByteLength = calculateEpsonPrintByteLength(safeS);
        if (currentByteLength >= targetHalfWidth) {
            return safeS;
        }
        int spacesToAdd = targetHalfWidth - currentByteLength;
        return safeS + " ".repeat(spacesToAdd);
    }

    /**
     * 指定された半角幅になるように文字列の左側を空白で埋める
     */
    public String padLeftHalfWidth(String s, int targetHalfWidth) {
        String safeS = (s != null) ? s : "";
        int currentByteLength = calculateEpsonPrintByteLength(safeS);
        if (currentByteLength >= targetHalfWidth) {
            return safeS;
        }
        int spacesToAdd = targetHalfWidth - currentByteLength;
        return " ".repeat(spacesToAdd) + safeS;
    }

    /**
     * 左右に文字列を配置してフォーマット
     */
    public String formatToLeftAndRight(String leftText, String rightText, int totalWidth) {
        String safeLeftText = (leftText != null) ? leftText : "";
        String safeRightText = (rightText != null) ? rightText : "";
        
        int leftLength = calculateEpsonPrintByteLength(safeLeftText);
        int rightLength = calculateEpsonPrintByteLength(safeRightText);
        
        if (leftLength + rightLength >= totalWidth) {
            return safeLeftText + safeRightText;
        }
        
        int spacesToAdd = totalWidth - leftLength - rightLength;
        return safeLeftText + " ".repeat(spacesToAdd) + safeRightText;
    }

    /**
     * テキストを中央配置でフォーマット
     */
    public String formatToCenter(String text, int totalWidth) {
        String safeText = (text != null) ? text : "";
        int textLength = calculateEpsonPrintByteLength(safeText);
        
        if (textLength >= totalWidth) {
            return safeText;
        }
        
        int totalSpaces = totalWidth - textLength;
        int leftSpaces = totalSpaces / 2;
        int rightSpaces = totalSpaces - leftSpaces;
        
        return " ".repeat(leftSpaces) + safeText + " ".repeat(rightSpaces);
    }

    /**
     * 数値を金額フォーマットで表示
     */
    public String formatCurrency(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * 価格を右寄せでフォーマット
     */
    public String formatPriceRight(long price, int width) {
        String formattedPrice = "¥" + formatCurrency(price);
        return padLeftHalfWidth(formattedPrice, width);
    }

    /**
     * 区切り線を生成
     */
    public String createSeparatorLine(int width) {
        return "-".repeat(width);
    }

    /**
     * 空行を生成
     */
    public String createEmptyLine() {
        return "";
    }

    // 定数のgetterメソッド
    public int getReceiptTotalWidthHalf() {
        return RECEIPT_TOTAL_WIDTH_HALF;
    }

    public int getReceiptItemNameMaxWidthHalf() {
        return RECEIPT_ITEM_NAME_MAX_WIDTH_HALF;
    }

    public int getReceiptQuantityWidthHalf() {
        return RECEIPT_QUANTITY_WIDTH_HALF;
    }

    public int getReceiptPriceWidthHalf() {
        return RECEIPT_PRICE_WIDTH_HALF;
    }
}