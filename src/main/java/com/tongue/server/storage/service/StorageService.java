package com.tongue.server.storage.service;

import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.StorageProperties;
import com.tongue.server.service.LocalFileStorageService;
import com.tongue.server.service.StoredImageFile;
import com.tongue.server.storage.StorageResult;
import com.tongue.server.storage.dto.FileUploadResponse;
import com.tongue.server.storage.entity.FileObjectEntity;
import com.tongue.server.storage.entity.TongueImageFileEntity;
import com.tongue.server.storage.repository.FileObjectRepository;
import com.tongue.server.storage.repository.TongueImageFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class StorageService {

    private final StorageProperties storageProperties;
    private final LocalFileStorageService localFileStorageService;
    private final FileObjectRepository fileObjectRepository;
    private final TongueImageFileRepository tongueImageFileRepository;

    public StorageService(
            StorageProperties storageProperties,
            LocalFileStorageService localFileStorageService,
            FileObjectRepository fileObjectRepository,
            TongueImageFileRepository tongueImageFileRepository
    ) {
        this.storageProperties = storageProperties;
        this.localFileStorageService = localFileStorageService;
        this.fileObjectRepository = fileObjectRepository;
        this.tongueImageFileRepository = tongueImageFileRepository;
    }

    @Transactional
    public StorageResult storeTongueImage(
            Long userId,
            Long reportId,
            MultipartFile image,
            String traceId
    ) {
        StoredImageFile stored = localFileStorageService.storeTongueImage(
                userId,
                reportId,
                image,
                traceId
        );

        FileObjectEntity fileObject = new FileObjectEntity();
        fileObject.ownerUserId = userId;
        fileObject.storageMode = storageProperties.getMode();
        fileObject.bucket = "local";
        fileObject.objectKey = stored.getStoragePath();
        fileObject.storagePath = stored.getStoragePath();
        fileObject.publicUrl = buildPublicUrl(stored.getStoragePath());
        fileObject.originalFilename = stored.getOriginalFilename();
        fileObject.contentType = stored.getContentType();
        fileObject.fileSize = stored.getFileSize();
        fileObject.purpose = "tongue_image";
        fileObjectRepository.save(fileObject);

        TongueImageFileEntity imageFile = new TongueImageFileEntity();
        imageFile.userId = userId;
        imageFile.fileObjectId = fileObject.id;
        imageFile.reportId = reportId;
        imageFile.purpose = "tongue_image";
        tongueImageFileRepository.save(imageFile);

        StorageResult result = new StorageResult();
        result.fileObjectId = fileObject.id;
        result.tongueImageFileId = imageFile.id;
        result.storagePath = stored.getStoragePath();
        result.imageUrl = fileObject.publicUrl;
        result.contentType = stored.getContentType();
        result.fileSize = stored.getFileSize();
        result.originalFilename = stored.getOriginalFilename();
        return result;
    }

    @Transactional(readOnly = true)
    public FileUploadResponse viewUrl(Long fileId) {
        Long userId = AuthContext.requireUserId();
        FileObjectEntity file = fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "文件不存在",
                        null
                ));
        if (!userId.equals(file.ownerUserId) && !"ADMIN".equals(AuthContext.get().role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有文件访问权限", null);
        }
        return toResponse(file);
    }

    @Transactional
    public void deleteFile(Long fileId) {
        Long userId = AuthContext.requireUserId();
        FileObjectEntity file = fileObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "文件不存在",
                        null
                ));
        if (!userId.equals(file.ownerUserId) && !"ADMIN".equals(AuthContext.get().role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有文件删除权限", null);
        }
        file.status = "DELETED";
        file.deletedAt = LocalDateTime.now();
        fileObjectRepository.save(file);
    }

    public FileUploadResponse toResponse(FileObjectEntity file) {
        FileUploadResponse response = new FileUploadResponse();
        response.fileId = file.id;
        response.viewUrl = StringUtils.hasText(file.publicUrl) ? file.publicUrl : file.storagePath;
        response.contentType = file.contentType;
        response.fileSize = file.fileSize;
        response.originalFilename = file.originalFilename;
        return response;
    }

    private String buildPublicUrl(String storagePath) {
        if (!StringUtils.hasText(storageProperties.getPublicBaseUrl())) {
            return null;
        }
        return storageProperties.getPublicBaseUrl().replaceAll("/+$", "")
                + "/files/"
                + storagePath.replace("\\", "/");
    }
}
