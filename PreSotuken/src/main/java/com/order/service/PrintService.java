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
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.order.entity.Menu;
import com.order.entity.MenuPrinterMap;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.PrinterConfig;
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
    private final LogoService logoService;
    private final MenuPrinterMapRepository menuPrinterMapRepository;
    private final PrinterConfigRepository printerConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON生成用

    // --- 印字幅に関する定数 (半角換算、プリンターのフォントに合わせて調整が必要) ---
    private static final int INDENT_SPACES = 0;
    private static final int ITEM_NAME_COL_WIDTH = 12;
    private static final int QUANTITY_COL_WIDTH = 10;
    private static final int UNIT_PRICE_COL_WIDTH = 10;
    private static final int SUBTOTAL_LABEL_WIDTH = 14;
    private static final int SUB_TOTAL_AMOUNT_WIDTH = 14;
    private static final int TAX_DETAIL_LABEL_WIDTH = 18;
    private static final int TAX_AMOUNT_WIDTH = 10;
    // 小計伝票のレイアウト用（半角換算）の補助定数
    /** レシート全体の幅 (全角17文字) */
    private static final int RECEIPT_TOTAL_WIDTH_HALF = 34;
    /** 品名エリアの最大幅 (全角9文字分)。この幅で数量の開始位置が決まる */
    private static final int RECEIPT_ITEM_NAME_MAX_WIDTH_HALF = 18;
    /** 数量エリアの幅 (品名エリアの右隣) */
    private static final int RECEIPT_QUANTITY_WIDTH_HALF = 6;
    /** 単価エリアの幅 (数量エリアの右隣) */
    private static final int RECEIPT_PRICE_WIDTH_HALF = 10;
    // --- ここまで補助定数 ---
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
    
    /**
     * レシートの左側・右側に文字列を配置するためのヘルパー関数。
     * 指定した全体幅の中で、文字列を左寄せと右寄せに配置して返す
     * @param leftText 左側に配置する文字列
     * @param rightText 右側に配置する文字列
     * @param totalWidth 全体の半角幅
     * @return フォーマットされた文字列
     */
    private String formatToLeftAndRight(String leftText, String rightText, int totalWidth) {
        int leftBytes = calculateEpsonPrintByteLength(leftText);
        int rightBytes = calculateEpsonPrintByteLength(rightText);
        int paddingBytes = totalWidth - leftBytes - rightBytes;

        if (paddingBytes < 0) {
            // 幅が足りない場合は、とりあえず連結して返す（あるいは別の方法も検討可）
            return leftText + rightText;
        }
        return leftText + " ".repeat(paddingBytes) + rightText;
    }

    // 単品伝票印刷 (変更後)
    // このメソッドは、JSONコマンドを生成してWebSocketでフロントエンドに通知する
    public void printLabelsForOrder(PaymentDetail detail, Integer seatId) {
        String seatName = seatRepo.findById(seatId)
                .map(Seat::getSeatName)
                .orElse("不明な席");
        
        if (detail == null) {
            notifyClientError(seatId, "印刷対象のPaymentDetailがnullです。");
            return;
        }

        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));

        User user = detail.getUser();
        String username = (user != null) ? user.getUserName() : "卓上端末";

        // JSONコマンドを格納するArrayNode
        ArrayNode commands = objectMapper.createArrayNode();
        commands.add(createSoundCommand("pattern_a", 1));
        commands.add(createAddTextLangCommand("ja"));
        commands.add(createAddTextFontCommand("FONT_A")); // フォントA

        Menu menu = detail.getMenu();
        if (menu == null) {
            notifyClientError(seatId, "menu_id=" + detail.getMenu().getMenuId() + " のメニューが存在しません");
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

        // JSONコマンドの組み立て処理
        // 各印刷命令をObjectNodeとして作成し、commandsに追加する

        // 初期化・リセット系は、印刷ジョブの最初に一度だけ発行する方が効率的
        // ここでは各アイテムごとに発行するが、設計によって調整
//            if (commands.isEmpty()) { // 最初のアイテムのときだけ実行
//                 // ブザー
//                ObjectNode soundCmd = objectMapper.createObjectNode();
//                soundCmd.put("api", "addSound");
//                soundCmd.put("pattern", "pattern_a");
//                soundCmd.put("repeat", 1);
//                commands.add(soundCmd);
//
//                // 言語設定
//                ObjectNode textLangCmd = objectMapper.createObjectNode();
//                textLangCmd.put("api", "addTextLang");
//                textLangCmd.put("lang", "ja");
//                commands.add(textLangCmd);
//
//                // 文字サイズのリセット (デフォルト1倍角)
//                ObjectNode textSizeResetCmd = objectMapper.createObjectNode();
//                textSizeResetCmd.put("api", "addTextSize");
//                textSizeResetCmd.put("width", 1);
//                textSizeResetCmd.put("height", 1);
//                commands.add(textSizeResetCmd);
//
//                 // 文字装飾のリセット (デフォルト)
//                ObjectNode textStyleResetCmd = objectMapper.createObjectNode();
//                textStyleResetCmd.put("api", "addTextStyle");
//                textStyleResetCmd.put("reverse", false);
//                textStyleResetCmd.put("ul", false);
//                textStyleResetCmd.put("em", false);
//                textStyleResetCmd.put("color", "COLOR_1"); // Enum名で渡す
//                commands.add(textStyleResetCmd);
//            }

        // 個別のアイテム印刷コマンド
//            commands.add(createFeedUnitCommand(0)); // 少し空白
        
//            //初期設定
//            commands.add(createSoundCommand("pattern_a", 1));
//            commands.add(createAddTextLangCommand("ja"));
//            commands.add(createAddTextFontCommand("FONT_A")); // フォントA
//            commands.add(createTextAlignCommand("center"));
//            commands.add(createFeedCommand());

        // テーブル名
        commands.add(createTextAlignCommand("left")); // デフォルトに戻す
        commands.add(createTextCommand("テーブル: " + seatName));

        // 注文者名
        commands.add(createFeedUnitCommand(5));
        commands.add(createTextCommand((username != null ? username : "不明") + "             " + timeStr));
        System.out.println(timeStr);

        // 日時 (右寄せ)
//            commands.add(createTextAlignCommand("right"));
//            commands.add(createTextCommand("                "+timeStr));
        commands.add(createFeedUnitCommand(8));

        // 注文商品 (倍角)
        commands.add(createTextDoubleCommand(true, true)); // 倍角設定
        commands.add(createTextAlignCommand("left")); // 左寄せに戻す
        commands.add(createTextCommand(itemName));

        // 点数 (右寄せ、倍角はそのまま)
        commands.add(createTextAlignCommand("right"));
        commands.add(createTextCommand("          "+quantity + "点"));
        commands.add(createFeedUnitCommand(8));

        // 倍角とアラインメントをリセット (次のアイテムのために)
        commands.add(createTextDoubleCommand(false, false));
        commands.add(createTextAlignCommand("left")); // デフォルトに戻しておく
        
//          commands.add(createFeedUnitCommand(15));
        commands.add(createCutCommand("feed")); // 最後はfeedカット
//            commands.add(createFeedCommand()); // 最後の改行
        MenuPrinterMap printerMap = menuPrinterMapRepository.findFirstByMenu_MenuIdOrderByPrinter_PrinterIdAsc(detail.getMenu().getMenuId());
        String printerIp = printerMap.getPrinter().getPrinterIp();
        sendPrintCommandsToFrontend(printerIp, seatId, commands.toString());

    }
    
    

