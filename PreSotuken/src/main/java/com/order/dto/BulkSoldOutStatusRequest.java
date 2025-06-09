// BulkSoldOutStatusRequest.java
package com.order.dto; // 上記と同じく、DTOをまとめるパッケージに

import java.util.List; // リストを使うのでimport

import lombok.Getter;
import lombok.Setter;

@Getter // getterメソッドを自動生成
@Setter // setterメソッドを自動生成
public class BulkSoldOutStatusRequest {
    private List<Integer> menuIds; // 更新対象のメニューIDのリスト
    private Boolean isSoldOut;     // 品切れ状態 (true:品切れ中, false:品切れ解除)
}