package com.order.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.order.entity.Store;
import com.order.repository.StoreRepository;

@Service
public class StoreService {

    private final StoreRepository storeRepository;

    public StoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }
}
