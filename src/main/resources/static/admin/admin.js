/* FishMatrix 用户中台（无构建依赖，直接由 tf-user 提供） */
const TOKEN_KEY = 'fm.admin.token.v1';
const PAGE_SIZE = 15;
const REFRESH_MS = 10_000;
const REQUEST_TIMEOUT_MS = 20_000;

const STATUS_LABELS = {
  PENDING: { text: '等待通过', className: 'badge--pending' },
  ACTIVE: { text: '已通过', className: 'badge--active' },
  REJECTED: { text: '已拒绝', className: 'badge--rejected' },
  DISABLED: { text: '已停用', className: 'badge--disabled' },
};

const ACTIONS = {
  approve: {
    tone: 'approve',
    icon: '✓',
    eyebrow: '审批确认',
    busyLabel: '正在通过…',
    title: (user) =>
      user.status === 'REJECTED' ? `重新通过「${user.username}」？` : `通过「${user.username}」的申请？`,
    description: (user) =>
      user.status === 'REJECTED'
        ? '重新放行后，对方可以再次进入 FishMatrix。'
        : '请确认是你认识的人，再放行这份申请。',
    impactTitle: '确认通过后',
    impactText: '该用户可以立即登录，并进入工作台与聊天。',
    confirmLabel: '确认通过',
    doneTitle: (user) => `已通过「${user.username}」`,
    doneDescription: '审批结果已经记录，列表已同步。',
    toastDetail: '用户现在可以登录 FishMatrix。',
  },
  reject: {
    tone: 'danger',
    icon: '×',
    eyebrow: '审批确认',
    busyLabel: '正在拒绝…',
    title: (user) => `拒绝「${user.username}」的申请？`,
    description: '拒绝会把这份申请标记为未通过。',
    impactTitle: '拒绝后',
    impactText: '该用户无法登录；之后仍可在「已拒绝」中重新通过。',
    confirmLabel: '确认拒绝',
    doneTitle: (user) => `已拒绝「${user.username}」`,
    doneDescription: '审批结果已经记录，列表已同步。',
    toastDetail: '对方将无法登录，可在「已拒绝」中重新通过。',
  },
  disable: {
    tone: 'danger',
    icon: '!',
    eyebrow: '账号管理',
    busyLabel: '正在停用…',
    title: (user) => `停用「${user.username}」？`,
    description: '停用会立即收回这个账号的访问权限。',
    impactTitle: '停用后',
    impactText: '该用户将无法继续使用 FishMatrix，已登录的会话会失效。',
    confirmLabel: '确认停用',
    doneTitle: (user) => `已停用「${user.username}」`,
    doneDescription: '账号状态已经更新，列表已同步。',
    toastDetail: '该用户已无法继续访问。',
  },
  enable: {
    tone: 'info',
    icon: '↻',
    eyebrow: '账号管理',
    busyLabel: '正在启用…',
    title: (user) => `重新启用「${user.username}」？`,
    description: '启用后，这个账号会恢复登录权限。',
    impactTitle: '启用后',
    impactText: '该用户可以再次登录 FishMatrix。',
    confirmLabel: '确认启用',
    doneTitle: (user) => `已启用「${user.username}」`,
    doneDescription: '账号状态已经更新，列表已同步。',
    toastDetail: '该用户可以重新登录了。',
  },
};

