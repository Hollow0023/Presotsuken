package com.order.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;

import com.order.entity.*;
import com.order.repository.*;
import com.order.service.PaymentSplitService;

/**
 * 会計履歴の表示フィルタのテスト
 * 個別会計と割り勘会計の表示ルールを検証
 */
@ExtendWith(MockitoExtension.class)
class PaymentHistoryFilterTest {

    @Mock
    private VisitRepository visitRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentDetailRepository paymentDetailRepository;
    
    @Mock
    private PaymentTypeRepository paymentTypeRepository;
    
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private SeatRepository seatRepository;
    
    @Mock
    private PaymentSplitService paymentSplitService;
    
    @InjectMocks
    private PaymentController paymentController;
    
    private Store store;
    private Seat seat;
    private Visit visit1;
    private Visit visit2;
    private Visit visit3;
    
    // 通常会計
    private Payment normalPayment;
    
    // 割り勘会計（親会計 + 子会計2つ）
    private Payment splitParent;
    private Payment splitChild1;
    private Payment splitChild2;
    
    // 個別会計（親会計 + 子会計2つ）
    private Payment individualParent;
    private Payment individualChild1;
    private Payment individualChild2;
    
    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(1);
        
        seat = new Seat();
        seat.setSeatId(1);
        seat.setSeatName("テーブル1");
        
        // 通常会計用
        visit1 = new Visit();
        visit1.setVisitId(1);
        visit1.setStore(store);
        visit1.setSeat(seat);
        visit1.setVisitTime(LocalDateTime.now().minusHours(3));
        
        normalPayment = new Payment();
        normalPayment.setPaymentId(1);
        normalPayment.setStore(store);
        normalPayment.setVisit(visit1);
        normalPayment.setTotal(1000.0);
        normalPayment.setPaymentTime(LocalDateTime.now().minusHours(3));
        normalPayment.setPaymentStatus("COMPLETED");
        normalPayment.setCancel(false);
        normalPayment.setVisitCancel(false);
        // totalSplits は null（通常会計）
        
        // 割り勘会計用
        visit2 = new Visit();
        visit2.setVisitId(2);
        visit2.setStore(store);
        visit2.setSeat(seat);
        visit2.setVisitTime(LocalDateTime.now().minusHours(2));
        
        splitParent = new Payment();
        splitParent.setPaymentId(2);
        splitParent.setStore(store);
        splitParent.setVisit(visit2);
        splitParent.setTotal(3000.0);
        splitParent.setPaymentTime(LocalDateTime.now().minusHours(2));
        splitParent.setPaymentStatus("COMPLETED");
        splitParent.setTotalSplits(3); // 割り勘の総分割数
        splitParent.setCancel(false);
        splitParent.setVisitCancel(false);
        
        splitChild1 = new Payment();
        splitChild1.setPaymentId(102);
        splitChild1.setParentPayment(splitParent);
        splitChild1.setStore(store);
        splitChild1.setVisit(visit2);
        splitChild1.setTotal(1000.0);
        splitChild1.setSplitNumber(1);
        splitChild1.setTotalSplits(3); // 子会計にも設定される
        splitChild1.setPaymentTime(LocalDateTime.now().minusHours(2));
        splitChild1.setPaymentStatus("PARTIAL");
        splitChild1.setCancel(false);
        splitChild1.setVisitCancel(false);
        
        splitChild2 = new Payment();
        splitChild2.setPaymentId(103);
        splitChild2.setParentPayment(splitParent);
        splitChild2.setStore(store);
        splitChild2.setVisit(visit2);
        splitChild2.setTotal(1000.0);
        splitChild2.setSplitNumber(2);
        splitChild2.setTotalSplits(3); // 子会計にも設定される
        splitChild2.setPaymentTime(LocalDateTime.now().minusHours(2));
        splitChild2.setPaymentStatus("PARTIAL");
        splitChild2.setCancel(false);
        splitChild2.setVisitCancel(false);
        
        // 個別会計用
        visit3 = new Visit();
        visit3.setVisitId(3);
        visit3.setStore(store);
        visit3.setSeat(seat);
        visit3.setVisitTime(LocalDateTime.now().minusHours(1));
        
