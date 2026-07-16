package com.prayerlink.prayer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayerlink.common.dto.PrayerDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JacksonTest {
    @Test
    public void test() throws Exception {
        ObjectMapper om = new ObjectMapper();
        String json = "{\"deviceId\":\"91af252d-9128-45cf-90a3-e4410bd73556\",\"prayerText\":\"This is a test\",\"groupId\":null}";
        PrayerDTO dto = om.readValue(json, PrayerDTO.class);
        assertEquals("This is a test", dto.getPrayerText());
    }
}
