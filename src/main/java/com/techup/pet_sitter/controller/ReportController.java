package com.techup.pet_sitter.controller;

import com.techup.pet_sitter.dto.ReportAdminListItem;
import com.techup.pet_sitter.entity.Report;
import com.techup.pet_sitter.repository.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reports;

    public ReportController(ReportRepository reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<ReportAdminListItem> listAll() {
        return reports.findAdminList();
    }

    @GetMapping("/{id}")
    public ReportAdminListItem getById(@PathVariable Long id) {
        return reports.findAdminDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    @PatchMapping("/{id}/cancel")
    public ReportAdminListItem cancel(@PathVariable Long id) {
        return updateStatus(id, "canceled");
    }

    @PatchMapping("/{id}/resolve")
    public ReportAdminListItem resolve(@PathVariable Long id) {
        return updateStatus(id, "resolved");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!reports.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        reports.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private ReportAdminListItem updateStatus(Long id, String status) {
        Report report = reports.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        report.setStatus(status);
        reports.save(report);
        return reports.findAdminDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }
}
