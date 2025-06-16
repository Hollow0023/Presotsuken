package com.order.repository; // パッケージ名は適宜変更してね

import java.util.Optional; // データが見つからない場合を考慮してOptionalを使うよ

import org.springframework.data.jpa.repository.JpaRepository; // JpaRepositoryをインポート
import org.springframework.stereotype.Repository; // @Repositoryを使うためにインポート

import com.order.entity.Logo;

@Repository
public interface LogoRepository extends JpaRepository<Logo, Long> {
    Optional<Logo> findById(Long storeId);
}