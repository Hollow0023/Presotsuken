package com.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.MenuGroup;
import com.order.entity.Store;

public interface MenuGroupRepository extends JpaRepository<MenuGroup, Integer> {
    Optional<MenuGroup> findByStoreAndGroupName(Store store, String groupName);

    List<MenuGroup> findByStore(Store store);
//    List<MenuGroup> findByStore_StoreId(Integer storeId);
    List<MenuGroup> findByStore_StoreIdAndForAdminOnlyFalseOrForAdminOnlyIsNull(Integer storeId);
    List<MenuGroup> findByStore_StoreId(Integer storeId);
}
