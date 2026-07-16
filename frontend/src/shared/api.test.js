import { describe, it, expect, vi, beforeEach } from 'vitest';
import { registerDevice, fetchPrayers, submitPrayer, validatePasscode } from './api';
import * as apiClient from './apiClient';

describe('API Client tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('registerDeviceCallsCorrectEndpoint', async () => {
    const mockResponse = { deviceId: 'device-123', status: 'CREATED' };
    const fetchMock = vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });

    const result = await registerDevice('device-123');

    expect(fetchMock).toHaveBeenCalledWith('/api/identity/register', {
      method: 'POST',
      body: JSON.stringify({ deviceId: 'device-123' }),
    });
    expect(result).toEqual(mockResponse);
  });

  it('registerDevice throws error if no deviceId', async () => {
    await expect(registerDevice(null)).rejects.toThrow('deviceId is required');
  });

  it('registerDevice throws error on !res.ok', async () => {
    vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({ ok: false, status: 500 });
    await expect(registerDevice('d123')).rejects.toThrow('Registration failed with status 500');
  });

  it('fetchPrayersPassesDeviceId', async () => {
    const mockPrayers = [{ prayerId: 'p-1', text: 'Test' }];
    const fetchMock = vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({
      ok: true,
      json: async () => mockPrayers,
    });

    const result = await fetchPrayers('device-123');

    expect(fetchMock).toHaveBeenCalledWith('/api/prayers?deviceId=device-123');
    expect(result).toEqual(mockPrayers);
  });

  it('fetchPrayers throws error on !res.ok', async () => {
    vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({ ok: false, status: 500 });
    await expect(fetchPrayers('d123')).rejects.toThrow('Failed to fetch prayers: 500');
  });

  it('submitPrayerSendsCorrectPayload', async () => {
    const mockResponse = { prayerId: 'p-1', status: 'OPEN' };
    const fetchMock = vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });

    const result = await submitPrayer('This is a long prayer text of 10+ chars', 'device-123', 'group-456');

    expect(fetchMock).toHaveBeenCalledWith('/api/prayers', {
      method: 'POST',
      body: JSON.stringify({
        text: 'This is a long prayer text of 10+ chars',
        deviceId: 'device-123',
        groupId: 'group-456',
      }),
    });
    expect(result).toEqual(mockResponse);
  });

  it('submitPrayer throws error if text is too short', async () => {
    await expect(submitPrayer('short', 'd123')).rejects.toThrow('Prayer text must be at least 10 characters long');
  });

  it('submitPrayer throws error on !res.ok', async () => {
    vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({ ok: false, status: 500 });
    await expect(submitPrayer('This is a long prayer text of 10+ chars', 'd123')).rejects.toThrow('Submission failed with status 500');
  });

  it('validatePasscodeReturnsGroupOnSuccess', async () => {
    const mockGroup = { groupId: 'g-123', passcode: 'AAABBB' };
    const fetchMock = vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockGroup,
    });

    const result = await validatePasscode('AAABBB');

    expect(fetchMock).toHaveBeenCalledWith('/api/groups/validate?passcode=AAABBB');
    expect(result).toEqual(mockGroup);
  });

  it('validatePasscodeReturnsNullOn404', async () => {
    const fetchMock = vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({
      ok: false,
      status: 404,
    });

    const result = await validatePasscode('NOTFND');

    expect(fetchMock).toHaveBeenCalledWith('/api/groups/validate?passcode=NOTFND');
    expect(result).toBeNull();
  });

  it('validatePasscode throws error if passcode length is invalid', async () => {
    await expect(validatePasscode('123')).rejects.toThrow('Passcode must be exactly 6 characters');
  });

  it('validatePasscode throws error on !res.ok', async () => {
    vi.spyOn(apiClient, 'fetchSecureData').mockResolvedValue({ ok: false, status: 500 });
    await expect(validatePasscode('AAABBB')).rejects.toThrow('Passcode validation failed: 500');
  });
});
