package com.order.entity; // パッケージ名は陽翔君のプロジェクトに合わせてね

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table; // @Table をインポート
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "plan") // テーブル名がクラス名と異なる場合や明示的に指定する場合に使う
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id") // DBのカラム名とマッピング
    private Integer planId;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "plan_description")
    private String planDescription;

    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store; // Storeエンティティとの関連付け
}