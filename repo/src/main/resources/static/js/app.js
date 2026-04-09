/* ============================================================
   Booking Portal — Complete jQuery SPA
   ============================================================ */

// ---- US Geography for hierarchical destination filters ----
const US_GEO={
 'California':{'Los Angeles':['Hollywood','Santa Monica','Downtown LA','Beverly Hills'],'San Francisco':['Mission','SoMa','Marina','Nob Hill'],'San Diego':['Gaslamp','La Jolla','Pacific Beach']},
 'New York':{'New York City':['Manhattan','Brooklyn','Queens','Bronx'],'Buffalo':['Elmwood','Allentown']},
 'Illinois':{'Chicago':['Loop','Lincoln Park','Wicker Park','Hyde Park'],'Springfield':['Downtown','West Side']},
 'Oregon':{'Portland':['Pearl District','Hawthorne','Alberta','Downtown']},
 'Texas':{'Austin':['Downtown','South Congress','East Austin'],'Houston':['Midtown','Heights','Montrose'],'Dallas':['Uptown','Deep Ellum','Bishop Arts']}
};
const US_STATES=['AL','AK','AZ','AR','CA','CO','CT','DE','FL','GA','HI','ID','IL','IN','IA','KS','KY','LA','ME','MD','MA','MI','MN','MS','MO','MT','NE','NV','NH','NJ','NM','NY','NC','ND','OH','OK','OR','PA','RI','SC','SD','TN','TX','UT','VT','VA','WA','WV','WI','WY'];

// ---- API Client ----
const API={
 _json(method,url,data,headers){
  const opts={method,url,contentType:'application/json',xhrFields:{withCredentials:true},headers:headers||{}};
  if(data&&method!=='GET')opts.data=JSON.stringify(data);
  return $.ajax(opts);
 },
 get(u){return this._json('GET',u)},
 post(u,d,h){return this._json('POST',u,d,h)},
 put(u,d,h){return this._json('PUT',u,d,h)},
 patch(u,d,h){return this._json('PATCH',u,d,h)},
 del(u){return this._json('DELETE',u)},
 upload(url,formData){return $.ajax({url,method:'POST',data:formData,processData:false,contentType:false,xhrFields:{withCredentials:true}})},
 // Generate a unique idempotency key header
 idemHeader(){return {'Idempotency-Key':'idem-'+Date.now()+'-'+Math.random().toString(36).substr(2,8)}}
};

// ---- Form Validation ----
const Validate={
 email(v){return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)},
 phone(v){return /^\+?[\d\s()-]{7,}$/.test(v)},
 zip(v){return /^\d{5}(-\d{4})?$/.test(v)},
 required(v){return v!=null&&String(v).trim().length>0},
 minLen(v,n){return v&&v.length>=n},
 stateValid(s){return US_STATES.includes(s)},
 check(el,fn,msg){
  const v=$(el).val();
  if(!fn(v)){$(el).addClass('is-invalid').removeClass('is-valid');$(el).siblings('.invalid-feedback').text(msg);return false}
  $(el).removeClass('is-invalid').addClass('is-valid');return true;
 },
 live(form){
  $(form).find('[data-validate]').off('input.v blur.v').on('input.v blur.v',function(){
   const rules=$(this).data('validate').split(',');
   for(const r of rules){
    const[fn,...args]=r.split(':');
    let ok=true,msg='Invalid';
    if(fn==='required'){ok=Validate.required($(this).val());msg='This field is required'}
    else if(fn==='email'){ok=Validate.email($(this).val());msg='Enter a valid email'}
    else if(fn==='phone'){ok=Validate.phone($(this).val());msg='Enter a valid phone number'}
    else if(fn==='zip'){ok=Validate.zip($(this).val());msg='Enter a valid US ZIP code (e.g. 12345)'}
    else if(fn==='minlen'){ok=Validate.minLen($(this).val(),parseInt(args[0]));msg=`Minimum ${args[0]} characters`}
    else if(fn==='state'){ok=Validate.stateValid($(this).val());msg='Enter a valid US state code'}
    if(!ok){$(this).addClass('is-invalid').removeClass('is-valid');$(this).siblings('.invalid-feedback').text(msg);return}
   }
   $(this).removeClass('is-invalid').addClass('is-valid');
  });
 },
 checkForm(form){
  let valid=true;
  $(form).find('[data-validate]').each(function(){$(this).trigger('blur.v');if($(this).hasClass('is-invalid'))valid=false});
  return valid;
 }
};

// ---- LocalStorage helpers ----
const Store={
 get(k,def){try{const v=localStorage.getItem('bp_'+k);return v?JSON.parse(v):def}catch(e){return def}},
 set(k,v){try{localStorage.setItem('bp_'+k,JSON.stringify(v))}catch(e){}},
 addSearchTerm(t){if(!t)return;let terms=this.get('searches',[]);terms=terms.filter(x=>x!==t);terms.unshift(t);this.set('searches',terms.slice(0,15))},
 getSearchTerms(){return this.get('searches',[])}
};

// ---- Badge helper ----
function statusBadge(s){return `<span class="badge badge-${(s||'').toLowerCase()}">${s}</span>`}
function uuid(){return 'xxxxxxxx'.replace(/x/g,()=>((Math.random()*16)|0).toString(16))}
function fmtDate(d){if(!d)return'-';return d.substring(0,10)}
function fmtDateTime(d){if(!d)return'-';return d.replace('T',' ').substring(0,16)}
function escHtml(s){if(!s)return'';const d=document.createElement('div');d.textContent=s;return d.innerHTML}

// ================================================================
//  MAIN APP
// ================================================================
const App={
 user:null,
 init(){this.checkAuth()},
 checkAuth(){API.get('/api/auth/me').done(u=>{this.user=u;this.showApp()}).fail(()=>this.showLogin())},
 showLogin(){$('#app-main').addClass('hidden');$('#login-page').removeClass('hidden');LoginPage.init()},
 showApp(){$('#login-page').addClass('hidden');$('#app-main').removeClass('hidden');this.renderHeader();this.navigate(this.defaultPage())},
 defaultPage(){return{CUSTOMER:'search',PHOTOGRAPHER:'photo-dashboard',ADMINISTRATOR:'admin-dashboard'}[this.user.role]||'search'},
 renderHeader(){
  const nav=$('#main-nav').empty();const r=this.user.role;
  if(r==='CUSTOMER'){
   nav.append('<a href="#" data-page="search">Browse</a><a href="#" data-page="orders">My Orders</a><a href="#" data-page="addresses">Addresses</a><a href="#" data-page="chat">Messages</a><a href="#" data-page="notifications">Alerts</a><a href="#" data-page="points">Points</a>');
  }else if(r==='PHOTOGRAPHER'){
   nav.append('<a href="#" data-page="photo-dashboard">Dashboard</a><a href="#" data-page="my-listings">Listings</a><a href="#" data-page="orders">Orders</a><a href="#" data-page="chat">Messages</a><a href="#" data-page="notifications">Alerts</a><a href="#" data-page="points">Points</a>');
  }else{
   nav.append('<a href="#" data-page="admin-dashboard">Dashboard</a><a href="#" data-page="search">Browse</a><a href="#" data-page="orders">Orders</a><a href="#" data-page="users-admin">Users</a><a href="#" data-page="blacklist-admin">Blacklist</a><a href="#" data-page="services-admin">Services</a><a href="#" data-page="chat">Messages</a><a href="#" data-page="notifications">Alerts</a><a href="#" data-page="points-admin">Points</a>');
  }
  $('#user-display').text(this.user.fullName);
  nav.find('a').on('click',function(e){e.preventDefault();App.navigate($(this).data('page'))});
 },
 navigate(page){
  $('#main-nav a').removeClass('active');$('#main-nav a[data-page="'+page+'"]').addClass('active');
  const C=$('#page-content').empty();
  const pages={
   'search':SearchPage,'orders':OrdersPage,'addresses':AddressPage,'chat':ChatPage,
   'notifications':NotifPage,'points':PointsPage,'points-admin':PointsAdminPage,
   'photo-dashboard':PhotoDashPage,'my-listings':MyListingsPage,
   'admin-dashboard':AdminDashPage,'users-admin':UsersAdminPage,'blacklist-admin':BlacklistAdminPage,
   'services-admin':ServicesAdminPage,'listing-detail':ListingDetailPage,'order-detail':OrderDetailPage
  };
  const p=pages[page];if(p)p.render(C);else C.html('<div class="card"><p>Page not found</p></div>');
 },
 logout(){API.post('/api/auth/logout').always(()=>{this.user=null;this.showLogin()})}
};

