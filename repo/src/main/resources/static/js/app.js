// Booking Portal - Main Application JS
const API = {
    request(method, url, data) {
        const opts = {
            method,
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin'
        };
        if (data && method !== 'GET') opts.body = JSON.stringify(data);
        return $.ajax({
            url,
            method,
            contentType: 'application/json',
            data: data && method !== 'GET' ? JSON.stringify(data) : undefined,
            xhrFields: { withCredentials: true }
        });
    },
    get(url) { return this.request('GET', url); },
    post(url, data) { return this.request('POST', url, data); },
    put(url, data) { return this.request('PUT', url, data); },
    patch(url, data) { return this.request('PATCH', url, data); },
    del(url) { return this.request('DELETE', url); }
};

const App = {
    currentUser: null,

    init() {
        this.checkAuth();
    },

    checkAuth() {
        API.get('/api/auth/me')
            .done(user => {
                this.currentUser = user;
                this.showApp();
            })
            .fail(() => this.showLogin());
    },

    showLogin() {
        $('#app-main').addClass('hidden');
        $('#login-page').removeClass('hidden');
        LoginPage.init();
    },

    showApp() {
        $('#login-page').addClass('hidden');
        $('#app-main').removeClass('hidden');
        this.renderHeader();
        this.navigate('dashboard');
    },

    renderHeader() {
        const nav = $('#main-nav');
        nav.empty();

        nav.append('<a href="#" data-page="dashboard">Dashboard</a>');
        nav.append('<a href="#" data-page="bookings">Bookings</a>');

        if (this.currentUser.role === 'ADMINISTRATOR') {
            nav.append('<a href="#" data-page="services">Services</a>');
            nav.append('<a href="#" data-page="users">Users</a>');
        }

        $('#user-display').text(this.currentUser.fullName + ' (' + this.currentUser.role + ')');

        nav.find('a').on('click', function(e) {
            e.preventDefault();
            App.navigate($(this).data('page'));
        });
    },

    navigate(page) {
        $('#main-nav a').removeClass('active');
        $('#main-nav a[data-page="' + page + '"]').addClass('active');

        const content = $('#page-content');
        content.empty();

        switch(page) {
            case 'dashboard': DashboardPage.render(content); break;
            case 'bookings': BookingsPage.render(content); break;
            case 'services': ServicesPage.render(content); break;
            case 'users': UsersPage.render(content); break;
        }
    },

    logout() {
        API.post('/api/auth/logout').always(() => {
            this.currentUser = null;
            this.showLogin();
        });
    }
};

// ---- Login Page ----
const LoginPage = {
    init() {
        const box = $('#login-box');
        box.find('#login-form').off('submit').on('submit', e => {
            e.preventDefault();
            this.doLogin();
        });
        box.find('#show-register').off('click').on('click', e => {
            e.preventDefault();
            this.showRegister();
        });
        box.find('#show-login').off('click').on('click', e => {
            e.preventDefault();
            this.showLoginForm();
        });
        box.find('#register-form').off('submit').on('submit', e => {
            e.preventDefault();
            this.doRegister();
        });
    },

    doLogin() {
        const username = $('#login-username').val();
        const password = $('#login-password').val();
        $('#login-error').addClass('hidden');

        API.post('/api/auth/login', { username, password })
            .done(user => {
                App.currentUser = user;
                App.showApp();
            })
            .fail(xhr => {
                const msg = xhr.responseJSON ? xhr.responseJSON.error : 'Login failed';
                $('#login-error').text(msg).removeClass('hidden');
            });
    },

    doRegister() {
        const data = {
            username: $('#reg-username').val(),
            email: $('#reg-email').val(),
            password: $('#reg-password').val(),
            fullName: $('#reg-fullname').val(),
            phone: $('#reg-phone').val()
        };
        $('#register-error').addClass('hidden');

        API.post('/api/auth/register', data)
            .done(() => {
                App.checkAuth();
            })
            .fail(xhr => {
                const msg = xhr.responseJSON ? xhr.responseJSON.error : 'Registration failed';
                $('#register-error').text(msg).removeClass('hidden');
            });
    },

    showRegister() {
        $('#login-section').addClass('hidden');
        $('#register-section').removeClass('hidden');
    },

    showLoginForm() {
        $('#register-section').addClass('hidden');
        $('#login-section').removeClass('hidden');
    }
};

