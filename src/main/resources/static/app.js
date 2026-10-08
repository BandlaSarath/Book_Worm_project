/* ═══════════════════════════════════════════════════════════════════════════
   Book Worm — App.js
   Vanilla JS SPA served by Spring Boot from /static/
   All API calls use relative paths (no hardcoded host/port).
═══════════════════════════════════════════════════════════════════════════ */

/* ── State ────────────────────────────────────────────────────────────── */
const State = {
  token: sessionStorage.getItem('bw_token') || null,
  user:  JSON.parse(sessionStorage.getItem('bw_user') || 'null'),
  cart:  null,           // CartResponse from backend
  pendingCartBook: null, // book to add after login
  checkoutOrder:  null,  // OrderResponse after placeOrder
  checkoutPayment:null,  // PaymentInitiateResponse
  confirmation:   null,  // PurchaseConfirmationResponse
};

/* ── API helpers ──────────────────────────────────────────────────────── */
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (State.token) headers['Authorization'] = 'Bearer ' + State.token;
  const opts = { method, headers };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const msg = (data && (data.message || data.error)) || `HTTP ${res.status}`;
    throw Object.assign(new Error(msg), { status: res.status, data });
  }
  return data;
}
const GET    = p => api('GET',    p);
const POST   = (p,b) => api('POST',   p, b);
const PUT    = (p,b) => api('PUT',    p, b);
const PATCH  = (p,b) => api('PATCH',  p, b);
const DELETE = p => api('DELETE', p);

/* ── Toast ────────────────────────────────────────────────────────────── */
function toast(msg, type = '') {
  const c = document.getElementById('toasts');
  const t = document.createElement('div');
  t.className = 'toast' + (type ? ' ' + type : '');
  t.textContent = msg;
  c.appendChild(t);
  setTimeout(() => t.remove(), 3500);
}

/* ── Colour palette for placeholder covers ────────────────────────────── */
const COVER_COLORS = [
  '#1a3a5c','#2a5a8c','#7c3aed','#be185d',
  '#c85e1a','#0f766e','#4338ca','#b45309',
];
function coverColor(title) {
  let h = 0;
  for (let i = 0; i < title.length; i++) h = (h * 31 + title.charCodeAt(i)) >>> 0;
  return COVER_COLORS[h % COVER_COLORS.length];
}
function coverEl(book, cls = 'book-cover') {
  const div = document.createElement('div');
  div.className = cls;
  if (book.coverImageUrl) {
    const img = document.createElement('img');
    img.src = book.coverImageUrl;
    img.alt = book.title;
    img.onerror = () => { img.remove(); div.textContent = book.title; };
    div.appendChild(img);
  } else {
    div.style.background = coverColor(book.title);
    div.textContent = book.title;
  }
  return div;
}

/* ── Cart badge ───────────────────────────────────────────────────────── */
async function refreshCartBadge() {
  if (!State.token) { document.getElementById('cartBadge').textContent = '0'; return; }
  try {
    State.cart = await GET('/api/v1/cart');
    const count = State.cart.items ? State.cart.items.reduce((s,i)=>s+i.quantity,0) : 0;
    document.getElementById('cartBadge').textContent = count;
  } catch { /* ignore */ }
}

/* ── Auth header ──────────────────────────────────────────────────────── */
function updateAuthUI() {
  const loginBtn  = document.getElementById('navLoginBtn');
  const logoutBtn = document.getElementById('navLogoutBtn');
  const userSpan  = document.getElementById('navUser');
  if (State.token && State.user) {
    loginBtn.classList.add('hidden');
    logoutBtn.classList.remove('hidden');
    userSpan.textContent = `Hi, ${State.user.firstName}`;
    userSpan.classList.remove('hidden');
  } else {
    loginBtn.classList.remove('hidden');
    logoutBtn.classList.add('hidden');
    userSpan.classList.add('hidden');
  }
}

function logout() {
  State.token = null; State.user = null; State.cart = null;
  sessionStorage.removeItem('bw_token');
  sessionStorage.removeItem('bw_user');
  updateAuthUI();
  refreshCartBadge();
  toast('Logged out');
  showPage('home', {});
}

/* ── Page routing ─────────────────────────────────────────────────────── */
const PAGES = ['home','detail','cart','auth','address','payment','confirmation','orders','categories'];
function showPage(name, params) {
  PAGES.forEach(p => document.getElementById('page-'+p).classList.add('hidden'));
  document.getElementById('page-'+name).classList.remove('hidden');
  document.querySelectorAll('.nav-links button').forEach(b => {
    b.classList.toggle('active', b.dataset.page === name);
  });
  window.scrollTo(0,0);
  if (name === 'home')         loadHome(params);
  if (name === 'categories')   loadCategories();
  if (name === 'detail')       loadDetail(params);
  if (name === 'cart')         loadCart();
  if (name === 'auth')         showAuth(params);
  if (name === 'address')      loadAddress();
  if (name === 'orders')       loadOrders();
  if (name === 'confirmation') showConfirmation(params);
}

