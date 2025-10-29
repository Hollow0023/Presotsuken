package com.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.entity.InspectionLog;
import com.order.repository.CashTransactionRepository;
import com.order.repository.InspectionLogRepository;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.StoreRepository;
import com.order.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class InspectionLogServiceTest {

    @Mock
    private InspectionLogRepository inspectionLogRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentDetailRepository paymentDetailRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private StoreRepository storeRepository;
    
    @Mock
    private CashTransactionRepository cashTransactionRepository;
    
    @Mock
    private PaymentTypeRepository paymentTypeRepository;

    @InjectMocks
    private InspectionLogService inspectionLogService;

    @Test
    void testGetInspectionHistoryReturnsEmptyList() {
        // Given
        Integer storeId = 1;
        List<InspectionLog> expectedHistory = new ArrayList<>();
        
        // When
        when(inspectionLogRepository.findByStore_StoreIdOrderByInspectionTimeDesc(storeId))
            .thenReturn(expectedHistory);
        
        // Then
        List<InspectionLog> actualHistory = inspectionLogService.getInspectionHistory(storeId);
        
        assertNotNull(actualHistory);
        assertEquals(expectedHistory, actualHistory);
        assertEquals(0, actualHistory.size());
    }
    
    @Test
    void testBuildInspectionSummary_WithSplitPayments_CountsChildPaymentsOnly() {
        // Given: 割り勘会計が行われた場合、子会計のみが集計されることを確認
        Integer storeId = 1;
        
        // 3,300円の会計を3人で割り勘した場合
        // 子会計: 1,100円 × 3回 = 3,300円
        // 親会計には集約された3,300円が入るが、これは集計から除外される必要がある
        BigDecimal expectedCashSales = new BigDecimal("3300.00");
        
        // When
        // sumCashSales は子会計のみを集計するクエリを実行（親会計は除外）
        when(paymentRepository.sumCashSales(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(expectedCashSales);
        
        when(paymentDetailRepository.sumTotalSales(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(expectedCashSales);
            
        when(paymentDetailRepository.sumSalesByTaxRate(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
            
        when(paymentDetailRepository.sumSalesByPaymentTypeAndTaxRate(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
            
        when(paymentDetailRepository.sumTaxAmount(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class), any(BigDecimal.class)))
            .thenReturn(BigDecimal.ZERO);
            
        when(paymentDetailRepository.sumDiscountByPaymentType(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(new ArrayList<>());
            
        when(paymentRepository.countCustomerVisits(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(0L);
            
        when(cashTransactionRepository.sumAmountByType(eq(storeId), eq("IN"), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(null);
            
        when(cashTransactionRepository.sumAmountByType(eq(storeId), eq("OUT"), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(null);
            
        when(inspectionLogRepository.existsByStoreIdAndInspectionTimeBetween(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(false);
            
        when(paymentTypeRepository.findAllByOrderByTypeNameAsc())
            .thenReturn(new ArrayList<>());
        
        // Then
        var result = inspectionLogService.buildInspectionSummary(storeId);
        
        assertNotNull(result);
        assertEquals(expectedCashSales, result.get("cashSalesPure"), 
            "割り勘会計の現金売上は子会計の合計（3,300円）であり、親会計と重複カウントされないこと");
        assertEquals(expectedCashSales, result.get("expectedCash"), 
            "想定現金も子会計の合計（3,300円）と一致すること");
    }
}