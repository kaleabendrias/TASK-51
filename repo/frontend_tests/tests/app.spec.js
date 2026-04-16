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
