// Community Store mobile-first SPA (Student, and read-only browsing for any role). Hash-routed, no build step.
import { api, ApiError, login, logout, session } from './api.js';

const view = document.getElementById('view');
const tabbar = document.getElementById('tabbar');
const logoutButton = document.getElementById('logout');
const CART_KEY = 'cs.cart';
const BUYER_ROLES = ['STUDENT', 'VENDOR', 'COMMUNITY_LEADER', 'FACULTY'];

// ---------------------------------------------------------------------------------------------- helpers

const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = value => `R${Number(value ?? 0).toLocaleString('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const date = value => value ? new Date(value).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '';
const stars = rating => rating == null ? 'No reviews yet' : '★'.repeat(Math.round(rating)) + '☆'.repeat(5 - Math.round(rating));
const conditionLabel = c => c === 'SECOND_HAND' ? 'SECOND-HAND' : 'NEW';
const roles = () => session()?.roles || [];
const hasRole = role => roles().includes(role);
const canBuy = () => roles().some(r => BUYER_ROLES.includes(r));
const canSell = () => hasRole('VENDOR') || hasRole('FACULTY');
const canOrganise = () => hasRole('COMMUNITY_LEADER') || hasRole('FACULTY');
const isAdmin = () => hasRole('ADMIN');
const ROLE_LABELS = { STUDENT: 'Student', VENDOR: 'Vendor', COMMUNITY_LEADER: 'Community leader', FACULTY: 'Faculty', ADMIN: 'Admin' };
const primaryRole = () => ['ADMIN', 'FACULTY', 'VENDOR', 'COMMUNITY_LEADER', 'STUDENT'].find(hasRole);
const manageLabel = () => canSell() && canOrganise() ? 'Manage' : canSell() ? 'Sell' : 'Events';

const CATEGORY_ICONS = {
  Textbooks: '📚', Electronics: '🔌', Clothing: '👕', Stationery: '✏️', Transport: '🚲', Appliances: '🧊',
  Furniture: '🪑', Lifestyle: '🌱', 'Lab Equipment': '🥽'
};

function toast(message, kind = 'info') {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.className = `toast ${kind}`;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { el.hidden = true; }, 3500);
}

function showError(error) {
  const details = error instanceof ApiError && error.details.length ? ` (${error.details.join('; ')})` : '';
  toast(`${error.message}${details}`, 'error');
}

function render(html) {
  view.innerHTML = html;
  window.scrollTo(0, 0);
}

function loading() {
  render('<p class="muted center">Loading…</p>');
}

function simulated(text) {
  return `<p class="simulated">SIMULATED — ${esc(text)}</p>`;
}

function productImage(product) {
  const url = product.imageUrls?.[0];
  if (url) return `<img src="${esc(url)}" alt="" loading="lazy">`;
  return `<div class="placeholder">${CATEGORY_ICONS[product.category] || '🛍'}</div>`;
}

function on(selector, event, handler) {
  view.querySelectorAll(selector).forEach(el => el.addEventListener(event, handler));
}

// ---------------------------------------------------------------------------------------------- cart

const cart = {
  items() {
    try { return JSON.parse(localStorage.getItem(CART_KEY)) || []; } catch { return []; }
  },
  save(items) {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
    updateBadges();
  },
  add(product, quantity) {
    const items = cart.items();
    const existing = items.find(i => i.productGuid === product.guid);
    if (existing) existing.quantity += quantity;
    else items.push({ productGuid: product.guid, title: product.title, price: product.price, quantity });
    cart.save(items);
  },
  remove(productGuid) {
    cart.save(cart.items().filter(i => i.productGuid !== productGuid));
  },
  clear() {
    cart.save([]);
  }
};

async function updateBadges() {
  const cartBadge = document.getElementById('cart-badge');
  const count = cart.items().reduce((n, i) => n + i.quantity, 0);
  cartBadge.textContent = count;
  cartBadge.hidden = count === 0;
  if (!session()) return;
  try {
    const { count: unread } = await api.unreadCount();
    const inboxBadge = document.getElementById('inbox-badge');
    inboxBadge.textContent = unread;
    inboxBadge.hidden = unread === 0;
  } catch {
    // badge is cosmetic
  }
}

// ---------------------------------------------------------------------------------------------- auth views

function loginView() {
  render(`
    <section class="card narrow">
      <h1>Welcome</h1>
      <p class="muted">Affordable, sustainable trading for your campus community.</p>
      <form id="login-form" class="form">
        <label>Email <input name="email" type="email" autocomplete="username" required></label>
        <label>Password <input name="password" type="password" autocomplete="current-password" required></label>
        <button class="primary" type="submit">Log in</button>
      </form>
      <p class="center"><a href="#/register">Create an account</a> · <a href="#/verify">Verify email</a></p>
    </section>`);
  on('#login-form', 'submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    try {
      await login(form.get('email').trim(), form.get('password'));
      location.hash = '#/home';
    } catch (error) {
      showError(error);
    }
  });
}

const UNIVERSITY_DOMAIN = (window.COMMUNITY_STORE_CONFIG.universityEmailDomain || 'university.com').toLowerCase();
const UNIVERSITY_TYPES = ['STUDENT', 'FACULTY'];
const BUSINESS_NUMBER_PATTERN = /^[A-Za-z0-9/ -]{4,50}$/;

const emailDomain = email => email.trim().toLowerCase().split('@').pop();

function registerView() {
  render(`
    <section class="card narrow">
      <h1>Create account</h1>
      <form id="register-form" class="form" novalidate>
        <label>I am a…
          <select name="userType">
            <option value="STUDENT">Student</option>
            <option value="VENDOR">Vendor</option>
            <option value="COMMUNITY_LEADER">Community leader</option>
            <option value="FACULTY">Faculty</option>
          </select>
        </label>
        <label>First name <input name="firstName" required maxlength="100" autocomplete="given-name"></label>
        <label>Last name <input name="lastName" required maxlength="100" autocomplete="family-name"></label>
        <label>Email <input name="email" type="email" required maxlength="255" autocomplete="email">
          <small id="email-hint" class="hint"></small>
        </label>
        <label>Confirm email <input name="confirmEmail" type="email" required maxlength="255" autocomplete="off"></label>
        <label id="business-field" hidden>Business registration number
          <input name="businessNumber" maxlength="50" placeholder="e.g. 2019/123456/07">
          <small class="hint">Shown to the admin who verifies your vendor account.</small>
        </label>
        <label>Password <input name="password" type="password" minlength="8" maxlength="72" required autocomplete="new-password"></label>
        <button class="primary" type="submit">Register</button>
      </form>
      <p id="next-steps" class="muted small"></p>
      <p class="center"><a href="#/login">Back to log in</a></p>
    </section>`);

  const form = view.querySelector('#register-form');
  const field = name => form.elements.namedItem(name);
  const fieldError = el => {
    let error = el.parentElement.querySelector('.field-error');
    if (!error) {
      error = document.createElement('small');
      error.className = 'field-error';
      el.insertAdjacentElement('afterend', error);
    }
    return error;
  };

  const NEXT_STEPS = {
    STUDENT: 'After verifying your email you can browse, buy and review straight away.',
    FACULTY: 'After verifying your email, an admin verifies your account. Add banking details in the desktop app before listing items.',
    VENDOR: 'After verifying your email, add your banking details in the desktop app — you can’t list items for sale until they’re on file — and wait for an admin to verify your business.',
    COMMUNITY_LEADER: 'After verifying your email, an admin verifies your account before you can post activities (desktop app).'
  };

  // Returns the error message for a field, or '' when it is valid. Mirrors server-auth's RegistrationPolicy.
  function validateField(name) {
    const type = field('userType').value;
    const el = field(name);
    const value = el.value.trim();
    switch (name) {
      case 'firstName':
      case 'lastName':
        return value ? '' : 'Required.';
      case 'email':
        if (!value) return 'Required.';
        if (el.validity.typeMismatch) return 'Enter a valid email address.';
        if (UNIVERSITY_TYPES.includes(type) && emailDomain(value) !== UNIVERSITY_DOMAIN) {
          return `Students and faculty must use an @${UNIVERSITY_DOMAIN} address.`;
        }
        return '';
      case 'confirmEmail':
        if (!value) return 'Please confirm your email.';
        return value.toLowerCase() === field('email').value.trim().toLowerCase() ? '' : 'Email addresses do not match.';
      case 'businessNumber':
        if (type !== 'VENDOR') return '';
        if (!value) return 'Vendors must enter a business registration number.';
        return BUSINESS_NUMBER_PATTERN.test(value) ? '' : 'Use 4–50 letters, digits, spaces, “/” or “-”.';
      case 'password':
        return value.length >= 8 ? '' : 'At least 8 characters.';
      default:
        return '';
    }
  }

  const touched = new Set();
  function show(name) {
    const message = validateField(name);
    const el = field(name);
    el.setCustomValidity(message);
    el.setAttribute('aria-invalid', String(Boolean(message) && touched.has(name)));
    const error = fieldError(el);
    error.textContent = touched.has(name) ? message : '';
    return !message;
  }

  const FIELDS = ['firstName', 'lastName', 'email', 'confirmEmail', 'businessNumber', 'password'];
  function syncType() {
    const type = field('userType').value;
    view.querySelector('#business-field').hidden = type !== 'VENDOR';
    view.querySelector('#email-hint').textContent = UNIVERSITY_TYPES.includes(type)
      ? `Use your @${UNIVERSITY_DOMAIN} address.` : 'Any email address.';
    view.querySelector('#next-steps').textContent = NEXT_STEPS[type];
    FIELDS.forEach(show);
  }

  FIELDS.forEach(name => {
    field(name).addEventListener('blur', () => { touched.add(name); show(name); });
    field(name).addEventListener('input', () => {
      show(name);
      if (name === 'email' && field('confirmEmail').value) { touched.add('confirmEmail'); show('confirmEmail'); }
    });
  });
  field('userType').addEventListener('change', syncType);
  syncType();

  form.addEventListener('submit', async e => {
    e.preventDefault();
    FIELDS.forEach(name => touched.add(name));
    const valid = FIELDS.map(show).every(Boolean);
    if (!valid) {
      form.querySelector('[aria-invalid="true"]')?.focus();
      return;
    }
    const body = Object.fromEntries(new FormData(form));
    delete body.confirmEmail;
    if (body.userType !== 'VENDOR') delete body.businessNumber;
    Object.keys(body).forEach(k => { if (k !== 'password') body[k] = body[k].trim(); });
    try {
      const result = await api.register(body);
      toast('Account created. Verification email is simulated.', 'success');
      location.hash = `#/verify?type=${body.userType}${result.simulatedVerificationToken ? `&token=${result.simulatedVerificationToken}` : ''}`;
    } catch (error) {
      showError(error);
    }
  });
}