// ---- Dashboard ----
const DashboardPage = {
    render(container) {
        container.html('<div class="stats-grid" id="stats-grid"></div><div class="card"><div class="card-header"><h2>Recent Bookings</h2></div><div class="table-wrap"><table id="dash-table"><thead><tr><th>Date</th><th>Service</th><th>Customer</th><th>Status</th></tr></thead><tbody></tbody></table></div></div>');
        this.loadData();
    },

    loadData() {
        API.get('/api/bookings').done(bookings => {
            const stats = this.calcStats(bookings);
            const grid = $('#stats-grid');
            grid.html(
                this.statCard('Total Bookings', stats.total) +
                this.statCard('Pending', stats.pending) +
                this.statCard('Confirmed', stats.confirmed) +
                this.statCard('Completed', stats.completed)
            );

            const tbody = $('#dash-table tbody');
            tbody.empty();
            bookings.slice(0, 10).forEach(b => {
                tbody.append(`<tr>
                    <td>${b.bookingDate}</td>
                    <td>${b.serviceName}</td>
                    <td>${b.customerName}</td>
                    <td><span class="badge badge-${b.status.toLowerCase()}">${b.status}</span></td>
                </tr>`);
            });
            if (bookings.length === 0) {
                tbody.append('<tr><td colspan="4" class="text-muted" style="text-align:center">No bookings yet</td></tr>');
            }
        });
    },

    calcStats(bookings) {
        return {
            total: bookings.length,
            pending: bookings.filter(b => b.status === 'PENDING').length,
            confirmed: bookings.filter(b => b.status === 'CONFIRMED').length,
            completed: bookings.filter(b => b.status === 'COMPLETED').length
        };
    },

    statCard(label, value) {
        return `<div class="stat-card"><div class="stat-label">${label}</div><div class="stat-value">${value}</div></div>`;
    }
};

