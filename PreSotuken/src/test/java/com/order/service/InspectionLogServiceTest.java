package com.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.order.entity.InspectionLog;
import com.order.repository.InspectionLogRepository;

@ExtendWith(MockitoExtension.class)
class InspectionLogServiceTest {

    @Mock
    private InspectionLogRepository inspectionLogRepository;

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
}