function verifyView(params) {
  render(`
    <section class="card narrow">
      <h1>Verify your email</h1>
      ${simulated('no email is actually sent. In this PoC the token is shown after registration and logged by server-auth.')}
      <form id="verify-form" class="form">
        <label>Verification token <input name="token" required value="${esc(params.get('token') || '')}"></label>
        <button class="primary" type="submit">Verify</button>
      </form>
      <p class="center"><a href="#/login">Back to log in</a></p>
    </section>`);
  on('#verify-form', 'submit', async e => {
    e.preventDefault();
    try {
      await api.verifyEmail(new FormData(e.target).get('token').trim());
      toast(params.get('type') === 'VENDOR'
        ? 'Email verified. Next: log in to the desktop app and add your banking details.'
        : 'Email verified — you can log in now.', 'success');
      location.hash = '#/login';
    } catch (error) {
      showError(error);
    }
  });
}

// ---------------------------------------------------------------------------------------------- home

async function homeView() {
  loading();
  const me = session();
  const [orders, upcoming] = await Promise.all([
    canBuy() ? api.orders({ role: 'buyer', size: 5 }) : Promise.resolve({ content: [] }),
    api.activities({ upcoming: true, size: 3 })
  ]);
  const shortcuts = [];
  if (canSell()) {
    const [summary, readiness] = await Promise.all([api.payoutSummary(), sellerReadiness()]);
    shortcuts.push(`
      <section>
        <div class="section-head"><h2>My sales</h2><a href="#/sales">Dashboard</a></div>
        ${sellerNotice(readiness)}
        <div class="stats">
          <div class="stat"><div class="label">Total sales</div><div class="value">${money(summary.totalSalesAmount)}</div></div>
          <div class="stat"><div class="label">Pending payout</div><div class="value">${money(summary.totalPendingPayoutAmount)}</div></div>
        </div>
        <div class="actions">
          ${readiness.canList ? '<a class="primary" href="#/listing/new">+ List an item</a>' : ''}
          <a class="secondary" href="#/listings">My listings</a>
        </div>
      </section>`);
  }
  if (canOrganise()) {
    shortcuts.push(`
      <section>
        <div class="section-head"><h2>My events</h2><a href="#/events">Manage</a></div>
        <div class="actions"><a class="primary" href="#/event/new">+ Post an event</a></div>
      </section>`);
  }
  if (isAdmin()) {
    shortcuts.push(`
      <section>
        <div class="section-head"><h2>Needs your attention</h2><a href="#/admin">Admin</a></div>
        ${attentionList(await adminAttention())}
      </section>`);
  }
  render(`
    <h1>Hi ${esc(me.name.split(' ')[0])} 👋</h1>
    <p class="muted">Signed in as ${esc(roles().map(r => ROLE_LABELS[r] || r).join(' · '))}</p>
    ${shortcuts.join('')}
    ${canBuy() ? `<section>
      <div class="section-head"><h2>My recent orders</h2><a href="#/orders">See all</a></div>
      ${orders.content.length ? orders.content.map(orderRow).join('') : '<p class="muted">No orders yet. <a href="#/browse">Start browsing</a>.</p>'}
    </section>` : ''}
    <section>
      <div class="section-head"><h2>Coming up on campus</h2><a href="#/bulletin">Bulletin</a></div>
      ${upcoming.content.length ? upcoming.content.map(activityCard).join('') : '<p class="muted">No upcoming activities.</p>'}
    </section>`);
}

function orderRow(order) {
  return `
    <a class="row" href="#/order/${esc(order.guid)}">
      <div>
        <strong>${esc(order.productTitle || 'Product')}</strong>
        <div class="muted small">${esc(order.escrowReferenceNumber)} · ${order.quantity} × ${money(order.unitPrice)}</div>
      </div>
      <span class="status status-${esc(order.escrowStatus)}">${esc(order.escrowStatus.replace(/_/g, ' '))}</span>
    </a>`;
}

// ---------------------------------------------------------------------------------------------- browse

async function browseView(params) {
  const search = params.get('search') || '';
  const category = params.get('category') || '';
  const condition = params.get('condition') || '';
  render(`
    <form id="search-form" class="search">
      <input name="search" type="search" placeholder="🔍 Search products (title starts with…)" value="${esc(search)}">
      <div class="filters">
        <select name="category">
          <option value="">All categories</option>
          ${Object.keys(CATEGORY_ICONS).map(c => `<option ${c === category ? 'selected' : ''}>${esc(c)}</option>`).join('')}
        </select>
        <select name="condition">
          <option value="">Any condition</option>
          <option value="NEW" ${condition === 'NEW' ? 'selected' : ''}>New</option>
          <option value="SECOND_HAND" ${condition === 'SECOND_HAND' ? 'selected' : ''}>Second-hand</option>
        </select>
      </div>
    </form>
    <div id="results"><p class="muted center">Loading…</p></div>`);
  const submit = () => {
    const form = new FormData(view.querySelector('#search-form'));
    const next = new URLSearchParams([...form].filter(([, v]) => v));
    location.hash = `#/browse${next.toString() ? `?${next}` : ''}`;
  };
  on('#search-form', 'submit', e => { e.preventDefault(); submit(); });
  on('#search-form select', 'change', submit);

  const page = await api.products({ search, category, condition, size: 50 });
  view.querySelector('#results').innerHTML = page.content.length
    ? `<div class="grid">${page.content.map(productCard).join('')}</div>`
    : '<p class="muted center">No products match your search.</p>';
}

function productCard(product) {
  return `
    <a class="product-card" href="#/product/${esc(product.guid)}">
      ${productImage(product)}
      <div class="product-body">
        <span class="badge-pill ${product.condition === 'NEW' ? 'new' : 'used'}">${conditionLabel(product.condition)}</span>
        <strong>${esc(product.title)}</strong>
        <span class="price">${money(product.price)}</span>
      </div>
    </a>`;
}

async function productView(guid) {
  loading();
  const detail = await api.product(guid);
  const p = detail.product;
  const buyable = canBuy() && p.status === 'ACTIVE' && p.vendorUserGuid !== session().guid;
  render(`
    <a href="#/browse" class="back">‹ Browse</a>
    <section class="card">
      <div class="hero">${productImage(p)}</div>
      <h1>${esc(p.title)}</h1>
      <p class="price large">${money(p.price)} · <span class="muted">${conditionLabel(p.condition)}</span></p>
      <p class="rating">${stars(detail.averageRating)} <span class="muted">(${detail.reviewCount} review${detail.reviewCount === 1 ? '' : 's'})</span></p>
      <p>${esc(p.description)}</p>
      <p class="muted small">${esc(p.category || '')} · ${p.stockQuantity} in stock</p>
      <p class="seller">Sold by <strong>${esc(detail.seller.name)}</strong>
        ${detail.seller.verified ? '<span class="verified">✔ Verified</span>' : ''}</p>
      ${buyable ? `
        <div class="buy">
          <label>Qty <input id="qty" type="number" min="1" max="${p.stockQuantity}" value="1"></label>
          <button id="add-to-cart" class="primary">Add to Cart</button>
        </div>` : p.status !== 'ACTIVE' ? '<p class="muted">This item is not currently available.</p>' : ''}
    </section>
    <section>
      <h2>Reviews</h2>
      ${detail.reviews.length ? detail.reviews.map(reviewItem).join('') : '<p class="muted">No reviews yet.</p>'}
    </section>`);
  on('#add-to-cart', 'click', () => {
    const qty = Math.max(1, Math.min(p.stockQuantity, Number(view.querySelector('#qty').value) || 1));
    cart.add(p, qty);
    toast(`Added ${qty} × ${p.title} to your cart`, 'success');
  });
}

