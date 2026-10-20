/* FishMatrix 用户中台（无构建依赖，直接由 tf-user 提供） */
const TOKEN_KEY = 'fm.admin.token.v1';
const PAGE_SIZE = 15;
const REFRESH_MS = 30_000;

const STATUS_LABELS = {
  PENDING: { text: '等待通过', className: 'badge--pending' },
  ACTIVE: { text: '已通过', className: 'badge--active' },
  REJECTED: { text: '已拒绝', className: 'badge--rejected' },
  DISABLED: { text: '已停用', className: 'badge--disabled' },
};

const state = {
  token: readToken(),
  user: null,
  status: '',
  query: '',
  page: 1,
  size: PAGE_SIZE,
  total: 0,
  loading: false,
};

const el = {
  console: document.getElementById('console'),
  loginLayer: document.getElementById('loginLayer'),
  loginForm: document.getElementById('loginForm'),
  loginUsername: document.getElementById('loginUsername'),
  loginPassword: document.getElementById('loginPassword'),
  loginError: document.getElementById('loginError'),
  loginBtn: document.getElementById('loginBtn'),
  adminName: document.getElementById('adminName'),
  logoutBtn: document.getElementById('logoutBtn'),
  stats: document.querySelectorAll('[data-stat]'),
  filters: document.getElementById('filters'),
  searchInput: document.getElementById('searchInput'),
  refreshBtn: document.getElementById('refreshBtn'),
  userRows: document.getElementById('userRows'),
  emptyState: document.getElementById('emptyState'),
  pageInfo: document.getElementById('pageInfo'),
  prevBtn: document.getElementById('prevBtn'),
  nextBtn: document.getElementById('nextBtn'),
  refreshHint: document.getElementById('refreshHint'),
  toast: document.getElementById('toast'),
};

let toastTimer;
let searchTimer;
let refreshTimer;

/* ── 工具 ── */

function readToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY) || '';
  } catch {
    return '';
  }
}

function writeToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
}

async function http(path, options = {}) {
  const { method = 'GET', body, token = state.token, autoSignOut = true } = options;
  const headers = new Headers();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (body !== undefined) headers.set('Content-Type', 'application/json');

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new Error('网络似乎断开了，稍后再试');
  }

  if (response.status === 401 && autoSignOut) {
    signOut(false);
    throw new Error('登录已失效，请重新进入中台');
  }

  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      const payload = await response.json();
      if (payload && payload.message) message = payload.message;
    } catch {
      // ignore
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response.json();
}

function formatTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 16).replace('T', ' ');
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

function toast(message, isError = false) {
  clearTimeout(toastTimer);
  el.toast.textContent = message;
  el.toast.classList.toggle('toast--error', isError);
  el.toast.hidden = false;
  toastTimer = setTimeout(() => {
    el.toast.hidden = true;
  }, 2800);
}

function showLogin(message = '') {
  stopAutoRefresh();
  el.console.hidden = true;
  el.loginLayer.hidden = false;
  el.loginError.textContent = message;
  el.loginPassword.value = '';
  el.loginUsername.focus();
}

function showConsole() {
  el.loginLayer.hidden = true;
  el.console.hidden = false;
}

function signOut(notify = true) {
  if (state.token) {
    http('/api/v1/user/auth/logout', { method: 'POST', autoSignOut: false }).catch(() => {});
  }
  state.token = '';
  state.user = null;
  writeToken('');
  showLogin();
  if (notify) toast('已退出用户中台');
}

/* ── 登录 ── */

