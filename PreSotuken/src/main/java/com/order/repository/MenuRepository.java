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
    
    // getMenusWithOptions で使う: 特定の店舗で、品切れでなく、指定された時間帯に利用可能で、メニュー名順にソートされたメニューを取得
    @Query("SELECT m FROM Menu m JOIN m.timeSlot ts WHERE m.store.storeId = :storeId AND m.isSoldOut = FALSE AND ts.timeSlotId = :timeSlotId ORDER BY m.menuName ASC")
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndMenuTimeSlotTimeSlotIdOrderByMenuNameAsc(Integer storeId, Integer timeSlotId);

    
    
    
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndTimeSlot_TimeSlotIdInOrderByMenuNameAsc(Integer storeId, List<Integer> timeSlotTimeSlotIds);
 
    
    
 // 特定の店舗のメニューをmenu_nameでソートして取得 (管理者用・品切れ表示しない場合)
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseOrderByMenuNameAsc(Integer storeId); // ★ 修正

    // 特定の店舗の全てのメニューをmenu_nameでソートして取得 (管理者用・品切れも表示する場合)
    List<Menu> findByStore_StoreIdOrderByMenuIdAsc(Integer storeId); // ★ 修正

    // isPlanStarterがtrueのメニューをmenu_nameでソートして取得
    List<Menu> findByIsPlanStarterTrueOrderByMenuNameAsc(); // ★ 修正

    // isPlanStarterがtrueで、特定のplanIdを持つメニューをmenu_nameでソートして取得
    List<Menu> findByIsPlanStarterTrueAndPlanIdOrderByMenuNameAsc(Integer planId); // ★ 修正


}