// ---- Bookings ----
const BookingsPage = {
    render(container) {
        container.html(`
            <div class="card">
                <div class="card-header">
                    <h2>Bookings</h2>
                    <button class="btn btn-primary" id="btn-new-booking">+ New Booking</button>
                </div>
                <div class="table-wrap">
                    <table id="bookings-table"><thead><tr>
                        <th>ID</th><th>Date</th><th>Time</th><th>Service</th>
                        <th>Customer</th><th>Photographer</th><th>Status</th><th>Actions</th>
                    </tr></thead><tbody></tbody></table>
                </div>
            </div>
            <div class="modal-overlay" id="booking-modal">
                <div class="modal">
                    <div class="modal-header">
                        <h3 id="booking-modal-title">New Booking</h3>
                        <button class="modal-close" id="close-booking-modal">&times;</button>
                    </div>
                    <form id="booking-form">
                        <input type="hidden" id="bf-id"/>
                        <div class="form-row">
                            <div class="form-group">
                                <label>Service</label>
                                <select class="form-control" id="bf-service" required></select>
                            </div>
                            <div class="form-group">
                                <label>Photographer</label>
                                <select class="form-control" id="bf-photographer"><option value="">Unassigned</option></select>
                            </div>
                        </div>
                        <div class="form-row">
                            <div class="form-group">
                                <label>Date</label>
                                <input type="date" class="form-control" id="bf-date" required/>
                            </div>
                            <div class="form-group">
                                <label>Location</label>
                                <input type="text" class="form-control" id="bf-location"/>
                            </div>
                        </div>
                        <div class="form-row">
                            <div class="form-group">
                                <label>Start Time</label>
                                <input type="time" class="form-control" id="bf-start" required/>
                            </div>
                            <div class="form-group">
                                <label>End Time</label>
                                <input type="time" class="form-control" id="bf-end" required/>
                            </div>
                        </div>
                        <div class="form-group">
                            <label>Notes</label>
                            <textarea class="form-control" id="bf-notes"></textarea>
                        </div>
                        <div id="booking-form-error" class="alert alert-error hidden"></div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-outline" id="cancel-booking">Cancel</button>
                            <button type="submit" class="btn btn-primary">Save Booking</button>
                        </div>
                    </form>
                </div>
            </div>
            <div class="modal-overlay" id="booking-detail-modal">
                <div class="modal">
                    <div class="modal-header">
                        <h3>Booking Details</h3>
                        <button class="modal-close" id="close-detail-modal">&times;</button>
                    </div>
                    <div id="booking-detail-content"></div>
                    <div class="card mt-1" id="attachments-section">
                        <h4 class="mb-1">Attachments</h4>
                        <div id="attachment-list" class="file-list"></div>
                        <div class="file-upload-area mt-1" id="file-drop-area">
                            Click or drop files here to upload
                            <input type="file" id="file-input" style="display:none"/>
                        </div>
                    </div>
                </div>
            </div>
        `);
        this.bindEvents();
        this.loadBookings();
    },

    bindEvents() {
        $('#btn-new-booking').on('click', () => this.openForm());
        $('#close-booking-modal, #cancel-booking').on('click', () => this.closeForm());
        $('#close-detail-modal').on('click', () => $('#booking-detail-modal').removeClass('active'));
        $('#booking-form').on('submit', e => { e.preventDefault(); this.saveBooking(); });
        $('#file-drop-area').on('click', () => $('#file-input').trigger('click'));
        $('#file-input').on('change', e => this.uploadFile(e.target.files[0]));
    },

    loadBookings() {
        API.get('/api/bookings').done(bookings => {
            const tbody = $('#bookings-table tbody').empty();
            bookings.forEach(b => {
                tbody.append(`<tr>
                    <td>${b.id}</td>
                    <td>${b.bookingDate}</td>
                    <td>${b.startTime} - ${b.endTime}</td>
                    <td>${b.serviceName}</td>
                    <td>${b.customerName}</td>
                    <td>${b.photographerName || '<span class="text-muted">Unassigned</span>'}</td>
                    <td><span class="badge badge-${b.status.toLowerCase()}">${b.status}</span></td>
                    <td>
                        <button class="btn btn-sm btn-outline btn-view" data-id="${b.id}">View</button>
                        ${this.statusButtons(b)}
                    </td>
                </tr>`);
            });
            tbody.find('.btn-view').on('click', function() {
                BookingsPage.viewBooking($(this).data('id'));
            });
            tbody.find('.btn-status').on('click', function() {
                BookingsPage.changeStatus($(this).data('id'), $(this).data('status'));
            });
        });
    },

    statusButtons(b) {
        const role = App.currentUser.role;
        let btns = '';
        if (b.status === 'PENDING' && (role === 'ADMINISTRATOR' || role === 'PHOTOGRAPHER')) {
            btns += `<button class="btn btn-sm btn-success btn-status" data-id="${b.id}" data-status="CONFIRMED">Confirm</button>`;
        }
        if (b.status === 'CONFIRMED' && (role === 'ADMINISTRATOR' || role === 'PHOTOGRAPHER')) {
            btns += `<button class="btn btn-sm btn-warning btn-status" data-id="${b.id}" data-status="IN_PROGRESS">Start</button>`;
        }
        if (b.status === 'IN_PROGRESS' && (role === 'ADMINISTRATOR' || role === 'PHOTOGRAPHER')) {
            btns += `<button class="btn btn-sm btn-success btn-status" data-id="${b.id}" data-status="COMPLETED">Complete</button>`;
        }
        if (['PENDING','CONFIRMED'].includes(b.status)) {
            btns += `<button class="btn btn-sm btn-danger btn-status" data-id="${b.id}" data-status="CANCELLED">Cancel</button>`;
        }
        return btns;
    },

    openForm(booking) {
        $('#booking-form-error').addClass('hidden');
        $('#bf-id').val(booking ? booking.id : '');
        $('#booking-modal-title').text(booking ? 'Edit Booking' : 'New Booking');

        API.get('/api/services').done(services => {
            const sel = $('#bf-service').empty();
            services.forEach(s => sel.append(`<option value="${s.id}">${s.name} ($${s.price})</option>`));
            if (booking) sel.val(booking.serviceId);
        });

        API.get('/api/users/photographers').done(photographers => {
            const sel = $('#bf-photographer');
            sel.find('option:not(:first)').remove();
            photographers.forEach(p => sel.append(`<option value="${p.id}">${p.fullName}</option>`));
            if (booking && booking.photographerId) sel.val(booking.photographerId);
        });

        if (booking) {
            $('#bf-date').val(booking.bookingDate);
            $('#bf-start').val(booking.startTime);
            $('#bf-end').val(booking.endTime);
            $('#bf-location').val(booking.location);
            $('#bf-notes').val(booking.notes);
        } else {
            $('#booking-form')[0].reset();
        }

        $('#booking-modal').addClass('active');
    },

    closeForm() {
        $('#booking-modal').removeClass('active');
    },

    saveBooking() {
        const id = $('#bf-id').val();
        const data = {
            serviceId: parseInt($('#bf-service').val()),
            photographerId: $('#bf-photographer').val() ? parseInt($('#bf-photographer').val()) : null,
            bookingDate: $('#bf-date').val(),
            startTime: $('#bf-start').val(),
            endTime: $('#bf-end').val(),
            location: $('#bf-location').val(),
            notes: $('#bf-notes').val()
        };

        const promise = id ? API.put('/api/bookings/' + id, data) : API.post('/api/bookings', data);
        promise
            .done(() => { this.closeForm(); this.loadBookings(); })
            .fail(xhr => {
                const msg = xhr.responseJSON ? xhr.responseJSON.error : 'Failed to save';
                $('#booking-form-error').text(msg).removeClass('hidden');
            });
    },

    changeStatus(id, status) {
        API.patch('/api/bookings/' + id + '/status', { status })
            .done(() => this.loadBookings())
            .fail(xhr => alert(xhr.responseJSON ? xhr.responseJSON.error : 'Failed'));
    },

    currentDetailId: null,

    viewBooking(id) {
        this.currentDetailId = id;
        API.get('/api/bookings/' + id).done(b => {
            $('#booking-detail-content').html(`
                <table class="detail-table">
                    <tr><td><strong>Service</strong></td><td>${b.serviceName}</td></tr>
                    <tr><td><strong>Date</strong></td><td>${b.bookingDate}</td></tr>
                    <tr><td><strong>Time</strong></td><td>${b.startTime} - ${b.endTime}</td></tr>
                    <tr><td><strong>Customer</strong></td><td>${b.customerName}</td></tr>
                    <tr><td><strong>Photographer</strong></td><td>${b.photographerName || 'Unassigned'}</td></tr>
                    <tr><td><strong>Location</strong></td><td>${b.location || '-'}</td></tr>
                    <tr><td><strong>Status</strong></td><td><span class="badge badge-${b.status.toLowerCase()}">${b.status}</span></td></tr>
                    <tr><td><strong>Price</strong></td><td>$${b.totalPrice}</td></tr>
                    <tr><td><strong>Notes</strong></td><td>${b.notes || '-'}</td></tr>
                </table>
            `);
            this.loadAttachments(id);
            $('#booking-detail-modal').addClass('active');
        });
    },

    loadAttachments(bookingId) {
        API.get('/api/attachments/booking/' + bookingId).done(attachments => {
            const list = $('#attachment-list').empty();
            attachments.forEach(a => {
                list.append(`<div class="file-item">
                    <span>${a.originalName} (${(a.fileSize/1024).toFixed(1)} KB)</span>
                    <span>
                        <a href="/api/attachments/${a.id}/download" class="btn btn-sm btn-outline">Download</a>
                        <button class="btn btn-sm btn-danger btn-del-attach" data-id="${a.id}">Delete</button>
                    </span>
                </div>`);
            });
            list.find('.btn-del-attach').on('click', function() {
                API.del('/api/attachments/' + $(this).data('id')).done(() => {
                    BookingsPage.loadAttachments(bookingId);
                });
            });
        });
    },

    uploadFile(file) {
        if (!file || !this.currentDetailId) return;
        const formData = new FormData();
        formData.append('file', file);
        $.ajax({
            url: '/api/attachments/booking/' + this.currentDetailId,
            method: 'POST',
            data: formData,
            processData: false,
            contentType: false,
            xhrFields: { withCredentials: true }
        }).done(() => {
            this.loadAttachments(this.currentDetailId);
            $('#file-input').val('');
        }).fail(xhr => {
            alert(xhr.responseJSON ? xhr.responseJSON.error : 'Upload failed');
        });
    }
};

