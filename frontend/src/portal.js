// portal.js - Entry point for the unified Prayer Link Portal
import './shared/apiClient.js';
import './styles/tokens.css';
import './styles/reset.css';
import './styles/base.css';
import './styles/components.css';
import './styles/animations.css';
import './styles/layout.css';
import './styles/admin.css';
import QRCode from 'qrcode';

// === State Variables ===
let currentUser = {
  initialized: false,
  authenticated: false,
  username: null,
  email: null,
  name: null,
  role: null,
  groupId: null,
  groups: [],
};

// Caches & Pagination for Admin view
let groupsCache = [];
let adminsCache = [];
let prayersCache = [];
let totalPrayersCount = 0;
let currentPrayersPage = 0;
const prayersPageSize = 20;

// Filter States (Admin)
let filterStatus = 'all';
let filterGroupId = 'all';
let filterFromDate = '';
let filterToDate = '';

// Sort States (Admin Prayers)
let sortField = 'createdAt';
let sortAscending = false;

// Active group for admin member management
let currentGroupDetails = null;

// Intercessor specific states
let selectedGroupId = null;
let intercessorActiveFilter = 'OPEN'; // 'OPEN' or 'CLOSED'
let intercessorCachedPrayers = [];

// === Initializer ===
document.addEventListener('DOMContentLoaded', () => {
  setupGlobalListeners();
  checkAuthStatus();
});

// === Auth & Routing Flow ===
async function checkAuthStatus() {
  try {
    const res = await fetch('/api/auth/status', { credentials: 'include' });
    if (!res.ok) throw new Error('Status fetch failed');
    const status = await res.json();

    currentUser.initialized = status.initialized;
    currentUser.authenticated = status.authenticated;
    currentUser.role = status.role;
    currentUser.username = status.username;
    currentUser.groupId = status.groupId;
    currentUser.email = status.email;
    currentUser.name = status.name;
    currentUser.groups = status.groups || [];

    renderLayout();

    if (!currentUser.initialized) {
      // First time platform setup
      showAuthCard('setup');
    } else if (!currentUser.authenticated) {
      // Not logged in, route to auth forms
      showAuthCard(window.location.hash.startsWith('#register') ? 'register' : 'login');
    } else {
      // Authenticated! Render appropriate dashboard
      hideAuthView();

      if (currentUser.role === 'APP_ADMIN' || currentUser.role === 'GROUP_ADMIN') {
        initAdminView();
      } else if (currentUser.role === 'INTERCESSOR') {
        initIntercessorView();
      }
    }
  } catch (err) {
    console.error('Auth check error:', err);
    showToast('Failed to connect to authentication service. Please try again.', 'error');
  }
}

function renderLayout() {
  const authContainer = document.getElementById('auth-container');
  const adminRoot = document.getElementById('admin-root');
  const intercessorRoot = document.getElementById('intercessor-root');
  const auroraBg = document.getElementById('aurora-bg');

  if (!currentUser.authenticated) {
    authContainer.style.display = 'flex';
    adminRoot.style.display = 'none';
    intercessorRoot.style.display = 'none';
    if (auroraBg) auroraBg.style.display = 'block';
  } else {
    authContainer.style.display = 'none';

    if (currentUser.role === 'APP_ADMIN' || currentUser.role === 'GROUP_ADMIN') {
      adminRoot.style.display = 'grid';
      intercessorRoot.style.display = 'none';
    } else if (currentUser.role === 'INTERCESSOR') {
      intercessorRoot.style.display = 'grid';
      adminRoot.style.display = 'none';
    }
  }
}

function showAuthCard(cardType) {
  const loginCard = document.getElementById('login-card');
  const registerCard = document.getElementById('register-card');
  const setupView = document.getElementById('setup-view');
  const loginView = document.getElementById('login-view');
  const adminRoot = document.getElementById('admin-root');
  const authContainer = document.getElementById('auth-container');

  authContainer.style.display = 'flex';
  adminRoot.style.display = 'none';

  loginCard.style.display = 'none';
  registerCard.style.display = 'none';
  if (setupView) setupView.style.display = 'none';

  if (cardType === 'setup') {
    if (setupView) setupView.style.display = 'block';
  } else if (cardType === 'register') {
    registerCard.style.display = 'block';

    // Parse email param if register?email=...
    const hash = window.location.hash || '';
    const pathAndQuery = hash.split('?');
    const queryString = pathAndQuery[1] || '';
    const urlParams = new URLSearchParams(queryString);
    const emailParam = urlParams.get('email');
    if (emailParam) {
      document.getElementById('register-email').value = emailParam;
    }
  } else {
    loginCard.style.display = 'block';
  }
}

function hideAuthView() {
  document.getElementById('auth-container').style.display = 'none';
}

// === ADMIN VIEW INITIALIZATION & ROUTING ===
function initAdminView() {
  const badge = document.getElementById('sidebar-user-badge');
  if (badge) {
    badge.textContent = `${currentUser.username} (${currentUser.role === 'APP_ADMIN' ? 'App Admin' : 'Circle Admin'})`;
  }
  renderAdminSidebarLinks();
  handleAdminHashRouting();
}

function renderAdminSidebarLinks() {
  const container = document.getElementById('sidebar-nav-links');
  if (!container) return;

  container.innerHTML = '';

  if (currentUser.role === 'APP_ADMIN') {
    container.innerHTML = `
      <a href="#dashboard" class="admin-sidebar__link" id="nav-dashboard">Dashboard</a>
      <a href="#groups" class="admin-sidebar__link" id="nav-groups">Prayer Circles</a>
      <a href="#admins" class="admin-sidebar__link" id="nav-admins">Console Admins</a>
      <a href="#logout" class="admin-sidebar__link" id="nav-logout" style="margin-top: auto; color: var(--color-error)">Logout</a>
    `;
  } else if (currentUser.role === 'GROUP_ADMIN') {
    container.innerHTML = `
      <a href="#members?groupId=${currentUser.groupId}" class="admin-sidebar__link" id="nav-members">My Circle</a>
      <a href="#dashboard" class="admin-sidebar__link" id="nav-dashboard">Circle Prayers</a>
      <a href="#logout" class="admin-sidebar__link" id="nav-logout" style="margin-top: auto; color: var(--color-error)">Logout</a>
    `;
  }
}

function handleAdminHashRouting() {
  const hash = window.location.hash || '#dashboard';

  // Collapse mobile nav on transition
  const navLinks = document.getElementById('sidebar-nav-links');
  if (navLinks) {
    navLinks.classList.remove('mobile-open');
  }

  // Remove active state from all links
  document.querySelectorAll('.admin-sidebar__link').forEach((link) => {
    link.classList.remove('active');
  });

  if (hash === '#logout') {
    handleLogout();
    return;
  }

  if (hash.startsWith('#dashboard')) {
    const link = document.getElementById('nav-dashboard');
    if (link) link.classList.add('active');
    navigateToAdminView('dashboard-view');
    loadDashboardData();
  } else if (hash.startsWith('#groups')) {
    const link = document.getElementById('nav-groups');
    if (link) link.classList.add('active');
    navigateToAdminView('groups-view');
    loadGroupsData();
  } else if (hash.startsWith('#admins')) {
    const link = document.getElementById('nav-admins');
    if (link) link.classList.add('active');
    navigateToAdminView('admins-view');
    loadAdminsData();
  } else if (hash.startsWith('#members')) {
    const link = document.getElementById('nav-members');
    if (link) link.classList.add('active');

    // Parse groupId from query parameters in hash
    const params = new URLSearchParams(hash.split('?')[1] || '');
    const groupId = params.get('groupId');

    if (groupId) {
      navigateToAdminView('members-view');
      loadGroupMembersData(groupId);
    } else {
      window.location.hash = '#dashboard';
    }
  }
}

