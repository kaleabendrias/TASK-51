/**
 * Frontend unit tests for app.js pure functions.
 *
 * setup.js (configured in package.json → jest.setupFiles) loads the REAL
 * src/main/resources/static/js/app.js into the jsdom global scope before
 * these tests run.  Every symbol tested here is the actual production code —
 * nothing is duplicated or stubbed inline.
 */

// ── Validate ─────────────────────────────────────────────────────────────────

describe('Validate.email', () => {
  test('accepts standard address', () => expect(Validate.email('user@example.com')).toBe(true));
  test('accepts email with subdomain', () => expect(Validate.email('u@mail.example.co.uk')).toBe(true));
  test('accepts plus-tagged address', () => expect(Validate.email('u+tag@example.com')).toBe(true));
  test('rejects address without @', () => expect(Validate.email('userexample.com')).toBe(false));
  test('rejects address without domain', () => expect(Validate.email('user@')).toBe(false));
  test('rejects address with spaces', () => expect(Validate.email('u ser@example.com')).toBe(false));
  test('rejects empty string', () => expect(Validate.email('')).toBe(false));
});

describe('Validate.phone', () => {
  test('accepts standard US format', () => expect(Validate.phone('555-123-4567')).toBe(true));
  test('accepts international with country code', () => expect(Validate.phone('+1 555 123 4567')).toBe(true));
  test('accepts parenthesised area code', () => expect(Validate.phone('(555) 123-4567')).toBe(true));
  test('rejects fewer than 7 chars', () => expect(Validate.phone('123')).toBe(false));
  test('rejects letters', () => expect(Validate.phone('abc-defg')).toBe(false));
  test('rejects empty string', () => expect(Validate.phone('')).toBe(false));
});

describe('Validate.zip', () => {
  test('accepts 5-digit ZIP', () => expect(Validate.zip('12345')).toBe(true));
  test('accepts ZIP+4 format', () => expect(Validate.zip('12345-6789')).toBe(true));
  test('rejects 4-digit ZIP', () => expect(Validate.zip('1234')).toBe(false));
  test('rejects 6-digit ZIP', () => expect(Validate.zip('123456')).toBe(false));
  test('rejects letters', () => expect(Validate.zip('ABCDE')).toBe(false));
  test('rejects empty string', () => expect(Validate.zip('')).toBe(false));
  test('rejects incomplete ZIP+4 extension', () => expect(Validate.zip('12345-678')).toBe(false));
});

describe('Validate.required', () => {
  test('accepts non-empty string', () => expect(Validate.required('hello')).toBe(true));
  test('accepts numeric zero (falsy but non-empty)', () => expect(Validate.required(0)).toBe(true));
  test('rejects null', () => expect(Validate.required(null)).toBe(false));
  test('rejects undefined', () => expect(Validate.required(undefined)).toBe(false));
  test('rejects empty string', () => expect(Validate.required('')).toBe(false));
  test('rejects whitespace-only string', () => expect(Validate.required('   ')).toBe(false));
});

describe('Validate.minLen', () => {
  test('passes when length equals minimum', () => expect(Validate.minLen('abc', 3)).toBeTruthy());
  test('passes when length exceeds minimum', () => expect(Validate.minLen('abcdef', 3)).toBeTruthy());
  test('fails when length is below minimum', () => expect(Validate.minLen('ab', 3)).toBeFalsy());
  test('fails for empty string', () => expect(Validate.minLen('', 1)).toBeFalsy());
  test('fails for null', () => expect(Validate.minLen(null, 1)).toBeFalsy());
});

describe('Validate.stateValid', () => {
  test('accepts valid state CA', () => expect(Validate.stateValid('CA')).toBe(true));
  test('accepts valid state NY', () => expect(Validate.stateValid('NY')).toBe(true));
  test('accepts last state WY', () => expect(Validate.stateValid('WY')).toBe(true));
  test('rejects lowercase code', () => expect(Validate.stateValid('ca')).toBe(false));
  test('rejects non-existent code', () => expect(Validate.stateValid('ZZ')).toBe(false));
  test('rejects empty string', () => expect(Validate.stateValid('')).toBe(false));
});