function reviewItem(review) {
  return `<div class="review"><div>${stars(review.rating)}</div><p>${esc(review.comment || '')}</p>
    <div class="muted small">${date(review.createdAt)}</div></div>`;
}

// ---------------------------------------------------------------------------------------------- cart & checkout

function cartView() {
  const items = cart.items();
  const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0);
  render(`
    <h1>Cart</h1>
    ${items.length ? `
      ${items.map(i => `
        <div class="row">
          <div><strong>${esc(i.title)}</strong><div class="muted small">${i.quantity} × ${money(i.price)}</div></div>
          <button class="link-button" data-remove="${esc(i.productGuid)}">Remove</button>
        </div>`).join('')}
      <p class="total">Total <strong>${money(total)}</strong></p>
      <p class="muted small">Each item becomes its own escrow order with the seller.</p>
      <button id="checkout" class="primary block">Check out</button>`
    : '<p class="muted">Your cart is empty. <a href="#/browse">Browse products</a>.</p>'}`);
  on('[data-remove]', 'click', e => { cart.remove(e.target.dataset.remove); cartView(); });
  on('#checkout', 'click', async e => {
    e.target.disabled = true;
    const created = [];
    for (const item of cart.items()) {
      try {
        created.push(await api.createOrder({ productGuid: item.productGuid, quantity: item.quantity }));
        cart.remove(item.productGuid);
      } catch (error) {
        showError(new ApiError(error.status, `${item.title}: ${error.message}`, error.details));
      }
    }
    if (created.length === 1) location.hash = `#/order/${created[0].guid}`;
    else if (created.length > 1) location.hash = '#/orders';
    else cartView();
  });
}

async function orderView(guid) {
  loading();
  const order = await api.order(guid);
  const isBuyer = order.buyerUserGuid === session().guid;
  const isVendor = order.vendorUserGuid === session().guid;
  const asAdmin = isAdmin() && !isBuyer && !isVendor;
  const actions = [];
  if (asAdmin && ['SHIPPED', 'DELIVERED_CONFIRMED', 'DISPUTED'].includes(order.escrowStatus)) {
    actions.push('<button class="primary block" data-admin="release">Release funds to seller</button>');
  }
  if (asAdmin && order.escrowStatus === 'DISPUTED') {
    actions.push('<button class="secondary block" data-admin="refund">Refund buyer</button>');
  }
  if (isVendor && order.escrowStatus === 'ESCROW_HELD') {
    actions.push('<button class="primary block" data-action="mark-shipped">Mark as shipped</button>');
  }
  if (isVendor && order.escrowStatus === 'AWAITING_PAYMENT') {
    actions.push('<button class="secondary block" data-action="cancel">Cancel order</button>');
  }
  if (isBuyer && order.escrowStatus === 'AWAITING_PAYMENT') {
    actions.push('<button class="primary block" data-action="confirm-payment">I have paid</button>');
    actions.push('<button class="secondary block" data-action="cancel">Cancel order</button>');
  }
  if (isBuyer && order.escrowStatus === 'SHIPPED') {
    actions.push('<button class="primary block" data-action="confirm-receipt">I received my order</button>');
  }
  if (isBuyer && ['ESCROW_HELD', 'SHIPPED'].includes(order.escrowStatus)) {
    actions.push('<button class="secondary block" data-action="dispute">Raise a dispute</button>');
  }
  render(`
    ${asAdmin ? adminBack('#/admin-orders', 'Orders & payouts') : isVendor ? '<a href="#/sales" class="back">‹ Sales dashboard</a>' : '<a href="#/orders" class="back">‹ Orders</a>'}
    <section class="card">
      <h1>${asAdmin ? 'Order (admin)' : isVendor ? 'Sale' : order.escrowStatus === 'AWAITING_PAYMENT' ? 'Checkout' : 'Order'}</h1>
      <p>Order summary: ${order.quantity} × ${esc(order.productTitle || 'Product')} — <strong>${money(order.totalAmount)}</strong></p>
      <span class="status status-${esc(order.escrowStatus)}">${esc(order.escrowStatus.replace(/_/g, ' '))}</span>
      <div class="escrow">
        <div class="muted small">Escrow reference</div>
        <div class="escrow-ref">${esc(order.escrowReferenceNumber)}</div>
        ${order.escrowStatus === 'AWAITING_PAYMENT'
          ? (isVendor ? '<p>Waiting for the buyer to pay into escrow. Don’t ship yet.</p>'
            : `<p>Please EFT ${money(order.totalAmount)} to the Community Store escrow account using the reference above.</p>`) : ''}
        ${isVendor && order.escrowStatus === 'ESCROW_HELD' ? '<p>Payment is held in escrow — ship the item, then mark it as shipped.</p>' : ''}
        ${order.escrowStatus === 'DELIVERED_CONFIRMED'
          ? `<p>${asAdmin ? 'The buyer confirmed receipt. Release the funds to the seller.' : 'Receipt confirmed — the funds are released to the seller once an admin approves the payout.'}</p>` : ''}
      </div>
      ${simulated('there is no real bank integration. "I have paid" is a self-attestation and no money moves.')}
      <p class="muted small">Funds are only released to the seller after the buyer confirms receipt and an admin approves the payout.</p>
      <ol class="timeline">
        <li class="${order.createdAt ? 'done' : ''}">Ordered ${date(order.createdAt)}</li>
        <li class="${order.paidAt ? 'done' : ''}">Paid into escrow ${date(order.paidAt)}</li>
        <li class="${order.shippedAt ? 'done' : ''}">Shipped ${date(order.shippedAt)}</li>
        <li class="${order.deliveryConfirmedAt ? 'done' : ''}">Receipt confirmed ${date(order.deliveryConfirmedAt)}</li>
        <li class="${order.fundsReleasedAt ? 'done' : ''}">Funds released to seller ${date(order.fundsReleasedAt)}</li>
      </ol>
      ${actions.join('')}
      ${isBuyer && ['DELIVERED_CONFIRMED', 'FUNDS_RELEASED'].includes(order.escrowStatus) && !order.reviewed ? reviewForm('product-review') : ''}
      ${order.reviewed ? '<p class="muted">You reviewed this purchase. Thank you!</p>' : ''}
    </section>`);
  on('[data-action]', 'click', async e => {
    const action = e.target.dataset.action;
    if (action === 'cancel' && !confirm('Cancel this order?')) return;
    if (action === 'dispute' && !confirm('Raise a dispute? Funds stay in escrow until an admin resolves it.')) return;
    try {
      await api.orderAction(guid, action);
      toast('Order updated', 'success');
      orderView(guid);
    } catch (error) {
      showError(error);
    }
  });
  on('[data-admin]', 'click', async e => {
    const refund = e.target.dataset.admin === 'refund';
    if (!confirm(refund ? 'Refund the buyer?' : 'Release the escrowed funds to the seller?')) return;
    try {
      await (refund ? api.admin.refund(guid) : api.admin.release(guid));
      toast(refund ? 'Buyer refunded' : 'Funds released', 'success');
      orderView(guid);
    } catch (error) {
      showError(error);
    }
  });
  on('#product-review', 'submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    try {
      await api.reviewProduct(order.productGuid, { orderGuid: guid, rating: Number(form.get('rating')), comment: form.get('comment') });
      toast('Review posted', 'success');
      orderView(guid);
    } catch (error) {
      showError(error);
    }
  });
}

function reviewForm(id) {
  return `
    <form id="${id}" class="form review-form">
      <h2>Leave a review</h2>
      <label>Rating
        <select name="rating">${[5, 4, 3, 2, 1].map(n => `<option value="${n}">${'★'.repeat(n)}</option>`).join('')}</select>
      </label>
      <label>Comment <textarea name="comment" maxlength="2000" rows="3"></textarea></label>
      <button class="primary" type="submit">Post review</button>
    </form>`;
}

async function ordersView() {
  loading();
  const page = await api.orders({ role: 'buyer', size: 50 });
  render(`
    <h1>My orders</h1>
    ${page.content.length ? page.content.map(orderRow).join('') : '<p class="muted">No orders yet.</p>'}`);
}

// ---------------------------------------------------------------------------------------------- bulletin

function activityCard(activity) {
  const past = new Date(activity.activityDate) < new Date();
  return `
    <a class="row activity" href="#/activity/${esc(activity.guid)}">
      <div>
        <strong>${esc(activity.title)}</strong>
        <div class="muted small">📅 ${date(activity.activityDate)} · 📍 ${esc(activity.location || 'TBC')}</div>
      </div>
      ${past ? '<span class="status">PAST</span>' : ''}
    </a>`;
}

