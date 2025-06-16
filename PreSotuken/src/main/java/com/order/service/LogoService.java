package com.order.service; // パッケージ名はcom.order.service に合わせるね！

import java.util.Optional; // Optionalをインポート

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // トランザクション管理のためにインポート

import com.order.entity.Logo; // Logoエンティティをインポート
import com.order.repository.LogoRepository; // LogoRepositoryをインポート

/**
 * ロゴに関するビジネスロジックを提供するサービス。
 */
@Service // これでSpringがこのクラスをサービスコンポーネントとして認識するよ
@Transactional // クラス内のPublicメソッド全てにトランザクションを適用する（必要に応じてメソッド単位でもOK）
public class LogoService {

    private final LogoRepository logoRepository; // リポジトリをインジェクションするフィールド
    
    

    // コンストラクタインジェクションを使うのがSpring Bootでは推奨だよ
    public LogoService(LogoRepository logoRepository) {
        this.logoRepository = logoRepository;
    }
    
    /**
     * 指定された店舗IDに紐づくロゴのBASE64エンコードされたテキストデータを取得する。
     *
     * @param storeId 取得対象の店舗ID
     * @return 存在する場合はBASE64エンコードされたロゴデータ文字列、
     * 存在しない場合はnull
     */
    @Transactional(readOnly = true)
    public String getLogoBase64Data(Long storeId) {
        // findLogoByStoreIdメソッドを使ってOptional<Logo>を取得
        Optional<Logo> logoOptional = findLogoByStoreId(storeId);

        // Optionalが値を保持していればそのlogoDataを返し、そうでなければnullを返す
        return logoOptional.map(Logo::getLogoData).orElse(null);
        // これは以下のコードと同じ意味だよ：
        // if (logoOptional.isPresent()) {
        //     return logoOptional.get().getLogoData();
        // } else {
        //     return null;
        // }
    }

    /**
     * 指定された店舗IDに紐づくロゴ情報を取得する。
     *
     * @param storeId 取得対象の店舗ID
     * @return 存在する場合はLogoエンティティをOptionalでラップしたもの、存在しない場合はOptional.empty()
     */
    @Transactional(readOnly = true) // 読み取り専用のトランザクションはパフォーマンスが向上する可能性があるよ
    public Optional<Logo> findLogoByStoreId(Long storeId) {
        return logoRepository.findById(storeId);
    }

    /**
     * ロゴ情報を保存または更新する。
     * 同じstoreIdのロゴが存在する場合は上書きし、存在しない場合は新規登録する。
     *
     * @param storeId 保存または更新する店舗ID
     * @param logoData BASE64エンコードされたロゴデータ
     * @return 保存または更新されたLogoエンティティ
     */
    public Logo saveOrUpdateLogo(Long storeId, String logoData) {
        // 同じstoreIdのロゴが存在するかをチェック
        Optional<Logo> existingLogo = logoRepository.findById(storeId);

        Logo logo;
        if (existingLogo.isPresent()) {
            // 既存のロゴがあれば上書き
            logo = existingLogo.get();
            logo.setLogoData(logoData); // ロゴデータを更新
            // idはFKなので、ここでsetId(storeId)は不要。
            // 既存エンティティに対してはIDがすでにセットされているため。
        } else {
            // 既存のロゴがなければ新規作成
            logo = new Logo(storeId, logoData); // storeIdをidとして設定
        }

        return logoRepository.save(logo); // 保存または更新を実行
    }
}