// ── Utility helpers ───────────────────────────────────────────────────────────

describe('statusBadge', () => {
  test('wraps status in a badge span with lowercased CSS class', () => {
    expect(statusBadge('PENDING')).toBe('<span class="badge badge-pending">PENDING</span>');
  });
  test('preserves original casing in the text content', () => {
    expect(statusBadge('CONFIRMED')).toContain('>CONFIRMED<');
  });
  test('lowercases the CSS class', () => {
    expect(statusBadge('CONFIRMED')).toContain('badge-confirmed');
  });
  test('handles null gracefully (no throw, returns a string)', () => {
    expect(typeof statusBadge(null)).toBe('string');
    expect(statusBadge(null)).toContain('badge-');
  });
  test('handles empty string without throwing', () => {
    expect(statusBadge('')).toContain('badge-');
  });
  test('handles mixed-case status', () => {
    expect(statusBadge('Completed')).toContain('badge-completed');
  });
});

describe('uuid', () => {
  test('returns an 8-character hex string', () => {
    expect(uuid()).toMatch(/^[0-9a-f]{8}$/);
  });
  test('returns only hex characters', () => {
    for (let i = 0; i < 10; i++) expect(uuid()).toMatch(/^[0-9a-f]+$/);
  });
  test('produces distinct values on successive calls', () => {
    const ids = new Set(Array.from({ length: 30 }, uuid));
    expect(ids.size).toBeGreaterThan(1);
  });
});

describe('fmtDate', () => {
  test('extracts first 10 chars from an ISO datetime', () => {
    expect(fmtDate('2024-03-15T10:30:00')).toBe('2024-03-15');
  });
  test('passes through a value already 10 chars long', () => {
    expect(fmtDate('2024-03-15')).toBe('2024-03-15');
  });
  test('returns "-" for null', () => expect(fmtDate(null)).toBe('-'));
  test('returns "-" for undefined', () => expect(fmtDate(undefined)).toBe('-'));
  test('returns "-" for empty string', () => expect(fmtDate('')).toBe('-'));
});

describe('fmtDateTime', () => {
  test('replaces T with space and trims to 16 chars', () => {
    expect(fmtDateTime('2024-03-15T10:30:00')).toBe('2024-03-15 10:30');
  });
  test('strips seconds', () => {
    expect(fmtDateTime('2024-12-01T23:59:59')).toBe('2024-12-01 23:59');
  });
  test('returns "-" for null', () => expect(fmtDateTime(null)).toBe('-'));
  test('returns "-" for undefined', () => expect(fmtDateTime(undefined)).toBe('-'));
  test('returns "-" for empty string', () => expect(fmtDateTime('')).toBe('-'));
});

describe('escHtml', () => {
  test('escapes < and > into HTML entities', () => {
    expect(escHtml('<script>')).toBe('&lt;script&gt;');
  });
  test('escapes & into &amp;', () => {
    expect(escHtml('a & b')).toBe('a &amp; b');
  });
  test('does not alter double-quotes (text nodes do not need to encode them)', () => {
    // DOM textContent→innerHTML only encodes < > & in text context; " passes through
    expect(escHtml('"hi"')).toBe('"hi"');
  });
  test('returns empty string for null', () => expect(escHtml(null)).toBe(''));
  test('returns empty string for empty input', () => expect(escHtml('')).toBe(''));
  test('leaves plain text unchanged', () => expect(escHtml('Hello World')).toBe('Hello World'));
  test('neutralises a typical XSS payload', () => {
    const safe = escHtml('<img src=x onerror=alert(1)>');
    expect(safe).not.toContain('<img');
    expect(safe).toContain('&lt;img');
  });
});

// ── API.idemHeader ────────────────────────────────────────────────────────────

describe('API.idemHeader', () => {
  test('returns an object with an Idempotency-Key property', () => {
    expect(API.idemHeader()).toHaveProperty('Idempotency-Key');
  });
  test('key starts with "idem-"', () => {
    expect(API.idemHeader()['Idempotency-Key']).toMatch(/^idem-/);
  });
  test('key is a non-empty string', () => {
    const k = API.idemHeader()['Idempotency-Key'];
    expect(typeof k).toBe('string');
    expect(k.length).toBeGreaterThan(0);
  });
  test('produces a different key on each call', () => {
    const k1 = API.idemHeader()['Idempotency-Key'];
    const k2 = API.idemHeader()['Idempotency-Key'];
    expect(k1).not.toBe(k2);
  });
});

