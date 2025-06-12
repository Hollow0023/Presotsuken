package com.order.service;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.order.entity.Menu;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.Seat;
import com.order.entity.User;
// 必要なリポジトリのインポート
import com.order.repository.MenuPrinterMapRepository;
import com.order.repository.MenuRepository;
import com.order.repository.PaymentDetailOptionRepository;
import com.order.repository.PrinterConfigRepository;
import com.order.repository.SeatRepository;
import com.order.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrintService {

    private final MenuPrinterMapRepository menuPrinterMapRepo;
    private final PrinterConfigRepository printerConfigRepo; // プリンター設定のリポジトリ
    private final MenuRepository menuRepo;
    private final SeatRepository seatRepo;
    private final UserRepository usersRepo;
    private final PaymentDetailOptionRepository paymentDetailOptionRepo;
    private final SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON生成用

    // --- 印字幅に関する定数 (半角換算、プリンターのフォントに合わせて調整が必要) ---
    private static final int INDENT_SPACES = 4;
    private static final int ITEM_NAME_COL_WIDTH = 18;
    private static final int QUANTITY_COL_WIDTH = 8;
    private static final int UNIT_PRICE_COL_WIDTH = 10;
    private static final int SUBTOTAL_LABEL_WIDTH = 10;
    private static final int SUB_TOTAL_AMOUNT_WIDTH = 10;
    private static final int TAX_DETAIL_LABEL_WIDTH = 18;
    private static final int TAX_AMOUNT_WIDTH = 10;
    private static final int TOTAL_LABEL_WIDTH = 10;
    private static final int GRAND_TOTAL_AMOUNT_WIDTH = 10;
    // --- ここまで定数定義 ---

    // ヘルパー関数: 全角文字を2バイト、半角文字を1バイトとして計算
    // これはフロントエンドでも同じロジックが必要になる場合がある (padRightHalfWidthなど使う場合)
    private int calculateEpsonPrintByteLength(String s) {
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

    // 指定された半角幅になるように文字列の右側を空白で埋める（全角文字を考慮）
    private String padRightHalfWidth(String s, int targetHalfWidth) {
        String safeS = (s != null) ? s : "";
        int currentByteLength = calculateEpsonPrintByteLength(safeS);
        if (currentByteLength >= targetHalfWidth) {
            return safeS;
        }
        int paddingLength = targetHalfWidth - currentByteLength;
        return safeS + " ".repeat(Math.max(0, paddingLength));
    }

    // 指定された半角幅になるように文字列の左側を空白で埋める（全角文字を考慮）
    private String padLeftHalfWidth(String s, int targetHalfWidth) {
        String safeS = (s != null) ? s : "";
        int currentByteLength = calculateEpsonPrintByteLength(safeS);
        if (currentByteLength >= targetHalfWidth) {
            return safeS;
        }
        int paddingLength = targetHalfWidth - currentByteLength;
        return " ".repeat(Math.max(0, paddingLength)) + safeS;
    }

    // 単品伝票印刷 (変更後)
    // このメソッドは、JSONコマンドを生成してWebSocketでフロントエンドに通知する
    public void printLabelsForOrder(List<PaymentDetail> details, Integer seatId) {
        String seatName = seatRepo.findById(seatId)
                .map(Seat::getSeatName)
                .orElse("不明な席");

        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));

        User user = details.stream()
                .map(PaymentDetail::getUser)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        String username = (user != null) ? user.getUserName() : "卓上端末";

        // JSONコマンドを格納するArrayNode
        ArrayNode commands = objectMapper.createArrayNode();

        for (PaymentDetail detail : details) {
            Menu menu = detail.getMenu();
            if (menu == null) {
                notifyClientError(seatId, "menu_id=" + detail.getMenu().getMenuId() + " のメニューが存在しません");
                continue;
            }

            List<PaymentDetailOption> optionList = paymentDetailOptionRepo.findByPaymentDetail(detail);
            String optionSuffix = optionList.isEmpty() ? "" :
                    optionList.stream()
                            .map(o -> o.getOptionItem().getItemName())
                            .collect(Collectors.joining("・", "（", "）"));

            String baseLabel = (menu.getReceiptLabel() != null && !menu.getReceiptLabel().isBlank())
                    ? menu.getReceiptLabel()
                    : menu.getMenuName();

            String itemName = baseLabel + optionSuffix;

            Integer currentQuantity = detail.getQuantity();
            int quantity = (currentQuantity != null) ? currentQuantity : 1;

            // ★★★ JSONコマンド組み立て部分 ★★★
            // 各印刷命令をObjectNodeとして作成し、commandsに追加する

            // 初期化・リセット系は、印刷ジョブの最初に一度だけ発行する方が効率的
            // ここでは各アイテムごとに発行するが、設計によって調整
            if (commands.isEmpty()) { // 最初のアイテムのときだけ実行
                 // ブザー
                ObjectNode soundCmd = objectMapper.createObjectNode();
                soundCmd.put("api", "addSound");
                soundCmd.put("pattern", "pattern_a");
                soundCmd.put("repeat", 1);
                commands.add(soundCmd);

                // 言語設定
                ObjectNode textLangCmd = objectMapper.createObjectNode();
                textLangCmd.put("api", "addTextLang");
                textLangCmd.put("lang", "ja");
                commands.add(textLangCmd);

                // 文字サイズのリセット (デフォルト1倍角)
                ObjectNode textSizeResetCmd = objectMapper.createObjectNode();
                textSizeResetCmd.put("api", "addTextSize");
                textSizeResetCmd.put("width", 1);
                textSizeResetCmd.put("height", 1);
                commands.add(textSizeResetCmd);

                 // 文字装飾のリセット (デフォルト)
                ObjectNode textStyleResetCmd = objectMapper.createObjectNode();
                textStyleResetCmd.put("api", "addTextStyle");
                textStyleResetCmd.put("reverse", false);
                textStyleResetCmd.put("ul", false);
                textStyleResetCmd.put("em", false);
                textStyleResetCmd.put("color", "COLOR_1"); // Enum名で渡す
                commands.add(textStyleResetCmd);
            }

            // 個別のアイテム印刷コマンド
            commands.add(createFeedUnitCommand(8)); // 少し空白

            // テーブル名
            commands.add(createTextAlignCommand("left")); // デフォルトに戻す
            commands.add(createTextCommand("テーブル: " + seatName));

            // 注文者名
            commands.add(createTextCommand(username != null ? username : "不明"));

            // 日時 (右寄せ)
            commands.add(createTextAlignCommand("right"));
            commands.add(createTextCommand(timeStr));
            commands.add(createFeedUnitCommand(8));

            // 注文商品 (倍角)
            commands.add(createTextDoubleCommand(true, true)); // 倍角設定
            commands.add(createTextAlignCommand("left")); // 左寄せに戻す
            commands.add(createTextCommand(itemName));

            // 点数 (右寄せ、倍角はそのまま)
            commands.add(createTextAlignCommand("right"));
            commands.add(createTextCommand(quantity + "点"));
            commands.add(createFeedUnitCommand(8));

            // 倍角とアラインメントをリセット (次のアイテムのために)
            commands.add(createTextDoubleCommand(false, false));
            commands.add(createTextAlignCommand("left")); // デフォルトに戻しておく
        }
        
        commands.add(createFeedUnitCommand(15));
        commands.add(createCutCommand("feed")); // 最後はfeedカット
        commands.add(createFeedCommand()); // 最後の改行

        // WebSocketでフロントエンドにJSONコマンドを送信
        sendPrintCommandsToFrontend(seatId, commands.toString());
    }
    
    // 小計伝票印刷メソッド (printReceiptForPayment) も同様にJSONを組み立てる
    public void printReceiptForPayment(List<PaymentDetail> detailsForReceipt, Integer seatId) {
        if (detailsForReceipt == null || detailsForReceipt.isEmpty()) {
            System.out.println("印刷するPaymentDetailがありません。小計伝票の生成をスキップします。");
            return;
        }

        // --- 伝票に必要な情報の計算 (既存ロジックそのまま) ---
        BigDecimal subtotalExcludingTax = BigDecimal.ZERO;
        BigDecimal subtotalIncludingTax = BigDecimal.ZERO;
        Map<BigDecimal, BigDecimal> taxRateToAmountMap = new TreeMap<>();
        Map<BigDecimal, BigDecimal> taxRateToTaxAmountMap = new TreeMap<>();

        for (PaymentDetail detail : detailsForReceipt) {
            BigDecimal unitPriceExcludingTax = BigDecimal.valueOf(detail.getMenu().getPrice());
            BigDecimal itemQuantity = BigDecimal.valueOf(detail.getQuantity());
            BigDecimal taxRateValueFromDb = BigDecimal.valueOf(detail.getTaxRate().getRate());

            BigDecimal itemLineTotalExcludingTax = unitPriceExcludingTax.multiply(itemQuantity);
            BigDecimal taxAmount = itemLineTotalExcludingTax.multiply(taxRateValueFromDb).setScale(0, RoundingMode.HALF_UP);
            BigDecimal itemLineTotalIncludingTax = itemLineTotalExcludingTax.add(taxAmount);

            subtotalExcludingTax = subtotalExcludingTax.add(itemLineTotalExcludingTax);
            subtotalIncludingTax = subtotalIncludingTax.add(itemLineTotalIncludingTax);

            taxRateToAmountMap.merge(taxRateValueFromDb, itemLineTotalExcludingTax, BigDecimal::add);
            taxRateToTaxAmountMap.merge(taxRateValueFromDb, taxAmount, BigDecimal::add);
        }

        BigDecimal totalTaxOnly = taxRateToTaxAmountMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grandTotalIncludingTax = subtotalIncludingTax;

        // --- 共通情報取得 (既存ロジックそのまま) ---
        String seatName = seatRepo.findById(seatId)
                .map(Seat::getSeatName)
                .orElse("不明な席");

        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));

        User user = detailsForReceipt.get(0).getUser();
        String username = (user != null) ? user.getUserName() : "卓上端末";

        String logoImageBase64 = "[ロゴBase64データ]"; // ここに実際のBase64データが入る

        // JSONコマンドを格納するArrayNode
        ArrayNode commands = objectMapper.createArrayNode();

        // 初期設定系
        commands.add(createSoundCommand("pattern_a", 1));
        commands.add(createAddTextLangCommand("ja"));
        commands.add(createAddTextFontCommand("FONT_A")); // フォントA
        commands.add(createTextAlignCommand("center"));
        commands.add(createFeedCommand());

        // ロゴ画像
        // imageはBase64データを直接渡す形にする
