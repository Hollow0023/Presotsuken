package com.order.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageUploadService {

    // 画像の保存ベースディレクトリ (application.propertiesなどで設定)
    // upload.path を使うように変更
    @Value("${upload.path}") // ★ここを修正！
    private String uploadDir; // 変数名はそのまま uploadDir でOK

    /**
     * アップロードされた画像を保存し、保存された画像のパスを返します。
     * 画像は `uploadDir/{storeId}/{uuid}.jpg` の形式で保存されます。
     *
     * @param imageFile アップロードされたMultipartFile
     * @param storeId 店舗ID
     * @return DBに保存する画像の相対パス (例: /images/{storeId}/{uuid}.jpg)
     * @throws IOException ファイルの保存中にエラーが発生した場合
     */
    public String uploadImage(MultipartFile imageFile, Integer storeId) throws IOException {
        if (imageFile.isEmpty()) {
            throw new IOException("アップロードされたファイルが空です。");
        }

        // uploadDir が "file:./src/main/resources/static/images/" のように "file:" プレフィックスを持つ場合、
        // それを取り除く必要がある。
        String cleanUploadDir = uploadDir.replace("file:", "");

        // 保存先ディレクトリのパスを構築
        // 例: ./src/main/resources/static/images/{storeId}/
        Path storeDirPath = Paths.get(cleanUploadDir, String.valueOf(storeId)).toAbsolutePath().normalize();

        // ディレクトリが存在しない場合は作成
        if (!Files.exists(storeDirPath)) {
            Files.createDirectories(storeDirPath);
        }

        // ユニークなファイル名を生成 (UUID + 拡張子)
        String originalFilename = imageFile.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFileName = UUID.randomUUID().toString() + ".jpg"; 

        // ファイルの保存パス
        Path filePath = storeDirPath.resolve(newFileName);

        // ファイルを書き込み
        Files.copy(imageFile.getInputStream(), filePath);

        // DBに保存する相対パスを返す
        // Webアクセスパスは /images/{storeId}/{uuid}.jpg になる想定
        return "/images/" + storeId + "/" + newFileName;
    }

    /**
     * 指定されたパスの画像ファイルを削除します。
     *
     * @param imagePath DBに保存されている画像のパス (例: /images/{storeId}/{uuid}.jpg)
     * @throws IOException ファイルの削除中にエラーが発生した場合
     */
    public void deleteImage(String imagePath) throws IOException {
        if (imagePath == null || imagePath.isEmpty()) {
            return;
        }

        String cleanUploadDir = uploadDir.replace("file:", "");

        // DBパスから物理ファイルパスを構築
        // "/images/123/abc.jpg" -> "./src/main/resources/static/images/123/abc.jpg"
        Path filePathToDelete = Paths.get(cleanUploadDir, imagePath.replace("/images/", "")).toAbsolutePath().normalize();

        if (Files.exists(filePathToDelete)) {
            Files.delete(filePathToDelete);
            System.out.println("Deleted image file: " + filePathToDelete);
        } else {
            System.out.println("Image file not found for deletion: " + filePathToDelete);
        }
    }
}