// ── US_STATES ─────────────────────────────────────────────────────────────────

describe('US_STATES', () => {
  test('contains exactly 50 entries', () => expect(US_STATES.length).toBe(50));
  test('contains CA', () => expect(US_STATES).toContain('CA'));
  test('contains NY', () => expect(US_STATES).toContain('NY'));
  test('contains TX', () => expect(US_STATES).toContain('TX'));
  test('every entry is a 2-letter uppercase code', () => {
    for (const s of US_STATES) expect(s).toMatch(/^[A-Z]{2}$/);
  });
  test('has no duplicate entries', () => {
    expect(new Set(US_STATES).size).toBe(US_STATES.length);
  });
});

// ── US_GEO ────────────────────────────────────────────────────────────────────

describe('US_GEO', () => {
  test('contains California', () => expect(US_GEO).toHaveProperty('California'));
  test('California has Los Angeles', () => {
    expect(US_GEO['California']).toHaveProperty('Los Angeles');
  });
  test('LA neighborhoods include Hollywood', () => {
    expect(US_GEO['California']['Los Angeles']).toContain('Hollywood');
  });
  test('New York City neighborhoods include Manhattan', () => {
    expect(US_GEO['New York']['New York City']).toContain('Manhattan');
  });
  test('Texas has Austin, Houston, and Dallas', () => {
    const cities = Object.keys(US_GEO['Texas']);
    expect(cities).toContain('Austin');
    expect(cities).toContain('Houston');
    expect(cities).toContain('Dallas');
  });
  test('all neighborhood lists are non-empty arrays', () => {
    for (const state of Object.values(US_GEO))
      for (const neighborhoods of Object.values(state)) {
        expect(Array.isArray(neighborhoods)).toBe(true);
        expect(neighborhoods.length).toBeGreaterThan(0);
      }
  });
});

// ── Store ─────────────────────────────────────────────────────────────────────

describe('Store', () => {
  test('addSearchTerm does not throw', () => {
    expect(() => Store.addSearchTerm('portrait')).not.toThrow();
  });
  test('getSearchTerms does not throw when callback provided', () => {
    expect(() => Store.getSearchTerms(() => {})).not.toThrow();
  });
  test('getSearchTerms calls $.ajax with GET on the suggestions endpoint', () => {
    const ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
    });
    Store.getSearchTerms(() => {});
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: expect.stringContaining('/api/listings/search/suggestions') })
    );
    ajaxSpy.mockRestore();
  });
});

// ── Validate.check ────────────────────────────────────────────────────────────

describe('Validate.check', () => {
  test('returns false when stub val() is empty and required rule applied', () => {
    expect(Validate.check('#any', Validate.required, 'Required')).toBe(false);
  });
  test('returns false for invalid email when val() returns empty string', () => {
    expect(Validate.check('#any', Validate.email, 'Enter a valid email')).toBe(false);
  });
  test('returns true when validation function always passes', () => {
    expect(Validate.check('#any', () => true, 'msg')).toBe(true);
  });
  test('does not throw for unknown selector', () => {
    expect(() => Validate.check('#nonexistent', Validate.required, 'Required')).not.toThrow();
  });
});

// ── Validate.checkForm ────────────────────────────────────────────────────────

describe('Validate.checkForm', () => {
  test('does not throw when called on arbitrary selector', () => {
    expect(() => Validate.checkForm('#register-form')).not.toThrow();
  });
  test('returns true when each() stub never fires the callback (no fields visited)', () => {
    // Stub each() is a no-op — valid flag is never set to false
    expect(Validate.checkForm('#form-stub')).toBe(true);
  });
});

// ── API methods ───────────────────────────────────────────────────────────────