//        return commands;
    
    
    // 小計伝票を印刷するメソッド
    public void printReceiptForPayment(
            List<PaymentDetail> detailsForReceipt,
            Integer seatId,
            Integer storeId) {

        // --- 伝票に必要な情報の計算 (変更なし) ---
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

        // --- 共通情報取得 (変更なし) ---
        String seatName = seatRepo.findById(seatId).map(Seat::getSeatName).orElse("不明な席");
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        String logoImageBase64 = logoService.getLogoBase64Data((long)storeId);

        // --- JSONコマンド組み立て開始 ---
        ArrayNode commands = objectMapper.createArrayNode();
        commands.add(createSoundCommand("pattern_a", 1));
        commands.add(createAddTextLangCommand("ja"));
        commands.add(createAddTextFontCommand("FONT_A"));
        commands.add(createTextAlignCommand("center"));

        // ロゴ画像
        if(logoImageBase64 != null) {
            commands.add(createAddImageCommand(logoImageBase64, 0, 0, 256, 60, "COLOR_1", "MONO"));
            commands.add(createFeedUnitCommand(10));
        }

        // テーブルと日時
        commands.add(createTextAlignCommand("left"));
        String tableLine = "テーブル: " + seatName;
        commands.add(createTextCommand(formatToLeftAndRight(tableLine, timeStr, RECEIPT_TOTAL_WIDTH_HALF)));
        commands.add(createFeedUnitCommand(5));
        commands.add(createFeedCommand());
        
        // --- ヘッダー行のレイアウト設定 ---
        String itemHeaderLine = padRightHalfWidth("品名", RECEIPT_ITEM_NAME_MAX_WIDTH_HALF)
                            + padRightHalfWidth("数量", RECEIPT_QUANTITY_WIDTH_HALF)
                            + padLeftHalfWidth("単価", RECEIPT_PRICE_WIDTH_HALF);
        commands.add(createTextCommand(itemHeaderLine));
        commands.add(createTextCommand("-".repeat(RECEIPT_TOTAL_WIDTH_HALF))); // 罫線

        // --- 商品詳細を複数行に分割して描画 ---
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
            
            String remainingItemName = displayItemName;
            boolean isFirstLine = true;

            // 品名がなくなるまでループして、複数行に分割する
            while (!remainingItemName.isEmpty()) {
                int currentLineByteLength = 0;
                int cutIndex = 0;
                // RECEIPT_ITEM_NAME_MAX_WIDTH_HALF を超えないギリギリの文字数を探す
                for (int i = 0; i < remainingItemName.length(); i++) {
                    char c = remainingItemName.charAt(i);
                    int charBytes = 1;
                    try {
                        charBytes = String.valueOf(c).getBytes("Shift_JIS").length > 1 ? 2 : 1;
                    } catch (UnsupportedEncodingException e) { /* ignore */ }

                    if (currentLineByteLength + charBytes > RECEIPT_ITEM_NAME_MAX_WIDTH_HALF) {
                        break; // 幅を超えるのでここでカット
                    }
                    currentLineByteLength += charBytes;
                    cutIndex++;
                }

                String itemNameForThisLine = remainingItemName.substring(0, cutIndex);
                remainingItemName = remainingItemName.substring(cutIndex);

                // 品名部分を作成 (右側を空白で埋める)
                String paddedItemName = padRightHalfWidth(itemNameForThisLine, RECEIPT_ITEM_NAME_MAX_WIDTH_HALF);

                if (isFirstLine) {
                    // 1行目: 品名 + 数量 + 単価
                    String paddedQuantity = padRightHalfWidth(quantityStr, RECEIPT_QUANTITY_WIDTH_HALF);
                    String paddedPrice = padLeftHalfWidth(unitPriceStr, RECEIPT_PRICE_WIDTH_HALF);
                    commands.add(createTextCommand(paddedItemName + paddedQuantity + paddedPrice));
                    isFirstLine = false;
                } else {
                    // 2行目以降: 品名の続きのみ
                    commands.add(createTextCommand(paddedItemName));
                }
            }
        }
        commands.add(createTextCommand("-".repeat(RECEIPT_TOTAL_WIDTH_HALF))); // 罫線
        commands.add(createFeedUnitCommand(10));

        // --- 小計と税額の表示レイアウト ---
        // 小計 (税込)
        String subtotalAmount = "\\" + String.format("%,d", subtotalIncludingTax.longValue());
        commands.add(createTextCommand(formatToLeftAndRight("小計", subtotalAmount, RECEIPT_TOTAL_WIDTH_HALF)));

        // 税率ごとの対象額
        List<BigDecimal> sortedTaxRates = new ArrayList<>(taxRateToAmountMap.keySet());
        sortedTaxRates.sort(Comparator.naturalOrder());

        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal amountForRate = taxRateToAmountMap.get(rate);
            String taxRateLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%対象";
            String taxRateAmount = "\\" + String.format("%,d", amountForRate.longValue()) + ")";
            commands.add(createTextCommand(formatToLeftAndRight(taxRateLabel, taxRateAmount, RECEIPT_TOTAL_WIDTH_HALF)));
        }

        // 「内税」テキスト
        commands.add(createTextCommand("内税"));

        // 各税率ごとの税額
        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal taxAmountForRate = taxRateToTaxAmountMap.get(rate);
            String taxRateTaxLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%税";
            String taxRateTaxAmount = "\\" + String.format("%,d", taxAmountForRate.longValue()) + ")";
            commands.add(createTextCommand(formatToLeftAndRight(taxRateTaxLabel, taxRateTaxAmount, RECEIPT_TOTAL_WIDTH_HALF)));
        }
        
        commands.add(createFeedUnitCommand(15));
        commands.add(createCutCommand("feed"));

        PrinterConfig printer = printerConfigRepository.findByStoreIdAndReceiptOutput(storeId,true);
        String printerIp = printer.getPrinterIp();
        
        // フロントエンドに送信
        sendPrintCommandsToFrontend(printerIp, seatId, commands.toString());
    }


    // JSONコマンドをフロントエンドに送信するヘルパーメソッド
    public void sendPrintCommandsToFrontend(String printerIp, Integer seatId, String jsonCommands) {
        String targetPrinterIp = printerIp; // サンプルコードのIPアドレスを仮に使う

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