async function bulletinView() {
  loading();
  const [upcoming, past] = await Promise.all([api.activities({ upcoming: true, size: 50 }), api.activities({ upcoming: false, size: 20 })]);
  render(`
    <h1>Community bulletin</h1>
    <section><h2>Upcoming</h2>
      ${upcoming.content.length ? upcoming.content.map(activityCard).join('') : '<p class="muted">Nothing scheduled yet.</p>'}
    </section>
    <section><h2>Past — share your feedback</h2>
      ${past.content.length ? past.content.map(activityCard).join('') : '<p class="muted">No past activities.</p>'}
    </section>`);
}

async function activityView(guid) {
  loading();
  const detail = await api.activity(guid);
  const a = detail.activity;
  const past = new Date(a.activityDate) < new Date();
  const canReview = past && canBuy() && a.status !== 'CANCELLED' && a.organiserUserGuid !== session().guid;
  render(`
    <a href="#/bulletin" class="back">‹ Bulletin</a>
    <section class="card">
      <h1>${esc(a.title)}</h1>
      <p class="muted">📅 ${date(a.activityDate)} · 📍 ${esc(a.location || 'TBC')}</p>
      ${a.status === 'CANCELLED' ? '<p class="simulated">This activity was cancelled.</p>' : ''}
      <p>${esc(a.description)}</p>
      <p class="rating">${stars(detail.averageRating)} <span class="muted">(${detail.reviewCount})</span></p>
      ${canReview ? reviewForm('activity-review') : !past ? '<p class="muted small">You can review this activity after it has taken place.</p>' : ''}
    </section>
    <section><h2>Reviews</h2>
      ${detail.reviews.length ? detail.reviews.map(reviewItem).join('') : '<p class="muted">No reviews yet.</p>'}
    </section>`);
  on('#activity-review', 'submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    try {
      await api.reviewActivity(guid, { rating: Number(form.get('rating')), comment: form.get('comment') });
      toast('Review posted', 'success');
      activityView(guid);
    } catch (error) {
      showError(error);
    }
  });
}

// ---------------------------------------------------------------------------------------------- inbox

async function inboxView() {
  loading();
  const page = await api.notifications({ size: 50 });
  render(`
    <h1>Inbox</h1>
    ${page.content.length ? page.content.map(n => `
      <button class="row notification ${n.readFlag ? '' : 'unread'}" data-id="${n.id}" data-entity="${esc(n.relatedEntityType || '')}"
              data-entity-id="${esc(n.relatedEntityId || '')}">
        <div>
          <strong>${esc(n.subject)}</strong>
          <p>${esc(n.body || '')}</p>
          <div class="muted small">${date(n.createdAt)}</div>
        </div>
      </button>`).join('') : '<p class="muted">No notifications.</p>'}`);
  on('.notification', 'click', async e => {
    const el = e.currentTarget;
    if (el.classList.contains('unread')) {
      try {
        await api.markRead(el.dataset.id);
      } catch (error) {
        showError(error);
      }
    }
    const id = el.dataset.entityId;
    const target = !id ? null
      : el.dataset.entity === 'ORDER' ? `#/order/${id}`
      : el.dataset.entity === 'ACTIVITY' ? (isAdmin() ? `#/admin-event/${id}` : canOrganise() ? '#/events' : `#/activity/${id}`)
      : el.dataset.entity === 'ACCOUNT' && isAdmin() && id !== session().guid ? `#/admin-user/${id}`
      : null;
    if (target) location.hash = target;
    else inboxView();
    updateBadges();
  });
}

// ---------------------------------------------------------------------------------------------- seller & organiser tools
// Vendors and faculty sell; community leaders and faculty post events. The server enforces every rule — these
// screens only explain them up front.

const PRODUCT_CATEGORIES = Object.keys(CATEGORY_ICONS);

async function sellerReadiness() {
  const [me, banking] = await Promise.all([
    api.me(),
    api.bankingDetails().catch(error => { if (error.status === 404) return null; throw error; })
  ]);
  return { me, banking, verified: me.verificationStatus === 'VERIFIED', canList: Boolean(banking) && me.verificationStatus === 'VERIFIED' };
}

function sellerNotice({ banking, verified, me }) {
  if (!banking) {
    return `<p class="simulated">Add your <a href="#/banking">banking details</a> before you can list items for sale.</p>`;
  }
  if (!verified) {
    return `<p class="notice">Banking details on file. Your account is ${esc(me.verificationStatus.replace(/_/g, ' ').toLowerCase())} —
      an admin must verify it before you can list items.</p>`;
  }
  return '';
}

function organiserNotice(me) {
  return me.verificationStatus === 'VERIFIED' ? ''
    : `<p class="notice">Your account is ${esc(me.verificationStatus.replace(/_/g, ' ').toLowerCase())} — an admin must verify it before you can post events.</p>`;
}

function tile(href, icon, title, text) {
  return `<a class="tile" href="${href}"><span class="icon">${icon}</span><div><strong>${esc(title)}</strong><p class="muted small">${esc(text)}</p></div></a>`;
}

async function manageView() {
  loading();
  const sections = [];
  if (canSell()) {
    const readiness = await sellerReadiness();
    sections.push(`
      <section>
        <h2>Selling</h2>
        ${sellerNotice(readiness)}
        <div class="tiles">
          ${tile('#/sales', '📈', 'Sales dashboard', 'Sales, payouts and orders to fulfil')}
          ${tile('#/listings', '🏷', 'My listings', 'List items for sale and manage stock')}
          ${tile('#/banking', '🏦', 'Banking details', readiness.banking ? `On file: ${readiness.banking.bankName} ${readiness.banking.maskedAccountNumber}` : 'Required before you can list items')}
        </div>
      </section>`);
  }
  if (canOrganise()) {
    const me = await api.me();
    sections.push(`
      <section>
        <h2>Community events</h2>
        ${organiserNotice(me)}
        <div class="tiles">
          ${tile('#/event/new', '📣', 'Post an event', 'Publish an activity to the campus bulletin')}
          ${tile('#/events', '🗓', 'My events', 'Edit or cancel the events you organise')}
        </div>
      </section>`);
  }
  render(`<h1>${esc(manageLabel())}</h1>${sections.join('')}`);
}

async function salesView() {
  loading();
  const [summary, orders] = await Promise.all([api.payoutSummary(), api.orders({ role: 'vendor', size: 50 })]);
  const toFulfil = orders.content.filter(o => o.escrowStatus === 'ESCROW_HELD');
  render(`
    <a href="#/manage" class="back">‹ ${esc(manageLabel())}</a>
    <h1>Sales dashboard</h1>
    <div class="stats">
      <div class="stat"><div class="label">Total sales</div><div class="value">${money(summary.totalSalesAmount)}</div></div>
      <div class="stat"><div class="label">Orders</div><div class="value">${summary.totalSalesCount}</div></div>
      <div class="stat"><div class="label">Paid out</div><div class="value">${money(summary.totalPaidOutAmount)}</div></div>
      <div class="stat"><div class="label">Pending payout</div><div class="value">${money(summary.totalPendingPayoutAmount)}</div></div>
    </div>
    ${simulated('payouts are tracked, not paid: no money moves in this PoC.')}
    <section>
      <h2>To ship (${toFulfil.length})</h2>
      ${toFulfil.length ? toFulfil.map(orderRow).join('') : '<p class="muted">Nothing waiting to ship.</p>'}
    </section>
    <section>
      <h2>All orders</h2>
      ${orders.content.length ? orders.content.map(orderRow).join('') : '<p class="muted">No orders yet.</p>'}
    </section>`);
}

async function listingsView() {
  loading();
  const [readiness, page] = await Promise.all([sellerReadiness(), api.products({ mine: true, size: 100 })]);
  render(`
    <a href="#/manage" class="back">‹ ${esc(manageLabel())}</a>
    <div class="section-head"><h1>My listings</h1>
      ${readiness.canList ? '<a class="primary" href="#/listing/new">+ New</a>' : ''}</div>
    ${sellerNotice(readiness)}
    ${page.content.length ? page.content.map(p => `
      <div class="row">
        <div>
          <strong>${esc(p.title)}</strong>
          <div class="muted small">${money(p.price)} · ${p.stockQuantity} in stock · ${esc(p.category || '')}</div>
          <div class="actions">
            <a class="secondary" href="#/listing/${esc(p.guid)}">Edit</a>
            <button class="secondary" data-remove="${esc(p.guid)}" data-title="${esc(p.title)}">Remove</button>
          </div>
        </div>
        <span class="status">${esc(p.status.replace(/_/g, ' '))}</span>
      </div>`).join('') : '<p class="muted">You have no listings yet.</p>'}`);
  on('[data-remove]', 'click', async e => {
    if (!confirm(`Remove "${e.target.dataset.title}" from the store?`)) return;
    try {
      await api.removeProduct(e.target.dataset.remove);
      toast('Listing removed', 'success');
      listingsView();
    } catch (error) {
      showError(error);
    }
  });
}