describe('API._json / API.get / API.post / API.put / API.del / API.upload', () => {
  let ajaxSpy;
  beforeEach(() => {
    ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done:   jest.fn().mockReturnThis(),
      fail:   jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
  });
  afterEach(() => ajaxSpy.mockRestore());

  test('API.get passes GET method and URL to $.ajax', () => {
    API.get('/api/listings');
    expect(ajaxSpy).toHaveBeenCalledWith(expect.objectContaining({ method: 'GET', url: '/api/listings' }));
  });
  test('API.post passes POST method and JSON-serialised body', () => {
    API.post('/api/auth/login', { username: 'u', password: 'p' });
    const opts = ajaxSpy.mock.calls[0][0];
    expect(opts.method).toBe('POST');
    expect(opts.url).toBe('/api/auth/login');
    expect(JSON.parse(opts.data)).toEqual({ username: 'u', password: 'p' });
  });
  test('API.put passes PUT method', () => {
    API.put('/api/addresses/1', { label: 'Home' });
    expect(ajaxSpy).toHaveBeenCalledWith(expect.objectContaining({ method: 'PUT' }));
  });
  test('API.patch passes PATCH method', () => {
    API.patch('/api/users/1/enabled', { enabled: false });
    expect(ajaxSpy).toHaveBeenCalledWith(expect.objectContaining({ method: 'PATCH' }));
  });
  test('API.del passes DELETE method', () => {
    API.del('/api/addresses/5');
    expect(ajaxSpy).toHaveBeenCalledWith(expect.objectContaining({ method: 'DELETE', url: '/api/addresses/5' }));
  });
  test('API.upload sets processData false and contentType false', () => {
    API.upload('/api/messages/1/image', new FormData());
    const opts = ajaxSpy.mock.calls[0][0];
    expect(opts.processData).toBe(false);
    expect(opts.contentType).toBe(false);
  });
  test('API.post with extra headers merges them into $.ajax options', () => {
    const h = { 'Idempotency-Key': 'idem-test' };
    API.post('/api/orders', { listingId: 1 }, h);
    const opts = ajaxSpy.mock.calls[0][0];
    expect(opts.headers['Idempotency-Key']).toBe('idem-test');
  });
  test('API.get sets withCredentials in xhrFields', () => {
    API.get('/api/auth/me');
    const opts = ajaxSpy.mock.calls[0][0];
    expect(opts.xhrFields).toEqual(expect.objectContaining({ withCredentials: true }));
  });
});

// ── App.defaultPage ───────────────────────────────────────────────────────────

describe('App.defaultPage', () => {
  afterEach(() => { App.user = null; });

  test('returns "search" for CUSTOMER role', () => {
    App.user = { role: 'CUSTOMER' };
    expect(App.defaultPage()).toBe('search');
  });
  test('returns "photo-dashboard" for PHOTOGRAPHER role', () => {
    App.user = { role: 'PHOTOGRAPHER' };
    expect(App.defaultPage()).toBe('photo-dashboard');
  });
  test('returns "photo-dashboard" for SERVICE_PROVIDER role', () => {
    App.user = { role: 'SERVICE_PROVIDER' };
    expect(App.defaultPage()).toBe('photo-dashboard');
  });
  test('returns "admin-dashboard" for ADMINISTRATOR role', () => {
    App.user = { role: 'ADMINISTRATOR' };
    expect(App.defaultPage()).toBe('admin-dashboard');
  });
  test('falls back to "search" for unknown role', () => {
    App.user = { role: 'UNKNOWN_ROLE' };
    expect(App.defaultPage()).toBe('search');
  });
});

// ── App.navigate ──────────────────────────────────────────────────────────────

describe('App.navigate', () => {
  beforeEach(() => {
    App.user = { id: 1, role: 'ADMINISTRATOR', fullName: 'Test Admin' };
  });
  afterEach(() => { App.user = null; });

  test('does not throw for an unrecognised page key', () => {
    expect(() => App.navigate('no-such-page')).not.toThrow();
  });
  test('does not throw for every known page key', () => {
    const pages = [
      'search', 'orders', 'addresses', 'chat', 'notifications', 'points',
      'photo-dashboard', 'my-listings', 'admin-dashboard', 'users-admin',
      'blacklist-admin', 'services-admin', 'listing-detail', 'order-detail', 'points-admin',
    ];
    for (const p of pages) {
      expect(() => App.navigate(p)).not.toThrow();
    }
  });
});

