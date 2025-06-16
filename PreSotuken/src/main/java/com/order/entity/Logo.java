package com.order.entity; // パッケージ名は適宜変更してね

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor; // 全てのフィールドを引数にとるコンストラクタのためにインポート
import lombok.Data; // @Dataアノテーションを使うためにインポート
import lombok.NoArgsConstructor; // デフォルトコンストラクタのためにインポート

@Entity
@Table(name = "logo") // データベースのテーブル名と一致させる
@Data
@NoArgsConstructor // 引数なしのデフォルトコンストラクタを自動生成
@AllArgsConstructor // 全てのフィールドを引数にとるコンストラクタを自動生成
public class Logo {

    @Id
    @Column(name = "id")
    private Long id; // FKとしてorderテーブルのstore_idを受け取るのでLong型にする

    @Lob // LONGTEXTのような大きなテキストデータを扱う場合に指定するよ
    @Column(name = "logo", columnDefinition = "LONGTEXT") // logoカラムがLONGTEXT型であることを明示的に指定
    private String logoData; // BASE64エンコードされたロゴデータを保持する

}