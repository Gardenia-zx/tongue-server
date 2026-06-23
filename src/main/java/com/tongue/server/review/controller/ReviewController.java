package com.tongue.server.review.controller;

import com.tongue.server.common.ApiResponse;
import com.tongue.server.review.dto.CreateReviewRequest;
import com.tongue.server.review.dto.ReviewOrderResponse;
import com.tongue.server.review.dto.SubmitReviewRequest;
import com.tongue.server.review.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    public ApiResponse<ReviewOrderResponse> create(
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.create(request));
    }

    @GetMapping("/reviews/{reviewId}")
    public ApiResponse<ReviewOrderResponse> detail(@PathVariable Long reviewId) {
        return ApiResponse.success(reviewService.detail(reviewId));
    }

    @GetMapping("/reviews/my")
    public ApiResponse<List<ReviewOrderResponse>> my() {
        return ApiResponse.success(reviewService.myOrders());
    }

    @PostMapping("/reviews/{reviewId}/cancel")
    public ApiResponse<ReviewOrderResponse> cancel(@PathVariable Long reviewId) {
        return ApiResponse.success(reviewService.cancel(reviewId));
    }

    @GetMapping("/doctor/reviews")
    public ApiResponse<List<ReviewOrderResponse>> doctorQueue() {
        return ApiResponse.success(reviewService.doctorQueue());
    }

    @PostMapping("/doctor/reviews/{reviewId}/accept")
    public ApiResponse<ReviewOrderResponse> accept(@PathVariable Long reviewId) {
        return ApiResponse.success(reviewService.accept(reviewId));
    }

    @PostMapping("/doctor/reviews/{reviewId}/submit")
    public ApiResponse<ReviewOrderResponse> submit(
            @PathVariable Long reviewId,
            @RequestBody SubmitReviewRequest request
    ) {
        return ApiResponse.success(reviewService.submit(reviewId, request));
    }
}
