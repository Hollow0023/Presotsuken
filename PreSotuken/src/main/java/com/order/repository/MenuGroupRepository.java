package com.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    // 明示的なクエリで正しい条件を指定
    @Query("SELECT mg FROM MenuGroup mg WHERE mg.store.storeId = :storeId " +
           "AND mg.isPlanTarget = false " +
           "AND (mg.forAdminOnly = false OR mg.forAdminOnly IS NULL) " +
           "ORDER BY mg.sortOrder ASC")
    List<MenuGroup> findCustomerMenuGroupsByStoreId(@Param("storeId") Integer storeId);

    // 指定されたgroupIdのリストに含まれ、かつisPlanTargetがtrueのMenuGroupをソートして取得
    List<MenuGroup> findByGroupIdInAndIsPlanTargetTrueOrderBySortOrderAsc(List<Integer> groupIds);

    Optional<Integer> findTopByStoreOrderBySortOrderDesc(Store store);
    // ※ getCustomerMenuGroupsで使われているfindByStore_StoreIdAndForAdminOnlyFalseOrForAdminOnlyIsNull は
    //    上の sort_order でソートするメソッドに置き換える（または呼び出し側で変更）
    
    

}