async function login(event) {
  event.preventDefault();
  const username = el.loginUsername.value.trim();
  const password = el.loginPassword.value;
  if (!username || !password) {
    el.loginError.textContent = '名号和口号都要填';
    return;
  }

  el.loginBtn.disabled = true;
  el.loginBtn.textContent = '验证中…';
  el.loginError.textContent = '';

  try {
    const result = await http('/api/v1/user/auth/login', {
      method: 'POST',
      body: { username, password },
      token: '',
      autoSignOut: false,
    });

    if (!result.user || result.user.role !== 'ADMIN') {
      http('/api/v1/user/auth/logout', {
        method: 'POST',
        token: result.token,
        autoSignOut: false,
      }).catch(() => {});
      throw new Error('这个账号不是管理员');
    }

    state.token = result.token;
    state.user = result.user;
    writeToken(result.token);
    el.adminName.textContent = `管理员 · ${result.user.username}`;
    el.loginPassword.value = '';
    showConsole();
    await loadUsers();
    startAutoRefresh();
  } catch (error) {
    el.loginError.textContent = error.message;
    el.loginForm.classList.remove('shake');
    void el.loginForm.offsetWidth;
    el.loginForm.classList.add('shake');
  } finally {
    el.loginBtn.disabled = false;
    el.loginBtn.textContent = '进入中台';
  }
}

/* ── 数据 ── */

async function loadUsers({ silent = false } = {}) {
  if (state.loading) return;
  state.loading = true;
  el.refreshBtn.disabled = true;

  const params = new URLSearchParams({
    page: String(state.page),
    size: String(state.size),
  });
  if (state.status) params.set('status', state.status);
  if (state.query) params.set('q', state.query);

  try {
    const data = await http(`/api/v1/user/admin/users?${params.toString()}`);
    state.total = data.total;
    renderStats(data.stats);
    renderRows(data.items || []);

    const totalPages = Math.max(1, Math.ceil(data.total / state.size));
    if (state.page > totalPages) {
      state.page = totalPages;
      state.loading = false;
      el.refreshBtn.disabled = false;
      return loadUsers({ silent });
    }

    el.pageInfo.textContent = `第 ${state.page} / ${totalPages} 页 · 共 ${data.total} 人`;
    el.prevBtn.disabled = state.page <= 1;
    el.nextBtn.disabled = state.page >= totalPages;
    el.emptyState.hidden = (data.items || []).length > 0;
    el.refreshHint.textContent = `最后刷新 ${formatTime(new Date().toISOString())}`;
  } catch (error) {
    if (!silent) toast(error.message, true);
  } finally {
    state.loading = false;
    el.refreshBtn.disabled = false;
  }
}

function renderStats(stats) {
  el.stats.forEach((node) => {
    node.textContent = stats ? String(stats[node.dataset.stat] ?? 0) : '–';
  });
}

function renderRows(items) {
  el.userRows.textContent = '';
  const fragment = document.createDocumentFragment();

  items.forEach((user) => {
    const row = document.createElement('tr');

    const nameCell = document.createElement('td');
    const nameWrap = document.createElement('div');
    nameWrap.className = 'user-name';
    const avatar = document.createElement('span');
    avatar.className = 'user-avatar';
    avatar.textContent = Array.from(user.username)[0] || '?';
    const nameText = document.createElement('span');
    nameText.textContent = user.username;
    const idText = document.createElement('span');
    idText.className = 'user-id';
    idText.textContent = `#${user.id}`;
    nameText.appendChild(idText);
    nameWrap.append(avatar, nameText);
    nameCell.appendChild(nameWrap);

    const statusCell = document.createElement('td');
    const statusMeta = STATUS_LABELS[user.status] || {
      text: user.status,
      className: 'badge--disabled',
    };
    const badge = document.createElement('span');
    badge.className = `badge ${statusMeta.className}`;
    badge.textContent = statusMeta.text;
    statusCell.appendChild(badge);

    const roleCell = document.createElement('td');
    const roleTag = document.createElement('span');
    roleTag.className = `role-tag${user.role === 'ADMIN' ? ' role-tag--admin' : ''}`;
    roleTag.textContent = user.role;
    roleCell.appendChild(roleTag);

    const createdCell = document.createElement('td');
    createdCell.className = 'time-cell';
    createdCell.textContent = formatTime(user.createdAt);

    const reviewedCell = document.createElement('td');
    reviewedCell.className = 'time-cell';
    reviewedCell.textContent = user.approvedBy
      ? `${user.approvedBy} · ${formatTime(user.approvedAt)}`
      : '—';

    const loginCell = document.createElement('td');
    loginCell.className = 'time-cell';
    loginCell.textContent = formatTime(user.lastLoginAt);

    const actionCell = document.createElement('td');
    actionCell.className = 'col-actions';
    const actions = document.createElement('div');
    actions.className = 'cell-actions';

    if (user.status === 'PENDING') {
      actions.append(
        actionButton('通过', 'approve', user),
        actionButton('拒绝', 'reject', user, 'danger'),
      );
    } else if (user.status === 'ACTIVE') {
      if (user.id !== state.user?.id) {
        actions.append(actionButton('停用', 'disable', user, 'danger'));
      } else {
        const self = document.createElement('span');
        self.className = 'user-id';
        self.textContent = '当前账号';
        actions.appendChild(self);
      }
    } else if (user.status === 'REJECTED') {
      actions.append(actionButton('通过', 'approve', user));
    } else if (user.status === 'DISABLED') {
      actions.append(actionButton('启用', 'enable', user));
    }

    actionCell.appendChild(actions);
    row.append(nameCell, statusCell, roleCell, createdCell, reviewedCell, loginCell, actionCell);
    fragment.appendChild(row);
  });

  el.userRows.appendChild(fragment);
}