function navigateToAdminView(viewId) {
  const views = [
    'setup-view',
    'dashboard-view',
    'groups-view',
    'members-view',
    'admins-view',
  ];
  views.forEach((v) => {
    const el = document.getElementById(v);
    if (el) el.style.display = v === viewId ? 'block' : 'none';
  });
}

// === INTERCESSOR VIEW INITIALIZATION & ROUTING ===
function initIntercessorView() {
  const sidebarUserName = document.getElementById('sidebar-user-name');
  if (sidebarUserName) {
    sidebarUserName.textContent = currentUser.name;
  }
  renderIntercessorDashboard();
}

function renderIntercessorDashboard() {
  const sidebarGroupsList = document.getElementById('sidebar-groups-list');
  if (!sidebarGroupsList) return;

  sidebarGroupsList.innerHTML = '';

  if (!currentUser.groups || currentUser.groups.length === 0) {
    sidebarGroupsList.innerHTML = `
      <div style="font-size: var(--font-size-xs); color: var(--color-text-muted); padding: var(--space-2) var(--space-3); font-style: italic;">
        Not assigned to any groups.
      </div>
    `;
    return;
  }

  currentUser.groups.forEach((group) => {
    const link = document.createElement('a');
    link.href = '#';
    link.className = `admin-sidebar__link ${selectedGroupId === group.groupId ? 'active' : ''}`;
    link.innerHTML = `${group.name}`;

    link.addEventListener('click', (e) => {
      e.preventDefault();

      const activeLink = sidebarGroupsList.querySelector('.active');
      if (activeLink) activeLink.classList.remove('active');
      link.classList.add('active');

      selectedGroupId = group.groupId;
      document.getElementById('main-group-title').textContent = group.name;
      document.getElementById('main-group-subtitle').textContent = `Browse and pray for requests in ${group.name}.`;

      loadGroupPrayers(group.groupId);
    });

    sidebarGroupsList.appendChild(link);
  });
}

// === ADMIN CORE API CALLS ===
async function loadDashboardData() {
  await populateGroupFilterDropdown();

  const statusFilter = filterStatus !== 'all' ? `&status=${filterStatus}` : '';
  const groupFilter = filterGroupId !== 'all' ? `&groupId=${filterGroupId}` : '';
  const fromFilter = filterFromDate ? `&fromDate=${filterFromDate}` : '';
  const toFilter = filterToDate ? `&toDate=${filterToDate}` : '';

  try {
    const res = await fetch(
      `/api/admin/prayers?page=${currentPrayersPage}&size=${prayersPageSize}${statusFilter}${groupFilter}${fromFilter}${toFilter}`
    );
    if (!res.ok) throw new Error('Failed to load prayers');
    const data = await res.json();

    prayersCache = data.items;
    totalPrayersCount = data.totalCount;

    sortAndRenderPrayers();
  } catch (err) {
    showToast('Failed to load prayers list.', 'error');
  }
}

async function populateGroupFilterDropdown() {
  const dropdown = document.getElementById('filter-group');
  const wrapper = document.getElementById('filter-group-wrapper');
  if (!dropdown) return;

  if (currentUser.role === 'GROUP_ADMIN') {
    if (wrapper) wrapper.style.display = 'none';
    filterGroupId = currentUser.groupId;
    return;
  } else {
    if (wrapper) wrapper.style.display = 'flex';
  }

  if (groupsCache.length === 0) {
    await fetchGroupsList();
  }

  const prevVal = dropdown.value;
  dropdown.innerHTML = '<option value="all">All Groups</option>';
  groupsCache.forEach((g) => {
    dropdown.innerHTML += `<option value="${g.groupId}">${escapeHtml(g.name)}</option>`;
  });

  if (prevVal && [...dropdown.options].some((o) => o.value === prevVal)) {
    dropdown.value = prevVal;
  } else {
    dropdown.value = filterGroupId;
  }
}

async function fetchGroupsList() {
  try {
    const res = await fetch('/api/admin/groups');
    if (res.ok) {
      groupsCache = await res.json();
    }
  } catch (e) {
    console.error('Failed to pre-fetch groups list', e);
  }
}

async function loadGroupsData() {
  if (currentUser.role !== 'APP_ADMIN') return;
  await fetchGroupsList();
  renderGroupsTable();
}

async function loadAdminsData() {
  if (currentUser.role !== 'APP_ADMIN') return;
  try {
    const res = await fetch('/api/admin/admins');
    if (!res.ok) throw new Error();
    adminsCache = await res.json();

    renderAdminsTable();
  } catch (err) {
    showToast('Failed to load console administrators.', 'error');
  }
}

async function loadGroupMembersData(groupId) {
  if (currentUser.role === 'GROUP_ADMIN' && currentUser.groupId !== groupId) {
    showToast('Permission denied.', 'error');
    window.location.hash = '#dashboard';
    return;
  }

  let group = groupsCache.find((g) => g.groupId === groupId);
  if (!group) {
    try {
      const res = await fetch(`/api/groups/${groupId}`);
      if (res.ok) group = await res.json();
    } catch (e) {
      console.error(e);
    }
  }

  currentGroupDetails = group || {
    groupId,
    name: 'Private Circle',
    passcode: '-',
  };

  document.getElementById('breadcrumb-group-name').textContent = currentGroupDetails.name;
  document.getElementById('member-group-title').textContent = currentGroupDetails.name;
  document.getElementById('lbl-group-passcode').textContent = currentGroupDetails.passcode;

  const directLink = `${window.location.origin}/?groupId=${groupId}`;
  const lnkShare = document.getElementById('lnk-group-share');
  lnkShare.href = directLink;
  lnkShare.textContent = directLink;

  const canvas = document.getElementById('group-qr-canvas');
  if (canvas) {
    QRCode.toCanvas(canvas, directLink, { width: 200, margin: 1 }, (err) => {
      if (err) console.error('QR code generation failed', err);
    });
  }

  const editBtn = document.getElementById('btn-edit-my-group');
  if (editBtn) {
    editBtn.style.display = currentUser.role === 'GROUP_ADMIN' ? 'inline-block' : 'none';
  }

  try {
    const res = await fetch(`/api/admin/groups/${groupId}/members`);
    if (!res.ok) throw new Error();
    const members = await res.json();

    renderMembersTable(members);
  } catch (err) {
    showToast('Failed to retrieve circle member list.', 'error');
  }
}

