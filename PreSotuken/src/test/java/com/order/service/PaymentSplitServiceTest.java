package com.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.dto.IndividualPaymentRequest;
import com.order.dto.RemainingPaymentDto;
import com.order.dto.SplitPaymentRequest;
import com.order.entity.*;
import com.order.repository.*;

/**
 * PaymentSplitServiceのテスト
 */
@ExtendWith(MockitoExtension.class)
class PaymentSplitServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentDetailRepository paymentDetailRepository;
    
    @Mock
    private PaymentTypeRepository paymentTypeRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private VisitRepository visitRepository;
    
    @InjectMocks
    private PaymentSplitService paymentSplitService;
    
    private Payment originalPayment;
    private Visit visit;
    private Store store;
    private List<PaymentDetail> paymentDetails;
    
    @BeforeEach
    void setUp() {
        // テストデータのセットアップ
        store = new Store();
        store.setStoreId(1);
        
        Seat seat = new Seat();
        seat.setSeatId(1);
        seat.setSeatName("テーブル1");
        
        visit = new Visit();
        visit.setVisitId(1);
        visit.setStore(store);
        visit.setSeat(seat);
        visit.setVisitTime(LocalDateTime.now());
        
        originalPayment = new Payment();
        originalPayment.setPaymentId(1);
        originalPayment.setStore(store);
        originalPayment.setVisit(visit);
        originalPayment.setTotal(3300.0);
        originalPayment.setDiscount(0.0);
        
        // メニューと税率の設定
        Menu menu1 = new Menu();
        menu1.setMenuId(1);
        menu1.setMenuName("ラーメン");
        menu1.setPrice(1000.0);
        
        TaxRate taxRate = new TaxRate();
        taxRate.setTaxRateId(1);
        taxRate.setRate(0.10);
        
        PaymentDetail detail1 = new PaymentDetail();
        detail1.setPaymentDetailId(1);
        detail1.setPayment(originalPayment);
        detail1.setMenu(menu1);
        detail1.setQuantity(3);
        detail1.setSubtotal(3000.0);
        detail1.setTaxRate(taxRate);
        
        paymentDetails = Arrays.asList(detail1);
    }
    
    @Test
    void 割り勘会計_3人で割る_1人目() {
        // Given
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            if (p.getPaymentId() == null) {
                // 新しい子会計にIDを付与
                p.setPaymentId(100);
            }
            return p;
        });
        
        // When
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // Then
        assertNotNull(result);
        assertEquals(1100.0, result.getTotal(), 0.01); // 3300 / 3 = 1100 (切り捨て)
        assertEquals(1, result.getSplitNumber());
        assertEquals(3, result.getTotalSplits());
        assertEquals("PARTIAL", result.getPaymentStatus());
        
        // 元の会計がPARTIAL状態になっているか確認 (saveが呼ばれて更新される)
        verify(paymentRepository, atLeast(1)).save(any(Payment.class));
    }
    
    @Test
    void 割り勘会計_3人で割る_3人目_余りを含める() {
        // Given
        originalPayment.setPaymentStatus("PARTIAL");
        originalPayment.setTotalSplits(3);
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(3); // 最後
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // When
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // Then
        assertNotNull(result);
        // 3300 - (1100 * 2) = 1100 (余りを含む)
        assertEquals(1100.0, result.getTotal(), 0.01);
        assertEquals(3, result.getSplitNumber());
        assertEquals("COMPLETED", result.getPaymentStatus());
        
        // 元の会計がCOMPLETED状態になっているか確認
        assertEquals("COMPLETED", originalPayment.getPaymentStatus());
        
        // Visitの退店時刻が記録されているか確認
        verify(visitRepository).save(any(Visit.class));
    }
    
    @Test
    void 割り勘会計_余りが出る場合_最後に含める() {
        // Given
        originalPayment.setTotal(1000.0); // 1000円を3人で割る
        
        PaymentDetail detail = paymentDetails.get(0);
        detail.setSubtotal(909.09); // 税抜き 909.09円 → 税込み 1000円
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(3); // 最後
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // When
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // Then
        // 1000 / 3 = 333.33... → 1人目と2人目は333円、3人目は334円
        // 1000 - (333 * 2) = 334
        assertEquals(334.0, result.getTotal(), 0.01);
    }
    
    @Test
    void 個別会計_一部の商品を支払い() {
        // Given
        Menu menu2 = new Menu();
        menu2.setMenuId(2);
        menu2.setMenuName("餃子");
        menu2.setPrice(500.0);
        
        TaxRate taxRate = new TaxRate();
        taxRate.setTaxRateId(1);
        taxRate.setRate(0.10);
        
        PaymentDetail detail2 = new PaymentDetail();
        detail2.setPaymentDetailId(2);
        detail2.setPayment(originalPayment);
        detail2.setMenu(menu2);
        detail2.setQuantity(1);
        detail2.setSubtotal(500.0);
        detail2.setTaxRate(taxRate);
        
        List<PaymentDetail> allDetails = Arrays.asList(paymentDetails.get(0), detail2);
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        request.setPaymentDetailIds(Arrays.asList(2)); // 餃子のみ支払い
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findById(2)).thenReturn(Optional.of(detail2));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(allDetails);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(paymentDetailRepository.save(any(PaymentDetail.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // When
        Payment result = paymentSplitService.processIndividualPayment(request);
        
        // Then
        assertNotNull(result);
        assertEquals(550.0, result.getTotal(), 0.01); // 500 * 1.1 = 550
        assertEquals("PARTIAL", result.getPaymentStatus()); // まだラーメンが未払い
        
        // 餃子が支払い済みとしてマークされているか確認
        assertNotNull(detail2.getPaidInPayment());
    }
    
    @Test
    void 個別会計_全商品支払い後に完了状態になる() {
        // Given
        paymentDetails.get(0).setPaidInPayment(null); // 未払い
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        request.setPaymentDetailIds(Arrays.asList(1)); // 全商品を支払い
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findById(1)).thenReturn(Optional.of(paymentDetails.get(0)));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(paymentDetailRepository.save(any(PaymentDetail.class))).thenAnswer(i -> {
            PaymentDetail saved = (PaymentDetail) i.getArguments()[0];
            saved.setPaidInPayment(new Payment()); // 支払い済みとしてマーク
            return saved;
        });
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // When
        Payment result = paymentSplitService.processIndividualPayment(request);
        
        // Then
        assertEquals("COMPLETED", result.getPaymentStatus());
        
        // Visitの退店時刻が記録されているか確認
        verify(visitRepository).save(any(Visit.class));
    }
    
    @Test
    void 残り会計情報取得_未払い商品あり() {
        // Given
        Menu menu2 = new Menu();
        menu2.setMenuId(2);
        menu2.setMenuName("餃子");
        menu2.setPrice(500.0);
        
        TaxRate taxRate = new TaxRate();
        taxRate.setTaxRateId(1);
        taxRate.setRate(0.10);
        
        PaymentDetail detail2 = new PaymentDetail();
        detail2.setPaymentDetailId(2);
        detail2.setPayment(originalPayment);
        detail2.setMenu(menu2);
        detail2.setQuantity(1);
        detail2.setSubtotal(500.0);
        detail2.setTaxRate(taxRate);
        detail2.setPaidInPayment(new Payment()); // すでに支払い済み
        
        List<PaymentDetail> allDetails = Arrays.asList(paymentDetails.get(0), detail2);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(allDetails);
        when(paymentRepository.findAll()).thenReturn(Arrays.asList());
        
        // When
        RemainingPaymentDto result = paymentSplitService.getRemainingPayment(1);
        
        // Then
        assertNotNull(result);
        assertEquals(3850.0, result.getTotalAmount(), 0.01); // (3000 + 500) * 1.1
        assertEquals(550.0, result.getPaidAmount(), 0.01); // 餃子分
        assertEquals(3300.0, result.getRemainingAmount(), 0.01); // ラーメン分
        assertFalse(result.getIsFullyPaid());
        assertEquals(1, result.getUnpaidDetails().size());
        assertEquals("ラーメン", result.getUnpaidDetails().get(0).getMenuName());
    }
    
    @Test
    void 既に支払い済みの商品を再度支払おうとするとエラー() {
        // Given
        Payment paidPayment = new Payment();
        paidPayment.setPaymentId(999);
        paymentDetails.get(0).setPaidInPayment(paidPayment); // すでに支払い済み
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        request.setPaymentDetailIds(Arrays.asList(1));
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findById(1)).thenReturn(Optional.of(paymentDetails.get(0)));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            paymentSplitService.processIndividualPayment(request);
        });
    }
}
