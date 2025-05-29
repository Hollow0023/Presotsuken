package com.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.order.entity.Terminal;

public interface TerminalRepository extends JpaRepository<Terminal, Integer> {
    // storeIdとIPアドレスの複合条件でTerminalを検索
    Optional<Terminal> findByIpAddressAndStore_StoreId(String ipAddress, Integer storeId);

}
