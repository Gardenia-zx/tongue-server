package com.tongue.server.service;

import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LocalFileStorageService {

    private final StorageProperties storageProperties;
    private final AtomicLong fileIdGenerator = new AtomicLong(System.currentTimeMillis());

    public LocalFileStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public StoredImageFile storeTongueImage(
            Long userId,
            Long reportId,
            MultipartFile image,
            String traceId
    ) {
        validateImage(image, traceId);

        Long fileId = fileIdGenerator.incrementAndGet();
        String contentType = image.getContentType().toLowerCase(Locale.ROOT);
        String extension = extensionForContentType(contentType);
        String filename = "tongue_" + fileId + extension;

        Path reportDir = Paths.get(
                storageProperties.getUploadRoot(),
                String.valueOf(userId),
                String.valueOf(reportId)
        ).toAbsolutePath().normalize();
        Path destination = reportDir.resolve(filename).normalize();

        try {
            Files.createDirectories(reportDir);
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException(
                    ErrorCode.REPORT_SAVE_FAILED,
                    "舌象图片保存失败",
                    traceId,
                    ex
            );
        }

        StoredImageFile stored = new StoredImageFile();
        stored.setFileId(fileId);
        stored.setUserId(userId);
        stored.setReportId(reportId);
        stored.setStoragePath(destination.toString());
        stored.setOriginalFilename(image.getOriginalFilename());
        stored.setContentType(contentType);
        stored.setFileSize(image.getSize());
        return stored;
    }

    private void validateImage(MultipartFile image, String traceId) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_EMPTY, "请上传舌象图片", traceId);
        }

        if (image.getSize() > storageProperties.getMaxImageSizeBytes()) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE, "图片大小超过限制", traceId);
        }

        String contentType = image.getContentType();
        if (!StringUtils.hasText(contentType)) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_UNSUPPORTED, "无法识别图片类型", traceId);
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (!"image/jpeg".equals(normalized)
                && !"image/png".equals(normalized)
                && !"image/webp".equals(normalized)) {
            throw new BusinessException(
                    ErrorCode.IMAGE_TYPE_UNSUPPORTED,
                    "当前只支持 JPG、PNG、WEBP 格式图片",
                    traceId
            );
        }
    }

    private String extensionForContentType(String contentType) {
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }
}