// ── SearchPage ────────────────────────────────────────────────────────────────

describe('SearchPage', () => {
  test('page property defaults to 1', () => expect(SearchPage.page).toBe(1));
  test('perPage property defaults to 6', () => expect(SearchPage.perPage).toBe(6));
  test('results property defaults to an empty array', () => {
    expect(Array.isArray(SearchPage.results)).toBe(true);
    expect(SearchPage.results.length).toBe(0);
  });
  test('renderPagination does not throw when totalPages is 1 (single page — no output)', () => {
    SearchPage.totalPagesFromServer = 1;
    expect(() => SearchPage.renderPagination()).not.toThrow();
  });
  test('renderPagination does not throw when totalPages is 5', () => {
    SearchPage.totalPagesFromServer = 5;
    expect(() => SearchPage.renderPagination()).not.toThrow();
  });
  test('renderResults does not throw with empty results array', () => {
    SearchPage.results = [];
    expect(() => SearchPage.renderResults()).not.toThrow();
  });
  test('renderResults does not throw with populated listing data', () => {
    SearchPage.results = [{
      id: 1, title: 'Portrait Session', price: 150,
      description: 'A test listing', durationMinutes: 60,
      category: 'PORTRAIT', location: 'Los Angeles', photographerName: 'Jane Photo',
    }];
    expect(() => SearchPage.renderResults()).not.toThrow();
    SearchPage.results = [];
  });
  test('populateGeo does not throw', () => {
    expect(() => SearchPage.populateGeo()).not.toThrow();
  });
  test('render does not throw with stub container', () => {
    const C = global.$('#page-content');
    expect(() => SearchPage.render(C)).not.toThrow();
  });
});

// ── ListingDetailPage ─────────────────────────────────────────────────────────

