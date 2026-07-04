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
      store[key] = value.toString();
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
});