        individualParent = new Payment();
        individualParent.setPaymentId(3);
        individualParent.setStore(store);
        individualParent.setVisit(visit3);
        individualParent.setTotal(2000.0);
        individualParent.setPaymentTime(LocalDateTime.now().minusHours(1));
        individualParent.setPaymentStatus("COMPLETED");
        // totalSplits は null（個別会計の親）
        individualParent.setCancel(false);
        individualParent.setVisitCancel(false);
        
        individualChild1 = new Payment();
        individualChild1.setPaymentId(203);
        individualChild1.setParentPayment(individualParent);
        individualChild1.setStore(store);
        individualChild1.setVisit(visit3);
        individualChild1.setTotal(800.0);
        individualChild1.setPaymentTime(LocalDateTime.now().minusHours(1));
        individualChild1.setPaymentStatus("PARTIAL");
        // totalSplits は null（個別会計の子）
        individualChild1.setCancel(false);
        individualChild1.setVisitCancel(false);
        
        individualChild2 = new Payment();
        individualChild2.setPaymentId(204);
        individualChild2.setParentPayment(individualParent);
        individualChild2.setStore(store);
        individualChild2.setVisit(visit3);
        individualChild2.setTotal(1200.0);
        individualChild2.setPaymentTime(LocalDateTime.now().minusHours(1));
        individualChild2.setPaymentStatus("COMPLETED");
        // totalSplits は null（個別会計の子）
        individualChild2.setCancel(false);
        individualChild2.setVisitCancel(false);
    }
    
    @Test
    void 通常会計は表示される() {
        // Given
        List<Payment> allPayments = Arrays.asList(normalPayment);
        when(paymentRepository.findByStoreStoreIdAndCancelledStatus(1, false))
            .thenReturn(allPayments);
        when(paymentRepository.findByParentPaymentPaymentId(1))
            .thenReturn(Arrays.asList()); // 子会計なし
        when(paymentDetailRepository.findByPaymentPaymentId(anyInt()))
            .thenReturn(Arrays.asList());
        
        Model model = new ExtendedModelMap();
        
        // When
        String viewName = paymentController.showPaymentHistory(1, "active", model);
        
        // Then
        assertEquals("paymentHistory", viewName);
        @SuppressWarnings("unchecked")
        List<Payment> payments = (List<Payment>) model.getAttribute("payments");
        assertNotNull(payments);
        assertEquals(1, payments.size());
        assertEquals(normalPayment.getPaymentId(), payments.get(0).getPaymentId());
    }
    
    @Test
    void 割り勘会計は親会計のみ表示され子会計は非表示() {
        // Given
        List<Payment> allPayments = Arrays.asList(
            splitParent, 
            splitChild1, 
            splitChild2
        );
        when(paymentRepository.findByStoreStoreIdAndCancelledStatus(1, false))
            .thenReturn(allPayments);
        when(paymentRepository.findByParentPaymentPaymentId(2))
            .thenReturn(Arrays.asList(splitChild1, splitChild2));
        when(paymentDetailRepository.findByPaymentPaymentId(anyInt()))
            .thenReturn(Arrays.asList());
        
        Model model = new ExtendedModelMap();
        
        // When
        String viewName = paymentController.showPaymentHistory(1, "active", model);
        
        // Then
        assertEquals("paymentHistory", viewName);
        @SuppressWarnings("unchecked")
        List<Payment> payments = (List<Payment>) model.getAttribute("payments");
        assertNotNull(payments);
        
        // 親会計のみが表示されることを確認
        assertEquals(1, payments.size(), "割り勘会計では親会計のみが表示されるべき");
        assertEquals(splitParent.getPaymentId(), payments.get(0).getPaymentId());
        
        // 子会計が含まれていないことを確認
        boolean hasChild = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(splitChild1.getPaymentId()) || 
                          p.getPaymentId().equals(splitChild2.getPaymentId()));
        assertFalse(hasChild, "割り勘会計の子会計は表示されないべき");
    }
    
    @Test
    void 個別会計は子会計のみ表示され親会計は非表示() {
        // Given
        List<Payment> allPayments = Arrays.asList(
            individualParent,
            individualChild1,
            individualChild2
        );
        when(paymentRepository.findByStoreStoreIdAndCancelledStatus(1, false))
            .thenReturn(allPayments);
        when(paymentRepository.findByParentPaymentPaymentId(3))
            .thenReturn(Arrays.asList(individualChild1, individualChild2));
        when(paymentDetailRepository.findByPaymentPaymentId(anyInt()))
            .thenReturn(Arrays.asList());
        
        Model model = new ExtendedModelMap();
        
        // When
        String viewName = paymentController.showPaymentHistory(1, "active", model);
        
        // Then
        assertEquals("paymentHistory", viewName);
        @SuppressWarnings("unchecked")
        List<Payment> payments = (List<Payment>) model.getAttribute("payments");
        assertNotNull(payments);
        
        // 子会計のみが表示されることを確認
        assertEquals(2, payments.size(), "個別会計では子会計のみが表示されるべき");
        
        boolean hasChild1 = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(individualChild1.getPaymentId()));
        boolean hasChild2 = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(individualChild2.getPaymentId()));
        assertTrue(hasChild1, "個別会計の子会計1が表示されるべき");
        assertTrue(hasChild2, "個別会計の子会計2が表示されるべき");
        
        // 親会計が含まれていないことを確認
        boolean hasParent = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(individualParent.getPaymentId()));
        assertFalse(hasParent, "個別会計の親会計は表示されないべき");
    }
    
    @Test
    void 複数の会計タイプが混在する場合の表示() {
        // Given
        List<Payment> allPayments = Arrays.asList(
            normalPayment,
            splitParent,
            splitChild1,
            splitChild2,
            individualParent,
            individualChild1,
            individualChild2
        );
        when(paymentRepository.findByStoreStoreIdAndCancelledStatus(1, false))
            .thenReturn(allPayments);
        when(paymentRepository.findByParentPaymentPaymentId(1))
            .thenReturn(Arrays.asList()); // 通常会計: 子なし
        when(paymentRepository.findByParentPaymentPaymentId(2))
            .thenReturn(Arrays.asList(splitChild1, splitChild2)); // 割り勘: 子あり
        when(paymentRepository.findByParentPaymentPaymentId(3))
            .thenReturn(Arrays.asList(individualChild1, individualChild2)); // 個別: 子あり
        when(paymentDetailRepository.findByPaymentPaymentId(anyInt()))
            .thenReturn(Arrays.asList());
        
        Model model = new ExtendedModelMap();
        
        // When
        String viewName = paymentController.showPaymentHistory(1, "active", model);
        
        // Then
        assertEquals("paymentHistory", viewName);
        @SuppressWarnings("unchecked")
        List<Payment> payments = (List<Payment>) model.getAttribute("payments");
        assertNotNull(payments);
        
        // 期待される表示: 通常会計1 + 割り勘親会計1 + 個別会計子会計2 = 4件
        assertEquals(4, payments.size(), "通常会計、割り勘親会計、個別会計の子会計2つの計4件が表示されるべき");
        
        // 通常会計が含まれることを確認
        boolean hasNormal = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(normalPayment.getPaymentId()));
        assertTrue(hasNormal, "通常会計が表示されるべき");
        
        // 割り勘の親会計が含まれることを確認
        boolean hasSplitParent = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(splitParent.getPaymentId()));
        assertTrue(hasSplitParent, "割り勘の親会計が表示されるべき");
        
        // 割り勘の子会計が含まれないことを確認
        boolean hasSplitChild = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(splitChild1.getPaymentId()) || 
                          p.getPaymentId().equals(splitChild2.getPaymentId()));
        assertFalse(hasSplitChild, "割り勘の子会計は表示されないべき");
        
        // 個別会計の親会計が含まれないことを確認
        boolean hasIndividualParent = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(individualParent.getPaymentId()));
        assertFalse(hasIndividualParent, "個別会計の親会計は表示されないべき");
        
        // 個別会計の子会計が含まれることを確認
        boolean hasIndividualChild1 = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(individualChild1.getPaymentId()));
        boolean hasIndividualChild2 = payments.stream()
            .anyMatch(p -> p.getPaymentId().equals(individualChild2.getPaymentId()));
        assertTrue(hasIndividualChild1, "個別会計の子会計1が表示されるべき");
        assertTrue(hasIndividualChild2, "個別会計の子会計2が表示されるべき");
    }
}
