package com.prayerlink.prayer.service.security;

import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.common.exception.UnauthorizedException;
import com.prayerlink.common.util.HmacUtil;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates intercessor HMAC tokens (3-part format: signature|groupId|expiry).
 *
 * <p>The token encodes a signed claim that a specific group member is authorised to interact with
 * prayers assigned to their group. Validation involves parsing the token, checking expiry, and
 * brute-force matching the signature against all group members to identify the intercessor.
 */
@Component
public class IntercessorTokenValidator {

    public static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE = "Invalid or expired token";
    private final String hmacSecretKey;

    public IntercessorTokenValidator(
            @Value("${hmac.secret-key:default-secret-key-change-me-in-production}") String hmacSecretKey) {
        this.hmacSecretKey = hmacSecretKey;
    }

    /**
     * Validates a token for marking a prayer as prayed-for. Token format: {@code
     * signature|groupId|expiry}
     *
     * @param token the intercessor token from the request
     * @param assignedGroupId the group ID the prayer is assigned to
     * @param members the members of the assigned group
     * @return the email address of the validated intercessor
     * @throws UnauthorizedException if the token is invalid, expired, or unverifiable
     */
    public String validateForPrayer(String token, String assignedGroupId, List<GroupMemberDTO> members) {
        ParsedToken parsed = parseToken(token);
        validateGroupId(parsed.groupId, assignedGroupId);
        return matchMember(parsed, assignedGroupId, members);
    }

    /**
     * Validates a token for viewing group prayers. Token format: {@code signature|groupId|expiry}
     *
     * @param token the intercessor token from the request
     * @param groupId the group whose prayers are being accessed
     * @param members the members of the group
     * @throws UnauthorizedException if the token is invalid, expired, or unverifiable
     */
    public void validateForGroup(String token, String groupId, List<GroupMemberDTO> members) {
        ParsedToken parsed = parseToken(token);
        validateGroupId(parsed.groupId, groupId);
        matchMember(parsed, groupId, members);
    }

    private ParsedToken parseToken(String token) {
        if (token == null || !token.contains("|")) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        String[] parts = token.split("\\|");
        if (parts.length != 3) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        String signature = parts[0];
        String groupId = parts[1];
        String expiryStr = parts[2];

        try {
            long expiryTimestamp = Long.parseLong(expiryStr);
            if (expiryTimestamp < Instant.now().getEpochSecond()) {
                throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
            }
        } catch (NumberFormatException e) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        return new ParsedToken(signature, groupId, expiryStr);
    }

    private void validateGroupId(String tokenGroupId, String expectedGroupId) {
        if (!expectedGroupId.equals(tokenGroupId)) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }
    }

    private String matchMember(ParsedToken parsed, String groupId, List<GroupMemberDTO> members) {
        if (members == null || members.isEmpty()) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        for (GroupMemberDTO member : members) {
            String payload = groupId + ":" + member.getEmail() + ":" + parsed.expiryStr;
            if (HmacUtil.verifyToken(payload, parsed.signature, hmacSecretKey)) {
                return member.getEmail();
            }
        }

        throw new UnauthorizedException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
    }

    private record ParsedToken(String signature, String groupId, String expiryStr) {}
}
