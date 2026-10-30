package com.smartparking.controller;

import com.smartparking.dto.ReservationResponse;
import com.smartparking.dto.UsageReportResponse;
import com.smartparking.service.ReportService;
import com.smartparking.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReservationService reservationService;

    @GetMapping("/usage")
    public UsageReportResponse usage() {
        return reportService.usageReport();
    }

    /** Paginated, filterable session log (admin only). */
    @GetMapping("/sessions")
    public Page<ReservationResponse> sessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return reservationService.sessions(status, zone, from, to, page, size);
    }

    /** Full session history as CSV (admin only). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] csv = reservationService.exportCsv();
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=parking-sessions.csv")
                .body(csv);
    }
}