class ApiError extends Error {
  constructor(message, { status = 0, code = '' } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

const state = {
  token: readToken(),
  user: null,
  status: '',
  query: '',
  page: 1,
  size: PAGE_SIZE,
  total: 0,
  stats: null,
  items: [],
  freshIds: new Set(),
  lastListKey: '',
  meta: null,
  recentlyUpdatedId: null,
  loadSeq: 0,
  tab: 'users',
  permissions: {
    loaded: false,
    loading: false,
    loadSeq: 0,
    catalog: null,
    role: 'USER',
    drafts: new Map(),
    saving: false,
    error: '',
  },
  review: {
    open: false,
    busy: false,
    phase: 'confirm',
    canRetry: true,
    user: null,
    action: null,
    trigger: null,
    closeTimer: undefined,
    toastAfterClose: null,
  },
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
  envBadge: document.getElementById('envBadge'),
  envLabel: document.getElementById('envLabel'),
  envDetail: document.getElementById('envDetail'),
  loginEnv: document.getElementById('loginEnv'),
  consoleTabs: document.getElementById('consoleTabs'),
  usersView: document.getElementById('usersView'),
  permissionsView: document.getElementById('permissionsView'),
  permRefreshBtn: document.getElementById('permRefreshBtn'),
  roleList: document.getElementById('roleList'),
  permRoleTitle: document.getElementById('permRoleTitle'),
  permRoleHint: document.getElementById('permRoleHint'),
  permCount: document.getElementById('permCount'),
  permissionGroups: document.getElementById('permissionGroups'),
  permEmpty: document.getElementById('permEmpty'),
  permStatus: document.getElementById('permStatus'),
  permResetBtn: document.getElementById('permResetBtn'),
  permSaveBtn: document.getElementById('permSaveBtn'),
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
  reviewDialog: document.getElementById('reviewDialog'),
  reviewClose: document.getElementById('reviewClose'),
  reviewSignal: document.getElementById('reviewSignal'),
  reviewEyebrow: document.getElementById('reviewEyebrow'),
  reviewTitle: document.getElementById('reviewTitle'),
  reviewDesc: document.getElementById('reviewDesc'),
  reviewUser: document.getElementById('reviewUser'),
  reviewAvatar: document.getElementById('reviewAvatar'),
  reviewName: document.getElementById('reviewName'),
  reviewMeta: document.getElementById('reviewMeta'),
  reviewBadge: document.getElementById('reviewBadge'),
  reviewImpact: document.getElementById('reviewImpact'),
  reviewImpactIcon: document.getElementById('reviewImpactIcon'),
  reviewImpactTitle: document.getElementById('reviewImpactTitle'),
  reviewImpactText: document.getElementById('reviewImpactText'),
  reviewError: document.getElementById('reviewError'),
  reviewActions: document.getElementById('reviewActions'),
  reviewCancel: document.getElementById('reviewCancel'),
  reviewConfirm: document.getElementById('reviewConfirm'),
  toast: document.getElementById('toast'),
  toastIcon: document.getElementById('toastIcon'),
  toastTitle: document.getElementById('toastTitle'),
  toastDetail: document.getElementById('toastDetail'),
  toastClose: document.getElementById('toastClose'),
};

let toastTimer;
let searchTimer;
let refreshTimer;
let highlightTimer;

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

/* ── 运行环境 ── */

function renderMeta(meta) {
  if (!meta || !meta.environment) return;
  state.meta = meta;
  const label = meta.environmentLabel || meta.environment;
  const detail = [meta.database, meta.namespace, meta.version].filter(Boolean).join(' · ');

  el.envBadge.hidden = false;
  el.envBadge.className = `env-badge env-badge--${meta.environment}`;
  el.envLabel.textContent = label;
  el.envBadge.title = detail ? `${label} · ${detail}` : label;

  el.envDetail.hidden = !detail;
  el.envDetail.textContent = detail;

  el.loginEnv.hidden = false;
  el.loginEnv.className = `login-card__env login-card__env--${meta.environment}`;
  el.loginEnv.textContent = label;
  el.loginEnv.title = detail;

  document.title = `用户中台 · ${label} · FishMatrix`;
}

async function loadMeta() {
  try {
    renderMeta(await http('/api/v1/user/meta', { autoSignOut: false }));
  } catch {
    // 环境信息只用于提示，失败不影响审批
  }
}

async function http(path, options = {}) {
  const { method = 'GET', body, token = state.token, autoSignOut = true } = options;
  const headers = new Headers();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (body !== undefined) headers.set('Content-Type', 'application/json');

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new ApiError('请求超过 20 秒没有响应，操作可能尚未生效。', { code: 'TIMEOUT' });
    }
    throw new ApiError('网络连接中断，请检查网络后重试。', { code: 'NETWORK' });
  } finally {
    clearTimeout(timeout);
  }