async function listingFormView(guid) {
  loading();
  const existing = guid && guid !== 'new' ? (await api.product(guid)).product : null;
  if (!existing) {
    const readiness = await sellerReadiness();
    if (!readiness.canList) {
      render(`<a href="#/listings" class="back">‹ My listings</a><h1>New listing</h1>${sellerNotice(readiness)}`);
      return;
    }
  }
  const p = existing || { title: '', description: '', price: '', condition: 'NEW', category: '', stockQuantity: 1, status: 'ACTIVE', imageUrls: [] };
  render(`
    <a href="#/listings" class="back">‹ My listings</a>
    <section class="card">
      <h1>${existing ? 'Edit listing' : 'New listing'}</h1>
      <form id="listing-form" class="form">
        <label>Title <input name="title" required maxlength="255" value="${esc(p.title)}"></label>
        <label>Description <textarea name="description" rows="3" maxlength="5000">${esc(p.description)}</textarea></label>
        <label>Price (R) <input name="price" type="number" min="0.01" step="0.01" required value="${esc(p.price)}"></label>
        <label>Condition
          <select name="condition">
            <option value="NEW" ${p.condition === 'NEW' ? 'selected' : ''}>New</option>
            <option value="SECOND_HAND" ${p.condition === 'SECOND_HAND' ? 'selected' : ''}>Second-hand</option>
          </select>
        </label>
        <label>Category
          <select name="category">${PRODUCT_CATEGORIES.map(c => `<option ${c === p.category ? 'selected' : ''}>${esc(c)}</option>`).join('')}</select>
        </label>
        <label>Stock <input name="stockQuantity" type="number" min="0" step="1" required value="${esc(p.stockQuantity)}"></label>
        ${existing ? `<label>Status
          <select name="status">
            <option value="ACTIVE" ${p.status !== 'DRAFT' ? 'selected' : ''}>Active (visible)</option>
            <option value="DRAFT" ${p.status === 'DRAFT' ? 'selected' : ''}>Draft (hidden)</option>
          </select></label>` : ''}
        <label>Image URL (optional) <input name="imageUrl" type="url" maxlength="500" value="${esc(p.imageUrls?.[0] || '')}"></label>
        <button class="primary" type="submit">${existing ? 'Save changes' : 'List item'}</button>
      </form>
    </section>`);
  on('#listing-form', 'submit', async e => {
    e.preventDefault();
    const form = Object.fromEntries(new FormData(e.target));
    const body = {
      title: form.title.trim(),
      description: form.description.trim(),
      price: Number(form.price),
      condition: form.condition,
      category: form.category,
      stockQuantity: Number(form.stockQuantity),
      imageUrls: form.imageUrl.trim() ? [form.imageUrl.trim()] : []
    };
    if (existing) body.status = form.status;
    try {
      if (existing) await api.updateProduct(existing.guid, body);
      else await api.createProduct(body);
      toast(existing ? 'Listing updated' : 'Item listed for sale', 'success');
      location.hash = '#/listings';
    } catch (error) {
      showError(error);
    }
  });
}

async function bankingView() {
  loading();
  const banking = await api.bankingDetails().catch(error => { if (error.status === 404) return null; throw error; });
  const b = banking || { accountHolderName: session().name, bankName: '', branchCode: '', accountType: 'CHEQUE' };
  render(`
    <a href="#/manage" class="back">‹ ${esc(manageLabel())}</a>
    <section class="card">
      <h1>Banking details</h1>
      <p class="muted small">${banking ? `On file: ${esc(banking.bankName)} ${esc(banking.maskedAccountNumber)}. Saving replaces them.`
        : 'Required before you can list items for sale. Payouts go to this account.'}</p>
      <form id="banking-form" class="form">
        <label>Account holder <input name="accountHolderName" required maxlength="150" value="${esc(b.accountHolderName)}"></label>
        <label>Bank <input name="bankName" required maxlength="100" value="${esc(b.bankName)}"></label>
        <label>Account number <input name="accountNumber" required inputmode="numeric" pattern="\\d{6,20}" title="6-20 digits"
          placeholder="${banking ? 'Re-enter to change' : '6-20 digits'}"></label>
        <label>Branch code <input name="branchCode" required maxlength="20" value="${esc(b.branchCode)}"></label>
        <label>Account type
          <select name="accountType">
            <option value="CHEQUE" ${b.accountType === 'CHEQUE' ? 'selected' : ''}>Cheque</option>
            <option value="SAVINGS" ${b.accountType === 'SAVINGS' ? 'selected' : ''}>Savings</option>
          </select>
        </label>
        <button class="primary" type="submit">Save banking details</button>
      </form>
      <p class="muted small">Your account number is encrypted at rest and only ever shown masked.</p>
    </section>`);
  on('#banking-form', 'submit', async e => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target));
    Object.keys(body).forEach(k => { body[k] = body[k].trim(); });
    try {
      await api.saveBankingDetails(body);
      toast('Banking details saved', 'success');
      location.hash = '#/manage';
    } catch (error) {
      showError(error);
    }
  });
}

async function eventsView() {
  loading();
  const page = await api.activities({ mine: true, size: 100 });
  render(`
    <a href="#/manage" class="back">‹ ${esc(manageLabel())}</a>
    <div class="section-head"><h1>My events</h1><a class="primary" href="#/event/new">+ New</a></div>
    ${page.content.length ? page.content.map(a => `
      <div class="row">
        <div>
          <strong>${esc(a.title)}</strong>
          <div class="muted small">📅 ${date(a.activityDate)} · 📍 ${esc(a.location || 'TBC')}</div>
          ${a.status === 'CANCELLED' ? '' : `<div class="actions">
            <a class="secondary" href="#/event/${esc(a.guid)}">Edit</a>
            <button class="secondary" data-cancel="${esc(a.guid)}" data-title="${esc(a.title)}">Cancel event</button>
          </div>`}
        </div>
        <span class="status status-${esc(a.status)}">${esc(a.status.replace(/_/g, ' '))}</span>
      </div>`).join('') : '<p class="muted">You haven’t posted any events yet.</p>'}
    <p class="muted small">New and edited events are checked by an admin before they appear on the bulletin.</p>`);
  on('[data-cancel]', 'click', async e => {
    if (!confirm(`Cancel "${e.target.dataset.title}"?`)) return;
    try {
      await api.cancelActivity(e.target.dataset.cancel);
      toast('Event cancelled', 'success');
      eventsView();
    } catch (error) {
      showError(error);
    }
  });
}

// <input type="datetime-local"> wants local "YYYY-MM-DDTHH:mm"; the API stores local date-times too.
const toLocalInput = value => (value || '').slice(0, 16);

async function eventFormView(guid) {
  loading();
  const existing = guid && guid !== 'new' ? (await api.activity(guid)).activity : null;
  const me = existing ? null : await api.me();
  const a = existing || { title: '', description: '', activityDate: '', location: '' };
  const minDate = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  render(`
    <a href="#/events" class="back">‹ My events</a>
    <section class="card">
      <h1>${existing ? 'Edit event' : 'Post an event'}</h1>
      ${me ? organiserNotice(me) : ''}
      <form id="event-form" class="form">
        <label>Title <input name="title" required maxlength="255" value="${esc(a.title)}"></label>
        <label>Description <textarea name="description" rows="3" maxlength="5000">${esc(a.description)}</textarea></label>
        <label>Date &amp; time <input name="activityDate" type="datetime-local" required min="${minDate}" value="${esc(toLocalInput(a.activityDate))}"></label>
        <label>Location <input name="location" maxlength="255" value="${esc(a.location)}"></label>
        <button class="primary" type="submit">${existing ? 'Save changes' : 'Publish event'}</button>
      </form>
    </section>`);
  on('#event-form', 'submit', async e => {
    e.preventDefault();
    const form = Object.fromEntries(new FormData(e.target));
    const body = {
      title: form.title.trim(),
      description: form.description.trim(),
      activityDate: `${form.activityDate}:00`,
      location: form.location.trim()
    };
    try {
      if (existing) await api.updateActivity(existing.guid, body);
      else await api.createActivity(body);
      toast(existing && existing.status === 'PENDING_APPROVAL' ? 'Event updated'
        : 'Submitted — the event appears on the bulletin once an admin approves it', 'success');
      location.hash = '#/events';
    } catch (error) {
      showError(error);
    }
  });
}

// ---------------------------------------------------------------------------------------------- admin

const ACCOUNT_STATUSES = ['ACTIVE', 'SUSPENDED', 'DEACTIVATED'];
const ALL_ROLES = ['STUDENT', 'VENDOR', 'COMMUNITY_LEADER', 'FACULTY', 'ADMIN'];
const label = value => esc(String(value ?? '').replace(/_/g, ' '));
const rating = (avg, count) => count ? `${Number(avg).toFixed(1)}★ <span class="muted">(${count})</span>` : '<span class="muted">no reviews</span>';
const statCard = (title, value, href) => `<${href ? `a href="${href}"` : 'div'} class="stat"><div class="label">${esc(title)}</div><div class="value">${value}</div></${href ? 'a' : 'div'}>`;
const adminBack = (href = '#/admin', text = 'Admin') => `<a href="${href}" class="back">‹ ${esc(text)}</a>`;

