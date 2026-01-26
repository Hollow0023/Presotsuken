package com.order.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.order.entity.Menu;
import com.order.entity.Payment;
import com.order.entity.PaymentDetail;
import com.order.entity.Store;
import com.order.entity.TaxRate;
import com.order.repository.PaymentDetailRepository;
import com.order.repository.PaymentRepository;
import com.order.repository.PaymentTypeRepository;
import com.order.repository.SeatRepository;
import com.order.repository.UserRepository;
import com.order.repository.VisitRepository;
import com.order.service.PaymentSplitService;

/**
 * PaymentControllerの商品明細削除機能のテスト
 */
@ExtendWith(MockitoExtension.class)
class PaymentDetailDeleteTest {

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
    private Payment payment;
    private PaymentDetail detail1;
    private PaymentDetail detail2;
    private Menu menu1;
    private Menu menu2;
    private TaxRate taxRate;
    
    @BeforeEach
    void setUp() {
        // 店舗の設定
        store = new Store();
        store.setStoreId(1);
        
        // 税率の設定
        taxRate = new TaxRate();
        taxRate.setTaxRateId(1);
        taxRate.setRate(0.1); // 10%
        
        // メニューの設定
        menu1 = new Menu();
        menu1.setMenuId(1);
        menu1.setMenuName("ラーメン");
        menu1.setPrice(800.0);
        
        menu2 = new Menu();
        menu2.setMenuId(2);
        menu2.setMenuName("餃子");
        menu2.setPrice(400.0);
        
        // 会計の設定
        payment = new Payment();
        payment.setPaymentId(1);
        payment.setStore(store);
        payment.setDiscount(0.0);
        
        // 商品明細1の設定
        detail1 = new PaymentDetail();
        detail1.setPaymentDetailId(101);
        detail1.setPayment(payment);
        detail1.setStore(store);
        detail1.setMenu(menu1);
        detail1.setQuantity(2);
        detail1.setSubtotal(1600.0); // 800 * 2
        detail1.setDiscount(0.0);
        detail1.setTaxRate(taxRate);
        
        // 商品明細2の設定
        detail2 = new PaymentDetail();
        detail2.setPaymentDetailId(102);
        detail2.setPayment(payment);
        detail2.setStore(store);
        detail2.setMenu(menu2);
        detail2.setQuantity(1);
        detail2.setSubtotal(400.0); // 400 * 1
        detail2.setDiscount(0.0);
        detail2.setTaxRate(taxRate);
    }
    
