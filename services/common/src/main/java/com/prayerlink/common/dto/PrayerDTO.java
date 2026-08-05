package com.prayerlink.common.dto;

import com.prayerlink.common.enums.PrayerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record PrayerDTO(
        UUID prayerId,
        UUID deviceId,

        @NotBlank @Size(min = 10, max = 2000) String prayerText,

        UUID groupId,
        UUID assignedGroupId,
        PrayerStatus status,
        Integer prayedForCount,
        Instant createdAt,
        Instant updatedAt,
        Boolean hasPrayed,
        List<PrayerUpdateDTO> updates) {}
