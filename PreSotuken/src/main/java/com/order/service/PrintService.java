// src/main/java/com/order/service/PrintService.java
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.order.entity.Menu;
import com.order.entity.MenuPrinterMap;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.Seat;
import com.order.entity.User;
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
    private final PrinterConfigRepository printerConfigRepo;
    private final MenuRepository menuRepo;
    private final SeatRepository seatRepo;
    private final UserRepository usersRepo;
    private final PaymentDetailOptionRepository paymentDetailOptionRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;
    
    
    // --- 印字幅に関する定数 (半角換算、プリンターのフォントに合わせて調整が必要) ---
    // プリンターの印字幅は32桁〜40桁程度が多いので、それに合わせて調整してください。
    private static final int INDENT_SPACES = 4; // 各行の先頭の半角スペース数 "    "
    private static final int ITEM_NAME_COL_WIDTH = 18; // 「品名」列の商品名部分の表示幅（全角9文字相当）
    private static final int QUANTITY_COL_WIDTH = 8;  // 「数量」列の表示幅（半角換算）
    private static final int UNIT_PRICE_COL_WIDTH = 10; // 「単価」列の表示幅（半角換算、"\\X,XXX"を考慮）

    private static final int SUBTOTAL_LABEL_WIDTH = 10;
    private static final int SUB_TOTAL_AMOUNT_WIDTH = 10;

    private static final int TAX_DETAIL_LABEL_WIDTH = 18;
    private static final int TAX_AMOUNT_WIDTH = 10; 
    
    private static final int TOTAL_LABEL_WIDTH = 10; 
    private static final int GRAND_TOTAL_AMOUNT_WIDTH = 10; 
    // --- ここまで定数定義 ---

    // ヘルパー関数: 全角文字を2バイト、半角文字を1バイトとして計算
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

    //単品伝票印刷
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

        for (PaymentDetail detail : details) {
            Menu menu = detail.getMenu();
            if (menu == null) {
                notifyClientError(seatId, "menu_id=" + detail.getMenu() + " のメニューが存在しません");
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
            
            List<MenuPrinterMap> mappings = menuPrinterMapRepo.findByMenu_MenuId(menu.getMenuId());
            
            if (mappings.isEmpty()) {
                System.out.println("DEBUG: メニューID " + menu.getMenuId() + " に紐づくプリンタが設定されていません。印刷スキップ。");
                notifyClientError(seatId, "メニューID " + menu.getMenuId() + " に紐づくプリンタが設定されていません。");
                continue;
            }

            for (MenuPrinterMap map : mappings) {
                String ip = map.getPrinter().getPrinterIp();

                // ★★★ 修正後のXMLテンプレート組み立て部分 ★★★
                StringBuilder xmlContent = new StringBuilder();
                xmlContent.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
                xmlContent.append("<epos-print xmlns=\"http://www.epson-pos.com/schemas/2011/03/epos-print\">\n");

                // ブザー
                xmlContent.append("  <sound pattern=\"pattern_a\" repeat=\"1\"/>\n"); 
                
                // 言語設定と紙送り
                xmlContent.append("  <text lang=\"ja\"/>\n");
                xmlContent.append("  <feed unit=\"8\"/>\n");

                // テーブル名と席番号
                xmlContent.append("  <text width=\"1\" height=\"1\">テーブル: ").append(escapeXml(seatName)).append("&#10;</text>\n");
                
                // 注文者は左端に
                xmlContent.append("  <text>").append(escapeXml(username != null ? username : "不明")).append("&#10;</text>\n");
                
                // 日時は右端に（新しい行で右寄せ）
                xmlContent.append("  <text align=\"right\">").append(escapeXml(timeStr)).append("&#10;</text>\n"); 
                
                xmlContent.append("  <feed unit=\"8\"/>\n"); 
                
                // 注文商品は左端に（倍角）
                xmlContent.append("  <text dw=\"true\" dh=\"true\">").append(escapeXml(itemName)).append("&#10;</text>\n");
                
                // 点数は右端に（新しい行で右寄せ）
                // 注文商品とは別の行になるけど、スタンダードモードで両端寄せにするにはこれが一番確実な方法だよ。
                xmlContent.append("  <text align=\"right\">").append(quantity).append("点&#10;</text>\n");
                
                xmlContent.append("  <feed unit=\"8\"/>\n"); 

                // 装飾のリセット (念のため)
                xmlContent.append("  <text reverse=\"false\" ul=\"false\" em=\"false\" color=\"color_1\"/>\n");
                xmlContent.append("  <text width=\"1\" height=\"1\"/>\n");
                
                // カットは最後の一回だけ
                // 元のテンプレートに合わせて `type="reserve"` にする
                xmlContent.append("  <cut type=\"reserve\"/>\n"); 
                
                xmlContent.append("</epos-print>\n");

                sendToPrinter(ip, xmlContent.toString(), seatId);
            }
        }
    }

    
    //プリンターに送信
    private void sendToPrinter(String ip, String xmlData, Integer seatId) {
        String url = "http://" + ip + "/cgi-bin/epos/eposprint.cgi";
        
        System.out.println("--- プリンタ送信内容 ---");
        System.out.println("送信先IP: " + ip);
        System.out.println("印刷内容:\n" + xmlData);
        System.out.println("----------------------");

        try {
//            restTemplate.postForEntity(url, xmlData, String.class);    //いったんこめんとあうと
            System.out.println("送信先IP: " + ip + " へePOS-Print XMLを送信しました。");
        } catch (HttpClientErrorException e) {
            System.err.println("プリンタへの送信中にHTTPエラーが発生しました (IP: " + ip + ", ステータスコード: " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
            notifyClientError(seatId, "プリンタ通信エラー (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("プリンタへの送信中に予期せぬエラーが発生しました (IP: " + ip + "): " + e.getMessage());
            notifyClientError(seatId, "プリンタエラー: " + e.getMessage());
        }
    }

    
    // ★★★ 小計伝票印刷メソッド (printReceiptForPayment) ★★★
    // このメソッドは、注文全体のPaymentDetailリストを受け取って、小計伝票を印刷する用途
    public void printReceiptForPayment(List<PaymentDetail> detailsForReceipt, Integer seatId) {
        if (detailsForReceipt == null || detailsForReceipt.isEmpty()) {
            System.out.println("印刷するPaymentDetailがありません。小計伝票の生成をスキップします。");
            return;
        }

        // --- 伝票に必要な情報の計算 ---
        BigDecimal subtotal = BigDecimal.ZERO;
        // 税率ごとの集計をソートするためにTreeMapを使用
        // 税率のBigDecimalの比較は通常compareToを使用するため、自然順序付けでソートされる
        Map<BigDecimal, BigDecimal> taxRateToAmountMap = new TreeMap<>(); 
        Map<BigDecimal, BigDecimal> taxRateToTaxAmountMap = new TreeMap<>();

        for (PaymentDetail detail : detailsForReceipt) { // 引数のdetailsForReceiptを使用
            BigDecimal itemPrice = BigDecimal.valueOf(detail.getMenu().getPrice()); // Priceはint型を想定
            BigDecimal itemQuantity = BigDecimal.valueOf(detail.getQuantity());
            BigDecimal itemLineTotal = itemPrice.multiply(itemQuantity); // 各商品の合計金額

            subtotal = subtotal.add(itemLineTotal); // 全体の小計に加算

            // TaxRateエンティティから税率をDoubleで取得し、BigDecimalに変換
            // Double.toString()を介さず、BigDecimal.valueOf(double)で直接変換
            BigDecimal taxRateValueFromDb = BigDecimal.valueOf(detail.getTaxRate().getRate());
            
            // taxRateValueFromDb が 0.1 や 0.08 の形式なので、そのまま係数として使用
            BigDecimal taxRateFactor = taxRateValueFromDb; 
            
            // 税額計算（四捨五入）
            BigDecimal taxAmount = itemLineTotal.multiply(taxRateFactor).setScale(0, RoundingMode.HALF_UP); 
            
            // 税率ごとの対象額と税額をマップに集計
            // マップのキーは税率パーセンテージ（例: 10, 8）ではなく、DBから取得した0.1, 0.08の形式をそのまま使用
            taxRateToAmountMap.merge(taxRateValueFromDb, itemLineTotal, BigDecimal::add);
            taxRateToTaxAmountMap.merge(taxRateValueFromDb, taxAmount, BigDecimal::add);
        }

        // 全体の税額合計
        BigDecimal totalTaxOnly = taxRateToTaxAmountMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // 全体の合計額
        BigDecimal grandTotal = subtotal.add(totalTaxOnly); 

        // --- 共通情報取得 ---
        // 席名
        String seatName = seatRepo.findById(seatId)
                .map(Seat::getSeatName)
                .orElse("不明な席");

        // 現在時刻
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));

        // ユーザー名 (PaymentDetailリストが空でなければ必ず取得できるはず)
        User user = detailsForReceipt.get(0).getUser(); 
        String username = (user != null) ? user.getUserName() : "卓上端末";

        // ロゴ画像データのBase64文字列（DBから取得する想定）
        // TODO: ここでDBからロゴ画像(Base64文字列)を取得するロジックを実装
        String logoImageBase64 = "[ロゴBase64データ]"; // 例: logoRepository.findLogo().getBase64String();

        // --- XMLテンプレートの組み立て（小計伝票用） ---
        StringBuilder xmlContent = new StringBuilder();
        xmlContent.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xmlContent.append("<epos-print xmlns=\"http://www.epson-pos.com/schemas/2011/03/epos-print\">\n");

        // 言語とフォント設定
        xmlContent.append("  <text lang=\"ja\"/>\n");
        xmlContent.append("  <text font=\"font_a\"/>\n");
        xmlContent.append("  <text align=\"center\"/>\n");
        xmlContent.append("  <feed/>\n");

        // ロゴ画像
        xmlContent.append("  <image width=\"256\" height=\"60\" color=\"color_1\" mode=\"mono\">")
                  .append(logoImageBase64)
                  .append("</image>\n");
        xmlContent.append("  <feed unit=\"40\"/>\n");

        // 店舗名、電話番号
        xmlContent.append("  <text align=\"center\"/>\n");
        xmlContent.append("  <text width=\"1\" height=\"2\">ミラノファッション専門店&#10;</text>\n");
        xmlContent.append("  <text width=\"1\" height=\"1\">Tel.00-0000-0000&#10;</text>\n");
        xmlContent.append("  <feed unit=\"5\"/>\n");

        // テーブル名と時刻
        // テンプレートのスペースに合わせて整形
        // 「    テーブル:[席番号]             [時刻]」のような整形を目指す
        // 全体幅の調整が必要になる。ここではスペース文字数を仮定
        int tableAndTimeLeftPad = INDENT_SPACES + calculateEpsonPrintByteLength("テーブル:");
        int tableAndTimeRightPad = 20; // テーブル名＋席番号の右側のスペースの幅
        int timeStringPad = 15; // 時刻の右側のスペースの幅

        String tableAndTimeLine = " ".repeat(INDENT_SPACES) 
                                + padRightHalfWidth("テーブル:" + escapeXml(seatName), 
                                                     calculateEpsonPrintByteLength("テーブル:") + calculateEpsonPrintByteLength(escapeXml(seatName)) + 15) // 半角15文字分のスペースを調整
                                + escapeXml(timeStr);
        xmlContent.append("  <text align=\"left\"/>\n");
        xmlContent.append("  <text>").append(tableAndTimeLine).append("&#10;</text>\n");
        xmlContent.append("  <feed unit=\"5\"/>\n");
        xmlContent.append("  <feed/>\n");

        // 品名、数量、単価のヘッダー
        xmlContent.append("  <text align=\"left\"/>\n");
        String itemHeaderLine = " ".repeat(INDENT_SPACES) // 先頭のスペース
                                + padRightHalfWidth("品名", ITEM_NAME_COL_WIDTH)
                                + padRightHalfWidth("数量", QUANTITY_COL_WIDTH) 
                                + padLeftHalfWidth("単価", UNIT_PRICE_COL_WIDTH);
        xmlContent.append("  <text>").append(itemHeaderLine).append("&#10;</text>\n");

        // 商品のリスト（複数行対応）
        for (PaymentDetail detail : detailsForReceipt) { // 引数のdetailsForReceiptをループ
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
            String unitPriceStr = "\\" + String.format("%,d", detail.getMenu().getPrice()); // 単価にカンマと円マーク

            // 各商品の行を整形
            String itemLine = " ".repeat(INDENT_SPACES) // 先頭のスペース
                              + padRightHalfWidth(escapeXml(displayItemName), ITEM_NAME_COL_WIDTH)
                              + padRightHalfWidth(escapeXml(quantityStr), QUANTITY_COL_WIDTH) 
                              + padLeftHalfWidth(escapeXml(unitPriceStr), UNIT_PRICE_COL_WIDTH);
            xmlContent.append("  <text>").append(itemLine).append("&#10;</text>\n");
        }
        xmlContent.append("  <feed unit=\"10\"/>\n");
        xmlContent.append("  <feed/>\n");

        // 小計
        // 4スペース + "小計" + (中央の空白) + "\\[小計]"
        String subtotalLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("小計", SUBTOTAL_LABEL_WIDTH + 14) // 中央の空白調整
                               + padLeftHalfWidth("\\" + String.format("%,d", subtotal.longValue()), SUB_TOTAL_AMOUNT_WIDTH);
        xmlContent.append("  <text>").append(subtotalLine).append("&#10;</text>\n");

        // 税率ごとの表示
        // マップのキー (税率) をソートされた順で取得
        List<BigDecimal> sortedTaxRates = new ArrayList<>(taxRateToAmountMap.keySet());
        sortedTaxRates.sort(Comparator.naturalOrder()); // 税率を昇順でソート (例: 8%, 10%)

        for (BigDecimal rate : sortedTaxRates) {
            BigDecimal amountForRate = taxRateToAmountMap.get(rate); // その税率の対象額
            BigDecimal taxAmountForRate = taxRateToTaxAmountMap.get(rate); // その税率の税額

            // ([税率]%対象 [税率%対象額])
            // 4スペース + "([税率]%対象" + (中央の空白) + "[税率%対象額])"
            String taxRateLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%対象"; // 例: "(10%対象"
            String taxRateAmountLine = " ".repeat(INDENT_SPACES)
                                     + padRightHalfWidth(taxRateLabel, TAX_DETAIL_LABEL_WIDTH) 
                                     + padLeftHalfWidth("\\" + String.format("%,d", amountForRate.longValue()) + ")", TAX_AMOUNT_WIDTH + 1); // )の分+1
            xmlContent.append("  <text>").append(taxRateAmountLine).append("&#10;</text>\n");

            // 内税 [税のみの額]
            // 4スペース + "内税" + (中央の空白) + "[税のみの額]"
            String taxOnlyLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("内税", TAX_DETAIL_LABEL_WIDTH) 
                               + padLeftHalfWidth("\\" + String.format("%,d", taxAmountForRate.longValue()), TAX_AMOUNT_WIDTH);
            xmlContent.append("  <text>").append(taxOnlyLine).append("&#10;</text>\n");
            
            // ([税率]%税 [税率の額])
            // 4スペース + "([税率]%税" + (中央の空白) + "[税率の額])"
            String taxRateTaxLabel = "(" + rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%税"; // 例: "(10%税"
            String taxRateTaxLine = " ".repeat(INDENT_SPACES)
                                  + padRightHalfWidth(taxRateTaxLabel, TAX_DETAIL_LABEL_WIDTH)
                                  + padLeftHalfWidth("\\" + String.format("%,d", taxAmountForRate.longValue()) + ")", TAX_AMOUNT_WIDTH + 1); // )の分+1
            xmlContent.append("  <text>").append(taxRateTaxLine).append("&#10;</text>\n");
        }

        // 区切り線
        xmlContent.append("  <text> ").append("---------------------------------------").append("&#10;</text>\n");
        xmlContent.append("  <feed unit=\"15\"/>\n");

        // 合計
        // 4スペース + "合計" + (中央の空白) + "\\[合計額]"
        String totalLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("合計", TOTAL_LABEL_WIDTH + 8) // 中央の空白調整
                           + padLeftHalfWidth("\\" + String.format("%,d", grandTotal.longValue()), GRAND_TOTAL_AMOUNT_WIDTH);
        xmlContent.append("  <text>").append(totalLine).append("&#10;</text>\n");
        xmlContent.append("  <text dw=\"false\" dh=\"false\"/>\n"); // 倍角解除
        xmlContent.append("  <feed unit=\"10\"/>\n");

        // (内消費税等 \3,000）
        // 4スペース + "(内消費税等" + (中央の空白) + "\\[合計税額]）"
        String totalTaxOnlyLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth("(内消費税等", TAX_DETAIL_LABEL_WIDTH + 6) // ラベルと中央の空白調整
                                   + padLeftHalfWidth("\\" + String.format("%,d", totalTaxOnly.longValue()) + "）", TAX_AMOUNT_WIDTH + 1); // ）の分+1
        xmlContent.append("  <text>").append(totalTaxOnlyLine).append("&#10;</text>\n");

        // [税率]%対象 \[対象額] - 全税率の合計対象額を表示
        // テンプレートでは1行だが、計算ロジックから全体合計の税率対象額を表示する想定
        String combinedTaxableAmountLine = "";
        if (!taxRateToAmountMap.isEmpty()) {
            BigDecimal combinedTaxableAmount = taxRateToAmountMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            String totalTaxableAmountLabel = taxRateToAmountMap.size() > 1 ? "対象" : 
                                               taxRateToAmountMap.keySet().iterator().next().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%対象"; 
            totalTaxableAmountLabel = "(" + totalTaxableAmountLabel; // (対象 または (10%対象
            
            combinedTaxableAmountLine = " ".repeat(INDENT_SPACES) + padRightHalfWidth(totalTaxableAmountLabel, TAX_DETAIL_LABEL_WIDTH) // ラベル幅
                                     + padLeftHalfWidth("\\" + String.format("%,d", combinedTaxableAmount.longValue()) + ")", TAX_AMOUNT_WIDTH + 1); // 金額＋）
            xmlContent.append("  <text>").append(combinedTaxableAmountLine).append("&#10;</text>\n");
        }
        
        xmlContent.append("  <feed unit=\"35\"/>\n");
        xmlContent.append("  <feed/>\n");

        // 注釈
        xmlContent.append("  <text align=\"center\"/>\n");
        xmlContent.append("  <text>注) ※印は軽減税率適用商品</text>\n");
        xmlContent.append("  <feed/>\n");

        // カットと最終紙送り
        xmlContent.append("  <cut type=\"feed\"/>\n");
        xmlContent.append("  <feed/>\n");

        xmlContent.append("</epos-print>\n");

        // プリンターへの送信
        // 小計伝票を印刷するプリンターのIPアドレスを特定する必要がある
        // 今回は、渡されたPaymentDetailリストの最初のメニューに紐づくプリンターを使用する。
        // もし専用のレシートプリンターがあるなら、printerConfigRepoなどから取得するロジックに変更。
        Menu firstMenu = detailsForReceipt.get(0).getMenu();
        List<MenuPrinterMap> mappings = menuPrinterMapRepo.findByMenu_MenuId(firstMenu.getMenuId());
        
        if (!mappings.isEmpty()) {
            String ip = mappings.get(0).getPrinter().getPrinterIp();
            sendToPrinter(ip, xmlContent.toString(), seatId);
        } else {
            System.err.println("小計伝票を印刷するプリンタが見つかりませんでした。");
            notifyClientError(seatId, "小計伝票プリンタ設定なし。");
        }
    }


    
    
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private void notifyClientError(Integer seatId, String message) {
        Map<String, String> payload = new HashMap<>();
        payload.put("type", "PRINT_ERROR");
        payload.put("message", message);
        messagingTemplate.convertAndSend("/topic/seats/" + seatId, payload);
    }
    
    
}