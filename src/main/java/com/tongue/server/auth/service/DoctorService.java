package com.tongue.server.auth.service;

import com.tongue.server.auth.AuthContext;
import com.tongue.server.auth.dto.DoctorProfileResponse;
import com.tongue.server.auth.dto.DoctorProfileUpsertRequest;
import com.tongue.server.auth.entity.AppUserEntity;
import com.tongue.server.auth.entity.DoctorProfileEntity;
import com.tongue.server.auth.repository.AppUserRepository;
import com.tongue.server.auth.repository.DoctorProfileRepository;
import com.tongue.server.common.BusinessException;
import com.tongue.server.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DoctorService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final AppUserRepository appUserRepository;

    public DoctorService(
            DoctorProfileRepository doctorProfileRepository,
            AppUserRepository appUserRepository
    ) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<DoctorProfileResponse> approvedDoctors() {
        List<DoctorProfileResponse> result = new ArrayList<DoctorProfileResponse>();
        for (DoctorProfileEntity doctor : doctorProfileRepository.findByReviewStatusOrderByCreatedAtDesc("APPROVED")) {
            result.add(toResponse(doctor));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DoctorProfileResponse doctor(Long doctorId) {
        DoctorProfileEntity doctor = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "医生不存在",
                        null
                ));
        return toResponse(doctor);
    }

    @Transactional
    public DoctorProfileResponse upsertMyProfile(DoctorProfileUpsertRequest request) {
        Long userId = AuthContext.requireUserId();
        DoctorProfileEntity doctor = doctorProfileRepository.findByUserId(userId).orElse(null);
        if (doctor == null) {
            doctor = new DoctorProfileEntity();
            doctor.userId = userId;
            doctor.reviewStatus = "PENDING";
        }
        doctor.realName = request.realName;
        doctor.title = request.title;
        doctor.introduction = request.introduction;
        doctor.specialty = request.specialty;
        doctorProfileRepository.save(doctor);

        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录", null));
        user.role = "DOCTOR";
        appUserRepository.save(user);

        return toResponse(doctor);
    }

    @Transactional
    public DoctorProfileResponse approve(Long doctorId, boolean approved) {
        AuthContext.requireRole("ADMIN");
        DoctorProfileEntity doctor = doctorProfileRepository.findById(doctorId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "医生不存在",
                        null
                ));
        doctor.reviewStatus = approved ? "APPROVED" : "REJECTED";
        doctorProfileRepository.save(doctor);
        return toResponse(doctor);
    }

    public DoctorProfileResponse toResponse(DoctorProfileEntity doctor) {
        DoctorProfileResponse response = new DoctorProfileResponse();
        response.doctorId = doctor.id;
        response.userId = doctor.userId;
        response.realName = doctor.realName;
        response.title = doctor.title;
        response.introduction = doctor.introduction;
        response.specialty = doctor.specialty;
        response.reviewStatus = doctor.reviewStatus;
        return response;
    }
}
