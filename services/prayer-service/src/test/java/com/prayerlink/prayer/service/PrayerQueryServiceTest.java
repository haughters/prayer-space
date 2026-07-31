package com.prayerlink.prayer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.common.exception.ResourceNotFoundException;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.mapper.PrayerMapper;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.repository.PrayerUpdateRepository;
import com.prayerlink.prayer.service.security.GroupAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PrayerQueryServiceTest {

    @Mock
    private PrayerRepository prayerRepository;

    @Mock
    private PrayerUpdateRepository prayerUpdateRepository;

    @Mock
    private PrayerMapper prayerMapper;

    @Mock
    private GroupAuthorizationService groupAuthorizationService;

    @InjectMocks
    private PrayerQueryService queryService;

    private final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID DEV1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID G1 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-000000000005");

    // --- getPrayer ---

    @Test
    void getPrayerReturnsDto() {
        Prayer prayer = Prayer.builder().prayerId(P1).build();
        PrayerDTO expected = PrayerDTO.builder().prayerId(P1).build();

        when(prayerRepository.findById(P1)).thenReturn(Optional.of(prayer));
        when(prayerUpdateRepository.findByPrayerId(P1)).thenReturn(List.of());
        when(prayerMapper.convertToDTO(prayer, List.of())).thenReturn(expected);

        PrayerDTO result = queryService.getPrayer(P1);
        assertEquals(P1, result.prayerId());
    }

    @Test
    void getPrayerNotFoundThrows() {
        when(prayerRepository.findById(MISSING)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> queryService.getPrayer(MISSING));
    }

    // --- getPrayersByDevice ---

    @Test
    void getPrayersByDeviceReturnsList() {
        Prayer p1 = Prayer.builder().prayerId(P1).build();
        PrayerDTO dto1 = PrayerDTO.builder().prayerId(P1).build();

        when(prayerRepository.findByDeviceId(DEV1)).thenReturn(List.of(p1));
        when(prayerMapper.convertToDTO(p1)).thenReturn(dto1);

        List<PrayerDTO> result = queryService.getPrayersByDevice(DEV1);
        assertEquals(1, result.size());
        assertEquals(P1, result.get(0).prayerId());
    }

    @Test
    void getPrayersByDeviceReturnsEmptyList() {
        when(prayerRepository.findByDeviceId(DEV1)).thenReturn(List.of());
        List<PrayerDTO> result = queryService.getPrayersByDevice(DEV1);
        assertTrue(result.isEmpty());
    }

    // --- getGroupPrayers ---

    @Test
    void getGroupPrayersValidatesTokenAndReturnsOpenPrayers() {
        Prayer open = Prayer.builder().prayerId(P1).status(PrayerStatus.OPEN).build();
        Prayer closed =
                Prayer.builder().prayerId(P2).status(PrayerStatus.CLOSED).build();
        PrayerDTO dto = PrayerDTO.builder().prayerId(P1).build();

        when(prayerRepository.findByGroupIdAndStatus(G1, PrayerStatus.OPEN)).thenReturn(List.of(open));
        when(prayerMapper.convertToDTO(open)).thenReturn(dto);

        List<PrayerDTO> result = queryService.getGroupPrayers(G1, "token");

        assertEquals(1, result.size());
        assertEquals(P1, result.get(0).prayerId());
        verify(groupAuthorizationService).validateTokenForGroup("token", G1);
    }

    // --- getGroupPrayersAuth ---

    @Test
    void getGroupPrayersAuthVerifiesMembershipAndReturnsPrayers() {
        Prayer prayer = Prayer.builder().prayerId(P1).build();
        PrayerDTO dto = PrayerDTO.builder().prayerId(P1).build();

        when(prayerRepository.findByGroupId(G1)).thenReturn(List.of(prayer));
        when(prayerMapper.convertToDTO(prayer, List.of(), "u@t.com")).thenReturn(dto);

        List<PrayerDTO> result = queryService.getGroupPrayersAuth(G1, "u@t.com");

        assertEquals(1, result.size());
        verify(groupAuthorizationService).assertIsMember("u@t.com", G1);
    }

    @Test
    void getGroupPrayersAuthNonMemberThrowsForbidden() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                .when(groupAuthorizationService)
                .assertIsMember("other@t.com", G1);

        assertThrows(ResponseStatusException.class, () -> queryService.getGroupPrayersAuth(G1, "other@t.com"));
    }
}
