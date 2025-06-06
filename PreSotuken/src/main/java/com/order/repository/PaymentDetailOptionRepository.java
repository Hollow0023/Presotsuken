package com.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.order.entity.PaymentDetailOption; // PaymentDetailOption エンティティをインポート

@Repository // Spring Beanとして登録されることを示すアノテーション
public interface PaymentDetailOptionRepository extends JpaRepository<PaymentDetailOption, Integer> {
    // JpaRepository を継承することで、PaymentDetailOption エンティティの
    // CRUD操作（save, findById, findAll, deleteなど）が自動的に提供されます。
    // 必要に応じて、ここにカスタムクエリメソッドを追加できます。
}