// ---- Services (Admin) ----
const ServicesPage = {
    render(container) {
        container.html(`
            <div class="card">
                <div class="card-header">
                    <h2>Services</h2>
                    <button class="btn btn-primary" id="btn-new-service">+ New Service</button>
                </div>
                <div class="table-wrap">
                    <table id="services-table"><thead><tr>
                        <th>Name</th><th>Price</th><th>Duration</th><th>Active</th><th>Actions</th>
                    </tr></thead><tbody></tbody></table>
                </div>
            </div>
            <div class="modal-overlay" id="service-modal">
                <div class="modal">
                    <div class="modal-header">
                        <h3 id="service-modal-title">New Service</h3>
                        <button class="modal-close" id="close-service-modal">&times;</button>
                    </div>
                    <form id="service-form">
                        <input type="hidden" id="sf-id"/>
                        <div class="form-group">
                            <label>Name</label>
                            <input type="text" class="form-control" id="sf-name" required/>
                        </div>
                        <div class="form-group">
                            <label>Description</label>
                            <textarea class="form-control" id="sf-desc"></textarea>
                        </div>
                        <div class="form-row">
                            <div class="form-group">
                                <label>Price ($)</label>
                                <input type="number" step="0.01" class="form-control" id="sf-price" required/>
                            </div>
                            <div class="form-group">
                                <label>Duration (minutes)</label>
                                <input type="number" class="form-control" id="sf-duration" required/>
                            </div>
                        </div>
                        <div class="form-group">
                            <label><input type="checkbox" id="sf-active" checked/> Active</label>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-outline" id="cancel-service">Cancel</button>
                            <button type="submit" class="btn btn-primary">Save</button>
                        </div>
                    </form>
                </div>
            </div>
        `);
        this.bindEvents();
        this.loadServices();
    },

    bindEvents() {
        $('#btn-new-service').on('click', () => this.openForm());
        $('#close-service-modal, #cancel-service').on('click', () => this.closeForm());
        $('#service-form').on('submit', e => { e.preventDefault(); this.save(); });
    },

    loadServices() {
        API.get('/api/services/all').done(services => {
            const tbody = $('#services-table tbody').empty();
            services.forEach(s => {
                tbody.append(`<tr>
                    <td>${s.name}</td>
                    <td>$${s.price}</td>
                    <td>${s.durationMinutes} min</td>
                    <td>${s.active ? 'Yes' : 'No'}</td>
                    <td><button class="btn btn-sm btn-outline btn-edit-svc" data-id="${s.id}">Edit</button></td>
                </tr>`);
            });
            tbody.find('.btn-edit-svc').on('click', function() {
                const sid = $(this).data('id');
                API.get('/api/services/' + sid).done(s => ServicesPage.openForm(s));
            });
        });
    },

    openForm(service) {
        $('#sf-id').val(service ? service.id : '');
        $('#service-modal-title').text(service ? 'Edit Service' : 'New Service');
        if (service) {
            $('#sf-name').val(service.name);
            $('#sf-desc').val(service.description);
            $('#sf-price').val(service.price);
            $('#sf-duration').val(service.durationMinutes);
            $('#sf-active').prop('checked', service.active);
        } else {
            $('#service-form')[0].reset();
        }
        $('#service-modal').addClass('active');
    },

    closeForm() { $('#service-modal').removeClass('active'); },

    save() {
        const id = $('#sf-id').val();
        const data = {
            name: $('#sf-name').val(),
            description: $('#sf-desc').val(),
            price: parseFloat($('#sf-price').val()),
            durationMinutes: parseInt($('#sf-duration').val()),
            active: $('#sf-active').is(':checked')
        };
        const p = id ? API.put('/api/services/' + id, data) : API.post('/api/services', data);
        p.done(() => { this.closeForm(); this.loadServices(); })
         .fail(xhr => alert(xhr.responseJSON ? xhr.responseJSON.error : 'Failed'));
    }
};

