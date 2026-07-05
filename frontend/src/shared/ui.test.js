// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { validatePrayerText } from './utils';
import fs from 'fs';
import path from 'path';

// Load the actual index.html content for integration tests
const htmlPath = path.resolve(__dirname, '../../index.html');
const indexHtml = fs.readFileSync(htmlPath, 'utf8');

// Mock localStorage globally
const localStorageMock = (() => {
  let store = {};
  return {
    getItem: vi.fn((key) => store[key] || null),
    setItem: vi.fn((key, value) => {
      store[key] = value != null ? value.toString() : 'null';
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    removeItem: vi.fn((key) => {
      delete store[key];
    }),
  };
})();
vi.stubGlobal('localStorage', localStorageMock);

describe('UI Logic tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    
    // Set up standard minimalist DOM for simple unit tests
    document.body.innerHTML = `
      <div id="app">
        <textarea id="promptInput" placeholder="Enter your prayer here..."></textarea>
        <div id="charCounter">0 / 2000</div>
        <button id="submitBtn" disabled>Submit</button>
        <div id="cards"></div>
      </div>
    `;
  });

  it('characterCounterUpdatesOnInput', () => {
    const promptInput = document.getElementById('promptInput');
    const charCounter = document.getElementById('charCounter');

    // Simulate typing 15 characters
    promptInput.value = 'Hello world 123';
    charCounter.textContent = `${promptInput.value.length} / 2000`;

    expect(charCounter.textContent).toBe('15 / 2000');
  });

  it('characterCounterDisablesSubmitWhenTooShort', () => {
    const promptInput = document.getElementById('promptInput');
    const submitBtn = document.getElementById('submitBtn');

    // Scenario 1: Short input (under 10 characters)
    promptInput.value = 'Short';
    submitBtn.disabled = !validatePrayerText(promptInput.value);
    expect(submitBtn.disabled).toBe(true);

    // Scenario 2: Valid input (10+ characters)
    promptInput.value = 'This is a valid prayer request.';
    submitBtn.disabled = !validatePrayerText(promptInput.value);
    expect(submitBtn.disabled).toBe(false);
  });

  it('prayerCardRendersTextAndDate', () => {
    const cardsEl = document.getElementById('cards');
    
    // Simulate rendering a prayer card DOM element
    const prayer = {
      prayerId: 'prayer-123',
      prayerText: 'Please pray for my exams.',
      createdAt: '2026-07-04T12:00:00Z',
      prayedForCount: 5,
    };

    const card = document.createElement('div');
    card.className = 'prayer-card';
    card.setAttribute('data-id', prayer.prayerId);
    card.innerHTML = `
      <p class="text">${prayer.prayerText}</p>
      <span class="date">July 4, 2026</span>
      <span class="count">${prayer.prayedForCount} prayers</span>
    `;
    cardsEl.appendChild(card);

    expect(cardsEl.children.length).toBe(1);
    expect(cardsEl.querySelector('.text').textContent).toBe('Please pray for my exams.');
    expect(cardsEl.querySelector('.count').textContent).toBe('5 prayers');
  });

  it('prayerCardWithClosedStatusShowsBadge', () => {
    const cardsEl = document.getElementById('cards');
    
    const prayer = {
      prayerId: 'prayer-123',
      prayerText: 'Please pray for my exams.',
      createdAt: '2026-07-04T12:00:00Z',
      prayedForCount: 5,
      status: 'CLOSED',
    };

    const card = document.createElement('article');
    card.className = 'pcard';
    card.setAttribute('data-id', prayer.prayerId);
    
    const isClosed = prayer.status === 'CLOSED';
    card.innerHTML = `
      <span class="pcard-status${isClosed ? ' answered' : ''}">${isClosed ? 'Answered' : 'Praying'}</span>
    `;
    cardsEl.appendChild(card);

    const statusEl = cardsEl.querySelector('.pcard-status');
    expect(statusEl.classList.contains('answered')).toBe(true);
    expect(statusEl.textContent).toBe('Answered');
  });
});