describe('ListingDetailPage', () => {
  test('listingId property defaults to null', () => {
    expect(ListingDetailPage.listingId).toBeNull();
  });
  test('render with null listingId does not throw', () => {
    ListingDetailPage.listingId = null;
    expect(() => ListingDetailPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── OrderDetailPage ───────────────────────────────────────────────────────────

describe('OrderDetailPage', () => {
  test('orderId property defaults to null', () => {
    expect(OrderDetailPage.orderId).toBeNull();
  });
  test('render with null orderId does not throw', () => {
    OrderDetailPage.orderId = null;
    expect(() => OrderDetailPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── OrdersPage ────────────────────────────────────────────────────────────────

describe('OrdersPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => OrdersPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── PointsPage ────────────────────────────────────────────────────────────────

describe('PointsPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => PointsPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── NotifPage ─────────────────────────────────────────────────────────────────

describe('NotifPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => NotifPage.render(global.$('#page-content'))).not.toThrow();
  });
  test('loadNotifs does not throw', () => {
    expect(() => NotifPage.loadNotifs()).not.toThrow();
  });
});

// ── AddressPage ───────────────────────────────────────────────────────────────

describe('AddressPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => AddressPage.render(global.$('#page-content'))).not.toThrow();
  });
  test('load does not throw (API stub swallows .done() callback)', () => {
    expect(() => AddressPage.load()).not.toThrow();
  });
});

// ── MyListingsPage ────────────────────────────────────────────────────────────

describe('MyListingsPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => MyListingsPage.render(global.$('#page-content'))).not.toThrow();
  });
  test('load does not throw', () => {
    expect(() => MyListingsPage.load()).not.toThrow();
  });
});

// ── ChatPage ──────────────────────────────────────────────────────────────────

describe('ChatPage', () => {
  beforeEach(() => { App.user = { id: 4, role: 'CUSTOMER', fullName: 'Test Customer' }; });
  afterEach(() => {
    App.user = null;
    if (ChatPage.eventSource) { ChatPage.eventSource.close(); ChatPage.eventSource = null; }
  });

  test('render does not throw with stub container (EventSource mocked)', () => {
    expect(() => ChatPage.render(global.$('#page-content'))).not.toThrow();
  });
  test('connectSse sets eventSource to a MockEventSource instance', () => {
    ChatPage.connectSse();
    expect(ChatPage.eventSource).toBeInstanceOf(global.EventSource);
  });
  test('sendMsg does not throw when activeConv is null', () => {
    ChatPage.activeConv = null;
    expect(() => ChatPage.sendMsg()).not.toThrow();
  });
});

// ── PhotoDashPage ─────────────────────────────────────────────────────────────

describe('PhotoDashPage', () => {
  beforeEach(() => { App.user = { id: 2, role: 'PHOTOGRAPHER', fullName: 'Jane Photo' }; });
  afterEach(() => { App.user = null; });

  test('render does not throw with stub container', () => {
    expect(() => PhotoDashPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── AdminDashPage ─────────────────────────────────────────────────────────────

describe('AdminDashPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => AdminDashPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── UsersAdminPage ────────────────────────────────────────────────────────────

describe('UsersAdminPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => UsersAdminPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── BlacklistAdminPage ────────────────────────────────────────────────────────

describe('BlacklistAdminPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => BlacklistAdminPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── ServicesAdminPage ─────────────────────────────────────────────────────────

describe('ServicesAdminPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => ServicesAdminPage.render(global.$('#page-content'))).not.toThrow();
  });
});

// ── PointsAdminPage ───────────────────────────────────────────────────────────

describe('PointsAdminPage', () => {
  test('render does not throw with stub container', () => {
    expect(() => PointsAdminPage.render(global.$('#page-content'))).not.toThrow();
  });
  test('showTab("leaderboard") does not throw', () => {
    expect(() => PointsAdminPage.showTab('leaderboard')).not.toThrow();
  });
  test('showTab("rules") does not throw', () => {
    expect(() => PointsAdminPage.showTab('rules')).not.toThrow();
  });
  test('showTab("adjustments") does not throw', () => {
    expect(() => PointsAdminPage.showTab('adjustments')).not.toThrow();
  });
  test('showTab("adjust") does not throw', () => {
    expect(() => PointsAdminPage.showTab('adjust')).not.toThrow();
  });
});

// ── LoginPage ─────────────────────────────────────────────────────────────────

describe('LoginPage', () => {
  test('init does not throw', () => {
    expect(() => LoginPage.init()).not.toThrow();
  });
  test('doLogin does not call $.ajax when username/password are empty (stub val returns "")', () => {
    const spy = jest.spyOn(global.$, 'ajax');
    LoginPage.doLogin();
    expect(spy).not.toHaveBeenCalled();
    spy.mockRestore();
  });
  test('doLogin calls API.post with /api/auth/login when credentials are provided', () => {
    const postSpy = jest.spyOn(API, 'post').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
    });
    // Temporarily override $ so val() returns credentials for the login inputs
    const origDollar = global.$;
    global.$ = function(sel) {
      const o = origDollar(sel);
      if (sel === '#login-username') o.val = () => 'admin';
      else if (sel === '#login-password') o.val = () => 'secret123';
      return o;
    };
    Object.assign(global.$, origDollar);

    LoginPage.doLogin();

    global.$ = origDollar;
    expect(postSpy).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({ username: 'admin', password: 'secret123' })
    );
    postSpy.mockRestore();
  });
  test('doRegister does not throw (checkForm with stub each() passes vacuously)', () => {
    expect(() => LoginPage.doRegister()).not.toThrow();
  });
});

// ── Interaction flow: API endpoint verification ────────────────────────────────

describe('OrdersPage API call', () => {
  let ajaxSpy;
  beforeEach(() => {
    ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
  });
  afterEach(() => ajaxSpy.mockRestore());

  test('render calls $.ajax GET /api/orders', () => {
    OrdersPage.render(global.$('#page-content'));
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/orders' })
    );
  });
});

describe('AddressPage API call', () => {
  let ajaxSpy;
  beforeEach(() => {
    ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
  });
  afterEach(() => ajaxSpy.mockRestore());

  test('load calls $.ajax GET /api/addresses', () => {
    AddressPage.load();
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/addresses' })
    );
  });
});

describe('NotifPage API call', () => {
  let ajaxSpy;
  beforeEach(() => {
    ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
  });
  afterEach(() => ajaxSpy.mockRestore());

  test('loadNotifs calls $.ajax GET /api/notifications', () => {
    NotifPage.loadNotifs();
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/notifications' })
    );
  });
});

describe('MyListingsPage API call', () => {
  let ajaxSpy;
  beforeEach(() => {
    ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
  });
  afterEach(() => ajaxSpy.mockRestore());

  test('load calls $.ajax GET /api/listings/my', () => {
    MyListingsPage.load();
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/listings/my' })
    );
  });
});

describe('ChatPage API call', () => {
  beforeEach(() => {
    App.user = { id: 4, role: 'CUSTOMER', fullName: 'Test User' };
  });
  afterEach(() => {
    App.user = null;
    if (ChatPage.eventSource) { ChatPage.eventSource.close(); ChatPage.eventSource = null; }
  });

  test('render calls $.ajax GET /api/messages/conversations', () => {
    const ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
    ChatPage.render(global.$('#page-content'));
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/messages/conversations' })
    );
    ajaxSpy.mockRestore();
  });
});