  if (response.status === 401 && autoSignOut) {
    signOut(false);
    throw new ApiError('登录已失效，请重新进入中台。', {
      status: 401,
      code: 'UNAUTHORIZED',
    });
  }

  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`;
    let code = '';
    try {
      const payload = await response.json();
      if (payload && payload.message) message = payload.message;
      if (payload && payload.error) code = payload.error;
    } catch {
      // ignore
    }
    throw new ApiError(message, { status: response.status, code });
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

function formatClock(date = new Date()) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function toast({ title, detail = '', tone = 'success', duration }) {
  const visibleFor = duration ?? (tone === 'error' ? 6000 : 3800);
  clearTimeout(toastTimer);
  el.toast.hidden = true;
  el.toast.className = `toast${tone === 'error' ? ' toast--error' : ''}${
    tone === 'info' ? ' toast--info' : ''
  }`;
  el.toast.setAttribute('role', tone === 'error' ? 'alert' : 'status');
  el.toastIcon.textContent = tone === 'error' ? '!' : tone === 'info' ? 'i' : '✓';
  el.toastTitle.textContent = title;
  el.toastDetail.textContent = detail;
  el.toastDetail.hidden = !detail;
  el.toast.style.setProperty('--toast-duration', `${visibleFor}ms`);
  el.toast.hidden = false;
  void el.toast.offsetWidth;
  toastTimer = setTimeout(hideToast, visibleFor);
}

function hideToast() {
  clearTimeout(toastTimer);
  el.toast.hidden = true;
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
  closeReview({ force: true });
  if (state.token) {
    http('/api/v1/user/auth/logout', { method: 'POST', autoSignOut: false }).catch(() => {});
  }
  state.token = '';
  state.user = null;
  state.items = [];
  state.permissions.loadSeq += 1;
  state.permissions.loading = false;
  state.permissions.loaded = false;
  state.permissions.catalog = null;
  state.permissions.drafts = new Map();
  state.permissions.saving = false;
  writeToken('');
  switchTab('users');
  showLogin();
  if (notify) {
    toast({
      title: '已退出用户中台',
      detail: '下次进入需要重新验证管理员身份。',
      tone: 'info',
    });
  }
}

/* ── 审核确认面板 ── */

function read(value, user) {
  return typeof value === 'function' ? value(user) : value;
}

function setConfirmContent(label, busy = false) {
  el.reviewConfirm.textContent = '';
  if (busy) {
    const spinner = document.createElement('span');
    spinner.className = 'btn-spinner';
    spinner.setAttribute('aria-hidden', 'true');
    el.reviewConfirm.appendChild(spinner);
  }
  el.reviewConfirm.appendChild(document.createTextNode(label));
}

function openReview(user, action, trigger) {
  const config = ACTIONS[action];
  if (!config) return;

  clearTimeout(state.review.closeTimer);
  state.review = {
    open: true,
    busy: false,
    phase: 'confirm',
    canRetry: true,
    user,
    action,
    trigger: trigger || document.activeElement,
    closeTimer: undefined,
    toastAfterClose: null,
  };

  const statusMeta = STATUS_LABELS[user.status] || {
    text: user.status,
    className: 'badge--disabled',
  };

  el.reviewDialog.dataset.tone = config.tone;
  el.reviewDialog.dataset.phase = 'confirm';
  el.reviewDialog.dataset.busy = 'false';
  el.reviewDialog.setAttribute('aria-busy', 'false');
  el.reviewSignal.textContent = config.icon;
  el.reviewEyebrow.textContent = read(config.eyebrow, user);
  el.reviewTitle.textContent = read(config.title, user);
  el.reviewDesc.textContent = read(config.description, user);
  el.reviewAvatar.textContent = Array.from(user.username)[0] || '?';
  el.reviewName.textContent = user.username;
  el.reviewMeta.textContent = `#${user.id} · ${formatTime(user.createdAt)} 登记`;
  el.reviewBadge.className = `badge ${statusMeta.className}`;
  el.reviewBadge.textContent = statusMeta.text;
  el.reviewImpactIcon.textContent = config.tone === 'danger' ? '!' : '→';
  el.reviewImpactTitle.textContent = config.impactTitle;
  el.reviewImpactText.textContent = config.impactText;
  el.reviewUser.hidden = false;
  el.reviewImpact.hidden = false;
  el.reviewError.hidden = true;
  el.reviewError.textContent = '';
  el.reviewActions.hidden = false;
  el.reviewCancel.hidden = false;
  el.reviewCancel.disabled = false;
  el.reviewCancel.textContent = '再想想';
  el.reviewConfirm.hidden = false;
  el.reviewConfirm.disabled = false;
  el.reviewConfirm.dataset.tone = config.tone;
  setConfirmContent(config.confirmLabel);

  if (!el.reviewDialog.open) el.reviewDialog.showModal();

  requestAnimationFrame(() => {
    const target = config.tone === 'danger' ? el.reviewCancel : el.reviewConfirm;
    target.focus({ preventScroll: true });
  });
}

function closeReview({ force = false } = {}) {
  if (!el.reviewDialog.open) return;
  if (state.review.busy && !force) return;
  clearTimeout(state.review.closeTimer);
  el.reviewDialog.close();
}

function setReviewBusy(busy) {
  state.review.busy = busy;
  el.reviewDialog.dataset.busy = String(busy);
  el.reviewDialog.setAttribute('aria-busy', String(busy));
  el.reviewClose.disabled = busy;
  el.reviewCancel.disabled = busy;
  el.reviewConfirm.disabled = busy;

  if (busy) {
    const config = ACTIONS[state.review.action];
    setConfirmContent(config.busyLabel, true);
  } else if (state.review.phase === 'confirm') {
    const config = ACTIONS[state.review.action];
    setConfirmContent(config.confirmLabel);
  }
}

