import { fetchSecureData } from './apiClient.js';

export async function registerDevice(deviceId) {
  if (!deviceId) throw new Error("deviceId is required");
  const res = await fetchSecureData("/api/identity/register", {
    method: "POST",
    body: JSON.stringify({ deviceId })
  });
  if (!res.ok) throw new Error(`Registration failed with status ${res.status}`);
  return res.json();
}

export async function fetchPrayers(deviceId) {
  const url = deviceId ? `/api/prayers?deviceId=${encodeURIComponent(deviceId)}` : "/api/prayers";
  const res = await fetchSecureData(url);
  if (!res.ok) throw new Error(`Failed to fetch prayers: ${res.status}`);
  return res.json();
}

export async function submitPrayer(text, deviceId, groupId = null) {
  if (!text || text.trim().length < 10) {
    throw new Error("Prayer text must be at least 10 characters long");
  }
  const payload = { text, deviceId };
  if (groupId) {
    payload.groupId = groupId;
  }
  const res = await fetchSecureData("/api/prayers", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error(`Submission failed with status ${res.status}`);
  return res.json();
}

export async function validatePasscode(passcode) {
  if (!passcode || passcode.trim().length !== 6) {
    throw new Error("Passcode must be exactly 6 characters");
  }
  const res = await fetchSecureData(`/api/groups/validate?passcode=${encodeURIComponent(passcode.trim().toUpperCase())}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Passcode validation failed: ${res.status}`);
  return res.json();
}