/* ══════════════════════════════════════════════════════════════════════════
   HOME / CATALOGUE
══════════════════════════════════════════════════════════════════════════ */
async function loadHome(params = {}) {
  const grid  = document.getElementById('booksGrid');
  const title = document.getElementById('catalogueTitle');
  if (params.heading) title.textContent = params.heading;
  else title.textContent = 'All Books';

  grid.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div></div>';

  const qp = new URLSearchParams();
  if (params.q)         qp.set('q', params.q);
  if (params.category)  qp.set('categoryId', params.category);
  if (params.author)    qp.set('authorId', params.author);
  if (params.language)  qp.set('language', params.language);
  if (params.format)    qp.set('format', params.format);
  if (params.minPrice)  qp.set('minPrice', params.minPrice);
  if (params.maxPrice)  qp.set('maxPrice', params.maxPrice);
  qp.set('size', '40');

  // sync filter UI
  document.getElementById('fLanguage').value  = params.language  || '';
  document.getElementById('fFormat').value    = params.format    || '';
  document.getElementById('fMinPrice').value  = params.minPrice  || '';
  document.getElementById('fMaxPrice').value  = params.maxPrice  || '';

  try {
    const data = await GET('/api/v1/books?' + qp.toString());
    const books = data.content || [];
    if (books.length === 0) {
      grid.innerHTML = `<div class="empty-state" style="grid-column:1/-1">
        <div class="empty-icon">📚</div>
        <h3>No books found</h3>
        <p>Try a different search or filter.</p>
      </div>`;
      return;
    }
    grid.innerHTML = '';
    books.forEach(b => grid.appendChild(makeBookCard(b)));

    // load recommendations if logged in
    if (State.token) loadRecommendations();
  } catch(e) {
    grid.innerHTML = `<div class="alert alert-error" style="grid-column:1/-1">
      Failed to load books: ${e.message}</div>`;
  }
}

function makeBookCard(book) {
  const div = document.createElement('div');
  div.className = 'book-card';

  const cover = coverEl(book);
  cover.style.cursor = 'pointer';
  cover.onclick = () => showPage('detail', book.id);
  div.appendChild(cover);

  const info = document.createElement('div');
  info.className = 'book-info';
  const authName = book.authors && book.authors[0] ? book.authors[0].name : 'Unknown Author';
  const avail = book.availability === 'IN_STOCK';
  info.innerHTML = `
    <div class="book-title">${esc(book.title)}</div>
    <div class="book-author">${esc(authName)}</div>
    <div class="book-badges">
      ${book.format ? `<span class="badge badge-format">${book.format}</span>` : ''}
      ${book.language ? `<span class="badge badge-lang">${esc(book.language)}</span>` : ''}
      <span class="badge badge-avail ${avail?'in':''}">${avail?'In Stock':'Out of Stock'}</span>
    </div>
    <div class="book-price">₹${fmtPrice(book.price)}</div>
    ${book.deliveryInfo ? `<div class="book-delivery">🚚 ${esc(book.deliveryInfo)}</div>` : ''}
  `;
  div.appendChild(info);

  const actions = document.createElement('div');
  actions.className = 'book-card-actions';
  const btnDetail = document.createElement('button');
  btnDetail.className = 'btn btn-outline btn-sm';
  btnDetail.textContent = 'Details';
  btnDetail.onclick = () => showPage('detail', book.id);

  const btnCart = document.createElement('button');
  btnCart.className = 'btn btn-accent btn-sm';
  btnCart.textContent = 'Add to Cart';
  btnCart.onclick = () => addToCart(book);

  actions.appendChild(btnDetail);
  actions.appendChild(btnCart);
  div.appendChild(actions);
  return div;
}

async function loadRecommendations() {
  const sec = document.getElementById('recsSection');
  const grid = document.getElementById('recsGrid');
  try {
    const recs = await GET('/api/v1/recommendations?limit=5');
    if (!recs || recs.length === 0) { sec.classList.add('hidden'); return; }
    grid.innerHTML = '';
    recs.forEach(b => grid.appendChild(makeBookCard(b)));
    sec.classList.remove('hidden');
  } catch { sec.classList.add('hidden'); }
}

/* ── Search / Filters ─────────────────────────────────────────────────── */
function doSearch() {
  const q = document.getElementById('heroSearch').value.trim();
  applyFilters(q);
}
function applyFilters(q) {
  const params = {
    q:        q !== undefined ? q : document.getElementById('heroSearch').value.trim(),
    language: document.getElementById('fLanguage').value,
    format:   document.getElementById('fFormat').value,
    minPrice: document.getElementById('fMinPrice').value,
    maxPrice: document.getElementById('fMaxPrice').value,
  };
  Object.keys(params).forEach(k => { if (!params[k]) delete params[k]; });
  showPage('home', params);
}
function clearFilters() {
  document.getElementById('heroSearch').value = '';
  document.getElementById('fLanguage').value  = '';
  document.getElementById('fFormat').value    = '';
  document.getElementById('fMinPrice').value  = '';
  document.getElementById('fMaxPrice').value  = '';
  showPage('home', {});
}

/* ══════════════════════════════════════════════════════════════════════════
   CATEGORIES
══════════════════════════════════════════════════════════════════════════ */
const CATEGORY_ICONS = {
  'Fiction':       '📖', 'Non-Fiction':   '📰', 'Science':       '🔬',
  'Technology':    '💻', 'History':       '🏛️', 'Biography':     '👤',
  'Children':      '🧒', 'Comics':        '💬', 'Self-Help':     '🌱',
  'Business':      '💼', 'Art':           '🎨', 'Travel':        '✈️',
};
async function loadCategories() {
  const grid = document.getElementById('categoriesGrid');
  grid.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div></div>';
  try {
    const cats = await GET('/api/v1/categories');
    grid.innerHTML = '';
    cats.forEach(c => {
      const card = document.createElement('div');
      card.className = 'category-card';
      card.innerHTML = `<span class="category-icon">${CATEGORY_ICONS[c.name] || '📚'}</span>${esc(c.name)}`;
      card.onclick = () => {
        showPage('home', { category: c.id, heading: c.name + ' Books' });
      };
      grid.appendChild(card);
    });
  } catch(e) {
    grid.innerHTML = `<div class="alert alert-error">Failed to load categories: ${e.message}</div>`;
  }
}

