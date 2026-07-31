package com.prayerlink.common.dto;

import java.time.Instant;
import lombok.Builder;

@Builder
public record PrayerUpdateDTO(String updateText, Instant updatedAt) {}
