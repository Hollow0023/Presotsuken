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
import com.order.entity.Menu;
import com.order.entity.MenuPrinterMap;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.PrinterConfig;
import com.order.entity.Seat;
import com.order.entity.User;
// 必要なリポジトリのインポート
import com.order.repository.MenuPrinterMapRepository;
import com.order.repository.PaymentDetailOptionRepository;
import com.order.repository.PrinterConfigRepository;
import com.order.repository.SeatRepository;
import com.order.service.print.PrintCommandService;
import com.order.service.print.PrintFormatService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrintService {

    private final SeatRepository seatRepo;
    private final PaymentDetailOptionRepository paymentDetailOptionRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogoService logoService;
    private final MenuPrinterMapRepository menuPrinterMapRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrintFormatService printFormatService;
    private final PrintCommandService gencmd;

    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON生成用

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
        commands.add(gencmd.createSoundCommand("pattern_a", 1));
        commands.add(gencmd.createAddTextLangCommand("ja"));
        commands.add(gencmd.createAddTextFontCommand("FONT_A")); // フォントA

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

        // ★★★ JSONコマンド組み立て部分 ★★★
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
        commands.add(gencmd.createTextAlignCommand("left")); // デフォルトに戻す
        commands.add(gencmd.createTextCommand("テーブル: " + seatName));

        // 注文者名
        commands.add(gencmd.createFeedUnitCommand(5));
        commands.add(gencmd.createTextCommand((username != null ? username : "不明") + "             " + timeStr));
        System.out.println(timeStr);

        // 日時 (右寄せ)
//            commands.add(createTextAlignCommand("right"));
//            commands.add(createTextCommand("                "+timeStr));
        commands.add(gencmd.createFeedUnitCommand(8));

        // 注文商品 (倍角)
        commands.add(gencmd.createTextDoubleCommand(true, true)); // 倍角設定
        commands.add(gencmd.createTextAlignCommand("left")); // 左寄せに戻す
        commands.add(gencmd.createTextCommand(itemName));
        
        // 点数 (右寄せ、倍角はそのまま)
        commands.add(gencmd.createTextAlignCommand("right"));
        commands.add(gencmd.createTextCommand("          "+quantity + "点"));
        commands.add(gencmd.createFeedUnitCommand(8));

        // 倍角とアラインメントをリセット (次のアイテムのために)
        commands.add(gencmd.createTextDoubleCommand(false, false));
        commands.add(gencmd.createTextAlignCommand("left")); // デフォルトに戻しておく
        
//          commands.add(createFeedUnitCommand(15));
        commands.add(gencmd.createCutCommand("feed")); // 最後はfeedカット
//            commands.add(createFeedCommand()); // 最後の改行
        MenuPrinterMap printerMap = menuPrinterMapRepository.findFirstByMenu_MenuIdOrderByPrinter_PrinterIdAsc(detail.getMenu().getMenuId());
        String printerIp = printerMap.getPrinter().getPrinterIp();
        sendPrintCommandsToFrontend(printerIp, seatId, commands.toString());
    }
    
    // 小計伝票印刷メソッド
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
        commands.add(gencmd.createSoundCommand("pattern_a", 1));
        commands.add(gencmd.createAddTextLangCommand("ja"));
        commands.add(gencmd.createAddTextFontCommand("FONT_A"));
        commands.add(gencmd.createTextAlignCommand("center"));

        // ロゴ画像
        if(logoImageBase64 != null) {
            commands.add(gencmd.createAddImageCommand(logoImageBase64, 0, 0, 256, 60, "COLOR_1", "MONO"));
            commands.add(gencmd.createFeedUnitCommand(10));
        }

        // テーブルと日時
        commands.add(gencmd.createTextAlignCommand("left"));
        String tableLine = "テーブル: " + seatName;
        commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight(tableLine, timeStr, printFormatService.getReceiptTotalWidthHalf())));
        commands.add(gencmd.createFeedUnitCommand(5));
        commands.add(gencmd.createFeedCommand());
        
        // --- ★★★ ヘッダーのレイアウトを修正 ★★★ ---
        String itemHeaderLine = printFormatService.padRightHalfWidth("品名", printFormatService.getReceiptItemNameMaxWidthHalf())
                            + printFormatService.padRightHalfWidth("数量", printFormatService.getReceiptQuantityWidthHalf())
                            + printFormatService.padLeftHalfWidth("単価", printFormatService.getReceiptPriceWidthHalf());
        commands.add(gencmd.createTextCommand(itemHeaderLine));
        commands.add(gencmd.createTextCommand("-".repeat(printFormatService.getReceiptTotalWidthHalf()))); // 罫線

        // --- ★★★ 商品詳細のループ (改行処理を追加) ★★★ ---
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
                // 品名エリアの最大幅を超えないギリギリの文字数を探す
                for (int i = 0; i < remainingItemName.length(); i++) {
                    char c = remainingItemName.charAt(i);
                    int charBytes = 1;
                    try {
                        charBytes = String.valueOf(c).getBytes("Shift_JIS").length > 1 ? 2 : 1;
                    } catch (UnsupportedEncodingException e) { /* ignore */ }

                    if (currentLineByteLength + charBytes > printFormatService.getReceiptItemNameMaxWidthHalf()) {
                        break; // 幅を超えるのでここでカット
                    }
                    currentLineByteLength += charBytes;
                    cutIndex++;
                }

                String itemNameForThisLine = remainingItemName.substring(0, cutIndex);
                remainingItemName = remainingItemName.substring(cutIndex);

                // 品名部分を作成 (右側を空白で埋める)
                String paddedItemName = printFormatService.padRightHalfWidth(itemNameForThisLine, printFormatService.getReceiptItemNameMaxWidthHalf());

                if (isFirstLine) {
                    // 1行目: 品名 + 数量 + 単価
                    String paddedQuantity = printFormatService.padRightHalfWidth(quantityStr, printFormatService.getReceiptQuantityWidthHalf());
                    String paddedPrice = printFormatService.padLeftHalfWidth(unitPriceStr, printFormatService.getReceiptPriceWidthHalf());
                    commands.add(gencmd.createTextCommand(paddedItemName + paddedQuantity + paddedPrice));
                    isFirstLine = false;
                } else {
                    // 2行目以降: 品名の続きのみ
                    commands.add(gencmd.createTextCommand(paddedItemName));
                }
            }
        }
        commands.add(gencmd.createTextCommand("-".repeat(printFormatService.getReceiptTotalWidthHalf()))); // 罫線
        commands.add(gencmd.createFeedUnitCommand(10));

        // --- ★★★ 小計・税額表示のレイアウトを修正 ★★★ ---
        // 小計 (税込)
        String subtotalAmount = "\\" + String.format("%,d", subtotalIncludingTax.longValue());
        commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("小計", subtotalAmount, printFormatService.getReceiptTotalWidthHalf())));

        // 税率ごとの対象額
        List<BigDecimal> sortedTaxRates = new ArrayList<>(taxRateToAmountMap.keySet());
        sortedTaxRates.sort(Comparator.naturalOrder());

        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal amountForRate = taxRateToAmountMap.get(rate);
            String taxRateLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%対象";
            String taxRateAmount = "\\" + String.format("%,d", amountForRate.longValue()) + ")";
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight(taxRateLabel, taxRateAmount, printFormatService.getReceiptTotalWidthHalf())));
        }

        // 「内税」テキスト
        commands.add(gencmd.createTextCommand("内税"));

        // 各税率ごとの税額
        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal taxAmountForRate = taxRateToTaxAmountMap.get(rate);
            String taxRateTaxLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%税";
            String taxRateTaxAmount = "\\" + String.format("%,d", taxAmountForRate.longValue()) + ")";
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight(taxRateTaxLabel, taxRateTaxAmount, printFormatService.getReceiptTotalWidthHalf())));
        }
        
        commands.add(gencmd.createFeedUnitCommand(15));
        commands.add(gencmd.createCutCommand("feed"));

        PrinterConfig printer = printerConfigRepository.findByStoreIdAndReceiptOutput(storeId,true);
        String printerIp = printer.getPrinterIp();
        
        // フロントエンドに送信
        sendPrintCommandsToFrontend(printerIp, seatId, commands.toString());
    }


    /**
     * 領収書を印刷する
     * @param receipt 領収書エンティティ
     * @param storeId 店舗ID
     * @param reprint 再印字フラグ
     */
    public void printReceipt(com.order.entity.Receipt receipt, Integer storeId, boolean reprint) {
        ArrayNode commands = objectMapper.createArrayNode();

        // ロゴを追加
        String logoImageBase64 = logoService.getLogoBase64Data((long)storeId);
        if(logoImageBase64 != null) {
            commands.add(gencmd.createAddImageCommand(logoImageBase64, 0, 0, 256, 60, "COLOR_1", "MONO"));
            commands.add(gencmd.createFeedUnitCommand(10));
        }
        commands.add(gencmd.createFeedCommand());

        // ヘッダー：店名と発行日時
        String storeName = receipt.getStore().getStoreName();
        LocalDateTime issuedAt = receipt.getIssuedAt();
        String dateStr = issuedAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

        commands.add(gencmd.createTextAlignCommand("center"));
        commands.add(gencmd.createTextCommand(storeName));
        commands.add(gencmd.createTextCommand("領収書"));
        if (reprint) {
            commands.add(gencmd.createTextCommand("【再印字】"));
        }
        commands.add(gencmd.createTextAlignCommand("left"));
        commands.add(gencmd.createFeedUnitCommand(5));

        // 印字番号と発行日時
        commands.add(gencmd.createTextCommand("印字番号: " + receipt.getReceiptNo()));
        commands.add(gencmd.createTextCommand("発行日時: " + dateStr));
        commands.add(gencmd.createTextCommand("発行者: " + receipt.getIssuer().getUserName()));
        commands.add(gencmd.createFeedUnitCommand(5));
        commands.add(gencmd.createTextCommand("-".repeat(printFormatService.getReceiptTotalWidthHalf())));

        // 合計金額（税込）
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (receipt.getNetAmount10() != null) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(receipt.getNetAmount10()));
        }
        if (receipt.getTaxAmount10() != null) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(receipt.getTaxAmount10()));
        }
        if (receipt.getNetAmount8() != null) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(receipt.getNetAmount8()));
        }
        if (receipt.getTaxAmount8() != null) {
            totalAmount = totalAmount.add(BigDecimal.valueOf(receipt.getTaxAmount8()));
        }

        String totalAmountStr = "\\" + String.format("%,d", totalAmount.longValue());
        commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("合計金額", totalAmountStr, printFormatService.getReceiptTotalWidthHalf())));
        commands.add(gencmd.createFeedUnitCommand(5));

        // 税率別内訳
        commands.add(gencmd.createTextCommand("【内訳】"));

        // 10%対象
        if (receipt.getNetAmount10() != null && receipt.getNetAmount10() > 0) {
            String net10Str = "\\" + String.format("%,d", receipt.getNetAmount10().longValue());
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("10%対象(税抜)", net10Str, printFormatService.getReceiptTotalWidthHalf())));
            
            String tax10Str = "\\" + String.format("%,d", receipt.getTaxAmount10().longValue());
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("10%税額", tax10Str, printFormatService.getReceiptTotalWidthHalf())));
            
            BigDecimal gross10 = BigDecimal.valueOf(receipt.getNetAmount10()).add(BigDecimal.valueOf(receipt.getTaxAmount10()));
            String gross10Str = "\\" + String.format("%,d", gross10.longValue());
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("10%税込", gross10Str, printFormatService.getReceiptTotalWidthHalf())));
        }

        // 8%対象
        if (receipt.getNetAmount8() != null && receipt.getNetAmount8() > 0) {
            String net8Str = "\\" + String.format("%,d", receipt.getNetAmount8().longValue());
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("8%対象(税抜)", net8Str, printFormatService.getReceiptTotalWidthHalf())));
            
            String tax8Str = "\\" + String.format("%,d", receipt.getTaxAmount8().longValue());
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("8%税額", tax8Str, printFormatService.getReceiptTotalWidthHalf())));
            
            BigDecimal gross8 = BigDecimal.valueOf(receipt.getNetAmount8()).add(BigDecimal.valueOf(receipt.getTaxAmount8()));
            String gross8Str = "\\" + String.format("%,d", gross8.longValue());
            commands.add(gencmd.createTextCommand(printFormatService.formatToLeftAndRight("8%税込", gross8Str, printFormatService.getReceiptTotalWidthHalf())));
        }

        commands.add(gencmd.createFeedUnitCommand(5));
        commands.add(gencmd.createTextCommand("-".repeat(printFormatService.getReceiptTotalWidthHalf())));

        // フッター：会計ID、領収書ID、発行者ID
        commands.add(gencmd.createFeedUnitCommand(5));
        commands.add(gencmd.createTextCommand("会計ID: " + receipt.getPayment().getPaymentId()));
        commands.add(gencmd.createTextCommand("領収書ID: " + receipt.getReceiptId()));
        commands.add(gencmd.createTextCommand("発行者ID: " + receipt.getIssuer().getUserId()));

        // QRコード（印字番号を埋め込み）
        commands.add(gencmd.createFeedUnitCommand(5));
        commands.add(gencmd.createQRCodeCommand(receipt.getReceiptNo()));

        commands.add(gencmd.createFeedUnitCommand(15));
        commands.add(gencmd.createCutCommand("feed"));

        // プリンター取得
        PrinterConfig printer = printerConfigRepository.findByStoreIdAndReceiptOutput(storeId, true);
        String printerIp = printer.getPrinterIp();

        // フロントエンドに送信（seatIdは領収書印刷では不要なので0を使用）
        sendPrintCommandsToFrontend(printerIp, 0, commands.toString());
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
    
    private void notifyClientError(Integer seatId, String message) {
        Map<String, String> payload = new HashMap<>();
        payload.put("type", "PRINT_ERROR");
        payload.put("message", message);
        messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
    }
}