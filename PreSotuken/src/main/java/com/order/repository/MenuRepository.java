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
	List<Menu> findByStore_StoreIdAndDeletedAtIsNull(Integer storeId);
	
	@Query("SELECT m FROM Menu m WHERE :now BETWEEN m.timeSlot.startTime AND m.timeSlot.endTime AND m.deletedAt IS NULL")
	List<Menu> findMenusAvailableAt(@Param("now") LocalTime now);

    // isPlanStarterがtrueのメニューを検索（削除されていないもののみ）
    List<Menu> findByIsPlanStarterTrueAndDeletedAtIsNull();

    // isPlanStarterがtrueで、特定のplanIdを持つメニューを検索（削除されていないもののみ）
    List<Menu> findByIsPlanStarterTrueAndPlanIdAndDeletedAtIsNull(Integer planId);
    
    // getMenusWithOptions で使う: 特定の店舗で、品切れでなく、指定された時間帯に利用可能で、メニュー名順にソートされたメニューを取得（削除されていないもののみ）
    @Query("SELECT m FROM Menu m JOIN m.timeSlot ts WHERE m.store.storeId = :storeId AND m.isSoldOut = FALSE AND ts.timeSlotId = :timeSlotId AND m.deletedAt IS NULL ORDER BY m.menuName ASC")
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndMenuTimeSlotTimeSlotIdOrderByMenuNameAsc(Integer storeId, Integer timeSlotId);

    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndTimeSlot_TimeSlotIdInAndDeletedAtIsNullOrderByMenuNameAsc(Integer storeId, List<Integer> timeSlotTimeSlotIds);
 
    // 特定の店舗のメニューをmenu_nameでソートして取得 (管理者用・品切れ表示しない場合、削除されていないもののみ)
    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndDeletedAtIsNullOrderByMenuNameAsc(Integer storeId);

    // 特定の店舗の全てのメニューをmenu_nameでソートして取得 (管理者用・品切れも表示する場合、削除されていないもののみ)
    List<Menu> findByStore_StoreIdAndDeletedAtIsNullOrderByMenuIdAsc(Integer storeId);

    // isPlanStarterがtrueのメニューをmenu_nameでソートして取得（削除されていないもののみ）
    List<Menu> findByIsPlanStarterTrueAndDeletedAtIsNullOrderByMenuNameAsc();

    // isPlanStarterがtrueで、特定のplanIdを持つメニューをmenu_nameでソートして取得（削除されていないもののみ）
    List<Menu> findByIsPlanStarterTrueAndPlanIdAndDeletedAtIsNullOrderByMenuNameAsc(Integer planId);
    
    // 特定のメニューグループに属するメニューを取得（削除されていないもののみ）
    List<Menu> findByMenuGroup_GroupIdAndDeletedAtIsNull(Integer groupId);
    
//    List<Menu> findByStore_StoreIdAndIsSoldOutFalseAndIsPlanTargetFalseAndTimeSlot_TimeSlotIdInOrderByMenuNameAsc(
//            Integer storeId, List<Integer> timeSlotIds);
//
//    @Query("SELECT m FROM Menu m " +
//           "JOIN m.menuGroup mg " +
//           "WHERE m.store.storeId = :storeId " +
//           "AND m.isSoldOut = FALSE " +
//           "AND m.timeSlot.timeSlotId IN :timeSlotIds " +
//           "AND mg.groupId IN :menuGroupIds " + // ★ここを mg.groupId に修正するよ！
//           "ORDER BY m.menuName ASC")
//    List<Menu> findByStoreIdAndSoldOutFalseAndMenuGroupIdsInAndTimeSlotIdsIn(
//            @Param("storeId") Integer storeId,
//            @Param("menuGroupIds") Set<Integer> menuGroupIds,
//            @Param("timeSlotIds") List<Integer> timeSlotIds);
//

}
