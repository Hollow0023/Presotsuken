package com.order.repository;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.Menu;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Integer> {
    List<Menu> findByStore_StoreId(Integer storeId);

    @Query("SELECT m FROM Menu m WHERE :now BETWEEN m.timeSlot.startTime AND m.timeSlot.endTime")
    List<Menu> findMenusAvailableAt(@Param("now") LocalTime now);

    // isPlanStarterがtrueのメニューを検索
    List<Menu> findByIsPlanStarterTrue();

    // isPlanStarterがtrueで、特定のplanIdを持つメニューを検索
    List<Menu> findByIsPlanStarterTrueAndPlanId(Integer planId);
    
    // getMenusWithOptions で使用するクエリ。指定店舗のうち、品切れでなく選択した時間帯に属するメニューを名前順に取得する。
    @Query("SELECT m FROM Menu m JOIN m.timeSlot ts WHERE m.store.storeId = :storeId AND m.isSoldOut = FALSE AND ts.timeSlotId = :timeSlotId ORDER BY m.menuName ASC")
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndMenuTimeSlotTimeSlotIdOrderByMenuNameAsc(Integer storeId, Integer timeSlotId);

    
    
    
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndTimeSlot_TimeSlotIdInOrderByMenuNameAsc(Integer storeId, List<Integer> timeSlotTimeSlotIds);


    // 管理画面向け：品切れを除外して店舗内メニューを名前順に取得
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseOrderByMenuNameAsc(Integer storeId);

    // 管理画面向け：品切れを含めた店舗内メニューをID順に取得
    List<Menu> findByStore_StoreIdOrderByMenuIdAsc(Integer storeId);

    // 飲み放題開始メニューのみを名前順に取得
    List<Menu> findByIsPlanStarterTrueOrderByMenuNameAsc();

    // 飲み放題開始メニューのうち、指定プランに紐づくものを名前順に取得
    List<Menu> findByIsPlanStarterTrueAndPlanIdOrderByMenuNameAsc(Integer planId);
    
    
//    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndIsPlanTargetFalseAndTimeSlot_TimeSlotIdInOrderByMenuNameAsc(
//            Integer storeId, List<Integer> timeSlotIds);
//
//    @Query("SELECT m FROM Menu m " +
//           "JOIN m.menuGroup mg " +
//           "WHERE m.store.storeId = :storeId " +
//           "AND m.isSoldOut = FALSE " +
//           "AND m.timeSlot.timeSlotId IN :timeSlotIds " +
//           "AND mg.groupId IN :menuGroupIds " +
//           "ORDER BY m.menuName ASC")
//    List<Menu> findByStoreIdAndSoldOutFalseAndMenuGroupIdsInAndTimeSlotIdsIn(
//            @Param("storeId") Integer storeId,
//            @Param("menuGroupIds") Set<Integer> menuGroupIds,
//            @Param("timeSlotIds") List<Integer> timeSlotIds);
//

}