//        commands.add(createAddImageCommand(logoImageBase64, 0, 0, 256, 60, "COLOR_1", "MONO"));
        commands.add(createFeedUnitCommand(40));

        // テキストアラインメントをセンターに (上の画像のアラインメント設定を引き継ぐ可能性があるので明示的に)
        commands.add(createTextAlignCommand("center"));
        commands.add(createFeedUnitCommand(5));

        // テーブルと日時 (Java側でパディング処理済み文字列を生成)
        String tableAndTimeLine = " ".repeat(INDENT_SPACES)
                                + padRightHalfWidth("テーブル:" + seatName, calculateEpsonPrintByteLength("テーブル:") + calculateEpsonPrintByteLength(seatName) + 15)
                                + timeStr; // XMLエンティティ化は不要になる
        commands.add(createTextAlignCommand("left")); // 左寄せに戻す
        commands.add(createTextCommand(tableAndTimeLine));
        commands.add(createFeedUnitCommand(5));
        commands.add(createFeedCommand());

        // 品名、数量、単価ヘッダー
        commands.add(createTextAlignCommand("left"));
        String itemHeaderLine = " ".repeat(INDENT_SPACES)
                                + padRightHalfWidth("品名", ITEM_NAME_COL_WIDTH)
                                + padRightHalfWidth("数量", QUANTITY_COL_WIDTH)
                                + padLeftHalfWidth("単価", UNIT_PRICE_COL_WIDTH);
        commands.add(createTextCommand(itemHeaderLine));

        // 商品詳細
        for (PaymentDetail detail : detailsForReceipt) {
            Menu menu = detail.getMenu();
            List<PaymentDetailOption> optionList = paymentDetailOptionRepo.findByPaymentDetail(detail);
            String optionSuffix = optionList.isEmpty() ? "" :
                    optionList.stream()
                            .map(o -> o.getOptionItem().getItemName())
                            .collect(Collectors.joining("・", "（", "）"));

            String displayItemName = (menu.getReceiptLabel() != null && !menu.getReceiptLabel().isBlank())
                    ? menu.getReceiptLabel()
                    : menu.getMenuName();
            displayItemName += optionSuffix;

            String quantityStr = String.valueOf(detail.getQuantity());

            BigDecimal unitPriceIncludingTax = BigDecimal.valueOf(menu.getPrice())
                    .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(detail.getTaxRate().getRate())))
                    .setScale(0, RoundingMode.HALF_UP);
            String unitPriceStr = "\\" + String.format("%,d", unitPriceIncludingTax.longValue());

            String itemLine = " ".repeat(INDENT_SPACES)
                            + padRightHalfWidth(displayItemName, ITEM_NAME_COL_WIDTH)
                            + padRightHalfWidth(quantityStr, QUANTITY_COL_WIDTH)
                            + padLeftHalfWidth(unitPriceStr, UNIT_PRICE_COL_WIDTH);
            commands.add(createTextCommand(itemLine));
        }
        commands.add(createFeedUnitCommand(10));
        commands.add(createFeedCommand());

        // 小計 (税込)
        String subtotalLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("小計", SUBTOTAL_LABEL_WIDTH + 14)
                            + padLeftHalfWidth("\\" + String.format("%,d", subtotalIncludingTax.longValue()), SUB_TOTAL_AMOUNT_WIDTH);
        commands.add(createTextCommand(subtotalLine));

        // 税率ごとの対象額
        List<BigDecimal> sortedTaxRates = new ArrayList<>(taxRateToAmountMap.keySet());
        sortedTaxRates.sort(Comparator.naturalOrder());

        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal amountForRate = taxRateToAmountMap.get(rate);
            String taxRateLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%対象";
            String taxRateAmountLine = " ".repeat(INDENT_SPACES)
                                    + padRightHalfWidth(taxRateLabel, TAX_DETAIL_LABEL_WIDTH)
                                    + padLeftHalfWidth("\\" + String.format("%,d", amountForRate.longValue()) + ")", TAX_AMOUNT_WIDTH + 1);
            commands.add(createTextCommand(taxRateAmountLine));
        }

        // 「内税」テキスト
        commands.add(createTextCommand(" ".repeat(INDENT_SPACES) + "内税"));

        // 各税率ごとの税額
        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal taxAmountForRate = taxRateToTaxAmountMap.get(rate);
            String taxRateTaxLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%税";
            String taxRateTaxLine = " ".repeat(INDENT_SPACES)
                                + padRightHalfWidth(taxRateTaxLabel, TAX_DETAIL_LABEL_WIDTH)
                                + padLeftHalfWidth("\\" + String.format("%,d", taxAmountForRate.longValue()) + ")", TAX_AMOUNT_WIDTH + 1);
            commands.add(createTextCommand(taxRateTaxLine));
        }

        commands.add(createFeedUnitCommand(15));
        commands.add(createCutCommand("feed")); // 最後はfeedカット
        commands.add(createFeedCommand()); // 最後の改行

        // フロントエンドに送信
        sendPrintCommandsToFrontend(seatId, commands.toString());
    }

    // JSONコマンドをフロントエンドに送信するヘルパーメソッド
    private void sendPrintCommandsToFrontend(Integer seatId, String jsonCommands) {
        // 通常、プリンターのIPアドレスはフロントエンドが管理・保持し、
        // 印刷命令の実行時に使用する。
        // ここでは便宜的に、特定のプリンターIPをメッセージに含めるように変更
        // 実際には、複数のプリンターが存在する場合、どのプリンターに送るかの情報も必要になる
        // (例: プリンターの種類 'receipt_printer', 'kitchen_printer' など)
        String targetPrinterIp = "192.168.11.101"; // サンプルコードのIPアドレスを仮に使う

        Map<String, String> payload = new HashMap<>();
        payload.put("type", "PRINT_COMMANDS");
        payload.put("ip", targetPrinterIp); // フロントエンドがどのプリンターに送るか判断できるようIPも渡す
        payload.put("commands", jsonCommands); // JSON文字列をそのまま渡す

        // WebSocketを通じてフロントエンドにメッセージを送信
        messagingTemplate.convertAndSend("/topic/printer/" + seatId, payload);

        System.out.println("--- フロントエンドへ印刷コマンドを通知 ---");
        System.out.println("対象IP: " + targetPrinterIp);
        System.out.println("JSONコマンド:\n" + jsonCommands);
        System.out.println("-------------------------------------");
    }

 // JSONコマンド生成ヘルパーメソッド群
    private ObjectNode createTextCommand(String content) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addText");
        cmd.put("content", content);
        return cmd;
    }

    private ObjectNode createTextAlignCommand(String align) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextAlign");
        cmd.put("align", align); // "left", "center", "right"
        return cmd;
    }

    private ObjectNode createTextDoubleCommand(boolean dw, boolean dh) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextDouble");
        cmd.put("dw", dw);
        cmd.put("dh", dh);
        return cmd;
    }

    private ObjectNode createFeedCommand() {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addFeed");
        return cmd;
    }

    private ObjectNode createFeedUnitCommand(int unit) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addFeedUnit");
        cmd.put("unit", unit);
        return cmd;
    }

    private ObjectNode createCutCommand(String type) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addCut");
        cmd.put("type", type); // "reserve", "feed", "no_feed"など
        return cmd;
    }

    private ObjectNode createSoundCommand(String pattern, int repeat) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addSound");
        cmd.put("pattern", pattern); // "pattern_a"など
        cmd.put("repeat", repeat);
        return cmd;
    }
    
    // addTextLangコマンド生成ヘルパー
    private ObjectNode createAddTextLangCommand(String lang) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextLang");
        cmd.put("lang", lang);
        return cmd;
    }

    // addTextFontコマンド生成ヘルパー
    private ObjectNode createAddTextFontCommand(String font) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextFont");
        cmd.put("font", font);
        return cmd;
    }
    
    // addTextStyleコマンド生成ヘルパー
    private ObjectNode createAddTextStyleCommand(boolean reverse, boolean ul, boolean em, String color) {
        ObjectNode cmd = objectMapper.createObjectNode();
        cmd.put("api", "addTextStyle");
        cmd.put("reverse", reverse);
        cmd.put("ul", ul);
        cmd.put("em", em);
        cmd.put("color", color); // "COLOR_1" など
        return cmd;
    }

    // addImageコマンド生成ヘルパー (Base64データを含める)
    private ObjectNode createAddImageCommand(String base64Content, int x, int y, int width, int height, String color, String mode) {
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
    private void notifyClientError(Integer seatId, String message) {
        Map<String, String> payload = new HashMap<>();
        payload.put("type", "PRINT_ERROR");
        payload.put("message", message);
        messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
    }
}

