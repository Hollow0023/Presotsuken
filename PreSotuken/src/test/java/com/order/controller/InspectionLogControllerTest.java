package com.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.order.entity.InspectionLog;
import com.order.interceptor.AdminPageInterceptor;
import com.order.interceptor.LoginCheckInterceptor;
import com.order.service.CookieUtil;
import com.order.service.InspectionLogService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@WebMvcTest(InspectionLogController.class)
@TestPropertySource(properties = "upload.path=/tmp")
class InspectionLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InspectionLogService inspectionLogService;

    @MockBean
    private CookieUtil cookieUtil;
    
    @MockBean
    private LoginCheckInterceptor loginCheckInterceptor;
    
    @MockBean
    private AdminPageInterceptor adminPageInterceptor;

    @Test
    void testShowHistoryWithEmptyData() throws Exception {
        // Mock interceptors to allow the request
        when(loginCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(adminPageInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        
        // Mock the cookie utility to return a store ID
        when(cookieUtil.getStoreIdFromCookie(any(HttpServletRequest.class))).thenReturn(1);
        
        // Mock the service to return an empty list
        List<InspectionLog> emptyHistory = new ArrayList<>();
        when(inspectionLogService.getInspectionHistory(1)).thenReturn(emptyHistory);

        mockMvc.perform(get("/admin/inspection/history")
                .cookie(new Cookie("storeId", "1"))
                .cookie(new Cookie("adminFlag", "true")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inspectionHistory"))
                .andExpect(model().attribute("inspectionHistory", emptyHistory));
    }
}