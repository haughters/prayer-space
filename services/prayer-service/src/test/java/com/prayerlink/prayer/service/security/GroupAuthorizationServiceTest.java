package com.prayerlink.prayer.service.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.common.dto.GroupMemberDTO;
import com.prayerlink.prayer.client.GroupServiceClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GroupAuthorizationServiceTest {

    @Mock
    private GroupServiceClient groupServiceClient;

    @Mock
    private IntercessorTokenValidator tokenValidator;

    @InjectMocks
    private GroupAuthorizationService authorizationService;

    private final UUID G1 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final String EMAIL = "user@example.com";

    @Test
    void validateTokenForPrayerCallsValidatorWithMembers() {
        GroupMemberDTO member = GroupMemberDTO.builder().email(EMAIL).build();
        when(groupServiceClient.fetchGroupMembers(G1)).thenReturn(List.of(member));
        when(tokenValidator.validateForPrayer("token", G1.toString(), List.of(member)))
                .thenReturn(EMAIL);

        String result = authorizationService.validateTokenForPrayer("token", G1);

        assertEquals(EMAIL, result);
        verify(groupServiceClient).fetchGroupMembers(G1);
        verify(tokenValidator).validateForPrayer("token", G1.toString(), List.of(member));
    }

    @Test
    void validateTokenForGroupCallsValidatorWithMembers() {
        GroupMemberDTO member = GroupMemberDTO.builder().email(EMAIL).build();
        when(groupServiceClient.fetchGroupMembers(G1)).thenReturn(List.of(member));

        authorizationService.validateTokenForGroup("token", G1);

        verify(groupServiceClient).fetchGroupMembers(G1);
        verify(tokenValidator).validateForGroup("token", G1.toString(), List.of(member));
    }

    @Test
    void assertIsMemberDoesNotThrowIfMember() {
        GroupMemberDTO member = GroupMemberDTO.builder().email(EMAIL).build();
        when(groupServiceClient.fetchGroupMembers(G1)).thenReturn(List.of(member));

        assertDoesNotThrow(() -> authorizationService.assertIsMember(EMAIL, G1));
    }

    @Test
    void assertIsMemberThrowsForbiddenIfNotMember() {
        GroupMemberDTO member =
                GroupMemberDTO.builder().email("other@example.com").build();
        when(groupServiceClient.fetchGroupMembers(G1)).thenReturn(List.of(member));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> authorizationService.assertIsMember(EMAIL, G1));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals("You do not belong to this group", exception.getReason());
    }

    @Test
    void assertIsMemberIsCaseInsensitive() {
        GroupMemberDTO member =
                GroupMemberDTO.builder().email("USER@EXAMPLE.COM").build();
        when(groupServiceClient.fetchGroupMembers(G1)).thenReturn(List.of(member));

        assertDoesNotThrow(() -> authorizationService.assertIsMember("user@example.com", G1));
    }
}
