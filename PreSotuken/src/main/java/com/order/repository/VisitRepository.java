package com.order.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.order.entity.Visit;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Integer> {
	List<Visit> findByStore_StoreId(Integer storeId);
	Visit findFirstBySeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(int seatId);
	List<Visit> findByStore_StoreIdAndLeaveTimeIsNull(Integer storeId);
	Visit findTopByStore_StoreIdAndSeat_SeatIdOrderByVisitTimeDesc(Integer storeId, Integer seatId);
	Visit findFirstBySeat_Store_StoreIdAndSeat_SeatIdAndLeaveTimeIsNullOrderByVisitTimeDesc(
		    int storeId, int seatId);

	/**
	 * 指定された店舗と期間における来店人数の合計を取得します
	 * @param storeId 店舗ID
	 * @param start 集計開始日時
	 * @param end 集計終了日時
	 * @return 来店人数の合計
	 */
	@Query("""
		SELECT COALESCE(SUM(v.numberOfPeople), 0)
		FROM Visit v
		WHERE v.visitId IN (
			SELECT DISTINCT p.visit.visitId
			FROM Payment p
			WHERE p.store.storeId = :storeId
			  AND p.paymentTime >= :start
			  AND p.paymentTime < :end
			  AND p.visitCancel = false
			  AND COALESCE(p.cancel, false) = false
		)
	""")
	Long sumNumberOfPeopleByPaymentTime(
		@Param("storeId") Integer storeId,
		@Param("start") LocalDateTime start,
		@Param("end") LocalDateTime end
	);

}