// === INTERCESSOR CORE API CALLS ===
function loadGroupPrayers(groupId) {
  const emptyState = document.getElementById('empty-state');
  const prayersListContainer = document.getElementById('prayers-list-container');
  const filterToolbar = document.getElementById('filter-toolbar');

  emptyState.style.display = 'none';
  prayersListContainer.style.display = 'none';
  filterToolbar.style.display = 'flex';

  fetch(`/api/prayers/group/${groupId}/auth`)
    .then((res) => {
      if (!res.ok) throw new Error('Failed to load group prayers');
      return res.json();
    })
    .then((prayers) => {
      intercessorCachedPrayers = prayers;
      renderIntercessorPrayersList();
    })
    .catch((err) => {
      showToast(err.message, 'error');
    });
}

// === RENDER TABLES (ADMIN) ===
function renderPrayersTable() {
  const tbody = document.getElementById('dashboard-prayers-table-body');
  if (!tbody) return;

  tbody.innerHTML = '';

  if (prayersCache.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center" style="padding: var(--space-8); color: var(--color-text-muted);">
          No prayers found matching criteria.
        </td>
      </tr>
    `;
    updatePaginationControls();
    return;
  }

  prayersCache.forEach((p) => {
    const tr = document.createElement('tr');
    tr.style.cursor = 'pointer';
    tr.addEventListener('click', () => openPrayerDetailModal(p));

    const dateStr = new Date(p.createdAt).toLocaleDateString();
    const groupName = getGroupNameCached(p.assignedGroupId);
    const updatesText =
      p.updates && p.updates.length > 0
        ? `<div style="font-size: var(--font-size-xs); color: var(--color-success); margin-top: 4px; font-style: italic;">✓ Answered update included</div>`
        : '';

    tr.innerHTML = `
      <td>
        <div style="font-weight: 500;">"${escapeHtml(truncateString(p.prayerText, 70))}"</div>
        ${updatesText}
      </td>
      <td>${escapeHtml(groupName)}</td>
      <td><span class="status-badge ${p.status.toLowerCase()}">${p.status}</span></td>
      <td style="text-align: right; font-weight: 600;">🙏 ${p.prayedForCount || 0}</td>
      <td style="text-align: right; color: var(--color-text-muted);">${dateStr}</td>
    `;
    tbody.appendChild(tr);
  });

  updatePaginationControls();
}

function renderGroupsTable() {
  const tbody = document.getElementById('groups-table-body');
  if (!tbody) return;

  tbody.innerHTML = '';

  if (groupsCache.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" class="text-center" style="padding: var(--space-8); color: var(--color-text-muted);">
          No prayer groups circles configured.
        </td>
      </tr>
    `;
    return;
  }

  groupsCache.forEach((g) => {
    const tr = document.createElement('tr');
    const dateStr = new Date(g.createdAt).toLocaleDateString();

    tr.innerHTML = `
      <td>
        <div style="font-weight: 600; color: var(--color-text-primary);">${escapeHtml(g.name)}</div>
        <div style="font-size: var(--font-size-xs); color: var(--color-text-muted);">${escapeHtml(g.description || '')}</div>
      </td>
      <td style="text-align: right; font-weight: 600;">👥 ${g.memberCount || 0}</td>
      <td><code style="background: rgba(0,0,0,0.04); padding: 2px 6px; border-radius: 4px; font-weight: 700;">${g.passcode}</code></td>
      <td>${g.optOutGeneral ? '🔴 Excluded' : '🟢 Included'}</td>
      <td style="color: var(--color-text-muted);">${dateStr}</td>
      <td style="text-align: right; white-space: nowrap;">
        <div style="display: flex; gap: var(--space-2); justify-content: flex-end;">
          <button class="btn-secondary btn-sm" onclick="window.location.hash = '#members?groupId=${g.groupId}'">Members</button>
          <button class="btn-secondary btn-sm btn-edit-group" data-id="${g.groupId}">Edit</button>
          <button class="btn-secondary btn-sm btn-regen-code" data-id="${g.groupId}" title="Regenerate Passcode">🔑</button>
          <button class="btn-secondary btn-sm btn-delete-group" data-id="${g.groupId}" style="color: var(--color-error)">Delete</button>
        </div>
      </td>
    `;
    tbody.appendChild(tr);
  });

  // Bind actions
  tbody.querySelectorAll('.btn-edit-group').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const group = groupsCache.find((g) => g.groupId === btn.getAttribute('data-id'));
      openGroupModal(group);
    });
  });

  tbody.querySelectorAll('.btn-regen-code').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const groupId = btn.getAttribute('data-id');
      const group = groupsCache.find((g) => g.groupId === groupId);
      showConfirmModal(
        'Regenerate Passcode?',
        `Generating a new passcode for '${group.name}' will immediately revoke the current passcode '${group.passcode}'. Members trying to join with the old passcode will be blocked.`,
        async () => {
          try {
            const res = await fetch(`/api/admin/groups/${groupId}/regenerate-passcode`, { method: 'POST' });
            if (res.ok) {
              const data = await res.json();
              showToast(`New passcode generated: ${data.passcode}`, 'success');
              loadGroupsData();
            } else {
              throw new Error();
            }
          } catch {
            showToast('Failed to regenerate passcode.', 'error');
          }
        }
      );
    });
  });

  tbody.querySelectorAll('.btn-delete-group').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const groupId = btn.getAttribute('data-id');
      const group = groupsCache.find((g) => g.groupId === groupId);
      showConfirmModal(
        'Delete Circle Group?',
        `Are you sure you want to delete '${group.name}'? This will permanently remove all of its ${group.memberCount || 0} intercessor members. Existing prayers assigned to this circle will remain. This action cannot be undone.`,
        async () => {
          try {
            const res = await fetch(`/api/admin/groups/${groupId}`, { method: 'DELETE' });
            if (res.ok || res.status === 204) {
              showToast('Group deleted successfully.', 'success');
              loadGroupsData();
            } else {
              throw new Error();
            }
          } catch {
            showToast('Failed to delete group.', 'error');
          }
        }
      );
    });
  });
}

