package com.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.order.entity.SeatGroup;
import com.order.entity.Store;
import com.order.interceptor.AdminPageInterceptor;
import com.order.interceptor.LoginCheckInterceptor;
import com.order.repository.SeatGroupRepository;
import com.order.service.CookieUtil;

/**
 * 座席グループ追加APIのテスト
 */
@WebMvcTest(SeatGroupRestController.class)
@TestPropertySource(properties = "upload.path=/tmp")
class SeatGroupRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeatGroupRepository seatGroupRepository;

    @MockBean
    private CookieUtil cookieUtil;
    
    @MockBean
    private LoginCheckInterceptor loginCheckInterceptor;
    
    @MockBean
    private AdminPageInterceptor adminPageInterceptor;

    /**
     * 座席グループ追加: ネストしたstoreオブジェクトを含むリクエストが正常に処理されることを確認
     */
    @Test
    void testCreateGroupWithNestedStoreObject() throws Exception {
        // Mock interceptors to allow the request
        when(loginCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(adminPageInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        
        // Mock the repository save to return a SeatGroup with ID
        when(seatGroupRepository.save(any(SeatGroup.class))).thenAnswer(invocation -> {
            SeatGroup group = invocation.getArgument(0);
            group.setSeatGroupId(1);
            return group;
        });

        // JSON with nested store object - この形式がフロントエンドから送信される
        String jsonWithNestedStore = """
            {
                "seatGroupName": "テストグループ",
                "store": { "storeId": 1 }
            }
            """;

        mockMvc.perform(post("/api/seat-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonWithNestedStore))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatGroupName").value("テストグループ"))
                .andExpect(jsonPath("$.store.storeId").value(1));
    }
}
