import { describe, it, expect, vi, beforeEach } from 'vitest';
import { registerDevice, fetchPrayers, submitPrayer, validatePasscode } from './api';

describe('API Client tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('registerDeviceCallsCorrectEndpoint', async () => {
    const mockResponse = { deviceId: 'device-123', status: 'CREATED' };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await registerDevice('device-123');

    expect(fetchMock).toHaveBeenCalledWith('/api/identity/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId: 'device-123' }),
    });
    expect(result).toEqual(mockResponse);
  });

  it('fetchPrayersPassesDeviceId', async () => {
    const mockPrayers = [{ prayerId: 'p-1', text: 'Test' }];
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockPrayers,
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await fetchPrayers('device-123');

    expect(fetchMock).toHaveBeenCalledWith('/api/prayers?deviceId=device-123');
    expect(result).toEqual(mockPrayers);
  });

  it('submitPrayerSendsCorrectPayload', async () => {
    const mockResponse = { prayerId: 'p-1', status: 'OPEN' };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await submitPrayer('This is a long prayer text of 10+ chars', 'device-123', 'group-456');

    expect(fetchMock).toHaveBeenCalledWith('/api/prayers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: 'This is a long prayer text of 10+ chars',
        deviceId: 'device-123',
        groupId: 'group-456',
      }),
    });
    expect(result).toEqual(mockResponse);
  });

  it('validatePasscodeReturnsGroupOnSuccess', async () => {
    const mockGroup = { groupId: 'g-123', passcode: 'AAABBB' };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockGroup,
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await validatePasscode('AAABBB');

    expect(fetchMock).toHaveBeenCalledWith('/api/groups/validate?passcode=AAABBB');
    expect(result).toEqual(mockGroup);
  });

  it('validatePasscodeReturnsNullOn404', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await validatePasscode('AAABBB');

    expect(fetchMock).toHaveBeenCalledWith('/api/groups/validate?passcode=AAABBB');
    expect(result).toBeNull();
  });
});
