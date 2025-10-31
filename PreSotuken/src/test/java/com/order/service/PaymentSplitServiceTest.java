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
import com.order.entity.Menu;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.Seat;
import com.order.entity.Store;
import com.order.entity.TaxRate;
import com.order.entity.User;
import com.order.entity.Visit;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;

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
        request.setDeposit(1100.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Arrays.asList()); // 子会計なし
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
        
        // 割り勘会計では PaymentDetail を分割しないため、paymentDetailRepository.save は呼ばれない
        verify(paymentDetailRepository, never()).save(any(PaymentDetail.class));
    }
    
    @Test
    void 割り勘会計_3人で割る_3人目_余りを含める() {
        // Given
        originalPayment.setPaymentStatus("PARTIAL");
        originalPayment.setTotalSplits(3);
        
        // 既に2人分支払い済みの子会計を作成
        Payment child1 = new Payment();
        child1.setPaymentId(101);
        child1.setParentPayment(originalPayment);
        child1.setTotal(1100.0);
        child1.setSubtotal(1100.0);
        child1.setDeposit(1200.0);
        child1.setDiscount(0.0);
        child1.setSplitNumber(1);
        
        Payment child2 = new Payment();
        child2.setPaymentId(102);
        child2.setParentPayment(originalPayment);
        child2.setTotal(1100.0);
        child2.setSubtotal(1100.0);
        child2.setDeposit(1100.0);
        child2.setDiscount(0.0);
        child2.setSplitNumber(2);
        
        // 3人目の子会計（これから作成される）
        Payment child3 = new Payment();
        child3.setPaymentId(103);
        child3.setParentPayment(originalPayment);
        child3.setTotal(1100.0);
        child3.setSubtotal(1100.0);
        child3.setDeposit(1100.0);
        child3.setDiscount(0.0);
        child3.setSplitNumber(3);
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(3); // 最後
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(1101.0); // 浮動小数点誤差を考慮して少し多めに設定
        request.setCashierId(1); // 担当者を設定
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        // 最初は2人分、保存後に3人分全てを返す
        when(paymentRepository.findByParentPaymentPaymentId(1))
            .thenReturn(Arrays.asList(child1, child2))
            .thenReturn(Arrays.asList(child1, child2, child3));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            // 新しい子会計を保存する時に、IDを設定
            if (p.getSplitNumber() != null && p.getSplitNumber() == 3 && p.getPaymentId() == null) {
                p.setPaymentId(103);
            }
            return p;
        });
        when(visitRepository.save(any(Visit.class))).thenAnswer(i -> i.getArguments()[0]);
        when(userRepository.findById(1)).thenReturn(Optional.of(new User())); // 担当者を返す
        
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
        
        // 親会計の集計値が正しく設定されているか確認
        assertEquals(3300.0, originalPayment.getTotal(), 0.01); // 1100 * 3
        assertEquals(3300.0, originalPayment.getSubtotal(), 0.01); // 1100 * 3
        assertEquals(3400.0, originalPayment.getDeposit(), 0.01); // 1200 + 1100 + 1100
        assertEquals(0.0, originalPayment.getDiscount(), 0.01);
        assertNotNull(originalPayment.getPaymentTime()); // 会計時刻が設定されている
        assertNotNull(originalPayment.getCashier()); // 担当者が設定されている
        
        // Visitの退店時刻が記録されているか確認
        verify(visitRepository).save(any(Visit.class));
    }
    
    @Test
    void 割り勘会計_余りが出る場合_最後に含める() {
        // Given
        originalPayment.setTotal(1000.0); // 1000円を3人で割る
        originalPayment.setPaymentStatus("PARTIAL");
        originalPayment.setTotalSplits(3);
        
        PaymentDetail detail = paymentDetails.get(0);
        detail.setSubtotal(909.09); // 税抜き 909.09円 → 税込み 1000円
        
        // 既に2人分支払い済みの子会計を作成
        Payment child1 = new Payment();
        child1.setPaymentId(101);
        child1.setParentPayment(originalPayment);
        child1.setTotal(333.0);
        child1.setSplitNumber(1);
        
        Payment child2 = new Payment();
        child2.setPaymentId(102);
        child2.setParentPayment(originalPayment);
        child2.setTotal(333.0);
        child2.setSplitNumber(2);
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3);
        request.setCurrentSplit(3); // 最後
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(334.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Arrays.asList(child1, child2));
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
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        
        IndividualPaymentRequest.ItemSelection item = new IndividualPaymentRequest.ItemSelection();
        item.setPaymentDetailId(2);
        item.setQuantity(1);
        request.setItems(Arrays.asList(item));
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(600.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findById(2)).thenReturn(Optional.of(detail2));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList(paymentDetails.get(0)));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            if (p.getPaymentId() == null) {
                p.setPaymentId(100);
            }
            return p;
        });
        when(paymentDetailRepository.save(any(PaymentDetail.class))).thenAnswer(i -> i.getArguments()[0]);
        
        // When
        Payment result = paymentSplitService.processIndividualPayment(request);
        
        // Then
        assertNotNull(result);
        assertEquals(550.0, result.getTotal(), 0.01); // 500 * 1.1 = 550
        assertEquals("PARTIAL", result.getPaymentStatus()); // まだラーメンが未払い
        
        // 元のdetail2の数量が0になっているか確認
        verify(paymentDetailRepository).delete(detail2);
    }
    
    @Test
    void 個別会計_全商品支払い後に完了状態になる() {
        // Given
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        
        IndividualPaymentRequest.ItemSelection item = new IndividualPaymentRequest.ItemSelection();
        item.setPaymentDetailId(1);
        item.setQuantity(3); // 全数量
        request.setItems(Arrays.asList(item));
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(3500.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findById(1)).thenReturn(Optional.of(paymentDetails.get(0)));
        when(paymentDetailRepository.findByPaymentPaymentId(1))
            .thenReturn(Arrays.asList(paymentDetails.get(0)))
            .thenReturn(Arrays.asList()); // 2回目の呼び出しでは空リストを返す（全て削除済み）
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            if (p.getPaymentId() == null) {
                p.setPaymentId(100);
            }
            return p;
        });
        when(paymentDetailRepository.save(any(PaymentDetail.class))).thenAnswer(i -> i.getArguments()[0]);
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
        
        // 子会計を作成（餃子分）
        Payment childPayment = new Payment();
        childPayment.setPaymentId(100);
        childPayment.setParentPayment(originalPayment);
        childPayment.setTotal(550.0);
        
        List<PaymentDetail> remainingDetails = Arrays.asList(paymentDetails.get(0)); // ラーメンのみ残っている
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(remainingDetails);
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Arrays.asList(childPayment));
        
        // When
        RemainingPaymentDto result = paymentSplitService.getRemainingPayment(1);
        
        // Then
        assertNotNull(result);
        assertEquals(3850.0, result.getTotalAmount(), 0.01); // (3000) * 1.1 + 550 = 3300 + 550
        assertEquals(550.0, result.getPaidAmount(), 0.01); // 餃子分
        assertEquals(3300.0, result.getRemainingAmount(), 0.01); // ラーメン分
        assertFalse(result.getIsFullyPaid());
        assertEquals(1, result.getUnpaidDetails().size());
        assertEquals("ラーメン", result.getUnpaidDetails().get(0).getMenuName());
    }
    
    @Test
    void 数量不足の商品を支払おうとするとエラー() {
        // Given
        paymentDetails.get(0).setQuantity(2); // 残り2個
        
        IndividualPaymentRequest request = new IndividualPaymentRequest();
        request.setPaymentId(1);
        
        IndividualPaymentRequest.ItemSelection item = new IndividualPaymentRequest.ItemSelection();
        item.setPaymentDetailId(1);
        item.setQuantity(5); // 5個支払おうとする（不足）
        request.setItems(Arrays.asList(item));
        request.setPaymentTime(LocalDateTime.now());
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findById(1)).thenReturn(Optional.of(paymentDetails.get(0)));
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            paymentSplitService.processIndividualPayment(request);
        });
    }
    
    @Test
    void 割り勘会計_分割人数が入店人数を超える場合はエラー() {
        // Given
        visit.setNumberOfPeople(3); // 入店人数は3人
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(5); // 5人で割ろうとする（入店人数より多い）
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(700.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        // paymentDetailRepository.findByPaymentPaymentId は呼ばれないので stubbing しない
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentSplitService.processSplitPayment(request);
        });
        
        // エラーメッセージに入店人数が含まれていることを確認
        assertTrue(exception.getMessage().contains("3人"));
        assertTrue(exception.getMessage().contains("入店人数"));
    }
    
    @Test
    void 割り勘会計_分割人数が入店人数と同じ場合は成功() {
        // Given
        visit.setNumberOfPeople(3); // 入店人数は3人
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3); // 3人で割る（入店人数と同じ）
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(1100.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Arrays.asList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            if (p.getPaymentId() == null) {
                p.setPaymentId(100);
            }
            return p;
        });
        
        // When
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // Then
        assertNotNull(result);
        assertEquals(1100.0, result.getTotal(), 0.01);
        assertEquals(1, result.getSplitNumber());
        assertEquals(3, result.getTotalSplits());
    }
    
    @Test
    void 割り勘会計_分割人数が入店人数より少ない場合は成功() {
        // Given
        visit.setNumberOfPeople(5); // 入店人数は5人
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(3); // 3人で割る（入店人数より少ない）
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(1100.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Arrays.asList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            if (p.getPaymentId() == null) {
                p.setPaymentId(100);
            }
            return p;
        });
        
        // When
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // Then
        assertNotNull(result);
        assertEquals(1100.0, result.getTotal(), 0.01);
        assertEquals(1, result.getSplitNumber());
        assertEquals(3, result.getTotalSplits());
    }
    
    @Test
    void 割り勘会計_入店人数がnullの場合は検証をスキップ() {
        // Given
        visit.setNumberOfPeople(null); // 入店人数が設定されていない
        
        SplitPaymentRequest request = new SplitPaymentRequest();
        request.setPaymentId(1);
        request.setNumberOfSplits(5); // 5人で割る
        request.setCurrentSplit(1);
        request.setPaymentTime(LocalDateTime.now());
        request.setDeposit(700.0);
        
        when(paymentRepository.findById(1)).thenReturn(Optional.of(originalPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(paymentDetails);
        when(paymentRepository.findByParentPaymentPaymentId(1)).thenReturn(Arrays.asList());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = (Payment) i.getArguments()[0];
            if (p.getPaymentId() == null) {
                p.setPaymentId(100);
            }
            return p;
        });
        
        // When
        Payment result = paymentSplitService.processSplitPayment(request);
        
        // Then
        assertNotNull(result);
        // 入店人数がnullの場合は検証がスキップされ、正常に処理される
        // これは後方互換性のための仕様：古いデータで入店人数が記録されていない場合でも割り勘会計を可能にする
        assertEquals(660.0, result.getTotal(), 0.01); // 3300 / 5 = 660
    }
}
