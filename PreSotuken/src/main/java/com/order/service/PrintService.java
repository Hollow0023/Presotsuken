package com.order.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.order.entity.Menu;
import com.order.entity.MenuPrinterMap;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentDetailOption;
import com.order.entity.PrinterConfig;
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
    private final SimpMessagingTemplate messagingTemplate; // WebSocket通知用

    public void printLabelsForOrder(List<PaymentDetail> details, Integer seatId) {
        Map<String, List<String>> printerLabelMap = new HashMap<>();
        List<String> allLabels = new ArrayList<>();

        String seatName = seatRepo.findById(seatId)
                .map(Seat::getSeatName)
                .orElse("不明な席");

        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

        User user = details.stream()
                .map(PaymentDetail::getUser)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        String username = (user != null) ? user.getUserName() : null;

        int total = 0;

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

            String label = baseLabel + optionSuffix;
            int subtotal = detail.getSubtotal().intValue();
            total += subtotal;
            allLabels.add(label);

            List<MenuPrinterMap> mappings = menuPrinterMapRepo.findByMenu_MenuId(menu.getMenuId());
            for (MenuPrinterMap map : mappings) {
                String ip = map.getPrinter().getPrinterIp();
                printerLabelMap.computeIfAbsent(ip, k -> new ArrayList<>()).add(label);
            }
        }

        // 商品ごとの印刷（各プリンタ）
        for (Map.Entry<String, List<String>> entry : printerLabelMap.entrySet()) {
            String ip = entry.getKey();
            List<String> labels = entry.getValue();

            for (String label : labels) {
                StringBuilder sb = new StringBuilder();
                sb.append("【テーブル】").append(seatName).append("\n");
                if (username != null) {
                    sb.append("【注文者】").append(username).append("\n");
                }
                sb.append("【注文時間】").append(timeStr).append("\n\n");
                sb.append(label).append("\n\n\n");

                sendToPrinter(ip, sb.toString());
            }
        }

        // 合計レシート出力: printer_configテーブルからreceipt_output = true のプリンタを検索
        List<PrinterConfig> receiptPrinters = printerConfigRepo.findByReceiptOutputTrue();
        for (PrinterConfig printer : receiptPrinters) {
            String ip = printer.getPrinterIp();
            StringBuilder totalSb = new StringBuilder();
            totalSb.append("【テーブル】").append(seatName).append("\n");
            if (username != null) {
                totalSb.append("【注文者】").append(username).append("\n");
            }
            totalSb.append("【注文時間】").append(timeStr).append("\n\n");
            for (String label : allLabels) {
                totalSb.append(label).append("\n");
            }
            totalSb.append("==============================\n");
            totalSb.append("合計金額: ").append(total).append("円\n");
            totalSb.append("==============================\n");
            sendToPrinter(ip, totalSb.toString());
        }
    }

    private void sendToPrinter(String ip, String text) {
        // TODO: ePOS-Print対応HTTP POST送信処理をここに実装する
        System.out.println("送信先IP: " + ip);
        System.out.println("印刷内容:\n" + text);
    }

    private void notifyClientError(Integer seatId, String message) {
        messagingTemplate.convertAndSend("/topic/seats/" + seatId,
                Map.of("type", "PRINT_ERROR", "message", message));
    }
}