function actionButton(label, action, user, tone = '') {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = `row-btn${tone ? ` row-btn--${tone}` : ''}`;
  button.textContent = label;
  button.addEventListener('click', () => changeStatus(user, action, label));
  return button;
}

async function changeStatus(user, action, label) {
  if (!window.confirm(`确认${label}「${user.username}」？`)) return;
  try {
    await http(`/api/v1/user/admin/users/${user.id}/${action}`, { method: 'POST' });
    toast(`已${label}「${user.username}」`);
    await loadUsers();
  } catch (error) {
    toast(error.message, true);
  }
}

/* ── 筛选 ── */

function selectStatus(status) {
  state.status = status;
  state.page = 1;
  el.filters.querySelectorAll('button').forEach((button) => {
    button.classList.toggle('is-active', button.dataset.status === status);
  });
  el.stats.forEach((node) => {
    node.classList.toggle('is-active', node.dataset.status === status);
  });
  loadUsers();
}

function scheduleSearch() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    state.query = el.searchInput.value.trim();
    state.page = 1;
    loadUsers();
  }, 300);
}

/* ── 自动刷新 / 启动 ── */

function startAutoRefresh() {
  stopAutoRefresh();
  refreshTimer = setInterval(() => {
    if (!document.hidden) loadUsers({ silent: true });
  }, REFRESH_MS);
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = undefined;
  }
}

async function bootstrap() {
  if (!state.token) {
    showLogin();
    return;
  }

  try {
    const me = await http('/api/v1/user/auth/me', { autoSignOut: false });
    if (me.role !== 'ADMIN') throw new Error('这个账号不是管理员');
    state.user = me;
    el.adminName.textContent = `管理员 · ${me.username}`;
    showConsole();
    await loadUsers();
    startAutoRefresh();
  } catch {
    state.token = '';
    writeToken('');
    showLogin();
  }
}

el.loginForm.addEventListener('submit', login);
el.logoutBtn.addEventListener('click', () => {
  if (window.confirm('确认退出用户中台？')) signOut();
});
el.refreshBtn.addEventListener('click', () => loadUsers());
el.filters.addEventListener('click', (event) => {
  const button = event.target.closest('button[data-status]');
  if (button) selectStatus(button.dataset.status || '');
});
el.stats.forEach((node) => {
  node.addEventListener('click', () => selectStatus(node.dataset.status || ''));
});
el.searchInput.addEventListener('input', scheduleSearch);
el.searchInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    clearTimeout(searchTimer);
    state.query = el.searchInput.value.trim();
    state.page = 1;
    loadUsers();
  }
});
el.prevBtn.addEventListener('click', () => {
  if (state.page > 1) {
    state.page -= 1;
    loadUsers();
  }
});
el.nextBtn.addEventListener('click', () => {
  const totalPages = Math.max(1, Math.ceil(state.total / state.size));
  if (state.page < totalPages) {
    state.page += 1;
    loadUsers();
  }
});
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.token) loadUsers({ silent: true });
});

bootstrap();
