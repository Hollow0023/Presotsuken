package com.order.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageUploadService {

    @Value("${upload.path}")
    private String uploadBasePath;

    public String uploadImage(MultipartFile file, int storeId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("ファイルが空です");
        }

        // 保存先ディレクトリの作成
        Path storeDir = Paths.get(uploadBasePath, String.valueOf(storeId));
        if (!Files.exists(storeDir)) {
            Files.createDirectories(storeDir);
        }

        // UUIDでファイル名を作成（拡張子は.jpg固定）
        String fileName = UUID.randomUUID().toString() + ".jpg";
        Path filePath = storeDir.resolve(fileName);

        // ファイルを保存
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Webからアクセスするためのパスを返す
        return "/images/" + storeId + "/" + fileName;
    }
}