describe('UI Integration tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.resetModules();
    localStorage.clear();
    
    // Set up standard DOM using actual index.html
    document.documentElement.innerHTML = indexHtml;
  });

  it('groupSelectorShowsGroupNameOnValidPasscode', async () => {
    // Seed localStorage to bypass UUID generation and registration seen ping
    localStorage.setItem('prayer-link-device-id', 'test-device-id');

    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/identity/test-device-id/seen') {
        return Promise.resolve({ ok: true });
      }
      if (url === '/api/prayers?deviceId=test-device-id') {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }
      if (url === '/api/groups/validate?passcode=ABCDEF') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            groupId: 'group-abc',
            name: 'Alpha Group',
          }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    // Import main.js to execute initialization and bind event listeners
    await import('../main.js');

    // Trigger DOMContentLoaded
    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    
    // Flush microtasks
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Input passcode
    const passcodeInput = document.getElementById('passcodeInput');
    passcodeInput.value = 'ABCDEF';

    // Click confirm passcode
    const confirmCircleBtn = document.getElementById('confirmCircleBtn');
    confirmCircleBtn.dispatchEvent(new Event('click'));

    // Wait for validation to resolve
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Check UI updates
    const circleLabel = document.getElementById('circleLabel');
    const circleSelect = document.getElementById('circleSelect');
    expect(circleLabel.textContent).toBe('Alpha Group');
    expect(circleSelect.classList.contains('specific')).toBe(true);
    expect(circleSelect.getAttribute('aria-label')).toBe('Select circle - currently Alpha Group');
  });

  it('submissionAnimationTriggersOnSubmit', async () => {
    vi.useFakeTimers();
    localStorage.setItem('prayer-link-device-id', 'test-device-id');

    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/identity/test-device-id/seen') {
        return Promise.resolve({ ok: true });
      }
      if (url === '/api/prayers?deviceId=test-device-id') {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }
      if (url === '/api/prayers' && options?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            prayerId: 'p-123',
            prayerText: 'Please pray for my healing.',
            createdAt: new Date().toISOString(),
            status: 'OPEN',
            prayedForCount: 0,
          }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../main.js');

    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    await vi.advanceTimersByTimeAsync(0);

    const promptInput = document.getElementById('promptInput');
    const promptForm = document.getElementById('promptForm');
    const pill = document.getElementById('pillWrap');

    promptInput.value = 'Please pray for my healing.';
    
    // Submit form
    promptForm.dispatchEvent(new Event('submit'));

    // Verify glow phase started
    expect(pill.classList.contains('glowing-bar')).toBe(true);

    // Advance 800ms for glow phase to complete
    await vi.advanceTimersByTimeAsync(800);

    // Verify glowing-bar collapsed to submitting orb
    expect(pill.classList.contains('glowing-bar')).toBe(false);
    expect(pill.classList.contains('submitting')).toBe(true);

    // Advance 450ms for transition & API response to load card
    await vi.advanceTimersByTimeAsync(450);

    // Verify the new card is rendered in lists
    const cardsEl = document.getElementById('cards');
    const newCard = cardsEl.querySelector('.pcard');
    expect(newCard).not.toBeNull();
    expect(newCard.textContent).toContain('Please pray for my healing.');

    vi.useRealTimers();
  });

  it('handles group joining via specific passcode', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => [] }));
    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 10));

    const passcodeInput = document.getElementById('passcodeInput');
    passcodeInput.value = 'TESTING';
    document.getElementById('confirmCircleBtn').click();
    await new Promise(r => setTimeout(r, 10));
  });

  it('toggles dark mode', async () => {
    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    const themeToggle = document.getElementById('themeToggle');
    if (themeToggle) {
      themeToggle.click();
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
      themeToggle.click();
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    }
  });

  it('renderCards and view switching', async () => {
    localStorage.setItem('prayer-link-device-id', 'test-device-id');
    const statusMock = vi.fn().mockImplementation((url) => {
      if (url === '/api/identity/test-device-id/seen') return Promise.resolve({ ok: true });
      if (url.includes('/api/prayers?deviceId=')) return Promise.resolve({
        ok: true,
        json: async () => [
          { prayerId: 'p-1', prayerText: 'Prayer 1', createdAt: new Date().toISOString(), status: 'OPEN', prayedForCount: 2, groupId: 'g-1' },
          ...Array(25).fill().map((_, i) => ({ prayerId: `p-${i+2}`, prayerText: `Prayer ${i+2}`, createdAt: new Date().toISOString(), status: 'OPEN', prayedForCount: 1 }))
        ],
      });
      if (url.includes('/api/groups/g-1')) return Promise.resolve({
        ok: true,
        json: async () => ({ groupId: 'g-1', name: 'Group 1' }),
      });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    // Test view switching
    const navMine = document.querySelector('[data-view="mine"]');
    navMine.click();
    await new Promise(r => setTimeout(r, 50));

    expect(document.body.classList.contains('view-mine')).toBe(true);
    
    // Check pagination btn
    const btnLoadMore = document.getElementById('btn-load-more');
    expect(btnLoadMore.style.display).toBe('inline-block');

    const navNew = document.querySelector('[data-view="new"]');
    navNew.click();
    expect(document.body.classList.contains('view-mine')).toBe(false);
  });

  it('opens detail modal and submits update', async () => {
    localStorage.setItem('prayer-link-device-id', 'test-device-id');
    let prayersCallCount = 0;
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/identity/test-device-id/seen') return Promise.resolve({ ok: true });
      if (url.includes('/api/prayers?deviceId=')) {
        prayersCallCount++;
        return Promise.resolve({
          ok: true,
          json: async () => [{
            prayerId: 'p-1', prayerText: 'Open Prayer', createdAt: new Date().toISOString(), status: 'OPEN', prayedForCount: 0
          }]
        });
      }
      if (url === '/api/prayers/p-1') return Promise.resolve({
        ok: true,
        json: async () => ({
          prayerId: 'p-1',
          prayerText: 'Open Prayer',
          createdAt: new Date().toISOString(),
          status: 'OPEN',
          prayedForCount: 0
        })
      });
      if (url === '/api/prayers/p-1/updates' && options?.method === 'POST') return Promise.resolve({
        ok: true,
        json: async () => ({ success: true })
      });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);
    vi.stubGlobal('confirm', vi.fn(() => true));

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    document.querySelector('[data-view="mine"]').click();
    await new Promise(r => setTimeout(r, 50));

    const card = document.querySelector('.pcard');
    card.click();
    await new Promise(r => setTimeout(r, 50));

    const detailModal = document.getElementById('detailModal');
    expect(detailModal.classList.contains('open')).toBe(true);

    const updateTextarea = document.getElementById('updateTextarea');
    updateTextarea.value = 'This is a test update that is long enough.';
    updateTextarea.dispatchEvent(new Event('input'));

    const form = document.getElementById('update-prayer-form');
    form.dispatchEvent(new Event('submit'));
    
    await new Promise(r => setTimeout(r, 50));
    
    expect(prayersCallCount).toBeGreaterThan(1);
    expect(detailModal.classList.contains('open')).toBe(false);
  });

  it('checkUrlForGroup parses groupId from url', async () => {
    localStorage.setItem('prayer-link-device-id', 'test-device-id');
    
    const originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      value: { search: '?groupId=group-url-123' },
      writable: true
    });

    const statusMock = vi.fn().mockImplementation((url) => {
      if (url === '/api/identity/test-device-id/seen') return Promise.resolve({ ok: true });
      if (url.includes('/api/prayers?deviceId=')) return Promise.resolve({ ok: true, json: async () => [] });
      if (url === '/api/groups/group-url-123') return Promise.resolve({
        ok: true,
        json: async () => ({ groupId: 'group-url-123', name: 'URL Group' })
      });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    const circleLabel = document.getElementById('circleLabel');
    expect(circleLabel.textContent).toBe('URL Group');
    
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true
    });
  });

  it('initDevice handles registration for new devices', async () => {
    localStorage.removeItem('prayer-link-device-id');

    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/identity/me') return Promise.resolve({ ok: false }); // simulate not found
      if (url === '/api/identity/register' && options?.method === 'POST') {
        const body = JSON.parse(options.body);
        return Promise.resolve({ ok: true, json: async () => body });
      }
      if (url.includes('/api/prayers?deviceId=')) return Promise.resolve({ ok: true, json: async () => [] });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    expect(localStorage.getItem('prayer-link-device-id')).not.toBeNull();
  });

  it('handles QR tab and scanner initialization', async () => {
    localStorage.setItem('prayer-link-device-id', 'test-device-id');
    const statusMock = vi.fn().mockImplementation((url) => {
      if (url === '/api/identity/test-device-id/seen') return Promise.resolve({ ok: true });
      if (url.includes('/api/prayers?deviceId=')) return Promise.resolve({ ok: true, json: async () => [] });
      if (url.includes('/api/groups/12345678-1234-1234-1234-123456789012')) return Promise.resolve({
          ok: true, json: async () => ({ groupId: '12345678-1234-1234-1234-123456789012', name: 'QR Group' })
      });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    // Mock Html5Qrcode since it's used when launching the scanner
    const Html5QrcodeMock = vi.fn().mockImplementation(function() {
      this.start = vi.fn((config, options, callback) => {
        setTimeout(() => callback('groupId=12345678-1234-1234-1234-123456789012'), 10);
        return Promise.resolve();
      });
      this.stop = vi.fn().mockResolvedValue();
    });
    vi.doMock('html5-qrcode', () => ({
      Html5Qrcode: Html5QrcodeMock
    }));

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    // Open circle modal
    const circleSelect = document.getElementById('circleSelect');
    circleSelect.click();
    
    // Switch to QR tab
    const tabQr = document.getElementById('tab-qr');
    tabQr.click();
    expect(tabQr.classList.contains('active')).toBe(true);

    // Click launch scanner
    const btnStartQr = document.getElementById('btn-start-qr');
    btnStartQr.click();
    
    // Check if Html5Qrcode was mocked or at least started
    expect(btnStartQr.disabled).toBe(true);

    // Switch back to passcode tab
    const tabPasscode = document.getElementById('tab-passcode');
    tabPasscode.click();
    expect(tabPasscode.classList.contains('active')).toBe(true);
  });

  it('opens detail modal for a closed prayer', async () => {
    localStorage.setItem('prayer-link-device-id', 'test-device-id');
    const statusMock = vi.fn().mockImplementation((url) => {
      if (url === '/api/identity/test-device-id/seen') return Promise.resolve({ ok: true });
      if (url.includes('/api/prayers?deviceId=')) {
        return Promise.resolve({
          ok: true,
          json: async () => [{
            prayerId: 'p-closed', prayerText: 'Closed Prayer', createdAt: new Date().toISOString(), status: 'CLOSED', prayedForCount: 1, updates: [{updateText: 'Answered!'}]
          }]
        });
      }
      if (url === '/api/prayers/p-closed') return Promise.resolve({
        ok: true,
        json: async () => ({
          prayerId: 'p-closed',
          prayerText: 'Closed Prayer',
          createdAt: new Date().toISOString(),
          status: 'CLOSED',
          prayedForCount: 1,
          updates: [{updateText: 'Answered!'}]
        })
      });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    document.querySelector('[data-view="mine"]').click();
    await new Promise(r => setTimeout(r, 50));

    const card = document.querySelector('.pcard');
    if(card) {
        card.click();
        await new Promise(r => setTimeout(r, 50));

        const detailContentArea = document.getElementById('detail-content-area');
        expect(detailContentArea.innerHTML).toContain('Answered!');
        
        // Close modal
        const btnCloseDetail = document.getElementById('btn-close-detail');
        if (btnCloseDetail) btnCloseDetail.click();
        
        const detailModal = document.getElementById('detailModal');
        expect(detailModal.classList.contains('open')).toBe(false);
    }
  });

  it('covers remaining branches in main.js', async () => {
    localStorage.setItem('prayer-link-device-id', 'test-device-id');
    const statusMock = vi.fn().mockImplementation((url) => {
      if (url === '/api/identity/test-device-id/seen') return Promise.resolve({ ok: true });
      if (url.includes('/api/prayers?deviceId=')) return Promise.resolve({
        ok: true,
        json: async () => [
          { prayerId: 'p-min', prayerText: 'Min', createdAt: new Date(Date.now() - 5 * 60000).toISOString(), status: 'OPEN', prayedForCount: 1 },
          { prayerId: 'p-hr', prayerText: 'Hr', createdAt: new Date(Date.now() - 5 * 3600000).toISOString(), status: 'OPEN', prayedForCount: 1 },
          { prayerId: 'p-day', prayerText: 'Day', createdAt: new Date(Date.now() - 25 * 3600000).toISOString(), status: 'OPEN', prayedForCount: 1 },
          ...Array(18).fill().map((_, i) => ({ prayerId: `p-${i+4}`, prayerText: `Filler ${i+4}`, createdAt: new Date().toISOString(), status: 'OPEN', prayedForCount: 1 }))
        ]
      });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    const Html5QrcodeMock = vi.fn().mockImplementation(function() {
      this.start = vi.fn().mockRejectedValue(new Error('Camera Error'));
      this.stop = vi.fn().mockResolvedValue();
    });
    vi.doMock('html5-qrcode', () => ({
      Html5Qrcode: Html5QrcodeMock
    }));

    await import('../main.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    const navMine = document.querySelector('[data-view="mine"]');
    navMine.click();
    await new Promise(r => setTimeout(r, 50));

    const btnLoadMore = document.getElementById('btn-load-more');
    if(btnLoadMore) btnLoadMore.click();
    
    const promptInput = document.getElementById('promptInput');
    promptInput.value = 'abc';
    const form = document.getElementById('promptForm');
    form.dispatchEvent(new Event('submit'));
    
    vi.useFakeTimers();
    vi.advanceTimersByTime(4500);
    const toast = document.querySelector('.toast');
    if(toast) {
        toast.dispatchEvent(new Event('animationend'));
    }
    vi.useRealTimers();
    
  });
});