// ================================================================
//  LOGIN PAGE
// ================================================================
const LoginPage={
 init(){
  const b=$('#login-box');
  b.find('#login-form').off('submit').on('submit',e=>{e.preventDefault();this.doLogin()});
  b.find('#register-form').off('submit').on('submit',e=>{e.preventDefault();this.doRegister()});
  b.find('#show-register').off('click').on('click',e=>{e.preventDefault();$('#login-section').addClass('hidden');$('#register-section').removeClass('hidden')});
  b.find('#show-login').off('click').on('click',e=>{e.preventDefault();$('#register-section').addClass('hidden');$('#login-section').removeClass('hidden')});
  Validate.live('#register-form');
 },
 doLogin(){
  const u=$('#login-username').val(),p=$('#login-password').val();$('#login-error').addClass('hidden');
  if(!u||!p){$('#login-error').text('Username and password required').removeClass('hidden');return}
  API.post('/api/auth/login',{username:u,password:p}).done(user=>{App.user=user;App.showApp()})
   .fail(xhr=>{$('#login-error').text(xhr.responseJSON?xhr.responseJSON.error:'Login failed').removeClass('hidden')});
 },
 doRegister(){
  if(!Validate.checkForm('#register-form'))return;
  $('#register-error').addClass('hidden');
  API.post('/api/auth/register',{username:$('#reg-username').val(),email:$('#reg-email').val(),password:$('#reg-password').val(),fullName:$('#reg-fullname').val(),phone:$('#reg-phone').val()})
   .done(()=>App.checkAuth()).fail(xhr=>{$('#register-error').text(xhr.responseJSON?xhr.responseJSON.error:'Registration failed').removeClass('hidden')});
 }
};

// ================================================================
//  SEARCH PAGE (Customer)
// ================================================================
const SearchPage={
 page:1,perPage:6,results:[],
 render(C){
  C.html(`
   <div class="card"><div class="card-header"><h2>Find a Photographer</h2>
    <button class="btn btn-outline btn-sm" id="toggle-filters">Filters</button></div>
    <div class="relative">
     <div class="search-bar"><input type="text" class="form-control" id="search-input" placeholder="Search by keyword, style, or photographer name..."/>
      <button class="btn btn-primary" id="search-btn">Search</button></div>
     <div class="suggestions-list hidden" id="search-suggestions"></div>
    </div>
    <div class="filter-panel collapsed" id="filter-panel">
     <div class="form-group"><label>State</label><select class="form-control" id="f-state"><option value="">All States</option></select></div>
     <div class="form-group"><label>City</label><select class="form-control" id="f-city"><option value="">All Cities</option></select></div>
     <div class="form-group"><label>Neighborhood</label><select class="form-control" id="f-neighborhood"><option value="">All</option></select></div>
     <div class="form-group"><label>Category</label><select class="form-control" id="f-category"><option value="">All</option><option>PORTRAIT</option><option>WEDDING</option><option>EVENT</option><option>FAMILY</option><option>PRODUCT</option></select></div>
     <div class="form-group"><label>Min Price ($)</label><input type="number" class="form-control" id="f-min-price" min="0"/></div>
     <div class="form-group"><label>Max Price ($)</label><input type="number" class="form-control" id="f-max-price" min="0"/></div>
     <div class="form-group"><label>Theme</label><select class="form-control" id="f-theme"><option value="">Any</option><option>CLASSIC</option><option>MODERN</option><option>VINTAGE</option><option>ARTISTIC</option><option>NATURAL</option></select></div>
     <div class="form-group"><label>Transport</label><select class="form-control" id="f-transport"><option value="">Any</option><option>WALK</option><option>DRIVE</option><option>PUBLIC_TRANSIT</option></select></div>
     <div class="form-group"><label>Min Rating</label><input type="number" class="form-control" id="f-min-rating" min="0" max="5" step="0.5"/></div>
     <div class="form-group"><label>Available Date</label><input type="date" class="form-control" id="f-avail-date"/></div>
     <div class="form-group"><label>Sort By</label><select class="form-control" id="f-sort"><option value="newest">Newest</option><option value="price-asc">Price: Low to High</option><option value="price-desc">Price: High to Low</option><option value="duration">Duration</option><option value="rating">Rating</option></select></div>
    </div>
   </div>
   <div id="listing-results" class="listing-grid"></div>
   <div class="pagination" id="search-pagination"></div>`);
  this.populateGeo();this.bindEvents();this.doSearch();
 },
 populateGeo(){
  const ss=$('#f-state');Object.keys(US_GEO).sort().forEach(s=>ss.append(`<option>${s}</option>`));
  ss.on('change',()=>{const st=ss.val();const cc=$('#f-city').empty().append('<option value="">All Cities</option>');$('#f-neighborhood').empty().append('<option value="">All</option>');if(st&&US_GEO[st])Object.keys(US_GEO[st]).sort().forEach(c=>cc.append(`<option>${c}</option>`))});
  $('#f-city').on('change',()=>{const st=$('#f-state').val(),ct=$('#f-city').val();const nn=$('#f-neighborhood').empty().append('<option value="">All</option>');if(st&&ct&&US_GEO[st]&&US_GEO[st][ct])US_GEO[st][ct].forEach(n=>nn.append(`<option>${n}</option>`))});
 },
 bindEvents(){
  $('#search-btn').on('click',()=>{this.page=1;this.doSearch()});
  $('#search-input').on('keydown',e=>{if(e.key==='Enter')$('#search-btn').click()});
  $('#toggle-filters').on('click',()=>$('#filter-panel').toggleClass('collapsed'));
  // Suggestions
  $('#search-input').on('focus',()=>{const terms=Store.getSearchTerms();if(terms.length){const sl=$('#search-suggestions').empty().removeClass('hidden');terms.forEach(t=>sl.append(`<div class="sug-item">${escHtml(t)}</div>`));sl.find('.sug-item').on('click',function(){$('#search-input').val($(this).text());sl.addClass('hidden');$('#search-btn').click()})}});
  $('#search-input').on('blur',()=>setTimeout(()=>$('#search-suggestions').addClass('hidden'),200));
 },
 doSearch(){
  const kw=$('#search-input').val();if(kw)Store.addSearchTerm(kw);
  const loc=[$('#f-state').val(),$('#f-city').val(),$('#f-neighborhood').val()].filter(Boolean).join(', ');
  const params=new URLSearchParams();
  if(kw)params.set('keyword',kw);
  if($('#f-category').val())params.set('category',$('#f-category').val());
  if($('#f-min-price').val())params.set('minPrice',$('#f-min-price').val());
  if($('#f-max-price').val())params.set('maxPrice',$('#f-max-price').val());
  if(loc)params.set('location',loc);
  // Wire new search dimensions
  if($('#f-theme').length&&$('#f-theme').val())params.set('theme',$('#f-theme').val());
  if($('#f-transport').length&&$('#f-transport').val())params.set('transportMode',$('#f-transport').val());
  if($('#f-min-rating').length&&$('#f-min-rating').val())params.set('minRating',$('#f-min-rating').val());
  if($('#f-avail-date').length&&$('#f-avail-date').val())params.set('availableDate',$('#f-avail-date').val());
  params.set('page',this.page);params.set('size',this.perPage);
  API.get('/api/listings/search?'+params.toString()).done(resp=>{
   // Backend returns {items:[], page, size, total, totalPages}
   this.results=this.sortResults(resp.items||[]);
   this.totalFromServer=resp.total||0;
   this.totalPagesFromServer=resp.totalPages||1;
   this.renderResults();
  });
 },
 sortResults(items){
  const s=$('#f-sort').val();
  if(s==='price-asc')items.sort((a,b)=>a.price-b.price);
  else if(s==='price-desc')items.sort((a,b)=>b.price-a.price);
  else if(s==='duration')items.sort((a,b)=>a.durationMinutes-b.durationMinutes);
  else if(s==='rating')items.sort((a,b)=>(b.rating||0)-(a.rating||0));
  return items;
 },
 renderResults(){
  const grid=$('#listing-results').empty();
  const pageData=this.results;
  if(!pageData.length){grid.html('<div class="card text-center text-muted" style="grid-column:1/-1;padding:3rem">No listings found. Try adjusting your filters.</div>');$('#search-pagination').empty();return}
  pageData.forEach(l=>{
   grid.append(`<div class="listing-card" data-id="${l.id}">
    <div class="listing-card-body">
     <h3>${escHtml(l.title)}</h3>
     <div class="price">$${l.price}</div>
     <p class="text-muted mt-1" style="font-size:.8rem">${escHtml((l.description||'').substring(0,100))}</p>
     <div class="meta mt-1"><span>${l.durationMinutes} min</span><span>${escHtml(l.category||'')}</span><span>${escHtml(l.location||'')}</span></div>
     <div class="meta"><span>by ${escHtml(l.photographerName)}</span></div>
    </div></div>`);
  });
  grid.find('.listing-card').on('click',function(){ListingDetailPage.listingId=$(this).data('id');App.navigate('listing-detail')});
  this.renderPagination();
 },
 renderPagination(){
  const total=this.totalPagesFromServer||1;const pg=$('#search-pagination').empty();
  if(total<=1)return;
  pg.append(`<button class="page-btn" ${this.page<=1?'disabled':''} data-p="${this.page-1}">Prev</button>`);
  for(let i=1;i<=Math.min(total,10);i++)pg.append(`<button class="page-btn ${i===this.page?'active':''}" data-p="${i}">${i}</button>`);
  pg.append(`<button class="page-btn" ${this.page>=total?'disabled':''} data-p="${this.page+1}">Next</button>`);
  pg.find('.page-btn').on('click',function(){if(!$(this).is(':disabled')){SearchPage.page=parseInt($(this).data('p'));SearchPage.doSearch()}});
 }
};

