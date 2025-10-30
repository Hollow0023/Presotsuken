package com.order.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.order.dto.IndividualPaymentRequest;
import com.order.dto.RemainingPaymentDto;
import com.order.dto.SplitPaymentRequest;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.PaymentType;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;

import lombok.RequiredArgsConstructor;

/**
 * 個別会計・割り勘サービス
 */
@Service
@RequiredArgsConstructor
public class PaymentSplitService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final VisitRepository visitRepository;
    
    /**
     * 割り勘会計処理
     * 合計金額を人数で割り、余りは最後の会計に含める
     */
    @Transactional
    public Payment processSplitPayment(SplitPaymentRequest request) {
        // 元の会計を取得
        Payment originalPayment = paymentRepository.findById(request.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("元の会計が見つかりません: " + request.getPaymentId()));
        
        // 既に個別会計が開始されていないかチェック
        // 個別会計の場合: paymentStatusがPARTIALで、totalSplitsがnull（または未設定）
        if ("PARTIAL".equals(originalPayment.getPaymentStatus())) {
            Integer totalSplits = originalPayment.getTotalSplits();
            if (totalSplits == null || totalSplits <= 0) {
                // totalSplitsが設定されていない場合は個別会計が進行中
                throw new IllegalArgumentException("個別会計が進行中のため、割り勘会計を開始できません。");
            }
        }
        
        // 合計金額を計算
        List<PaymentDetail> details = paymentDetailRepository.findByPaymentPaymentId(request.getPaymentId());
        double totalAmount = calculateTotalWithTax(details, originalPayment.getDiscount());
        
        // 1人あたりの金額を計算 (切り捨て)
        double amountPerPerson = Math.floor(totalAmount / request.getNumberOfSplits());
        
        // 今回の会計金額を決定 (最後の会計は余りを含める)
        double currentAmount;
        if (request.getCurrentSplit().equals(request.getNumberOfSplits())) {
            // 最後の会計: 余りを含める
            double alreadyPaid = amountPerPerson * (request.getNumberOfSplits() - 1);
            currentAmount = totalAmount - alreadyPaid;
        } else {
            currentAmount = amountPerPerson;
        }
        
        // 預かり金額の検証
        if (request.getDeposit() != null && request.getDeposit() < currentAmount) {
            throw new IllegalArgumentException("預かり金額が不足しています。必要額: " + currentAmount + "円、預かり: " + request.getDeposit() + "円");
        }
        
        // 既に支払い済みの分割回数を確認
        List<Payment> existingChildPayments = paymentRepository.findByParentPaymentPaymentId(originalPayment.getPaymentId())
            .stream()
            .filter(p -> p.getSplitNumber() != null)
            .collect(Collectors.toList());
        
        long paidCount = existingChildPayments.size();
        
        // 期待される分割番号と一致するか確認（厳密な順序チェック）
        if (paidCount + 1 != request.getCurrentSplit()) {
            throw new IllegalArgumentException("会計の順序が正しくありません。次の会計は " + (paidCount + 1) + " 人目です（現在" + paidCount + "人分支払い済み）");
        }
        
        // 既に合計額以上支払われていないか確認
        double alreadyPaidTotal = existingChildPayments.stream()
            .mapToDouble(p -> p.getTotal() != null ? p.getTotal() : 0.0)
            .sum();
        
        if (alreadyPaidTotal + currentAmount > totalAmount + 0.01) { // 浮動小数点の誤差を考慮
            throw new IllegalArgumentException("合計支払額が元の会計額を超えています");
        }
        
        // 元の会計を部分完了状態に更新 (初回のみ)
        if (!"PARTIAL".equals(originalPayment.getPaymentStatus())) {
            originalPayment.setPaymentStatus("PARTIAL");
            originalPayment.setTotalSplits(request.getNumberOfSplits());
            paymentRepository.save(originalPayment);
        }
        
        // 新しい会計レコードを作成
        Payment childPayment = createChildPayment(originalPayment, request, currentAmount);
        childPayment.setSplitNumber(request.getCurrentSplit());
        childPayment.setTotalSplits(request.getNumberOfSplits());
        
        
        // 最後の会計の場合、全体を完了状態にする
        if (request.getCurrentSplit().equals(request.getNumberOfSplits())) {
            childPayment.setPaymentStatus("COMPLETED");
            Payment saved = paymentRepository.save(childPayment);
            
            // 全ての子会計を取得して親会計の情報を集計
            List<Payment> allChildPayments = paymentRepository.findByParentPaymentPaymentId(originalPayment.getPaymentId());
            
            // 各フィールドを集計
            double aggregatedSubtotal = allChildPayments.stream()
                .mapToDouble(p -> p.getSubtotal() != null ? p.getSubtotal() : 0.0)
                .sum();
            double aggregatedTotal = allChildPayments.stream()
                .mapToDouble(p -> p.getTotal() != null ? p.getTotal() : 0.0)
                .sum();
            double aggregatedDeposit = allChildPayments.stream()
                .mapToDouble(p -> p.getDeposit() != null ? p.getDeposit() : 0.0)
                .sum();
            double aggregatedDiscount = allChildPayments.stream()
                .mapToDouble(p -> p.getDiscount() != null ? p.getDiscount() : 0.0)
                .sum();
            
            // 親会計の情報を更新
            originalPayment.setPaymentStatus("COMPLETED");
            originalPayment.setPaymentTime(request.getPaymentTime()); // 最後の会計時刻を設定
            originalPayment.setSubtotal(aggregatedSubtotal);
            originalPayment.setTotal(aggregatedTotal);
            originalPayment.setDeposit(aggregatedDeposit);
            originalPayment.setDiscount(aggregatedDiscount);
            originalPayment.setTotalSplits(request.getNumberOfSplits()); // 割り勘の総分割数を確実に設定
            // 最後の子会計の担当者を親会計の担当者として設定
            if (request.getCashierId() != null) {
                User cashier = userRepository.findById(request.getCashierId()).orElse(null);
                originalPayment.setCashier(cashier);
            }
            paymentRepository.save(originalPayment);
            
            // Visit の退店時刻を記録
            Visit visit = originalPayment.getVisit();
            if (visit.getLeaveTime() == null) {
                visit.setLeaveTime(request.getPaymentTime());
                visitRepository.save(visit);
            }
            
            return saved;
        } else {
            childPayment.setPaymentStatus("PARTIAL");
            return paymentRepository.save(childPayment);
        }
    }
    
    /**
     * 個別会計処理
     * 選択された商品と数量を指定して支払う
     * 元の PaymentDetail から数量を減らし、新しい Payment に新しい PaymentDetail を作成する
     */
    @Transactional
    public Payment processIndividualPayment(IndividualPaymentRequest request) {
        // 元の会計を取得
        Payment originalPayment = paymentRepository.findById(request.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("元の会計が見つかりません: " + request.getPaymentId()));
        
        // 既に割り勘会計が開始されていないかチェック
        if ("PARTIAL".equals(originalPayment.getPaymentStatus()) && 
            originalPayment.getTotalSplits() != null && originalPayment.getTotalSplits() > 0) {
            // PARTIALでtotalSplitsが設定されている場合、割り勘会計が進行中
            throw new IllegalArgumentException("割り勘会計が進行中のため、個別会計を開始できません。");
        }
        
        // 元の会計を部分完了状態に更新 (初回のみ)
        if (!"PARTIAL".equals(originalPayment.getPaymentStatus())) {
            originalPayment.setPaymentStatus("PARTIAL");
            paymentRepository.save(originalPayment);
        }
        
        // 選択された商品と数量を処理
        List<PaymentDetail> newDetails = new ArrayList<>();
        double totalAmount = 0.0;
        
        for (IndividualPaymentRequest.ItemSelection item : request.getItems()) {
            PaymentDetail originalDetail = paymentDetailRepository.findById(item.getPaymentDetailId())
                .orElseThrow(() -> new IllegalArgumentException("商品が見つかりません: " + item.getPaymentDetailId()));
            
            // 数量の検証
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("数量は1以上を指定してください");
            }
            
            if (item.getQuantity() > originalDetail.getQuantity()) {
                throw new IllegalArgumentException("商品 " + originalDetail.getMenu().getMenuName() + 
                    " の数量が不足しています。残り: " + originalDetail.getQuantity() + "個");
            }
            
            // 単価を計算（元の小計 / 元の数量）
            double unitPrice = originalDetail.getSubtotal() / originalDetail.getQuantity();
            double itemSubtotal = unitPrice * item.getQuantity();
            
            // 税率を取得
            double taxRate = originalDetail.getTaxRate() != null ? originalDetail.getTaxRate().getRate() : 0;
            
            // 税込み金額を計算
            double itemTotalWithTax = itemSubtotal * (1 + taxRate);
            totalAmount += itemTotalWithTax;
            
            // 元の PaymentDetail の数量を減らす
            originalDetail.setQuantity(originalDetail.getQuantity() - item.getQuantity());
            originalDetail.setSubtotal(unitPrice * originalDetail.getQuantity());
            
            if (originalDetail.getQuantity() == 0) {
                // 数量が0になった場合は削除
                paymentDetailRepository.delete(originalDetail);
            } else {
                paymentDetailRepository.save(originalDetail);
            }
            
            // 新しい PaymentDetail を作成（後で新しい Payment に紐付ける）
            PaymentDetail newDetail = new PaymentDetail();
            newDetail.setStore(originalDetail.getStore());
            newDetail.setMenu(originalDetail.getMenu());
            newDetail.setQuantity(item.getQuantity());
            newDetail.setSubtotal(itemSubtotal);
            newDetail.setUser(originalDetail.getUser());
            newDetail.setTaxRate(originalDetail.getTaxRate());
            newDetail.setOrderTime(originalDetail.getOrderTime());
            newDetail.setDiscount(0.0); // 個別商品の割引は0
            
            newDetails.add(newDetail);
        }
        
        // 割引を適用
        if (request.getDiscount() != null && request.getDiscount() > 0) {
            totalAmount -= request.getDiscount();
        }
        
        totalAmount = Math.max(0, totalAmount); // 負にならないようにする
        
        // 預かり金額の検証
        if (request.getDeposit() != null && request.getDeposit() < totalAmount) {
            throw new IllegalArgumentException("預かり金額が不足しています。必要額: " + totalAmount + "円、預かり: " + request.getDeposit() + "円");
        }
        
        // 新しい会計レコードを作成
        Payment childPayment = new Payment();
        childPayment.setStore(originalPayment.getStore());
        childPayment.setVisit(originalPayment.getVisit());
        childPayment.setParentPayment(originalPayment);
        childPayment.setPaymentTime(request.getPaymentTime());
        childPayment.setTotal(totalAmount);
        childPayment.setSubtotal(totalAmount); // 簡略化
        childPayment.setDiscount(request.getDiscount());
        childPayment.setDeposit(request.getDeposit());
        
        if (request.getPaymentTypeId() != null) {
            PaymentType type = paymentTypeRepository.findById(request.getPaymentTypeId()).orElse(null);
            childPayment.setPaymentType(type);
        }
        
        if (request.getCashierId() != null) {
            User cashier = userRepository.findById(request.getCashierId()).orElse(null);
            childPayment.setCashier(cashier);
        }
        
        childPayment = paymentRepository.save(childPayment);
        
        // 新しい PaymentDetail を新しい Payment に紐付けて保存
        for (PaymentDetail newDetail : newDetails) {
            newDetail.setPayment(childPayment);
            paymentDetailRepository.save(newDetail);
        }
        
        // 全ての商品が支払い済みか確認（元の Payment の PaymentDetail が全て削除または数量0）
        List<PaymentDetail> remainingDetails = paymentDetailRepository.findByPaymentPaymentId(request.getPaymentId());
        boolean allPaid = remainingDetails.isEmpty() || 
            remainingDetails.stream().allMatch(d -> d.getQuantity() == 0);
        
        if (allPaid) {
            // 全て支払い済みの場合、元の会計を完了状態にする
            childPayment.setPaymentStatus("COMPLETED");
            Payment saved = paymentRepository.save(childPayment);
            
            originalPayment.setPaymentStatus("COMPLETED");
            paymentRepository.save(originalPayment);
            
            // Visit の退店時刻を記録
            Visit visit = originalPayment.getVisit();
            if (visit.getLeaveTime() == null) {
                visit.setLeaveTime(request.getPaymentTime());
                visitRepository.save(visit);
            }
            
            return saved;
        } else {
            childPayment.setPaymentStatus("PARTIAL");
            return paymentRepository.save(childPayment);
        }
    }
    
    /**
     * 残りの会計情報を取得
     */
    public RemainingPaymentDto getRemainingPayment(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("会計が見つかりません: " + paymentId));
        
        RemainingPaymentDto result = new RemainingPaymentDto();
        result.setPaymentId(paymentId);
        
        // 元の会計に残っている商品を取得（個別会計で数量が減った後の状態）
        List<PaymentDetail> remainingDetails = paymentDetailRepository.findByPaymentPaymentId(paymentId);
        
        // 子会計の合計金額を計算
        List<Payment> childPayments = paymentRepository.findByParentPaymentPaymentId(paymentId);
        
        double paidAmount = childPayments.stream()
            .mapToDouble(p -> p.getTotal() != null ? p.getTotal() : 0.0)
            .sum();
        
        // 残りの商品の金額を計算
        double unpaidAmount = calculateTotalWithTax(remainingDetails, 0.0);
        
        // 元の会計の合計金額（支払い済み + 未払い）
        double totalAmount = paidAmount + unpaidAmount;
        
        result.setTotalAmount(totalAmount);
        result.setPaidAmount(paidAmount);
        result.setRemainingAmount(unpaidAmount);
        result.setIsFullyPaid(remainingDetails.isEmpty() || 
            remainingDetails.stream().allMatch(d -> d.getQuantity() == 0));
        
        // 未払い商品リスト
        List<RemainingPaymentDto.PaymentDetailDto> unpaidDtoList = remainingDetails.stream()
            .filter(d -> d.getQuantity() > 0)
            .map(this::convertToPaymentDetailDto)
            .collect(Collectors.toList());
        result.setUnpaidDetails(unpaidDtoList);
        
        // 子会計リスト
        List<RemainingPaymentDto.ChildPaymentDto> childDtoList = childPayments.stream()
            .map(this::convertToChildPaymentDto)
            .collect(Collectors.toList());
        result.setChildPayments(childDtoList);
        
        return result;
    }
    
    /**
     * 税込み合計金額を計算
     */
    private double calculateTotalWithTax(List<PaymentDetail> details, Double discount) {
        double total = details.stream()
            .mapToDouble(d -> {
                double base = d.getSubtotal() != null ? d.getSubtotal() : 0;
                double detailDiscount = d.getDiscount() != null ? d.getDiscount() : 0;
                double netSubtotalWithoutTax = Math.max(base - detailDiscount, 0);
                double taxRate = d.getTaxRate() != null ? d.getTaxRate().getRate() : 0;
                return netSubtotalWithoutTax * (1 + taxRate);
            })
            .sum();
        
        if (discount != null && discount > 0) {
            total -= discount;
        }
        
        return Math.max(total, 0);
    }
    
    /**
     * 子会計を作成
     */
    private Payment createChildPayment(Payment originalPayment, Object request, double amount) {
        Payment child = new Payment();
        child.setStore(originalPayment.getStore());
        child.setVisit(originalPayment.getVisit());
        child.setParentPayment(originalPayment);
        child.setTotal(amount);
        child.setSubtotal(amount); // 簡略化: 実際は税抜き計算が必要
        
        if (request instanceof SplitPaymentRequest) {
            SplitPaymentRequest req = (SplitPaymentRequest) request;
            child.setPaymentTime(req.getPaymentTime());
            child.setDeposit(req.getDeposit());
            
            if (req.getPaymentTypeId() != null) {
                PaymentType type = paymentTypeRepository.findById(req.getPaymentTypeId()).orElse(null);
                child.setPaymentType(type);
            }
            
            if (req.getCashierId() != null) {
                User cashier = userRepository.findById(req.getCashierId()).orElse(null);
                child.setCashier(cashier);
            }
        } else if (request instanceof IndividualPaymentRequest) {
            IndividualPaymentRequest req = (IndividualPaymentRequest) request;
            child.setPaymentTime(req.getPaymentTime());
            child.setDeposit(req.getDeposit());
            child.setDiscount(req.getDiscount());
            
            if (req.getPaymentTypeId() != null) {
                PaymentType type = paymentTypeRepository.findById(req.getPaymentTypeId()).orElse(null);
                child.setPaymentType(type);
            }
            
            if (req.getCashierId() != null) {
                User cashier = userRepository.findById(req.getCashierId()).orElse(null);
                child.setCashier(cashier);
            }
        }
        
        return child;
    }
    
    /**
     * PaymentDetail を DTO に変換
     */
    private RemainingPaymentDto.PaymentDetailDto convertToPaymentDetailDto(PaymentDetail detail) {
        RemainingPaymentDto.PaymentDetailDto dto = new RemainingPaymentDto.PaymentDetailDto();
        dto.setPaymentDetailId(detail.getPaymentDetailId());
        dto.setMenuName(detail.getMenu().getMenuName());
        dto.setQuantity(detail.getQuantity());
        dto.setPrice(detail.getMenu().getPrice());
        
        double taxRate = detail.getTaxRate() != null ? detail.getTaxRate().getRate() : 0;
        dto.setTaxRate(taxRate);
        
        double subtotal = detail.getSubtotal() != null ? detail.getSubtotal() : 0;
        double detailDiscount = detail.getDiscount() != null ? detail.getDiscount() : 0;
        dto.setSubtotal(subtotal);
        dto.setDiscount(detailDiscount);
        
        double netSubtotal = Math.max(subtotal - detailDiscount, 0);
        double totalWithTax = netSubtotal * (1 + taxRate);
        dto.setTotalWithTax(totalWithTax);
        
        return dto;
    }
    
    /**
     * 子会計を DTO に変換
     */
    private RemainingPaymentDto.ChildPaymentDto convertToChildPaymentDto(Payment payment) {
        RemainingPaymentDto.ChildPaymentDto dto = new RemainingPaymentDto.ChildPaymentDto();
        dto.setPaymentId(payment.getPaymentId());
        dto.setSplitNumber(payment.getSplitNumber());
        dto.setAmount(payment.getTotal());
        
        if (payment.getPaymentType() != null) {
            dto.setPaymentTypeName(payment.getPaymentType().getTypeName());
        }
        
        if (payment.getCashier() != null) {
            dto.setCashierName(payment.getCashier().getUserName());
        }
        
        return dto;
    }
}
