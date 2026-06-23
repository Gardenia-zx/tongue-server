package com.tongue.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.config.StorageProperties;
import com.tongue.server.dto.AgentRunResponse;
import com.tongue.server.dto.TongueAnalyzeResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LocalReportStorageService {

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    public LocalReportStorageService(
            StorageProperties storageProperties,
            ObjectMapper objectMapper
    ) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
    }

    public void saveAgentResult(
            Long userId,
            Long reportId,
            TongueAnalyzeResponse response,
            AgentRunResponse agentResponse,
            String traceId
    ) {
        Path userDir = Paths.get(
                storageProperties.getReportRoot(),
                String.valueOf(userId)
        ).toAbsolutePath().normalize();
        Path destination = userDir.resolve(reportId + ".json").normalize();

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("saved_at", OffsetDateTime.now().toString());
        payload.put("frontend_response", response);
        payload.put("agent_response", agentResponse);

        try {
            Files.createDirectories(userDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), payload);
        } catch (IOException ex) {
            throw new BusinessException(
                    ErrorCode.REPORT_SAVE_FAILED,
                    "报告结果保存失败",
                    traceId,
                    ex
            );
        }
    }
}
