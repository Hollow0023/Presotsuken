package com.order.service;

import java.time.LocalDateTime;
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
        
        // 元の会計を部分完了状態に更新 (初回のみ)
        if (!"PARTIAL".equals(originalPayment.getPaymentStatus()) && 
            !"COMPLETED".equals(originalPayment.getPaymentStatus())) {
            originalPayment.setPaymentStatus("PARTIAL");
            originalPayment.setTotalSplits(request.getNumberOfSplits());
            paymentRepository.save(originalPayment);
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
        
        // 新しい会計レコードを作成
        Payment childPayment = createChildPayment(originalPayment, request, currentAmount);
        childPayment.setSplitNumber(request.getCurrentSplit());
        childPayment.setTotalSplits(request.getNumberOfSplits());
        
        // 最後の会計の場合、全体を完了状態にする
        if (request.getCurrentSplit().equals(request.getNumberOfSplits())) {
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
     * 個別会計処理
     * 選択された商品のみを支払う
     */
    @Transactional
    public Payment processIndividualPayment(IndividualPaymentRequest request) {
        // 元の会計を取得
        Payment originalPayment = paymentRepository.findById(request.getPaymentId())
            .orElseThrow(() -> new IllegalArgumentException("元の会計が見つかりません: " + request.getPaymentId()));
        
        // 元の会計を部分完了状態に更新 (初回のみ)
        if (!"PARTIAL".equals(originalPayment.getPaymentStatus()) && 
            !"COMPLETED".equals(originalPayment.getPaymentStatus())) {
            originalPayment.setPaymentStatus("PARTIAL");
            paymentRepository.save(originalPayment);
        }
        
        // 選択された商品の金額を計算
        List<PaymentDetail> selectedDetails = request.getPaymentDetailIds().stream()
            .map(id -> paymentDetailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("商品が見つかりません: " + id)))
            .collect(Collectors.toList());
        
        // 選択商品がすでに支払い済みでないか確認
        for (PaymentDetail detail : selectedDetails) {
            if (detail.getPaidInPayment() != null) {
                throw new IllegalArgumentException("商品 " + detail.getMenu().getMenuName() + " は既に支払い済みです");
            }
        }
        
        double totalAmount = calculateTotalWithTax(selectedDetails, request.getDiscount());
        
        // 新しい会計レコードを作成
        Payment childPayment = createChildPayment(originalPayment, request, totalAmount);
        childPayment = paymentRepository.save(childPayment);
        
        // 選択された商品を支払い済みとしてマーク
        for (PaymentDetail detail : selectedDetails) {
            detail.setPaidInPayment(childPayment);
            paymentDetailRepository.save(detail);
        }
        
        // 全ての商品が支払い済みか確認
        List<PaymentDetail> allDetails = paymentDetailRepository.findByPaymentPaymentId(request.getPaymentId());
        boolean allPaid = allDetails.stream().allMatch(d -> d.getPaidInPayment() != null);
        
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
        
        // 全商品と未払い商品を取得
        List<PaymentDetail> allDetails = paymentDetailRepository.findByPaymentPaymentId(paymentId);
        List<PaymentDetail> unpaidDetails = allDetails.stream()
            .filter(d -> d.getPaidInPayment() == null)
            .collect(Collectors.toList());
        
        // 合計金額を計算
        double totalAmount = calculateTotalWithTax(allDetails, payment.getDiscount());
        double unpaidAmount = calculateTotalWithTax(unpaidDetails, 0.0);
        double paidAmount = totalAmount - unpaidAmount;
        
        result.setTotalAmount(totalAmount);
        result.setPaidAmount(paidAmount);
        result.setRemainingAmount(unpaidAmount);
        result.setIsFullyPaid(unpaidDetails.isEmpty());
        
        // 未払い商品リスト
        List<RemainingPaymentDto.PaymentDetailDto> unpaidDtoList = unpaidDetails.stream()
            .map(this::convertToPaymentDetailDto)
            .collect(Collectors.toList());
        result.setUnpaidDetails(unpaidDtoList);
        
        // 子会計リスト
        List<Payment> childPayments = paymentRepository.findAll().stream()
            .filter(p -> p.getParentPayment() != null && p.getParentPayment().getPaymentId().equals(paymentId))
            .collect(Collectors.toList());
        
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
