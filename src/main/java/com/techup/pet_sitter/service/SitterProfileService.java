package com.techup.pet_sitter.service;

import com.techup.pet_sitter.dto.ProfilePayload;
import com.techup.pet_sitter.entity.PetType;
import com.techup.pet_sitter.entity.SitterPetType;
import com.techup.pet_sitter.entity.SitterPhoto;
import com.techup.pet_sitter.entity.SitterProfile;
import com.techup.pet_sitter.entity.User;
import com.techup.pet_sitter.repository.PetTypeRepository;
import com.techup.pet_sitter.repository.SitterPetTypeRepository;
import com.techup.pet_sitter.repository.SitterPhotoRepository;
import com.techup.pet_sitter.repository.SitterProfileRepository;
import com.techup.pet_sitter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SitterProfileService {

    @Autowired
    private SitterProfileRepository sitterProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SitterPetTypeRepository sitterPetTypeRepository;

    @Autowired
    private PetTypeRepository petTypeRepository;

    @Autowired
    private SitterPhotoRepository sitterPhotoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public SitterProfile create(SitterProfile sitterProfile) {
        return sitterProfileRepository.save(sitterProfile);
    }

    public List<SitterProfile> getAll() {
        return sitterProfileRepository.findAllWithUser();
    }

    // ==========================================
    // Search + status filter + pagination (mirrors PostService.getPosts)
    // ==========================================

    public SitterProfilePageResponse getPaginated(String status, String keyword, Integer page, Integer limit) {

        int safePage = (page == null || page < 1) ? 1 : page;

        int requestedLimit = (limit == null) ? 10 : limit;
        int safeLimit = Math.max(1, Math.min(100, requestedLimit));

        String safeStatus = (status == null) ? "" : status.trim();
        String safeKeyword = (keyword == null) ? "" : keyword.trim();

        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);

        List<SitterProfile> sitters = sitterProfileRepository.searchSitterProfiles(safeStatus, safeKeyword, pageable);
        long totalItems = sitterProfileRepository.countSitterProfiles(safeStatus, safeKeyword);
        int totalPages = (int) Math.ceil((double) totalItems / safeLimit);

        SitterProfilePageResponse response = new SitterProfilePageResponse();
        response.setSitters(sitters);
        response.setCurrentPage(safePage);
        response.setTotalPages(totalPages);
        response.setTotalItems(totalItems);
        response.setLimit(safeLimit);
        return response;
    }

    public static class SitterProfilePageResponse {
        private List<SitterProfile> sitters;
        private int currentPage;
        private int totalPages;
        private long totalItems;
        private int limit;

        public List<SitterProfile> getSitters() {
            return sitters;
        }

        public void setSitters(List<SitterProfile> sitters) {
            this.sitters = sitters;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }

        public long getTotalItems() {
            return totalItems;
        }

        public void setTotalItems(long totalItems) {
            this.totalItems = totalItems;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }

    public SitterProfile getById(UUID id) {
        return sitterProfileRepository.findByIdWithUser(id)
                .orElseThrow(() -> new RuntimeException("SitterProfile not found with id: " + id));
    }

    // ==========================================
    // Join SitterProfile + User + PetType (via SitterPetType bridge)
    // ==========================================

    public SitterProfileDetailResponse getDetailById(UUID id) {
        SitterProfile sitterProfile = getById(id);
        User user = sitterProfile.getUser();
        sitterProfile.setUser(null); // avoid duplicating user in the JSON response (returned separately below)

        List<PetType> petTypes = sitterPetTypeRepository.findBySitterIdWithPetType(id).stream()
                .map(SitterPetType::getPetType)
                .collect(Collectors.toList());

        // Live gallery photos (joins sitter_profiles.user_id = sitter_photos.sitter_id); used for statuses other than the waiting ones
        List<String> photoUrls = sitterPhotoRepository.findBySitter_UserIdOrderBySortOrder(id).stream()
                .map(SitterPhoto::getPhotoUrl)
                .collect(Collectors.toList());

        SitterProfileDetailResponse response = new SitterProfileDetailResponse();
        response.setSitterProfile(sitterProfile);
        response.setUser(user);
        response.setPetTypes(petTypes);
        response.setPhotoUrls(photoUrls);
        return response;
    }

    public static class SitterProfileDetailResponse {
        private SitterProfile sitterProfile;
        private User user;
        private List<PetType> petTypes;
        private List<String> photoUrls;

        public SitterProfile getSitterProfile() {
            return sitterProfile;
        }

        public void setSitterProfile(SitterProfile sitterProfile) {
            this.sitterProfile = sitterProfile;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public List<PetType> getPetTypes() {
            return petTypes;
        }

        public void setPetTypes(List<PetType> petTypes) {
            this.petTypes = petTypes;
        }

        public List<String> getPhotoUrls() {
            return photoUrls;
        }

        public void setPhotoUrls(List<String> photoUrls) {
            this.photoUrls = photoUrls;
        }
    }

    public SitterProfile update(UUID id, SitterProfile updated) {
        SitterProfile existing = getById(id);
        existing.setDisplayName(updated.getDisplayName());
        existing.setIntroduction(updated.getIntroduction());
        existing.setMyPlace(updated.getMyPlace());
        existing.setServices(updated.getServices());
        existing.setAddressDetail(updated.getAddressDetail());
        existing.setDistrict(updated.getDistrict());
        existing.setSubDistrict(updated.getSubDistrict());
        existing.setProvince(updated.getProvince());
        existing.setPostCode(updated.getPostCode());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        existing.setExperienceYears(updated.getExperienceYears());
        existing.setBankName(updated.getBankName());
        existing.setAccountNumber(updated.getAccountNumber());
        existing.setAccountName(updated.getAccountName());
        existing.setBankCode(updated.getBankCode());
        existing.setBookBankImageUrl(updated.getBookBankImageUrl());
        return sitterProfileRepository.save(existing);
    }

    public void delete(UUID id) {
        SitterProfile existing = getById(id);
        sitterProfileRepository.delete(existing);
    }

    // Parses pending_profile JSON (matches ProfilePayload shape submitted by the sitter) into a typed record.
    private ProfilePayload readPendingProfile(String pendingProfileJson) {
        if (pendingProfileJson == null || pendingProfileJson.isBlank()) return null;
        try {
            return objectMapper.readValue(pendingProfileJson, ProfilePayload.class);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid pending profile data");
        }
    }

    // Applies the identity fields collected on first submission onto the User + SitterProfile rows.
    private void applyBasicFields(SitterProfile profile, ProfilePayload payload) {
        User user = profile.getUser();
        user.setName(payload.fullName());
        user.setPhone(payload.phone());
        user.setEmail(payload.email());
        user.setDateOfBirth(payload.dateOfBirth());
        user.setIdNumber(payload.idNumber());
        user.setAvatarUrl(payload.avatarUrl());
        profile.setIntroduction(payload.introduction());
        profile.setExperienceYears(payload.experienceYears());
        userRepository.save(user);
    }

    // Applies the full sitter profile submitted on the second round, including pet types and photos.
    private void applyFullProfile(SitterProfile profile, ProfilePayload payload) {
        applyBasicFields(profile, payload);
        profile.setDisplayName(payload.displayName());
        profile.setServices(payload.services());
        profile.setMyPlace(payload.myPlace());
        profile.setAddressDetail(payload.addressDetail());
        profile.setDistrict(payload.district());
        profile.setSubDistrict(payload.subDistrict());
        profile.setProvince(payload.province());
        profile.setPostCode(payload.postCode());
        profile.setLatitude(payload.latitude());
        profile.setLongitude(payload.longitude());
        profile.setBankName(payload.bankName());
        profile.setAccountName(payload.accountName());
        profile.setAccountNumber(payload.accountNumber());
        profile.setBankCode(payload.bankCode());
        profile.setBookBankImageUrl(payload.bookBankImageUrl());
        replacePetTypes(profile, payload.petTypes());
        replacePhotos(profile, payload.photoUrls());
    }

    private void replacePetTypes(SitterProfile profile, List<String> names) {
        List<PetType> matches = new HashSet<>(names).stream()
                .map(name -> petTypeRepository.findByName(name).orElseGet(() -> {
                    PetType petType = new PetType();
                    petType.setName(name);
                    return petTypeRepository.save(petType);
                }))
                .toList();

        sitterPetTypeRepository.deleteBySitter_UserId(profile.getUserId());

        List<SitterPetType> links = matches.stream().map(petType -> {
            SitterPetType link = new SitterPetType();
            link.setId(new SitterPetType.SitterPetTypeId(profile.getUserId(), petType.getId()));
            link.setSitter(profile);
            link.setPetType(petType);
            return link;
        }).toList();

        sitterPetTypeRepository.saveAll(links);
    }

    private void replacePhotos(SitterProfile profile, List<String> urls) {
        sitterPhotoRepository.deleteBySitter_UserId(profile.getUserId());

        List<SitterPhoto> replacements = new ArrayList<>();
        for (int index = 0; index < urls.size(); index++) {
            SitterPhoto photo = new SitterPhoto();
            photo.setSitter(profile);
            photo.setPhotoUrl(urls.get(index));
            photo.setSortOrder(index);
            replacements.add(photo);
        }

        sitterPhotoRepository.saveAll(replacements);
    }

    // Verify step: only runs when approval_status is "Waiting for verify"; applies pending_profile's
    // identity fields onto the User + sitter_profiles rows, sets approval_status to "Verified", and clears pending_profile.
    @Transactional
    public SitterProfile verify(UUID id) {
        SitterProfile existing = getById(id);
        if (!"Waiting for verify".equals(existing.getApprovalStatus())) {
            return existing;
        }

        ProfilePayload pending = readPendingProfile(existing.getPendingProfile());
        if (pending != null) {
            applyBasicFields(existing, pending);
        }

        existing.getUser().setVerified(true);
        existing.setApprovalStatus("Verified");
        existing.setPendingProfile(null);
        return sitterProfileRepository.save(existing);
    }

    // Reject step: from "Waiting for verify" reverts to "Unverified"; from "Waiting for approve"
    // moves to "Rejected" (and unlists the sitter if it was already listed). Either way records
    // the admin's rejection reason.
    @Transactional
    public SitterProfile reject(UUID id, String reason) {
        SitterProfile existing = getById(id);
        String status = existing.getApprovalStatus();

        if ("Waiting for verify".equals(status)) {
            existing.setApprovalStatus("Unverified");
            existing.setRejectionReason(reason);
            return sitterProfileRepository.save(existing);
        }

        if ("Waiting for approve".equals(status)) {
            existing.setApprovalStatus("Rejected");
            existing.setRejectionReason(reason);
            existing.setListed(false);
            return sitterProfileRepository.save(existing);
        }

        return existing;
    }

    // Approve step: only runs when approval_status is "Waiting for approve"; applies pending_profile's
    // full fields (incl. pet types and photos) onto sitter_profiles, sets approval_status to "Approved",
    // lists the sitter, and clears pending_profile.
    @Transactional
    public SitterProfile approve(UUID id) {
        SitterProfile existing = getById(id);
        if (!"Waiting for approve".equals(existing.getApprovalStatus())) {
            return existing;
        }

        ProfilePayload pending = readPendingProfile(existing.getPendingProfile());
        if (pending != null) {
            applyFullProfile(existing, pending);
        }

        existing.setApprovalStatus("Approved");
        existing.setListed(true);
        existing.setPendingProfile(null);
        return sitterProfileRepository.save(existing);
    }
}
