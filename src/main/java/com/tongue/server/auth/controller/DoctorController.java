package com.tongue.server.auth.controller;

import com.tongue.server.auth.dto.DoctorProfileResponse;
import com.tongue.server.auth.dto.DoctorProfileUpsertRequest;
import com.tongue.server.auth.service.DoctorService;
import com.tongue.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public ApiResponse<List<DoctorProfileResponse>> doctors() {
        return ApiResponse.success(doctorService.approvedDoctors());
    }

    @GetMapping("/{doctorId}")
    public ApiResponse<DoctorProfileResponse> doctor(@PathVariable Long doctorId) {
        return ApiResponse.success(doctorService.doctor(doctorId));
    }

    @PutMapping("/me/profile")
    public ApiResponse<DoctorProfileResponse> upsertMe(
            @RequestBody DoctorProfileUpsertRequest request
    ) {
        return ApiResponse.success(doctorService.upsertMyProfile(request));
    }
}
