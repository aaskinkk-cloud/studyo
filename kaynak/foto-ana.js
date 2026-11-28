/* ===== 2.29 STÜDYO · Karanlık Oda ana ekranı = yerel video Stüdyo ana ekranının (Ana.kt) aynısı =====
   Başlık şeridi "Karanlık Oda / Fotoğraf düzenleyici · web" + ⋯ menü (kitaplık, ayarlar, tema, görünüm, sürümler) ·
   sarı "Yeni fotoğraf" + "Video" kartı (dokun = yerel Stüdyo'nun taslak ekranı) · yapışkan "Son çalışmalarım" + Tümü ·
   satır kartları (küçük resim, ad, boyut, tarih, ⋮ = kitaplığın kendi menüsü). Varsayılan görünüm budur.
   2.28 "video düzeni" (logo, büyük başlık, alt gezinme) pasif seçenek olarak durur. */
/* ===== 2.28 STÜDYO · Karanlık Oda ana ekranı — yerel video Stüdyo ana ekranının düzeni =====
   Logo + KARANLIK ODA + tema · büyük başlık · iki kart (Fotoğraf mavi · Video turuncu) · geniş "Video taslaklarım" kartı ·
   "Son çalışmalar" yatay kartlar · alt gezinme (Ana sayfa · Kitaplık · Video · Ayarlar) · en altta sürümler.
   Video kartı / gezinmesi her durumda yerel Stüdyo'nun taslak ekranına döner (Android); yerel yoksa web Kareden Videoya.
   Görünümler: ⚙ Ayarlar › "Ana ekran görünümü" → Stüdyo düzeni (2.29, varsayılan) · Mavi-turuncu (2.28) · 2.26 kartları · Önceki ana ekran.
   2.28 KÖK NEDEN (yeni blok telefonda görünmüyordu): #home bir flex sütunu; 2.27'de .home-wrap'e verilen overflow-x:hidden onun
   en küçük yüksekliğini 0 yaptı, ekran boyuna sıkıştı; içindeki blok (overflow:hidden) de ezilip çizgiye döndü. Şimdi ikisi flex:none. */