    @Test
    void 商品明細を削除できること() {
        // 削除する商品明細IDを設定
        Integer paymentDetailId = 101;
        Integer storeId = 1;
        
        // Mockの設定
        when(paymentDetailRepository.findById(paymentDetailId)).thenReturn(Optional.of(detail1));
        
        // 削除後の残りの商品明細を返す（detail2のみ）
        List<PaymentDetail> remainingDetails = Arrays.asList(detail2);
        when(paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId()))
            .thenReturn(remainingDetails);
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, storeId);
        
        // 検証
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(1, response.getBody().get("remainingItemCount"));
        
        // 削除が呼ばれたことを確認
        verify(paymentDetailRepository, times(1)).deleteById(paymentDetailId);
        
        // Paymentの保存が呼ばれたことを確認
        verify(paymentRepository, times(1)).save(payment);
    }
    
    @Test
    void 店舗IDがnullの場合401が返されること() {
        // 店舗IDがnull
        Integer paymentDetailId = 101;
        Integer storeId = null;
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, storeId);
        
        // 検証
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals("店舗情報が取得できません。", response.getBody().get("message"));
        
        // 削除が呼ばれていないことを確認
        verify(paymentDetailRepository, never()).deleteById(any());
    }
    
    @Test
    void 存在しない商品明細を削除しようとすると404が返されること() {
        // 存在しない商品明細ID
        Integer paymentDetailId = 999;
        Integer storeId = 1;
        
        // Mockの設定
        when(paymentDetailRepository.findById(paymentDetailId)).thenReturn(Optional.empty());
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, storeId);
        
        // 検証
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        
        // 削除が呼ばれていないことを確認
        verify(paymentDetailRepository, never()).deleteById(any());
    }
    
    @Test
    void 異なる店舗の商品明細を削除しようとすると403が返されること() {
        // 異なる店舗ID
        Integer paymentDetailId = 101;
        Integer differentStoreId = 999;
        
        // Mockの設定
        when(paymentDetailRepository.findById(paymentDetailId)).thenReturn(Optional.of(detail1));
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, differentStoreId);
        
        // 検証
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        
        // 削除が呼ばれていないことを確認
        verify(paymentDetailRepository, never()).deleteById(any());
    }
    
    @Test
    void 削除後に合計金額が正しく再計算されること() {
        // 削除する商品明細IDを設定
        Integer paymentDetailId = 101;
        Integer storeId = 1;
        
        // Mockの設定
        when(paymentDetailRepository.findById(paymentDetailId)).thenReturn(Optional.of(detail1));
        
        // 削除後の残りの商品明細を返す（detail2のみ）
        List<PaymentDetail> remainingDetails = Arrays.asList(detail2);
        when(paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId()))
            .thenReturn(remainingDetails);
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, storeId);
        
        // 検証
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // 小計が正しく計算されていることを確認（detail2の小計: 400円）
        Double subtotal = (Double) response.getBody().get("subtotal");
        assertEquals(400.0, subtotal, 0.01);
        
        // 税込み合計が正しく計算されていることを確認（400 * 1.1 = 440円）
        Double total = (Double) response.getBody().get("total");
        assertEquals(440.0, total, 0.01);
    }
    
    @Test
    void 全商品を削除した場合でもエラーにならないこと() {
        // 最後の商品を削除する
        Integer paymentDetailId = 101;
        Integer storeId = 1;
        
        // Mockの設定
        when(paymentDetailRepository.findById(paymentDetailId)).thenReturn(Optional.of(detail1));
        
        // 削除後の商品明細は空
        List<PaymentDetail> emptyDetails = new ArrayList<>();
        when(paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId()))
            .thenReturn(emptyDetails);
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, storeId);
        
        // 検証
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(0, response.getBody().get("remainingItemCount"));
        
        // 小計と合計が0になっていることを確認
        Double subtotal = (Double) response.getBody().get("subtotal");
        assertEquals(0.0, subtotal, 0.01);
        
        Double total = (Double) response.getBody().get("total");
        assertEquals(0.0, total, 0.01);
    }
    
    @Test
    void 割引が設定されている商品明細を削除した場合も正しく再計算されること() {
        // 割引を設定
        detail1.setDiscount(200.0);
        detail2.setDiscount(100.0);
        
        // 削除する商品明細IDを設定
        Integer paymentDetailId = 101;
        Integer storeId = 1;
        
        // Mockの設定
        when(paymentDetailRepository.findById(paymentDetailId)).thenReturn(Optional.of(detail1));
        
        // 削除後の残りの商品明細を返す（detail2のみ）
        List<PaymentDetail> remainingDetails = Arrays.asList(detail2);
        when(paymentDetailRepository.findByPaymentPaymentId(payment.getPaymentId()))
            .thenReturn(remainingDetails);
        
        // テスト実行
        ResponseEntity<Map<String, Object>> response = 
            paymentController.deletePaymentDetail(paymentDetailId, storeId);
        
        // 検証
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // 小計が正しく計算されていることを確認（detail2: 400 - 100 = 300円）
        Double subtotal = (Double) response.getBody().get("subtotal");
        assertEquals(300.0, subtotal, 0.01);
        
        // 税込み合計が正しく計算されていることを確認（300 * 1.1 = 330円）
        Double total = (Double) response.getBody().get("total");
        assertEquals(330.0, total, 0.01);
    }
}
