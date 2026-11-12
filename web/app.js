(function(){
'use strict';
var DATA='../data/';
var LANGS={ar:'العربية',en:'English',tr:'Türkçe',ku:'کوردی'};
var RTL={ar:1,ku:1};
var WEB={
ar:{per:'100 $ =',offline:'لا يوجد اتصال، تظهر آخر بيانات محفوظة',retry:'إعادة المحاولة',ios:'لتثبيت التطبيق: زر المشاركة ← إضافة إلى الشاشة الرئيسية',favs_hint:'اضغط النجمة بأي سعر لإضافته للمفضلة (حتى 8)',theme_auto:'تلقائي',theme_light:'فاتح',theme_dark:'داكن',min:'الأدنى',max:'الأعلى',avg:'المتوسط',calc_note:'السعر المعتمد: لكل 100 دولار',tg:'قناة تليغرام',repo:'المصدر المفتوح',apk:'تحميل تطبيق أندرويد',ver:'نسخة الويب',mkt:'السوق'},
en:{per:'100 $ =',offline:'No connection, showing last saved data',retry:'Retry',ios:'To install: Share button → Add to Home Screen',favs_hint:'Tap the star on any price to add it to favorites (up to 8)',theme_auto:'Auto',theme_light:'Light',theme_dark:'Dark',min:'Low',max:'High',avg:'Average',calc_note:'Price basis: per 100 USD',tg:'Telegram channel',repo:'Open source',apk:'Download Android app',ver:'Web version',mkt:'Market'},
tr:{per:'100 $ =',offline:'Bağlantı yok, kayıtlı son veriler gösteriliyor',retry:'Yeniden dene',ios:'Yüklemek için: Paylaş → Ana Ekrana Ekle',favs_hint:'Favorilere eklemek için herhangi bir fiyatın yıldızına dokunun (en fazla 8)',theme_auto:'Otomatik',theme_light:'Açık',theme_dark:'Koyu',min:'En düşük',max:'En yüksek',avg:'Ortalama',calc_note:'Fiyat esası: 100 USD başına',tg:'Telegram kanalı',repo:'Açık kaynak',apk:'Android uygulamasını indir',ver:'Web sürümü',mkt:'Piyasa'},
ku:{per:'100 $ =',offline:'پەیوەندی نییە، دوایین داتای پاشەکەوتکراو پیشان دەدرێت',retry:'دووبارە',ios:'بۆ دامەزراندن: هاوبەشکردن ← زیادکردن بۆ شاشەی سەرەکی',favs_hint:'ئەستێرە لە هەر نرخێک دابگرە بۆ زیادکردن بۆ دڵخوازەکان (تا ٨)',theme_auto:'خۆکار',theme_light:'ڕووناک',theme_dark:'تاریک',min:'نزمترین',max:'بەرزترین',avg:'تێکڕا',calc_note:'بنەمای نرخ: بۆ هەر 100 دۆلار',tg:'کەناڵی تێلێگرام',repo:'سەرچاوە کراوە',apk:'داگرتنی ئەپی ئەندرۆید',ver:'وەشانی وێب',mkt:'بازاڕ'}};
var WORLD={usd_official:['cur_usd_official','iqd','us'],eur:['cur_eur','eur','eu'],gbp:['cur_gbp','gbp','gb'],try:['cur_try','try','tr'],cny:['cur_cny','cny','cn'],sar:['cur_sar','sar','sa'],syp:['cur_syp','syp','sy'],jod:['cur_jod','jod','jo'],aed:['cur_aed','aed','ae'],kwd:['cur_kwd','kwd','kw'],egp:['cur_egp','egp','eg'],toman:['cur_irr','irr','ir']};
var KURD={erbil:1,kirkuk:1,sulaymaniyah:1,duhok:1,halabja:1};
var TABS=['home','local','world','crypto','metals','settings'];
var ICONS={home:'<path d="M3 11l9-8 9 8M5 10v10h14V10"/>',local:'<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',world:'<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/>',crypto:'<circle cx="12" cy="12" r="9"/><path d="M10 7v10M10 7h3.5a2.2 2.2 0 010 4.4H10m3.5 0a2.4 2.4 0 010 4.8H10"/>',metals:'<path d="M2 17l3-5h7l3 5zM10 12l3-5h7l3 5h-8"/>',settings:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>'};
var S={lang:ls('lang')||'ar',theme:ls('theme')||'auto',tab:'home',favs:[],items:[],loaded:false,offline:false,calcAmt:'100',calcCity:'baghdad'};
try{S.favs=JSON.parse(ls('favs')||'[]')}catch(e){S.favs=[]}
if(!LANGS[S.lang])S.lang='ar';
function ls(k,v){try{if(v===undefined)return localStorage.getItem(k);localStorage.setItem(k,v)}catch(e){return null}}
function $(s){return document.querySelector(s)}
function t(k){var a=window.I18N_APP[S.lang]||{};return a[k]!==undefined?a[k]:(window.I18N_APP.ar[k]||k)}
function w(k){return (WEB[S.lang]||WEB.ar)[k]||WEB.en[k]||k}
function esc(s){return String(s).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function fmt(n,max){return new Intl.NumberFormat('en-US',{maximumFractionDigits:max}).format(n)}
function priceFmt(it){var p=it.price;if(it.group==='local'||p>=1000)return fmt(p,0);if(p>=1)return fmt(p,2);return fmt(p,5)}
function pad(n){return n<10?'0'+n:''+n}
function timeFmt(iso){var d=new Date(iso);if(isNaN(d))return '';return pad(d.getHours())+':'+pad(d.getMinutes())+'  '+d.getFullYear()+'/'+pad(d.getMonth()+1)+'/'+pad(d.getDate())}
function applyTheme(){var r=document.documentElement,th=S.theme;if(th==='auto')th=matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';r.setAttribute('data-t',th);var m=document.querySelector('meta[name=theme-color]');if(m)m.content='#011B39'}
function applyLang(){var r=document.documentElement;r.lang=S.lang;r.dir=RTL[S.lang]?'rtl':'ltr';document.title=t('app_name');$('#ttl').textContent=t('app_name')}
function build(cur,crypto,metals){
 var out=[];
 if(cur){
  Object.keys(cur.local||{}).forEach(function(k){var d=cur.local[k];out.push({id:'local:'+k,group:'local',key:k,nameKey:'city_'+(k==='qadisiyah'?'qadisiyyah':k),flag:KURD[k]?'kurdistan':'iq',unit:'iqd',price:d.price,time:d.time,delta:d.delta||0})});
  Object.keys(cur.world||{}).forEach(function(k){var m=WORLD[k]||['cur_'+k,k,'us'],d=cur.world[k];out.push({id:'world:'+k,group:'world',key:k,nameKey:m[0],flag:m[2],unit:m[1],price:d.price,time:d.time,delta:d.delta||0})});
 }
 if(crypto)Object.keys(crypto.items||{}).forEach(function(k){var d=crypto.items[k];out.push({id:'crypto:'+k,group:'crypto',key:k,nameKey:'cur_crypto_'+k.toLowerCase(),flag:'crypto_'+k.toLowerCase(),unit:'usd',price:d.price,time:d.time,delta:d.delta||0})});
 if(metals)Object.keys(metals.items||{}).forEach(function(k){var d=metals.items[k];out.push({id:'metals:'+k,group:'metals',key:k,nameKey:'metal_'+k,flag:'metal_'+k.replace(/\d+$/,''),unit:'usd/'+(d.unit||'g'),price:d.price,time:d.time,delta:d.delta||0})});
 return out;
}
function unitLabel(it){
 var u=it.unit;
 if(u==='iqd')return 'د.ع';
 if(u==='usd')return '$';
 if(u.indexOf('usd/')===0)return '$ / '+(u==='usd/t'?'t':'g');
 return t('currency_'+u+'_unit');
}
function itemName(it){return t(it.nameKey)}
function load(){
 var o=function(f){return fetch(DATA+f,{cache:'no-cache'}).then(function(r){if(!r.ok)throw 0;return r.json()}).catch(function(){return null})};
 return Promise.all([o('currencies.json'),o('crypto.json'),o('metals.json')]).then(function(r){
  S.items=build(r[0],r[1],r[2]);S.loaded=true;S.offline=!(r[0]||r[1]||r[2]);
 });
}
function rowHTML(it){
 var cls=it.delta>0?'up':it.delta<0?'down':'';
 var d=it.delta?'<div class="d '+(it.delta>0?'up':'down')+'">'+(it.delta>0?'▲':'▼')+' '+fmt(Math.abs(it.delta),it.group==='local'?0:4)+'</div>':'';
 var fav=S.favs.indexOf(it.id)>-1;
 var sub=it.group==='local'||it.group==='world'?w('per'):'';
 return '<div class="row '+cls+'" data-id="'+esc(it.id)+'"><button class="star'+(fav?' on':'')+'" data-fav="'+esc(it.id)+'" aria-label="favorite">'+(fav?'★':'☆')+'</button><img src="img/'+it.flag+'.webp" alt="" loading="lazy"><div class="nm"><b>'+esc(itemName(it))+'</b><small class="per">'+esc(sub)+'</small></div><div class="pr"><div><span class="p">'+priceFmt(it)+'</span><span class="u">'+esc(unitLabel(it))+'</span></div>'+d+'<div class="t">'+esc(timeFmt(it.time))+'</div></div></div>';
}
function list(group){var a=S.items.filter(function(i){return i.group===group});return a.length?a.map(rowHTML).join(''):'<div class="empty">'+esc(t('no_data'))+'</div>'}
function homeHTML(){
 var fv=S.favs.map(function(id){return S.items.filter(function(i){return i.id===id})[0]}).filter(Boolean);
 var h='<div class="sec">'+esc(t('favorites_title'))+'</div>';
 h+=fv.length?fv.map(rowHTML).join(''):'<div class="empty">'+esc(t('favorites_empty'))+'<br><small>'+esc(w('favs_hint'))+'</small></div>';
 var cities=S.items.filter(function(i){return i.group==='local'});
 var cur=cities.filter(function(c){return c.key===S.calcCity})[0]||cities[0];
 h+='<div class="sec">'+esc(t('calculator_title'))+'</div><div class="card"><label>'+esc(t('calculator_amount_label'))+'</label><input id="cAmt" inputmode="decimal" value="'+esc(S.calcAmt)+'"><label>'+esc(t('calculator_exchange_label'))+'</label><select id="cCity">'+cities.map(function(c){return '<option value="'+c.key+'"'+(cur&&c.key===cur.key?' selected':'')+'>'+esc(itemName(c))+'</option>'}).join('')+'</select><div class="res" id="cRes"></div><div class="hint">'+esc(w('calc_note'))+'</div></div>';
 return h;
}
function calc(){
 var cities=S.items.filter(function(i){return i.group==='local'});
 var c=cities.filter(function(x){return x.key===S.calcCity})[0]||cities[0];
 var a=parseFloat(String(S.calcAmt).replace(/[,٬\s]/g,'').replace(/[٠-٩]/g,function(d){return d.charCodeAt(0)-1632}));
 var el=$('#cRes');if(!el)return;
 el.textContent=(c&&isFinite(a))?fmt(a*c.price/100,0)+' '+'د.ع':'—';
}
function settingsHTML(){
 var h='<div class="sec">'+esc(t('settings_language'))+'</div><div class="chips">'+Object.keys(LANGS).map(function(k){return '<button class="chip'+(S.lang===k?' on':'')+'" data-lang="'+k+'">'+LANGS[k]+'</button>'}).join('')+'</div>';
 h+='<div class="sec">'+esc(t('settings_theme'))+'</div><div class="chips">'+['auto','light','dark'].map(function(k){return '<button class="chip'+(S.theme===k?' on':'')+'" data-theme="'+k+'">'+esc(w('theme_'+k))+'</button>'}).join('')+'</div>';
 h+='<div class="sec">'+esc(t('settings_social'))+'</div><div class="card" style="padding:4px 14px">'+
 [['https://t.me/BorsatDinari',w('tg')],['https://github.com/techmohammed/Borsat-Dinari',w('repo')],['https://github.com/techmohammed/Borsat-Dinari/releases/latest',w('apk')]].map(function(l){return '<a class="lnk" href="'+l[0]+'" target="_blank" rel="noopener"><span>'+esc(l[1])+'</span><span>↗</span></a>'}).join('')+'</div>';
 h+='<div class="hint">'+esc(t('app_name'))+' · '+esc(w('ver'))+' 1.0</div>';
 return h;
}
function render(){
 applyLang();
 var nav=$('#nav');
 nav.innerHTML=TABS.map(function(k){var lab=k==='home'?t('nav_home'):k==='local'?t('nav_local'):k==='world'?t('nav_global'):k==='crypto'?t('nav_crypto'):k==='metals'?t('nav_metals'):t('nav_settings');return '<button data-tab="'+k+'" class="'+(S.tab===k?'on':'')+'"><svg viewBox="0 0 24 24">'+ICONS[k]+'</svg><span>'+esc(lab)+'</span></button>'}).join('');
 var m=$('#main'),h;
 if(!S.loaded)h='<div class="empty">…</div>';
 else if(S.tab==='home')h=homeHTML();
 else if(S.tab==='settings')h=settingsHTML();
 else h=list(S.tab);
 m.innerHTML=h;
 if(S.tab==='home')calc();
 var b=$('#banner');
 if(S.offline){b.hidden=false;b.innerHTML='<span>'+esc(w('offline'))+'</span><button id="bx" aria-label="retry">⟳</button>'}
 else if(isIOS()&&!standalone()&&!ls('iosHint')){b.hidden=false;b.innerHTML='<span>'+esc(w('ios'))+'</span><button id="bx">×</button>';b.dataset.k='ios'}
 else b.hidden=true;
}
function isIOS(){return /iphone|ipad|ipod/i.test(navigator.userAgent)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1)}
function standalone(){return navigator.standalone===true||matchMedia('(display-mode: standalone)').matches}
// history
var RANGES=[['tab_day',1],['tab_week',7],['tab_month',30],['tab_2months',60]];
var hist={};
function getHist(it){
 var keys=[it.key,it.key.toLowerCase()];
 var p=hist[it.id];if(p)return p;
 p=fetch(DATA+'history/'+keys[0]+'.json',{cache:'no-cache'}).then(function(r){if(!r.ok)throw 0;return r.json()}).catch(function(){if(keys[1]===keys[0])return [];return fetch(DATA+'history/'+keys[1]+'.json',{cache:'no-cache'}).then(function(r){return r.ok?r.json():[]}).catch(function(){return []})});
 hist[it.id]=p;return p;
}
function openSheet(it,days){
 days=days||7;
 var sh=$('#sheet'),b=$('#shb');sh.hidden=false;
 b.innerHTML='<div class="sh-h"><img src="img/'+it.flag+'.webp" alt=""><b>'+esc(itemName(it))+'</b><span class="big">'+priceFmt(it)+' '+esc(unitLabel(it))+'</span></div><div class="rng">'+RANGES.map(function(r){return '<button class="chip'+(r[1]===days?' on':'')+'" data-days="'+r[1]+'">'+esc(t(r[0]))+'</button>'}).join('')+'</div><div id="chart"><div class="hint">…</div></div>';
 b.dataset.id=it.id;
 getHist(it).then(function(h){if(b.dataset.id!==it.id)return;draw(it,h,days)});
}
function draw(it,h,days){
 var box=$('#chart');if(!box)return;
 var now=Date.now()/1000,from=now-days*86400;
 var pts=(h||[]).filter(function(p){return p[0]>=from}).sort(function(a,b){return a[0]-b[0]});
 // keep the point just before range start so the line begins at range start
 var before=(h||[]).filter(function(p){return p[0]<from}).sort(function(a,b){return b[0]-a[0]})[0];
 if(before)pts.unshift([from,before[1]]);
 pts.push([now,it.price]);
 if(pts.length<3&&!before){box.innerHTML='<div class="hint">'+esc(t('chart_no_data'))+'</div>';return}
 var W=360,H=230,L=8,Rr=52,T=14,B=26,vals=pts.map(function(p){return p[1]}),mn=Math.min.apply(0,vals),mx=Math.max.apply(0,vals);
 if(mn===mx){mn-=1;mx+=1}
 var pad2=(mx-mn)*.1;mn-=pad2;mx+=pad2;
 var x=function(ts){return L+(ts-from)/(now-from)*(W-L-Rr)},y=function(v){return T+(1-(v-mn)/(mx-mn))*(H-T-B)};
 var d='',last=null;
 pts.forEach(function(p,i){var px=x(p[0]),py=y(p[1]);d+=i?('H'+px.toFixed(1)+'V'+py.toFixed(1)):('M'+px.toFixed(1)+' '+py.toFixed(1));last=p});
 d+='H'+x(now).toFixed(1);
 var grid='';for(var i=0;i<4;i++){var v=mn+(mx-mn)*i/3,yy=y(v);grid+='<line x1="'+L+'" x2="'+(W-Rr)+'" y1="'+yy+'" y2="'+yy+'" stroke="var(--grid)"/><text x="'+(W-Rr+4)+'" y="'+(yy+3)+'">'+fmt(v,it.group==='local'||v>=1000?0:v>=1?2:4)+'</text>'}
 var lab='';[0,.5,1].forEach(function(f){var ts=from+(now-from)*f,dt=new Date(ts*1000);var s=days<=1?pad(dt.getHours())+':'+pad(dt.getMinutes()):(dt.getMonth()+1)+'/'+dt.getDate();lab+='<text x="'+(L+(W-L-Rr)*f+(f===0?4:0))+'" y="'+(H-8)+'" text-anchor="'+(f===0?'start':f===1?'end':'middle')+'" direction="ltr">'+s+'</text>'});
 var real=pts.map(function(p){return p[1]}),avg=real.reduce(function(a,b){return a+b},0)/real.length;
 var f=function(v){return fmt(v,it.group==='local'||v>=1000?0:v>=1?2:4)};
 box.innerHTML='<svg class="ch" viewBox="0 0 '+W+' '+H+'" dir="ltr">'+grid+lab+'<path d="'+d+'" fill="none" stroke="var(--line)" stroke-width="2" stroke-linejoin="round"/></svg><div class="stats"><div>'+esc(w('min'))+'<b>'+f(Math.min.apply(0,real))+'</b></div><div>'+esc(w('avg'))+'<b>'+f(avg)+'</b></div><div>'+esc(w('max'))+'<b>'+f(Math.max.apply(0,real))+'</b></div></div>';
}
function toggleFav(id){var i=S.favs.indexOf(id);if(i>-1)S.favs.splice(i,1);else{if(S.favs.length>=8)S.favs.shift();S.favs.push(id)}ls('favs',JSON.stringify(S.favs))}
document.addEventListener('click',function(e){
 var el=e.target.closest('[data-fav]');
 if(el){e.stopPropagation();toggleFav(el.dataset.fav);render();return}
 el=e.target.closest('[data-tab]');if(el){S.tab=el.dataset.tab;render();scrollTo(0,0);return}
 el=e.target.closest('[data-lang]');if(el){S.lang=el.dataset.lang;ls('lang',S.lang);render();return}
 el=e.target.closest('[data-theme]');if(el){S.theme=el.dataset.theme;ls('theme',S.theme);applyTheme();render();return}
 el=e.target.closest('[data-days]');if(el){var it=S.items.filter(function(i){return i.id===$('#shb').dataset.id})[0];if(it)openSheet(it,+el.dataset.days);return}
 el=e.target.closest('.row');if(el){var it2=S.items.filter(function(i){return i.id===el.dataset.id})[0];if(it2)openSheet(it2,7);return}
 if(e.target.id==='shx'||e.target.id==='sheet'){$('#sheet').hidden=true;return}
 if(e.target.id==='bx'){var b=$('#banner');if(b.dataset.k==='ios'){ls('iosHint','1');b.hidden=true}else{S.loaded=false;render();load().then(render)}}
});
document.addEventListener('input',function(e){
 if(e.target.id==='cAmt'){S.calcAmt=e.target.value;calc()}
});
document.addEventListener('change',function(e){if(e.target.id==='cCity'){S.calcCity=e.target.value;calc()}});
matchMedia('(prefers-color-scheme: dark)').addEventListener&&matchMedia('(prefers-color-scheme: dark)').addEventListener('change',function(){if(S.theme==='auto')applyTheme()});
document.addEventListener('visibilitychange',function(){if(!document.hidden&&S.loaded){hist={};load().then(function(){if($('#sheet').hidden)render()})}});
applyTheme();render();
load().then(render);
if('serviceWorker' in navigator)navigator.serviceWorker.register('sw.js').catch(function(){});
})();