(function(){
  if (window.__fAna) return; window.__fAna = 1;
  var GOR = 'studyo.fAnaGor', ESKI = 'studyo.fAnaEski';
  function gor(){ try { var g = localStorage.getItem(GOR); if (g) return g; return localStorage.getItem(ESKI) === '1' ? 'eski' : 'yerel'; } catch(e){ return 'yerel'; } }
  var css = document.createElement('style');
  css.textContent =
    '#home .home-wrap{flex:none}.fa-blok,.fv-blok{flex:none}' +
    /* --- 2.29 yerel Stüdyo ana ekranı (Ana.kt; renkler Tema.kt gece/gündüz) --- */
    ':root{--fk-zemin:#F2F5F8;--fk-kart:#fff;--fk-kart2:#E6ECF1;--fk-cizgi:#C5D1DB;--fk-yazi:#15212C;--fk-soluk:#5A6E80;--fk-sari:#E8C860;--fk-ink:#1B1204;--fk-turkuaz:#0E9FAD}' +
    '[data-theme="dark"]{--fk-zemin:#121A21;--fk-kart:#1B2833;--fk-kart2:#243444;--fk-cizgi:#2E4153;--fk-yazi:#E6EDF3;--fk-soluk:#8AA0B2;--fk-turkuaz:#2EC5D3}' +
    'body.fk-acik #home{background:var(--fk-zemin);overflow-x:hidden}body.fk-acik #home .home-top{display:none}body.fk-acik #home .home-wrap{padding-top:0}' +
    'body.fk-acik:not(.fa-kit) #libBody,body.fk-acik:not(.fa-kit) #home .sec-head:has(#libTabs){display:none}' +
    '.fk-blok{color:var(--fk-yazi);min-width:0}' +
    '.fk-ust{position:relative;display:flex;align-items:center;margin:0 -16px;padding:10px 4px 10px 16px;background:var(--fk-kart)}' +
    '.fk-ust>div{flex:1;min-width:0}.fk-ust h1{margin:0;font-size:22px;line-height:1.25;font-weight:700}.fk-ust p{margin:2px 0 0;font-size:12px;color:var(--fk-soluk);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '.fk-uc{flex:none;width:52px;height:48px;border:0;background:none;color:var(--fk-yazi);font:24px/1 var(--f-ui,system-ui,sans-serif);letter-spacing:1px;cursor:pointer;border-radius:12px}' +
    '.fk-menu{position:absolute;right:10px;top:calc(100% - 6px);z-index:30;min-width:240px;max-width:calc(100vw - 20px);background:var(--fk-kart2);border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,.35);padding:6px 0}' +
    '.fk-menu button{display:block;width:100%;border:0;background:none;color:var(--fk-yazi);text-align:left;font:14px/1.3 var(--f-ui,system-ui,sans-serif);padding:12px 18px;cursor:pointer}.fk-menu button:active{background:rgba(128,140,150,.18)}' +
    '.fk-menu .fk-sur{padding:10px 18px 8px;border-top:1px solid var(--fk-cizgi);margin-top:4px;font-size:12px;line-height:1.55;color:var(--fk-soluk)}' +
    '.fk-kartlar{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:14px}' +
    '.fk-k{height:108px;border:0;border-radius:16px;padding:14px;text-align:left;display:flex;flex-direction:column;justify-content:space-between;cursor:pointer;font:inherit;min-width:0}' +
    '.fk-yeni{background:var(--fk-sari);color:var(--fk-ink)}.fk-yeni em{font:700 28px/1 var(--f-ui,system-ui,sans-serif);font-style:normal}.fk-yeni b{font-size:17px}' +
    '.fk-vid{background:var(--fk-kart2);color:var(--fk-yazi)}.fk-vid svg{color:var(--fk-turkuaz);fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}' +
    '.fk-vid b{display:block;font-size:17px}.fk-vid small{display:block;font-size:12px;color:var(--fk-soluk);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '.fk-sb{position:sticky;top:0;z-index:3;background:var(--fk-zemin);display:flex;align-items:center;gap:8px;margin-top:10px;padding:12px 0 8px;border-bottom:.6px solid var(--fk-cizgi)}' +
    '.fk-sb h2{flex:1;margin:0;font-size:16px;font-weight:700}' +
    '.fk-sb button{height:34px;padding:0 15px;border-radius:17px;border:1px solid #c9a94a;background:none;color:#e0c25e;font:13px var(--f-ui,system-ui,sans-serif);cursor:pointer}' +
    '[data-theme="light"] .fk-sb button{color:#8a6d12;border-color:#c9a94a}' +
    '.fk-liste{display:flex;flex-direction:column;gap:10px;margin-top:10px}' +
    '.fk-r{display:flex;align-items:center;gap:12px;padding:10px;border-radius:14px;background:var(--fk-kart);cursor:pointer}' +
    '.fk-r img,.fk-r .fk-bosr{width:96px;height:54px;border-radius:10px;object-fit:cover;flex:none;background:var(--fk-kart2)}' +
    '.fk-r>div{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}' +
    '.fk-r b{font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.fk-r span{font-size:12px;color:var(--fk-soluk);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.fk-r small{font-size:11.5px;color:var(--fk-soluk);opacity:.8}' +
    '.fk-r>button{flex:none;width:34px;height:34px;border:0;border-radius:50%;background:none;color:var(--fk-soluk);font:700 18px/1 var(--f-ui,system-ui,sans-serif);cursor:pointer}' +
    '.fk-bos{font-size:13px;color:var(--fk-soluk);margin:10px 0 0}' +
    '.fk-diger{font-size:12px;letter-spacing:2px;color:var(--fk-soluk);margin:28px 0 0;font-weight:700}' +
    /* --- 2.28 video düzeni (renkler yerel Stüdyo AS ile aynı) --- */
    ':root{--fv-zemin:#F3F5F7;--fv-kart:#fff;--fv-kart2:#EEF1F4;--fv-cizgi:#DFE4E8;--fv-yazi:#141A1F;--fv-soluk:#5F6C75;--fv-vurgu:#E8681C;--fv-vurgu2:#F39A4C;--fv-alt:#fff;--fv-mavi:#5AA9FF}' +
    '[data-theme="dark"]{--fv-zemin:#121A21;--fv-kart:#1F2B35;--fv-kart2:#283844;--fv-cizgi:#2F3F4B;--fv-yazi:#EEF2F5;--fv-soluk:#93A3AE;--fv-vurgu:#FF8A3D;--fv-vurgu2:#FFB26B;--fv-alt:#0E151B}' +
    'body.fv-acik #home{background:var(--fv-zemin);overflow-x:hidden}body.fv-acik #home .home-top{display:none}body.fv-acik #home .home-wrap{padding-bottom:104px}' +
    'body.fa-dog #home .home-wrap>.studyo-ev{display:none}' +
    'body.fv-acik:not(.fa-kit) #libBody,body.fv-acik:not(.fa-kit) #home .sec-head:has(#libTabs){display:none}' +
    '.fv-blok{color:var(--fv-yazi);min-width:0}.fv-blok svg,.fv-nav svg{fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}' +
    '.fv-ust{display:flex;align-items:center;gap:14px;margin:4px 0 0}.fv-logo{width:44px;height:44px;border-radius:13px;flex:none;object-fit:cover}' +
    '.fv-ad{flex:1;min-width:0;font-size:18px;font-weight:800;letter-spacing:.18em;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '.fv-ust #themeBtn{flex:none;width:52px;height:52px;border-radius:50%;background:var(--fv-kart);border:1px solid var(--fv-cizgi);color:var(--fv-yazi);display:grid;place-items:center}' +
    '.fv-bas{margin:26px 0 20px}.fv-bas h1{margin:0;font-size:34px;line-height:38px;font-weight:800;letter-spacing:-.02em}.fv-bas p{margin:8px 0 0;font-size:16px;color:var(--fv-soluk)}' +
    '.fv-kartlar{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}' +
    '.fv-kk{position:relative;overflow:hidden;min-height:220px;border-radius:26px;background:var(--fv-kart);border:1px solid var(--fv-cizgi);color:var(--fv-yazi);padding:22px 16px 22px 20px;text-align:left;display:flex;flex-direction:column;cursor:pointer;font:inherit}' +
    '.fv-kk::after{content:"";position:absolute;right:-36px;top:-36px;width:130px;height:130px;border-radius:50%;background:var(--t);opacity:.12}' +
    '.fv-ik{width:66px;height:66px;border-radius:20px;display:grid;place-items:center;flex:none;position:relative}.fv-kk .fv-ik::before{content:"";position:absolute;inset:0;border-radius:20px;background:var(--t);opacity:.16}' +
    '.fv-kk .fv-ik svg{color:var(--t);position:relative}.fv-kk b{margin-top:auto;padding-top:18px;font-size:22px;font-weight:700}.fv-kk span{margin-top:6px;font-size:15px;line-height:20px;color:var(--fv-soluk)}' +
    '.fv-gk{margin-top:16px;width:100%;display:flex;align-items:center;gap:18px;border:0;border-radius:26px;padding:22px;text-align:left;cursor:pointer;font:inherit;background:linear-gradient(135deg,var(--fv-vurgu2),var(--fv-vurgu));color:#1B0F05;box-shadow:0 8px 22px rgba(232,104,28,.3)}' +
    '.fv-gk .fv-ik{background:rgba(255,255,255,.3)}.fv-gk>div:not(.fv-ik){flex:1;min-width:0}.fv-gk b{display:block;font-size:22px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.fv-gk span{display:block;margin-top:3px;font-size:15px;line-height:21px;opacity:.85}' +
    '.fv-sb{display:flex;align-items:center;margin:28px 0 12px}.fv-sb h2{flex:1;margin:0;font-size:21px;font-weight:700}.fv-sb button{border:0;background:none;color:var(--fv-vurgu);font:600 16px/1 var(--f-ui,system-ui,sans-serif);padding:4px;cursor:pointer}' +
    '.fv-sira{display:flex;gap:12px;overflow-x:auto;margin:0 -16px;padding:0 16px 4px;scrollbar-width:none;touch-action:pan-x pan-y}.fv-sira::-webkit-scrollbar{display:none}' +
    '.fv-pk{flex:none;width:150px;border:0;background:none;padding:0;text-align:left;color:var(--fv-yazi);cursor:pointer;font:inherit}' +
    '.fv-pk i{display:block;width:150px;height:190px;border-radius:18px;background:var(--fv-kart2) center/cover no-repeat;border:1px solid var(--fv-cizgi)}' +
    '.fv-pk b{display:block;margin-top:7px;font-size:14px;font-weight:400;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.fv-pk small{font-size:12.5px;color:var(--fv-soluk)}' +
    '.fv-bos{border:1.5px solid var(--fv-cizgi);border-radius:18px;padding:18px;text-align:center;font-size:14px;color:var(--fv-soluk)}' +
    '.fv-diger{font-size:12px;letter-spacing:2px;color:var(--fv-soluk);margin:30px 0 0;font-weight:700}' +
    '.fv-nav{position:fixed;left:0;right:0;bottom:0;z-index:40;display:flex;padding:6px 6px calc(6px + env(safe-area-inset-bottom,0px));background:var(--fv-alt);border-top:.8px solid var(--fv-cizgi)}' +
    '.fv-nav button{flex:1;border:0;background:none;border-radius:14px;padding:6px 0;display:flex;flex-direction:column;align-items:center;gap:4px;color:var(--fv-soluk);font:600 12.5px/1 var(--f-ui,system-ui,sans-serif);cursor:pointer}' +
    '.fv-nav button.on{color:var(--fv-yazi);font-weight:700}.fv-nav button.on svg{color:var(--fv-vurgu)}' +
    '.ko-surum{font-size:12px;line-height:1.6;color:var(--muted,#8a96a0);text-align:center;margin:8px 0 0;padding-top:12px;border-top:1px solid rgba(128,140,150,.25)}' +
    /* --- 2.26 kart düzeni (seçenek) --- */
    '.fa-blok{max-width:100%;min-width:0;overflow:hidden}' +
    '.fa-ust{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin:4px 0 18px}.fa-ust>div:first-child{min-width:0;flex:1}' +
    '.fa-ust h1{margin:0;font-size:26px;line-height:1.1;font-weight:800;letter-spacing:-.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '.fa-sag{flex:none}.fa-sag .icon-btn{flex:none}' +
    '.fa-ust p{margin:6px 0 0;font-size:13px;color:var(--muted)}' +
    '.fa-vid{flex:none;display:inline-flex;align-items:center;gap:6px;height:40px;padding:0 14px;border-radius:20px;border:1px solid rgba(128,140,150,.35);background:rgba(128,140,150,.12);color:inherit;font:600 13px/1 var(--f-ui,system-ui,sans-serif);cursor:pointer}' +
    '.fa-vid svg,.fa-kart svg{fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}' +
    '.fa-kartlar{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-bottom:22px}' +
    '.fa-kart{min-width:0;overflow:hidden;min-height:128px;border:0;border-radius:22px;padding:18px;text-align:left;display:flex;flex-direction:column;justify-content:space-between;cursor:pointer;font:inherit}' +
    '.fa-yeni{background:#E8C860;color:#141414}.fa-yeni b{font-size:19px}' +
    '.fa-video{background:var(--surface-2,#26323f);color:inherit}.fa-video b{font-size:17px}.fa-video small{color:var(--muted);font-size:12px;margin-top:4px;display:block}' +
    '.fa-bas{display:flex;align-items:center;justify-content:space-between;margin:0 0 10px;padding-bottom:10px;border-bottom:1px solid rgba(128,140,150,.25)}' +
    '.fa-bas h2{margin:0;font-size:17px}.fa-bas button{height:34px;padding:0 14px;border-radius:17px;border:1px solid #c9a94a;background:none;color:#e0c25e;font:13px var(--f-ui,system-ui,sans-serif);cursor:pointer}' +
    '.fa-liste{display:flex;flex-direction:column;gap:10px;margin-bottom:26px}' +
    '.fa-satir{display:flex;gap:14px;align-items:center;padding:12px;border-radius:18px;background:var(--surface,#1b2833);border:0;color:inherit;text-align:left;cursor:pointer;font:inherit;width:100%}' +
    '.fa-satir img,.fa-satir .fa-bos{width:104px;height:62px;border-radius:12px;object-fit:cover;flex:none;background:var(--stage,#000)}' +
    '.fa-satir b{display:block;font-size:15px;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}' +
    '.fa-satir span{display:block;font-size:12px;color:var(--muted)}' +
    '.fa-not{font-size:13px;color:var(--muted);margin:0 0 22px}' +
    '.fa-diger{font-size:12px;letter-spacing:2px;color:var(--muted);margin:6px 0 10px;font-weight:700}' +
    'body.fa-acik #home .home-top{display:none}' +
    'body.fa-acik:not(.fa-kit) #libBody,body.fa-acik:not(.fa-kit) #home .sec-head:has(#libTabs){display:none}';
  document.head.appendChild(css);

  /* Video'ya geçiş — 2.28: Android'de HER ZAMAN yerel Stüdyo'nun ana ekranı (taslaklar; en yeni video editörü).
     Yerel uygulama yoksa Chrome kendiliğinden web Kareden Videoya'yı açar (browser_fallback_url). Bilgisayarda web Kareden Videoya.
     (2.27'de web Stüdyo'dan açılınca eski web video açılıyordu — videoda geriye gidiş.) */
  var YEREL = new URLSearchParams(location.search).get('yerel') === '1';
  var ANDROID = /Android/i.test(navigator.userAgent);
  function videoya(){
    if (YEREL || ANDROID) {
      var yedek = new URL('../video/', location.href).href;
      var u = 'intent://ana#Intent;scheme=studyo;package=app.studyo.android;S.browser_fallback_url=' + encodeURIComponent(yedek) + ';end';
      try { window.top.location.href = u; } catch(e){ location.href = u; }
      return;
    }
    var P = null; try { if (window.parent !== window) P = window.parent; } catch(e){}
    try { if (P && P.studyoVideoAc) { P.studyoVideoAc(); return; } if (P && typeof P.modulAc === 'function') { P.modulAc('video/', null, 'Kareden Videoya açılıyor…', true); return; } } catch(e){}
    location.href = '../video/';
  }
  function svg(yollar, s){ return '<svg width="' + s + '" height="' + s + '" viewBox="0 0 24 24" aria-hidden="true">' + yollar.map(function(d){ return '<path d="' + d + '"/>'; }).join('') + '</svg>'; }
  /* yerel Stüdyo'daki simgeler (AnaStudyo.kt YOL) */
  var Y = {
    foto: ['M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z', 'M7 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0', 'M4 18l5-5 4 3 3-2 4 4'],
    vid: ['M5.5 6h7.5a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H5.5a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z', 'M16 10.5l5.5-3v9l-5.5-3z'],
    klaket: ['M4 10h16v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z', 'M4 10l1.5-5.5 15 3.5-.5 2', 'M8.5 5.5l2.5 4M13.5 6.6l2.5 4'],
    ev: ['M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z'],
    klasor: ['M3.5 7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z'],
    ayar: ['M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0', 'M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z'],
    ok: ['M9 6l6 6-6 6'], geri: ['M15 18l-6-6 6-6'], arti: ['M12 5v14M5 12h14']
  };
  function kac(s){ return String(s || '').replace(/[<>&"]/g, function(c){ return {'<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;'}[c]; }); }
  function tarih(t){ try { return new Date(t).toLocaleString('tr-TR', {day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'}); } catch(e){ return ''; } }
  function neZaman(t){   // yerel Stüdyo ile aynı: Bugün HH:mm · Dün · 5 Kas
    if (!t) return ''; var d = new Date(t), b = new Date(), dun = new Date(); dun.setDate(b.getDate() - 1);
    function ayni(x, y){ return x.toDateString() === y.toDateString(); }
    if (ayni(d, b)) return 'Bugün ' + d.toLocaleTimeString('tr-TR', {hour: '2-digit', minute: '2-digit'});
    if (ayni(d, dun)) return 'Dün';
    return d.toLocaleDateString('tr-TR', {day: 'numeric', month: 'short'});
  }
  async function kayitlar(){ var r = []; try { r = (await DB.all()) || []; } catch(e){} r.sort(function(a, b){ return (b.updated || 0) - (a.updated || 0); }); return r; }
  function kitaplik(ac, btn){
    var a = (ac === undefined) ? !document.body.classList.contains('fa-kit') : ac;
    document.body.classList.toggle('fa-kit', a);
    document.querySelectorAll('.fa-tumu,.fv-tumu,.fk-tumu').forEach(function(b){ b.textContent = a ? 'Kapat' : (b.classList.contains('fv-tumu') ? 'Tümünü gör' : 'Tümü'); });
    if (a) { var l = document.querySelector('#libTabs') || document.querySelector('#libBody'); if (l) setTimeout(function(){ l.scrollIntoView({behavior: 'smooth', block: 'start'}); }, 50); }
  }

  /* ---------- sürümler (en altta, her görünümde) ---------- */
  var surumOnbellek = null;
  function metaOku(html){ var m = /name="surum" content="([^"]+)"[^>]*data-tarih="([^"]+)"/.exec(html || ''); return m ? m[1] + ' · ' + m[2] : '?'; }
  async function surumler(){
    if (surumOnbellek) return surumOnbellek;
    var m = document.querySelector('meta[name=surum]'), ko = m ? m.content + ' · ' + (m.dataset.tarih || '') : '?';
    var st = '?', kv = '?';
    try { var pm = window.parent !== window && window.parent.document.querySelector('meta[name=surum]'); if (pm) st = pm.content + ' · ' + (pm.dataset.tarih || ''); } catch(e){}
    try { if (st === '?') st = metaOku(await (await fetch('../index.html')).text()); } catch(e){}
    try { kv = metaOku(await (await fetch('../video/index.html')).text()); } catch(e){}
    surumOnbellek = 'Stüdyo (web) ' + st + '<br>Karanlık Oda ' + ko + ' · Kareden Videoya (web) ' + kv +
      '<br>Yerel Stüdyo (Android): sürümü yerel ana ekranda ve ⋯ menüsünde · bu sürüm için en az 4.56';
    return surumOnbellek;
  }
  async function surumYaz(){
    var wrap = document.querySelector('#home .home-wrap'); if (!wrap) return;
    var s = wrap.querySelector('.ko-surum'); if (!s) { s = document.createElement('p'); s.className = 'ko-surum'; }
    if (s !== wrap.lastElementChild) wrap.appendChild(s);   // her zaman en altta
    s.innerHTML = await surumler();
  }

  /* ---------- 2.29 yerel Stüdyo ana ekranı (varsayılan) ---------- */
  async function satirlar(kutu){
    var recs = await kayitlar(); kutu.innerHTML = '';
    if (!recs.length){ kutu.innerHTML = '<p class="fk-bos">Henüz fotoğraf yok. “Yeni fotoğraf” ile başla.</p>'; return; }
    recs.forEach(function(r){
      var d = document.createElement('div'); d.className = 'fk-r'; d.setAttribute('role', 'button'); d.setAttribute('aria-label', (r.name || 'Fotoğraf') + ' aç');
      d.innerHTML = (r.thumb ? '<img alt="" src="' + kac(r.thumb) + '">' : '<div class="fk-bosr"></div>') +
        '<div><b>' + kac(r.name || 'Fotoğraf') + '</b><span>' + (r.w && r.h ? r.w + '×' + r.h : 'Fotoğraf') + (r.state ? ' · düzenlendi' : '') + (r.fav ? ' · ★' : '') + '</span><small>' + tarih(r.updated) + '</small></div>' +
        '<button type="button" aria-label="' + kac(r.name || 'Fotoğraf') + ' seçenekleri">⋮</button>';
      d.onclick = function(e){ if (e.target.closest('button')) return; try { openRecord(r); } catch(_){} };
      d.querySelector('button').onclick = function(){ try { recMenu(r); } catch(_){} };
      kutu.appendChild(d);
    });
  }
  function menuKapat(){ var m = document.querySelector('.fk-menu'); if (m) m.remove(); document.removeEventListener('pointerdown', disTik, true); }
  function disTik(e){ if (!e.target.closest('.fk-menu,.fk-uc')) menuKapat(); }
  async function menuAc(ust){
    if (ust.querySelector('.fk-menu')) { menuKapat(); return; }
    var m = document.createElement('div'); m.className = 'fk-menu'; m.setAttribute('role', 'menu');
    var koyu = document.documentElement.getAttribute('data-theme') === 'dark';
    [['Kitaplık · tüm fotoğraflar', function(){ kitaplik(true); }],
     ['Video taslaklarım (Stüdyo)', videoya],
     ['⚙ Ayarlar', function(){ var k = document.querySelector('#ayarKutu'); if (k) { k.open = true; setTimeout(function(){ k.scrollIntoView({behavior: 'smooth', block: 'start'}); }, 50); } }],
     [koyu ? 'Gündüz teması' : 'Gece teması', function(){ var t = document.querySelector('#themeBtn'); if (t) t.click(); }],
     ['Ana ekran görünümü…', function(){ var k = document.querySelector('#ayarKutu'); if (k) { k.open = true; var s = k.querySelector('.fa-ayar select'); setTimeout(function(){ (s || k).scrollIntoView({behavior: 'smooth', block: 'center'}); if (s) s.focus(); }, 50); } }]
    ].forEach(function(x){ var b = document.createElement('button'); b.type = 'button'; b.setAttribute('role', 'menuitem'); b.textContent = x[0]; b.onclick = function(){ menuKapat(); x[1](); }; m.appendChild(b); });
    var sr = document.createElement('div'); sr.className = 'fk-sur'; sr.innerHTML = 'Sürümler<br>' + await surumler(); m.appendChild(sr);
    ust.appendChild(m);
    setTimeout(function(){ document.addEventListener('pointerdown', disTik, true); }, 0);
  }
  function yerelKur(wrap){
    var var_ = wrap.querySelector('.fk-blok');
    if (var_) { satirlar(var_.querySelector('.fk-liste')); return; }
    var blok = document.createElement('div'); blok.className = 'fk-blok';
    blok.innerHTML =
      '<div class="fk-ust"><div><h1>Karanlık Oda</h1><p>Fotoğraf düzenleyici · web uygulaması</p></div><button type="button" class="fk-uc" aria-label="Menü" aria-haspopup="menu">⋯</button></div>' +
      '<div class="fk-kartlar"><button type="button" class="fk-k fk-yeni"><em>＋</em><b>Yeni fotoğraf</b></button>' +
      '<button type="button" class="fk-k fk-vid">' + svg(Y.vid, 26) + '<div><b>Video</b><small>Stüdyo · taslaklarım</small></div></button></div>' +
      '<div class="fk-sb"><h2>Son çalışmalarım</h2><button type="button" class="fk-tumu">Tümü</button></div>' +
      '<div class="fk-liste"></div><div class="fk-diger">DİĞER</div>';
    var ust = blok.querySelector('.fk-ust');
    ust.querySelector('.fk-uc').onclick = function(){ menuAc(ust); };
    blok.querySelector('.fk-yeni').onclick = function(){ var p = document.querySelector('#pickGallery'); if (p) p.click(); };
    blok.querySelector('.fk-vid').onclick = videoya;
    blok.querySelector('.fk-tumu').onclick = function(){ kitaplik(); };
    var top = wrap.querySelector('.studyo-ev');
    if (top && top.parentNode === wrap) top.after(blok); else wrap.prepend(blok);
    satirlar(blok.querySelector('.fk-liste'));
  }

  /* ---------- 2.28 video düzeni (pasif seçenek) ---------- */
  function navKur(ev){
    var n = ev.querySelector('.fv-nav');
    if (gor() !== 'video') { if (n) n.remove(); return; }
    if (n) return;
    n = document.createElement('nav'); n.className = 'fv-nav'; n.setAttribute('aria-label', 'Gezinme');
    [['ev', 'Ana sayfa', function(){ kitaplik(false); ev.scrollTo({top: 0, behavior: 'smooth'}); }],
     ['klasor', 'Kitaplık', function(){ kitaplik(true); }],
     ['vid', 'Video', videoya],
     ['ayar', 'Ayarlar', function(){ var k = document.querySelector('#ayarKutu'); if (k) { k.open = true; setTimeout(function(){ k.scrollIntoView({behavior: 'smooth', block: 'start'}); }, 50); } }]
    ].forEach(function(x, i){
      var b = document.createElement('button'); b.type = 'button'; if (!i) b.className = 'on';
      b.innerHTML = svg(Y[x[0]], 27) + x[1]; b.onclick = x[2]; n.appendChild(b);
    });
    ev.appendChild(n);
  }
  async function sira(kutu){
    var recs = await kayitlar(); kutu.innerHTML = '';
    if (!recs.length){ kutu.outerHTML = '<div class="fv-sira"><div class="fv-bos" style="flex:1">Henüz çalışma yok. Yukarıdan fotoğraf seçerek başla.</div></div>'; return; }
    recs.slice(0, 12).forEach(function(r){
      var b = document.createElement('button'); b.type = 'button'; b.className = 'fv-pk'; b.setAttribute('aria-label', (r.name || 'Fotoğraf') + ' aç');
      b.innerHTML = '<i' + (r.thumb ? ' style="background-image:url(&quot;' + kac(r.thumb) + '&quot;)"' : '') + '></i><b>' + kac(r.name || 'Fotoğraf') + '</b><small>' + (r.fav ? '★ · ' : '') + neZaman(r.updated) + '</small>';
      b.onclick = function(){ try { openRecord(r); } catch(e){} };
      kutu.appendChild(b);
    });
  }
  function videoKur(wrap){
    var var_ = wrap.querySelector('.fv-blok');
    if (var_) { var s = var_.querySelector('.fv-sira'); if (s) sira(s); return; }
    var blok = document.createElement('div'); blok.className = 'fv-blok';
    blok.innerHTML =
      '<div class="fv-ust"><img class="fv-logo" alt="" src="icon-192.png"><div class="fv-ad">KARANLIK ODA</div></div>' +
      '<div class="fv-bas"><h1>Fotoğrafını düzenle.</h1><p>Fotoğraf ve video, tek bir yerde.</p></div>' +
      '<div class="fv-kartlar">' +
        '<button type="button" class="fv-kk fv-yeni" style="--t:var(--fv-mavi)"><div class="fv-ik">' + svg(Y.foto, 34) + '</div><b>Fotoğraf</b><span>Galeriden seç · filtre, ışık, HSL, kırp</span></button>' +
        '<button type="button" class="fv-kk fv-vk" style="--t:var(--fv-vurgu)"><div class="fv-ik">' + svg(Y.vid, 34) + '</div><b>Video</b><span>Taslaklarına dön · kes, müzik, metin, geçiş</span></button>' +
      '</div>' +
      '<button type="button" class="fv-gk"><div class="fv-ik">' + svg(Y.klaket, 30) + '</div><div><b>Video taslaklarım</b><span>Stüdyo video editörü · son projeler</span></div>' + svg(Y.ok, 26) + '</button>' +
      '<div class="fv-sb"><h2>Son çalışmalar</h2><button type="button" class="fv-tumu">Tümünü gör</button></div>' +
      '<div class="fv-sira"></div><div class="fv-diger">DİĞER</div>';
    blok.querySelector('.fv-yeni').onclick = function(){ var p = document.querySelector('#pickGallery'); if (p) p.click(); };
    blok.querySelector('.fv-vk').onclick = videoya;
    blok.querySelector('.fv-gk').onclick = videoya;
    blok.querySelector('.fv-tumu').onclick = function(){ kitaplik(); };
    var top = wrap.querySelector('.studyo-ev');
    if (top && top.parentNode === wrap) top.after(blok); else wrap.prepend(blok);
    var tb = document.querySelector('#themeBtn'); if (tb) blok.querySelector('.fv-ust').appendChild(tb);
    sira(blok.querySelector('.fv-sira'));
  }

  /* ---------- 2.26 kart düzeni (seçenek, aynen) ---------- */
  async function liste(kutu){
    var recs = await kayitlar(); kutu.innerHTML = '';
    if (!recs.length){ var n = document.createElement('p'); n.className = 'fa-not'; n.textContent = 'Henüz düzenlenmiş fotoğraf yok. “Yeni fotoğraf” ile başla.'; kutu.appendChild(n); return; }
    recs.slice(0, 6).forEach(function(r){
      var b = document.createElement('button'); b.type = 'button'; b.className = 'fa-satir'; b.setAttribute('aria-label', (r.name || 'Fotoğraf') + ' aç');
      var g = r.thumb ? '<img alt="" src="' + kac(r.thumb) + '">' : '<div class="fa-bos"></div>';
      b.innerHTML = g + '<div style="min-width:0;flex:1"><b>' + kac(r.name || 'Fotoğraf') + '</b><span>' + (r.w && r.h ? r.w + '×' + r.h + ' · ' : '') + (r.fav ? '★ · ' : '') + '</span><span>' + tarih(r.updated) + '</span></div>';
      b.onclick = function(){ try { openRecord(r); } catch(e){} };
      kutu.appendChild(b);
    });
  }
  function kartKur(wrap){
    var var_ = wrap.querySelector('.fa-blok');
    if (var_) { liste(var_.querySelector('.fa-liste')); return; }
    var blok = document.createElement('div'); blok.className = 'fa-blok';
    var surum = (document.querySelector('#home .sub') || {}).textContent || '';
    blok.innerHTML =
      '<div class="fa-ust"><div><h1>Karanlık Oda</h1><p>Fotoğraf düzenleyici · ' + (surum.match(/sürüm [0-9.]+/) || [''])[0] + '</p></div>' +
      '<div class="fa-sag" style="display:flex;gap:8px;align-items:center"><button type="button" class="fa-vid">' + svg(Y.vid, 18) + 'Video ›</button></div></div>' +
      '<div class="fa-kartlar"><button type="button" class="fa-kart fa-yeni">' + svg(Y.arti, 34) + '<b>Yeni fotoğraf</b></button>' +
      '<button type="button" class="fa-kart fa-video">' + svg(Y.vid, 22) + '<div><b>Video</b><small>Stüdyo · Kareden Videoya</small></div></button></div>' +
      '<div class="fa-bas"><h2>Son çalışmalarım</h2><button type="button" class="fa-tumu">Tümü</button></div>' +
      '<div class="fa-liste"></div><div class="fa-diger">DİĞER</div>';
    blok.querySelector('.fa-vid').onclick = videoya;
    blok.querySelector('.fa-video').onclick = videoya;
    blok.querySelector('.fa-yeni').onclick = function(){ var p = document.querySelector('#pickGallery'); if (p) p.click(); };
    blok.querySelector('.fa-tumu').onclick = function(){ kitaplik(); };
    if (YEREL) blok.querySelector('.fa-vid').innerHTML = svg(Y.geri, 18) + 'Video';
    var top = wrap.querySelector('.studyo-ev');
    if (top && top.parentNode === wrap) top.after(blok); else wrap.prepend(blok);
    var tb = document.querySelector('#themeBtn'); if (tb) blok.querySelector('.fa-sag').appendChild(tb);
    liste(blok.querySelector('.fa-liste'));
  }

  function kur(){
    var ev = document.querySelector('#home'), wrap = document.querySelector('#home .home-wrap'); if (!wrap) return false;
    var g = gor(), tb = document.querySelector('#themeBtn'), ht = wrap.querySelector('.home-top');
    var fv = wrap.querySelector('.fv-blok'), fa = wrap.querySelector('.fa-blok'), fk = wrap.querySelector('.fk-blok');
    if (g !== 'video' && fv) { fv.remove(); fv = null; }
    if (g !== '226' && fa) { fa.remove(); fa = null; }
    if (g !== 'yerel' && fk) { fk.remove(); menuKapat(); }
    document.body.classList.toggle('fk-acik', g === 'yerel');
    document.body.classList.toggle('fv-acik', g === 'video');
    document.body.classList.toggle('fa-acik', g === '226');
    document.body.classList.toggle('fa-dog', YEREL && g !== 'eski');
    if (g === 'eski' || g === 'yerel') { if (g === 'eski') document.body.classList.remove('fa-kit'); if (tb && ht && tb.parentNode !== ht) ht.appendChild(tb); }
    if (g === 'eski') {}
    else if (g === 'yerel') yerelKur(wrap);
    else if (g === 'video') videoKur(wrap);
    else kartKur(wrap);
    navKur(ev);
    surumYaz();
    return true;
  }
  /* Ayarlar kutusu: ana ekran görünümü seçimi (önceki görünümler korunur) */
  function ayar(){
    var k = document.querySelector('#ayarKutu'); if (!k || k.querySelector('.fa-ayar')) return;
    var l = document.createElement('label'); l.className = 'fa-ayar'; l.style.cssText = 'display:flex;align-items:center;gap:8px;padding:10px 4px;font-size:14px;flex-wrap:wrap';
    l.innerHTML = 'Ana ekran görünümü <select style="font:inherit;padding:6px 8px;border-radius:10px"><option value="yerel">Stüdyo düzeni (yerel video ana ekranı gibi)</option><option value="video">Mavi-turuncu düzen (2.28)</option><option value="226">2.26 kart düzeni</option><option value="eski">Önceki ana ekran</option></select>';
    var s = l.querySelector('select'); s.value = gor();
    s.onchange = function(){ try { localStorage.setItem(GOR, s.value); localStorage.removeItem(ESKI); } catch(e){} kur(); };
    k.appendChild(l);
    /* 2.29: düzenleyicide alt panel fotoğrafın üstüne açılsın (fotoğraf küçülmez) — tutamaktaki düğmeyle aynı */
    var u = document.createElement('label'); u.className = 'ko-ust-ayar'; u.style.cssText = 'display:flex;align-items:center;gap:8px;padding:10px 4px;font-size:14px';
    u.innerHTML = '<input type="checkbox"> Alt panel fotoğrafın üstüne açılsın (fotoğraf küçülmez, panel ekranın tamamına kadar çekilir)';
    var uc = u.querySelector('input'); uc.checked = document.documentElement.classList.contains('ko-ust');
    uc.onchange = function(){ if (window.koPanUst) window.koPanUst(uc.checked); };
    k.appendChild(u);
  }
  function dene(){ if (typeof DB === 'undefined' || !document.querySelector('#home .home-wrap')) return setTimeout(dene, 300); kur(); ayar(); }
  dene();
  /* editörden ana ekrana dönünce liste yenilenir */
  try { var oRL = window.renderLibrary; if (typeof oRL === 'function') window.renderLibrary = function(){ var r = oRL.apply(this, arguments); try { kur(); } catch(e){} return r; }; } catch(e){}
  var ev = document.querySelector('#home');
  if (ev && window.MutationObserver) new MutationObserver(function(){ if (!ev.hidden) kur(); }).observe(ev, {attributes: true, attributeFilter: ['hidden']});
})();
