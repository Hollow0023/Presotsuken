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

    // 全てのメニューグループをsort_orderでソートして取得 (管理者用)
    List<MenuGroup> findByStore_StoreIdOrderBySortOrderAsc(Integer storeId);

    // 顧客向けで、isPlanTarget=false かつ forAdminOnly=false/null のメニューグループをソートして取得
    List<MenuGroup> findByStore_StoreIdAndIsPlanTargetFalseAndForAdminOnlyFalseOrForAdminOnlyIsNullOrderBySortOrderAsc(Integer storeId);

    // 指定されたgroupIdのリストに含まれ、かつisPlanTargetがtrueのMenuGroupをソートして取得
    List<MenuGroup> findByGroupIdInAndIsPlanTargetTrueOrderBySortOrderAsc(List<Integer> groupIds);

    // ※ getCustomerMenuGroupsで使われているfindByStore_StoreIdAndForAdminOnlyFalseOrForAdminOnlyIsNull は
    //    上の sort_order でソートするメソッドに置き換える（または呼び出し側で変更）
    
    

}
