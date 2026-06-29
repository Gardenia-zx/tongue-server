package com.tongue.server.auth.service;

import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.StorageProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProfileAvatarStorageService {

    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024L * 1024L;

    private final Path avatarRoot;

    public ProfileAvatarStorageService(StorageProperties storageProperties) {
        this.avatarRoot = Paths.get(storageProperties.getUploadRoot())
                .resolve("avatars")
                .toAbsolutePath()
                .normalize();
    }

    public String save(Long userId, MultipartFile file, String previousFileName) {
        validate(file);
        String extension = extensionFor(file.getContentType());
        String fileName = userId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolve(fileName);

        try {
            Files.createDirectories(avatarRoot);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.REPORT_SAVE_FAILED, "头像保存失败，请稍后重试", null, ex);
        }

        if (StringUtils.hasText(previousFileName) && !previousFileName.equals(fileName)) {
            deleteQuietly(previousFileName);
        }
        return fileName;
    }

    public void deleteQuietly(String fileName) {
        if (!StringUtils.hasText(fileName)) return;
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (RuntimeException | IOException ignored) {
            // 删除旧头像失败不影响当前资料保存，后续可由定时清理任务处理。
        }
    }

    public AvatarResource load(String fileName) {
        Path path = resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "头像文件不存在", null);
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            String detected = Files.probeContentType(path);
            MediaType mediaType = StringUtils.hasText(detected)
                    ? MediaType.parseMediaType(detected)
                    : MediaType.APPLICATION_OCTET_STREAM;
            return new AvatarResource(resource, mediaType);
        } catch (MalformedURLException ex) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "头像文件不可访问", null, ex);
        }
    }

    public String publicUrl(String fileName) {
        if (!StringUtils.hasText(fileName)) return null;
        return "/api/public/profile-avatars/" + fileName;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_EMPTY, "请选择头像图片", null);
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE, "头像不能超过 2MB", null);
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equalsIgnoreCase(contentType)
                && !"image/png".equalsIgnoreCase(contentType)
                && !"image/webp".equalsIgnoreCase(contentType)) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_UNSUPPORTED, "头像仅支持 JPG、PNG 或 WEBP", null);
        }
    }

    private String extensionFor(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if ("image/png".equals(normalized)) return "png";
        if ("image/webp".equals(normalized)) return "webp";
        return "jpg";
    }

    private Path resolve(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "头像文件不存在", null);
        }
        String safeName = Paths.get(fileName).getFileName().toString();
        if (!safeName.equals(fileName)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "非法头像路径", null);
        }
        Path path = avatarRoot.resolve(safeName).normalize();
        if (!path.startsWith(avatarRoot)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "非法头像路径", null);
        }
        return path;
    }

    public static class AvatarResource {
        private final Resource resource;
        private final MediaType mediaType;

        public AvatarResource(Resource resource, MediaType mediaType) {
            this.resource = resource;
            this.mediaType = mediaType;
        }

        public Resource getResource() {
            return resource;
        }

        public MediaType getMediaType() {
            return mediaType;
        }
    }
}
