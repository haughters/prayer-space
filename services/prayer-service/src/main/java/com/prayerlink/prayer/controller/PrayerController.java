package com.prayerlink.prayer.controller;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.prayer.service.PrayerCommandService;
import com.prayerlink.prayer.service.PrayerQueryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prayers")
@Validated
public class PrayerController {

    private final PrayerCommandService commandService;
    private final PrayerQueryService queryService;

    public PrayerController(PrayerCommandService commandService, PrayerQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<PrayerDTO> createPrayer(@Valid @RequestBody PrayerDTO dto) {
        PrayerDTO created = commandService.createPrayer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{prayerId}")
    public ResponseEntity<PrayerDTO> getPrayer(@PathVariable("prayerId") UUID prayerId) {
        return ResponseEntity.ok(queryService.getPrayer(prayerId));
    }

    @GetMapping
    public ResponseEntity<List<PrayerDTO>> getPrayers(@RequestParam("deviceId") UUID deviceId) {
        return ResponseEntity.ok(queryService.getPrayersByDevice(deviceId));
    }

    @PostMapping("/{prayerId}/updates")
    public ResponseEntity<Map<String, String>> createUpdate(
            @PathVariable("prayerId") UUID prayerId,
            @RequestHeader(value = "X-Device-ID", required = false) UUID deviceIdHeader,
            @RequestBody Map<String, String> requestBody) {

        commandService.createUpdate(prayerId, deviceIdHeader, requestBody.get("updateText"));
        return ResponseEntity.ok(Map.of("message", "Prayer updated and closed successfully"));
    }

    @PostMapping("/{prayerId}/prayed")
    public ResponseEntity<?> markPrayed(
            @PathVariable("prayerId") UUID prayerId, @RequestBody Map<String, String> requestBody) {

        return ResponseEntity.ok(commandService.markPrayed(prayerId, requestBody.get("intercessorToken")));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<PrayerDTO>> getGroupPrayers(
            @PathVariable("groupId") UUID groupId, @RequestParam("token") String token) {

        return ResponseEntity.ok(queryService.getGroupPrayers(groupId, token));
    }

    @PostMapping("/{prayerId}/prayed/auth")
    public ResponseEntity<?> markPrayedAuth(
            @PathVariable("prayerId") UUID prayerId, @RequestAttribute("userEmail") String userEmail) {
        return ResponseEntity.ok(commandService.markPrayedAuth(prayerId, userEmail));
    }

    @GetMapping("/group/{groupId}/auth")
    public ResponseEntity<?> getGroupPrayersAuth(
            @PathVariable("groupId") UUID groupId, @RequestAttribute("userEmail") String userEmail) {

        return ResponseEntity.ok(queryService.getGroupPrayersAuth(groupId, userEmail));
    }
}