// ================================================================
//  LISTING DETAIL PAGE
// ================================================================
const ListingDetailPage={
 listingId:null,
 render(C){
  if(!this.listingId){C.html('<div class="card">No listing selected</div>');return}
  API.get('/api/listings/'+this.listingId).done(l=>{
   const now=new Date().toISOString().split('T')[0];
   const future=new Date(Date.now()+30*86400000).toISOString().split('T')[0];
   C.html(`<div class="card">
    <button class="btn btn-outline btn-sm mb-1" id="back-search">Back to Search</button>
    <h2>${escHtml(l.title)}</h2>
    <div class="d-flex gap-2 items-center mt-1"><span class="price" style="font-size:1.4rem;font-weight:700;color:var(--pri)">$${l.price}</span>
     <span class="text-muted">${l.durationMinutes} min</span><span class="badge badge-confirmed">${escHtml(l.category||'')}</span></div>
    <p class="mt-1">${escHtml(l.description||'')}</p>
    <div class="mt-1 text-muted"><strong>Location:</strong> ${escHtml(l.location||'N/A')} &bull; <strong>Photographer:</strong> ${escHtml(l.photographerName)}</div>
    <div class="mt-1 text-muted"><strong>Estimated Duration / ETA:</strong> ${l.durationMinutes} minutes from scheduled start time</div>
   </div>
   <div class="card"><div class="card-header"><h2>Available Time Slots</h2></div>
    <div class="table-wrap"><table id="slots-table"><thead><tr><th>Date</th><th>Time</th><th>Available</th><th></th></tr></thead><tbody></tbody></table></div>
   </div>
   <div class="card" id="booking-options" style="display:none">
    <h3 class="mb-1">Booking Options</h3>
    <div class="form-row">
     <div class="form-group"><label>Delivery Mode</label><select class="form-control" id="bo-delivery"><option value="ONSITE">On-site (attend in person)</option><option value="PICKUP">Pickup (collect finished work)</option><option value="COURIER">Courier (deliver to address)</option></select></div>
     <div class="form-group" id="bo-addr-group" style="display:none"><label>Delivery Address</label><select class="form-control" id="bo-address"></select></div>
    </div>
    <button class="btn btn-primary" id="bo-confirm-btn">Confirm Booking</button>
   </div>`);
   $('#back-search').on('click',()=>App.navigate('search'));
   $('#bo-delivery').on('change',function(){
    if($(this).val()==='COURIER'){$('#bo-addr-group').show();API.get('/api/addresses').done(addrs=>{const sel=$('#bo-address').empty();addrs.forEach(a=>sel.append(`<option value="${a.id}">${a.label}: ${a.street}, ${a.city}</option>`))})}
    else{$('#bo-addr-group').hide()}
   });
   API.get(`/api/timeslots/listing/${l.id}/available?start=${now}&end=${future}`).done(slots=>{
    const tb=$('#slots-table tbody');
    if(!slots.length){tb.append('<tr><td colspan="4" class="text-muted text-center">No available slots in the next 30 days</td></tr>');return}
    slots.forEach(s=>{
     const avail=s.capacity-s.bookedCount;
     tb.append(`<tr><td>${s.slotDate}</td><td>${s.startTime} - ${s.endTime}</td>
      <td>${avail} of ${s.capacity}</td>
      <td><button class="btn btn-primary btn-sm btn-book" data-slot="${s.id}" data-listing="${l.id}" data-price="${l.price}">Book Now</button></td></tr>`);
    });
    tb.find('.btn-book').on('click',function(){ListingDetailPage.bookSlot($(this).data('listing'),$(this).data('slot'),$(this).data('price'))});
   });
  });
 },
 bookSlot(listingId,slotId,price){
  // Show booking options panel instead of immediate confirm
  this._pendingSlot={listingId,slotId,price};
  $('#booking-options').show();
  $('html,body').animate({scrollTop:$('#booking-options').offset().top-80},300);
  $('#bo-confirm-btn').off('click').on('click',()=>{
   const mode=$('#bo-delivery').val();
   const addrId=mode==='COURIER'?parseInt($('#bo-address').val()):null;
   if(mode==='COURIER'&&!addrId){alert('Please select a delivery address');return}
   const body={listingId,timeSlotId:slotId,deliveryMode:mode};
   if(addrId)body.addressId=addrId;
   API.post('/api/orders',body,API.idemHeader()).done(order=>{
    alert('Order '+order.orderNumber+' created! Payment due within 30 minutes.');
    OrderDetailPage.orderId=order.id;App.navigate('order-detail');
   }).fail(xhr=>alert(xhr.responseJSON?xhr.responseJSON.error:'Booking failed'));
  });
 }
};

// ================================================================
//  ORDERS PAGE
// ================================================================
const OrdersPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>My Orders</h2></div>
   <div class="table-wrap"><table id="orders-table"><thead><tr><th>Order #</th><th>Listing</th><th>Date</th><th>Status</th><th>Price</th><th>Actions</th></tr></thead><tbody></tbody></table></div></div>`);
  API.get('/api/orders').done(orders=>{
   const tb=$('#orders-table tbody');
   if(!orders.length){tb.append('<tr><td colspan="6" class="text-muted text-center">No orders yet</td></tr>');return}
   orders.forEach(o=>{
    tb.append(`<tr><td><a href="#" class="view-order" data-id="${o.id}">${o.orderNumber}</a></td>
     <td>${escHtml(o.listingTitle)}</td><td>${o.slotDate||'-'}</td>
     <td>${statusBadge(o.status)}</td><td>$${o.totalPrice}</td>
     <td><button class="btn btn-sm btn-outline view-order" data-id="${o.id}">View</button></td></tr>`);
   });
   tb.find('.view-order').on('click',function(e){e.preventDefault();OrderDetailPage.orderId=$(this).data('id');App.navigate('order-detail')});
  });
 }
};