async function submitReview() {
  const review = state.review;
  if (!review.open || review.busy) return;

  if (review.phase === 'success') {
    closeReview();
    return;
  }
  if (review.phase === 'error' && !review.canRetry) {
    closeReview();
    return;
  }

  const config = ACTIONS[review.action];
  setReviewBusy(true);

  try {
    const updated = await http(
      `/api/v1/user/admin/users/${review.user.id}/${review.action}`,
      { method: 'POST' },
    );
    if (!updated) {
      throw new ApiError('服务没有返回更新后的用户信息，请刷新后确认。', {
        status: 500,
        code: 'EMPTY_RESPONSE',
      });
    }

    setReviewBusy(false);
    showReviewSuccess(updated, config);
    applyUserUpdate(updated);
    void loadUsers({ silent: true });
  } catch (error) {
    setReviewBusy(false);
    if (error?.status === 401) return;
    showReviewError(error);
  }
}

function showReviewSuccess(updated, config) {
  state.review.phase = 'success';
  state.review.canRetry = false;
  state.review.user = updated;
  state.review.toastAfterClose = {
    title: read(config.doneTitle, updated),
    detail: config.toastDetail,
    tone: 'success',
  };

  const statusMeta = STATUS_LABELS[updated.status] || {
    text: updated.status,
    className: 'badge--disabled',
  };

  el.reviewDialog.dataset.tone = 'approve';
  el.reviewDialog.dataset.phase = 'success';
  el.reviewSignal.textContent = '✓';
  el.reviewEyebrow.textContent = '操作完成';
  el.reviewTitle.textContent = read(config.doneTitle, updated);
  el.reviewDesc.textContent = config.doneDescription;
  el.reviewBadge.className = `badge ${statusMeta.className}`;
  el.reviewBadge.textContent = statusMeta.text;
  el.reviewImpact.hidden = true;
  el.reviewError.hidden = true;
  el.reviewActions.hidden = false;
  el.reviewCancel.hidden = true;
  el.reviewConfirm.hidden = false;
  el.reviewConfirm.disabled = false;
  el.reviewConfirm.dataset.tone = 'approve';
  setConfirmContent('完成');

  clearTimeout(state.review.closeTimer);
  state.review.closeTimer = setTimeout(() => {
    if (state.review.open && state.review.phase === 'success') closeReview();
  }, 1600);

  el.reviewConfirm.focus({ preventScroll: true });
}

function showReviewError(error) {
  const info = explainError(error);
  const config = ACTIONS[state.review.action];

  state.review.phase = 'error';
  state.review.canRetry = info.retry;
  el.reviewDialog.dataset.phase = 'error';
  el.reviewSignal.textContent = '!';
  el.reviewEyebrow.textContent = '操作未完成';
  el.reviewTitle.textContent = info.title;
  el.reviewDesc.textContent = info.description;
  el.reviewImpact.hidden = true;
  el.reviewError.hidden = false;
  el.reviewError.textContent = info.message;
  el.reviewActions.hidden = false;
  el.reviewCancel.hidden = false;
  el.reviewCancel.disabled = false;
  el.reviewCancel.textContent = '关闭';
  el.reviewConfirm.hidden = !info.retry;
  el.reviewConfirm.disabled = false;
  el.reviewConfirm.dataset.tone = config.tone;
  setConfirmContent('重试');

  if (info.refresh) void loadUsers({ silent: true });

  requestAnimationFrame(() => {
    (info.retry ? el.reviewConfirm : el.reviewCancel).focus({ preventScroll: true });
  });
}

function explainError(error) {
  if (error?.code === 'INVALID_STATE') {
    return {
      title: '这个用户的状态已经变化',
      description: '这次操作没有生效。',
      message: '可能已在另一个页面处理过，列表已经为你刷新。关闭后请确认最新状态。',
      retry: false,
      refresh: true,
    };
  }
  if (error?.code === 'USER_NOT_FOUND') {
    return {
      title: '没有找到这个用户',
      description: '这份申请可能已经被删除。',
      message: '列表已经为你刷新，关闭后请确认最新状态。',
      retry: false,
      refresh: true,
    };
  }
  if (error?.code === 'SELF_ACTION') {
    return {
      title: '不能对自己执行这个操作',
      description: '请换一个管理员账号处理。',
      message: error.message,
      retry: false,
      refresh: false,
    };
  }
  if (error?.code === 'FORBIDDEN') {
    return {
      title: '没有权限执行这个操作',
      description: '当前账号的管理员权限可能已经变化。',
      message: error.message,
      retry: false,
      refresh: false,
    };
  }
  if (error?.code === 'TIMEOUT') {
    return {
      title: '操作可能没有送达',
      description: '请求等待太久，系统没有及时回应。',
      message: '可以重试；如果仍然失败，先关闭窗口刷新列表，确认用户状态后再操作。',
      retry: true,
      refresh: false,
    };
  }
  if (error?.code === 'NETWORK') {
    return {
      title: '网络连接中断了',
      description: '这次操作可能尚未生效。',
      message: '检查网络后可以重试，列表会在操作成功后自动同步。',
      retry: true,
      refresh: false,
    };
  }
  if (error?.status === 503) {
    return {
      title: '用户服务暂时不可用',
      description: '这次操作没有被执行。',
      message: '稍后重试；如果持续出现，请联系维护人员检查用户服务。',
      retry: true,
      refresh: false,
    };
  }
  return {
    title: '这次操作没有完成',
    description: '系统没有返回成功结果。',
    message: error?.message || '请稍后重试。',
    retry: true,
    refresh: false,
  };
}

