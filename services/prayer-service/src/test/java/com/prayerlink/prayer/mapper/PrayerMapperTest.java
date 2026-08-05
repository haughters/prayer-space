package com.prayerlink.prayer.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import com.prayerlink.prayer.model.mapper.PrayerMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class PrayerMapperTest {

    private PrayerMapper prayerMapper;

    @BeforeEach
    void setUp() {
        prayerMapper = Mappers.getMapper(PrayerMapper.class);
    }

    @Test
    void testConvertToDTO_NoUpdates() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        prayer.setPrayerText("Test prayer");
        prayer.setPrayedForCount(5);

        PrayerDTO dto = prayerMapper.convertToDTO(prayer);

        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), dto.prayerId());
        assertEquals("Test prayer", dto.prayerText());
        assertEquals(5, dto.prayedForCount());
        assertTrue(dto.updates().isEmpty());
    }

    @Test
    void testConvertToDTO_WithUpdates() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));

        PrayerUpdate update = new PrayerUpdate();
        update.setUpdateText("Update 1");
        update.setUpdatedAt(Instant.now());

        PrayerDTO dto = prayerMapper.convertToDTO(prayer, List.of(update));

        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), dto.prayerId());
        assertEquals(1, dto.updates().size());
        assertEquals("Update 1", dto.updates().get(0).updateText());
    }

    @Test
    void testConvertToDTO_NullPrayedForCount() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        prayer.setPrayedForCount(null);

        PrayerDTO dto = prayerMapper.convertToDTO(prayer);

        assertEquals(0, dto.prayedForCount());
    }

    @Test
    void testConvertToDTO_WithIntercessorEmail_HasPrayed() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        prayer.setPrayedByEmails(Set.of("test@example.com"));

        PrayerDTO dto = prayerMapper.convertToDTO(prayer, List.of(), "test@example.com");

        assertEquals(true, dto.hasPrayed());
    }

    @Test
    void testConvertToDTO_WithIntercessorEmail_HasNotPrayed() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        prayer.setPrayedByEmails(Set.of("other@example.com"));

        PrayerDTO dto = prayerMapper.convertToDTO(prayer, List.of(), "test@example.com");

        assertNotEquals(true, dto.hasPrayed());
    }

    @Test
    void testConvertToDTO_NullPrayedByEmails() {
        Prayer prayer = new Prayer();
        prayer.setPrayerId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"));
        prayer.setPrayedByEmails(null);

        PrayerDTO dto = prayerMapper.convertToDTO(prayer, List.of(), "test@example.com");

        assertNotEquals(true, dto.hasPrayed());
    }
}