// ================================================================
//  ORDER DETAIL PAGE
// ================================================================
const OrderDetailPage={
 orderId:null,
 render(C){
  if(!this.orderId){C.html('<div class="card">No order selected</div>');return}
  API.get('/api/orders/'+this.orderId).done(o=>{
   const steps=['CREATED','CONFIRMED','PAID','CHECKED_IN','CHECKED_OUT','COMPLETED'];
   const ci=steps.indexOf(o.status);const isCancelled=o.status==='CANCELLED'||o.status==='REFUNDED';
   let tracker='<div class="status-tracker">';
   steps.forEach((s,i)=>{
    const cls=isCancelled?(i===0?'cancelled':''):(i<ci?'done':(i===ci?'active':''));
    const dot=i<ci?'&#10003;':(i===ci?(isCancelled?'&#10007;':i+1):i+1);
    tracker+=`<div class="status-step ${cls}"><div class="step-dot">${dot}</div><div class="step-label">${s.replace('_',' ')}</div></div>`;
    if(i<steps.length-1)tracker+=`<div class="status-connector ${i<ci?'done':''}"></div>`;
   });
   if(isCancelled)tracker+=`<div class="status-connector"></div><div class="status-step cancelled"><div class="step-dot">&#10007;</div><div class="step-label">${o.status}</div></div>`;
   tracker+='</div>';

   C.html(`<div class="card">
    <button class="btn btn-outline btn-sm mb-1" id="back-orders">Back to Orders</button>
    <div class="d-flex justify-between items-center"><h2>Order ${escHtml(o.orderNumber)}</h2>${statusBadge(o.status)}</div>
    ${tracker}
    <div class="form-row mt-2">
     <div><strong>Listing:</strong> ${escHtml(o.listingTitle)}<br><strong>Date:</strong> ${o.slotDate||'-'}<br><strong>Time:</strong> ${o.slotTime||'-'}<br><strong>Customer:</strong> ${escHtml(o.customerName)}</div>
     <div><strong>Photographer:</strong> ${escHtml(o.photographerName)}<br><strong>Total:</strong> $${o.totalPrice}<br><strong>Paid:</strong> $${o.paidAmount||0}<br><strong>Payment Due:</strong> ${fmtDateTime(o.paymentDeadline)}</div>
    </div>
    ${o.cancelReason?'<div class="alert alert-error mt-1"><strong>Cancel Reason:</strong> '+escHtml(o.cancelReason)+'</div>':''}
    ${o.refundAmount&&o.refundAmount>0?'<div class="alert alert-info mt-1"><strong>Refund:</strong> $'+o.refundAmount+'</div>':''}
    <div class="btn-group mt-2" id="order-actions"></div>
   </div>
   <div class="card"><div class="card-header"><h2>Audit Trail</h2></div><div class="table-wrap"><table id="audit-table"><thead><tr><th>Time</th><th>Action</th><th>From</th><th>To</th><th>By</th><th>Detail</th></tr></thead><tbody></tbody></table></div></div>`);

   $('#back-orders').on('click',()=>App.navigate('orders'));
   this.renderActions(o);
   API.get('/api/orders/'+o.id+'/audit').done(trail=>{
    const tb=$('#audit-table tbody');
    trail.forEach(a=>tb.append(`<tr><td>${fmtDateTime(a.createdAt)}</td><td>${a.action}</td><td>${a.fromStatus||'-'}</td><td>${a.toStatus}</td><td>${escHtml(a.performedByName)}</td><td>${escHtml((a.detail||'').substring(0,80))}</td></tr>`));
   });
  });
 },
 renderActions(o){
  const acts=$('#order-actions').empty();const r=App.user.role;const s=o.status;
  if(s==='CREATED'&&(r==='PHOTOGRAPHER'||r==='ADMINISTRATOR'))
   acts.append('<button class="btn btn-success" data-act="confirm">Confirm</button>');
  if(s==='CONFIRMED'&&(r==='CUSTOMER'||r==='ADMINISTRATOR'))
   acts.append('<button class="btn btn-primary" data-act="pay">Record Payment</button>');
  if(s==='PAID'&&(r==='PHOTOGRAPHER'||r==='ADMINISTRATOR'))
   acts.append('<button class="btn btn-warning" data-act="check-in">Check In</button>');
  if(s==='CHECKED_IN'&&(r==='PHOTOGRAPHER'||r==='ADMINISTRATOR'))
   acts.append('<button class="btn btn-warning" data-act="check-out">Check Out</button>');
  if(s==='CHECKED_OUT'&&(r==='PHOTOGRAPHER'||r==='ADMINISTRATOR'))
   acts.append('<button class="btn btn-success" data-act="complete">Complete</button>');
  if(['CREATED','CONFIRMED','PAID','CHECKED_IN'].includes(s))
   acts.append('<button class="btn btn-danger" data-act="cancel">Cancel</button>');
  if(s==='COMPLETED'&&r==='ADMINISTRATOR')
   acts.append('<button class="btn btn-danger" data-act="refund">Refund</button>');
  if(['CREATED','CONFIRMED','PAID'].includes(s))
   acts.append('<button class="btn btn-outline" data-act="reschedule">Reschedule</button>');

  acts.find('button').on('click',function(){OrderDetailPage.doAction(o,$(this).data('act'))});
 },
 doAction(order,act){
  const h=API.idemHeader();const reload=()=>this.render($('#page-content').empty());
  if(act==='confirm'){API.post('/api/orders/'+order.id+'/confirm',{},h).done(reload).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'))}
  else if(act==='pay'){
   const ref=prompt('Enter payment reference:');if(!ref)return;
   API.post('/api/orders/'+order.id+'/pay',{amount:order.totalPrice,paymentReference:ref},h).done(reload).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'));
  }
  else if(act==='check-in'){API.post('/api/orders/'+order.id+'/check-in',{},h).done(reload)}
  else if(act==='check-out'){API.post('/api/orders/'+order.id+'/check-out',{},h).done(reload)}
  else if(act==='complete'){API.post('/api/orders/'+order.id+'/complete',{},h).done(reload)}
  else if(act==='cancel'){const reason=prompt('Reason for cancellation:');if(!reason)return;API.post('/api/orders/'+order.id+'/cancel',{reason},h).done(reload).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'))}
  else if(act==='refund'){const amt=prompt('Refund amount:',order.paidAmount);if(!amt)return;API.post('/api/orders/'+order.id+'/refund',{amount:parseFloat(amt),reason:'Admin refund'},h).done(reload)}
  else if(act==='reschedule'){alert('Select a new time slot from the listing page to reschedule.')}
 }
};

// ================================================================
//  ADDRESS PAGE
// ================================================================
const AddressPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>My Addresses</h2><button class="btn btn-primary" id="add-addr">+ Add Address</button></div>
   <div class="addr-grid" id="addr-list"></div></div>
   <div class="modal-overlay" id="addr-modal"><div class="modal"><div class="modal-header"><h3 id="addr-modal-title">Add Address</h3><button class="modal-close" id="close-addr">&times;</button></div>
    <form id="addr-form">
     <input type="hidden" id="af-id"/>
     <div class="form-group"><label>Label</label><input class="form-control" id="af-label" placeholder="e.g. Home, Work" data-validate="required"/><div class="invalid-feedback"></div></div>
     <div class="form-group"><label>Street</label><input class="form-control" id="af-street" data-validate="required"/><div class="invalid-feedback"></div></div>
     <div class="form-row">
      <div class="form-group"><label>City</label><input class="form-control" id="af-city" data-validate="required"/><div class="invalid-feedback"></div></div>
      <div class="form-group"><label>State (2-letter code)</label><input class="form-control" id="af-state" maxlength="2" style="text-transform:uppercase" data-validate="required,state"/><div class="invalid-feedback"></div></div>
     </div>
     <div class="form-row">
      <div class="form-group"><label>ZIP Code</label><input class="form-control" id="af-zip" data-validate="required,zip"/><div class="invalid-feedback"></div></div>
      <div class="form-group"><label>Country</label><input class="form-control" id="af-country" value="US" readonly/></div>
     </div>
     <div class="form-check"><input type="checkbox" id="af-default"/><label for="af-default">Set as default address</label></div>
     <div class="modal-footer"><button type="button" class="btn btn-outline" id="cancel-addr">Cancel</button><button type="submit" class="btn btn-primary">Save</button></div>
    </form></div></div>`);
  Validate.live('#addr-form');
  $('#add-addr').on('click',()=>this.openForm());
  $('#close-addr,#cancel-addr').on('click',()=>$('#addr-modal').removeClass('active'));
  $('#addr-form').on('submit',e=>{e.preventDefault();this.save()});
  this.load();
 },
 load(){
  API.get('/api/addresses').done(addrs=>{
   const grid=$('#addr-list').empty();
   if(!addrs.length){grid.html('<div class="text-muted">No addresses yet. Add your first address.</div>');return}
   addrs.forEach(a=>{
    grid.append(`<div class="addr-card ${a.isDefault?'default':''}">
     ${a.isDefault?'<div class="addr-default">DEFAULT</div>':''}
     <div class="addr-label">${escHtml(a.label||'Address')}</div>
     <div class="addr-line">${escHtml(a.street)}</div>
     <div class="addr-line">${escHtml(a.city)}, ${escHtml(a.state)} ${escHtml(a.postalCode)}</div>
     <div class="addr-line">${escHtml(a.country)}</div>
     <div class="addr-actions">
      <button class="btn btn-sm btn-outline edit-addr" data-id="${a.id}">Edit</button>
      <button class="btn btn-sm btn-danger del-addr" data-id="${a.id}">Delete</button>
      ${!a.isDefault?`<button class="btn btn-sm btn-primary set-default" data-id="${a.id}">Set Default</button>`:''}
     </div></div>`);
   });
   grid.find('.edit-addr').on('click',function(){const id=$(this).data('id');API.get('/api/addresses/'+id).done(a=>AddressPage.openForm(a))});
   grid.find('.del-addr').on('click',function(){if(confirm('Delete this address?'))API.del('/api/addresses/'+$(this).data('id')).done(()=>AddressPage.load())});
   grid.find('.set-default').on('click',function(){const id=$(this).data('id');API.get('/api/addresses/'+id).done(a=>{a.isDefault=true;API.put('/api/addresses/'+id,a).done(()=>AddressPage.load())})});
  });
 },
 openForm(addr){
  $('#addr-form')[0].reset();$('#addr-form .form-control').removeClass('is-valid is-invalid');
  $('#af-id').val(addr?addr.id:'');$('#addr-modal-title').text(addr?'Edit Address':'Add Address');
  if(addr){$('#af-label').val(addr.label);$('#af-street').val(addr.street);$('#af-city').val(addr.city);$('#af-state').val(addr.state);$('#af-zip').val(addr.postalCode);$('#af-country').val(addr.country||'US');$('#af-default').prop('checked',addr.isDefault)}
  else{$('#af-country').val('US')}
  $('#addr-modal').addClass('active');
 },
 save(){
  if(!Validate.checkForm('#addr-form'))return;
  const id=$('#af-id').val();
  const data={label:$('#af-label').val(),street:$('#af-street').val(),city:$('#af-city').val(),state:$('#af-state').val().toUpperCase(),postalCode:$('#af-zip').val(),country:$('#af-country').val(),isDefault:$('#af-default').is(':checked')};
  // Validate state+zip consistency (first digit of ZIP should loosely match state region)
  const p=id?API.put('/api/addresses/'+id,data):API.post('/api/addresses',data);
  p.done(()=>{$('#addr-modal').removeClass('active');this.load()}).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'));
 }
};

// ================================================================
//  CHAT PAGE
// ================================================================
const ChatPage={
 activeConv:null,pollTimer:null,
 render(C){
  C.html(`<div class="card" style="padding:0"><div class="chat-container">
   <div class="chat-sidebar" id="chat-sidebar"></div>
   <div class="chat-main">
    <div class="chat-messages" id="chat-messages"><div class="text-center text-muted" style="padding:3rem">Select a conversation</div></div>
    <div class="chat-input-area">
     <label class="img-upload-btn" title="Send image (JPEG/PNG, max 5MB)">IMG<input type="file" accept="image/jpeg,image/png" id="chat-img-input" style="display:none"/></label>
     <input type="text" class="form-control" id="chat-input" placeholder="Type a message..."/>
     <button class="btn btn-primary btn-sm" id="chat-send">Send</button>
    </div>
   </div></div></div>`);
  this.loadConversations();
  $('#chat-send').on('click',()=>this.sendMsg());
  $('#chat-input').on('keydown',e=>{if(e.key==='Enter')this.sendMsg()});
  $('#chat-img-input').on('change',e=>{if(e.target.files[0])this.sendImage(e.target.files[0])});
  // Poll for new messages every 5 seconds
  if(this.pollTimer)clearInterval(this.pollTimer);
  this.pollTimer=setInterval(()=>{if(this.activeConv)this.loadMessages(this.activeConv,true)},5000);
 },
 loadConversations(){
  API.get('/api/messages/conversations').done(convs=>{
   const sb=$('#chat-sidebar').empty();
   if(!convs.length){sb.html('<div class="text-muted" style="padding:1rem;font-size:.85rem">No conversations yet</div>');return}
   convs.forEach(c=>{
    const other=c.participantOne===App.user.id?c.participantTwoName:c.participantOneName;
    sb.append(`<div class="chat-conv-item ${this.activeConv===c.id?'active':''}" data-id="${c.id}">
     <div class="d-flex justify-between items-center"><span class="conv-name">${escHtml(other)}</span>
      ${c.unreadCount>0?`<span class="unread-badge">${c.unreadCount}</span>`:''}</div>
     <div class="conv-preview">${c.lastMessageAt?fmtDateTime(c.lastMessageAt):'No messages'}</div></div>`);
   });
   sb.find('.chat-conv-item').on('click',function(){ChatPage.activeConv=$(this).data('id');ChatPage.loadMessages($(this).data('id'));sb.find('.chat-conv-item').removeClass('active');$(this).addClass('active')});
  });
 },
 loadMessages(convId,silent){
  API.get('/api/messages/conversations/'+convId).done(msgs=>{
   const el=$('#chat-messages').empty();
   msgs.forEach(m=>{
    const mine=m.senderId===App.user.id;
    let html=`<div class="chat-msg ${mine?'mine':''}"><div class="msg-bubble">${escHtml(m.content)}`;
    if(m.attachments&&m.attachments.length){
     m.attachments.forEach(a=>{html+=`<br><img src="/api/messages/attachments/${a.id}/download" class="msg-img" alt="${escHtml(a.originalName)}"/>`});
    }
    html+=`</div><div class="msg-meta">${escHtml(m.senderName||'')} &bull; ${fmtDateTime(m.createdAt)}`;
    if(mine&&m.readAt) html+=' &bull; Read';
    else if(mine) html+=' &bull; Sent';
    html+=`</div></div>`;
    el.append(html);
   });
   el.scrollTop(el[0].scrollHeight);
   if(!silent)this.loadConversations();
  });
 },
 sendMsg(){
  const txt=$('#chat-input').val().trim();if(!txt||!this.activeConv)return;
  API.post('/api/messages/conversations/'+this.activeConv+'/reply',{content:txt}).done(()=>{
   $('#chat-input').val('');this.loadMessages(this.activeConv);this.loadConversations();
  });
 },
 sendImage(file){
  if(!this.activeConv)return;
  if(!['image/jpeg','image/png'].includes(file.type)){alert('Only JPEG and PNG images are allowed');return}
  if(file.size>5*1024*1024){alert('File size must not exceed 5 MB');return}
  const fd=new FormData();fd.append('file',file);
  API.upload('/api/messages/conversations/'+this.activeConv+'/image',fd).done(()=>{
   this.loadMessages(this.activeConv);this.loadConversations();$('#chat-img-input').val('');
  }).fail(xhr=>alert(xhr.responseJSON?.error||'Upload failed'));
 }
};

// ================================================================
//  NOTIFICATIONS PAGE
// ================================================================
const NotifPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>Notifications</h2><button class="btn btn-outline btn-sm" id="notif-prefs-btn">Preferences</button></div>
   <div id="notif-list"></div></div>
   <div class="modal-overlay" id="notif-pref-modal"><div class="modal"><div class="modal-header"><h3>Notification Preferences</h3><button class="modal-close" id="close-notif-pref">&times;</button></div>
    <form id="notif-pref-form">
     <p class="text-muted mb-2" style="font-size:.82rem">Toggle which notification types you receive. Compliance notices cannot be muted.</p>
     <div class="form-group d-flex justify-between items-center"><label>Order Updates</label><label class="toggle"><input type="checkbox" id="np-orders"/><span class="slider"></span></label></div>
     <div class="form-group d-flex justify-between items-center"><label>Holds</label><label class="toggle"><input type="checkbox" id="np-holds"/><span class="slider"></span></label></div>
     <div class="form-group d-flex justify-between items-center"><label>Reminders</label><label class="toggle"><input type="checkbox" id="np-reminders"/><span class="slider"></span></label></div>
     <div class="form-group d-flex justify-between items-center"><label>Approvals</label><label class="toggle"><input type="checkbox" id="np-approvals"/><span class="slider"></span></label></div>
     <div class="form-group d-flex justify-between items-center"><label>Compliance (mandatory)</label><label class="toggle"><input type="checkbox" id="np-compliance" checked disabled/><span class="slider"></span></label></div>
     <div class="form-group d-flex justify-between items-center" style="border-top:1px solid var(--g200);padding-top:.75rem"><label><strong>Mute All Non-Critical</strong></label><label class="toggle"><input type="checkbox" id="np-mute"/><span class="slider"></span></label></div>
     <div class="modal-footer"><button type="button" class="btn btn-outline" id="cancel-notif-pref">Cancel</button><button type="submit" class="btn btn-primary">Save</button></div>
    </form></div></div>`);
  $('#notif-prefs-btn').on('click',()=>this.openPrefs());
  $('#close-notif-pref,#cancel-notif-pref').on('click',()=>$('#notif-pref-modal').removeClass('active'));
  $('#notif-pref-form').on('submit',e=>{e.preventDefault();this.savePrefs()});
  this.loadNotifs();
 },
 loadNotifs(){
  API.get('/api/notifications').done(notifs=>{
   const list=$('#notif-list').empty();
   if(!notifs.length){list.html('<div class="text-center text-muted" style="padding:2rem">No notifications</div>');return}
   notifs.forEach(n=>{
    const subj=n.subject||'';
    const iconCls=subj.includes('CANCEL')||subj.includes('COMPLIANCE')?'compliance':subj.includes('HOLD')?'hold':subj.includes('APPROV')?'approval':'order';
    const iconChar={order:'O',hold:'H',approval:'A',compliance:'!'}[iconCls];
    list.append(`<div class="notif-item">
     <div class="notif-icon ${iconCls}">${iconChar}</div>
     <div class="notif-body"><div class="notif-title">${escHtml(subj)}</div><div class="notif-desc">${escHtml((n.body||'').substring(0,120))}</div></div>
     <div class="notif-time">${fmtDateTime(n.createdAt)}</div></div>`);
   });
  });
 },
 openPrefs(){
  API.get('/api/notifications/preferences').done(p=>{
   $('#np-orders').prop('checked',p.orderUpdates);$('#np-holds').prop('checked',p.holds);
   $('#np-reminders').prop('checked',p.reminders);$('#np-approvals').prop('checked',p.approvals);
   $('#np-mute').prop('checked',p.muteNonCritical);$('#notif-pref-modal').addClass('active');
  });
 },
 savePrefs(){
  API.put('/api/notifications/preferences',{orderUpdates:$('#np-orders').is(':checked'),holds:$('#np-holds').is(':checked'),reminders:$('#np-reminders').is(':checked'),approvals:$('#np-approvals').is(':checked'),compliance:true,muteNonCritical:$('#np-mute').is(':checked')})
   .done(()=>{$('#notif-pref-modal').removeClass('active');alert('Preferences saved')});
 }
};