async function adminAttention() {
  const [summary, queue] = await Promise.all([api.admin.salesSummary(), api.admin.users({ status: 'PENDING_REVIEW', size: 1 })]);
  return { summary, pendingVerifications: queue.totalElements, lowRated: summary.vendors.filter(v => v.lowRating) };
}

function attentionList({ summary, pendingVerifications, lowRated }) {
  const items = [
    [pendingVerifications, 'account(s) awaiting verification', '#/admin-queue'],
    [summary.ordersAwaitingRelease, 'order(s) awaiting fund release or dispute resolution', '#/admin-orders'],
    [summary.eventsAwaitingApproval, 'event(s) awaiting approval', '#/admin-events'],
    [lowRated.length, 'seller(s) with a low rating', '#/admin-sales?filter=low']
  ].filter(([count]) => count > 0);
  return items.length
    ? items.map(([count, text, href]) => `<a class="row attention" href="${href}"><span><strong>${count}</strong> ${esc(text)}</span><span>›</span></a>`).join('')
    : '<p class="muted">Nothing needs your attention. 🎉</p>';
}

async function adminView() {
  loading();
  const attention = await adminAttention();
  const s = attention.summary;
  render(`
    <h1>Admin</h1>
    <section>
      <h2>Needs your attention</h2>
      ${attentionList(attention)}
    </section>
    <section>
      <div class="section-head"><h2>Platform sales</h2><a href="#/admin-sales">By vendor</a></div>
      <div class="stats">
        ${statCard('Total sales', money(s.totalSalesAmount), '#/admin-sales')}
        ${statCard('Orders', s.totalOrders, '#/admin-orders?filter=all')}
        ${statCard('Paid out', money(s.totalPaidOutAmount))}
        ${statCard('Pending payout', money(s.totalPendingPayoutAmount))}
      </div>
    </section>
    <section>
      <h2>Manage</h2>
      <div class="tiles">
        ${tile('#/admin-queue', '✅', 'Verification queue', 'Review new vendors, faculty and community leaders')}
        ${tile('#/admin-users', '👥', 'Users', 'Search users, change account status and roles')}
        ${tile('#/admin-sales', '📊', 'Sales dashboard', 'Platform totals and a breakdown per vendor')}
        ${tile('#/admin-orders', '💸', 'Orders & payouts', 'Release escrowed funds, settle disputes')}
        ${tile('#/admin-events', '📣', 'Events & reviews', 'Approve event posts, see ratings and reviews')}
      </div>
    </section>`);
}

// --- users -----------------------------------------------------------------------------------

function userRow(u) {
  return `
    <a class="row" href="#/admin-user/${esc(u.guid)}">
      <div>
        <strong>${esc(u.firstName)} ${esc(u.lastName)}</strong>
        <div class="muted small">${esc(u.email)}</div>
        <div class="muted small">${u.roles.map(r => ROLE_LABELS[r] || r).join(' · ')}${u.businessRegistrationNumber ? ` · Business no. ${esc(u.businessRegistrationNumber)}` : ''}</div>
      </div>
      <div class="badges">
        <span class="status status-${esc(u.status)}">${label(u.status)}</span>
        <span class="status status-${esc(u.verificationStatus)}">${label(u.verificationStatus)}</span>
      </div>
    </a>`;
}

async function adminQueueView() {
  loading();
  const page = await api.admin.users({ status: 'PENDING_REVIEW', size: 100 });
  render(`
    ${adminBack()}
    <h1>Verification queue</h1>
    ${simulated('no university or business registry is consulted; verification is your manual decision.')}
    ${page.content.length ? page.content.map(u => `
      <div class="row">
        <div>
          <strong>${esc(u.firstName)} ${esc(u.lastName)}</strong> <span class="muted small">${esc(ROLE_LABELS[u.userType] || u.userType)}</span>
          <div class="muted small">${esc(u.email)} · registered ${date(u.createdAt)}</div>
          ${u.businessRegistrationNumber ? `<div class="small">Business no. <strong>${esc(u.businessRegistrationNumber)}</strong></div>` : ''}
          <div class="actions">
            <button class="primary" data-verify="${esc(u.guid)}">Verify</button>
            <button class="secondary" data-reject="${esc(u.guid)}" data-name="${esc(u.email)}">Reject</button>
            <a class="secondary" href="#/admin-user/${esc(u.guid)}">Details</a>
          </div>
        </div>
      </div>`).join('') : '<p class="muted">No accounts are waiting for review.</p>'}`);
  on('[data-verify]', 'click', async e => {
    try {
      await api.admin.verify(e.target.dataset.verify);
      toast('Account verified', 'success');
      adminQueueView();
    } catch (error) { showError(error); }
  });
  on('[data-reject]', 'click', async e => {
    const reason = prompt(`Reason for rejecting ${e.target.dataset.name}:`);
    if (!reason?.trim()) return;
    try {
      await api.admin.reject(e.target.dataset.reject, reason.trim());
      toast('Account rejected', 'success');
      adminQueueView();
    } catch (error) { showError(error); }
  });
}

async function adminUsersView(params) {
  const filters = { q: params.get('q') || '', role: params.get('role') || '', accountStatus: params.get('accountStatus') || '', status: params.get('status') || '' };
  render(`
    ${adminBack()}
    <h1>Users</h1>
    <form id="user-search" class="search">
      <input name="q" type="search" placeholder="🔍 Email or name starts with…" value="${esc(filters.q)}">
      <div class="filters three">
        <select name="role"><option value="">Any role</option>${ALL_ROLES.map(r => `<option value="${r}" ${r === filters.role ? 'selected' : ''}>${ROLE_LABELS[r]}</option>`).join('')}</select>
        <select name="accountStatus"><option value="">Any account status</option>${['PENDING', ...ACCOUNT_STATUSES].map(s => `<option value="${s}" ${s === filters.accountStatus ? 'selected' : ''}>${label(s)}</option>`).join('')}</select>
        <select name="status"><option value="">Any verification</option>${['UNVERIFIED', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED'].map(s => `<option value="${s}" ${s === filters.status ? 'selected' : ''}>${label(s)}</option>`).join('')}</select>
      </div>
    </form>
    <div id="results"><p class="muted center">Loading…</p></div>`);
  const submit = () => {
    const next = new URLSearchParams([...new FormData(view.querySelector('#user-search'))].filter(([, v]) => v));
    location.hash = `#/admin-users${next.toString() ? `?${next}` : ''}`;
  };
  on('#user-search', 'submit', e => { e.preventDefault(); submit(); });
  on('#user-search select', 'change', submit);
  const page = await api.admin.users({ ...filters, size: 100, sort: 'createdAt,desc' });
  view.querySelector('#results').innerHTML = `<p class="muted small">${page.totalElements} user(s)</p>`
    + (page.content.length ? page.content.map(userRow).join('') : '<p class="muted center">No users match.</p>');
}