describe('PointsPage API calls', () => {
  let ajaxSpy;
  beforeEach(() => {
    ajaxSpy = jest.spyOn(global.$, 'ajax').mockReturnValue({
      done: jest.fn().mockReturnThis(),
      fail: jest.fn().mockReturnThis(),
      always: jest.fn().mockReturnThis(),
    });
  });
  afterEach(() => ajaxSpy.mockRestore());

  test('render calls $.ajax GET /api/points/balance', () => {
    PointsPage.render(global.$('#page-content'));
    expect(ajaxSpy).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'GET', url: '/api/points/balance' })
    );
  });
});

// ── SearchPage.renderResults HTML content assertion ───────────────────────────

describe('SearchPage.renderResults HTML output', () => {
  afterEach(() => { SearchPage.results = []; });

  test('appends a listing card with title and price for each result', () => {
    SearchPage.results = [{
      id: 42, title: 'Studio Session', price: 150,
      durationMinutes: 60, description: 'Great', category: 'PORTRAIT',
      location: 'NYC', photographerName: 'Jane',
    }];

    const captured = [];
    const origDollar = global.$;
    global.$ = function(sel) {
      const o = origDollar(sel);
      if (sel === '#listing-results') {
        o.append = function(html) { captured.push(html); return o; };
      }
      return o;
    };
    Object.assign(global.$, origDollar);

    SearchPage.renderResults();
    global.$ = origDollar;

    expect(captured.length).toBeGreaterThan(0);
    const allHtml = captured.join('');
    expect(allHtml).toContain('Studio Session');
    expect(allHtml).toContain('150');
    expect(allHtml).toContain('data-id="42"');
  });

  test('empty results renders "No listings found" message via html()', () => {
    SearchPage.results = [];
    let capturedHtml = '';
    const origDollar = global.$;
    global.$ = function(sel) {
      const o = origDollar(sel);
      if (sel === '#listing-results') {
        o.html = function(content) {
          if (content !== undefined) capturedHtml = content;
          return o;
        };
        o.empty = function() { return o; };
      }
      return o;
    };
    Object.assign(global.$, origDollar);

    SearchPage.renderResults();
    global.$ = origDollar;

    expect(capturedHtml).toContain('No listings found');
  });

  test('XSS: title with < > is escaped in the rendered HTML', () => {
    SearchPage.results = [{
      id: 1, title: '<script>alert(1)</script>', price: 100,
      durationMinutes: 30, description: '', category: '', location: '', photographerName: '',
    }];

    const captured = [];
    const origDollar = global.$;
    global.$ = function(sel) {
      const o = origDollar(sel);
      if (sel === '#listing-results') {
        o.append = function(html) { captured.push(html); return o; };
      }
      return o;
    };
    Object.assign(global.$, origDollar);

    SearchPage.renderResults();
    global.$ = origDollar;

    const allHtml = captured.join('');
    expect(allHtml).not.toContain('<script>');
    expect(allHtml).toContain('&lt;script&gt;');
  });
});