/* ══════════════════════════════════════════════════════════════════════════
   BOOK DETAIL
══════════════════════════════════════════════════════════════════════════ */
async function loadDetail(bookId) {
  const container = document.getElementById('detailContent');
  container.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div></div>';
  try {
    const book = await GET('/api/v1/books/' + bookId);
    renderDetail(book, container);
    loadRelated(bookId);
  } catch(e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderDetail(book, container) {
  const authName = book.authors && book.authors[0] ? book.authors[0].name : 'Unknown Author';
  const allAuthors = book.authors ? book.authors.map(a=>a.name).join(', ') : 'Unknown';
  const catNames = book.categories ? book.categories.map(c=>c.name).join(', ') : '—';
  const avail = book.availability === 'IN_STOCK';

  const layout = document.createElement('div');
  layout.className = 'detail-layout';

  // Cover
  const cover = coverEl(book, 'detail-cover');
  layout.appendChild(cover);

  // Info panel
  const info = document.createElement('div');
  info.className = 'detail-info';
  info.innerHTML = `
    <h1>${esc(book.title)}</h1>
    <div class="book-author">by ${esc(allAuthors)}</div>
    <div class="book-badges" style="margin:.5rem 0">
      ${book.format   ? `<span class="badge badge-format">${book.format}</span>` : ''}
      ${book.language ? `<span class="badge badge-lang">${esc(book.language)}</span>` : ''}
      <span class="badge badge-avail ${avail?'in':''}">${avail?'In Stock':'Out of Stock'}</span>
    </div>
    <div class="detail-price">₹${fmtPrice(book.price)}</div>
    ${book.deliveryInfo ? `<div class="book-delivery" style="margin:.3rem 0">🚚 ${esc(book.deliveryInfo)}</div>`:''}
    <table class="detail-table">
      <tr><td>Category</td><td>${esc(catNames)}</td></tr>
      <tr><td>ISBN</td><td>${esc(book.isbn||'—')}</td></tr>
      <tr><td>Publisher</td><td>${esc(book.publisher||'—')}</td></tr>
      <tr><td>Published</td><td>${book.publishedDate||'—'}</td></tr>
      <tr><td>Stock</td><td>${book.stock ?? '—'}</td></tr>
    </table>
    ${book.description ? `<div class="detail-desc">${esc(book.description)}</div>` : ''}
    <div class="detail-actions">
      <button class="btn btn-accent btn-lg" onclick="addToCart(${JSON.stringify(book).replace(/"/g,'&quot;')})">
        🛒 Add to Cart
      </button>
      <button class="btn btn-outline" onclick="showPage('home',{})">← Back to Books</button>
    </div>
  `;
  layout.appendChild(info);
  container.innerHTML = '';
  container.appendChild(layout);
}

async function loadRelated(bookId) {
  const sec  = document.getElementById('relatedSection');
  const grid = document.getElementById('relatedGrid');
  try {
    const books = await GET('/api/v1/books/' + bookId + '/related');
    if (!books || books.length === 0) { sec.classList.add('hidden'); return; }
    grid.innerHTML = '';
    books.forEach(b => grid.appendChild(makeBookCard(b)));
    sec.classList.remove('hidden');
  } catch { sec.classList.add('hidden'); }
}

/* ══════════════════════════════════════════════════════════════════════════
   CART
══════════════════════════════════════════════════════════════════════════ */
async function addToCart(book) {
  if (!State.token) {
    State.pendingCartBook = book;
    toast('Please log in to add items to your cart', 'error');
    showPage('auth', 'login');
    return;
  }
  try {
    State.cart = await POST('/api/v1/cart/items', { bookId: book.id, quantity: 1 });
    refreshCartBadge();
    toast(`"${book.title}" added to cart!`, 'success');
  } catch(e) {
    if (e.status === 401 || e.status === 403) {
      State.pendingCartBook = book;
      showPage('auth', 'login');
    } else {
      toast(e.message, 'error');
    }
  }
}

async function loadCart() {
  const container = document.getElementById('cartContent');
  if (!State.token) {
    container.innerHTML = `<div class="alert alert-info">
      Please <button class="btn btn-primary btn-sm" onclick="showPage('auth','login')">Log In</button>
      to view your cart.
    </div>`;
    return;
  }
  container.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div></div>';
  try {
    State.cart = await GET('/api/v1/cart');
    renderCart(container);
  } catch(e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderCart(container) {
  const cart = State.cart;
  const items = cart.items || [];

  if (items.length === 0) {
    container.innerHTML = `<div class="empty-state">
      <div class="empty-icon">🛒</div>
      <h3>Your cart is empty</h3>
      <p>Browse our catalogue and add some books!</p>
      <button class="btn btn-primary mt-2" onclick="showPage('home',{})">Browse Books</button>
    </div>`;
    return;
  }

  const layout = document.createElement('div');
  layout.className = 'cart-layout';

  // Items panel
  const panel = document.createElement('div');
  panel.className = 'card';
  panel.innerHTML = `<div class="card-title">Shopping Cart (${items.length} item${items.length>1?'s':''})</div>`;

  items.forEach(item => {
    const row = document.createElement('div');
    row.className = 'cart-item';
    const cv = coverEl(item.book, 'cart-item-cover');
    row.appendChild(cv);

    const info = document.createElement('div');
    info.className = 'cart-item-info';
    info.innerHTML = `
      <div class="cart-item-title">${esc(item.book.title)}</div>
      <div class="cart-item-meta">${item.book.authors?.[0]?.name || ''} · ${item.book.format||''}</div>
      <div class="cart-item-price">₹${fmtPrice(item.priceSnapshot)}</div>
    `;
    const qtyWrap = document.createElement('div');
    qtyWrap.className = 'qty-control';
    qtyWrap.innerHTML = `
      <button class="qty-btn" data-action="dec" data-id="${item.id}">−</button>
      <span class="qty-val">${item.quantity}</span>
      <button class="qty-btn" data-action="inc" data-id="${item.id}" data-qty="${item.quantity}">+</button>
    `;
    const removeBtn = document.createElement('button');
    removeBtn.className = 'cart-remove';
    removeBtn.textContent = '✕ Remove';
    removeBtn.onclick = () => removeCartItem(item.id);
    info.appendChild(qtyWrap);
    info.appendChild(removeBtn);
    row.appendChild(info);

    const lineTotal = document.createElement('div');
    lineTotal.style.cssText = 'font-weight:700;white-space:nowrap;padding-top:.25rem';
    lineTotal.textContent = '₹' + fmtPrice(item.lineTotal);
    row.appendChild(lineTotal);
    panel.appendChild(row);
  });

  // Qty buttons
  panel.querySelectorAll('.qty-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id  = btn.dataset.id;
      const cur = parseInt(btn.closest('.qty-control').querySelector('.qty-val').textContent);
      const qty = btn.dataset.action === 'inc' ? cur + 1 : Math.max(1, cur - 1);
      try {
        State.cart = await PUT('/api/v1/cart/items/' + id, { quantity: qty });
        refreshCartBadge();
        renderCart(container);
      } catch(e) { toast(e.message, 'error'); }
    });
  });

  // Clear button
  const clearBtn = document.createElement('button');
  clearBtn.className = 'btn btn-outline-muted btn-sm';
  clearBtn.style.margin = '.75rem';
  clearBtn.textContent = 'Clear Cart';
  clearBtn.onclick = async () => {
    if (!confirm('Remove all items?')) return;
    try {
      await DELETE('/api/v1/cart');
      State.cart = null;
      refreshCartBadge();
      loadCart();
    } catch(e) { toast(e.message, 'error'); }
  };
  panel.appendChild(clearBtn);
  layout.appendChild(panel);

  // Summary panel
  const summary = document.createElement('div');
  summary.className = 'card order-summary';
  const subtotal = cart.subtotal || 0;
  summary.innerHTML = `
    <div class="card-title">Order Summary</div>
    <div class="summary-row"><span>Subtotal (${items.length} items)</span><span>₹${fmtPrice(subtotal)}</span></div>
    <div class="summary-row"><span>Delivery</span><span class="text-muted">Free</span></div>
    <div class="summary-row total"><span>Total</span><span>₹${fmtPrice(subtotal)}</span></div>
    <button class="btn btn-accent w-full mt-2" onclick="proceedCheckout()" style="margin-top:1rem">Proceed to Checkout →</button>
    <button class="btn btn-outline-muted w-full btn-sm" onclick="showPage('home',{})" style="margin-top:.5rem">Continue Shopping</button>
  `;
  layout.appendChild(summary);
  container.innerHTML = '';
  container.appendChild(layout);
}

async function removeCartItem(itemId) {
  try {
    State.cart = await DELETE('/api/v1/cart/items/' + itemId);
    refreshCartBadge();
    loadCart();
  } catch(e) { toast(e.message, 'error'); }
}

function proceedCheckout() {
  if (!State.token) { showPage('auth', 'login'); return; }
  if (!State.cart || !State.cart.items || State.cart.items.length === 0) {
    toast('Your cart is empty', 'error'); return;
  }
  showPage('address');
}

/* ══════════════════════════════════════════════════════════════════════════
   AUTH
══════════════════════════════════════════════════════════════════════════ */
function showAuth(mode = 'login') {
  document.getElementById('tabLogin').classList.toggle('active', mode !== 'register');
  document.getElementById('tabRegister').classList.toggle('active', mode === 'register');
  document.getElementById('formLogin').classList.toggle('hidden', mode === 'register');
  document.getElementById('formRegister').classList.toggle('hidden', mode !== 'register');
  document.getElementById('authError').textContent = '';
}

async function doLogin(e) {
  e.preventDefault();
  const email    = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value;
  const errEl    = document.getElementById('authError');
  errEl.textContent = '';
  const btn = e.submitter;
  btn.disabled = true; btn.textContent = 'Logging in…';
  try {
    const res = await POST('/api/v1/auth/login', { email, password });
    State.token = res.token;
    State.user  = res.user;
    sessionStorage.setItem('bw_token', res.token);
    sessionStorage.setItem('bw_user',  JSON.stringify(res.user));
    updateAuthUI();
    await refreshCartBadge();
    toast(`Welcome back, ${res.user.firstName}!`, 'success');
    // If we got here from add-to-cart, add the pending book
    if (State.pendingCartBook) {
      const b = State.pendingCartBook;
      State.pendingCartBook = null;
      await addToCart(b);
      showPage('cart');
    } else {
      showPage('home', {});
    }
  } catch(e) {
    errEl.textContent = e.message || 'Invalid email or password.';
  } finally { btn.disabled = false; btn.textContent = 'Log In'; }
}

async function doRegister(e) {
  e.preventDefault();
  const errEl = document.getElementById('authError');
  errEl.textContent = '';
  const body = {
    email:     document.getElementById('regEmail').value.trim(),
    password:  document.getElementById('regPassword').value,
    firstName: document.getElementById('regFirst').value.trim(),
    lastName:  document.getElementById('regLast').value.trim(),
  };
  const btn = e.submitter;
  btn.disabled = true; btn.textContent = 'Creating account…';
  try {
    const res = await POST('/api/v1/auth/register', body);
    State.token = res.token;
    State.user  = res.user;
    sessionStorage.setItem('bw_token', res.token);
    sessionStorage.setItem('bw_user',  JSON.stringify(res.user));
    updateAuthUI();
    await refreshCartBadge();
    toast(`Account created! Welcome, ${res.user.firstName}!`, 'success');
    showPage('home', {});
  } catch(e) {
    errEl.textContent = e.message || 'Registration failed.';
  } finally { btn.disabled = false; btn.textContent = 'Create Account'; }
}

/* ══════════════════════════════════════════════════════════════════════════
   ADDRESS
══════════════════════════════════════════════════════════════════════════ */
let selectedAddressId = null;

async function loadAddress() {
  const container = document.getElementById('addressContent');
  container.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div></div>';
  selectedAddressId = null;
  try {
    const addresses = await GET('/api/v1/addresses');
    renderAddressPage(addresses, container);
  } catch(e) {
    if (e.status === 401) { showPage('auth','login'); return; }
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function renderAddressPage(addresses, container) {
  container.innerHTML = '';

  // Steps
  container.innerHTML = checkoutSteps(2);

  const card = document.createElement('div');
  card.className = 'card';
  card.innerHTML = '<div class="card-title">Delivery Address</div>';

  // Existing addresses
  if (addresses.length > 0) {
    const list = document.createElement('div');
    list.className = 'address-list';
    // Pre-select: prefer the isDefault address, otherwise the first one
    const defaultAddr = addresses.find(a => a.isDefault) || addresses[0];
    selectedAddressId = defaultAddr.id;

    addresses.forEach(addr => {
      const isSelected = addr.id === defaultAddr.id;
      const row = document.createElement('label');
      row.className = 'address-card' + (isSelected ? ' selected' : '');
      row.innerHTML = `
        <input type="radio" name="addr" value="${addr.id}" ${isSelected ? 'checked' : ''}>
        <div class="address-text">
          <strong>${esc(addr.line1)}${addr.line2 ? ', ' + esc(addr.line2) : ''}</strong>
          ${esc(addr.city)}, ${esc(addr.state)} – ${esc(addr.postalCode)}<br>${esc(addr.country)}
        </div>
      `;
      row.querySelector('input').addEventListener('change', () => {
        selectedAddressId = addr.id;
        list.querySelectorAll('.address-card').forEach(c => c.classList.remove('selected'));
        row.classList.add('selected');
      });
      list.appendChild(row);
    });
    card.appendChild(list);
  }

  // New address form (collapsed by default if addresses exist)
  const showForm = addresses.length === 0;
  const formToggle = document.createElement('button');
  formToggle.className = 'btn btn-outline btn-sm';
  formToggle.textContent = '+ Add New Address';
  formToggle.style.marginBottom = '1rem';

  const form = document.createElement('form');
  form.className = showForm ? '' : 'hidden';
  form.id = 'addrForm';
  form.innerHTML = `
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">Address Line 1 <span class="req">*</span></label>
        <input class="form-control" id="addrLine1" required maxlength="200" placeholder="Street address">
      </div>
      <div class="form-group">
        <label class="form-label">Address Line 2</label>
        <input class="form-control" id="addrLine2" maxlength="200" placeholder="Apartment, suite, etc.">
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">City <span class="req">*</span></label>
        <input class="form-control" id="addrCity" required maxlength="100">
      </div>
      <div class="form-group">
        <label class="form-label">State <span class="req">*</span></label>
        <input class="form-control" id="addrState" required maxlength="100">
      </div>
    </div>
    <div class="form-row">
      <div class="form-group">
        <label class="form-label">Postal Code <span class="req">*</span></label>
        <input class="form-control" id="addrPostal" required pattern="\\d{4,10}" placeholder="e.g. 600001">
      </div>
      <div class="form-group">
        <label class="form-label">Country <span class="req">*</span></label>
        <input class="form-control" id="addrCountry" required value="India" maxlength="60">
      </div>
    </div>
    <label style="display:flex;align-items:center;gap:.5rem;margin-bottom:1rem;font-size:.9rem">
      <input type="checkbox" id="addrDefault"> Set as default address
    </label>
    <button type="submit" class="btn btn-primary">Save Address</button>
    <div id="addrError" class="alert alert-error hidden" style="margin-top:.75rem"></div>
  `;
  form.onsubmit = async (ev) => {
    ev.preventDefault();
    const errEl = document.getElementById('addrError');
    errEl.classList.add('hidden');
    const body = {
      line1:      document.getElementById('addrLine1').value.trim(),
      line2:      document.getElementById('addrLine2').value.trim() || null,
      city:       document.getElementById('addrCity').value.trim(),
      state:      document.getElementById('addrState').value.trim(),
      postalCode: document.getElementById('addrPostal').value.trim(),
      country:    document.getElementById('addrCountry').value.trim(),
      isDefault:  document.getElementById('addrDefault').checked,
    };
    try {
      const saved = await POST('/api/v1/addresses', body);
      selectedAddressId = saved.id;
      toast('Address saved!', 'success');
      loadAddress();
    } catch(e) {
      errEl.textContent = e.message;
      errEl.classList.remove('hidden');
    }
  };

  formToggle.onclick = () => {
    form.classList.toggle('hidden');
    formToggle.textContent = form.classList.contains('hidden') ? '+ Add New Address' : '− Hide Form';
  };
  if (!showForm) card.appendChild(formToggle);
  card.appendChild(form);

  // Continue button
  const continueBtn = document.createElement('button');
  continueBtn.className = 'btn btn-accent btn-lg';
  continueBtn.style.marginTop = '1.5rem';
  continueBtn.textContent = 'Continue to Payment →';
  continueBtn.onclick = () => {
    if (!selectedAddressId) { toast('Please select a delivery address', 'error'); return; }
    showPage('payment');
  };
  card.appendChild(continueBtn);
  container.appendChild(card);
}

/* ══════════════════════════════════════════════════════════════════════════
   PAYMENT
══════════════════════════════════════════════════════════════════════════ */
async function loadPaymentPage() {
  // Called by showPage('payment') via the payment page init button
  const container = document.getElementById('paymentContent');
  container.innerHTML = checkoutSteps(3);
  const card = document.createElement('div');
  card.className = 'card';
  card.innerHTML = `
    <div class="card-title">Payment</div>
    <div class="alert alert-warn">
      🔒 This is a <strong>mock payment</strong> for demonstration purposes only.
      No real payment is processed.
    </div>
    <div style="margin:.75rem 0;font-weight:600">Select Payment Method</div>
    <div class="payment-methods" id="pmMethods">
      ${makePayMethod('CREDIT_CARD','💳','Credit Card','Visa, Mastercard, Amex')}
      ${makePayMethod('DEBIT_CARD','🏧','Debit Card','All major banks')}
      ${makePayMethod('UPI','📱','UPI','GPay, PhonePe, Paytm')}
      ${makePayMethod('WALLET','👛','Wallet','Book Worm Wallet')}
    </div>
    <div class="payment-fields" id="pmFields"></div>
    <div id="payError" class="alert alert-error hidden" style="margin-top:.75rem"></div>
    <button class="btn btn-accent btn-lg" id="btnPay" onclick="submitPayment()" style="margin-top:1.25rem">Pay Now</button>
    <button class="btn btn-outline-muted btn-sm" onclick="showPage('address')" style="margin-top:.75rem;display:block">← Back to Address</button>
  `;
  container.appendChild(card);

  // Select first method by default
  container.querySelectorAll('.payment-method').forEach(el => {
    el.addEventListener('click', () => {
      container.querySelectorAll('.payment-method').forEach(e=>e.classList.remove('selected'));
      el.classList.add('selected');
      renderPayFields(el.dataset.method);
    });
  });
  container.querySelector('.payment-method')?.click();
}

function makePayMethod(val, icon, label, sub) {
  return `<div class="payment-method" data-method="${val}">
    <span class="payment-method-icon">${icon}</span>
    <div><div class="payment-method-label">${label}</div><div class="payment-method-sub">${sub}</div></div>
  </div>`;
}

function renderPayFields(method) {
  const f = document.getElementById('pmFields');
  if (!f) return;
  if (method === 'CREDIT_CARD' || method === 'DEBIT_CARD') {
    f.innerHTML = `
      <div class="form-group">
        <label class="form-label">Cardholder Name</label>
        <input class="form-control" id="pmCardName" placeholder="Name on card">
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">Last 4 Digits <span class="req">*</span></label>
          <input class="form-control" id="pmLast4" maxlength="4" pattern="\\d{4}" placeholder="1234">
          <div class="form-hint">Only last 4 digits — no full card number stored</div>
        </div>
        <div class="form-group">
          <label class="form-label">Expiry (MM/YY)</label>
          <div class="form-row" style="gap:.5rem">
            <input class="form-control" id="pmExpM" maxlength="2" placeholder="MM" style="padding:.55rem .5rem">
            <input class="form-control" id="pmExpY" maxlength="4" placeholder="YYYY" style="padding:.55rem .5rem">
          </div>
        </div>
      </div>
    `;
  } else if (method === 'UPI') {
    f.innerHTML = `
      <div class="form-group">
        <label class="form-label">UPI ID <span class="req">*</span></label>
        <input class="form-control" id="pmUpi" placeholder="yourname@bank">
      </div>
    `;
  } else if (method === 'WALLET') {
    f.innerHTML = `
      <div class="form-group">
        <label class="form-label">Wallet Provider</label>
        <select class="form-control" id="pmWallet">
          <option value="BookWorm Wallet">Book Worm Wallet</option>
          <option value="Paytm">Paytm</option>
          <option value="Amazon Pay">Amazon Pay</option>
        </select>
      </div>
    `;
  } else { f.innerHTML = ''; }
}

async function submitPayment() {
  const errEl = document.getElementById('payError');
  errEl.classList.add('hidden');
  // Query within paymentContent so stale off-DOM elements are not matched
  const methodEl = document.getElementById('paymentContent').querySelector('.payment-method.selected');
  if (!methodEl) { toast('Please select a payment method', 'error'); return; }
  const method = methodEl.dataset.method;
  const btn = document.getElementById('btnPay');
  btn.disabled = true; btn.textContent = 'Processing…';

  try {
    // 1. Place order
    const orderBody = { addressId: selectedAddressId, giftPointsToRedeem: 0 };
    const order = await POST('/api/v1/orders', orderBody);
    State.checkoutOrder = order;

    // 2. Initiate payment
    const payBody = { orderId: order.id, method };
    if (method === 'CREDIT_CARD' || method === 'DEBIT_CARD') {
      payBody.cardHolderName  = document.getElementById('pmCardName')?.value || '';
      payBody.lastFourDigits  = document.getElementById('pmLast4')?.value || '0000';
      payBody.expiryMonth     = parseInt(document.getElementById('pmExpM')?.value) || 12;
      payBody.expiryYear      = parseInt(document.getElementById('pmExpY')?.value) || 2027;
    } else if (method === 'UPI') {
      payBody.upiId = document.getElementById('pmUpi')?.value || 'demo@upi';
    } else if (method === 'WALLET') {
      payBody.walletProvider = document.getElementById('pmWallet')?.value || 'BookWorm Wallet';
    }
    const payInit = await POST('/api/v1/payments/initiate', payBody);
    State.checkoutPayment = payInit;

    // 3. Complete payment (mock SUCCESS)
    const completeBody = {
      orderId:        order.id,
      transactionRef: payInit.transactionRef,
      mockResult:     'SUCCESS',
    };
    const confirmation = await POST('/api/v1/payments/complete', completeBody);
    State.confirmation = confirmation;
    showPage('confirmation');
  } catch(e) {
    errEl.textContent = e.message;
    errEl.classList.remove('hidden');
    btn.disabled = false; btn.textContent = 'Pay Now';
  }
}

/* ══════════════════════════════════════════════════════════════════════════
   CONFIRMATION
══════════════════════════════════════════════════════════════════════════ */
function showConfirmation() {
  const container = document.getElementById('confirmationContent');
  const conf = State.confirmation;
  if (!conf) { showPage('home',{}); return; }
  const order = conf.order;
  const payment = conf.payment;
  refreshCartBadge();

  container.innerHTML = `
    ${checkoutSteps(4)}
    <div class="card">
      <div class="confirmation-hero">
        <span class="confirm-icon">✅</span>
        <div class="confirm-title">Order Confirmed!</div>
        <div class="confirm-sub">${esc(conf.message || 'Thank you for your purchase.')}</div>
      </div>
      <div class="form-row" style="gap:1.5rem">
        <div>
          <div class="text-muted text-small fw-bold" style="margin-bottom:.3rem">ORDER DETAILS</div>
          <table class="order-detail-table">
            <tr><td class="text-muted text-small">Order ID</td><td><strong>#${order.id}</strong></td></tr>
            <tr><td class="text-muted text-small">Status</td><td><span class="status-badge status-${order.status}">${order.status}</span></td></tr>
            <tr><td class="text-muted text-small">Date</td><td>${fmtDate(order.placedAt)}</td></tr>
            <tr><td class="text-muted text-small">Total</td><td><strong>₹${fmtPrice(order.totalAmount)}</strong></td></tr>
          </table>
        </div>
        <div>
          <div class="text-muted text-small fw-bold" style="margin-bottom:.3rem">PAYMENT</div>
          <table class="order-detail-table">
            <tr><td class="text-muted text-small">Method</td><td>${payment?.method || '—'}</td></tr>
            <tr><td class="text-muted text-small">Status</td><td><span class="status-badge status-${payment?.status||''}">${payment?.status||'—'}</span></td></tr>
            <tr><td class="text-muted text-small">Ref</td><td>${esc(payment?.transactionRef||'—')}</td></tr>
            <tr><td class="text-muted text-small">Amount</td><td>₹${fmtPrice(payment?.amount)}</td></tr>
          </table>
        </div>
      </div>
      <div class="text-muted text-small fw-bold" style="margin:.75rem 0 .3rem">ITEMS ORDERED</div>
      <table class="order-detail-table">
        <thead><tr><th>Book</th><th>Qty</th><th>Price</th><th>Subtotal</th></tr></thead>
        <tbody>
          ${(order.items||[]).map(i=>`<tr>
            <td>${esc(i.book?.title||'')}</td>
            <td>${i.quantity}</td>
            <td>₹${fmtPrice(i.priceAtPurchase)}</td>
            <td>₹${fmtPrice(i.lineTotal)}</td>
          </tr>`).join('')}
        </tbody>
      </table>
      <div style="display:flex;gap:.75rem;margin-top:1.5rem;flex-wrap:wrap">
        <button class="btn btn-primary" onclick="showPage('orders')">View My Orders</button>
        <button class="btn btn-outline" onclick="showPage('home',{})">Continue Shopping</button>
      </div>
    </div>
  `;
}

/* ══════════════════════════════════════════════════════════════════════════
   ORDERS
══════════════════════════════════════════════════════════════════════════ */
async function loadOrders() {
  const container = document.getElementById('ordersContent');
  if (!State.token) {
    container.innerHTML = `<div class="alert alert-info">
      Please <button class="btn btn-primary btn-sm" onclick="showPage('auth','login')">Log In</button>
      to view your orders.
    </div>`;
    return;
  }
  container.innerHTML = '<div class="spinner-wrap"><div class="spinner"></div></div>';
  try {
    const data = await GET('/api/v1/orders?size=20');
    const orders = data.content || [];
    if (orders.length === 0) {
      container.innerHTML = `<div class="empty-state">
        <div class="empty-icon">📦</div>
        <h3>No orders yet</h3>
        <p>Start shopping to see your orders here.</p>
        <button class="btn btn-primary mt-2" onclick="showPage('home',{})">Browse Books</button>
      </div>`;
      return;
    }
    container.innerHTML = `<div class="section-header">
      <h2>My Orders</h2>
      <span class="text-muted text-small">${orders.length} order${orders.length>1?'s':''}</span>
    </div>`;
    orders.forEach(order => container.appendChild(makeOrderCard(order)));
  } catch(e) {
    container.innerHTML = `<div class="alert alert-error">${e.message}</div>`;
  }
}

function makeOrderCard(order) {
  const card = document.createElement('div');
  card.className = 'order-card';
  const header = document.createElement('div');
  header.className = 'order-card-header';
  header.innerHTML = `
    <div>
      <div class="fw-bold">Order #${order.id}</div>
      <div class="text-muted text-small">${fmtDate(order.placedAt)}</div>
    </div>
    <div style="display:flex;align-items:center;gap:.75rem;flex-wrap:wrap">
      <span class="status-badge status-${order.status}">${order.status}</span>
      <span class="fw-bold">₹${fmtPrice(order.totalAmount)}</span>
      <span class="text-muted text-small">${(order.items||[]).length} item${(order.items||[]).length>1?'s':''}</span>
    </div>
  `;

  const body = document.createElement('div');
  body.className = 'order-card-body hidden';

  // Items table
  const itemsHtml = (order.items||[]).map(i=>`<tr>
    <td>${esc(i.book?.title||'')}</td>
    <td>${i.quantity}</td>
    <td>₹${fmtPrice(i.priceAtPurchase)}</td>
    <td>₹${fmtPrice(i.lineTotal)}</td>
  </tr>`).join('');

  body.innerHTML = `
    <table class="order-detail-table" style="margin-bottom:1rem">
      <thead><tr><th>Book</th><th>Qty</th><th>Unit Price</th><th>Subtotal</th></tr></thead>
      <tbody>${itemsHtml}</tbody>
    </table>
    ${order.cancellationEligible ?
      `<div class="alert alert-warn">⏰ You can cancel this order within 48 hours of placement.</div>` : ''}
    <div style="display:flex;gap:.5rem;flex-wrap:wrap;margin-top:.75rem">
      ${order.cancellationEligible ?
        `<button class="btn btn-danger btn-sm" onclick="cancelOrder(${order.id})">Cancel Order</button>` : ''}
      <button class="btn btn-outline btn-sm" onclick="buyAgain(${order.id})">🔄 Buy Again</button>
    </div>
  `;

  header.onclick = () => body.classList.toggle('hidden');
  card.appendChild(header);
  card.appendChild(body);
  return card;
}

async function cancelOrder(orderId) {
  if (!confirm('Are you sure you want to cancel this order?')) return;
  try {
    await POST('/api/v1/orders/' + orderId + '/cancel');
    toast('Order cancelled successfully', 'success');
    loadOrders();
  } catch(e) { toast(e.message, 'error'); }
}

async function buyAgain(orderId) {
  try {
    const res = await POST('/api/v1/orders/' + orderId + '/buy-again');
    await refreshCartBadge();
    // Response has { cart, skippedItems } — no itemsAdded field
    const addedCount = res.cart?.items?.length ?? 0;
    const skippedCount = res.skippedItems?.length ?? 0;
    let msg = `${addedCount} item${addedCount !== 1 ? 's' : ''} added to cart!`;
    if (skippedCount > 0) msg += ` (${skippedCount} item${skippedCount !== 1 ? 's' : ''} out of stock, skipped)`;
    toast(msg, 'success');
    showPage('cart');
  } catch(e) { toast(e.message, 'error'); }
}

/* ── Checkout steps indicator ─────────────────────────────────────────── */
function checkoutSteps(active) {
  const steps = [
    {n:1, label:'Cart'},
    {n:2, label:'Address'},
    {n:3, label:'Payment'},
    {n:4, label:'Confirmed'},
  ];
  return `<div class="checkout-steps" style="margin-bottom:1.5rem">
    ${steps.map((s,i) => `
      <div class="step ${s.n < active ? 'done' : s.n === active ? 'active' : ''}">
        <div class="step-dot">${s.n < active ? '✓' : s.n}</div>
        <span>${s.label}</span>
      </div>
      ${i < steps.length-1 ? '<div class="step-sep"></div>' : ''}
    `).join('')}
  </div>`;
}

/* ── Formatters ───────────────────────────────────────────────────────── */
function fmtPrice(v) {
  if (v === null || v === undefined) return '—';
  return parseFloat(v).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
function fmtDate(s) {
  if (!s) return '—';
  return new Date(s).toLocaleDateString('en-IN', { year:'numeric', month:'short', day:'numeric' });
}
function esc(s) {
  if (!s) return '';
  return String(s)
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
    .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

/* ── Init ─────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  updateAuthUI();
  refreshCartBadge();
  showPage('home', {});

  // Nav buttons
  document.querySelectorAll('.nav-links button[data-page]').forEach(btn => {
    btn.addEventListener('click', () => showPage(btn.dataset.page, {}));
  });

  // Search
  document.getElementById('heroSearch').addEventListener('keydown', e => {
    if (e.key === 'Enter') doSearch();
  });
  document.getElementById('btnSearch').addEventListener('click', doSearch);
  document.getElementById('btnFilter').addEventListener('click', () => applyFilters());
  document.getElementById('btnClearFilters').addEventListener('click', clearFilters);

  // Auth tabs
  document.getElementById('tabLogin').addEventListener('click', () => showAuth('login'));
  document.getElementById('tabRegister').addEventListener('click', () => showAuth('register'));
  document.getElementById('formLogin').addEventListener('submit', doLogin);
  document.getElementById('formRegister').addEventListener('submit', doRegister);
  document.getElementById('navLoginBtn').addEventListener('click', () => showPage('auth','login'));
  document.getElementById('navLogoutBtn').addEventListener('click', logout);
  document.getElementById('navCartBtn').addEventListener('click', () => showPage('cart'));
});