async function adminUserView(guid) {
  loading();
  const detail = await api.admin.user(guid);
  const u = detail.user;
  const self = u.guid === session().guid;
  const seller = u.roles.includes('VENDOR') || u.roles.includes('FACULTY');
  const p = detail.payoutSummary;
  render(`
    ${adminBack('#/admin-users', 'Users')}
    <section class="card">
      <h1>${esc(u.firstName)} ${esc(u.lastName)}</h1>
      <p class="muted">${esc(u.email)}</p>
      <div class="badges">
        <span class="status status-${esc(u.status)}">${label(u.status)}</span>
        <span class="status status-${esc(u.verificationStatus)}">${label(u.verificationStatus)}</span>
      </div>
      <dl class="facts">
        <dt>Registered as</dt><dd>${esc(ROLE_LABELS[u.userType] || u.userType || '—')}</dd>
        ${u.businessRegistrationNumber ? `<dt>Business no.</dt><dd>${esc(u.businessRegistrationNumber)}</dd>` : ''}
        <dt>Joined</dt><dd>${date(u.createdAt)}</dd>
        <dt>Last login</dt><dd>${date(detail.lastLoginAt) || 'Never'}</dd>
        ${seller ? `<dt>Banking</dt><dd>${detail.bankingDetails ? `${esc(detail.bankingDetails.bankName)} ${esc(detail.bankingDetails.maskedAccountNumber)}` : 'Not on file'}</dd>` : ''}
      </dl>
      ${u.verificationStatus === 'PENDING_REVIEW' || u.verificationStatus === 'REJECTED' ? `
        <div class="actions">
          <button class="primary" id="verify">Verify account</button>
          ${u.verificationStatus === 'PENDING_REVIEW' ? '<button class="secondary" id="reject">Reject</button>' : ''}
        </div>` : ''}
    </section>
    ${seller ? `
    <section>
      <div class="section-head"><h2>Sales</h2><a href="#/admin-vendor/${esc(u.guid)}">Full dashboard</a></div>
      <div class="stats">
        ${statCard('Total sales', money(p.totalSalesAmount))}
        ${statCard('Pending payout', money(p.totalPendingPayoutAmount))}
      </div>
    </section>` : ''}
    <section class="card">
      <h2>Account status</h2>
      ${self ? '<p class="muted small">You can’t change the status of your own account.</p>' : `
      <form id="status-form" class="form">
        <label>Status
          <select name="status">${ACCOUNT_STATUSES.map(s => `<option value="${s}" ${s === u.status ? 'selected' : ''}>${label(s)}</option>`).join('')}</select>
        </label>
        <label>Reason (sent to the user) <input name="reason" maxlength="500" placeholder="e.g. Repeated low ratings"></label>
        <button class="primary" type="submit">Update status</button>
        <p class="muted small">Suspended and deactivated users can’t log in. Sessions already open end when their token expires (within an hour).</p>
      </form>`}
    </section>
    <section class="card">
      <h2>Roles</h2>
      <form id="roles-form" class="form">
        <div class="checks">${ALL_ROLES.map(r => `
          <label class="check"><input type="checkbox" name="roles" value="${r}" ${u.roles.includes(r) ? 'checked' : ''}
            ${self && r === 'ADMIN' ? 'disabled' : ''}> ${ROLE_LABELS[r]}</label>`).join('')}</div>
        <button class="primary" type="submit">Save roles</button>
        <p class="muted small">Changes apply from the user’s next login.</p>
      </form>
    </section>`);
  on('#verify', 'click', async () => {
    try { await api.admin.verify(guid); toast('Account verified', 'success'); adminUserView(guid); } catch (error) { showError(error); }
  });
  on('#reject', 'click', async () => {
    const reason = prompt('Reason for rejecting this account:');
    if (!reason?.trim()) return;
    try { await api.admin.reject(guid, reason.trim()); toast('Account rejected', 'success'); adminUserView(guid); } catch (error) { showError(error); }
  });
  on('#status-form', 'submit', async e => {
    e.preventDefault();
    const form = new FormData(e.target);
    const status = form.get('status');
    if (status !== 'ACTIVE' && !confirm(`${label(status)} ${u.email}? They won’t be able to log in.`)) return;
    try { await api.admin.setStatus(guid, status, form.get('reason').trim()); toast('Status updated', 'success'); adminUserView(guid); } catch (error) { showError(error); }
  });
  on('#roles-form', 'submit', async e => {
    e.preventDefault();
    const roles = [...e.target.querySelectorAll('input[name=roles]')].filter(i => i.checked).map(i => i.value);
    if (self) roles.push('ADMIN');
    if (!roles.length) { toast('Select at least one role', 'error'); return; }
    try { await api.admin.setRoles(guid, [...new Set(roles)]); toast('Roles updated', 'success'); adminUserView(guid); } catch (error) { showError(error); }
  });
}

// --- sales -----------------------------------------------------------------------------------

async function adminSalesView(params) {
  loading();
  const s = await api.admin.salesSummary();
  const lowOnly = params.get('filter') === 'low';
  const vendors = lowOnly ? s.vendors.filter(v => v.lowRating) : s.vendors;
  render(`
    ${adminBack()}
    <h1>Sales dashboard</h1>
    <div class="stats">
      ${statCard('Total sales', money(s.totalSalesAmount))}
      ${statCard('Orders', s.totalOrders, '#/admin-orders?filter=all')}
      ${statCard('Paid out', money(s.totalPaidOutAmount))}
      ${statCard('Pending payout', money(s.totalPendingPayoutAmount))}
      ${statCard('Refunded', money(s.totalRefundedAmount))}
      ${statCard('Awaiting your action', s.ordersAwaitingRelease, '#/admin-orders')}
    </div>
    <p class="muted small">By status: ${Object.entries(s.ordersByStatus).map(([k, v]) => `${label(k)} ${v}`).join(' · ') || 'no orders yet'}</p>
    ${simulated('payouts are tracked, not paid: no money moves in this PoC.')}
    <div class="section-head"><h2>By vendor${lowOnly ? ' — low rating' : ''}</h2>
      <a href="#/admin-sales${lowOnly ? '' : '?filter=low'}">${lowOnly ? 'Show all' : 'Low ratings only'}</a></div>
    ${vendors.length ? vendors.map(v => `
      <a class="row" href="#/admin-vendor/${esc(v.vendorUserGuid)}">
        <div>
          <strong>${esc(v.vendorName)}</strong> ${v.lowRating ? '<span class="status status-DISPUTED">LOW RATING</span>' : ''}
          ${v.accountStatus && v.accountStatus !== 'ACTIVE' ? `<span class="status status-${esc(v.accountStatus)}">${label(v.accountStatus)}</span>` : ''}
          <div class="muted small">${v.totalOrders} orders · ${v.activeListings} active listings · ${rating(v.averageRating, v.reviewCount)}</div>
          <div class="small">Paid ${money(v.totalPaidOutAmount)} · Pending ${money(v.totalPendingPayoutAmount)}${Number(v.totalRefundedAmount) ? ` · Refunded ${money(v.totalRefundedAmount)}` : ''}</div>
        </div>
        <span class="price">${money(v.totalSalesAmount)}</span>
      </a>`).join('') : '<p class="muted">No vendors match.</p>'}`);
}

function adminOrderRow(o) {
  const canRelease = ['SHIPPED', 'DELIVERED_CONFIRMED', 'DISPUTED'].includes(o.escrowStatus);
  return `
    <div class="row">
      <div>
        <a href="#/order/${esc(o.guid)}"><strong>${esc(o.productTitle || 'Product')}</strong></a>
        <div class="muted small">${esc(o.escrowReferenceNumber)} · ${o.quantity} × ${money(o.unitPrice)} = ${money(o.totalAmount)} · ${date(o.createdAt)}</div>
        ${canRelease ? `<div class="actions">
          <button class="primary" data-release="${esc(o.guid)}" data-ref="${esc(o.escrowReferenceNumber)}">Release funds</button>
          ${o.escrowStatus === 'DISPUTED' ? `<button class="secondary" data-refund="${esc(o.guid)}" data-ref="${esc(o.escrowReferenceNumber)}">Refund buyer</button>` : ''}
        </div>` : ''}
      </div>
      <span class="status status-${esc(o.escrowStatus)}">${label(o.escrowStatus)}</span>
    </div>`;
}

function bindOrderActions(reload) {
  on('[data-release]', 'click', async e => {
    if (!confirm(`Release the escrowed funds for ${e.target.dataset.ref} to the seller?`)) return;
    try { await api.admin.release(e.target.dataset.release); toast('Funds released', 'success'); reload(); } catch (error) { showError(error); }
  });
  on('[data-refund]', 'click', async e => {
    if (!confirm(`Refund the buyer for ${e.target.dataset.ref}?`)) return;
    try { await api.admin.refund(e.target.dataset.refund); toast('Buyer refunded', 'success'); reload(); } catch (error) { showError(error); }
  });
}

async function adminVendorView(guid) {
  loading();
  const [s, orders, products, reviews] = await Promise.all([
    api.admin.vendorSummary(guid), api.admin.orders({ vendor: guid }), api.admin.vendorProducts(guid), api.admin.vendorReviews(guid)]);
  render(`
    ${adminBack('#/admin-sales', 'Sales dashboard')}
    <h1>${esc(s.vendorName)}</h1>
    <p>${rating(s.averageRating, s.reviewCount)} ${s.lowRating ? '<span class="status status-DISPUTED">LOW RATING</span>' : ''}
      · <a href="#/admin-user/${esc(guid)}">Account &amp; status</a></p>
    <div class="stats">
      ${statCard('Total sales', money(s.totalSalesAmount))}
      ${statCard('Orders', s.totalOrders)}
      ${statCard('Paid out', money(s.totalPaidOutAmount))}
      ${statCard('Pending payout', money(s.totalPendingPayoutAmount))}
      ${statCard('Refunded', money(s.totalRefundedAmount))}
      ${statCard('Active listings', s.activeListings)}
    </div>
    <section><h2>Orders</h2>${orders.content.length ? orders.content.map(adminOrderRow).join('') : '<p class="muted">No orders yet.</p>'}</section>
    <section><h2>Reviews (${reviews.totalElements})</h2>
      ${reviews.content.length ? reviews.content.map(r => `
        <div class="review">
          <div>${stars(r.rating)} <span class="muted small">on ${esc(r.subjectTitle || 'a product')}</span></div>
          <p>${esc(r.comment || '')}</p>
          <div class="muted small">${esc(r.reviewerName)} · ${date(r.createdAt)}</div>
        </div>`).join('') : '<p class="muted">No reviews yet.</p>'}
    </section>
    <section><h2>Listings</h2>
      ${products.content.length ? products.content.map(p => `
        <a class="row" href="#/product/${esc(p.guid)}"><div><strong>${esc(p.title)}</strong>
          <div class="muted small">${money(p.price)} · ${p.stockQuantity} in stock</div></div>
          <span class="status">${label(p.status)}</span></a>`).join('') : '<p class="muted">No listings.</p>'}
    </section>`);
  bindOrderActions(() => adminVendorView(guid));
}

