package com.order.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.order.controller.PaymentController;
import com.order.interceptor.AdminPageInterceptor;
import com.order.interceptor.LoginCheckInterceptor;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.SeatRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;

import jakarta.servlet.http.Cookie;

/**
 * GlobalExceptionHandlerのテストクラス
 * MethodArgumentTypeMismatchExceptionのハンドリングをテスト
 */
@WebMvcTest(PaymentController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VisitRepository visitRepository;
    
    @MockBean
    private PaymentRepository paymentRepository;
    
    @MockBean
    private PaymentDetailRepository paymentDetailRepository;
    
    @MockBean
    private PaymentTypeRepository paymentTypeRepository;
    
    @MockBean
    private SimpMessagingTemplate messagingTemplate;
    
    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private SeatRepository seatRepository;
    
    @MockBean
    private LoginCheckInterceptor loginCheckInterceptor;
    
    @MockBean
    private AdminPageInterceptor adminPageInterceptor;

    /**
     * paymentIdに"undefined"などの無効な文字列が指定された場合、
     * MethodArgumentTypeMismatchExceptionが発生し、
     * GlobalExceptionHandlerで適切に処理されることを確認
     */
    @Test
    void testMethodArgumentTypeMismatchHandling() throws Exception {
        // インターセプターをモック
        when(loginCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(adminPageInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        
        mockMvc.perform(get("/payments/history/detail")
                .param("paymentId", "undefined")  // 無効な値
                .cookie(new Cookie("storeId", "1"))
                .cookie(new Cookie("userId", "1")))
                .andExpect(status().isOk())
                .andExpect(view().name("error"))
                .andExpect(model().attributeExists("message"))
                .andExpect(model().attributeExists("stacktrace"));
    }
}