// ================================================================
//  POINTS PAGE (Customer/Photographer)
// ================================================================
const PointsPage={
 render(C){
  C.html(`<div class="stats-grid"><div class="stat-card" id="pts-balance"><div class="stat-label">Your Points</div><div class="stat-value">-</div></div></div>
   <div class="card"><div class="card-header"><h2>Points History</h2></div><div class="table-wrap"><table id="pts-table"><thead><tr><th>Date</th><th>Action</th><th>Points</th><th>Balance</th><th>Description</th></tr></thead><tbody></tbody></table></div></div>
   <div class="card"><div class="card-header"><h2>Leaderboard</h2></div><div class="table-wrap"><table id="lb-table"><thead><tr><th>Rank</th><th>Name</th><th>Points</th><th>Completions</th></tr></thead><tbody></tbody></table></div></div>`);
  API.get('/api/points/balance').done(d=>$('#pts-balance .stat-value').text(d.balance));
  API.get('/api/points/history').done(data=>{
   const tb=$('#pts-table tbody');
   data.forEach(e=>tb.append(`<tr><td>${fmtDateTime(e.createdAt)}</td><td>${e.action}</td><td class="${e.points>=0?'':'text-muted'}" style="font-weight:600">${e.points>0?'+':''}${e.points}</td><td>${e.balanceAfter}</td><td>${escHtml(e.description||'-')}</td></tr>`));
  });
  API.get('/api/points/leaderboard').done(data=>{
   const tb=$('#lb-table tbody');
   data.forEach((e,i)=>{
    const rnk=i===0?'gold':i===1?'silver':i===2?'bronze':'';
    tb.append(`<tr><td><span class="lb-rank ${rnk}">${i+1}</span></td><td>${escHtml(e.fullName||e.username)}</td><td style="font-weight:600">${e.points}</td><td>${e.completionCount||0}</td></tr>`);
   });
  });
 }
};

