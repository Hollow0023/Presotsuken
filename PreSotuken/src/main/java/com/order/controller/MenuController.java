package com.order.controller;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.order.entity.Menu;
import com.order.entity.MenuGroup;
import com.order.entity.MenuTimeSlot;
import com.order.entity.Store;
import com.order.entity.TaxRate;
import com.order.repository.MenuGroupRepository;
import com.order.repository.MenuRepository;
import com.order.repository.MenuTimeSlotRepository;
import com.order.repository.StoreRepository;
import com.order.repository.TaxRateRepository;
import com.order.service.ImageUploadService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/menu")
public class MenuController {

    private final MenuRepository menuRepository;
    private final TaxRateRepository taxRateRepository;
    private final MenuGroupRepository menuGroupRepository;
    private final StoreRepository storeRepository;
    private final MenuTimeSlotRepository menuSlotRepository;
    private final ImageUploadService imageUploadService;

    @GetMapping("/add")
    public String showAddMenuForm(HttpServletRequest request, Model model) {
        Integer storeId = null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                storeId = Integer.parseInt(cookie.getValue());
                break;
            }
        }

        if (storeId == null) return "redirect:/login";

        List<TaxRate> taxRates = taxRateRepository.findByStore_StoreId(storeId);
        List<MenuGroup> menuGroups = menuGroupRepository.findByStore_StoreId(storeId);
        List<MenuTimeSlot> timeSlots = menuSlotRepository.findByStoreStoreId(storeId);
        
        model.addAttribute("menu", new Menu());
        model.addAttribute("taxRates", taxRates);
        model.addAttribute("menuGroups", menuGroups);
        model.addAttribute("timeSlots",timeSlots);

        return "menu_add";
    }

    @PostMapping("/add")
    public String addMenu(@ModelAttribute Menu menu,
                          @RequestParam("imageFile") MultipartFile imageFile,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) throws IOException {

        Integer storeId = null;
        for (Cookie cookie : request.getCookies()) {
            if ("storeId".equals(cookie.getName())) {
                storeId = Integer.parseInt(cookie.getValue());
                break;
            }
        }
        if (storeId == null) return "redirect:/login";

        Optional<Store> optionalStore = storeRepository.findById(storeId);
        if (optionalStore.isEmpty()) return "redirect:/login";

        // ✅ ImageUploadServiceを使って保存処理（拡張子は固定で.jpg）
        if (!imageFile.isEmpty()) {
            String imagePath = imageUploadService.uploadImage(imageFile, storeId);
            menu.setMenuImage(imagePath); // DBに保存するのは /images/{storeId}/{uuid}.jpg
        }

        menu.setStore(optionalStore.get());
        menu.setIsSoldOut(false);
        if (menu.getReceiptLabel() == null || menu.getReceiptLabel().trim().isEmpty()) {
            menu.setReceiptLabel(menu.getMenuName());
        }

        menuRepository.save(menu);
        redirectAttributes.addFlashAttribute("success", "メニューを追加しました！");
        return "redirect:/menu/add";
    }

}
