package com.order.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 割り勘会計リクエスト
 */
@Data
public class SplitPaymentRequest {
    private Integer paymentId;        // 元の会計ID
    private Integer numberOfSplits;   // 分割数 (何人で割るか)
    private Integer currentSplit;     // 現在何番目の会計か (1から始まる)
    private Integer paymentTypeId;    // 支払い種別ID
    private Integer cashierId;        // 担当者ID
    private Double deposit;           // 預かり金額
    private LocalDateTime paymentTime; // 会計時刻
}
