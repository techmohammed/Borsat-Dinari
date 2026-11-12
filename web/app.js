(function(){
'use strict';
var D=window.I18N_APP,IC=window.ICONS,VER='1.1.8';
var DATA='../data/';
var $=function(s,r){return (r||document).querySelector(s)};
var esc=function(s){return String(s).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})};
function lsGet(k,d){try{var v=localStorage.getItem('bd_'+k);return v===null?d:v}catch(e){return d}}
function lsSet(k,v){try{localStorage.setItem('bd_'+k,v)}catch(e){}}
var LANGS=['ar','en','tr','ku'],RTL={ar:1,ku:1};
var LASTR=+lsGet('lastr',0)||0;
var S={lang:lsGet('lang','ar'),theme:lsGet('theme','light'),favs:null,data:{cur:null,cry:null,met:null},loading:false,drag:false,prev:{},nav:0};
if(LANGS.indexOf(S.lang)<0)S.lang='ar';
try{S.favs=JSON.parse(lsGet('favs',''))}catch(e){}
var DEFF=['baghdad','erbil','usd_official','eur','BTC','ETH','gold24','gold22'];
if(!Array.isArray(S.favs)||S.favs.join()==='baghdad,erbil,usd_official,basra,eur')S.favs=DEFF.slice();
['cur','cry','met'].forEach(function(k){try{S.data[k]=JSON.parse(lsGet('d_'+k,'null'))}catch(e){}});
function t(k){var l=D[S.lang];return (l&&l[k]!==undefined)?l[k]:(D.ar[k]!==undefined?D.ar[k]:k)}
var SUBTXT={ar:'طريقة الاستخدام',en:'Usage',tr:'Kullanım',ku:'بەکارهێنان'};
var BADLINE=/widget|pdf|ويدجت|ودجت|تصدير|export|dışa|ویدجێت|هەناردە/i;
function clean(s){return s.split('\n').filter(function(l){return !BADLINE.test(l)}).join('\n')}
function icon(n,sz,cls){var o=IC[n];if(!o)return '';return '<svg class="i '+(cls||'')+'" width="'+sz+'" height="'+sz+'" viewBox="'+o.vb+'">'+o.b+'</svg>'}
function pad2(n){return n<10?'0'+n:''+n}
function lrm(s){return '‎'+s+'‎'}

/* ---------- items ---------- */
var LOCAL=['baghdad','erbil','sulaymaniyah','duhok','kirkuk','mosul','karbala','najaf','basra','wasit','muthanna','salahaldin','diyala','dhiqar','qadisiyah','babil','maysan','anbar','halabja'];
var WORLD=['usd_official','eur','gbp','try','cny','sar','syp','jod','aed','kwd','egp','toman'];
var CRYPTO=['BTC','ETH','USDT','BNB','XRP','USDC','SOL','ZEC','HYPE','DOGE'];
var METALS=['gold24','gold22','gold21','gold18','silver','platinum','palladium','copper','aluminum','nickel'];
var WMETA={usd_official:{n:'cur_usd_official',f:'us',c:'USD',u:'100$',r:'iqd',d:0},eur:{n:'cur_eur',f:'eu',c:'EUR',u:'100€',r:'USD',d:2},gbp:{n:'cur_gbp',f:'gb',c:'GBP',u:'100£',r:'USD',d:2},
 try:{n:'cur_try',f:'tr',c:'TRY',u:'100$',r:'TRY',d:0},cny:{n:'cur_cny',f:'cn',c:'CNY',u:'100$',r:'CNY',d:2},sar:{n:'cur_sar',f:'sa',c:'SAR',u:'100$',r:'SAR',d:2},
 syp:{n:'cur_syp',f:'sy',c:'SYP',u:'100$',r:'SYP',d:0},jod:{n:'cur_jod',f:'jo',c:'JOD',u:'100$',r:'JOD',d:2},aed:{n:'cur_aed',f:'ae',c:'AED',u:'100$',r:'AED',d:2},
 kwd:{n:'cur_kwd',f:'kw',c:'KWD',u:'100$',r:'KWD',d:2},egp:{n:'cur_egp',f:'eg',c:'EGP',u:'100$',r:'EGP',d:0},toman:{n:'cur_irr',f:'ir',c:'IRR',u:'100$',r:'IRR',d:0}};
var MSYM={gold24:'AU',gold22:'AU',gold21:'AU',gold18:'AU',silver:'AG',platinum:'PT',palladium:'PD',copper:'CU',aluminum:'AL',nickel:'NI'};
var MFLAG={gold24:'gold',gold22:'gold',gold21:'gold',gold18:'gold'};
var KURD={erbil:1,sulaymaniyah:1,halabja:1,kirkuk:1,duhok:1};
function items(){
 var o={},c=S.data.cur||{},cr=S.data.cry||{},m=S.data.met||{};
 LOCAL.forEach(function(k){var d=(c.local||{})[k];o[k]={id:k,kind:'local',n:'city_'+(k==='qadisiyah'?'qadisiyyah':k),f:KURD[k]?'kurdistan':'iq',c:'IQD',u:'100$',r:'iqd',d:0,p:d&&d.price,dl:d&&d.delta,tm:d&&d.time}});
 WORLD.forEach(function(k){var w=WMETA[k],d=(c.world||{})[k];o[k]={id:k,kind:'world',n:w.n,f:w.f,c:w.c,u:w.u,r:w.r,d:w.d,p:d&&d.price,dl:d&&d.delta,tm:d&&d.time}});
 CRYPTO.forEach(function(k){var d=(cr.items||{})[k];o[k]={id:k,kind:'crypto',n:'cur_crypto_'+k.toLowerCase(),f:'crypto_'+k.toLowerCase(),c:k,u:'',r:'USD',d:-1,p:d&&d.price,dl:d&&d.delta,tm:d&&d.time}});
 METALS.forEach(function(k){var d=(m.items||{})[k];var un=d&&d.unit==='t'?'metal_unit_ton':'metal_unit_gram';o[k]={id:k,kind:'metal',n:'metal_'+k,f:MFLAG[k]?'metal_gold':'metal_'+k,c:MSYM[k],u:'@'+un,r:'USD',d:2,p:d&&d.price,dl:d&&d.delta,tm:d&&d.time,mu:un}});
 return o;
}
var IT={};
function resUnit(it){return it.r==='iqd'?t('iqd_short'):it.r}
function unitTxt(it){return it.u&&it.u[0]==='@'?t(it.u.slice(1)):it.u}
function grp(n,dec){return n.toLocaleString('en-US',{minimumFractionDigits:dec,maximumFractionDigits:dec})}
function fmtPrice(it){var p=it.p;if(p==null)return '—';if(it.kind==='crypto'){return p>=1?grp(p,2):p.toLocaleString('en-US',{maximumFractionDigits:6})}return grp(p,it.d)}
function fmtDelta(it){var v=Math.abs(it.dl||0);if(it.kind==='local'||it.id==='usd_official'||it.id==='toman'||it.id==='try'||it.id==='syp'||it.id==='egp')return grp(v,0);if(it.kind==='crypto')return v>=1?grp(v,2):v.toFixed(4);if(it.kind==='metal')return grp(v,2);return v.toFixed(2)}
function trend(it){if(!it.dl||!it.tm)return 0;var a=Date.now()-Date.parse(it.tm);if(isNaN(a)||a>3*3600e3)return 0;return it.dl>0?1:-1}
function dt(it){return it.tm?new Date(it.tm):null}

/* ---------- data ---------- */
function getJ(f,k){return fetch(DATA+f,{cache:'no-cache'}).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(j){S.data[k]=j;lsSet('d_'+k,JSON.stringify(j))}).catch(function(){})}
var failed=false;
function load(manual){
 if(S.loading)return Promise.resolve();
 S.loading=true;strip();
 var before=snap();
 return Promise.all([getJ('currencies.json','cur'),getJ('crypto.json','cry'),getJ('metals.json','met')]).then(function(){
  S.loading=false;LASTR=Date.now();lsSet('lastr',LASTR);IT=items();render(true,before);if(manual)wave()
 });
}
function snap(){var o={};for(var k in IT)o[k]=IT[k].p;return o}

