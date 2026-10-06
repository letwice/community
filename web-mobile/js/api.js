// REST client for the Community Store services. Tokens live in sessionStorage for the tab's lifetime.
const cfg = window.COMMUNITY_STORE_CONFIG;
const TOKEN_KEY = 'cs.tokens';

export class ApiError extends Error {
  constructor(status, message, details = []) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function loadTokens() {
  try {
    return JSON.parse(sessionStorage.getItem(TOKEN_KEY)) || null;
  } catch {
    return null;
  }
}

let tokens = loadTokens();

function saveTokens(next) {
  tokens = next;
  if (next) sessionStorage.setItem(TOKEN_KEY, JSON.stringify(next));
  else sessionStorage.removeItem(TOKEN_KEY);
}

function decode(jwt) {
  const payload = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(decodeURIComponent(escape(atob(payload))));
}

export function session() {
  if (!tokens) return null;
  const claims = decode(tokens.access_token);
  return { guid: claims.sub, name: claims.name, email: claims.email, roles: claims.roles || [] };
}

async function parse(response) {
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, body?.message || `HTTP ${response.status}`, body?.details || []);
  }
  return body;
}

async function tokenRequest(form) {
  let response;
  try {
    response = await fetch(`${cfg.authUrl}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(form)
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the auth service — is the backend running?');
  }
  return parse(response);
}

export async function login(email, password) {
  saveTokens(await tokenRequest({ grant_type: 'password', username: email, password }));
  return session();
}

async function refresh() {
  if (!tokens?.refresh_token) return false;
  try {
    saveTokens(await tokenRequest({ grant_type: 'refresh_token', refresh_token: tokens.refresh_token }));
    return true;
  } catch {
    saveTokens(null);
    return false;
  }
}

export async function logout() {
  if (tokens) {
    try {
      await request('POST', `${cfg.userUrl}/frontend/users/me/logout`);
    } catch {
      // best effort
    }
  }
  saveTokens(null);
}

async function request(method, url, body, retry = true) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (tokens) headers.Authorization = `Bearer ${tokens.access_token}`;
  let response;
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'Cannot reach the server — is the backend running?');
  }
  if (response.status === 401 && retry && tokens && await refresh()) {
    return request(method, url, body, false);
  }
  if (response.status === 401) {
    saveTokens(null);
    window.dispatchEvent(new Event('cs:logged-out'));
  }
  return parse(response);
}

const qs = params => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') search.set(k, v); });
  const s = search.toString();
  return s ? `?${s}` : '';
};

export const api = {
  register: body => fetch(`${cfg.authUrl}/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  }).then(parse, () => { throw new ApiError(0, 'Cannot reach the auth service'); }),
  verifyEmail: token => fetch(`${cfg.authUrl}/verify-email`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token })
  }).then(parse, () => { throw new ApiError(0, 'Cannot reach the auth service'); }),

  me: () => request('GET', `${cfg.userUrl}/frontend/users/me`),
  payoutSummary: () => request('GET', `${cfg.userUrl}/frontend/users/me/payout-summary`),
  bankingDetails: () => request('GET', `${cfg.userUrl}/frontend/users/me/banking-details`),
  saveBankingDetails: body => request('POST', `${cfg.userUrl}/frontend/users/me/banking-details`, body),

  createProduct: body => request('POST', `${cfg.productUrl}/frontend/products`, body),
  updateProduct: (guid, body) => request('PUT', `${cfg.productUrl}/frontend/products/${encodeURIComponent(guid)}`, body),
  removeProduct: guid => request('DELETE', `${cfg.productUrl}/frontend/products/${encodeURIComponent(guid)}`),

  createActivity: body => request('POST', `${cfg.productUrl}/frontend/activities`, body),
  updateActivity: (guid, body) => request('PUT', `${cfg.productUrl}/frontend/activities/${encodeURIComponent(guid)}`, body),
  cancelActivity: guid => request('DELETE', `${cfg.productUrl}/frontend/activities/${encodeURIComponent(guid)}`),

  products: params => request('GET', `${cfg.productUrl}/frontend/products${qs(params)}`),
  product: guid => request('GET', `${cfg.productUrl}/frontend/products/${encodeURIComponent(guid)}`),
  reviewProduct: (guid, body) => request('POST', `${cfg.productUrl}/frontend/products/${encodeURIComponent(guid)}/reviews`, body),

  createOrder: body => request('POST', `${cfg.productUrl}/frontend/orders`, body),
  orders: params => request('GET', `${cfg.productUrl}/frontend/orders${qs(params)}`),
  order: guid => request('GET', `${cfg.productUrl}/frontend/orders/${encodeURIComponent(guid)}`),
  orderAction: (guid, action) => request('POST', `${cfg.productUrl}/frontend/orders/${encodeURIComponent(guid)}/${action}`),

  activities: params => request('GET', `${cfg.productUrl}/frontend/activities${qs(params)}`),
  activity: guid => request('GET', `${cfg.productUrl}/frontend/activities/${encodeURIComponent(guid)}`),
  reviewActivity: (guid, body) => request('POST', `${cfg.productUrl}/frontend/activities/${encodeURIComponent(guid)}/reviews`, body),

  admin: {
    users: params => request('GET', `${cfg.userUrl}/backoffice/users${qs(params)}`),
    user: guid => request('GET', `${cfg.userUrl}/backoffice/users/${encodeURIComponent(guid)}`),
    verify: guid => request('POST', `${cfg.userUrl}/backoffice/users/${encodeURIComponent(guid)}/verify`),
    reject: (guid, reason) => request('POST', `${cfg.userUrl}/backoffice/users/${encodeURIComponent(guid)}/reject`, { reason }),
    setStatus: (guid, status, reason) => request('PUT', `${cfg.userUrl}/backoffice/users/${encodeURIComponent(guid)}/status`, { status, reason }),
    setRoles: (guid, roles) => request('PUT', `${cfg.userUrl}/backoffice/users/${encodeURIComponent(guid)}/roles`, { roles }),

    salesSummary: () => request('GET', `${cfg.productUrl}/backoffice/sales-summary`),
    vendorSummary: guid => request('GET', `${cfg.productUrl}/backoffice/vendors/${encodeURIComponent(guid)}/sales-summary`),
    vendorProducts: guid => request('GET', `${cfg.productUrl}/backoffice/vendors/${encodeURIComponent(guid)}/products`),
    vendorReviews: guid => request('GET', `${cfg.productUrl}/backoffice/vendors/${encodeURIComponent(guid)}/reviews`),
    // statuses: array of escrow statuses (repeated query param)
    orders: ({ statuses = [], vendor, size = 50 } = {}) => {
      const search = new URLSearchParams();
      statuses.forEach(s => search.append('status', s));
      if (vendor) search.set('vendor', vendor);
      search.set('size', size);
      return request('GET', `${cfg.productUrl}/backoffice/orders?${search}`);
    },
    release: guid => request('POST', `${cfg.productUrl}/backoffice/orders/${encodeURIComponent(guid)}/release`),
    refund: guid => request('POST', `${cfg.productUrl}/backoffice/orders/${encodeURIComponent(guid)}/resolve`, { outcome: 'REFUND' }),

    activities: params => request('GET', `${cfg.productUrl}/backoffice/activities${qs(params)}`),
    activityReviews: guid => request('GET', `${cfg.productUrl}/backoffice/activities/${encodeURIComponent(guid)}/reviews`),
    approveActivity: guid => request('POST', `${cfg.productUrl}/backoffice/activities/${encodeURIComponent(guid)}/approve`),
    rejectActivity: (guid, reason) => request('POST', `${cfg.productUrl}/backoffice/activities/${encodeURIComponent(guid)}/reject`, { reason })
  },

  notifications: params => request('GET', `${cfg.notificationUrl}/frontend/notifications${qs(params)}`),
  markRead: id => request('PUT', `${cfg.notificationUrl}/frontend/notifications/${id}/read`),
  unreadCount: () => request('GET', `${cfg.notificationUrl}/frontend/notifications/unread-count`)
};