function applyUserUpdate(updated) {
  const oldStatus = state.status;
  state.items = state.items.map((user) => (user.id === updated.id ? updated : user));

  if (oldStatus && updated.status !== oldStatus) {
    state.items = state.items.filter((user) => user.id !== updated.id);
    state.total = Math.max(0, state.total - 1);
  }

  state.recentlyUpdatedId = updated.id;
  renderRows(state.items);
  el.emptyState.hidden = state.items.length > 0;

  clearTimeout(highlightTimer);
  highlightTimer = setTimeout(() => {
    if (state.recentlyUpdatedId !== updated.id) return;
    state.recentlyUpdatedId = null;
    renderRows(state.items);
  }, 2200);
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
    switchTab('users');
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
  const seq = ++state.loadSeq;
  if (!silent) {
    el.refreshBtn.disabled = true;
    el.refreshHint.classList.remove('is-error');
    el.refreshHint.textContent = '正在同步审批队列…';
  }

  const params = new URLSearchParams({
    page: String(state.page),
    size: String(state.size),
  });
  if (state.status) params.set('status', state.status);
  if (state.query) params.set('q', state.query);

  const listKey = `${state.status}|${state.query}|${state.page}|${state.size}`;
  const sameList = state.lastListKey === listKey;
  const previousIds = new Set(state.items.map((user) => user.id));
  const previousPending = state.stats ? Number(state.stats.pending) || 0 : null;

  try {
    const data = await http(`/api/v1/user/admin/users?${params.toString()}`);
    if (seq !== state.loadSeq) return;

    state.total = Number(data.total) || 0;
    state.stats = data.stats || null;
    state.items = Array.isArray(data.items) ? data.items : [];
    state.lastListKey = listKey;

    const freshPending = sameList
      ? state.items.filter((user) => user.status === 'PENDING' && !previousIds.has(user.id))
      : [];
    state.freshIds = new Set(freshPending.map((user) => user.id));

    renderStats(state.stats);
    renderRows(state.items);
    state.freshIds = new Set();

    const pendingDelta = previousPending === null
      ? 0
      : Number(state.stats?.pending ?? 0) - previousPending;
    if (sameList && pendingDelta > 0) {
      toast({
        title: `收到 ${pendingDelta} 份新申请`,
        detail: freshPending.length > 0 ? '审批队列已自动更新。' : '可在「待审批」中查看。',
        tone: 'info',
      });
    }

    const totalPages = Math.max(1, Math.ceil(state.total / state.size));
    if (state.page > totalPages) {
      state.page = totalPages;
      return loadUsers({ silent });
    }

    el.pageInfo.textContent = `第 ${state.page} / ${totalPages} 页 · 共 ${state.total} 人`;
    el.prevBtn.disabled = state.page <= 1;
    el.nextBtn.disabled = state.page >= totalPages;
    el.emptyState.hidden = state.items.length > 0;
    el.refreshHint.classList.remove('is-error');
    el.refreshHint.textContent = `刚刚同步 · ${formatClock()}`;
  } catch (error) {
    if (seq !== state.loadSeq) return;
    if (error?.status === 401) return;
    if (silent) {
      el.refreshHint.classList.add('is-error');
      el.refreshHint.textContent = '自动同步失败 · 请手动刷新';
    } else {
      toast({
        title: '列表没有更新',
        detail: error.message,
        tone: 'error',
      });
    }
  } finally {
    if (seq === state.loadSeq) {
      el.refreshBtn.disabled = false;
    }
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
    if (user.id === state.recentlyUpdatedId) row.classList.add('row--updated');
    if (state.freshIds.has(user.id)) row.classList.add('row--fresh');

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
        actionButton('通过', 'approve', user, 'approve'),
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
      actions.append(actionButton('通过', 'approve', user, 'approve'));
    } else if (user.status === 'DISABLED') {
      actions.append(actionButton('启用', 'enable', user, 'approve'));
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
  button.setAttribute('aria-label', `${label}「${user.username}」`);
  button.addEventListener('click', () => openReview(user, action, button));
  return button;
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

/* ── 角色权限 ── */

const MODULE_LABELS = {
  user: '用户中台',
  chat: '聊天',
  reader: '阅读',
  music: '音乐',
};

const ROLE_NOTES = {
  ADMIN: '管理员始终拥有完整目录，新增权限会自动包含，无需单独裁剪。',
  USER: '普通用户默认可以上传公共内容、管理自己的私有内容；保存后在下一次身份刷新时生效。',
};

function switchTab(tab) {
  state.tab = tab === 'permissions' ? 'permissions' : 'users';
  el.consoleTabs.querySelectorAll('button[data-tab]').forEach((button) => {
    const active = button.dataset.tab === state.tab;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-selected', String(active));
    button.tabIndex = active ? 0 : -1;
  });
  el.usersView.hidden = state.tab !== 'users';
  el.permissionsView.hidden = state.tab !== 'permissions';
  el.refreshHint.textContent =
    state.tab === 'users' ? '每 10 秒自动刷新审批队列' : '权限目录按需刷新';

  if (state.tab === 'permissions' && !state.permissions.loaded) {
    void loadPermissionCatalog();
  }
}

function canManageRoles() {
  return Boolean(
    state.user?.role === 'ADMIN' || state.user?.permissions?.includes('user.role.manage'),
  );
}

function findRole(role) {
  return state.permissions.catalog?.roles?.find((item) => item.role === role) ?? null;
}

function selectedRole() {
  return findRole(state.permissions.role);
}

function roleDraft(roleView) {
  if (!state.permissions.drafts.has(roleView.role)) {
    state.permissions.drafts.set(roleView.role, new Set(roleView.permissions));
  }
  return state.permissions.drafts.get(roleView.role);
}

function sameCodes(a, b) {
  if (a.size !== b.length) return false;
  return b.every((code) => a.has(code));
}

function isDirtyRole(roleView) {
  const draft = state.permissions.drafts.get(roleView.role);
  return Boolean(draft && !sameCodes(draft, roleView.permissions));
}

function isEditableRole(roleView) {
  return Boolean(roleView && roleView.role !== 'ADMIN' && canManageRoles());
}

function setPermStatus(text, tone = '') {
  el.permStatus.textContent = text;
  if (tone) el.permStatus.dataset.tone = tone;
  else delete el.permStatus.dataset.tone;
}

function syncPermControls() {
  const roleView = selectedRole();
  const editable = isEditableRole(roleView);
  const dirty = roleView ? isDirtyRole(roleView) : false;
  const busy = state.permissions.saving;

  el.permSaveBtn.disabled = !editable || !dirty || busy;
  el.permResetBtn.disabled = !editable || !dirty || busy;

  if (!roleView) {
    el.permCount.textContent = '–';
  } else if (dirty) {
    const draft = roleDraft(roleView);
    el.permCount.textContent = `已授权 ${draft.size} / ${state.permissions.catalog.permissions.length} · 未保存`;
  } else {
    el.permCount.textContent = `已授权 ${roleView.permissions.length} / ${
      state.permissions.catalog.permissions.length
    }`;
  }
  renderRoleList();
}

function renderRoleList() {
  const catalog = state.permissions.catalog;
  el.roleList.textContent = '';
  if (!catalog) return;

  const fragment = document.createDocumentFragment();
  catalog.roles.forEach((roleView) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'role-chip';
    button.setAttribute('role', 'option');
    const active = roleView.role === state.permissions.role;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-selected', String(active));
    button.dataset.role = roleView.role;

    const name = document.createElement('span');
    name.className = 'role-chip__name';
    name.textContent = roleView.label;

    const code = document.createElement('span');
    code.className = 'role-chip__code';
    code.textContent = roleView.role;

    const count = document.createElement('span');
    count.className = 'role-chip__count';
    count.textContent = String(roleView.permissions.length);

    button.append(name, code, count);

    if (isDirtyRole(roleView)) {
      const dirty = document.createElement('span');
      dirty.className = 'role-chip__dirty';
      dirty.textContent = '未保存';
      button.appendChild(dirty);
    }

    fragment.appendChild(button);
  });

  el.roleList.appendChild(fragment);
}

function renderPermissionEditor() {
  const catalog = state.permissions.catalog;
  const roleView = selectedRole();
  el.permissionGroups.textContent = '';

  if (!catalog || !roleView) {
    el.permEmpty.hidden = false;
    el.permRoleTitle.textContent = '–';
    el.permRoleHint.textContent = '选择左侧角色查看授权';
    syncPermControls();
    return;
  }

  el.permEmpty.hidden = true;
  const editable = isEditableRole(roleView);
  const draft = roleDraft(roleView);

  el.permRoleTitle.textContent = `${roleView.label} · ${roleView.role}`;
  el.permRoleHint.textContent = editable
    ? ROLE_NOTES[roleView.role] || '勾选后保存，角色授权会即时写入用户中台。'
    : ROLE_NOTES.ADMIN;
  el.permissionGroups.classList.toggle('is-readonly', !editable);

  const groups = new Map();
  catalog.permissions.forEach((permission) => {
    const key = permission.module || 'other';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(permission);
  });

  const fragment = document.createDocumentFragment();
  groups.forEach((items, module) => {
    const section = document.createElement('section');
    section.className = 'perm-group';

    const head = document.createElement('header');
    head.className = 'perm-group__head';
    const title = document.createElement('h4');
    title.textContent = MODULE_LABELS[module] || module;
    const meta = document.createElement('span');
    meta.textContent = `${items.filter((item) => draft.has(item.code)).length} / ${items.length}`;
    head.append(title, meta);

    const list = document.createElement('div');
    list.className = 'perm-group__list';

    items.forEach((permission) => {
      const label = document.createElement('label');
      label.className = 'perm-item';

      const input = document.createElement('input');
      input.type = 'checkbox';
      input.value = permission.code;
      input.checked = draft.has(permission.code);
      input.disabled = !editable || state.permissions.saving;
      input.addEventListener('change', () => {
        if (input.checked) draft.add(permission.code);
        else draft.delete(permission.code);
        meta.textContent = `${items.filter((item) => draft.has(item.code)).length} / ${items.length}`;
        setPermStatus('有未保存的改动', 'warn');
        syncPermControls();
      });

      const body = document.createElement('span');
      body.className = 'perm-item__body';
      const name = document.createElement('span');
      name.className = 'perm-item__name';
      name.textContent = permission.name;
      const code = document.createElement('span');
      code.className = 'perm-item__code';
      code.textContent = permission.code;
      const desc = document.createElement('span');
      desc.className = 'perm-item__desc';
      desc.textContent = permission.description || '—';
      body.append(name, code, desc);

      label.append(input, body);
      list.appendChild(label);
    });

    section.append(head, list);
    fragment.appendChild(section);
  });

  el.permissionGroups.appendChild(fragment);
  syncPermControls();
}

async function loadPermissionCatalog({ silent = false } = {}) {
  if (state.permissions.loading) return;
  const seq = ++state.permissions.loadSeq;
  state.permissions.loading = true;
  el.permRefreshBtn.disabled = true;
  if (!silent) setPermStatus('正在读取权限目录…');

  try {
    const catalog = await http('/api/v1/user/admin/permissions');
    if (seq !== state.permissions.loadSeq) return;

    const roles = Array.isArray(catalog?.roles) ? catalog.roles : [];
    const permissions = Array.isArray(catalog?.permissions) ? catalog.permissions : [];
    state.permissions.catalog = { permissions, roles };
    state.permissions.loaded = true;
    state.permissions.drafts = new Map();
    state.permissions.error = '';

    if (!roles.some((item) => item.role === state.permissions.role)) {
      state.permissions.role = roles.find((item) => item.role === 'USER')?.role ?? roles[0]?.role;
    }

    renderPermissionEditor();
    setPermStatus(`目录已同步 · ${formatClock()}`, 'ok');
  } catch (error) {
    if (seq !== state.permissions.loadSeq) return;
    if (error?.status === 401) return;
    state.permissions.error = error.message;
    el.permEmpty.hidden = Boolean(state.permissions.catalog);
    el.permRoleTitle.textContent = state.permissions.catalog ? el.permRoleTitle.textContent : '–';
    el.permRoleHint.textContent = state.permissions.catalog
      ? el.permRoleHint.textContent
      : '权限目录加载失败';
    setPermStatus(`读取失败：${error.message}`, 'error');
    if (!silent) {
      toast({ title: '权限目录没有加载', detail: error.message, tone: 'error' });
    }
  } finally {
    if (seq === state.permissions.loadSeq) {
      state.permissions.loading = false;
      el.permRefreshBtn.disabled = false;
    }
  }
}

function selectRole(role) {
  if (!findRole(role)) return;
  state.permissions.role = role;
  renderPermissionEditor();
  setPermStatus('', '');
}

function resetRolePermissions() {
  const roleView = selectedRole();
  if (!roleView || !isEditableRole(roleView) || state.permissions.saving) return;
  state.permissions.drafts.set(roleView.role, new Set(roleView.permissions));
  renderPermissionEditor();
  setPermStatus('已撤销本次改动', 'info');
}

async function saveRolePermissions() {
  const roleView = selectedRole();
  if (!roleView || !isEditableRole(roleView) || state.permissions.saving) return;
  if (!isDirtyRole(roleView)) return;

  const codes = Array.from(roleDraft(roleView)).sort();
  state.permissions.saving = true;
  state.permissions.error = '';
  setPermStatus('正在保存授权…');
  el.permSaveBtn.disabled = true;
  el.permResetBtn.disabled = true;
  renderPermissionEditor();

  try {
    const saved = await http(
      `/api/v1/user/admin/roles/${encodeURIComponent(roleView.role)}/permissions`,
      { method: 'PUT', body: { permissions: codes } },
    );
    const permissions = Array.isArray(saved?.permissions) ? saved.permissions : codes;
    roleView.permissions = permissions;
    state.permissions.drafts.set(roleView.role, new Set(permissions));
    state.permissions.saving = false;
    renderPermissionEditor();
    setPermStatus(`已保存 · ${formatClock()}`, 'ok');
    toast({
      title: `已更新「${roleView.label}」的权限`,
      detail: '新的授权会在用户下一次身份刷新或登录后生效。',
      tone: 'success',
    });
  } catch (error) {
    state.permissions.saving = false;
    if (error?.status === 401) return;
    const message =
      error?.code === 'ADMIN_LOCKOUT'
        ? '管理员角色必须保留「角色权限配置」权限。'
        : error?.code === 'UNKNOWN_PERMISSION'
          ? error.message
          : error.message || '请稍后重试。';
    setPermStatus(`保存失败：${message}`, 'error');
    toast({ title: '授权没有保存', detail: message, tone: 'error' });
    renderPermissionEditor();
  }
}

/* ── 自动刷新 / 启动 ── */

function startAutoRefresh() {
  stopAutoRefresh();
  refreshTimer = setInterval(() => {
    if (!document.hidden && !state.review.open && state.tab === 'users') {
      loadUsers({ silent: true });
    }
  }, REFRESH_MS);
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = undefined;
  }
}