// ================================================================
//  PHOTOGRAPHER DASHBOARD
// ================================================================
const PhotoDashPage={
 render(C){
  C.html(`<h2 class="mb-2">Welcome, ${escHtml(App.user.fullName)}</h2><div class="stats-grid" id="photo-stats"></div>
   <div class="card"><div class="card-header"><h2>Upcoming Orders</h2></div><div class="table-wrap"><table id="photo-orders"><thead><tr><th>Order</th><th>Customer</th><th>Date</th><th>Status</th><th></th></tr></thead><tbody></tbody></table></div></div>`);
  API.get('/api/orders').done(orders=>{
   const pending=orders.filter(o=>o.status==='CREATED').length;
   const confirmed=orders.filter(o=>['CONFIRMED','PAID'].includes(o.status)).length;
   const active=orders.filter(o=>['CHECKED_IN','CHECKED_OUT'].includes(o.status)).length;
   $('#photo-stats').html(
    `<div class="stat-card orange"><div class="stat-label">Pending</div><div class="stat-value">${pending}</div></div>`+
    `<div class="stat-card"><div class="stat-label">Confirmed</div><div class="stat-value">${confirmed}</div></div>`+
    `<div class="stat-card green"><div class="stat-label">Active</div><div class="stat-value">${active}</div></div>`+
    `<div class="stat-card"><div class="stat-label">Total</div><div class="stat-value">${orders.length}</div></div>`);
   const tb=$('#photo-orders tbody');
   orders.filter(o=>!['COMPLETED','CANCELLED','REFUNDED'].includes(o.status)).slice(0,10).forEach(o=>{
    tb.append(`<tr><td>${o.orderNumber}</td><td>${escHtml(o.customerName)}</td><td>${o.slotDate||'-'}</td><td>${statusBadge(o.status)}</td><td><button class="btn btn-sm btn-outline view-order" data-id="${o.id}">View</button></td></tr>`);
   });
   tb.find('.view-order').on('click',function(){OrderDetailPage.orderId=$(this).data('id');App.navigate('order-detail')});
  });
 }
};