// ---- Users (Admin) ----
const UsersPage = {
    render(container) {
        container.html(`
            <div class="card">
                <div class="card-header"><h2>Users</h2></div>
                <div class="table-wrap">
                    <table id="users-table"><thead><tr>
                        <th>Username</th><th>Full Name</th><th>Email</th><th>Role</th><th>Enabled</th><th>Actions</th>
                    </tr></thead><tbody></tbody></table>
                </div>
            </div>
        `);
        this.loadUsers();
    },

    loadUsers() {
        API.get('/api/users').done(users => {
            const tbody = $('#users-table tbody').empty();
            users.forEach(u => {
                tbody.append(`<tr>
                    <td>${u.username}</td>
                    <td>${u.fullName}</td>
                    <td>${u.email}</td>
                    <td>${u.roleName}</td>
                    <td>${u.enabled ? 'Yes' : 'No'}</td>
                    <td>
                        <button class="btn btn-sm ${u.enabled ? 'btn-danger' : 'btn-success'} btn-toggle"
                                data-id="${u.id}" data-enabled="${!u.enabled}">
                            ${u.enabled ? 'Disable' : 'Enable'}
                        </button>
                    </td>
                </tr>`);
            });
            tbody.find('.btn-toggle').on('click', function() {
                API.patch('/api/users/' + $(this).data('id') + '/enabled',
                    { enabled: $(this).data('enabled') === true || $(this).data('enabled') === 'true' })
                    .done(() => UsersPage.loadUsers());
            });
        });
    }
};

// Boot
$(document).ready(() => App.init());
