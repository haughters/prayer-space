package com.prayerlink.prayer.model.mapper;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.dto.PrayerUpdateDTO;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PrayerMapper {

    @Mapping(target = "hasPrayed", ignore = true)
    @Mapping(target = "prayedForCount", source = "prayer.prayedForCount", defaultValue = "0")
    PrayerDTO convertToDTO(Prayer prayer, List<PrayerUpdate> updates);

    default PrayerDTO convertToDTO(Prayer prayer) {
        return convertToDTO(prayer, List.of());
    }

    default PrayerDTO convertToDTO(Prayer prayer, List<PrayerUpdate> updates, String intercessorEmail) {
        PrayerDTO dto = convertToDTO(prayer, updates);
        if (intercessorEmail != null && prayer.getPrayedByEmails() != null) {
            boolean hasPrayed = prayer.getPrayedByEmails().contains(intercessorEmail);
            return dto.toBuilder().hasPrayed(hasPrayed).build();
        }
        return dto;
    }

    PrayerUpdateDTO convertToDTO(PrayerUpdate update);
}
