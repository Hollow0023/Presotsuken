// src/main/java/com/order/service/PrintService.java
package com.order.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

                // ★★★ ここを新しいXMLテンプレートに修正するよ！ ★★★
                StringBuilder xmlContent = new StringBuilder();
                xmlContent.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
                xmlContent.append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">");
                xmlContent.append("<s:Body>");
                xmlContent.append("<epos-print xmlns=\"http://www.epson-pos.com/schemas/2011/03/epos-print\">");
                xmlContent.append("<sound pattern=\"pattern_a\" repeat=\"1\"/>");
                xmlContent.append("<cut type=\"reserve\"/>");
                xmlContent.append("<text lang=\"ja\"/>");
                xmlContent.append("<feed unit=\"8\"/>");
                xmlContent.append("<text dw=\"false\" dh=\"false\"/>");
                xmlContent.append("<text> テーブル:</text>"); // スペースを維持
                xmlContent.append("<text width=\"1\" height=\"1\"/>");
                xmlContent.append("<text>  ").append(escapeXml(seatName)).append("&#10;</text>"); // スペースと改行を維持
                xmlContent.append("<text width=\"1\" height=\"1\"/>");
                xmlContent.append("<text> ").append(escapeXml(username != null ? username : "")).append("</text>"); // スペースを維持
                xmlContent.append("<text x=\"320\"/>"); // x属性を維持
                xmlContent.append("<text> ").append(escapeXml(timeStr)).append("</text>"); // スペースを維持
                xmlContent.append("<feed unit=\"8\"/>");
                xmlContent.append("<text dw=\"true\" dh=\"true\" />");
                xmlContent.append("<text> ").append(escapeXml(itemName)).append("&#10;</text>"); // スペースと改行を維持
                xmlContent.append("<text x=\"280\"/>"); // x属性を維持
                xmlContent.append("<text> ").append(quantity).append("点</text>"); // スペースを維持
                xmlContent.append("<feed unit=\"8\"/>");
                xmlContent.append("<text reverse=\"false\" ul=\"false\" em=\"false\" color=\"color_1\"/>");
                xmlContent.append("<text width=\"1\" height=\"1\"/>");
                xmlContent.append("<cut type=\"reserve\"/>");
                xmlContent.append("</epos-print>");
                xmlContent.append("</s:Body>");
                xmlContent.append("</s:Envelope>");

                sendToPrinter(ip, xmlContent.toString(), seatId);
            }
        }
    }

    private void sendToPrinter(String ip, String xmlData, Integer seatId) {
        String url = "http://" + ip + "/cgi-bin/epos/eposprint.cgi";
        
        System.out.println("--- プリンタ送信内容 ---");
        System.out.println("送信先IP: " + ip);
        System.out.println("印刷内容:\n" + xmlData);
        System.out.println("----------------------");

        try {
            // 実際のプリンタへのHTTP POST送信処理（現在はコメントアウト）
            // restTemplate.postForEntity(url, xmlData, String.class);
            System.out.println("送信先IP: " + ip + " へePOS-Print XMLを送信しました。（実際にはスキップ）");
        } catch (HttpClientErrorException e) {
            System.err.println("プリンタへの送信中にHTTPエラーが発生しました (IP: " + ip + ", ステータスコード: " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
            notifyClientError(seatId, "プリンタ通信エラー (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("プリンタへの送信中に予期せぬエラーが発生しました (IP: " + ip + "): " + e.getMessage());
            notifyClientError(seatId, "プリンtaエラー: " + e.getMessage());
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