/* ---------- routing ---------- */
var TABS=['crypto','global','home','local','metals'];
var TAB_ICON={crypto:'nav_bitcoin',global:'globe',home:'nav_home',local:'nav_bars',metals:'nav_metals'};
var TAB_LAB={crypto:'navbar_crypto',global:'navbar_global',home:'nav_home',local:'navbar_local',metals:'navbar_metals'};
var TAB_TTL={home:'app_name',crypto:'nav_crypto',metals:'nav_metals',global:'nav_global',local:'nav_local',settings:'nav_settings'};
var TAB_STRIP={home:'favorites_title',global:'market_global_title',local:'market_local_title',crypto:'market_crypto_title',metals:'market_metals_title'};
var SUB={calc:'calculator_title',about:'settings_about',guide:'settings_guide',terms:'settings_terms',support:'settings_support',social:'settings_social'};
var route='home',hinit=false;
function parseHash(){var h=(location.hash||'').replace(/^#\/?/,'');return h}
function valid(r){return TABS.indexOf(r)>-1||r==='settings'||SUB[r]||/^h\/\w+$/.test(r)}
function go(r){if(r===route)return;S.nav++;location.hash='#/'+r}
function back(){if(S.nav>0){S.nav--;history.back()}else{var p=route.indexOf('h/')===0?'home':(['guide','terms','support','social','about'].indexOf(route)>-1?'settings':'home');location.replace('#/'+p)}}
window.addEventListener('hashchange',function(){var r=parseHash();if(!valid(r))r='home';if(r===route)return;if(route.indexOf('h/')===0&&r.indexOf('h/')!==0)hinit=false;route=r;lsSet('route',r);render(false,null,true)});

/* ---------- shell ---------- */
function applyLang(){var d=document.documentElement;d.lang=S.lang;d.dir=RTL[S.lang]?'rtl':'ltr';d.dataset.t=S.theme;
 var m=document.querySelector('meta[name=theme-color]');if(m)m.content=S.theme==='dark'?'#011B39':'#011B39'}
function titleOf(){
 if(route.indexOf('h/')===0){var it=IT[route.slice(2)];return it?t(it.n):t('app_name')}
 if(SUB[route])return t(SUB[route]);return t(TAB_TTL[route]||'app_name')}
function header(){
 var sub=!(TABS.indexOf(route)>-1||route==='settings');
 var isSub=route!=='home'&&(SUB[route]||route.indexOf('h/')===0||route==='settings');
 $('#hdr').innerHTML='<'+(isSub?'button class="lg" id="bk"':'span class="lg"')+' aria-label="back"><img src="img/header_bg.webp" alt="" draggable="false"></'+(isSub?'button':'span')+'><div id="ttlw"><span id="ttl">'+esc(titleOf())+'</span></div>';
 var b=$('#bk');if(b)b.onclick=back;
 document.title=titleOf();
}
function nav(){
 var n=$('#nav'),show=TABS.indexOf(route)>-1||route==='settings';
 n.style.display=show?'':'none';if(!show)return;
 n.innerHTML=TABS.map(function(k){return '<button data-tab="'+k+'" class="'+(route===k?'on':'')+'"><span class="nh">'+icon(TAB_ICON[k],route===k?27:22)+'</span><span class="nl">'+esc(t(TAB_LAB[k]))+'</span><span class="nu"></span></button>'}).join('');
 // visual order must be: metals local home global crypto  (RTL row => DOM crypto first)
 n.querySelectorAll('button').forEach(function(b){b.onclick=function(){go(b.dataset.tab)}});
}
/* ---------- update strip ---------- */
function stampOf(){if(!LASTR)return '—';var d=new Date(LASTR);return d.getDate()+'/'+(d.getMonth()+1)+'/'+d.getFullYear()+' - '+pad2(d.getHours())+':'+pad2(d.getMinutes())}
function stripHTML(){return '<div class="strip" id="strip"><span class="lb">'+esc(t(TAB_STRIP[route]||'favorites_title'))+'</span><span class="st num">'+lrm(esc(stampOf()))+'</span><span class="sep"></span>'+icon('update_clock',18)+'</div>'}
function strip(){}
/* ---------- rows ---------- */
function rowHTML(it,mode){
 var tr=trend(it),cls=tr>0?'up':tr<0?'down':'',d=dt(it);
 var meta='<div class="mt num"><div class="dr '+cls+'">'+(tr?icon(tr>0?'tri_up':'tri_down',9)+'<span class="dt'+(fmtDelta(it).length>6?' s':'')+'">'+(tr>0?'+':'-')+fmtDelta(it)+'</span>':'<span class="inv">.</span>')+'</div>'
 +'<div class="tr">'+(d?icon('clock_outline',10)+pad2(d.getHours())+':'+pad2(d.getMinutes()):'')+'</div>'
 +'<div class="da">'+(d?icon('update_calendar',10)+d.getDate()+'/'+(d.getMonth()+1)+'/'+d.getFullYear():'')+'</div></div>';
 var star=mode==='fav'?'<span class="sb drg" data-drag="'+it.id+'">'+icon('drag_handle',22)+'</span>':'<span class="sb" data-fav="'+it.id+'">'+icon(S.favs.indexOf(it.id)>-1?'star_filled':'star_outline',22,'')+'</span>';
 var starCol=S.favs.indexOf(it.id)>-1&&mode!=='fav'?' style="color:#F5B301"':'';
 if(starCol)star=star.replace('<span class="sb"','<span class="sb"'+starCol);
 return '<div class="row '+cls+'" data-id="'+it.id+'"><img class="fl" src="img/'+it.f+'.webp" alt="" draggable="false">'
 +'<div class="rt"><div class="n">'+esc(t(it.n))+'</div><div class="cu"><span class="chip">'+esc(it.c)+'</span>'+(unitTxt(it)?'<span class="un">'+esc(unitTxt(it))+'</span>':'')+'</div></div>'
 +'<span class="vd"></span><div class="pg"><div class="p" data-p="'+it.id+'">'+fmtPrice(it)+'</div><div class="ru num">'+esc(resUnit(it))+'</div></div><span class="vd"></span>'+meta+star+'</div>';
}
function listFor(r){var ids=r==='local'?LOCAL:r==='global'?WORLD:r==='crypto'?CRYPTO:METALS;return ids.map(function(i){return IT[i]})}
function emptyBox(r){var k=r==='crypto'?'crypto_empty':r==='metals'?'metals_empty':'no_data';return '<div class="empty"><b>'+esc(t(k))+'</b></div>'}
function actCard(c,ic,lab,sub,id){return '<button class="act" data-act="'+id+'"><span class="c" style="background:'+c+'">'+icon(ic,19)+'</span><span class="t">'+esc(lab)+'</span><span class="s">'+(sub?lrm(esc(sub)):'&nbsp;')+'</span></button>'}
function homePage(){
 var favs=S.favs.filter(function(i){return IT[i]});
 var h=stripHTML()+'<div class="list" id="favlist">'+(favs.length?favs.map(function(i){return rowHTML(IT[i],'fav')}).join(''):'<div class="empty"><b>'+esc(t('favorites_empty'))+'</b><small>'+esc(t('favorites_hint'))+'</small></div>')+'</div>';
 h+='<div class="list"><div class="acts">'
  +actCard('#2878F0','refresh_history',t('quick_refresh'),stampOf(),'refresh')
  +actCard('#0BA77D','calculator',t('quick_calculator'),t('quick_convert'),'calc')
  +actCard('#E89A20','menu_burger',t('quick_about'),t('app_name'),'about')
  +actCard('#6653D8','nav_settings',t('nav_settings'),t('app_name'),'settings')+'</div></div>';
 return h}
/* ---------- pages ---------- */
function mkRow(label,sub,ic,opts){opts=opts||{};
 return '<button class="sr'+(opts.dis?' dis':'')+'" '+(opts.a?'data-a="'+opts.a+'"':'')+'><span class="ci">'+icon(ic,20)+'</span><span class="tx"><b>'+esc(label)+'</b>'+(sub?'<small>'+esc(sub)+'</small>':'')+'</span>'+(opts.tail||'')+'</button>'}
function chev(){return '<span class="tl">'+icon('set_chevron',22,'chev')+'</span>'}
var langOpen=false;
var LF={ar:'iq',en:'us',tr:'tr',ku:'kurdistan'};
function settingsPage(){
 var dark=S.theme==='dark';
 var h='<div class="set '+(RTL[S.lang]?'rtl':'ltr')+'"><div class="stt">'+esc(t('settings_section_prefs'))+'</div><div class="scard">';
 h+=mkRow(t('settings_language'),t('language_'+S.lang),'globe',{a:'lang',tail:chev()});
 if(langOpen){LANGS.forEach(function(l){h+='<button class="lo" data-lang="'+l+'"><img src="img/'+LF[l]+'.webp" alt=""><span>'+esc(t('language_'+l))+'</span>'+(l===S.lang?icon('set_check',20):'')+'</button>'})}
 h+='<div class="dv"></div>'+mkRow(t('settings_appearance'),t('settings_appearance_sub'),'set_theme',{a:'theme',tail:'<span class="tl"><span class="sw'+(dark?' on':'')+'"></span></span>'});
 h+='</div><div class="stt">'+esc(t('settings_section_general'))+'</div><div class="scard">';
 h+=mkRow(t('settings_guide'),SUBTXT[S.lang],'set_help',{a:'guide',tail:chev()})+'<div class="dv"></div>';
 h+=mkRow(t('settings_terms'),t('settings_terms_sub'),'set_doc',{a:'terms',tail:chev()})+'<div class="dv"></div>';
 h+=mkRow(t('settings_support'),t('settings_support_sub'),'set_mail',{a:'support',tail:chev()})+'<div class="dv"></div>';
 h+=mkRow(t('settings_social'),t('settings_social_sub'),'set_people',{a:'social',tail:chev()})+'<div class="dv"></div>';
 h+=mkRow(t('settings_about'),t('settings_about_sub'),'set_info',{a:'about',tail:'<span class="tl num">'+VER+'</span>'});
 h+='</div><div class="stt">'+esc(t('settings_section_extras'))+'</div><div class="scard">';
 h+=mkRow(t('settings_share'),t('settings_share_sub'),'set_share',{a:'share',tail:chev()})+'<div class="dv"></div>';
 h+=mkRow(t('settings_rate'),t('settings_rate_sub'),'set_star',{dis:1,tail:'<span class="tl">'+esc(t('coming_soon'))+'</span>'});
 return h+'</div></div>'}
var TOAST={ar:'تم نسخ رابط التطبيق',en:'App link copied',tr:'Uygulama bağlantısı kopyalandı',ku:'لینکی ئەپ کۆپی کرا'};
function toast(m){var e=$('#toast');e.textContent=m;e.classList.add('on');clearTimeout(toast.t);toast.t=setTimeout(function(){e.classList.remove('on')},1800)}
function shareApp(){
 var url=location.origin+location.pathname,txt=t('share_text').replace(/https?:\/\/\S+/,'').trim();
 if(navigator.share){navigator.share({text:txt,url:url}).catch(function(){});return}
 var full=txt+'\n'+url;
 if(navigator.clipboard)navigator.clipboard.writeText(full).then(function(){toast(TOAST[S.lang])},function(){prompt('',full)});else prompt('',full)}
function card(title,body){return '<div class="ic"><h3>'+esc(title)+'</h3><p>'+esc(body)+'</p></div>'}
function infoPage(r){
 var dirc=RTL[S.lang]?'rtl':'ltr',h='<div class="pad" style="direction:'+dirc+';padding-top:10px">';
 if(r==='guide')['guide_home','guide_colors','guide_update'].forEach(function(k){h+=card(t(k+'_title'),clean(t(k+'_text')))});
 if(r==='terms')['prices','accuracy','liability','advice','privacy','rights','changes'].forEach(function(k){h+=card(t('terms_'+k+'_title'),t('terms_'+k+'_text'))});
 return h+'</div>'}
function aboutPage(){
 var F=['free','ads','nonprofit','permissions','notifications','lightweight','privacy','history','opensource'];
 var dirc=RTL[S.lang]?'rtl':'ltr';
 var ln=function(s,i,c){return '<div class="al'+(c||'')+'" style="'+(i?'margin-top:10px':'')+'">'+esc(s)+'</div>'};
 return '<div class="ab" style="direction:'+dirc+'"><img class="aic" src="icons/icon-192.png" alt=""><div class="an">'+esc(t('app_name'))+'</div><div class="ds">'+esc(t('about_description'))+'</div><div class="dvd"></div>'
 +'<div class="h2">'+esc(t('about_features_title'))+'</div><div class="ic">'+F.map(function(k,i){return ln(t('about_feature_'+k),i)}).join('')+'</div>'
 +'<div class="h2">'+esc(t('about_info_title'))+'</div><div class="ic">'+[t('about_version_format').replace('%s',VER),t('about_designer'),t('about_year'),t('about_last_update')].map(function(s,i){return ln(s,i,' sb2')}).join('')+'</div>'
 +'<div class="h2">'+esc(t('about_updates_title'))+'</div><div class="ic"><div class="al" style="line-height:1.9">'+esc(clean(t('about_updates_text'))).replace(/\n/g,'<br>')+'</div></div>'
 +'<div class="qc"><img src="img/about_quote_bg.webp" alt=""><p>'+esc(t('about_quote'))+'</p></div></div>'}
var SOC=[['whatsapp','https://whatsapp.com/channel/0029VbDz68h7YSd54EV6rw0R','WHATSAPP'],['instagram','https://www.instagram.com/borsatdinari','INSTAGRAM'],['telegram','https://t.me/BorsatDinari','TELEGRAM'],['facebook','https://www.facebook.com/share/1Bv8F5CUZE/','FACEBOOK'],['x','https://x.com/Borsatdinari','X'],['threads','https://www.threads.com/@borsatdinari','THREADS'],
 ['github','https://github.com/techmohammed/Borsat-Dinari','GITHUB'],['gitlab','https://gitlab.com/techmohammed/Borsat-Dinari','GITLAB'],['gmail','mailto:BorsatDinari@gmail.com','GMAIL'],['pinterest','https://www.pinterest.com/borsatdinari/','PINTEREST'],
 ['reddit','https://www.reddit.com/u/BorsatDinari','REDDIT'],['discord','https://discord.com/users/1568517464592224297','DISCORD'],['linkedin','https://www.linkedin.com/in/borsatdinari','LINKEDIN'],['youtube','https://www.youtube.com/@BorsatDinari','YOUTUBE'],
 ['tiktok','https://tiktok.com/@borsatdinari','TIKTOK'],['apkpure','https://apkpure.com/dp/com.mokhtar.com','APKPURE'],['fdroid',null,'F-DROID'],['playstore',null,'PLAY STORE'],['aptoide',null,'APTOIDE'],['uptodown',null,'UPTODOWN']];
function socItem(k){var s=SOC.filter(function(x){return x[0]===k})[0];return '<'+(s[1]?'a href="'+s[1]+'" target="_blank" rel="noopener"':'span')+' class="sc'+(s[1]?'':' off')+'"><img src="img/soc_'+k+'.webp" alt=""><span>'+s[2]+'</span></'+(s[1]?'a':'span')+'>'}
function socialPage(){
 var row=function(a){return '<div class="sr1">'+a.map(socItem).join('')+'</div>'};
 return '<div class="soc"><p>'+esc(t('social_intro'))+'</p>'+row(['whatsapp','instagram'])+row(['telegram','facebook','x','threads'])
 +'<div class="mid"><div>'+socItem('github')+socItem('gitlab')+'</div><img src="icons/icon-192.png" alt=""><div>'+socItem('gmail')+socItem('pinterest')+'</div></div>'
 +row(['reddit','discord','linkedin','youtube'])+row(['tiktok','apkpure','fdroid','playstore'])+row(['aptoide','uptodown'])+'</div>'}
var sup={type:'idea',subj:'',msg:''};
function supportPage(){
 var T=['idea','suggestion','problem','question','other'];
 return '<div class="pad fm" style="direction:'+(RTL[S.lang]?'rtl':'ltr')+';padding-top:6px"><p style="font-size:14px;line-height:1.8">'+esc(t('support_intro'))+'</p><label>'+esc(t('support_to_label'))+'</label><span class="em">BorsatDinari@gmail.com</span><label>'+esc(t('support_type_label'))+'</label><div class="tg">'
 +T.map(function(k){return '<button data-ty="'+k+'" class="'+(sup.type===k?'on':'')+'">'+esc(t('support_type_'+k))+'</button>'}).join('')+'</div><label></label><input id="sSub" value="'+esc(sup.subj)+'" placeholder="'+esc(t('support_subject_hint'))+'"><label></label><textarea id="sMsg" placeholder="'+esc(t('support_message_hint'))+'">'+esc(sup.msg)+'</textarea><button class="snd" id="sSend">'+esc(t('support_send'))+'</button></div>'}
function bindSupport(){
 var P=$('#page');
 P.querySelectorAll('[data-ty]').forEach(function(b){b.onclick=function(){sup.type=b.dataset.ty;P.querySelectorAll('[data-ty]').forEach(function(x){x.classList.toggle('on',x===b)})}});
 $('#sSub').oninput=function(){sup.subj=this.value};$('#sMsg').oninput=function(){sup.msg=this.value;this.classList.remove('er')};
 $('#sSend').onclick=function(){if(!sup.msg.trim()){$('#sMsg').classList.add('er');toast(t('support_empty_message'));return}
  var s=encodeURIComponent('['+t('support_type_'+sup.type)+'] '+(sup.subj||t('app_name'))),b=encodeURIComponent(sup.msg);location.href='mailto:BorsatDinari@gmail.com?subject='+s+'&body='+b}}
/* ---------- history ---------- */
var RANGES=[['tab_2months',60],['tab_month',30],['tab_week',7],['tab_day',1]];
var hist={},H={days:1,from:0,to:0,custom:false};
function getHist(id){if(hist[id])return hist[id];
 return hist[id]=fetch(DATA+'history/'+id+'.json',{cache:'no-cache'}).then(function(r){if(!r.ok)throw 0;return r.json()}).catch(function(){return fetch(DATA+'history/'+id.toLowerCase()+'.json',{cache:'no-cache'}).then(function(r){return r.ok?r.json():[]}).catch(function(){return []})})}
function sod(ms){var d=new Date(ms);d.setHours(0,0,0,0);return d.getTime()}
function eod(ms){var d=new Date(ms);d.setHours(23,59,59,999);return d.getTime()}
function setPeriod(days){H.custom=false;H.days=days;var now=Date.now();H.from=sod(now-(days-1)*86400000);H.to=eod(now)}
function histPage(it){
 var tabs=RANGES.map(function(r){return '<button data-d="'+r[1]+'" class="'+(!H.custom&&r[1]===H.days?'on':'')+'">'+esc(t(r[0]))+'</button>'}).join('');
 return '<div class="hp"><div class="htop"><button class="hcal" id="hCal" aria-label="range">'+icon('update_calendar',22)+'</button><div class="htabs">'+tabs+'</div></div><div id="hBody"><div id="chart"></div><div id="hList"></div></div></div>'}
function fmtV(it,v){if(it.kind==='crypto')return v>=1?grp(v,2):v.toLocaleString('en-US',{maximumFractionDigits:6});return grp(v,it.d)}
function histEntries(it,h){
 var all=(h||[]).slice().sort(function(a,b){return a[0]-b[0]});
 var out=[];all.forEach(function(p,i){var ms=p[0]*1000;var prev=i?all[i-1][1]:null;out.push({ms:ms,price:p[1],tr:prev==null||p[1]===prev?0:(p[1]>prev?1:-1)})});
 return out}
function histRender(it,h,slide){
 var P=$('#page'),ents=histEntries(it,h).filter(function(e){return e.ms>=H.from&&e.ms<=H.to});
 var allE=histEntries(it,h),before=allE.filter(function(e){return e.ms<H.from}).pop();
 var list=ents.slice().reverse(),cb=$('#chart'),lb=$('#hList');if(!cb)return;
 if(!ents.length&&!before){cb.style.display='none';lb.innerHTML='<div class="hint" style="margin-top:60px;font-size:14px">'+esc(t('no_data'))+'</div>';return}
 cb.style.display='';
 drawChart(it,ents,before);
 if(!ents.length){lb.innerHTML='<div class="hint" style="margin-top:30px;font-size:14px">'+esc(t('no_data'))+'</div>';return}
 lb.innerHTML=list.map(function(e){var d=new Date(e.ms);var ic=e.tr>0?'<svg class="hi" viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#1E9E46"/><path d="M12,6L17.5,12.2H13.7V18H10.3V12.2H6.5Z" fill="#fff"/></svg>':e.tr<0?'<svg class="hi" viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#E0312D"/><path d="M12,18L6.5,11.8H10.3V6H13.7V11.8H17.5Z" fill="#fff"/></svg>':'<svg class="hi" viewBox="0 0 24 24"><circle cx="12" cy="12" r="12" fill="#AEB8C4"/><circle cx="12" cy="12" r="4.2" fill="#E9EDF2"/></svg>';
  return '<div class="hrw">'+ic+'<span class="hpr">'+fmtV(it,e.price)+'</span><span class="hdt">'+d.getDate()+'/'+(d.getMonth()+1)+'</span><span class="htm">'+pad2(d.getHours())+':'+pad2(d.getMinutes())+'</span></div>'}).join('');
 if(slide){[cb,lb].forEach(function(el){el.animate([{transform:'translateX('+slide+'px)',opacity:.25},{transform:'none',opacity:1}],{duration:200,easing:'ease-out'})})}
}
function niceScale(mn,mx){var raw=mx-mn,span=raw<1e-9?Math.max(Math.abs(mx)*.01,1):raw,rs=span/4,mag=Math.pow(10,Math.floor(Math.log10(rs))),nm=rs/mag,st=mag*(nm<=1?1:nm<=2?2:nm<=2.5?2.5:nm<=5?5:10);
 return {min:Math.floor((mn-span*.05)/st)*st,max:Math.ceil((mx+span*.05)/st)*st,step:st}}
function drawChart(it,ents,before){
 var box=$('#chart');if(!box)return;
 var DAY=86400000,HR=3600000,now=Date.now(),daily=sod(Math.max(Math.min(H.to,now),H.from))>sod(H.from),pts,aF,aT;
 var raw=ents.map(function(e){return [e.ms,e.price]});
 var lastP=raw.length?raw[raw.length-1][1]:(before?before.price:it.p),nowE=Math.min(H.to,now);
 if(!daily){if(before)raw.unshift([H.from,before.price]);if(raw.length&&raw[raw.length-1][0]<nowE-60000&&lastP!=null)raw.push([nowE,lastP])}
 if(daily){var m={},ord=[];raw.forEach(function(p){var d=sod(p[0]);if(!m[d]){m[d]=[];ord.push(d)}m[d].push(p[1])});
  var d0=sod(H.from),dN=sod(nowE);if(before&&!m[d0]){m[d0]=[before.price];ord.push(d0)}if(lastP!=null&&!m[dN]&&dN>=d0){m[dN]=[lastP];ord.push(dN)}pts=ord.sort(function(a,b){return a-b}).map(function(d){return [d,m[d].reduce(function(a,b){return a+b},0)/m[d].length]});aF=sod(H.from);aT=sod(Math.max(Math.min(H.to,now),H.from))}
 else{pts=raw;aF=H.from;aT=Math.max(Math.min(H.to,now),raw.length?raw[raw.length-1][0]:H.to)}
 if(aT<=aF)aT=aF+1;
 var W=box.clientWidth||360,Hh=250;
 if(pts.length<1){box.innerHTML='<div class="hint" style="padding-top:100px;font-size:13px">'+esc(t('chart_no_data'))+'</div>';box.style.height=Hh+'px';return}
 var pr=pts.map(function(p){return p[1]}),sc=niceScale(Math.min.apply(0,pr),Math.max.apply(0,pr)),rng=(sc.max-sc.min)||1;
 var cl=58,cr=W-22,ct=14,cb2=Hh-34,cw=Math.max(1,cr-cl),ch=Math.max(1,cb2-ct);
 var xF=function(t0){return cl+cw*((t0-aF)/(aT-aF))},yF=function(v){return cb2-((v-sc.min)/rng*ch)};
 var tick=function(v){return sc.step>=1?grp(Math.round(v),0):sc.step>=.1?v.toFixed(1):v.toFixed(2)};
 var s='';
 var n=Math.max(1,Math.min(12,Math.round(rng/sc.step)));
 for(var i=0;i<=n;i++){var v=sc.min+i*sc.step,y=yF(v);s+='<line x1="'+cl+'" x2="'+cr+'" y1="'+y+'" y2="'+y+'" class="gl"/><text x="6" y="'+(y+4)+'" text-anchor="start">'+tick(v)+'</text>'}
 var xt=[],span=aT-aF;
 if(!daily){var sh=span<=6*HR?1:span<=12*HR?2:span<=20*HR?3:4,c=new Date(aF);c.setMinutes(0,0,0);var g=0;while((c.getTime()<aF||c.getHours()%sh!==0)&&g++<48)c.setHours(c.getHours()+1);g=0;while(c.getTime()<=aT&&g++<30){xt.push([c.getTime(),pad2(c.getHours())+':00']);c.setHours(c.getHours()+sh)}
  if(!xt.length)xt=[[aF,pad2(new Date(aF).getHours())+':'+pad2(new Date(aF).getMinutes())],[aT,pad2(new Date(aT).getHours())+':'+pad2(new Date(aT).getMinutes())]]}
 else{var days=Math.round(span/DAY)+1,k=days<=7?1:days<=14?2:days<=35?5:days<=70?10:15,c2=new Date(aF),g2=0;c2.setHours(0,0,0,0);while(c2.getTime()<=aT&&g2++<40){xt.push([c2.getTime(),c2.getDate()+'/'+(c2.getMonth()+1)]);c2.setDate(c2.getDate()+k)}}
 xt.forEach(function(q){var x=xF(q[0]);s+='<line x1="'+x+'" x2="'+x+'" y1="'+ct+'" y2="'+cb2+'" class="gl"/><text x="'+x+'" y="'+(cb2+22)+'" text-anchor="middle">'+q[1]+'</text>'});
 var xs=pts.map(function(p){return xF(p[0])}),ys=pts.map(function(p){return yF(p[1])}),topY=Math.min.apply(0,ys);
 var col=function(i){var a=pts[i][1],b=pts[i+1][1];return b>a?'up':b<a?'down':'flat'};
 var defs='<defs>'+['up','down','flat'].map(function(c){return '<linearGradient id="g'+c+'" gradientUnits="userSpaceOnUse" x1="0" y1="'+topY+'" x2="0" y2="'+cb2+'"><stop offset="0" style="stop-color:var(--c'+c+');stop-opacity:.37"/><stop offset="1" style="stop-color:var(--c'+c+');stop-opacity:.024"/></linearGradient>'}).join('')+'</defs>';
 var fills='',lines='';
 for(var j=0;j<pts.length-1;j++){var c3=col(j);fills+='<path d="M'+xs[j]+' '+ys[j]+'L'+xs[j+1]+' '+ys[j+1]+'L'+xs[j+1]+' '+cb2+'L'+xs[j]+' '+cb2+'Z" fill="url(#g'+c3+')"/>';lines+='<line x1="'+xs[j]+'" y1="'+ys[j]+'" x2="'+xs[j+1]+'" y2="'+ys[j+1]+'" style="stroke:var(--c'+c3+')" stroke-width="2.5" stroke-linecap="round"/>'}
 var dots='',idx=pts.length<=31?pts.map(function(_,i){return i}):[pts.length-1],R=pts.length>14?3.2:3.8;
 idx.forEach(function(i){var c4=i===0?col(0):col(i-1);dots+='<circle cx="'+xs[i]+'" cy="'+ys[i]+'" r="'+(R+1.7)+'" style="fill:var(--card)"/><circle cx="'+xs[i]+'" cy="'+ys[i]+'" r="'+R+'" style="fill:var(--c'+c4+')"/>'});
 box.style.height=Hh+'px';
 box.innerHTML='<svg class="ch" width="'+W+'" height="'+Hh+'" viewBox="0 0 '+W+' '+Hh+'">'+defs+s+fills+lines+dots+'</svg>'}
function bindHist(it){
 var P=$('#page'),tabs=[].slice.call(P.querySelectorAll('[data-d]'));
 var draw=function(slide){getHist(it.id).then(function(h){if(route!=='h/'+it.id)return;histRender(it,h,slide)})};
 var cur=function(){return H.custom?-1:RANGES.map(function(r){return r[1]}).indexOf(H.days)};
 var pick=function(days){var prev=cur();setPeriod(days);tabs.forEach(function(b){b.classList.toggle('on',+b.dataset.d===days)});var ni=cur();draw(prev>-1&&prev!==ni?(ni<prev?56:-56):0);wave()};
 tabs.forEach(function(b){b.onclick=function(){pick(+b.dataset.d)}});
 $('#hCal').onclick=function(){openRange(function(f,t2){H.custom=true;H.from=f;H.to=t2;tabs.forEach(function(b){b.classList.remove('on')});draw(0);wave()})};
 histSwipe=function(dx){var i=cur();if(i<0)return;var n=dx>0?i-1:i+1;if(n<0||n>3)return;pick(RANGES[n][1])};
 draw(0)}
var histSwipe=null;
function ymd(ms){var d=new Date(ms);return d.getFullYear()+'-'+pad2(d.getMonth()+1)+'-'+pad2(d.getDate())}
function openRange(cb){
 var now=Date.now(),old=document.getElementById('rdlg');if(old)old.remove();
 var d=document.createElement('div');d.id='rdlg';d.className='dlg';
 d.innerHTML='<div class="dc"><h3>'+esc(t('pick_range_title'))+'</h3><input type="date" id="rF" min="'+ymd(now-59*86400000)+'" max="'+ymd(now)+'" value="'+ymd(now)+'"><input type="date" id="rT" min="'+ymd(now-59*86400000)+'" max="'+ymd(now)+'" value="'+ymd(now)+'"><div class="da2"><button id="rC">'+esc(t('cancel'))+'</button><button id="rO">'+esc(t('pick_range_ok'))+'</button></div></div>';
 document.body.appendChild(d);
 $('#rC',d).onclick=function(){d.remove()};d.onclick=function(e){if(e.target===d)d.remove()};
 $('#rO',d).onclick=function(){var f=new Date($('#rF',d).value+'T00:00:00').getTime(),t2=new Date($('#rT',d).value+'T23:59:59').getTime();if(isNaN(f)||isNaN(t2))return;if(t2<f){var x=f;f=t2;t2=x}d.remove();cb(sod(f),eod(t2))}}
/* ---------- calculator ---------- */
var C={amt:'',src:'usd',dst:null,pop:null};
function copts(){
 var o=[{id:'usd',name:t('currency_us_dollar'),f:'us',rate:100,unit:t('currency_us_dollar')}];
 var all=LOCAL.concat(WORLD,CRYPTO,METALS),seen={};
 var ord=S.favs.filter(function(i){return all.indexOf(i)>-1}).concat(all);
 ord.forEach(function(i){if(seen[i])return;seen[i]=1;var it=IT[i];if(!it||!it.p)return;
  var rate=(it.kind==='world'&&(i==='eur'||i==='gbp'))?10000/it.p:(it.kind==='crypto'||it.kind==='metal')?100/it.p:it.p;
  var unit=it.kind==='crypto'?it.c:it.kind==='metal'?t(it.mu)+' '+t(it.n):it.kind==='local'||i==='usd_official'?t('currency_iqd_unit'):t('currency_'+({toman:'irr'}[i]||i)+'_unit');
  o.push({id:i,name:t(it.n),f:it.f,rate:rate,unit:unit,kind:it.kind})});
 return o}
function fmtAmt(s){if(!s)return '';var p=s.split('.');var i=p[0].replace(/\B(?=(\d{3})+(?!\d))/g,',');return p.length>1?i+'.'+p[1]:i}
function calcOut(){
 var o=copts(),s=o.filter(function(x){return x.id===C.src})[0]||o[0],d=o.filter(function(x){return x.id===C.dst})[0]||o[1]||o[0];
 var a=parseFloat(C.amt);if(!C.amt||isNaN(a)||!s||!d)return {n:'—',u:d?d.unit:'',s:s,d:d,o:o};
 var r=a*d.rate/s.rate,big=!(d.kind==='crypto'||d.kind==='metal')||Math.abs(r)>=1;
 var txt=r.toLocaleString('en-US',{maximumFractionDigits:big?2:8});return {n:txt,u:d.unit,s:s,d:d,o:o}}
function calcPage(){
 var c=calcOut();if(!C.dst)C.dst=c.d.id;
 var pad=function(k){return '<button data-k="'+k+'" class="'+(k==='del'?'del':'')+'">'+(k==='del'?'⌫':k)+'</button>'};
 return '<div class="cl" id="cl"><div class="cin'+(C.amt?'':' ph')+'" id="cAmt">'+(C.amt?fmtAmt(C.amt):'0')+'</div>'
 +'<div class="sel2"><button class="sl s1" data-sel="src"><img src="img/'+c.s.f+'.webp" alt=""><span>'+esc(c.s.name)+'</span></button><button class="sl s2" data-sel="dst"><img src="img/'+c.d.f+'.webp" alt=""><span>'+esc(c.d.name)+'</span></button><button class="swp" id="swp" aria-label="swap">'+icon('swap_vertical_white',24)+'</button></div>'
 +'<div class="rp n1" id="rN">'+esc(c.n)+'</div><div class="rp n2" id="rU">'+esc(c.u)+'</div>'
 +'<div class="pad3">'+['9','8','7','6','5','4','3','2','1','del','0','.'].map(pad).join('')+'</div></div>'}
function calcUpd(){var c=calcOut();$('#cAmt').textContent=C.amt?fmtAmt(C.amt):'0';$('#cAmt').classList.toggle('ph',!C.amt);$('#rN').textContent=c.n;$('#rU').textContent=c.u}
function bindCalc(){
 var cl=$('#cl'),P=$('#page');
 var press=function(k){if(k==='del')C.amt=C.amt.slice(0,-1);else if(k==='.'){if(C.amt.indexOf('.')<0)C.amt=(C.amt||'0')+'.'}else{if(C.amt.replace('.','').length<14)C.amt+=k;if(C.amt==='0'+k&&k!=='.')C.amt=k}calcUpd()};
 cl.querySelectorAll('[data-k]').forEach(function(b){var tm;b.onclick=function(){press(b.dataset.k)};
  if(b.dataset.k==='del'){b.addEventListener('touchstart',function(){tm=setTimeout(function(){C.amt='';calcUpd();b.dataset.lp=1},500)},{passive:true});b.addEventListener('touchend',function(){clearTimeout(tm)});b.addEventListener('click',function(e){if(b.dataset.lp){e.stopImmediatePropagation();delete b.dataset.lp}},true)}});
 $('#swp').onclick=function(){var o=calcOut();var s=C.src,d=o.d.id;C.src=d;C.dst=s;P.innerHTML=calcPage();bindCalc()};
 cl.querySelectorAll('[data-sel]').forEach(function(b){b.onclick=function(e){e.stopPropagation();openPop(b.dataset.sel,b)}})}
function closePop(){var p=$('#cpop');if(p)p.remove()}
function openPop(which,btn){
 closePop();var cl=$('#cl'),o=copts(),cur=which==='src'?C.src:C.dst;
 var p=document.createElement('div');p.className='pop';p.id='cpop';
 var top=btn.offsetTop+btn.offsetHeight+4+$('.sel2').offsetTop;
 p.style.top=top+'px';p.style.left='18px';p.style.right='18px';p.style.maxHeight=Math.min(5*58+12,window.innerHeight*.48)+'px';
 p.innerHTML=o.map(function(x){return '<button data-o="'+x.id+'"><img src="img/'+x.f+'.webp" alt=""><span>'+esc(x.name)+'</span></button>'}).join('');
 cl.appendChild(p);
 p.onclick=function(e){var b=e.target.closest('[data-o]');if(!b)return;if(which==='src')C.src=b.dataset.o;else C.dst=b.dataset.o;$('#page').innerHTML=calcPage();bindCalc()};
 var cur2=p.querySelector('[data-o="'+cur+'"]');if(cur2)p.scrollTop=cur2.offsetTop-60}
document.addEventListener('click',function(e){if(!e.target.closest('#cpop'))closePop()},true);
/* ---------- render ---------- */
function render(keepScroll,before,fade){
 applyLang();header();nav();instBar();
 var P=$('#page'),sc=keepScroll?P.scrollTop:0,h='';
 if(!IT.baghdad)IT=items();
 if(TABS.indexOf(route)>-1){
  if(route==='home')h=homePage();
  else{var l=listFor(route),has=l.some(function(i){return i.p!=null});h=stripHTML()+'<div class="list">'+(has?l.map(function(i){return rowHTML(i)}).join(''):emptyBox(route))+'</div>'}
 }else if(route==='settings')h=settingsPage();
 else if(route==='calc')h=calcPage();
 else if(route==='about')h=aboutPage();
 else if(route==='guide'||route==='terms')h=infoPage(route);
 else if(route==='social')h=socialPage();
 else if(route==='support')h=supportPage();
 else if(route.indexOf('h/')===0){var it=IT[route.slice(2)];if(it&&!hinit){setPeriod(1);hinit=true}h=it?histPage(it):''}
 P.innerHTML=h;
 P.classList.toggle('hist',route.indexOf('h/')===0);P.classList.remove('fade');if(fade){void P.offsetWidth;P.classList.add('fade')}
 P.scrollTop=sc;
 if(route==='calc')bindCalc();else if(route==='support')bindSupport();else if(route.indexOf('h/')===0&&IT[route.slice(2)])bindHist(IT[route.slice(2)]);
 if(before){P.querySelectorAll('[data-p]').forEach(function(el){var id=el.dataset.p,it=IT[id];if(it&&before[id]!=null&&it.p!=null&&before[id]!==it.p)animPrice(el,fmtPrice(it))})}
}
function animPrice(el,final){
 var n=final.length,step=0;var iv=setInterval(function(){step++;if(step>=12){el.textContent=final;clearInterval(iv);return}
  var out='';for(var i=0;i<n;i++){var c=final[i];if(/\d/.test(c)){var settle=Math.round(7+(i/n)*4);out+=step>=settle?c:Math.floor(Math.random()*10)}else out+=c}el.textContent=out},75)}
/* ---------- wave ---------- */
var waving=false;
function wave(){
 if(waving)return;var ttl=$('#ttl'),w=$('#ttlw');if(!ttl||!w||!ttl.firstChild||!ttl.firstChild.nodeValue)return;
 waving=true;var txt=ttl.firstChild.nodeValue,box=w.getBoundingClientRect(),cs=getComputedStyle(ttl);
 var els=[],anims=[];
 for(var i=0;i<txt.length;i++){
  var rg=document.createRange();rg.setStart(ttl.firstChild,i);rg.setEnd(ttl.firstChild,i+1);var r=rg.getBoundingClientRect();
  if(!r.width||txt[i]===' ')continue;
  var e=document.createElement('span');e.className='wc';e.textContent=txt;
  var l=r.left-box.left,tp=r.top-box.top;
  e.style.clipPath='inset(0 '+(box.width-(l+r.width))+'px 0 '+l+'px)';
  e.style.transformOrigin=(l+r.width/2)+'px '+(tp+r.height/2)+'px';
  w.appendChild(e);els.push(e);
  var kf=[0,.25,.5,.75,1].map(function(f){var k=Math.sin(f*Math.PI);return {transform:'translateY('+(-7*k)+'px) scale('+(1+.15*k)+')',color:k?'rgb('+Math.round(255+(56-255)*k)+','+Math.round(255+(239-255)*k)+','+Math.round(255+(125-255)*k)+')':'#fff',offset:f}});
  anims.push(e.animate(kf,{duration:400,delay:els.length*80,fill:'both',easing:'linear'}));
 }
 ttl.style.visibility='hidden';
 var done=anims.length?anims[anims.length-1].finished:Promise.resolve();
 done.then(function(){els.forEach(function(e){e.remove()});ttl.style.visibility='';waving=false}).catch(function(){els.forEach(function(e){e.remove()});ttl.style.visibility='';waving=false});
}
/* ---------- install bar ---------- */
var IN={ar:{bar:'تثبيت التطبيق',ok:'تم تثبيت التطبيق بنجاح',title:'تثبيت التطبيق',done:'تم',cancel:'إلغاء',
 ios:'اضغط زر المشاركة في Safari (المربع والسهم للأعلى) ثم اختر «إضافة إلى الشاشة الرئيسية» ثم «إضافة».',
 iosx:'افتح هذه الصفحة في متصفح Safari، ثم اضغط زر المشاركة واختر «إضافة إلى الشاشة الرئيسية» ثم «إضافة».',
 aff:'اضغط قائمة المتصفح (⋮) ثم اختر «تثبيت» أو «إضافة إلى الشاشة الرئيسية».',
 sam:'اضغط قائمة المتصفح (☰) ثم «إضافة الصفحة إلى» ثم «الشاشة الرئيسية».',
 desk:'اضغط أيقونة التثبيت في شريط العنوان أعلى المتصفح، أو من قائمة المتصفح اختر «تثبيت التطبيق».',
 mac:'من قائمة Safari اختر «ملف» ثم «إضافة إلى Dock».',
 ff:'متصفح Firefox على الكمبيوتر لا يدعم تثبيت التطبيقات. افتح الرابط في Chrome أو Edge.',
 gen:'من قائمة المتصفح اختر «تثبيت التطبيق» أو «إضافة إلى الشاشة الرئيسية».'},
 en:{bar:'Install app',ok:'App installed successfully',title:'Install the app',done:'Done',cancel:'Cancel',
 ios:'Tap the Share button in Safari (square with an up arrow), then choose “Add to Home Screen”, then “Add”.',
 iosx:'Open this page in Safari, then tap Share and choose “Add to Home Screen”, then “Add”.',
 aff:'Tap the browser menu (⋮), then choose “Install” or “Add to Home screen”.',
 sam:'Tap the browser menu (☰), then “Add page to”, then “Home screen”.',
 desk:'Click the install icon in the address bar, or choose “Install app” from the browser menu.',
 mac:'In Safari choose File, then “Add to Dock”.',
 ff:'Desktop Firefox does not support installing apps. Open the link in Chrome or Edge.',
 gen:'From the browser menu choose “Install app” or “Add to Home screen”.'},
 tr:{bar:'Uygulamayı yükle',ok:'Uygulama başarıyla yüklendi',title:'Uygulamayı yükle',done:'Tamam',cancel:'İptal',
 ios:'Safari’de Paylaş düğmesine (yukarı oklu kare) dokunun, ardından “Ana Ekrana Ekle” ve “Ekle” seçin.',
 iosx:'Bu sayfayı Safari’de açın, Paylaş’a dokunun ve “Ana Ekrana Ekle” ile “Ekle” seçin.',
 aff:'Tarayıcı menüsüne (⋮) dokunun, ardından “Yükle” veya “Ana ekrana ekle” seçin.',
 sam:'Tarayıcı menüsüne (☰) dokunun, ardından “Sayfayı ekle” ve “Ana ekran” seçin.',
 desk:'Adres çubuğundaki yükleme simgesine tıklayın veya tarayıcı menüsünden “Uygulamayı yükle” seçin.',
 mac:'Safari’de Dosya menüsünden “Dock’a Ekle” seçin.',
 ff:'Masaüstü Firefox uygulama yüklemeyi desteklemez. Bağlantıyı Chrome veya Edge’de açın.',
 gen:'Tarayıcı menüsünden “Uygulamayı yükle” veya “Ana ekrana ekle” seçin.'},
 ku:{bar:'دامەزراندنی ئەپ',ok:'ئەپەکە بە سەرکەوتوویی دامەزرا',title:'دامەزراندنی ئەپ',done:'تەواو',cancel:'هەڵوەشاندنەوە',
 ios:'لە Safari دوگمەی هاوبەشکردن (چوارگۆشەی تیرەکەی سەرەوە) دابگرە، پاشان «زیادکردن بۆ شاشەی سەرەکی» و «زیادکردن» هەڵبژێرە.',
 iosx:'ئەم پەڕەیە لە Safari بکەرەوە، پاشان هاوبەشکردن دابگرە و «زیادکردن بۆ شاشەی سەرەکی» هەڵبژێرە.',
 aff:'لیستی وێبگەڕ (⋮) دابگرە، پاشان «دامەزراندن» یان «زیادکردن بۆ شاشەی سەرەکی» هەڵبژێرە.',
 sam:'لیستی وێبگەڕ (☰) دابگرە، پاشان «زیادکردنی پەڕە بۆ» و «شاشەی سەرەکی» هەڵبژێرە.',
 desk:'ئایکۆنی دامەزراندن لە شریتی ناونیشان دابگرە، یان لە لیستی وێبگەڕ «دامەزراندنی ئەپ» هەڵبژێرە.',
 mac:'لە Safari «File» پاشان «Add to Dock» هەڵبژێرە.',
 ff:'Firefox لەسەر کۆمپیوتەر پشتگیری دامەزراندنی ئەپ ناکات. بەستەرەکە لە Chrome یان Edge بکەرەوە.',
 gen:'لە لیستی وێبگەڕ «دامەزراندنی ئەپ» یان «زیادکردن بۆ شاشەی سەرەکی» هەڵبژێرە.'}};
var DL='<svg class="i" width="18" height="18" viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z" fill="currentColor"/></svg>';
var CK='<svg class="i" width="18" height="18" viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" fill="currentColor"/></svg>';
var deferred=null,instState=lsGet('inst','')==='1'?'done':'',okTimer=null;
function isStandalone(){return window.navigator.standalone===true||(window.matchMedia&&(matchMedia('(display-mode: standalone)').matches||matchMedia('(display-mode: window-controls-overlay)').matches||matchMedia('(display-mode: minimal-ui)').matches))}
function ua(){var u=navigator.userAgent,ios=/iPhone|iPad|iPod/.test(u)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1),and=/Android/.test(u);
 return {ios:ios,android:and,safari:/Safari/.test(u)&&!/Chrome|CriOS|FxiOS|EdgiOS|OPiOS|Android/.test(u),ff:/Firefox|FxiOS/.test(u),sam:/SamsungBrowser/.test(u),mac:/Macintosh/.test(u)&&!ios,desktop:!ios&&!and}}
function instStep(){var d=ua(),x=IN[S.lang]||IN.ar;
 if(d.ios)return (d.safari&&!/CriOS|FxiOS|EdgiOS/.test(navigator.userAgent))?x.ios:x.iosx;
 if(d.android)return d.sam?x.sam:d.ff?x.aff:x.gen;
 if(d.mac&&d.safari)return x.mac;
 if(d.ff)return x.ff;
 return x.desk}
function instVisible(){return !isStandalone()&&(TABS.indexOf(route)>-1||route==='settings')&&(instState!=='done'||okTimer)}
function instBar(){
 var el=$('#ibar');if(!el)return;
 var x=IN[S.lang]||IN.ar,show=instVisible()&&!(instState==='done'&&!okTimer);
 if(!show){el.style.display='none';return}
 el.style.display='';
 var ok=instState==='done';
 el.className='strip ibar'+(ok?' okb':'');
 el.innerHTML='<span class="lb">'+esc(ok?x.ok:x.bar)+'</span><span class="sep"></span>'+(ok?CK:DL);
}
function instDialog(){
 var x=IN[S.lang]||IN.ar,old=document.getElementById('idlg');if(old)old.remove();
 var d=document.createElement('div');d.id='idlg';d.className='dlg';
 d.innerHTML='<div class="dc" style="direction:'+(RTL[S.lang]?'rtl':'ltr')+'"><h3>'+esc(x.title)+'</h3><p style="margin:0 0 14px;line-height:1.9;font-size:14px">'+esc(instStep())+'</p><div class="da2"><button id="iC">'+esc(x.cancel)+'</button><button id="iO">'+esc(x.done)+'</button></div></div>';
 document.body.appendChild(d);
 $('#iC',d).onclick=function(){d.remove()};d.onclick=function(e){if(e.target===d)d.remove()};
 $('#iO',d).onclick=function(){d.remove();instDone()}}
function instDone(){instState='done';lsSet('inst','1');clearTimeout(okTimer);okTimer=setTimeout(function(){okTimer=null;instBar();layoutFix()},4000);instBar()}
function layoutFix(){}
window.addEventListener('beforeinstallprompt',function(e){e.preventDefault();deferred=e;if(instState==='done'){instState='';lsSet('inst','')}instBar()});
window.addEventListener('appinstalled',function(){deferred=null;instDone()});
document.addEventListener('click',function(e){var b=e.target.closest('#ibar');if(!b||instState==='done')return;
 if(deferred){var p=deferred;deferred=null;p.prompt();p.userChoice.then(function(r){if(r&&r.outcome==='accepted')instDone();else{deferred=p}}).catch(function(){})}else instDialog()});
/* ---------- haptics ---------- */
var hapEl=null;
function hap(){
 try{if(navigator.vibrate){navigator.vibrate(6);return}}catch(e){}
 try{if(!hapEl){var l=document.createElement('label');l.style.cssText='position:fixed;left:-9999px;top:0;opacity:0;pointer-events:none';var i=document.createElement('input');i.type='checkbox';i.setAttribute('switch','');l.appendChild(i);document.body.appendChild(l);hapEl=l}hapEl.click()}catch(e){}
}
document.addEventListener('click',function(e){if(e.target.closest('button,.row,[data-a],[data-act],[data-tab],a'))hap()},true);
/* ---------- events ---------- */
document.addEventListener('click',function(e){
 var f=e.target.closest('[data-fav]');
 if(f){e.stopPropagation();var id=f.dataset.fav,i=S.favs.indexOf(id);if(i>-1)S.favs.splice(i,1);else{if(S.favs.length>=8)S.favs.shift();S.favs.push(id)}lsSet('favs',JSON.stringify(S.favs));render(true);return}
 var a=e.target.closest('[data-act]');
 if(a){var k=a.dataset.act;if(k==='refresh')load(true);else go(k);return}
 var sa=e.target.closest('[data-a]');
 if(sa){var k2=sa.dataset.a;if(k2==='lang'){langOpen=!langOpen;render(true)}else if(k2==='theme'){S.theme=S.theme==='dark'?'light':'dark';lsSet('theme',S.theme);render(true)}else if(k2==='share')shareApp();else go(k2);return}
 var lo=e.target.closest('[data-lang]');
 if(lo){S.lang=lo.dataset.lang;lsSet('lang',S.lang);langOpen=false;render(true);return}
 var row=e.target.closest('.row');
 if(row&&!S.drag){go('h/'+row.dataset.id)}
});
/* swipe + pull-to-refresh */
(function(){
 var P=$('#page'),sx,sy,st,pull=0,tracking=false,cs={};
 P.addEventListener('touchstart',function(e){if(e.touches.length!==1||S.drag){tracking=false;return}var t0=e.touches[0];sx=t0.clientX;sy=t0.clientY;st=Date.now();tracking=true;pull=0},{passive:true});
 P.addEventListener('touchmove',function(e){if(!tracking)return;var t0=e.touches[0],dy=t0.clientY-sy,dx=t0.clientX-sx;
  if(P.scrollTop<=0&&dy>10&&Math.abs(dy)>Math.abs(dx)&&TABS.concat(['settings']).indexOf(route)>-1){pull=Math.min(dy,110);cs.opacity=Math.min(1,pull/70);cs.transform='translateY('+(pull*.55)+'px) rotate('+(pull*3)+'deg)'}},{passive:true});
 P.addEventListener('touchend',function(e){if(!tracking)return;tracking=false;var t0=e.changedTouches[0],dx=t0.clientX-sx,dy=t0.clientY-sy,dtm=Date.now()-st;
  var wasPull=pull;cs.opacity=0;cs.transform='';
  if(wasPull>=70){pull=0;if(navigator.vibrate)try{navigator.vibrate(10)}catch(e){}load(true);return}pull=0;
  var i=TABS.indexOf(route);
  if(histSwipe&&route.indexOf('h/')===0&&Math.abs(dx)>70&&Math.abs(dx)>Math.abs(dy)*1.6&&Math.abs(dx)/dtm>.3){histSwipe(dx);return}
  if(i>-1&&Math.abs(dx)>70&&Math.abs(dx)>Math.abs(dy)*1.6&&Math.abs(dx)/dtm>.3){
   var n=dx<0?(i+1)%5:(i+4)%5;go(TABS[n])}},{passive:true});
})();
/* drag reorder */
document.addEventListener('pointerdown',function(e){
 var h=e.target.closest('[data-drag]');if(!h)return;
 var row=h.closest('.row'),list=$('#favlist'),rows=[].slice.call(list.querySelectorAll('.row'));
 var from=rows.indexOf(row),rh=row.offsetHeight+4,y0=e.clientY,to=from;S.drag=true;row.classList.add('drag');
 try{h.setPointerCapture(e.pointerId)}catch(x){}
 var mv=function(ev){var dy=ev.clientY-y0;dy=Math.max(-from*rh,Math.min((rows.length-1-from)*rh,dy));row.style.transform='translateY('+dy+'px)';
  to=Math.max(0,Math.min(rows.length-1,Math.round(from+dy/rh)));
  rows.forEach(function(r,i){if(r===row)return;var s=0;if(from<to&&i>from&&i<=to)s=-rh;else if(from>to&&i<from&&i>=to)s=rh;r.style.transform=s?'translateY('+s+'px)':'';r.style.transition='transform .12s'})};
 var up=function(){h.removeEventListener('pointermove',mv);h.removeEventListener('pointerup',up);h.removeEventListener('pointercancel',up);
  if(to!==from){var ids=rows.map(function(r){return r.dataset.id});var m=ids.splice(from,1)[0];ids.splice(to,0,m);
   var rest=S.favs.filter(function(i){return ids.indexOf(i)<0});S.favs=ids.concat(rest);lsSet('favs',JSON.stringify(S.favs));if(navigator.vibrate)try{navigator.vibrate(10)}catch(x){}}
  setTimeout(function(){S.drag=false},50);render(true)};
 h.addEventListener('pointermove',mv);h.addEventListener('pointerup',up);h.addEventListener('pointercancel',up);e.preventDefault()});
/* ---------- boot ---------- */
var r0=parseHash();if(!valid(r0))r0=lsGet('route','home');if(!valid(r0))r0='home';
route=r0;history.replaceState(null,'','#/'+route);lsSet('route',route);
IT=items();render(false);
load(false);
setInterval(function(){if(!document.hidden)load(false)},15*60*1000);
document.addEventListener('visibilitychange',function(){if(!document.hidden)load(false)});
if('serviceWorker' in navigator)window.addEventListener('load',function(){navigator.serviceWorker.register('sw.js').catch(function(){})});
})();
