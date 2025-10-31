package com.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.order.repository.VisitRepository;

@ExtendWith(MockitoExtension.class)
class InspectionLogServiceTest {

    @Mock
    private InspectionLogRepository inspectionLogRepository;
    
    @Mock
    private PaymentDetailRepository paymentDetailRepository;
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private PaymentTypeRepository paymentTypeRepository;
    
    @Mock
    private CashTransactionRepository cashTransactionRepository;
    
    @Mock
    private VisitRepository visitRepository;

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
    void testBuildInspectionSummary_CalculatesTaxAmountByPaymentType() {
        // Given
        Integer storeId = 1;
        
        // Mock基本的なデータ
        when(inspectionLogRepository.existsByStoreIdAndInspectionTimeBetween(eq(storeId), any(), any()))
            .thenReturn(false);
        when(paymentRepository.sumCashSales(eq(storeId), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(paymentDetailRepository.sumTotalSales(eq(storeId), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(paymentDetailRepository.sumSalesByTaxRate(eq(storeId), any(), any()))
            .thenReturn(new ArrayList<>());
        when(paymentDetailRepository.sumSalesByPaymentTypeAndTaxRate(eq(storeId), any(), any()))
            .thenReturn(new ArrayList<>());
        when(paymentDetailRepository.sumTaxAmount(eq(storeId), any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        
        // 支払い方法別の消費税額データをモック
        List<Object[]> taxAmountData = new ArrayList<>();
        // 現金 10%: 100円
        taxAmountData.add(new Object[]{"現金", 0.10, 100.0});
        // 現金 8%: 80円
        taxAmountData.add(new Object[]{"現金", 0.08, 80.0});
        // カード 10%: 200円
        taxAmountData.add(new Object[]{"カード", 0.10, 200.0});
        
        when(paymentDetailRepository.sumTaxAmountByPaymentTypeAndTaxRate(eq(storeId), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(taxAmountData);
        
        when(paymentDetailRepository.sumDiscountByPaymentType(eq(storeId), any(), any()))
            .thenReturn(new ArrayList<>());
        when(visitRepository.sumNumberOfPeopleByPaymentTime(eq(storeId), any(), any()))
            .thenReturn(0L);
        when(cashTransactionRepository.sumAmountByType(eq(storeId), any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(paymentTypeRepository.findAllByOrderByTypeNameAsc())
            .thenReturn(new ArrayList<>());
        
        // When
        Map<String, Object> result = inspectionLogService.buildInspectionSummary(storeId);
        
        // Then
        assertNotNull(result);
        
        // 支払い方法別の消費税額が正しく計算されていることを確認
        assertTrue(result.containsKey("taxAmountByPaymentType_現金_10%"));
        assertTrue(result.containsKey("taxAmountByPaymentType_現金_8%"));
        assertTrue(result.containsKey("taxAmountByPaymentType_カード_10%"));
        
        assertEquals(new BigDecimal("100.0"), result.get("taxAmountByPaymentType_現金_10%"));
        assertEquals(new BigDecimal("80.0"), result.get("taxAmountByPaymentType_現金_8%"));
        assertEquals(new BigDecimal("200.0"), result.get("taxAmountByPaymentType_カード_10%"));
    }
}