// ================================================================
//  MY LISTINGS (Photographer)
// ================================================================
const MyListingsPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>My Listings</h2><button class="btn btn-primary" id="add-listing">+ New Listing</button></div>
   <div class="table-wrap"><table id="my-listings-table"><thead><tr><th>Title</th><th>Category</th><th>Price</th><th>Duration</th><th>Active</th><th>Actions</th></tr></thead><tbody></tbody></table></div></div>
   <div class="modal-overlay" id="listing-modal"><div class="modal"><div class="modal-header"><h3 id="listing-modal-title">New Listing</h3><button class="modal-close" id="close-listing">&times;</button></div>
    <form id="listing-form"><input type="hidden" id="lf-id"/>
     <div class="form-group"><label>Title</label><input class="form-control" id="lf-title" data-validate="required"/><div class="invalid-feedback"></div></div>
     <div class="form-group"><label>Description</label><textarea class="form-control" id="lf-desc"></textarea></div>
     <div class="form-row">
      <div class="form-group"><label>Category</label><select class="form-control" id="lf-cat"><option>PORTRAIT</option><option>WEDDING</option><option>EVENT</option><option>FAMILY</option><option>PRODUCT</option></select></div>
      <div class="form-group"><label>Price ($)</label><input type="number" step="0.01" class="form-control" id="lf-price" data-validate="required"/><div class="invalid-feedback"></div></div>
     </div>
     <div class="form-row">
      <div class="form-group"><label>Duration (min)</label><input type="number" class="form-control" id="lf-dur" data-validate="required"/><div class="invalid-feedback"></div></div>
      <div class="form-group"><label>Location</label><input class="form-control" id="lf-loc"/></div>
     </div>
     <div class="form-check"><input type="checkbox" id="lf-active" checked/><label for="lf-active">Active</label></div>
     <div class="modal-footer"><button type="button" class="btn btn-outline" id="cancel-listing">Cancel</button><button type="submit" class="btn btn-primary">Save</button></div>
    </form></div></div>`);
  Validate.live('#listing-form');
  $('#add-listing').on('click',()=>this.openForm());
  $('#close-listing,#cancel-listing').on('click',()=>$('#listing-modal').removeClass('active'));
  $('#listing-form').on('submit',e=>{e.preventDefault();this.save()});
  this.load();
 },
 load(){
  API.get('/api/listings/my').done(listings=>{
   const tb=$('#my-listings-table tbody').empty();
   listings.forEach(l=>tb.append(`<tr><td>${escHtml(l.title)}</td><td>${l.category||'-'}</td><td>$${l.price}</td><td>${l.durationMinutes} min</td><td>${l.active?'Yes':'No'}</td><td><button class="btn btn-sm btn-outline edit-listing" data-id="${l.id}">Edit</button> <button class="btn btn-sm btn-outline manage-slots" data-id="${l.id}">Slots</button></td></tr>`));
   tb.find('.edit-listing').on('click',function(){API.get('/api/listings/'+$(this).data('id')).done(l=>MyListingsPage.openForm(l))});
   tb.find('.manage-slots').on('click',function(){MyListingsPage.manageSlots($(this).data('id'))});
  });
 },
 openForm(l){
  $('#listing-form')[0].reset();$('#lf-id').val(l?l.id:'');$('#listing-modal-title').text(l?'Edit Listing':'New Listing');
  if(l){$('#lf-title').val(l.title);$('#lf-desc').val(l.description);$('#lf-cat').val(l.category);$('#lf-price').val(l.price);$('#lf-dur').val(l.durationMinutes);$('#lf-loc').val(l.location);$('#lf-active').prop('checked',l.active)}
  $('#listing-modal').addClass('active');
 },
 save(){
  if(!Validate.checkForm('#listing-form'))return;
  const id=$('#lf-id').val();
  const data={title:$('#lf-title').val(),description:$('#lf-desc').val(),category:$('#lf-cat').val(),price:parseFloat($('#lf-price').val()),durationMinutes:parseInt($('#lf-dur').val()),location:$('#lf-loc').val(),active:$('#lf-active').is(':checked')};
  (id?API.put('/api/listings/'+id,data):API.post('/api/listings',data)).done(()=>{$('#listing-modal').removeClass('active');this.load()}).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'));
 },
 manageSlots(listingId){
  const modal=$(`<div class="modal-overlay active" id="slots-modal"><div class="modal modal-lg"><div class="modal-header"><h3>Time Slots</h3><button class="modal-close">&times;</button></div>
   <div class="form-row mb-2"><div class="form-group"><label>Date</label><input type="date" class="form-control" id="ns-date"/></div><div class="form-group"><label>Start</label><input type="time" class="form-control" id="ns-start"/></div><div class="form-group"><label>End</label><input type="time" class="form-control" id="ns-end"/></div><div class="form-group"><label>Capacity</label><input type="number" class="form-control" id="ns-cap" value="1" min="1"/></div><div class="form-group" style="align-self:end"><button class="btn btn-primary" id="add-slot-btn">Add Slot</button></div></div>
   <div class="table-wrap"><table><thead><tr><th>Date</th><th>Time</th><th>Capacity</th><th>Booked</th></tr></thead><tbody id="slots-list"></tbody></table></div></div></div>`);
  $('body').append(modal);
  modal.find('.modal-close').on('click',()=>modal.remove());
  const loadSlots=()=>{API.get('/api/timeslots/listing/'+listingId).done(slots=>{const tb=$('#slots-list').empty();slots.forEach(s=>tb.append(`<tr><td>${s.slotDate}</td><td>${s.startTime}-${s.endTime}</td><td>${s.capacity}</td><td>${s.bookedCount}</td></tr>`))})};
  loadSlots();
  $('#add-slot-btn').on('click',()=>{
   API.post('/api/timeslots',{listingId,slotDate:$('#ns-date').val(),startTime:$('#ns-start').val(),endTime:$('#ns-end').val(),capacity:parseInt($('#ns-cap').val())}).done(()=>loadSlots()).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'));
  });
 }
};

// ================================================================
//  ADMIN DASHBOARD
// ================================================================
const AdminDashPage={
 render(C){
  C.html(`<h2 class="mb-2">Admin Dashboard</h2><div class="stats-grid" id="admin-stats"></div>
   <div class="card"><div class="card-header"><h2>Recent Orders</h2></div><div class="table-wrap"><table id="admin-orders"><thead><tr><th>Order</th><th>Customer</th><th>Photographer</th><th>Status</th><th>Price</th><th></th></tr></thead><tbody></tbody></table></div></div>`);
  API.get('/api/orders').done(orders=>{
   const stats={total:orders.length,created:0,paid:0,completed:0};
   orders.forEach(o=>{if(o.status==='CREATED')stats.created++;if(o.status==='PAID')stats.paid++;if(o.status==='COMPLETED')stats.completed++});
   $('#admin-stats').html(
    `<div class="stat-card"><div class="stat-label">Total Orders</div><div class="stat-value">${stats.total}</div></div>`+
    `<div class="stat-card orange"><div class="stat-label">Awaiting Payment</div><div class="stat-value">${stats.created}</div></div>`+
    `<div class="stat-card"><div class="stat-label">Paid</div><div class="stat-value">${stats.paid}</div></div>`+
    `<div class="stat-card green"><div class="stat-label">Completed</div><div class="stat-value">${stats.completed}</div></div>`);
   const tb=$('#admin-orders tbody');
   orders.slice(0,15).forEach(o=>tb.append(`<tr><td><a href="#" class="view-order" data-id="${o.id}">${o.orderNumber}</a></td><td>${escHtml(o.customerName)}</td><td>${escHtml(o.photographerName)}</td><td>${statusBadge(o.status)}</td><td>$${o.totalPrice}</td><td><button class="btn btn-sm btn-outline view-order" data-id="${o.id}">View</button></td></tr>`));
   tb.find('.view-order').on('click',function(e){e.preventDefault();OrderDetailPage.orderId=$(this).data('id');App.navigate('order-detail')});
  });
 }
};

// ================================================================
//  USERS ADMIN
// ================================================================
const UsersAdminPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>User Management</h2></div>
   <div class="table-wrap"><table id="users-table"><thead><tr><th>Username</th><th>Name</th><th>Email</th><th>Role</th><th>Points</th><th>Enabled</th><th>Actions</th></tr></thead><tbody></tbody></table></div></div>`);
  API.get('/api/users').done(users=>{
   const tb=$('#users-table tbody');
   users.forEach(u=>tb.append(`<tr><td>${escHtml(u.username)}</td><td>${escHtml(u.fullName)}</td><td>${escHtml(u.email)}</td><td>${u.roleName}</td><td>${u.pointsBalance||0}</td><td>${u.enabled?'Yes':'No'}</td><td>
    <button class="btn btn-sm ${u.enabled?'btn-danger':'btn-success'} toggle-user" data-id="${u.id}" data-en="${!u.enabled}">${u.enabled?'Disable':'Enable'}</button></td></tr>`));
   tb.find('.toggle-user').on('click',function(){API.patch('/api/users/'+$(this).data('id')+'/enabled',{enabled:$(this).data('en')===true||$(this).data('en')==='true'}).done(()=>UsersAdminPage.render($('#page-content').empty()))});
  });
 }
};