function renderMembersTable(members) {
  const tbody = document.getElementById('members-table-body');
  if (!tbody) return;

  tbody.innerHTML = '';

  if (members.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center" style="padding: var(--space-8); color: var(--color-text-muted);">
          No registered intercessor members found in this circle.
        </td>
      </tr>
    `;
    return;
  }

  members.forEach((m) => {
    const tr = document.createElement('tr');
    const dateStr = new Date(m.joinedAt).toLocaleDateString();
    const statusIcon = m.bounced
      ? '<span title="Bouncing/Delivery Failure">🔴 Bounced</span>'
      : '<span title="Delivery Healthy">🟢 Active</span>';

    tr.innerHTML = `
      <td><strong>${escapeHtml(m.name || '')}</strong></td>
      <td>${escapeHtml(m.email)}</td>
      <td>${statusIcon}</td>
      <td style="color: var(--color-text-muted);">${dateStr}</td>
      <td style="text-align: right;">
        <button class="btn-secondary btn-sm btn-remove-member" data-id="${m.memberId}" style="color: var(--color-error)">Remove</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  tbody.querySelectorAll('.btn-remove-member').forEach((btn) => {
    btn.addEventListener('click', () => {
      const memberId = btn.getAttribute('data-id');
      const member = members.find((m) => m.memberId === memberId);
      showConfirmModal(
        'Remove Intercessor?',
        `Remove ${member.name || member.email} from the circle '${currentGroupDetails.name}'? They will no longer receive prayer requests for this group.`,
        async () => {
          try {
            const res = await fetch(`/api/admin/groups/${currentGroupDetails.groupId}/members/${memberId}`, {
              method: 'DELETE',
            });
            if (res.ok || res.status === 204) {
              showToast('Intercessor removed.', 'success');
              loadGroupMembersData(currentGroupDetails.groupId);
            } else {
              throw new Error();
            }
          } catch {
            showToast('Failed to remove member.', 'error');
          }
        }
      );
    });
  });
}

function renderAdminsTable() {
  const tbody = document.getElementById('admins-table-body');
  if (!tbody) return;

  tbody.innerHTML = '';

  adminsCache.forEach((a) => {
    const tr = document.createElement('tr');
    const dateStr = new Date(a.createdAt).toLocaleDateString();
    const isSelf = a.username === currentUser.username;
    const groupName = a.groupId ? getGroupNameCached(a.groupId) : 'All Circles (Full Access)';

    tr.innerHTML = `
      <td><strong>${escapeHtml(a.username)}</strong> ${isSelf ? '<span class="status-badge open">You</span>' : ''}</td>
      <td><span class="status-badge ${a.role === 'APP_ADMIN' ? 'closed' : 'open'}">${a.role === 'APP_ADMIN' ? 'App Admin' : 'Group Admin'}</span></td>
      <td>${escapeHtml(groupName)}</td>
      <td style="color: var(--color-text-muted);">${dateStr}</td>
      <td style="text-align: right;">
        <button class="btn-secondary btn-sm btn-delete-admin" data-id="${a.adminId}" ${isSelf ? 'disabled title="You cannot delete yourself."' : ''} style="color: var(--color-error)">Delete</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  tbody.querySelectorAll('.btn-delete-admin').forEach((btn) => {
    btn.addEventListener('click', () => {
      const adminId = btn.getAttribute('data-id');
      const admin = adminsCache.find((a) => a.adminId === adminId);
      showConfirmModal(
        'Delete Console Account?',
        `Are you sure you want to delete administrator '${admin.username}'? They will immediately lose access to the console.`,
        async () => {
          try {
            const res = await fetch(`/api/admin/admins/${adminId}`, { method: 'DELETE' });
            if (res.ok || res.status === 204) {
              showToast('Console account deleted.', 'success');
              loadAdminsData();
            } else {
              const err = await res.json();
              showToast(err.error || 'Failed to delete admin.', 'error');
            }
          } catch {
            showToast('Failed to delete admin.', 'error');
          }
        }
      );
    });
  });
}

function updatePaginationControls() {
  const prevBtn = document.getElementById('btn-prayers-prev');
  const nextBtn = document.getElementById('btn-prayers-next');
  const infoLabel = document.getElementById('prayers-page-info');

  if (!prevBtn || !nextBtn || !infoLabel) return;

  const totalPages = Math.ceil(totalPrayersCount / prayersPageSize);
  prevBtn.disabled = currentPrayersPage <= 0;
  nextBtn.disabled = currentPrayersPage >= totalPages - 1 || totalPages === 0;

  const startIdx = totalPrayersCount === 0 ? 0 : currentPrayersPage * prayersPageSize + 1;
  const endIdx = Math.min((currentPrayersPage + 1) * prayersPageSize, totalPrayersCount);

  infoLabel.textContent = `Showing ${startIdx}-${endIdx} of ${totalPrayersCount} requests (Page ${currentPrayersPage + 1} of ${totalPages || 1})`;
}

// === RENDER PRAYERS (INTERCESSOR) ===
function renderIntercessorPrayersList() {
  const prayersListContainer = document.getElementById('prayers-list-container');
  if (!prayersListContainer) return;

  prayersListContainer.innerHTML = '';
  const filtered = intercessorCachedPrayers.filter((p) => p.status === intercessorActiveFilter);

  if (filtered.length === 0) {
    prayersListContainer.style.display = 'block';
    prayersListContainer.innerHTML = `
      <div class="text-center py-8 card w-full">
        <span style="font-size: var(--font-size-2xl);">🕊️</span>
        <p class="color-text-secondary mt-2">No ${intercessorActiveFilter === 'OPEN' ? 'active' : 'answered'} prayer requests in this circle.</p>
      </div>
    `;
    return;
  }

  prayersListContainer.style.display = 'grid';

  filtered.forEach((prayer) => {
    const card = document.createElement('article');
    card.className = 'card flex flex-col justify-between';
    card.style.padding = 'var(--space-6)';
    card.style.borderRadius = 'var(--radius-md)';
    card.style.minHeight = '180px';

    const timeStr = new Date(prayer.createdAt).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });

    let actionBtnHtml = '';
    if (prayer.status === 'CLOSED') {
      actionBtnHtml = `
        <div style="font-size: var(--font-size-xs); font-weight: 600; color: var(--color-success); display: flex; align-items: center; gap: var(--space-1);">
          🎉 Answered / Closed
        </div>
      `;
    } else if (prayer.hasPrayed) {
      actionBtnHtml = `
        <button class="btn-success w-full" disabled style="display: flex; align-items: center; justify-content: center; gap: var(--space-1); background: var(--color-success) !important; color: white;">
          ✓ Offered
        </button>
      `;
    } else {
      actionBtnHtml = `
        <button class="btn-primary w-full btn-pray" data-id="${prayer.prayerId}">
          Pray For This
        </button>
      `;
    }

    card.innerHTML = `
      <div>
        <div class="flex justify-between items-center mb-3">
          <span class="color-text-muted" style="font-size: var(--font-size-xs); font-weight: 500;">${timeStr}</span>
          <span class="badge flex items-center gap-1" style="font-size: var(--font-size-xs);" id="badge-${prayer.prayerId}">
            🙏 <span class="badge-count">${prayer.prayedForCount}</span>
          </span>
        </div>
        <p class="font-display mb-6 color-text-primary" style="font-size: var(--font-size-md); font-style: italic; line-height: 1.5;">
          "${prayer.prayerText}"
        </p>
      </div>
      <div>
        ${actionBtnHtml}
      </div>
    `;

    const prayBtn = card.querySelector('.btn-pray');
    if (prayBtn) {
      prayBtn.addEventListener('click', (e) => {
        const prayerId = prayBtn.getAttribute('data-id');

        fetch(`/api/prayers/${prayerId}/prayed/auth`, { method: 'POST' })
          .then((res) => {
            if (res.status === 409) {
              showToast("You've already recorded your prayer for this request.", 'info');
              prayBtn.textContent = '✓ Offered';
              prayBtn.className = 'btn-success w-full';
              prayBtn.disabled = true;
              throw new Error('Already prayed');
            }
            if (!res.ok) throw new Error('Failed to record prayer action.');
            return res.json();
          })
          .then((data) => {
            const badgeCount = card.querySelector(`#badge-${prayerId} .badge-count`);
            if (badgeCount) badgeCount.textContent = data.prayedForCount;

            triggerEmojiBurst(e, prayBtn);

            prayBtn.textContent = '✓ Offered';
            prayBtn.className = 'btn-success w-full';
            prayBtn.disabled = true;

            const cachedP = intercessorCachedPrayers.find((p) => p.prayerId === prayerId);
            if (cachedP) {
              cachedP.hasPrayed = true;
              cachedP.prayedForCount = data.prayedForCount;
            }

            showToast('Thank you for offering your prayer!', 'success');
          })
          .catch((err) => {
            if (err.message !== 'Already prayed') {
              showToast(err.message, 'error');
            }
          });
      });
    }

    prayersListContainer.appendChild(card);
  });
}