//// src/main/java/com/order/service/PrintService.java
//package com.order.service;
//
//import java.io.UnsupportedEncodingException;
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.TreeMap;
//import java.util.stream.Collectors;
//
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.RestTemplate;
//
//import com.order.entity.Menu;
//import com.order.entity.MenuPrinterMap;
//import com.order.entity.PaymentDetail;
//import com.order.entity.PaymentDetailOption;
//import com.order.entity.PrinterConfig;
//import com.order.entity.Seat;
//import com.order.entity.Store;
//import com.order.entity.User;
//import com.order.repository.MenuPrinterMapRepository;
//import com.order.repository.MenuRepository;
//import com.order.repository.PaymentDetailOptionRepository;
//import com.order.repository.PrinterConfigRepository;
//import com.order.repository.SeatRepository;
//import com.order.repository.UserRepository;
//
//import lombok.RequiredArgsConstructor;
//
//@Service
//@RequiredArgsConstructor
//public class PrintService {
//
//    private final MenuPrinterMapRepository menuPrinterMapRepo;
//    private final PrinterConfigRepository printerConfigRepo;
//    private final MenuRepository menuRepo;
//    private final SeatRepository seatRepo;
//    private final UserRepository usersRepo;
//    private final PaymentDetailOptionRepository paymentDetailOptionRepo;
//    private final SimpMessagingTemplate messagingTemplate;
//    private final RestTemplate restTemplate;
//    
//    
//    // --- 印字幅に関する定数 (半角換算、プリンターのフォントに合わせて調整が必要) ---
//    // プリンターの印字幅は32桁〜40桁程度が多いので、それに合わせて調整してください。
//    private static final int INDENT_SPACES = 4; // 各行の先頭の半角スペース数 "    "
//    private static final int ITEM_NAME_COL_WIDTH = 18; // 「品名」列の商品名部分の表示幅（全角9文字相当）
//    private static final int QUANTITY_COL_WIDTH = 8;  // 「数量」列の表示幅（半角換算）
//    private static final int UNIT_PRICE_COL_WIDTH = 10; // 「単価」列の表示幅（半角換算、"\\X,XXX"を考慮）
//
//    private static final int SUBTOTAL_LABEL_WIDTH = 10;
//    private static final int SUB_TOTAL_AMOUNT_WIDTH = 10;
//
//    private static final int TAX_DETAIL_LABEL_WIDTH = 18;
//    private static final int TAX_AMOUNT_WIDTH = 10; 
//    
//    private static final int TOTAL_LABEL_WIDTH = 10; 
//    private static final int GRAND_TOTAL_AMOUNT_WIDTH = 10; 
//    // --- ここまで定数定義 ---
//
//    // ヘルパー関数: 全角文字を2バイト、半角文字を1バイトとして計算
//    private int calculateEpsonPrintByteLength(String s) {
//        int length = 0;
//        if (s == null) return 0;
//        for (char c : s.toCharArray()) {
//            try {
//                if (String.valueOf(c).getBytes("Shift_JIS").length == 2) {
//                    length += 2;
//                } else {
//                    length += 1;
//                }
//            } catch (UnsupportedEncodingException e) {
//                length += 1;
//            }
//        }
//        return length;
//    }
//
//    // 指定された半角幅になるように文字列の右側を空白で埋める（全角文字を考慮）
//    private String padRightHalfWidth(String s, int targetHalfWidth) {
//        String safeS = (s != null) ? s : "";
//        int currentByteLength = calculateEpsonPrintByteLength(safeS);
//        if (currentByteLength >= targetHalfWidth) {
//            return safeS;
//        }
//        int paddingLength = targetHalfWidth - currentByteLength;
//        return safeS + " ".repeat(Math.max(0, paddingLength));
//    }
//
//    // 指定された半角幅になるように文字列の左側を空白で埋める（全角文字を考慮）
//    private String padLeftHalfWidth(String s, int targetHalfWidth) {
//        String safeS = (s != null) ? s : "";
//        int currentByteLength = calculateEpsonPrintByteLength(safeS);
//        if (currentByteLength >= targetHalfWidth) {
//            return safeS;
//        }
//        int paddingLength = targetHalfWidth - currentByteLength;
//        return " ".repeat(Math.max(0, paddingLength)) + safeS;
//    }
//
//    //単品伝票印刷
//    public void printLabelsForOrder(List<PaymentDetail> details, Integer seatId) {
//        String seatName = seatRepo.findById(seatId)
//                .map(Seat::getSeatName)
//                .orElse("不明な席");
//
//        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
//
//        User user = details.stream()
//                .map(PaymentDetail::getUser)
//                .filter(Objects::nonNull)
//                .findFirst().orElse(null);
//        String username = (user != null) ? user.getUserName() : "卓上端末";
//
//        for (PaymentDetail detail : details) {
//            Menu menu = detail.getMenu();
//            if (menu == null) {
//                notifyClientError(seatId, "menu_id=" + detail.getMenu() + " のメニューが存在しません");
//                continue;
//            }
//
//            List<PaymentDetailOption> optionList = paymentDetailOptionRepo.findByPaymentDetail(detail);
//            String optionSuffix = optionList.isEmpty() ? "" :
//                    optionList.stream()
//                            .map(o -> o.getOptionItem().getItemName())
//                            .collect(Collectors.joining("・", "（", "）"));
//
//            String baseLabel = (menu.getReceiptLabel() != null && !menu.getReceiptLabel().isBlank())
//                    ? menu.getReceiptLabel()
//                    : menu.getMenuName();
//
//            String itemName = baseLabel + optionSuffix;
//            
//            Integer currentQuantity = detail.getQuantity();
//            int quantity = (currentQuantity != null) ? currentQuantity : 1;
//            
//            List<MenuPrinterMap> mappings = menuPrinterMapRepo.findByMenu_MenuId(menu.getMenuId());
//            
//            if (mappings.isEmpty()) {
//                System.out.println("DEBUG: メニューID " + menu.getMenuId() + " に紐づくプリンタが設定されていません。印刷スキップ。");
//                notifyClientError(seatId, "メニューID " + menu.getMenuId() + " に紐づくプリンタが設定されていません。");
//                continue;
//            }
//
//            for (MenuPrinterMap map : mappings) {
//                String ip = map.getPrinter().getPrinterIp();
//
//                // ★★★ 修正後のXMLテンプレート組み立て部分 ★★★
//                StringBuilder xmlContent = new StringBuilder();
//                xmlContent.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
//                xmlContent.append("<epos-print xmlns=\"http://www.epson-pos.com/schemas/2011/03/epos-print\">\n");
//
//                // ブザー
//                xmlContent.append("  <sound pattern=\"pattern_a\" repeat=\"1\"/>\n"); 
//                
//                // 言語設定と紙送り
//                xmlContent.append("  <text lang=\"ja\"/>\n");
//                xmlContent.append("  <feed unit=\"8\"/>\n");
//
//                // テーブル名と席番号
//                xmlContent.append("  <text width=\"1\" height=\"1\">テーブル: ").append(escapeXml(seatName)).append("&#10;</text>\n");
//                
//                // 注文者は左端に
//                xmlContent.append("  <text>").append(escapeXml(username != null ? username : "不明")).append("&#10;</text>\n");
//                
//                // 日時は右端に（新しい行で右寄せ）
//                xmlContent.append("  <text align=\"right\">").append(escapeXml(timeStr)).append("&#10;</text>\n"); 
//                
//                xmlContent.append("  <feed unit=\"8\"/>\n"); 
//                
//                // 注文商品は左端に（倍角）
//                xmlContent.append("  <text dw=\"true\" dh=\"true\">").append(escapeXml(itemName)).append("&#10;</text>\n");
//                
//                // 点数は右端に（新しい行で右寄せ）
//                // 注文商品とは別の行になるけど、スタンダードモードで両端寄せにするにはこれが一番確実な方法だよ。
//                xmlContent.append("  <text align=\"right\">").append(quantity).append("点&#10;</text>\n");
//                
//                xmlContent.append("  <feed unit=\"8\"/>\n"); 
//
//                // 装飾のリセット (念のため)
//                xmlContent.append("  <text reverse=\"false\" ul=\"false\" em=\"false\" color=\"color_1\"/>\n");
//                xmlContent.append("  <text width=\"1\" height=\"1\"/>\n");
//                
//                // カットは最後の一回だけ
//                // 元のテンプレートに合わせて `type="reserve"` にする
//                xmlContent.append("  <cut type=\"reserve\"/>\n"); 
//                
//                xmlContent.append("</epos-print>\n");
//
//                sendToPrinter(ip, xmlContent.toString(), seatId);
//            }
//        }
//    }
//
//    
//    //プリンターに送信
//    private void sendToPrinter(String ip, String xmlData, Integer seatId) {
//        String url = "http://" + ip + "/cgi-bin/epos/eposprint.cgi";
//        
//        System.out.println("--- プリンタ送信内容 ---");
//        System.out.println("送信先IP: " + ip);
//        System.out.println("印刷内容:\n" + xmlData);
//        System.out.println("----------------------");
//
//        try {
////            restTemplate.postForEntity(url, xmlData, String.class);    //いったんこめんとあうと
//            System.out.println("送信先IP: " + ip + " へePOS-Print XMLを送信しました。");
//        } catch (HttpClientErrorException e) {
//            System.err.println("プリンタへの送信中にHTTPエラーが発生しました (IP: " + ip + ", ステータスコード: " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
//            notifyClientError(seatId, "プリンタ通信エラー (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
//        } catch (Exception e) {
//            System.err.println("プリンタへの送信中に予期せぬエラーが発生しました (IP: " + ip + "): " + e.getMessage());
//            notifyClientError(seatId, "プリンタエラー: " + e.getMessage());
//        }
//    }
//
//    
//    // ★★★ 小計伝票印刷メソッド (printReceiptForPayment) ★★★
//    public void printReceiptForPayment(List<PaymentDetail> detailsForReceipt, Integer seatId) {
//        if (detailsForReceipt == null || detailsForReceipt.isEmpty()) {
//            System.out.println("印刷するPaymentDetailがありません。小計伝票の生成をスキップします。");
//            return;
//        }
//
//        // --- 伝票に必要な情報の計算 ---
//        BigDecimal subtotalExcludingTax = BigDecimal.ZERO; // 税抜き小計
//        BigDecimal subtotalIncludingTax = BigDecimal.ZERO; // 税込み小計
//        Map<BigDecimal, BigDecimal> taxRateToAmountMap = new TreeMap<>(); // 税率(%) -> 税率対象額 (税抜き)
//        Map<BigDecimal, BigDecimal> taxRateToTaxAmountMap = new TreeMap<>(); // 税率(%) -> 税額
//
//        for (PaymentDetail detail : detailsForReceipt) {
//            BigDecimal unitPriceExcludingTax = BigDecimal.valueOf(detail.getMenu().getPrice()); 
//            BigDecimal itemQuantity = BigDecimal.valueOf(detail.getQuantity());
//            BigDecimal taxRateValueFromDb = BigDecimal.valueOf(detail.getTaxRate().getRate());
//
//            // 各商品の税抜き行合計
//            BigDecimal itemLineTotalExcludingTax = unitPriceExcludingTax.multiply(itemQuantity); 
//            // 各商品の税額
//            BigDecimal taxAmount = itemLineTotalExcludingTax.multiply(taxRateValueFromDb).setScale(0, RoundingMode.HALF_UP); 
//            // 各商品の税込み行合計
//            BigDecimal itemLineTotalIncludingTax = itemLineTotalExcludingTax.add(taxAmount);
//
//            subtotalExcludingTax = subtotalExcludingTax.add(itemLineTotalExcludingTax); // 全体の税抜き小計に加算
//            subtotalIncludingTax = subtotalIncludingTax.add(itemLineTotalIncludingTax); // 全体の税込み小計に加算
//
//            taxRateToAmountMap.merge(taxRateValueFromDb, itemLineTotalExcludingTax, BigDecimal::add); // 税抜き対象額を集計
//            taxRateToTaxAmountMap.merge(taxRateValueFromDb, taxAmount, BigDecimal::add); // 税額を集計
//        }
//
//        BigDecimal totalTaxOnly = taxRateToTaxAmountMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add); // 全体の税額合計
//        BigDecimal grandTotalIncludingTax = subtotalIncludingTax; // 合計額は税込み小計に等しい
//
//        // --- 共通情報取得 ---
//        String seatName = seatRepo.findById(seatId)
//                .map(Seat::getSeatName)
//                .orElse("不明な席");
//
//        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
//
//        User user = detailsForReceipt.get(0).getUser(); 
//        String username = (user != null) ? user.getUserName() : "卓上端末";
//
//        String logoImageBase64 = "[ロゴBase64データ]"; 
//
//        // --- XMLテンプレートの組み立て（小計伝票用） ---
//        StringBuilder xmlContent = new StringBuilder();
//        xmlContent.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
//        xmlContent.append("<epos-print xmlns=\"http://www.epson-pos.com/schemas/2011/03/epos-print\">\n");
//
//        xmlContent.append("  <text lang=\"ja\"/>\n");
//        xmlContent.append("  <text font=\"font_a\"/>\n");
//        xmlContent.append("  <text align=\"center\"/>\n");
//        xmlContent.append("  <feed/>\n");
//
//        xmlContent.append("  <image width=\"256\" height=\"60\" color=\"color_1\" mode=\"mono\">")
//                  .append(logoImageBase64)
//                  .append("</image>\n");
//        xmlContent.append("  <feed unit=\"40\"/>\n");
//
//        xmlContent.append("  <text align=\"center\"/>\n");
//        xmlContent.append("  <feed unit=\"5\"/>\n");
//
//        String tableAndTimeLine = " ".repeat(INDENT_SPACES) 
//                                + padRightHalfWidth("テーブル:" + escapeXml(seatName), 
//                                                     calculateEpsonPrintByteLength("テーブル:") + calculateEpsonPrintByteLength(escapeXml(seatName)) + 15)
//                                + escapeXml(timeStr);
//        xmlContent.append("  <text align=\"left\"/>\n");
//        xmlContent.append("  <text>").append(tableAndTimeLine).append("&#10;</text>\n");
//        xmlContent.append("  <feed unit=\"5\"/>\n");
//        xmlContent.append("  <feed/>\n");
//
//        xmlContent.append("  <text align=\"left\"/>\n");
//        String itemHeaderLine = " ".repeat(INDENT_SPACES)
//                                + padRightHalfWidth("品名", ITEM_NAME_COL_WIDTH)
//                                + padRightHalfWidth("数量", QUANTITY_COL_WIDTH) 
//                                + padLeftHalfWidth("単価", UNIT_PRICE_COL_WIDTH);
//        xmlContent.append("  <text>").append(itemHeaderLine).append("&#10;</text>\n");
//
//        for (PaymentDetail detail : detailsForReceipt) {
//            Menu menu = detail.getMenu();
//            List<PaymentDetailOption> optionList = paymentDetailOptionRepo.findByPaymentDetail(detail);
//            String optionSuffix = optionList.isEmpty() ? "" :
//                    optionList.stream()
//                            .map(o -> o.getOptionItem().getItemName())
//                            .collect(Collectors.joining("・", "（", "）"));
//
//            String displayItemName = (menu.getReceiptLabel() != null && !menu.getReceiptLabel().isBlank())
//                    ? menu.getReceiptLabel()
//                    : menu.getMenuName();
//            displayItemName += optionSuffix;
//
//            String quantityStr = String.valueOf(detail.getQuantity());
//            
//            // 単価を税込みで表示
//            BigDecimal unitPriceIncludingTax = BigDecimal.valueOf(detail.getMenu().getPrice())
//                                                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(detail.getTaxRate().getRate())))
//                                                .setScale(0, RoundingMode.HALF_UP);
//            String unitPriceStr = "\\" + String.format("%,d", unitPriceIncludingTax.longValue()); 
//
//            String itemLine = " ".repeat(INDENT_SPACES)
//                              + padRightHalfWidth(escapeXml(displayItemName), ITEM_NAME_COL_WIDTH)
//                              + padRightHalfWidth(escapeXml(quantityStr), QUANTITY_COL_WIDTH)
//                              + padLeftHalfWidth(escapeXml(unitPriceStr), UNIT_PRICE_COL_WIDTH);
//            xmlContent.append("  <text>").append(itemLine).append("&#10;</text>\n");
//        }
//        xmlContent.append("  <feed unit=\"10\"/>\n");
//        xmlContent.append("  <feed/>\n");
//
//        // 小計
//        // 小計は税込み金額の合計を表示
//        String subtotalLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("小計", SUBTOTAL_LABEL_WIDTH + 14) 
//                               + padLeftHalfWidth("\\" + String.format("%,d", subtotalIncludingTax.longValue()), SUB_TOTAL_AMOUNT_WIDTH);
//        xmlContent.append("  <text>").append(subtotalLine).append("&#10;</text>\n");
//
//        // --- 税率ごとの表示の順序を修正 ---
//        List<BigDecimal> sortedTaxRates = new ArrayList<>(taxRateToAmountMap.keySet());
//        sortedTaxRates.sort(Comparator.naturalOrder()); // 税率を昇順でソート (例: 0.08, 0.1)
//
//        // 1. 各税率ごとの「(X%対象 [税率%対象額])」をすべて出力
//        for (BigDecimal rate : sortedTaxRates) {
//            BigDecimal amountForRate = taxRateToAmountMap.get(rate); 
//            String taxRateLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%対象"; 
//            String taxRateAmountLine = " ".repeat(INDENT_SPACES)
//                                     + padRightHalfWidth(taxRateLabel, TAX_DETAIL_LABEL_WIDTH) 
//                                     + padLeftHalfWidth("\\" + String.format("%,d", amountForRate.longValue()) + ")", TAX_AMOUNT_WIDTH + 1); 
//            xmlContent.append("  <text>").append(taxRateAmountLine).append("&#10;</text>\n");
//        }
//
//        // 2. 「内税」テキストを復活
//        xmlContent.append("  <text> ").append("    内税").append("&#10;</text>\n"); // テンプレートのスペースに合わせて修正
//
//        // 3. 各税率ごとの「(X%税 [税率の額])」をまとめて出力
//        for (BigDecimal rate : sortedTaxRates) {
//            BigDecimal taxAmountForRate = taxRateToTaxAmountMap.get(rate); 
//            String taxRateTaxLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%税"; 
//            String taxRateTaxLine = " ".repeat(INDENT_SPACES)
//                                  + padRightHalfWidth(taxRateTaxLabel, TAX_DETAIL_LABEL_WIDTH)
//                                  + padLeftHalfWidth("\\" + String.format("%,d", taxAmountForRate.longValue()) + ")", TAX_AMOUNT_WIDTH + 1); 
//            xmlContent.append("  <text>").append(taxRateTaxLine).append("&#10;</text>\n");
//        }
//        
//        xmlContent.append("  <feed unit=\"15\"/>\n");
//
////        // 合計
////        // 合計も税込み金額を表示
////        String totalLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("合計", TOTAL_LABEL_WIDTH + 8) 
////                           + padLeftHalfWidth("\\" + String.format("%,d", grandTotalIncludingTax.longValue()), GRAND_TOTAL_AMOUNT_WIDTH); // grandTotalをgrandTotalIncludingTaxに変更
////        xmlContent.append("  <text>").append(totalLine).append("&#10;</text>\n");
////        xmlContent.append("  <text dw=\"false\" dh=\"false\"/>\n");
////        xmlContent.append("  <feed unit=\"10\"/>\n");
//
//        // --- 合計以下「注)」までの表示を削除 ---
//        // 削除する部分のXML生成ロジックをコメントアウトまたは削除
//        /*
//        // (内消費税等 [金額]） - 全体の消費税合計
//        String totalTaxOnlyLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("(内消費税等", TAX_DETAIL_LABEL_WIDTH + 6) 
//                                   + padLeftHalfWidth("\\" + String.format("%,d", totalTaxOnly.longValue()) + "）", TAX_AMOUNT_WIDTH + 1); 
//        xmlContent.append("  <text>").append(totalTaxOnlyLine).append("&#10;</text>\n");
//
//        // [税率]%対象 \[対象額] - 全税率の合計対象額
//        String combinedTaxableAmountLabel = "(対象"; // 「(対象」
//        String combinedTaxableAmountLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth(combinedTaxableAmountLabel, TAX_DETAIL_LABEL_WIDTH + 6) // ラベル幅調整
//                                     + padLeftHalfWidth("\\" + String.format("%,d", subtotal.longValue()) + ")", TAX_AMOUNT_WIDTH + 1); // subtotalは税抜きの合計
//        xmlContent.append("  <text>").append(combinedTaxableAmountLine).append("&#10;</text>\n");
//        */
//        
//        // 削除された部分に対応するfeedも削除または調整
//        xmlContent.append("  <feed/>\n"); // これもテンプレートにあったので残す
//
//        /*
//        xmlContent.append("  <text align=\"center\"/>\n");
//        xmlContent.append("  <text>注) ※印は軽減税率適用商品</text>\n");
//        xmlContent.append("  <feed/>\n");
//        */
//
//        xmlContent.append("  <cut type=\"feed\"/>\n");
//        xmlContent.append("  <feed/>\n");
//
//        xmlContent.append("</epos-print>\n");
//        
//        
//     // プリンターのIP取得ロジック
//        // storeIdを取得
//        Store store = seatRepo.findById(seatId)
//                        .map(Seat::getStore)
//                        .orElse(null);
//
//        if (store == null) {
//            System.err.println("SeatID: " + seatId + " に紐づく店舗情報が見つかりませんでした。");
//            notifyClientError(seatId, "店舗情報が見つかりません。");
//            return;
//        }
//
//        // storeIdとreceipt_outputでプリンタを検索
//        // receipt_outputの値は、PrinterConfigエンティティのフィールドで設定されていると仮定
//        // 例: PrinterConfigに `outputType` フィールドがあり、"RECEIPT_OUTPUT"のような文字列で区別される
//        // または `isReceiptPrinter` のようなbooleanフィールドがある
//        List<PrinterConfig> receiptPrinters = printerConfigRepo.findByStoreIdAndReceiptOutput(store.getStoreId(), true); // 仮の"RECEIPT_OUTPUT"
//
//        if (receiptPrinters.isEmpty()) {
//            System.err.println("StoreId: " + store.getStoreId() + " のレシート出力用プリンタ設定が見つかりませんでした。");
//            notifyClientError(seatId, "レシートプリンタ設定なし。");
//            return;
//        }
//
//        // 見つかった全てのプリンタへ送信
//        for (PrinterConfig printer : receiptPrinters) {
//            String ip = printer.getPrinterIp(); // PrinterConfigエンティティにgetPrinterIp()がある仮定
//            sendToPrinter(ip, xmlContent.toString(), seatId);
//        }
//        
//        
//        
////	//    多分不要
////        Menu firstMenu = detailsForReceipt.get(0).getMenu();
////        List<MenuPrinterMap> mappings = menuPrinterMapRepo.findByMenu_MenuId(firstMenu.getMenuId());
////        
////        if (!mappings.isEmpty()) {
////            String ip = mappings.get(0).getPrinter().getPrinterIp();
////            sendToPrinter(ip, xmlContent.toString(), seatId);
////        } else {
////            System.err.println("小計伝票を印刷するプリンタが見つかりませんでした。");
////            notifyClientError(seatId, "小計伝票プリンタ設定なし。");
////        }
//    }
//    
//    
//    
//    private String escapeXml(String text) {
//        if (text == null) {
//            return "";
//        }
//        return text.replace("&", "&amp;")
//                   .replace("<", "&lt;")
//                   .replace(">", "&gt;")
//                   .replace("\"", "&quot;")
//                   .replace("'", "&apos;");
//    }
//
//    private void notifyClientError(Integer seatId, String message) {
//        Map<String, String> payload = new HashMap<>();
//        payload.put("type", "PRINT_ERROR");
//        payload.put("message", message);
//        messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
//    }
//    
//    
//}