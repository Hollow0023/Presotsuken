package com.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
 * PaymentControllerのテストクラス
 * 領収書管理画面へのアクセス時のエラーハンドリングをテスト
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

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
     * paymentIdが指定されていない場合は会計履歴一覧へリダイレクトされることを確認
     */
    @Test
    void testShowPaymentDetailWithoutPaymentId() throws Exception {
        // インターセプターをモック
        when(loginCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(adminPageInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        
        mockMvc.perform(get("/payments/history/detail")
                .cookie(new Cookie("storeId", "1"))
                .cookie(new Cookie("userId", "1")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payments/history"));
    }
    
    /**
     * 有効なpaymentIdが指定された場合は正常に表示されることを確認
     */
    @Test
    void testShowPaymentDetailWithValidPaymentId() throws Exception {
        // インターセプターをモック
        when(loginCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(adminPageInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        
        mockMvc.perform(get("/payments/history/detail")
                .param("paymentId", "123")
                .cookie(new Cookie("storeId", "1"))
                .cookie(new Cookie("userId", "1")))
                .andExpect(status().isOk())
                .andExpect(view().name("payment-detail"));
    }
}
