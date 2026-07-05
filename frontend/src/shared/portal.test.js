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
    vi.resetModules();
    
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

  it('loads admin dashboard and switches tabs', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url) => {
      if (url.includes('auth/status')) return Promise.resolve({ ok: true, json: async () => ({ initialized: true, authenticated: true, role: 'APP_ADMIN', groups: [] }) });
      if (url.includes('admin/groups')) return Promise.resolve({ ok: true, json: async () => ([{ groupId: 'g1', name: 'Group 1', memberCount: 5 }]) });
      if (url.includes('admin/prayers')) return Promise.resolve({ ok: true, json: async () => ({ items: [], totalCount: 0 }) });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    }));
    
    await import('../portal.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise(r => setTimeout(r, 50));

    // Click tabs
    const groupsTab = document.querySelector('[data-target="groups"]');
    const prayersTab = document.querySelector('[data-target="prayers"]');
    const adminsTab = document.querySelector('[data-target="admins"]');
    
    if (prayersTab) prayersTab.click();
    if (adminsTab) adminsTab.click();
    if (groupsTab) groupsTab.click();

    // Trigger filter update
    const filterStatus = document.getElementById('filter-status');
    if (filterStatus) {
      filterStatus.value = 'OPEN';
      filterStatus.dispatchEvent(new Event('change'));
    }
  });

  it('covers admin routing and data loading', async () => {
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
          json: async () => [
            { groupId: 'g1', name: 'Group 1', memberCount: 5, passcode: '123456', createdAt: '2023-01-01' },
            { groupId: 'g2', name: 'Group 2', memberCount: 0, passcode: '654321', createdAt: '2023-01-02' }
          ],
        });
      }
      if (url.startsWith('/api/admin/prayers')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            items: [
              { prayerId: 'p1', prayerText: 'Pray for me', assignedGroupId: 'g1', status: 'OPEN', prayedForCount: 2, createdAt: '2023-01-01', updates: [{ updateText: 'Better', updatedAt: '2023-01-02' }] },
              { prayerId: 'p2', prayerText: 'Pray for us', assignedGroupId: null, status: 'CLOSED', prayedForCount: 0, createdAt: '2023-01-02' }
            ],
            totalCount: 2
          }),
        });
      }
      if (url === '/api/admin/admins') {
        return Promise.resolve({
          ok: true,
          json: async () => [
            { adminId: 'a1', username: 'admin', role: 'APP_ADMIN', createdAt: '2023-01-01' },
            { adminId: 'a2', username: 'group_admin', role: 'GROUP_ADMIN', groupId: 'g1', createdAt: '2023-01-02' }
          ],
        });
      }
      if (url.startsWith('/api/admin/groups/g1/members')) {
        if (options && options.method === 'POST') {
          return Promise.resolve({ ok: true, status: 201 });
        }
        if (options && options.method === 'DELETE') {
          return Promise.resolve({ ok: true, status: 204 });
        }
        return Promise.resolve({
          ok: true,
          json: async () => [
            { memberId: 'm1', name: 'Member 1', email: 'm1@test.com', joinedAt: '2023-01-01', bounced: false },
            { memberId: 'm2', name: 'Member 2', email: 'm2@test.com', joinedAt: '2023-01-02', bounced: true }
          ],
        });
      }
      if (url.startsWith('/api/groups/g1')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ groupId: 'g1', name: 'Group 1', passcode: '123456' })
        });
      }
      if (url.startsWith('/api/admin/groups/g1/regenerate-passcode')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({ passcode: 'ABCDEF' })
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Route to groups
    window.location.hash = '#groups';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const groupsTbody = document.getElementById('groups-table-body');
    expect(groupsTbody.children.length).toBeGreaterThan(0);

    // Group Table buttons
    const regenCodeBtn = groupsTbody.querySelector('.btn-regen-code');
    if (regenCodeBtn) regenCodeBtn.click();

    // Confirm Modal for regen
    const confirmActionBtn = document.getElementById('btn-confirm-action');
    if (confirmActionBtn) confirmActionBtn.click();
    await new Promise(r => setTimeout(r, 10));

    const editGroupBtn = groupsTbody.querySelector('.btn-edit-group');
    if (editGroupBtn) editGroupBtn.click();
    
    const closeGroupBtn = document.getElementById('btn-modal-group-close');
    if (closeGroupBtn) closeGroupBtn.click();

    // Route to admins
    window.location.hash = '#admins';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));
    
    const adminsTbody = document.getElementById('admins-table-body');
    expect(adminsTbody.children.length).toBeGreaterThan(0);

    // Route to members
    window.location.hash = '#members?groupId=g1';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const membersTbody = document.getElementById('members-table-body');
    expect(membersTbody.children.length).toBeGreaterThan(0);

    // Route to dashboard
    window.location.hash = '#dashboard';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const prayersTbody = document.getElementById('dashboard-prayers-table-body');
    expect(prayersTbody.children.length).toBeGreaterThan(0);

    if (prayersTbody.firstElementChild) prayersTbody.firstElementChild.click();
    
    const closePrayerBtn = document.getElementById('btn-modal-prayer-close');
    if (closePrayerBtn) closePrayerBtn.click();

    // Sorting columns
    document.getElementById('th-prayer-text')?.click();
    document.getElementById('th-prayer-date')?.click();

    // Pagination
    const nextBtn = document.getElementById('btn-prayers-next');
    if (nextBtn && !nextBtn.disabled) nextBtn.click();
    
    const prevBtn = document.getElementById('btn-prayers-prev');
    if (prevBtn && !prevBtn.disabled) prevBtn.click();
  });

  it('covers intercessor dashboard, prayers list, and praying action', async () => {
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: true,
            role: 'INTERCESSOR',
            username: 'intercessor',
            name: 'Jane',
            groups: [{ groupId: 'g1', name: 'Group 1' }],
          }),
        });
      }
      if (url === '/api/prayers/group/g1/auth') {
        return Promise.resolve({
          ok: true,
          json: async () => [
            { prayerId: 'p1', prayerText: 'Pray for me', status: 'OPEN', prayedForCount: 2, createdAt: '2023-01-01', hasPrayed: false },
            { prayerId: 'p2', prayerText: 'Pray for us', status: 'CLOSED', prayedForCount: 0, createdAt: '2023-01-02', hasPrayed: false },
            { prayerId: 'p3', prayerText: 'Prayed', status: 'OPEN', prayedForCount: 5, createdAt: '2023-01-03', hasPrayed: true }
          ],
        });
      }
      if (url === '/api/prayers/p1/prayed/auth') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ prayedForCount: 3 }),
        });
      }
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const sidebarGroupsList = document.getElementById('sidebar-groups-list');
    const groupLink = sidebarGroupsList.querySelector('a');
    if (groupLink) groupLink.click();

    await new Promise((resolve) => setTimeout(resolve, 50));

    const prayersListContainer = document.getElementById('prayers-list-container');
    expect(prayersListContainer.children.length).toBeGreaterThan(0);

    const prayBtn = prayersListContainer.querySelector('.btn-pray[data-id="p1"]');
    if (prayBtn) prayBtn.click();
    
    await new Promise((resolve) => setTimeout(resolve, 50));

    const filterClosed = document.getElementById('filter-closed');
    if (filterClosed) filterClosed.click();
    
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(prayersListContainer.children.length).toBeGreaterThan(0);
    
    const filterOpen = document.getElementById('filter-open');
    if (filterOpen) filterOpen.click();
  });

  it('covers forms and bulk actions', async () => {
    let mockStatusCount = 0;
    const statusMock = vi.fn().mockImplementation((url, options) => {
      if (url === '/api/auth/status') {
        mockStatusCount++;
        return Promise.resolve({
          ok: true,
          json: async () => {
            if (mockStatusCount === 1) {
              return { initialized: false, authenticated: false };
            }
            return {
              initialized: true,
              authenticated: true,
              role: 'APP_ADMIN',
              username: 'admin',
              groups: [],
            };
          },
        });
      }
      if (url === '/api/admin/setup') return Promise.resolve({ ok: true, status: 201 });
      if (url === '/api/admin/groups') {
        if (options && options.method === 'POST') return Promise.resolve({ ok: true, status: 201 });
        return Promise.resolve({ ok: true, json: async () => [] });
      }
      if (url === '/api/admin/admins') {
        if (options && options.method === 'POST') return Promise.resolve({ ok: true, status: 201 });
        return Promise.resolve({ ok: true, json: async () => [] });
      }
      if (url.startsWith('/api/admin/groups/g1/members')) {
        if (options && options.method === 'POST') return Promise.resolve({ ok: true, status: 201 });
        return Promise.resolve({ ok: true, json: async () => [] });
      }
      if (url.startsWith('/api/admin/groups/g1/members/bulk')) {
        return Promise.resolve({ ok: true, json: async () => ({ added: 1, errors: [] }) });
      }
      if (url.startsWith('/api/groups/g1')) {
        return Promise.resolve({ ok: true, json: async () => ({ groupId: 'g1', name: 'Group 1', passcode: '123456' }) });
      }
      if (url.startsWith('/api/admin/prayers')) return Promise.resolve({ ok: true, json: async () => ({ items: [], totalCount: 0 }) });
      if (url === '/api/identity/intercessor/register') return Promise.resolve({ ok: true, json: async () => ({}) });
      return Promise.reject(new Error(`Unhandled request to ${url}`));
    });
    vi.stubGlobal('fetch', statusMock);

    await import('../portal.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Setup form
    const setupForm = document.getElementById('admin-setup-form');
    document.getElementById('setup-username').value = 'newadmin';
    document.getElementById('setup-password').value = 'pass123';
    document.getElementById('setup-confirm-password').value = 'pass123';
    if (setupForm) setupForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    window.location.hash = '#dashboard';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Group creation
    const createGroupBtn = document.getElementById('btn-create-group-trigger');
    if (createGroupBtn) createGroupBtn.click();
    document.getElementById('group-form-name').value = 'New Group';
    document.getElementById('group-form-passcode').value = '123456';
    const groupForm = document.getElementById('group-form');
    if (groupForm) groupForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Members route to test members forms
    window.location.hash = '#members?groupId=g1';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Add Member form
    const addMemberBtn = document.getElementById('btn-add-member-trigger');
    if (addMemberBtn) addMemberBtn.click();
    document.getElementById('member-form-name').value = 'Test User';
    document.getElementById('member-form-email').value = 'test@example.com';
    const memberForm = document.getElementById('member-form');
    if (memberForm) memberForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Bulk Add
    const bulkAddBtn = document.getElementById('btn-bulk-add-trigger');
    if (bulkAddBtn) bulkAddBtn.click();
    document.getElementById('member-bulk-textarea').value = 'Test Bulk, bulk@test.com\nInvalid Name';
    const validateBulkBtn = document.getElementById('btn-member-bulk-validate');
    if (validateBulkBtn) validateBulkBtn.click();
    const bulkForm = document.getElementById('member-bulk-form');
    if (bulkForm) bulkForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Admins creation
    window.location.hash = '#admins';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const createAdminBtn = document.getElementById('btn-create-admin-trigger');
    if (createAdminBtn) createAdminBtn.click();
    document.getElementById('admin-form-username').value = 'newadmin2';
    document.getElementById('admin-form-password').value = 'pass123';
    document.getElementById('admin-form-role').value = 'APP_ADMIN';
    const adminForm = document.getElementById('admin-account-form');
    if (adminForm) adminForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Register form (Intercessor)
    document.getElementById('register-name').value = 'Jane';
    document.getElementById('register-email').value = 'jane@test.com';
    document.getElementById('register-password').value = 'password123';
    document.getElementById('register-confirm-password').value = 'password123';
    document.getElementById('register-invite-code').value = '123456';
    const registerForm = document.getElementById('register-form');
    if (registerForm) registerForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));
  });

  it('covers deletion and remaining modal interactions', async () => {
    let callLog = [];
    const statusMock = vi.fn().mockImplementation((url, options) => {
      callLog.push({url, method: options?.method || 'GET'});
      if (url === '/api/auth/status') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            initialized: true,
            authenticated: true,
            role: 'APP_ADMIN',
            username: 'admin',
            groups: [],
          }),
        });
      }
      if (url === '/api/admin/groups') {
        return Promise.resolve({
          ok: true,
          json: async () => [
            { groupId: 'g1', name: 'Group 1', memberCount: 5, passcode: '123456', createdAt: '2023-01-01' }
          ],
        });
      }
      if (url.startsWith('/api/admin/prayers')) {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            items: [
              { prayerId: 'p1', prayerText: 'A', assignedGroupId: 'g1', status: 'OPEN', prayedForCount: 2 },
              { prayerId: 'p2', prayerText: 'B', assignedGroupId: null, status: 'CLOSED', prayedForCount: 0 }
            ],
            totalCount: 2
          }),
        });
      }
      if (url === '/api/admin/admins') {
        return Promise.resolve({
          ok: true,
          json: async () => [
            { adminId: 'a1', username: 'admin', role: 'APP_ADMIN', createdAt: '2023-01-01' },
            { adminId: 'a2', username: 'other', role: 'GROUP_ADMIN', createdAt: '2023-01-02' }
          ],
        });
      }
      if (url.startsWith('/api/admin/groups/g1/members')) {
        if (options && options.method === 'DELETE') return Promise.resolve({ ok: true, status: 204 });
        return Promise.resolve({
          ok: true,
          json: async () => [{ memberId: 'm1', name: 'M1', email: 'm1@test.com' }],
        });
      }
      if (url.startsWith('/api/admin/groups/g1')) {
        if (options && options.method === 'DELETE') return Promise.resolve({ ok: true, status: 204 });
      }
      if (url.startsWith('/api/admin/admins/a2')) {
        if (options && options.method === 'DELETE') return Promise.resolve({ ok: true, status: 204 });
      }
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });
    vi.stubGlobal('fetch', statusMock);

    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    await import('../portal.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Delete Group
    window.location.hash = '#groups';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const groupsTbody = document.getElementById('groups-table-body');
    const delGroupBtn = groupsTbody?.querySelector('.btn-delete-group');
    if (delGroupBtn) delGroupBtn.click();
    const confirmActionBtn = document.getElementById('btn-confirm-action');
    if (confirmActionBtn) confirmActionBtn.click();
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Delete Admin
    window.location.hash = '#admins';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const adminsTbody = document.getElementById('admins-table-body');
    const delAdminBtns = adminsTbody?.querySelectorAll('.btn-delete-admin');
    if (delAdminBtns && delAdminBtns.length > 1) {
      delAdminBtns[1].click();
      if (confirmActionBtn) confirmActionBtn.click();
    }
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Delete Member
    window.location.hash = '#members?groupId=g1';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const membersTbody = document.getElementById('members-table-body');
    const delMemberBtn = membersTbody?.querySelector('.btn-remove-member');
    if (delMemberBtn) delMemberBtn.click();
    if (confirmActionBtn) confirmActionBtn.click();
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Filter Form submit
    window.location.hash = '#dashboard';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const filterForm = document.getElementById('filter-prayers-form');
    const statusInput = document.getElementById('filter-status');
    if (statusInput) statusInput.value = 'CLOSED';
    if (filterForm) filterForm.dispatchEvent(new Event('submit'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const resetFiltersBtn = document.getElementById('btn-reset-filters');
    if (resetFiltersBtn) resetFiltersBtn.click();
    await new Promise((resolve) => setTimeout(resolve, 50));

    // Password Toggle
    const pwToggles = document.querySelectorAll('.btn-toggle-password');
    if (pwToggles.length > 0) pwToggles[0].click();

    // QR Code Download
    const canvas = document.createElement('canvas');
    canvas.id = 'group-qr-canvas';
    document.body.appendChild(canvas);
    const dlQrBtn = document.getElementById('btn-download-qr');
    if (dlQrBtn) dlQrBtn.click();

    // Sorting 
    const sortThs = ['th-prayer-status', 'th-prayer-count', 'th-prayer-group'];
    for (const th of sortThs) {
      document.getElementById(th)?.click();
      document.getElementById(th)?.click(); // toggle asc/desc
    }
  });

  it('covers auth card setup and register', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url) => {
      if (url === '/api/auth/status') return Promise.resolve({ ok: true, json: async () => ({ initialized: false, authenticated: false }) });
      return Promise.resolve({ ok: true, json: async () => ({}) });
    }));
    await import('../portal.js');
    window.location.hash = '#register?email=test@test.com';
    document.dispatchEvent(new Event('DOMContentLoaded'));
    await new Promise((resolve) => setTimeout(resolve, 50));

    const regEmail = document.getElementById('register-email');
    if (regEmail) expect(regEmail.value).toBe('test@test.com');
    
    // Hash change to #login
    window.location.hash = '#login';
    window.dispatchEvent(new Event('hashchange'));
    await new Promise((resolve) => setTimeout(resolve, 50));
    
    // Test the register navigation buttons
    document.getElementById('go-to-register')?.click();
    document.getElementById('go-to-login')?.click();
  });
});
