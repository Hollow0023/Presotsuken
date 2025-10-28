package com.order.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.order.entity.*;
import com.order.repository.*;
import com.order.service.PaymentSplitService;

/**
 * PaymentControllerの子会計情報取得機能のテスト
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerChildPaymentsTest {

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
    private Visit visit;
    private Payment parentPayment;
    private Payment childPayment1;
    private Payment childPayment2;
    private PaymentType paymentType;
    private User cashier;
    
    @BeforeEach
    void setUp() {
        // テストデータのセットアップ
        store = new Store();
        store.setStoreId(1);
        
        seat = new Seat();
        seat.setSeatId(1);
        seat.setSeatName("テーブル1");
        
        visit = new Visit();
        visit.setVisitId(1);
        visit.setStore(store);
        visit.setSeat(seat);
        visit.setVisitTime(LocalDateTime.now());
        
        paymentType = new PaymentType();
        paymentType.setTypeId(1);
        paymentType.setTypeName("現金");
        
        cashier = new User();
        cashier.setUserId(1);
        cashier.setUserName("田中");
        
        // 親会計
        parentPayment = new Payment();
        parentPayment.setPaymentId(1);
        parentPayment.setStore(store);
        parentPayment.setVisit(visit);
        parentPayment.setTotal(3300.0);
        parentPayment.setPaymentTime(LocalDateTime.now());
        parentPayment.setPaymentStatus("COMPLETED");
        parentPayment.setTotalSplits(3);
        
        // 子会計1
        childPayment1 = new Payment();
        childPayment1.setPaymentId(101);
        childPayment1.setParentPayment(parentPayment);
        childPayment1.setStore(store);
        childPayment1.setVisit(visit);
        childPayment1.setTotal(1100.0);
        childPayment1.setDeposit(1100.0);
        childPayment1.setSplitNumber(1);
        childPayment1.setPaymentTime(LocalDateTime.now().minusMinutes(5));
        childPayment1.setPaymentType(paymentType);
        childPayment1.setCashier(cashier);
        childPayment1.setPaymentStatus("PARTIAL");
        
        // 子会計2
        childPayment2 = new Payment();
        childPayment2.setPaymentId(102);
        childPayment2.setParentPayment(parentPayment);
        childPayment2.setStore(store);
        childPayment2.setVisit(visit);
        childPayment2.setTotal(1100.0);
        childPayment2.setDeposit(1200.0);
        childPayment2.setSplitNumber(2);
        childPayment2.setPaymentTime(LocalDateTime.now().minusMinutes(3));
        childPayment2.setPaymentType(paymentType);
        childPayment2.setCashier(cashier);
        childPayment2.setPaymentStatus("PARTIAL");
    }
    
    @Test
    void 親会計詳細取得時に子会計リストが含まれる() {
        // Given
        when(paymentRepository.findById(1)).thenReturn(Optional.of(parentPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList());
        when(seatRepository.findByStore_StoreIdOrderBySeatNameAsc(1)).thenReturn(Arrays.asList(seat));
        when(userRepository.findByStore_StoreId(1)).thenReturn(Arrays.asList(cashier));
        when(paymentTypeRepository.findByStoreId(1)).thenReturn(Arrays.asList(paymentType));
        when(paymentRepository.findAll()).thenReturn(Arrays.asList(parentPayment, childPayment1, childPayment2));
        
        // When
        ResponseEntity<Map<String, Object>> response = paymentController.getPaymentHistoryDetail(1, 1);
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        
        Map<String, Object> result = response.getBody();
        assertNotNull(result);
        
        // 子会計リストが含まれているか確認
        assertTrue(result.containsKey("childPayments"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childPayments = (List<Map<String, Object>>) result.get("childPayments");
        assertEquals(2, childPayments.size());
        
        // 1人目の子会計を確認
        Map<String, Object> child1 = childPayments.stream()
            .filter(c -> (Integer) c.get("splitNumber") == 1)
            .findFirst()
            .orElse(null);
        assertNotNull(child1);
        assertEquals(101, child1.get("paymentId"));
        assertEquals(1100.0, child1.get("amount"));
        assertEquals(1100.0, child1.get("deposit"));
        assertEquals("現金", child1.get("paymentTypeName"));
        assertEquals("田中", child1.get("cashierName"));
        
        // 親会計の情報も確認
        assertEquals("COMPLETED", result.get("paymentStatus"));
        assertEquals(3, result.get("totalSplits"));
    }
    
    @Test
    void 子会計が存在しない場合は空のリストが返される() {
        // Given
        when(paymentRepository.findById(1)).thenReturn(Optional.of(parentPayment));
        when(paymentDetailRepository.findByPaymentPaymentId(1)).thenReturn(Arrays.asList());
        when(seatRepository.findByStore_StoreIdOrderBySeatNameAsc(1)).thenReturn(Arrays.asList(seat));
        when(userRepository.findByStore_StoreId(1)).thenReturn(Arrays.asList(cashier));
        when(paymentTypeRepository.findByStoreId(1)).thenReturn(Arrays.asList(paymentType));
        when(paymentRepository.findAll()).thenReturn(Arrays.asList(parentPayment)); // 子会計なし
        
        // When
        ResponseEntity<Map<String, Object>> response = paymentController.getPaymentHistoryDetail(1, 1);
        
        // Then
        assertNotNull(response);
        Map<String, Object> result = response.getBody();
        assertNotNull(result);
        
        assertTrue(result.containsKey("childPayments"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childPayments = (List<Map<String, Object>>) result.get("childPayments");
        assertEquals(0, childPayments.size());
    }
}