async function bootstrap() {
  void loadMeta();
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
    switchTab('users');
    await loadUsers();
    startAutoRefresh();
  } catch (error) {
    state.token = '';
    writeToken('');
    showLogin(
      error?.status === 401
        ? '登录已失效，请重新进入中台'
        : '暂时无法确认登录状态，请重新进入中台',
    );
  }
}

/* ── 事件 ── */

el.loginForm.addEventListener('submit', login);
el.logoutBtn.addEventListener('click', () => signOut());
el.refreshBtn.addEventListener('click', () => loadUsers());
el.filters.addEventListener('click', (event) => {
  const button = event.target.closest('button[data-status]');
  if (button) selectStatus(button.dataset.status || '');
});
el.stats.forEach((node) => {
  node.addEventListener('click', () => selectStatus(node.dataset.status || ''));
});
el.consoleTabs.addEventListener('click', (event) => {
  const button = event.target.closest('button[data-tab]');
  if (button) switchTab(button.dataset.tab);
});
el.permRefreshBtn.addEventListener('click', () => loadPermissionCatalog());
el.roleList.addEventListener('click', (event) => {
  const button = event.target.closest('button[data-role]');
  if (button) selectRole(button.dataset.role);
});
el.permResetBtn.addEventListener('click', resetRolePermissions);
el.permSaveBtn.addEventListener('click', () => {
  void saveRolePermissions();
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

el.reviewCancel.addEventListener('click', () => closeReview());
el.reviewClose.addEventListener('click', () => closeReview());
el.reviewConfirm.addEventListener('click', () => {
  if (state.review.phase === 'success') {
    closeReview();
    return;
  }
  if (state.review.phase === 'error' && !state.review.canRetry) {
    closeReview();
    return;
  }
  submitReview();
});

el.reviewDialog.addEventListener('cancel', (event) => {
  if (state.review.busy) event.preventDefault();
});

el.reviewDialog.addEventListener('click', (event) => {
  if (event.target !== el.reviewDialog || state.review.busy) return;
  const rect = el.reviewDialog.getBoundingClientRect();
  const inside =
    event.clientX >= rect.left &&
    event.clientX <= rect.right &&
    event.clientY >= rect.top &&
    event.clientY <= rect.bottom;
  if (!inside) closeReview();
});

el.reviewDialog.addEventListener('close', () => {
  const trigger = state.review.trigger;
  const pendingToast = state.review.toastAfterClose;
  clearTimeout(state.review.closeTimer);

  state.review.open = false;
  state.review.busy = false;
  state.review.phase = 'confirm';
  state.review.canRetry = true;
  state.review.user = null;
  state.review.action = null;
  state.review.trigger = null;
  state.review.closeTimer = undefined;
  state.review.toastAfterClose = null;
  el.reviewDialog.dataset.busy = 'false';
  el.reviewDialog.setAttribute('aria-busy', 'false');

  if (pendingToast) toast(pendingToast);

  requestAnimationFrame(() => {
    if (!el.console.hidden && trigger?.isConnected) {
      trigger.focus({ preventScroll: true });
    } else if (!el.console.hidden) {
      el.refreshBtn.focus({ preventScroll: true });
    } else {
      el.loginUsername.focus({ preventScroll: true });
    }
  });
});

el.toastClose.addEventListener('click', hideToast);

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.token && !state.review.open && state.tab === 'users') {
    loadUsers({ silent: true });
  }
});

bootstrap();
