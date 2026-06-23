package com.tongue.server.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongue.server.auth.AuthContext;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import com.tongue.server.notification.service.NotificationService;
import com.tongue.server.review.dto.CreateReviewRequest;
import com.tongue.server.review.dto.ReviewOrderResponse;
import com.tongue.server.review.dto.SubmitReviewRequest;
import com.tongue.server.review.entity.DoctorReviewCommentEntity;
import com.tongue.server.review.entity.DoctorReviewOrderEntity;
import com.tongue.server.review.repository.DoctorReviewCommentRepository;
import com.tongue.server.review.repository.DoctorReviewOrderRepository;
import com.tongue.server.tongue.entity.TongueReportEntity;
import com.tongue.server.tongue.entity.TongueReportVersionEntity;
import com.tongue.server.tongue.repository.TongueReportRepository;
import com.tongue.server.tongue.repository.TongueReportVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewService {

    private final DoctorReviewOrderRepository orderRepository;
    private final DoctorReviewCommentRepository commentRepository;
    private final TongueReportRepository reportRepository;
    private final TongueReportVersionRepository versionRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReviewService(
            DoctorReviewOrderRepository orderRepository,
            DoctorReviewCommentRepository commentRepository,
            TongueReportRepository reportRepository,
            TongueReportVersionRepository versionRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.versionRepository = versionRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewOrderResponse create(CreateReviewRequest request) {
        Long userId = AuthContext.requireUserId();
        TongueReportEntity report = reportRepository.findByIdAndUserIdAndDeletedAtIsNull(
                request.reportId,
                userId
        ).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报告不存在", null));

        DoctorReviewOrderEntity order = new DoctorReviewOrderEntity();
        order.userId = userId;
        order.reportId = report.id;
        order.status = "SUBMITTED";
        order.userRemark = request.userRemark;
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public ReviewOrderResponse detail(Long reviewId) {
        DoctorReviewOrderEntity order = loadVisible(reviewId);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<ReviewOrderResponse> myOrders() {
        Long userId = AuthContext.requireUserId();
        List<ReviewOrderResponse> result = new ArrayList<ReviewOrderResponse>();
        for (DoctorReviewOrderEntity order : orderRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            result.add(toResponse(order));
        }
        return result;
    }

    @Transactional
    public ReviewOrderResponse cancel(Long reviewId) {
        Long userId = AuthContext.requireUserId();
        DoctorReviewOrderEntity order = orderRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审核单不存在", null));
        if (!userId.equals(order.userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有审核单访问权限", null);
        }
        order.status = "CANCELED";
        order.canceledAt = LocalDateTime.now();
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<ReviewOrderResponse> doctorQueue() {
        AuthContext.requireRole("DOCTOR");
        Long doctorUserId = AuthContext.requireUserId();
        List<ReviewOrderResponse> result = new ArrayList<ReviewOrderResponse>();
        for (DoctorReviewOrderEntity order : orderRepository.findByStatusOrderByCreatedAtAsc("SUBMITTED")) {
            result.add(toResponse(order));
        }
        for (DoctorReviewOrderEntity order : orderRepository.findByDoctorUserIdOrderByCreatedAtDesc(doctorUserId)) {
            result.add(toResponse(order));
        }
        return result;
    }

    @Transactional
    public ReviewOrderResponse accept(Long reviewId) {
        AuthContext.requireRole("DOCTOR");
        Long doctorUserId = AuthContext.requireUserId();
        DoctorReviewOrderEntity order = orderRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审核单不存在", null));
        if (!"SUBMITTED".equals(order.status)) {
            throw new BusinessException(ErrorCode.TASK_NOT_RETRYABLE, "当前审核单不可接单", null);
        }
        order.doctorUserId = doctorUserId;
        order.status = "IN_REVIEW";
        order.acceptedAt = LocalDateTime.now();
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public ReviewOrderResponse submit(Long reviewId, SubmitReviewRequest request) {
        AuthContext.requireRole("DOCTOR");
        Long doctorUserId = AuthContext.requireUserId();
        DoctorReviewOrderEntity order = orderRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审核单不存在", null));
        if (!doctorUserId.equals(order.doctorUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只能提交自己接单的审核", null);
        }

        DoctorReviewCommentEntity comment = new DoctorReviewCommentEntity();
        comment.reviewOrderId = order.id;
        comment.doctorUserId = doctorUserId;
        comment.commentText = request.commentText;
        try {
            comment.revisedReportJson = objectMapper.writeValueAsString(request.revisedReport);
        } catch (Exception ex) {
            comment.revisedReportJson = "{}";
        }
        commentRepository.save(comment);

        TongueReportVersionEntity version = new TongueReportVersionEntity();
        version.reportId = order.reportId;
        version.versionNo = Long.valueOf(versionRepository.countByReportId(order.reportId) + 1L).intValue();
        version.sourceType = "DOCTOR";
        version.summary = request.commentText;
        version.reportJson = comment.revisedReportJson;
        version.doctorReviewId = order.id;
        versionRepository.save(version);

        order.status = "COMPLETED";
        order.completedAt = LocalDateTime.now();
        orderRepository.save(order);

        notificationService.create(
                order.userId,
                "DOCTOR_REVIEW_COMPLETED",
                "医生审核已完成",
                "你的舌象报告医生审核已完成。",
                "{\"review_id\":" + order.id + ",\"report_id\":" + order.reportId + "}"
        );
        return toResponse(order);
    }

    private DoctorReviewOrderEntity loadVisible(Long reviewId) {
        Long userId = AuthContext.requireUserId();
        DoctorReviewOrderEntity order = orderRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审核单不存在", null));
        if (!userId.equals(order.userId)
                && !userId.equals(order.doctorUserId)
                && !"ADMIN".equals(AuthContext.get().role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "没有审核单访问权限", null);
        }
        return order;
    }

    public ReviewOrderResponse toResponse(DoctorReviewOrderEntity order) {
        ReviewOrderResponse response = new ReviewOrderResponse();
        response.reviewId = order.id;
        response.reportId = order.reportId;
        response.userId = order.userId;
        response.doctorUserId = order.doctorUserId;
        response.status = order.status;
        response.payStatus = order.payStatus;
        response.priceAmount = order.priceAmount;
        response.userRemark = order.userRemark;
        response.createdAt = order.createdAt;
        return response;
    }
}
