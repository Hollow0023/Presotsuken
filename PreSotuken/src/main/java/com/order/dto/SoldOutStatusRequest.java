// SoldOutStatusRequest.java
package com.order.dto; // もしくは com.order.controller.request など、DTOをまとめるパッケージに

import lombok.Getter;
import lombok.Setter;

@Getter // getterメソッドを自動生成
@Setter // setterメソッドを自動生成
public class SoldOutStatusRequest {
    private Boolean isSoldOut; // 品切れ状態を表す真偽値 (true:品切れ中, false:品切れ解除)
}