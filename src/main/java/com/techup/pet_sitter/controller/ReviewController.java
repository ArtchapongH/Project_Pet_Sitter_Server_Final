package com.techup.pet_sitter.controller;

import com.techup.pet_sitter.dto.ReviewAdminListItem;
import com.techup.pet_sitter.repository.ReviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviews;

    public ReviewController(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/sitter/{sitterId}")
    public List<ReviewAdminListItem> listBySitter(@PathVariable UUID sitterId) {
        return reviews.findAdminListBySitterId(sitterId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!reviews.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }
        reviews.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