// ================================================================
//  BLACKLIST ADMIN
// ================================================================
const BlacklistAdminPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>Blacklist Management</h2><button class="btn btn-danger" id="add-blacklist">+ Blacklist User</button></div>
   <div class="table-wrap"><table id="bl-table"><thead><tr><th>User</th><th>Reason</th><th>Duration</th><th>Expires</th><th>Active</th><th>Actions</th></tr></thead><tbody></tbody></table></div></div>`);
  API.get('/api/blacklist').done(entries=>{
   const tb=$('#bl-table tbody');
   entries.forEach(e=>tb.append(`<tr><td>${escHtml(e.userName)}</td><td>${escHtml((e.reason||'').substring(0,50))}</td><td>${e.durationDays} days</td><td>${fmtDateTime(e.expiresAt)}</td><td>${e.active?'Yes':'No'}</td><td>${e.active?`<button class="btn btn-sm btn-success lift-bl" data-id="${e.id}">Lift</button>`:'-'}</td></tr>`));
   tb.find('.lift-bl').on('click',function(){const reason=prompt('Reason for lifting:');if(reason)API.post('/api/blacklist/'+$(this).data('id')+'/lift',{reason}).done(()=>BlacklistAdminPage.render($('#page-content').empty()))});
  });
  $('#add-blacklist').on('click',()=>{
   const userId=prompt('User ID to blacklist:');if(!userId)return;
   const reason=prompt('Reason:');if(!reason)return;
   const days=prompt('Duration (days, default 7):','7');
   API.post('/api/blacklist',{userId:parseInt(userId),reason,durationDays:parseInt(days||'7')}).done(()=>BlacklistAdminPage.render($('#page-content').empty())).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'));
  });
 }
};

// ================================================================
//  SERVICES ADMIN
// ================================================================
const ServicesAdminPage={
 render(C){
  C.html(`<div class="card"><div class="card-header"><h2>Services</h2><button class="btn btn-primary" id="add-svc">+ New Service</button></div>
   <div class="table-wrap"><table id="svc-table"><thead><tr><th>Name</th><th>Price</th><th>Duration</th><th>Active</th><th></th></tr></thead><tbody></tbody></table></div></div>
   <div class="modal-overlay" id="svc-modal"><div class="modal"><div class="modal-header"><h3 id="svc-modal-title">New Service</h3><button class="modal-close" id="close-svc">&times;</button></div>
    <form id="svc-form"><input type="hidden" id="sf-id"/>
     <div class="form-group"><label>Name</label><input class="form-control" id="sf-name" data-validate="required"/><div class="invalid-feedback"></div></div>
     <div class="form-group"><label>Description</label><textarea class="form-control" id="sf-desc"></textarea></div>
     <div class="form-row"><div class="form-group"><label>Price ($)</label><input type="number" step="0.01" class="form-control" id="sf-price" data-validate="required"/><div class="invalid-feedback"></div></div>
      <div class="form-group"><label>Duration (min)</label><input type="number" class="form-control" id="sf-dur" data-validate="required"/><div class="invalid-feedback"></div></div></div>
     <div class="form-check"><input type="checkbox" id="sf-active" checked/><label for="sf-active">Active</label></div>
     <div class="modal-footer"><button type="button" class="btn btn-outline" id="cancel-svc">Cancel</button><button type="submit" class="btn btn-primary">Save</button></div>
    </form></div></div>`);
  Validate.live('#svc-form');
  $('#add-svc').on('click',()=>this.openForm());$('#close-svc,#cancel-svc').on('click',()=>$('#svc-modal').removeClass('active'));
  $('#svc-form').on('submit',e=>{e.preventDefault();this.save()});this.load();
 },
 load(){API.get('/api/services/all').done(svcs=>{const tb=$('#svc-table tbody').empty();svcs.forEach(s=>tb.append(`<tr><td>${escHtml(s.name)}</td><td>$${s.price}</td><td>${s.durationMinutes} min</td><td>${s.active?'Yes':'No'}</td><td><button class="btn btn-sm btn-outline edit-svc" data-id="${s.id}">Edit</button></td></tr>`));tb.find('.edit-svc').on('click',function(){API.get('/api/services/'+$(this).data('id')).done(s=>ServicesAdminPage.openForm(s))})})},
 openForm(s){$('#svc-form')[0].reset();$('#sf-id').val(s?s.id:'');$('#svc-modal-title').text(s?'Edit Service':'New Service');if(s){$('#sf-name').val(s.name);$('#sf-desc').val(s.description);$('#sf-price').val(s.price);$('#sf-dur').val(s.durationMinutes);$('#sf-active').prop('checked',s.active)}$('#svc-modal').addClass('active')},
 save(){if(!Validate.checkForm('#svc-form'))return;const id=$('#sf-id').val();const data={name:$('#sf-name').val(),description:$('#sf-desc').val(),price:parseFloat($('#sf-price').val()),durationMinutes:parseInt($('#sf-dur').val()),active:$('#sf-active').is(':checked')};(id?API.put('/api/services/'+id,data):API.post('/api/services',data)).done(()=>{$('#svc-modal').removeClass('active');this.load()}).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'))}
};

// ================================================================
//  POINTS ADMIN
// ================================================================
const PointsAdminPage={
 render(C){
  C.html(`<div class="tabs" id="pts-tabs"><div class="tab active" data-tab="leaderboard">Leaderboard</div><div class="tab" data-tab="rules">Rules</div><div class="tab" data-tab="adjustments">Adjustments</div><div class="tab" data-tab="adjust">Manual Adjust</div></div><div id="pts-tab-content"></div>`);
  $('#pts-tabs .tab').on('click',function(){$('#pts-tabs .tab').removeClass('active');$(this).addClass('active');PointsAdminPage.showTab($(this).data('tab'))});
  this.showTab('leaderboard');
 },
 showTab(tab){
  const C=$('#pts-tab-content').empty();
  if(tab==='leaderboard'){
   C.html('<div class="card"><div class="card-header"><h2>Leaderboard</h2></div><div class="table-wrap"><table><thead><tr><th>Rank</th><th>Name</th><th>Points</th><th>Completions</th></tr></thead><tbody id="alb-table"></tbody></table></div></div>');
   API.get('/api/points/leaderboard').done(data=>{const tb=$('#alb-table');data.forEach((e,i)=>{const rnk=i===0?'gold':i===1?'silver':i===2?'bronze':'';tb.append(`<tr><td><span class="lb-rank ${rnk}">${i+1}</span></td><td>${escHtml(e.fullName||e.username)}</td><td style="font-weight:600">${e.points}</td><td>${e.completionCount||0}</td></tr>`)})});
  }else if(tab==='rules'){
   C.html('<div class="card"><div class="card-header"><h2>Points Rules</h2><button class="btn btn-primary btn-sm" id="add-rule">+ Add Rule</button></div><div class="table-wrap"><table><thead><tr><th>Name</th><th>Trigger</th><th>Points</th><th>Scope</th><th>Active</th><th></th></tr></thead><tbody id="rules-table"></tbody></table></div></div>');
   API.get('/api/points/rules').done(rules=>{const tb=$('#rules-table');rules.forEach(r=>tb.append(`<tr><td>${escHtml(r.name)}</td><td>${r.triggerEvent}</td><td>${r.points}</td><td>${r.scope}</td><td>${r.active?'Yes':'No'}</td><td><button class="btn btn-sm btn-outline edit-rule" data-id="${r.id}">Edit</button></td></tr>`));
    tb.find('.edit-rule').on('click',function(){const id=$(this).data('id');API.get('/api/points/rules').done(rules=>{const r=rules.find(x=>x.id==id);if(r)PointsAdminPage.editRule(r)})})});
   $('#add-rule').on('click',()=>this.editRule());
  }else if(tab==='adjustments'){
   C.html('<div class="card"><div class="card-header"><h2>Adjustment Audit Log</h2></div><div class="table-wrap"><table><thead><tr><th>Date</th><th>User</th><th>Adjusted By</th><th>Points</th><th>Before</th><th>After</th><th>Reason</th></tr></thead><tbody id="adj-table"></tbody></table></div></div>');
   API.get('/api/points/adjustments').done(adjs=>{const tb=$('#adj-table');adjs.forEach(a=>tb.append(`<tr><td>${fmtDateTime(a.createdAt)}</td><td>${escHtml(a.userName)}</td><td>${escHtml(a.adjustedByName)}</td><td style="font-weight:600">${a.points>0?'+':''}${a.points}</td><td>${a.balanceBefore}</td><td>${a.balanceAfter}</td><td>${escHtml(a.reason)}</td></tr>`))});
  }else if(tab==='adjust'){
   C.html(`<div class="card"><div class="card-header"><h2>Manual Points Adjustment</h2></div>
    <form id="adj-form">
     <div class="form-row"><div class="form-group"><label>User ID</label><input type="number" class="form-control" id="adj-user" data-validate="required"/><div class="invalid-feedback"></div></div>
      <div class="form-group"><label>Points (+/-)</label><input type="number" class="form-control" id="adj-pts" data-validate="required"/><div class="invalid-feedback"></div></div></div>
     <div class="form-group"><label>Reason (mandatory)</label><textarea class="form-control" id="adj-reason" data-validate="required" placeholder="This note will be permanently logged in the audit trail"></textarea><div class="invalid-feedback"></div></div>
     <button type="submit" class="btn btn-primary">Submit Adjustment</button></form></div>`);
   Validate.live('#adj-form');
   $('#adj-form').on('submit',function(e){e.preventDefault();
    if(!Validate.checkForm('#adj-form'))return;
    API.post('/api/points/adjust',{userId:parseInt($('#adj-user').val()),points:parseInt($('#adj-pts').val()),reason:$('#adj-reason').val()})
     .done(d=>{alert(`Adjusted: ${d.balanceBefore} -> ${d.balanceAfter}`);$('#adj-form')[0].reset()}).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'))});
  }
 },
 editRule(r){
  const html=`<div class="modal-overlay active" id="rule-modal"><div class="modal"><div class="modal-header"><h3>${r?'Edit':'New'} Rule</h3><button class="modal-close">&times;</button></div>
   <form id="rule-form"><input type="hidden" id="rf-id" value="${r?r.id:''}"/>
    <div class="form-group"><label>Name</label><input class="form-control" id="rf-name" value="${r?escHtml(r.name):''}"/></div>
    <div class="form-group"><label>Description</label><input class="form-control" id="rf-desc" value="${r?escHtml(r.description||''):''}"/></div>
    <div class="form-row"><div class="form-group"><label>Points</label><input type="number" class="form-control" id="rf-pts" value="${r?r.points:''}"/></div>
     <div class="form-group"><label>Scope</label><select class="form-control" id="rf-scope"><option>INDIVIDUAL</option><option>CLASS</option><option>DEPARTMENT</option><option>TEAM</option></select></div></div>
    <div class="form-group"><label>Trigger Event</label><input class="form-control" id="rf-trigger" value="${r?r.triggerEvent:''}"/></div>
    <div class="form-check"><input type="checkbox" id="rf-active" ${!r||r.active?'checked':''}/><label for="rf-active">Active</label></div>
    <div class="modal-footer"><button type="button" class="btn btn-outline close-rule">Cancel</button><button type="submit" class="btn btn-primary">Save</button></div>
   </form></div></div>`;
  $('body').append(html);
  if(r)$('#rf-scope').val(r.scope);
  $('#rule-modal .modal-close,#rule-modal .close-rule').on('click',()=>$('#rule-modal').remove());
  $('#rule-form').on('submit',function(e){e.preventDefault();
   const id=$('#rf-id').val();const data={name:$('#rf-name').val(),description:$('#rf-desc').val(),points:parseInt($('#rf-pts').val()),scope:$('#rf-scope').val(),triggerEvent:$('#rf-trigger').val(),active:$('#rf-active').is(':checked')};
   (id?API.put('/api/points/rules/'+id,data):API.post('/api/points/rules',data)).done(()=>{$('#rule-modal').remove();PointsAdminPage.showTab('rules')}).fail(xhr=>alert(xhr.responseJSON?.error||'Failed'))});
 }
};

// ---- Boot ----
$(document).ready(()=>App.init());
