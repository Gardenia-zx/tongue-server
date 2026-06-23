package com.tongue.server.controller;

import com.tongue.server.common.ApiResponse;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.tongue.dto.EvidenceResponse;
import com.tongue.server.tongue.dto.FeatureResponse;
import com.tongue.server.tongue.dto.ReportDetailResponse;
import com.tongue.server.tongue.dto.ReportListItemResponse;
import com.tongue.server.tongue.dto.ReportVersionResponse;
import com.tongue.server.tongue.dto.TaskStatusResponse;
import com.tongue.server.tongue.dto.TongueAnalyzeCreateResponse;
import com.tongue.server.tongue.service.TongueAnalysisAppService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tongue")
public class TongueAnalyzeController {

    private final TongueAnalysisAppService tongueAnalysisAppService;

    public TongueAnalyzeController(TongueAnalysisAppService tongueAnalysisAppService) {
        this.tongueAnalysisAppService = tongueAnalysisAppService;
    }

    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApiResponse<TongueAnalyzeCreateResponse> analyze(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "threadId", required = false) String threadId,
            @RequestParam(value = "clientTraceId", required = false) String clientTraceId,
            @RequestParam(value = "userDescription", required = false) String userDescription,
            @RequestParam(value = "user_description", required = false) String userDescriptionSnake
    ) {
        String traceId = StringUtils.hasText(clientTraceId)
                ? clientTraceId.trim()
                : "trace_" + UUID.randomUUID().toString();
        if (image == null || image.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_EMPTY, "请上传舌象图片", traceId);
        }

        TongueAnalyzeCreateResponse response = tongueAnalysisAppService.createAnalysis(
                image,
                conversationId,
                threadId,
                traceId,
                StringUtils.hasText(userDescription) ? userDescription : userDescriptionSnake
        );
        return ApiResponse.success(response, traceId);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskStatusResponse> task(@PathVariable Long taskId) {
        return ApiResponse.success(tongueAnalysisAppService.taskStatus(taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<TaskStatusResponse> retry(@PathVariable Long taskId) {
        return ApiResponse.success(tongueAnalysisAppService.retry(taskId));
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReportListItemResponse>> reports() {
        return ApiResponse.success(tongueAnalysisAppService.reports());
    }

    @GetMapping("/reports/{reportId}")
    public ApiResponse<ReportDetailResponse> report(@PathVariable Long reportId) {
        return ApiResponse.success(tongueAnalysisAppService.reportDetail(reportId));
    }

    @GetMapping("/reports/{reportId}/versions")
    public ApiResponse<List<ReportVersionResponse>> versions(@PathVariable Long reportId) {
        return ApiResponse.success(tongueAnalysisAppService.versions(reportId));
    }

    @GetMapping("/reports/{reportId}/features")
    public ApiResponse<List<FeatureResponse>> features(@PathVariable Long reportId) {
        return ApiResponse.success(tongueAnalysisAppService.features(reportId));
    }

    @GetMapping("/reports/{reportId}/evidence")
    public ApiResponse<List<EvidenceResponse>> evidence(@PathVariable Long reportId) {
        return ApiResponse.success(tongueAnalysisAppService.evidence(reportId));
    }

    @PostMapping("/reports/{reportId}/export")
    public ApiResponse<ReportDetailResponse> export(@PathVariable Long reportId) {
        return ApiResponse.success(tongueAnalysisAppService.reportDetail(reportId));
    }

    @DeleteMapping("/reports/{reportId}")
    public ApiResponse<Object> deleteReport(@PathVariable Long reportId) {
        tongueAnalysisAppService.deleteReport(reportId);
        return ApiResponse.success(null);
    }
}