const ORDER_FILTERS = {
  attention: { title: 'Needs your action', statuses: ['DELIVERED_CONFIRMED', 'DISPUTED'] },
  shipped: { title: 'Shipped, not yet confirmed', statuses: ['SHIPPED'] },
  released: { title: 'Funds released', statuses: ['FUNDS_RELEASED'] },
  all: { title: 'All orders', statuses: [] }
};

async function adminOrdersView(params) {
  loading();
  const key = ORDER_FILTERS[params.get('filter')] ? params.get('filter') : 'attention';
  const page = await api.admin.orders({ statuses: ORDER_FILTERS[key].statuses });
  render(`
    ${adminBack()}
    <h1>Orders &amp; payouts</h1>
    <div class="chips">${Object.entries(ORDER_FILTERS).map(([k, f]) =>
      `<a class="chip ${k === key ? 'active' : ''}" href="#/admin-orders?filter=${k}">${esc(f.title)}</a>`).join('')}</div>
    <p class="muted small">Funds stay in escrow until you release them. For a shipped order the buyer never confirmed, you can release after checking with both parties.</p>
    ${page.content.length ? page.content.map(adminOrderRow).join('') : '<p class="muted">No orders here.</p>'}`);
  bindOrderActions(() => adminOrdersView(params));
}

// --- events ----------------------------------------------------------------------------------

const EVENT_FILTERS = [['PENDING_APPROVAL', 'Awaiting approval'], ['PUBLISHED', 'Published'], ['COMPLETED', 'Completed'],
  ['REJECTED', 'Rejected'], ['CANCELLED', 'Cancelled'], ['', 'All']];

async function adminEventsView(params) {
  loading();
  const status = params.has('status') ? params.get('status') : 'PENDING_APPROVAL';
  const page = await api.admin.activities({ status, size: 100 });
  render(`
    ${adminBack()}
    <h1>Events &amp; reviews</h1>
    <div class="chips">${EVENT_FILTERS.map(([value, title]) =>
      `<a class="chip ${value === status ? 'active' : ''}" href="#/admin-events?status=${value}">${esc(title)}</a>`).join('')}</div>
    ${page.content.length ? page.content.map(({ activity: a, organiserName, averageRating, reviewCount }) => `
      <div class="row">
        <div>
          <a href="#/admin-event/${esc(a.guid)}"><strong>${esc(a.title)}</strong></a>
          <div class="muted small">📅 ${date(a.activityDate)} · 📍 ${esc(a.location || 'TBC')}</div>
          <div class="muted small">By ${esc(organiserName)} · ${rating(averageRating, reviewCount)}</div>
          ${a.status === 'PENDING_APPROVAL' ? `<div class="actions">
            <button class="primary" data-approve="${esc(a.guid)}">Approve</button>
            <button class="secondary" data-reject-event="${esc(a.guid)}">Reject</button></div>` : ''}
        </div>
        <span class="status status-${esc(a.status)}">${label(a.status)}</span>
      </div>`).join('') : '<p class="muted">No events here.</p>'}`);
  bindEventActions(() => adminEventsView(params));
}

function bindEventActions(reload) {
  on('[data-approve]', 'click', async e => {
    try { await api.admin.approveActivity(e.target.dataset.approve); toast('Event approved and published', 'success'); reload(); } catch (error) { showError(error); }
  });
  on('[data-reject-event]', 'click', async e => {
    const reason = prompt('Reason for rejecting this event (sent to the organiser):');
    if (!reason?.trim()) return;
    try { await api.admin.rejectActivity(e.target.dataset.rejectEvent, reason.trim()); toast('Event rejected', 'success'); reload(); } catch (error) { showError(error); }
  });
}

async function adminEventView(guid) {
  loading();
  const [detail, reviews] = await Promise.all([api.activity(guid), api.admin.activityReviews(guid)]);
  const a = detail.activity;
  render(`
    ${adminBack('#/admin-events?status=', 'Events')}
    <section class="card">
      <h1>${esc(a.title)}</h1>
      <span class="status status-${esc(a.status)}">${label(a.status)}</span>
      <p class="muted">📅 ${date(a.activityDate)} · 📍 ${esc(a.location || 'TBC')}</p>
      <p>${esc(a.description)}</p>
      <p><a href="#/admin-user/${esc(a.organiserUserGuid)}">Organiser account</a> · ${rating(detail.averageRating, detail.reviewCount)}</p>
      ${a.status === 'PENDING_APPROVAL' ? `<div class="actions">
        <button class="primary" data-approve="${esc(a.guid)}">Approve</button>
        <button class="secondary" data-reject-event="${esc(a.guid)}">Reject</button></div>` : ''}
    </section>
    <section><h2>Reviews (${reviews.totalElements})</h2>
      ${reviews.content.length ? reviews.content.map(r => `
        <div class="review"><div>${stars(r.rating)}</div><p>${esc(r.comment || '')}</p>
          <div class="muted small">${esc(r.reviewerName)} · ${date(r.createdAt)}</div></div>`).join('') : '<p class="muted">No reviews yet.</p>'}
    </section>`);
  bindEventActions(() => adminEventView(guid));
}

// ---------------------------------------------------------------------------------------------- router

const PUBLIC_ROUTES = { login: loginView, register: registerView, verify: verifyView };
const ROUTES = {
  home: homeView,
  browse: browseView,
  product: productView,
  cart: cartView,
  order: orderView,
  orders: ordersView,
  bulletin: bulletinView,
  activity: activityView,
  inbox: inboxView,
  manage: manageView,
  sales: salesView,
  listings: listingsView,
  listing: listingFormView,
  banking: bankingView,
  events: eventsView,
  event: eventFormView,
  admin: adminView,
  'admin-queue': adminQueueView,
  'admin-users': adminUsersView,
  'admin-user': adminUserView,
  'admin-sales': adminSalesView,
  'admin-vendor': adminVendorView,
  'admin-orders': adminOrdersView,
  'admin-events': adminEventsView,
  'admin-event': adminEventView
};

// Routes that need a particular role; anyone else is sent home.
const ROLE_ROUTES = {
  manage: () => canSell() || canOrganise(),
  sales: canSell,
  listings: canSell,
  listing: canSell,
  banking: canSell,
  events: canOrganise,
  event: canOrganise,
  cart: canBuy
};
Object.keys(ROUTES).filter(name => name.startsWith('admin')).forEach(name => { ROLE_ROUTES[name] = isAdmin; });

function syncChrome(loggedIn) {
  const badge = document.getElementById('role-badge');
  const role = loggedIn ? primaryRole() : null;
  badge.hidden = !role;
  if (role) {
    badge.textContent = ROLE_LABELS[role];
    badge.className = `role-badge ${role}`;
  }
  const manages = loggedIn && (canSell() || canOrganise());
  document.getElementById('manage-tab').hidden = !manages;
  if (manages) {
    document.getElementById('manage-label').textContent = manageLabel();
    document.getElementById('manage-icon').textContent = canSell() && canOrganise() ? '🧰' : canSell() ? '🏪' : '📣';
  }
  document.getElementById('cart-tab').hidden = loggedIn && !canBuy();
  document.getElementById('admin-tab').hidden = !(loggedIn && isAdmin());
}

const MANAGE_TABS = ['manage', 'sales', 'listings', 'listing', 'banking', 'events', 'event'];

async function route() {
  const [path, query] = location.hash.replace(/^#\/?/, '').split('?');
  const [name, arg] = path.split('/');
  const params = new URLSearchParams(query || '');
  const loggedIn = Boolean(session());

  tabbar.hidden = !loggedIn;
  logoutButton.hidden = !loggedIn;
  syncChrome(loggedIn);
  const activeTab = MANAGE_TABS.includes(name) ? 'manage' : name.startsWith('admin') ? 'admin' : name;
  document.querySelectorAll('#tabbar a').forEach(a => a.classList.toggle('active', a.dataset.tab === activeTab));

  if (PUBLIC_ROUTES[name]) {
    if (loggedIn) { location.hash = '#/home'; return; }
    PUBLIC_ROUTES[name](params);
    return;
  }
  if (!loggedIn) { location.hash = '#/login'; return; }
  const handler = ROUTES[name];
  if (!handler || (ROLE_ROUTES[name] && !ROLE_ROUTES[name]())) { location.hash = '#/home'; return; }
  try {
    await handler(arg ?? params, params);
  } catch (error) {
    showError(error);
    render(`<p class="muted center">${esc(error.message)}</p>`);
  }
  updateBadges();
}

logoutButton.addEventListener('click', async () => {
  await logout();
  location.hash = '#/login';
});
window.addEventListener('cs:logged-out', () => { location.hash = '#/login'; });
window.addEventListener('hashchange', route);
route();
