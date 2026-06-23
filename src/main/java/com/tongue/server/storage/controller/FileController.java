package com.tongue.server.storage.controller;

import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.ApiResponse;
import com.tongue.server.storage.StorageResult;
import com.tongue.server.storage.dto.FileUploadResponse;
import com.tongue.server.storage.service.StorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(
            value = "/upload/tongue-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<FileUploadResponse> uploadTongueImage(
            @RequestParam("image") MultipartFile image
    ) {
        Long userId = AuthContext.requireUserId();
        StorageResult result = storageService.storeTongueImage(
                userId,
                0L,
                image,
                "trace_" + UUID.randomUUID()
        );
        FileUploadResponse response = new FileUploadResponse();
        response.fileId = result.fileObjectId;
        response.viewUrl = result.imageUrl;
        response.contentType = result.contentType;
        response.fileSize = result.fileSize;
        response.originalFilename = result.originalFilename;
        return ApiResponse.success(response);
    }

    @GetMapping("/{fileId}/view-url")
    public ApiResponse<FileUploadResponse> viewUrl(@PathVariable Long fileId) {
        return ApiResponse.success(storageService.viewUrl(fileId));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Object> delete(@PathVariable Long fileId) {
        storageService.deleteFile(fileId);
        return ApiResponse.success(null);
    }
}
