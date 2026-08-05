package com.prayerlink.prayer.service;

import com.prayerlink.common.dto.PrayerDTO;
import com.prayerlink.common.enums.PrayerStatus;
import com.prayerlink.common.exception.ResourceNotFoundException;
import com.prayerlink.prayer.model.Prayer;
import com.prayerlink.prayer.model.PrayerUpdate;
import com.prayerlink.prayer.model.mapper.PrayerMapper;
import com.prayerlink.prayer.repository.PrayerRepository;
import com.prayerlink.prayer.repository.PrayerUpdateRepository;
import com.prayerlink.prayer.service.security.GroupAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PrayerQueryService {

    private final PrayerRepository prayerRepository;
    private final PrayerUpdateRepository prayerUpdateRepository;
    private final PrayerMapper prayerMapper;
    private final GroupAuthorizationService groupAuthorizationService;

    public PrayerQueryService(
            PrayerRepository prayerRepository,
            PrayerUpdateRepository prayerUpdateRepository,
            PrayerMapper prayerMapper,
            GroupAuthorizationService groupAuthorizationService) {
        this.prayerRepository = prayerRepository;
        this.prayerUpdateRepository = prayerUpdateRepository;
        this.prayerMapper = prayerMapper;
        this.groupAuthorizationService = groupAuthorizationService;
    }

    public PrayerDTO getPrayer(UUID prayerId) {
        Prayer prayer = findPrayerOrThrow(prayerId);
        List<PrayerUpdate> updates = prayerUpdateRepository.findByPrayerId(prayerId);
        return prayerMapper.convertToDTO(prayer, updates);
    }

    public List<PrayerDTO> getPrayersByDevice(UUID deviceId) {
        return prayerRepository.findByDeviceId(deviceId).stream()
                .map(prayerMapper::convertToDTO)
                .toList();
    }

    public List<PrayerDTO> getGroupPrayers(UUID groupId, String token) {
        groupAuthorizationService.validateTokenForGroup(token, groupId);

        return prayerRepository.findByGroupIdAndStatus(groupId, PrayerStatus.OPEN).stream()
                .map(prayerMapper::convertToDTO)
                .toList();
    }

    public List<PrayerDTO> getGroupPrayersAuth(UUID groupId, String email) {
        groupAuthorizationService.assertIsMember(email, groupId);

        return prayerRepository.findByGroupId(groupId).stream()
                .map(p -> prayerMapper.convertToDTO(p, List.of(), email))
                .toList();
    }

    private Prayer findPrayerOrThrow(UUID prayerId) {
        return prayerRepository
                .findById(prayerId)
                .orElseThrow(() -> new ResourceNotFoundException("Prayer not found with id: " + prayerId));
    }
}
