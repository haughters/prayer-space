package com.prayerlink.prayer.security;

import static org.junit.jupiter.api.Assertions.*;

import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.exception.UnauthorizedException;
import com.prayerlink.common.util.HmacUtil;
import com.prayerlink.prayer.service.security.IntercessorTokenValidator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntercessorTokenValidatorTest {

    private static final String SECRET = "test-secret-key-must-be-very-long-and-secure-for-hmac-sha-256-prayer-link";

    private IntercessorTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IntercessorTokenValidator(SECRET);
    }

    @Test
    void validateForPrayerWithValidToken() {
        String groupId = "group-123";
        String email = "user@example.com";
        long expiry = Instant.now().getEpochSecond() + 3600;
        String payload = groupId + ":" + email + ":" + expiry;
        String signature = HmacUtil.generateToken(payload, SECRET);
        String token = signature + "|" + groupId + "|" + expiry;

        GroupMemberDTO member = GroupMemberDTO.builder().email(email).build();

        String result = validator.validateForPrayer(token, groupId, List.of(member));
        assertEquals(email, result);
    }

    @Test
    void validateForPrayerWithExpiredToken() {
        String groupId = "group-123";
        String email = "user@example.com";
        long expiry = Instant.now().getEpochSecond() - 3600;
        String payload = groupId + ":" + email + ":" + expiry;
        String signature = HmacUtil.generateToken(payload, SECRET);
        String token = signature + "|" + groupId + "|" + expiry;

        GroupMemberDTO member = GroupMemberDTO.builder().email(email).build();

        assertThrows(UnauthorizedException.class, () -> validator.validateForPrayer(token, groupId, List.of(member)));
    }

    @Test
    void validateForPrayerWithNullToken() {
        assertThrows(UnauthorizedException.class, () -> validator.validateForPrayer(null, "group-123", List.of()));
    }

    @Test
    void validateForPrayerWithMalformedToken() {
        assertThrows(
                UnauthorizedException.class,
                () -> validator.validateForPrayer("no-pipes-here", "group-123", List.of()));
    }

    @Test
    void validateForPrayerWithWrongPartCount() {
        assertThrows(
                UnauthorizedException.class, () -> validator.validateForPrayer("sig|expiry", "group-123", List.of()));
    }

    @Test
    void validateForPrayerWithGroupIdMismatch() {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "sig|wrong-group|" + expiry;

        assertThrows(UnauthorizedException.class, () -> validator.validateForPrayer(token, "group-123", List.of()));
    }

    @Test
    void validateForPrayerWithInvalidSignature() {
        String groupId = "group-123";
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "bad-signature|" + groupId + "|" + expiry;

        GroupMemberDTO member =
                GroupMemberDTO.builder().email("user@example.com").build();

        assertThrows(UnauthorizedException.class, () -> validator.validateForPrayer(token, groupId, List.of(member)));
    }

    @Test
    void validateForPrayerWithEmptyMembers() {
        String groupId = "group-123";
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "sig|" + groupId + "|" + expiry;

        assertThrows(UnauthorizedException.class, () -> validator.validateForPrayer(token, groupId, List.of()));
    }

    @Test
    void validateForPrayerWithInvalidExpiry() {
        String token = "sig|group-123|not-a-number";

        assertThrows(UnauthorizedException.class, () -> validator.validateForPrayer(token, "group-123", List.of()));
    }

    @Test
    void validateForGroupWithValidToken() {
        String groupId = "group-123";
        String email = "user@example.com";
        long expiry = Instant.now().getEpochSecond() + 3600;
        String payload = groupId + ":" + email + ":" + expiry;
        String signature = HmacUtil.generateToken(payload, SECRET);
        String token = signature + "|" + groupId + "|" + expiry;

        GroupMemberDTO member = GroupMemberDTO.builder().email(email).build();

        assertDoesNotThrow(() -> validator.validateForGroup(token, groupId, List.of(member)));
    }

    @Test
    void validateForGroupWithInvalidToken() {
        String groupId = "group-123";
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = "bad-sig|" + groupId + "|" + expiry;

        GroupMemberDTO member =
                GroupMemberDTO.builder().email("user@example.com").build();

        assertThrows(UnauthorizedException.class, () -> validator.validateForGroup(token, groupId, List.of(member)));
    }
}