// === ACTION LISTENERS & BINDINGS ===
function setupGlobalListeners() {
  window.addEventListener('hashchange', () => {
    if (currentUser.authenticated) {
      if (currentUser.role === 'APP_ADMIN' || currentUser.role === 'GROUP_ADMIN') {
        handleAdminHashRouting();
      }
    } else {
      showAuthCard(window.location.hash.startsWith('#register') ? 'register' : 'login');
    }
  });

  // Password visibility button toggle
  document.querySelectorAll('.btn-toggle-password').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = btn.previousElementSibling;
      const eyeOpen = btn.querySelector('.icon-eye');
      const eyeClosed = btn.querySelector('.icon-eye-off');

      if (input.type === 'password') {
        input.type = 'text';
        if (eyeOpen) eyeOpen.style.display = 'none';
        if (eyeClosed) eyeClosed.style.display = 'block';
      } else {
        input.type = 'password';
        if (eyeOpen) eyeOpen.style.display = 'block';
        if (eyeClosed) eyeClosed.style.display = 'none';
      }
    });
  });

  // Setup platform initial APP_ADMIN form
  const setupForm = document.getElementById('admin-setup-form');
  if (setupForm) {
    setupForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('setup-username').value;
      const password = document.getElementById('setup-password').value;
      const confirmPass = document.getElementById('setup-confirm-password').value;

      if (password !== confirmPass) {
        showToast('Passwords do not match.', 'error');
        return;
      }

      const submitBtn = document.getElementById('btn-setup-submit');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Initializing...';

      try {
        const res = await fetch('/api/admin/setup', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password }),
        });

        if (res.status === 201 || res.ok) {
          const data = await res.json();
          if (data.token) localStorage.setItem('pl-auth-token', data.token);
          showToast('Platform initialized successfully!', 'success');
          await checkAuthStatus();
          window.location.hash = '#dashboard';
        } else {
          const err = await res.json();
          showToast(err.error || 'Initialization failed.', 'error');
          submitBtn.disabled = false;
          submitBtn.textContent = 'Initialize Admin';
        }
      } catch (err) {
        showToast('Platform initialization request failed.', 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Initialize Admin';
      }
    });
  }

  // Unified Login submission
  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const identifier = document.getElementById('login-identifier').value;
      const password = document.getElementById('login-password').value;

      const submitBtn = loginForm.querySelector('button[type="submit"]');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Verifying...';

      try {
        const res = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ identifier, password }),
        });

        if (res.ok) {
          const data = await res.json();
          if (data.token) localStorage.setItem('pl-auth-token', data.token);
          showToast(`Welcome back, ${data.name || data.username}!`, 'success');
          await checkAuthStatus();
          window.location.hash = '#dashboard';
        } else {
          const err = await res.json();
          showToast(err.error || 'Invalid credentials.', 'error');
          submitBtn.disabled = false;
          submitBtn.textContent = 'Log In';
        }
      } catch (err) {
        showToast('Login request failed.', 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Log In';
      }
    });
  }

  // Registration form submission (Intercessor accounts)
  const registerForm = document.getElementById('register-form');
  if (registerForm) {
    registerForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('register-name').value;
      const email = document.getElementById('register-email').value;
      const password = document.getElementById('register-password').value;
      const confirmPassword = document.getElementById('register-confirm-password').value;
      const inviteCode = document.getElementById('register-invite-code').value;

      if (password.length < 8) {
        showToast('Password must be at least 8 characters long', 'error');
        return;
      }

      if (password !== confirmPassword) {
        showToast('Passwords do not match', 'error');
        return;
      }

      fetch('/api/identity/intercessor/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password, inviteCode }),
      })
        .then(async (res) => {
          if (res.status === 409) {
            throw new Error('An account already exists for this email address.');
          }
          if (!res.ok) {
            try {
              const errData = await res.json();
              if (errData && errData.error) {
                throw new Error(errData.error);
              }
            } catch (e) {
              // ignore json parse error and throw general error
            }
            throw new Error('Registration failed. Please try again.');
          }
          return res.json();
        })
        .then(() => {
          showToast('Account created successfully!', 'success');
          window.location.hash = '#dashboard';
          checkAuthStatus();
        })
        .catch((err) => {
          showToast(err.message, 'error');
        });
    });
  }

  // Registration navigation triggers
  document.getElementById('go-to-register').addEventListener('click', (e) => {
    e.preventDefault();
    window.location.hash = '#register';
  });

  document.getElementById('go-to-login').addEventListener('click', (e) => {
    e.preventDefault();
    window.location.hash = '#login';
  });

  // Admin Logout triggers
  const adminLogoutBtn = document.getElementById('btn-logout');
  if (adminLogoutBtn) {
    adminLogoutBtn.addEventListener('click', handleLogout);
  }

  // Intercessor Logout triggers
  const intercessorLogoutBtn = document.getElementById('btn-intercessor-logout');
  if (intercessorLogoutBtn) {
    intercessorLogoutBtn.addEventListener('click', handleLogout);
  }

  // Admin filtering and paging listeners
  const filterForm = document.getElementById('filter-prayers-form');
  if (filterForm) {
    filterForm.addEventListener('submit', (e) => {
      e.preventDefault();
      filterStatus = document.getElementById('filter-status').value;
      filterGroupId = document.getElementById('filter-group')
        ? document.getElementById('filter-group').value
        : currentUser.groupId;
      filterFromDate = document.getElementById('filter-from-date').value;
      filterToDate = document.getElementById('filter-to-date').value;
      currentPrayersPage = 0;
      loadDashboardData();
    });

    document.getElementById('btn-reset-filters').addEventListener('click', () => {
      document.getElementById('filter-status').value = 'all';
      if (document.getElementById('filter-group')) {
        document.getElementById('filter-group').value = 'all';
      }
      document.getElementById('filter-from-date').value = '';
      document.getElementById('filter-to-date').value = '';
      filterStatus = 'all';
      filterGroupId = currentUser.groupId || 'all';
      filterFromDate = '';
      filterToDate = '';
      currentPrayersPage = 0;
      loadDashboardData();
    });
  }

  document.getElementById('btn-prayers-prev').addEventListener('click', () => {
    if (currentPrayersPage > 0) {
      currentPrayersPage--;
      loadDashboardData();
    }
  });

  document.getElementById('btn-prayers-next').addEventListener('click', () => {
    currentPrayersPage++;
    loadDashboardData();
  });

  // Sorting triggers
  document.getElementById('th-prayer-text').addEventListener('click', () => toggleSort('prayerText'));
  document.getElementById('th-prayer-group').addEventListener('click', () => toggleSort('assignedGroupId'));
  document.getElementById('th-prayer-status').addEventListener('click', () => toggleSort('status'));
  document.getElementById('th-prayer-count').addEventListener('click', () => toggleSort('prayedForCount'));
  document.getElementById('th-prayer-date').addEventListener('click', () => toggleSort('createdAt'));

  const editMyGroupBtn = document.getElementById('btn-edit-my-group');
  if (editMyGroupBtn) {
    editMyGroupBtn.addEventListener('click', () => {
      openGroupModal(currentGroupDetails);
    });
  }

  const toggleBtn = document.getElementById('btn-sidebar-mobile-toggle');
  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      const navLinks = document.getElementById('sidebar-nav-links');
      if (navLinks) {
        navLinks.classList.toggle('mobile-open');
      }
    });
  }

  document.getElementById('btn-create-group-trigger').addEventListener('click', () => {
    openGroupModal();
  });

  document.getElementById('btn-group-passcode-generate').addEventListener('click', () => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    let code = '';
    for (let i = 0; i < 6; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    document.getElementById('group-form-passcode').value = code;
  });

  document.getElementById('btn-modal-group-close').addEventListener('click', closeGroupModal);
  document.getElementById('btn-group-form-cancel').addEventListener('click', closeGroupModal);

  const groupForm = document.getElementById('group-form');
  groupForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('group-form-id').value;
    const name = document.getElementById('group-form-name').value;
    const passcode = document.getElementById('group-form-passcode').value.toUpperCase().trim();
    const optOutGeneral = document.getElementById('group-form-optout').checked;

    if (passcode.length !== 6) {
      showToast('Passcode must be exactly 6 characters.', 'error');
      return;
    }

    const payload = { name, passcode, optOutGeneral };
    const method = id ? 'PUT' : 'POST';
    const url = id ? `/api/admin/groups/${id}` : '/api/admin/groups';

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (res.ok || res.status === 201) {
        showToast('Circle details saved successfully.', 'success');
        closeGroupModal();
        loadGroupsData();
      } else if (res.status === 409) {
        const feedback = document.getElementById('group-form-passcode-feedback');
        feedback.textContent = 'Passcode is already in use by another circle.';
        feedback.style.display = 'block';
      } else {
        throw new Error();
      }
    } catch {
      showToast('Failed to save group details.', 'error');
    }
  });

  document.getElementById('btn-add-member-trigger').addEventListener('click', () => {
    openMemberModal();
  });
  document.getElementById('btn-modal-member-close').addEventListener('click', closeMemberModal);
  document.getElementById('btn-member-form-cancel').addEventListener('click', closeMemberModal);

  document.getElementById('member-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('member-form-name').value.trim();
    const email = document.getElementById('member-form-email').value.trim();

    try {
      const res = await fetch(`/api/admin/groups/${currentGroupDetails.groupId}/members`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email }),
      });

      if (res.status === 201 || res.ok) {
        showToast('Intercessor added successfully.', 'success');
        closeMemberModal();
        loadGroupMembersData(currentGroupDetails.groupId);
      } else {
        const err = await res.json();
        showToast(err.error || 'Failed to add intercessor.', 'error');
      }
    } catch {
      showToast('Network error while adding member.', 'error');
    }
  });

  document.getElementById('btn-bulk-add-trigger').addEventListener('click', () => {
    openBulkModal();
  });
  document.getElementById('btn-modal-member-bulk-close').addEventListener('click', closeBulkModal);
  document.getElementById('btn-member-bulk-cancel').addEventListener('click', closeBulkModal);

  document.getElementById('btn-member-bulk-validate').addEventListener('click', () => {
    const csvContent = document.getElementById('member-bulk-textarea').value;
    const parsed = parseCSVRecords(csvContent);
    renderBulkPreview(parsed);
  });

  document.getElementById('member-bulk-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const csvContent = document.getElementById('member-bulk-textarea').value;
    const parsed = parseCSVRecords(csvContent);
    const validRows = parsed
      .filter((row) => row.valid)
      .map((row) => ({ name: row.name, email: row.email }));

    if (validRows.length === 0) {
      showToast('No valid rows to import.', 'error');
      return;
    }

    const submitBtn = document.getElementById('btn-member-bulk-submit');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Importing...';

    try {
      const res = await fetch(`/api/admin/groups/${currentGroupDetails.groupId}/members/bulk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ members: validRows }),
      });

      if (res.ok) {
        const result = await res.json();
        showToast(`Bulk import finished. Added: ${result.added}, Failures: ${result.errors.length}`, 'info');
        closeBulkModal();
        loadGroupMembersData(currentGroupDetails.groupId);
      } else {
        showToast('Failed to execute bulk import.', 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Import Valid Rows';
      }
    } catch {
      showToast('Bulk import network request failed.', 'error');
      submitBtn.disabled = false;
      submitBtn.textContent = 'Import Valid Rows';
    }
  });

  document.getElementById('btn-create-admin-trigger').addEventListener('click', () => {
    openAdminModal();
  });
  document.getElementById('btn-modal-admin-close').addEventListener('click', closeAdminModal);
  document.getElementById('btn-admin-form-cancel').addEventListener('click', closeAdminModal);

  const adminRoleSelect = document.getElementById('admin-form-role');
  adminRoleSelect.addEventListener('change', () => {
    const isGroup = adminRoleSelect.value === 'GROUP_ADMIN';
    document.getElementById('admin-form-group-select-wrapper').style.display = isGroup ? 'block' : 'none';
  });

  document.getElementById('admin-account-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('admin-form-username').value.trim();
    const password = document.getElementById('admin-form-password').value;
    const role = document.getElementById('admin-form-role').value;
    const groupId = role === 'GROUP_ADMIN' ? document.getElementById('admin-form-group-select').value : '';

    if (role === 'GROUP_ADMIN' && !groupId) {
      showToast('You must select a circle group for GROUP_ADMIN.', 'error');
      return;
    }

    try {
      const res = await fetch('/api/admin/admins', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, role, groupId }),
      });

      if (res.status === 201 || res.ok) {
        showToast('Console account created successfully.', 'success');
        closeAdminModal();
        loadAdminsData();
      } else if (res.status === 409) {
        showToast('Username already exists.', 'error');
      } else {
        showToast('Failed to create admin.', 'error');
      }
    } catch {
      showToast('Network error creating admin.', 'error');
    }
  });

  document.getElementById('btn-modal-prayer-close').addEventListener('click', closePrayerDetailModal);

  document.getElementById('btn-download-qr').addEventListener('click', () => {
    const canvas = document.getElementById('group-qr-canvas');
    if (!canvas) return;

    const dataUrl = canvas.toDataURL('image/png');
    const link = document.createElement('a');
    link.download = `${currentGroupDetails.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-qr.png`;
    link.href = dataUrl;
    link.click();
  });

  // Intercessor filter toolbar listeners
  const filterOpen = document.getElementById('filter-open');
  const filterClosed = document.getElementById('filter-closed');

  if (filterOpen) {
    filterOpen.addEventListener('click', () => {
      filterOpen.style.background = 'var(--color-surface-solid)';
      filterOpen.style.fontWeight = '600';
      filterOpen.style.boxShadow = 'var(--shadow-sm)';

      filterClosed.style.background = 'transparent';
      filterClosed.style.fontWeight = '500';

      intercessorActiveFilter = 'OPEN';
      renderIntercessorPrayersList();
    });
  }

  if (filterClosed) {
    filterClosed.addEventListener('click', () => {
      filterClosed.style.background = 'var(--color-surface-solid)';
      filterClosed.style.fontWeight = '600';
      filterClosed.style.boxShadow = 'var(--shadow-sm)';

      filterOpen.style.background = 'transparent';
      filterOpen.style.fontWeight = '500';

      intercessorActiveFilter = 'CLOSED';
      renderIntercessorPrayersList();
    });
  }
}

async function handleLogout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST' });
    localStorage.removeItem('pl-auth-token');
    showToast('Signed out successfully.', 'info');
  } catch (e) {
    console.error('Logout error:', e);
  }

  currentUser = {
    initialized: true,
    authenticated: false,
    username: null,
    email: null,
    name: null,
    role: null,
    groupId: null,
    groups: [],
  };

  selectedGroupId = null;
  intercessorCachedPrayers = [];

  window.location.hash = '#login';
  checkAuthStatus();
}

// === Modal Controllers ===
function openGroupModal(group = null) {
  document.getElementById('group-modal-title').textContent = group ? 'Edit Circle Details' : 'Create Circle';
  document.getElementById('group-form-id').value = group ? group.groupId : '';
  document.getElementById('group-form-name').value = group ? group.name : '';
  document.getElementById('group-form-passcode').value = group ? group.passcode : '';
  document.getElementById('group-form-optout').checked = group ? group.optOutGeneral : false;
  document.getElementById('group-form-passcode-feedback').style.display = 'none';

  const nameInput = document.getElementById('group-form-name');
  if (nameInput) {
    nameInput.disabled = currentUser.role === 'GROUP_ADMIN';
  }

  document.getElementById('modal-group-backdrop').style.display = 'block';
  document.getElementById('modal-group').style.display = 'block';
  document.getElementById('group-form-name').focus();
}

function closeGroupModal() {
  const nameInput = document.getElementById('group-form-name');
  if (nameInput) {
    nameInput.disabled = false;
  }
  document.getElementById('modal-group-backdrop').style.display = 'none';
  document.getElementById('modal-group').style.display = 'none';
}

function openMemberModal() {
  document.getElementById('member-form-name').value = '';
  document.getElementById('member-form-email').value = '';
  document.getElementById('modal-member-backdrop').style.display = 'block';
  document.getElementById('modal-member').style.display = 'block';
  document.getElementById('member-form-name').focus();
}

function closeMemberModal() {
  document.getElementById('modal-member-backdrop').style.display = 'none';
  document.getElementById('modal-member').style.display = 'none';
}

function openBulkModal() {
  document.getElementById('member-bulk-textarea').value = '';
  document.getElementById('bulk-preview-section').style.display = 'none';
  document.getElementById('bulk-summary-status').textContent = 'Ready to parse';
  document.getElementById('btn-member-bulk-submit').disabled = true;
  document.getElementById('modal-member-bulk-backdrop').style.display = 'block';
  document.getElementById('modal-member-bulk').style.display = 'block';
  document.getElementById('member-bulk-textarea').focus();
}

function closeBulkModal() {
  document.getElementById('modal-member-bulk-backdrop').style.display = 'none';
  document.getElementById('modal-member-bulk').style.display = 'none';
}

async function openAdminModal() {
  document.getElementById('admin-form-username').value = '';
  document.getElementById('admin-form-password').value = '';
  document.getElementById('admin-form-role').value = 'APP_ADMIN';
  document.getElementById('admin-form-group-select-wrapper').style.display = 'none';

  const select = document.getElementById('admin-form-group-select');
  if (select) {
    if (groupsCache.length === 0) {
      await fetchGroupsList();
    }
    select.innerHTML = '<option value="">-- Choose Circle Group --</option>';
    groupsCache.forEach((g) => {
      select.innerHTML += `<option value="${g.groupId}">${escapeHtml(g.name)}</option>`;
    });
  }

  document.getElementById('modal-admin-backdrop').style.display = 'block';
  document.getElementById('modal-admin').style.display = 'block';
  document.getElementById('admin-form-username').focus();
}

function closeAdminModal() {
  document.getElementById('modal-admin-backdrop').style.display = 'none';
  document.getElementById('modal-admin').style.display = 'none';
}

function openPrayerDetailModal(prayer) {
  const content = document.getElementById('prayer-detail-content');
  if (!content) return;

  const dateStr = new Date(prayer.createdAt).toLocaleString();
  const groupName = getGroupNameCached(prayer.assignedGroupId);

  let updatesHtml = '';
  if (prayer.updates && prayer.updates.length > 0) {
    updatesHtml = `
      <div style="border-top: 1px solid rgba(0,0,0,0.06); padding-top: var(--space-4); margin-top: var(--space-4); text-align: left;">
        <h4 class="font-display mb-2" style="font-size: var(--font-size-sm); color: var(--color-success);">Answered Update</h4>
        <p style="font-style: italic; white-space: pre-wrap; font-size: var(--font-size-sm); line-height: 1.5; color: var(--color-text-secondary);">"${escapeHtml(prayer.updates[0].updateText)}"</p>
        <div class="color-text-muted mt-2" style="font-size: var(--font-size-xs);">Shared on: ${new Date(prayer.updates[0].updatedAt).toLocaleString()}</div>
      </div>
    `;
  }

  content.innerHTML = `
    <span class="status-badge ${prayer.status.toLowerCase()} mb-4">${prayer.status}</span>
    <blockquote class="font-display mb-4" style="font-size: var(--font-size-md); font-style: italic; line-height: 1.6; border: none; padding: 0;">
      "${escapeHtml(prayer.prayerText)}"
    </blockquote>
    <div style="font-size: var(--font-size-xs); color: var(--color-text-muted); display: flex; flex-direction: column; gap: var(--space-1); margin-bottom: var(--space-4);">
      <div><strong>Submitted:</strong> ${dateStr}</div>
      <div><strong>Original Circle:</strong> ${escapeHtml(groupName)}</div>
      <div><strong>Device ID:</strong> <code style="font-size: 11px;">${prayer.deviceId}</code></div>
      <div><strong>Total Prayers Count:</strong> 🙏 ${prayer.prayedForCount || 0}</div>
    </div>
    ${updatesHtml}
  `;

  document.getElementById('modal-prayer-backdrop').style.display = 'block';
  document.getElementById('modal-prayer').style.display = 'block';
}

function closePrayerDetailModal() {
  document.getElementById('modal-prayer-backdrop').style.display = 'none';
  document.getElementById('modal-prayer').style.display = 'none';
}

// === Confirmation Overlay ===
function showConfirmModal(title, message, onConfirm) {
  const confirmModal = document.getElementById('modal-confirm');
  const confirmBackdrop = document.getElementById('modal-confirm-backdrop');
  if (!confirmModal || !confirmBackdrop) return;

  document.getElementById('confirm-title').textContent = title;
  document.getElementById('confirm-message').textContent = message;

  const cancelBtn = document.getElementById('btn-confirm-cancel');
  const actionBtn = document.getElementById('btn-confirm-action');

  const close = () => {
    confirmModal.style.display = 'none';
    confirmBackdrop.style.display = 'none';
  };

  const newActionBtn = actionBtn.cloneNode(true);
  actionBtn.parentNode.replaceChild(newActionBtn, actionBtn);

  newActionBtn.addEventListener('click', () => {
    close();
    onConfirm();
  });

  cancelBtn.onclick = close;
  confirmBackdrop.onclick = close;

  confirmModal.style.display = 'block';
  confirmBackdrop.style.display = 'block';
}

// === CSV Parser Helper ===
function parseCSVRecords(text) {
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const lines = text.split('\n');
  const results = [];

  lines.forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed) return;

    const parts = trimmed.split(',');
    if (parts.length < 2) {
      results.push({
        valid: false,
        name: trimmed,
        email: '',
        reason: 'Missing email address (comma split failed).',
      });
      return;
    }

    const name = parts.slice(0, -1).join(',').trim();
    const email = parts[parts.length - 1].trim();

    if (!name) {
      results.push({ valid: false, name: '', email, reason: 'Name is empty.' });
      return;
    }

    if (!email || !EMAIL_PATTERN.test(email)) {
      results.push({
        valid: false,
        name,
        email,
        reason: 'Invalid email address format.',
      });
      return;
    }

    results.push({ valid: true, name, email, reason: '' });
  });

  return results;
}

function renderBulkPreview(records) {
  const tbody = document.getElementById('bulk-preview-table-body');
  const previewSection = document.getElementById('bulk-preview-section');
  const submitBtn = document.getElementById('btn-member-bulk-submit');
  const summaryStatus = document.getElementById('bulk-summary-status');

  if (!tbody || !previewSection) return;

  tbody.innerHTML = '';
  let validCount = 0;

  records.forEach((r) => {
    const tr = document.createElement('tr');
    if (r.valid) validCount++;

    tr.innerHTML = `
      <td>${r.valid ? '✅ Valid' : '❌ Error'}</td>
      <td>${escapeHtml(r.name)}</td>
      <td>${escapeHtml(r.email)}</td>
      <td style="color: var(--color-error);">${escapeHtml(r.reason)}</td>
    `;
    tbody.appendChild(tr);
  });

  previewSection.style.display = 'block';
  summaryStatus.innerHTML = `Parsed <strong>${records.length}</strong> rows. Found <strong>${validCount}</strong> valid entries to import.`;
  submitBtn.disabled = validCount === 0;
}

// === Toast Handler ===
function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;

  let icon = 'ℹ️';
  if (type === 'success') icon = '🙏';
  if (type === 'error') icon = '⚠️';
  toast.textContent = `${icon} ${message}`;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.animation = 'toast-slide-out var(--transition-base) forwards';
    toast.addEventListener('animationend', () => {
      toast.remove();
    });
  }, 4000);
}

// === Utility helpers ===
function escapeHtml(str) {
  if (!str) return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function truncateString(str, num) {
  if (!str) return '';
  if (str.length <= num) return str;
  return str.slice(0, num) + '...';
}

function getGroupNameCached(groupId) {
  if (!groupId) return 'Community Wall (General)';
  const group = groupsCache.find((g) => g.groupId === groupId);
  return group ? group.name : 'Private Circle';
}

function toggleSort(field) {
  if (sortField === field) {
    sortAscending = !sortAscending;
  } else {
    sortField = field;
    sortAscending = false;
  }

  updateSortHeaders();
  sortAndRenderPrayers();
}

function updateSortHeaders() {
  const fields = {
    prayerText: 'th-prayer-text',
    assignedGroupId: 'th-prayer-group',
    status: 'th-prayer-status',
    prayedForCount: 'th-prayer-count',
    createdAt: 'th-prayer-date',
  };

  Object.entries(fields).forEach(([f, id]) => {
    const th = document.getElementById(id);
    if (!th) return;

    let title = th.textContent.replace(/ [▲▼]/g, '');
    if (f === sortField) {
      title += sortAscending ? ' ▲' : ' ▼';
    }
    th.textContent = title;
  });
}

function sortAndRenderPrayers() {
  prayersCache.sort((a, b) => {
    let valA = a[sortField];
    let valB = b[sortField];

    if (sortField === 'assignedGroupId') {
      valA = getGroupNameCached(valA);
      valB = getGroupNameCached(valB);
    }

    if (valA === undefined || valA === null) valA = '';
    if (valB === undefined || valB === null) valB = '';

    if (typeof valA === 'string') {
      return sortAscending ? valA.localeCompare(valB) : valB.localeCompare(valA);
    } else {
      return sortAscending
        ? valA < valB
          ? -1
          : valA > valB
            ? 1
            : 0
        : valB < valA
          ? -1
          : valB > valA
            ? 1
            : 0;
    }
  });

  renderPrayersTable();
}

// Emojis burst animation helper (Premium aesthetic micro-interactions)
function triggerEmojiBurst(event, anchorElement) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    return;
  }
  const burstCount = 6;
  const emojis = ['🙏', '✨', '🤍', '🕊️', '🌸'];

  for (let i = 0; i < burstCount; i++) {
    const floating = document.createElement('span');
    floating.className = 'floating-emoji';
    floating.textContent = emojis[Math.floor(Math.random() * emojis.length)];

    const x = event.clientX || anchorElement.getBoundingClientRect().left + 30;
    const y = event.clientY || anchorElement.getBoundingClientRect().top - 15;

    floating.style.position = 'fixed';
    floating.style.left = `${x - 15}px`;
    floating.style.top = `${y - 15}px`;
    floating.style.zIndex = '9999';

    const driftX = Math.random() * 50 - 25 + 'px';
    const driftXEnd = Math.random() * 80 - 40 + 'px';
    const rotate = Math.random() * 40 - 20 + 'deg';
    const rotateEnd = Math.random() * 80 - 40 + 'deg';

    floating.style.setProperty('--drift-x', driftX);
    floating.style.setProperty('--drift-x-end', driftXEnd);
    floating.style.setProperty('--rotate', rotate);
    floating.style.setProperty('--rotate-end', rotateEnd);

    document.body.appendChild(floating);
    floating.addEventListener('animationend', () => {
      floating.remove();
    });
  }
}
