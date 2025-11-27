package com.order.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.order.entity.Store;
import com.order.repository.StoreRepository;

@Service
public class StoreService {

    private final StoreRepository storeRepository;

    public StoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public List<Store> getAllStores() {
        return storeRepository.findAll();
    }
    /**
     * 指定された店舗IDで店舗情報を取得するよ。
     * Optional<Store> を返すのは、そのIDの店舗が見つからない可能性もあるからだよ。
     *
     * @param storeId 取得したい店舗のID
     * @return 店舗情報が存在すればStoreオブジェクトを包んだOptional、存在しなければ空のOptional
     */
    public Optional<Store> getStoreById(Integer storeId) {
        // Repositoryを使ってデータベースからIDで店舗を探してるよ
        return storeRepository.findById(storeId);
    }

    /**
     * 店舗情報を更新するよ。
     * 新しい情報がセットされたStoreオブジェクトを受け取って、データベースに保存するんだ。
     * JpaRepositoryのsaveメソッドは、IDがあれば更新、なければ新規追加してくれる優れものだけど、
     * 今回は「編集のみ」だから、常に既存の情報を更新する形で使うことになるね。
     *
     * @param store 更新する店舗情報がセットされたStoreオブジェクト
     * @return 更新されたStoreオブジェクト（DBに保存された状態）
     */
    public Store updateStore(Store store) {
        // Repositoryを使ってデータベースに店舗情報を保存（更新）してるよ
        return storeRepository.save(store);
    }

    /**
     * 新しい店舗を作成します
     * 
     * @param storeName 店舗名
     * @return 作成された店舗オブジェクト
     * @throws IllegalArgumentException 店舗名が不正な場合
     */
    public Store createStore(String storeName) {
        if (storeName == null || storeName.trim().isEmpty()) {
            throw new IllegalArgumentException("店舗名を入力してください。");
        }

        Store newStore = new Store();
        newStore.setStoreName(storeName.trim());
        return storeRepository.save(newStore);
    }
}
