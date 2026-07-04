// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import fs from 'fs';
import path from 'path';

// Mock qrcode module to avoid canvas/node issues
vi.mock('qrcode', () => {
  return {
    default: {
      toCanvas: vi.fn(),
      toDataURL: vi.fn(),
    }
  };
});

// Load the actual portal.html content
const htmlPath = path.resolve(__dirname, '../../portal.html');
const portalHtml = fs.readFileSync(htmlPath, 'utf8');

describe('Portal Auth tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    
    // Set up standard DOM using actual portal.html
    document.documentElement.innerHTML = portalHtml;
    window.location.hash = '';
  });

  it('loginFormSubmitsCredentials', async () => {
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: false,
            role: null,
            username: null,
            groupId: null,
            email: null,
            name: null,
            groups: [],
          }),
        });
      }
      if (url === '/api/auth/login') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            username: 'admin',
            role: 'APP_ADMIN',
            name: 'John Admin',
          }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    // Import portal.js to register event listeners
    await import('../portal.js');

    // Trigger DOMContentLoaded
    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    await new Promise((resolve) => setTimeout(resolve, 0));

    // Verify login view is shown
    const loginCard = document.getElementById('login-card');
    expect(loginCard.style.display).not.toBe('none');

    // Enter credentials
    document.getElementById('login-identifier').value = 'admin';
    document.getElementById('login-password').value = 'password123';

    // Mock status check return for after login
    statusMock.mockImplementation((url, options) => {
      if (url === '/api/auth/login') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            username: 'admin',
            role: 'APP_ADMIN',
            name: 'John Admin',
          }),
        });
      }
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: true,
            role: 'APP_ADMIN',
            username: 'admin',
            name: 'John Admin',
            groups: [],
          }),
        });
      }
      if (url === '/api/admin/groups') {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }
      if (url.startsWith('/api/admin/prayers')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ items: [], totalCount: 0 }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });

    // Submit form
    const loginForm = document.getElementById('login-form');
    loginForm.dispatchEvent(new Event('submit'));

    await new Promise((resolve) => setTimeout(resolve, 50));

    // Check login fetch payload
    const loginCall = statusMock.mock.calls.find(call => call[0] === '/api/auth/login');
    expect(logoutBtn => true); // dummy check to keep type signature
    expect(loginCall).toBeDefined();
    expect(JSON.parse(loginCall[1].body)).toEqual({
      identifier: 'admin',
      password: 'password123',
    });
  });

  it('loginFormShowsErrorOnFailure', async () => {
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: false,
          }),
        });
      }
      if (url === '/api/auth/login') {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ error: 'Invalid credentials.' }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');

    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    await new Promise((resolve) => setTimeout(resolve, 0));

    // Enter incorrect credentials
    document.getElementById('login-identifier').value = 'wrong-user';
    document.getElementById('login-password').value = 'wrong-password';

    // Submit
    const loginForm = document.getElementById('login-form');
    loginForm.dispatchEvent(new Event('submit'));

    await new Promise((resolve) => setTimeout(resolve, 50));

    // Check that toast-container shows the error
    const toastContainer = document.getElementById('toast-container');
    expect(toastContainer.textContent).toContain('Invalid credentials.');
  });

  it('logoutButtonCallsLogoutEndpoint', async () => {
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: true,
            role: 'APP_ADMIN',
            username: 'admin',
            name: 'John Admin',
            groups: [],
          }),
        });
      }
      if (url === '/api/admin/groups') {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }
      if (url.startsWith('/api/admin/prayers')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ items: [], totalCount: 0 }),
        });
      }
      if (url === '/api/auth/logout') {
        return Promise.resolve({
          ok: true,
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');

    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Click static intercessor logout button
    const logoutBtn = document.getElementById('btn-intercessor-logout');
    expect(logoutBtn).toBeDefined();
    
    logoutBtn.dispatchEvent(new Event('click'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const logoutCall = statusMock.mock.calls.find(call => call[0] === '/api/auth/logout');
    expect(logoutCall).toBeDefined();
    expect(logoutCall[1].method).toBe('POST');
  });

  it('adminLayoutShownForAdminRole', async () => {
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: true,
            role: 'APP_ADMIN',
            username: 'admin',
            name: 'John Admin',
            groups: [],
          }),
        });
      }
      if (url === '/api/admin/groups') {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }
      if (url.startsWith('/api/admin/prayers')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ items: [], totalCount: 0 }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');

    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    await new Promise((resolve) => setTimeout(resolve, 50));

    const adminRoot = document.getElementById('admin-root');
    const authContainer = document.getElementById('auth-container');
    const intercessorRoot = document.getElementById('intercessor-root');

    expect(adminRoot.style.display).toBe('grid');
    expect(authContainer.style.display).toBe('none');
    expect(intercessorRoot.style.display).toBe('none');
  });

  it('intercessorLayoutShownForIntercessorRole', async () => {
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: true,
            role: 'INTERCESSOR',
            username: 'intercessor',
            name: 'Jane Intercessor',
            groups: [{ groupId: 'group-1', name: 'Group One' }],
          }),
        });
      }
      if (url === '/api/prayers/group/group-1/auth') {
        return Promise.resolve({
          ok: true,
          json: async () => [],
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');

    const domLoadedEvent = new Event('DOMContentLoaded');
    document.dispatchEvent(domLoadedEvent);
    await new Promise((resolve) => setTimeout(resolve, 50));

    const adminRoot = document.getElementById('admin-root');
    const authContainer = document.getElementById('auth-container');
    const intercessorRoot = document.getElementById('intercessor-root');

    expect(intercessorRoot.style.display).toBe('grid');
    expect(authContainer.style.display).toBe('none');
    expect(adminRoot.style.display).toBe('none');
  });
});
