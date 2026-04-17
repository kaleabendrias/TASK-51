/**
 * Jest setup file: mocks browser globals and executes the real app.js
 * so tests run against actual production code, not inline duplicates.
 *
 * Strategy:
 *  1. Install a jQuery stub (global.$) before app.js is evaluated so that
 *     the SPA's module-level declarations do not trigger network calls.
 *  2. Load src/main/resources/static/js/app.js via fs.readFileSync.
 *  3. Execute it inside a new Function() body (non-strict scope) bound to
 *     `global`.  This captures all const/let/function declarations in that
 *     function scope, then the trailing block assigns every exported symbol
 *     explicitly to `this` (== global) so test files can access them as
 *     ordinary globals in the jsdom environment.
 */
'use strict';

const fs   = require('fs');
const path = require('path');

// ── 1. jQuery stub ────────────────────────────────────────────────────────
// Must be in place BEFORE app.js executes (the boot line is
//   $(document).ready(()=>App.init())
// Our stub's .ready() silently swallows the callback so App.init()
// is never triggered during test setup.

const mkJq = function mkJq() {
  const o = {
    val:          function() { return ''; },
    html:         function() { return o; },
    text:         function() { return o; },
    append:       function() { return o; },
    prepend:      function() { return o; },
    empty:        function() { return o; },
    remove:       function() { return o; },
    addClass:     function() { return o; },
    removeClass:  function() { return o; },
    toggleClass:  function() { return o; },
    hasClass:     function() { return false; },
    is:           function() { return false; },
    on:           function() { return o; },
    off:          function() { return o; },
    find:         function() { return o; },
    filter:       function() { return o; },
    closest:      function() { return o; },
    siblings:     function() { return o; },
    children:     function() { return o; },
    parent:       function() { return o; },
    each:         function() { return o; },
    map:          function() { return []; },
    trigger:      function() { return o; },
    prop:         function() { return o; },
    attr:         function() { return o; },
    data:         function() { return ''; },
    ready:        function() { return o; },  // <-- does NOT call the callback
    done:         function() { return o; },
    fail:         function() { return o; },
    always:       function() { return o; },
    hide:         function() { return o; },
    show:         function() { return o; },
    css:          function() { return o; },
    click:        function() { return o; },
    submit:       function() { return o; },
    focus:        function() { return o; },
    blur:         function() { return o; },
    change:       function() { return o; },
    scrollTop:    function() { return 0; },
    serialize:    function() { return ''; },
    length: 0,
  };
  return o;
};

const $stub = function jQueryStub(/* selector */) { return mkJq(); };
$stub.ajax  = function() { return mkJq(); };
$stub.fn    = {};
$stub.extend = function(a, b) { return Object.assign(a, b); };

global.$       = $stub;
global.jQuery  = $stub;

// Mock EventSource so ChatPage.connectSse() does not throw in jsdom
global.EventSource = class MockEventSource {
  constructor(url) { this.url = url; }
  addEventListener() {}
  close() {}
  set onerror(_fn) {}
  set onmessage(_fn) {}
};

// ── 2. Load the real app.js ───────────────────────────────────────────────
const appJsPath = path.resolve(
  __dirname,
  '../src/main/resources/static/js/app.js'
);
const appSrc = fs.readFileSync(appJsPath, 'utf8');

// ── 3. Execute & export module-scoped symbols to global ──────────────────
// new Function() creates a non-strict function.  When called via
// bootstrap.call(global) its `this` is the global object.
// All const/let/function declarations in appSrc are local to this function
// scope, then the trailing block copies each one to `this` (global).
const bootstrap = new Function(
  // language=JavaScript
  appSrc + `
;
// Re-export every testable symbol to the global (window) scope
this.US_GEO      = typeof US_GEO      !== 'undefined' ? US_GEO      : undefined;
this.US_STATES   = typeof US_STATES   !== 'undefined' ? US_STATES   : undefined;
this.Validate    = typeof Validate    !== 'undefined' ? Validate    : undefined;
this.API         = typeof API         !== 'undefined' ? API         : undefined;
this.statusBadge = typeof statusBadge !== 'undefined' ? statusBadge : undefined;
this.uuid        = typeof uuid        !== 'undefined' ? uuid        : undefined;
this.fmtDate     = typeof fmtDate     !== 'undefined' ? fmtDate     : undefined;
this.fmtDateTime = typeof fmtDateTime !== 'undefined' ? fmtDateTime : undefined;
this.escHtml     = typeof escHtml     !== 'undefined' ? escHtml     : undefined;
this.Store             = typeof Store             !== 'undefined' ? Store             : undefined;
this.App               = typeof App               !== 'undefined' ? App               : undefined;
this.LoginPage         = typeof LoginPage         !== 'undefined' ? LoginPage         : undefined;
this.SearchPage        = typeof SearchPage        !== 'undefined' ? SearchPage        : undefined;
this.ListingDetailPage = typeof ListingDetailPage !== 'undefined' ? ListingDetailPage : undefined;
this.OrdersPage        = typeof OrdersPage        !== 'undefined' ? OrdersPage        : undefined;
this.OrderDetailPage   = typeof OrderDetailPage   !== 'undefined' ? OrderDetailPage   : undefined;
this.AddressPage       = typeof AddressPage       !== 'undefined' ? AddressPage       : undefined;
this.ChatPage          = typeof ChatPage          !== 'undefined' ? ChatPage          : undefined;
this.NotifPage         = typeof NotifPage         !== 'undefined' ? NotifPage         : undefined;
this.PointsPage        = typeof PointsPage        !== 'undefined' ? PointsPage        : undefined;
this.PhotoDashPage     = typeof PhotoDashPage     !== 'undefined' ? PhotoDashPage     : undefined;
this.MyListingsPage    = typeof MyListingsPage    !== 'undefined' ? MyListingsPage    : undefined;
this.AdminDashPage     = typeof AdminDashPage     !== 'undefined' ? AdminDashPage     : undefined;
this.UsersAdminPage    = typeof UsersAdminPage    !== 'undefined' ? UsersAdminPage    : undefined;
this.BlacklistAdminPage= typeof BlacklistAdminPage!== 'undefined' ? BlacklistAdminPage: undefined;
this.ServicesAdminPage = typeof ServicesAdminPage !== 'undefined' ? ServicesAdminPage : undefined;
this.PointsAdminPage   = typeof PointsAdminPage   !== 'undefined' ? PointsAdminPage   : undefined;
`
);

bootstrap.call(global);
