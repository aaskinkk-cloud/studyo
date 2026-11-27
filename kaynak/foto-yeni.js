/* ===== 2.18–2.22 · FOTOĞRAF EDİTÖRÜ YENİ DÜZEN (video editörünün 2.16 düzeni örnek alındı) — yalnız görünüm ve gezinme =====
   Alt çubuk 5 bölüm: Düzenle · Renk · Rötuş · Metin · Kaydet. Seçili bölümün araçları hemen üstte hap düğmeler (hep görünür, gizli menü yok).
   Haplar Karanlık Oda'nın kendi sekme düğmelerine basar (setTab); sekmeler gizli ama yerinde. Panelin içi: araç başlığı + bölüm sekmeleri (3. katman).
   Açık bölümün simgesine yine dokununca panel kapanır, fotoğraf tam boy görünür. Her bölüm son açılan aracı hatırlar.
   Önceki düzen silinmedi: ⚙ Ayarlar › Editör düzeni (localStorage 'studyo.fYeni'). */
(function(){
  if (!window.STUDYO) return;
  var YENI = true; try { YENI = localStorage.getItem('studyo.fYeni') !== '0'; } catch(e){}
  var B = document.body || document.documentElement;
  function ic(d){ return '<svg viewBox="0 0 24 24">' + d + '</svg>'; }
  var I = {
    duz: ic('<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>'),
    renk: ic('<path d="M12 3a9 9 0 100 18c1 0 1.6-.8 1.6-1.6 0-.9-.8-1.3-.8-2.2 0-.9.7-1.6 1.6-1.6H16a5 5 0 005-5c0-4.1-4-7.6-9-7.6z"/><circle cx="7.5" cy="11.5" r="1"/><circle cx="10.5" cy="7.5" r="1"/><circle cx="15" cy="7.5" r="1"/>'),
    rotus: ic('<path d="M12 3l1.8 4.6L18.5 9l-4.7 1.6L12 15l-1.8-4.4L5.5 9l4.7-1.4z"/><path d="M18 15l.9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9z"/>'),
    metin: ic('<path d="M5 6V4h14v2M12 4v16M9 20h6"/>'),
    kaydet: ic('<path d="M12 3v12M7 10l5 5 5-5"/><path d="M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2"/>')
  };
  /* araç: [Karanlık Oda sekmesi (data-t) ya da #id, ad] */
  var G = [
    {id:'duz', ad:'Düzenle', ic:I.duz, h:[['auto','Otomatik'],['crop','Kırp'],['light','Işık'],['detail','Detay'],['hdr','HDR']]},
    {id:'renk', ad:'Renk', ic:I.renk, h:[['filters','Filtreler'],['color','Renk'],['hsl','HSL'],['curves','Eğriler']]},
    {id:'rotus', ad:'Rötuş', ic:I.rotus, h:[['selfi','Selfi'],['retouch','Rötuş'],['brush','Fırça'],['#stKesTab','Kes'],['fx','Efektler']]},
    {id:'metin', ad:'Metin', ic:I.metin, h:[['text','Yazı · İmza'],['overlay','Katman']]},
    /* Haber (Kabataş Haber) ayrı tam ekran açar: Metin'de değil, paylaşım yerinde; bölüm simgesi onu hiç kendiliğinden açmaz */
    {id:'kay', ad:'Kaydet', ic:I.kaydet, h:[['history','Geçmiş'],['@kaydet','Kitaplığa kaydet'],['export','Dışa aktar'],['haber','Kabataş Haber']]}
  ];
  var son = {}; try { son = JSON.parse(localStorage.getItem('studyo.fGrup') || '{}') || {}; } catch(e){}
  var secG = 'duz';

  var css = document.createElement('style'); css.id = 'st-fyeni-css';
  css.textContent = [
    'html.st-fy{--st-tq:#2ec5d3;--st-or:#f5a524;--st-or-ink:#1b1204}',
    'html.st-fy #editor #tabs,html.st-fy #grpBar{display:none!important}',
    '#stFGrup{display:none}',
    'html.st-fy #stFGrup{display:block;width:100%;background:var(--bg);padding-bottom:calc(2px + env(safe-area-inset-bottom,0px))}',
    '#stFGrup .st-hap{display:flex;gap:7px;padding:9px 12px 5px;overflow-x:auto;scrollbar-width:none;-webkit-overflow-scrolling:touch}',
    '#stFGrup .st-hap::-webkit-scrollbar{display:none}',
    '#stFGrup .st-hap button{flex:1 0 auto;height:34px;padding:0 15px;border-radius:17px;border:1.5px solid color-mix(in srgb,var(--text) 16%,transparent);background:none;color:var(--text);font:600 13px/1 var(--f-ui);white-space:nowrap;position:relative;transition:background .15s,border-color .15s,color .15s}',
    '#stFGrup .st-hap button.on{background:var(--st-or);border-color:var(--st-or);color:var(--st-or-ink)}',
    '#stFGrup .st-hap button i{position:absolute;top:5px;right:7px;width:5px;height:5px;border-radius:50%;background:var(--st-or)}',
    '#stFGrup .st-hap button.on i{background:var(--st-or-ink)}',
    '#stFGrup .st-bol{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));padding:2px 4px 0}',
    '#stFGrup .st-bol button{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;min-height:56px;padding:4px 0;border:0;background:none;color:var(--muted);font:500 11.5px/1.1 var(--f-ui);border-radius:14px;position:relative}',
    '#stFGrup .st-bol button svg{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}',
    '#stFGrup .st-bol button.on{color:var(--st-or);font-weight:700}',
    '#stFGrup .st-bol button i{position:absolute;top:8px;left:calc(50% + 10px);width:6px;height:6px;border-radius:50%;background:var(--st-or)}',
    '#stFGrup .st-bol button:active{background:color-mix(in srgb,var(--text) 8%,transparent)}',
    /* 2.21 · üst çubuk: küçük, tek renk, tek tip; sığmazsa sağa-sola kayar (taşma yok) */
    'html.st-fy #editor .topbar{flex-wrap:nowrap!important;overflow-x:auto;overflow-y:hidden;scrollbar-width:none;gap:0!important;padding:3px 6px!important;min-height:0!important;align-items:center}',
    'html.st-fy #editor .topbar::-webkit-scrollbar{display:none}',
    'html.st-fy #editor .topbar>*{flex:none!important}',
    'html.st-fy #editor .topbar .icon-btn{width:34px!important;height:34px!important;min-width:34px!important;padding:0!important;border:0!important;background:none!important;color:var(--text)!important;opacity:.82;border-radius:10px}',
    'html.st-fy #editor .topbar .icon-btn:disabled{opacity:.3}',
    'html.st-fy #editor .topbar .icon-btn:active{background:color-mix(in srgb,var(--text) 9%,transparent)!important}',
    'html.st-fy #editor .topbar .icon-btn svg,html.st-fy #editor .topbar .icon-btn .icon{width:19px!important;height:19px!important}',
    'html.st-fy #editor .topbar .info{flex:0 1 auto!important;min-width:0;max-width:84px;padding:0 4px}',
    'html.st-fy #editor .topbar .info b{font-size:11.5px!important;font-weight:600!important;opacity:.7;display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
    'html.st-fy #editor .topbar .info small{display:none!important}',
    'html.st-fy #editor .topbar .basit-btn,html.st-fy #saveBtn{height:28px!important;padding:0 11px!important;border-radius:14px!important;border:1px solid color-mix(in srgb,var(--text) 22%,transparent)!important;background:none!important;color:var(--text)!important;font:600 12px/26px var(--f-ui)!important;margin-left:4px!important;box-shadow:none!important}',
    'html.st-fy #saveBtn:not(.tel){font-size:0!important}',
    'html.st-fy #saveBtn:not(.tel)::after{font-size:12px!important;line-height:26px}',
    'html.st-fy #editor .topbar .basit-btn.on{background:color-mix(in srgb,var(--text) 12%,transparent)!important}',
    /* panel kapalıyken tutamak görünür kalır: yukarı sürükle ya da dokun → panel açılır */
    '@media (max-width:899px){html.st-fy body.ed-kapali #editor .panel{height:26px!important;overflow:hidden!important;border-top:1px solid color-mix(in srgb,var(--text) 9%,transparent)!important}',
    ' html.st-fy body.ed-kapali #editor .panel>:not(.ed-tut){visibility:hidden}',
    ' html.st-fy body.ed-kapali #editor .ed-tut{display:flex!important}}',
    /* panel: sakin kart, ferah boşluk */
    'html.st-fy #editor .panel{border-radius:22px 22px 0 0;border-top:1px solid color-mix(in srgb,var(--text) 9%,transparent)}',
    'html.st-fy #panelIn .bgrp,html.st-fy #panelIn .yz-grp{border-radius:14px}',
    'html.st-fy .st-bsek button.on svg{color:var(--st-or)!important}',
    'html.st-fy .st-bsek button.on::after{background:var(--st-or)!important}',
    'html.st-fy .st-satir button.on{background:var(--st-or)!important;color:var(--st-or-ink)!important}',
    'html.st-fy #panelIn .btn.pri,html.st-fy #panelIn .btn.primary{background:var(--st-or)!important;border-color:var(--st-or)!important;color:var(--st-or-ink)!important}',
    'html.st-fy #panelIn .chip.on,html.st-fy #panelIn .chips button.on{box-shadow:0 0 0 1.5px var(--st-or) inset}',
    /* 2.19 · açıklamalar tek satır, dokununca açılır/kapanır (ekranı kaplamaz) */
    'html.st-fy.st-notsiz #panelIn .note{display:none!important}',
    '#stNot{display:none}',
    'html.st-fy #stNot{display:grid;place-items:center;width:34px;height:34px;border:0;border-radius:50%;background:none;color:var(--muted);font:700 15px/1 var(--f-ui);padding:0;flex:none}',
    'html.st-fy:not(.st-notsiz) #stNot{color:var(--st-or)}',
    /* başlık satırı ince: daha geniş düzenleme alanı */
    'html.st-fy .st-pbas{padding-top:0!important;padding-bottom:2px!important}',
    'html.st-fy .st-pbas>button{width:36px!important;height:36px!important}',
    /* panel kaydırma: kenarda çubuk görünmez, parmakla yukarı-aşağı */
    'html.st-fy #editor .panel{overflow-y:auto!important;-webkit-overflow-scrolling:touch;overscroll-behavior:contain;scrollbar-width:none}',
    'html.st-fy #editor .panel::-webkit-scrollbar{display:none}',
    'html.st-fy #panelIn .note{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:1;overflow:hidden;cursor:pointer;position:relative;padding-right:22px!important;opacity:.8}',
    'html.st-fy #panelIn .note::after{content:"⌄";position:absolute;right:4px;top:-2px;font-size:15px;opacity:.7}',
    'html.st-fy #panelIn .note.ac{-webkit-line-clamp:unset;display:block}',
    'html.st-fy #panelIn .note.ac::after{content:"⌃";top:auto;bottom:-4px}',
    /* sıfırla / tümü düğmeleri ayrı satır kaplamaz: sağa yaslanır, sonraki öğe yanına sığar */
    'html.st-fy #panelIn>.panel-title.st-pt-dug{float:right;width:auto!important;max-width:60%;margin:0 0 4px 8px!important;min-height:0;position:relative;z-index:2}',
    'html.st-fy #panelIn>.panel-title.st-pt-dug .btn{padding:5px 10px!important;font-size:12.5px!important}',
    'html.st-fy #panelIn>.panel-title.st-pt-dug~*{clear:none}',
    /* HSL renk noktaları tek sıra, küçük: renk seçimi ve kaydırıcılar aynı sayfaya sığar */
    'html.st-fy #panelIn .hsl-dots{flex-wrap:nowrap!important;overflow-x:auto;gap:9px!important;padding:4px 3px!important;scrollbar-width:none}',
    'html.st-fy #panelIn .hsl-dots button{width:32px!important;height:32px!important;min-width:32px!important;flex:none!important}',
    'html.st-fy #panelIn .yz-ara{margin:6px 2px 0}',
    'html.st-fy #panelIn .hsl-dots+.yz-ara{display:none}',   /* seçili renk noktası zaten gösteriyor */
    /* 2.22 · filtreler ızgara (dikey): alt alta kayar, Lightroom gibi; şerit görünümü seçenek */
    'html.st-fy.st-izgara #panelIn .presets{display:grid!important;grid-template-columns:repeat(4,minmax(0,1fr))!important;flex-wrap:wrap!important;overflow:visible!important;gap:10px 8px!important}',
    'html.st-fy.st-izgara #panelIn .presets .pz{width:auto!important;min-width:0}',
    'html.st-fy.st-izgara #panelIn .presets .pz canvas{width:100%!important;height:auto!important;aspect-ratio:1}',
    'html.st-fy #panelIn .presets .pz{font-size:10.5px;line-height:1.2;text-align:center;word-break:break-word}',
    '#stIzgara{display:none}',
    'html.st-fy.st-filtre #stIzgara{display:grid;place-items:center;width:34px;height:34px;border:0;border-radius:50%;background:none;color:var(--muted);padding:0;flex:none}',
    '#stIzgara svg{width:19px;height:19px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}',
    '.duzen-st{margin:10px 0 4px}.duzen-st b{display:block;font:700 13px var(--f-ui);margin-bottom:6px}.duzen-st small{display:block;opacity:.6;margin-top:6px}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);

  /* ---------- ⚙ Ayarlar › Editör düzeni (her iki düzende de görünür) ---------- */
  (function ayar(n){
    var k = document.getElementById('ayarKutu'); if (!k){ if (n < 60) setTimeout(function(){ ayar(n + 1); }, 250); return; }
    if (document.getElementById('stDuzen')) return;
    var d = document.createElement('div'); d.className = 'duzen-st'; d.id = 'stDuzen';
    d.innerHTML = '<b>Editör düzeni</b><div class="seg"><button type="button" data-d="1">Bölümlü (yeni)</button><button type="button" data-d="0">Tek sıra (önceki)</button></div><small>Değiştirince sayfa yeniden açılır; düzenlemeler kaybolmaz.</small>';
    d.querySelectorAll('button').forEach(function(b){ b.classList.toggle('on', (b.dataset.d === '1') === YENI);
      b.onclick = function(){ if ((b.dataset.d === '1') === YENI) return; try { localStorage.setItem('studyo.fYeni', b.dataset.d); } catch(e){} location.reload(); }; });
    k.appendChild(d);
  })(0);
  if (!YENI) return;
  document.documentElement.classList.add('st-fy');

  /* açıklamalar varsayılan gizli; panel başlığındaki ⓘ hepsini gösterir/gizler (tercih saklanır) */
  var notsiz = true; try { notsiz = localStorage.getItem('studyo.fNot') !== '1'; } catch(e){}
  document.documentElement.classList.toggle('st-notsiz', notsiz);
  (function ibtn(n){ var h = document.querySelector('.st-pbas'); if (!h){ if (n < 80) setTimeout(function(){ ibtn(n + 1); }, 250); return; }
    if (document.getElementById('stNot')) return;
    var b = document.createElement('button'); b.type = 'button'; b.id = 'stNot'; b.textContent = 'ⓘ'; b.title = 'Açıklamaları göster / gizle'; b.setAttribute('aria-label', 'Açıklamaları göster / gizle');
    b.onclick = function(){ notsiz = !notsiz; document.documentElement.classList.toggle('st-notsiz', notsiz); try { localStorage.setItem('studyo.fNot', notsiz ? '0' : '1'); } catch(e){} };
    var ok = h.querySelector(':scope > button'); h.insertBefore(b, ok || null); })(0);
  /* sayfalara bölme yok: bütün ayarlar alt alta, panel parmakla kayar (panel küçülünce ayar "kaybolmaz").
     Kök neden: sayfalama, panel küçülünce ayarları gizli sayfalara taşıyordu; sayfa düğmeleri fark edilmeyince ayar kayboluyor sanılıyordu.
     Kaydırıcılar (3+) InShot gibi ad sırası + tek kaydırıcı olarak kalır. */
  (function sayf(n){ var o = window.stSayfala; if (typeof o !== 'function'){ if (n < 60) setTimeout(function(){ sayf(n + 1); }, 250); return; }
    window.stSayfala = function(a){ if (a) a.maxH = 1e6; return o.apply(this, arguments); }; })(0);
  /* filtre görünümü: ızgara (varsayılan) / şerit — panel başlığında, yalnız Filtreler'de */
  var izgara = true; try { izgara = localStorage.getItem('studyo.fIzgara') !== '0'; } catch(e){}
  document.documentElement.classList.toggle('st-izgara', izgara);
  var IZ = ic('<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>'),
      SE = ic('<rect x="3" y="7" width="5" height="10" rx="1.5"/><rect x="9.5" y="7" width="5" height="10" rx="1.5"/><rect x="16" y="7" width="5" height="10" rx="1.5"/>');
  (function zbtn(n){ var h = document.querySelector('.st-pbas'); if (!h){ if (n < 80) setTimeout(function(){ zbtn(n + 1); }, 250); return; }
    if (document.getElementById('stIzgara')) return;
    var b = document.createElement('button'); b.type = 'button'; b.id = 'stIzgara';
    var guncel = function(){ b.innerHTML = izgara ? SE : IZ; b.title = izgara ? 'Şerit görünümü' : 'Izgara görünümü'; b.setAttribute('aria-label', b.title); };
    b.onclick = function(){ izgara = !izgara; document.documentElement.classList.toggle('st-izgara', izgara); try { localStorage.setItem('studyo.fIzgara', izgara ? '1' : '0'); } catch(e){} guncel(); };
    guncel(); var ok = h.querySelector(':scope > button:last-child'); h.insertBefore(b, ok || null); })(0);
  /* seçim yapınca panel yeniden çizilir: kaydırma yerleri (panel aşağı-yukarı, şeritler sağa-sola) korunur, başa dönmez.
     Kök neden: renderPanel() paneli sıfırdan kurup en üste / şeritleri en sola alıyordu. */
  (function rp(n){ var o = window.renderPanel; if (typeof o !== 'function'){ if (n < 60) setTimeout(function(){ rp(n + 1); }, 250); return; }
    var SEC = 'presets,chips,st-satir,st-bsek,hsl-dots,swatches,yz-sab,im-sab'.split(',');
    window.renderPanel = function(){
      var pan = document.getElementById('panel'), pin = document.getElementById('panelIn'), tab = (typeof ED !== 'undefined') ? ED.tab : '', kayit = null;
      if (pan && pin && pin._stTab === tab){ kayit = {y: pan.scrollTop, x: {}};
        SEC.forEach(function(c){ [].forEach.call(document.querySelectorAll('#panel .' + c), function(e, i){ if (e.scrollLeft) kayit.x[c + i] = e.scrollLeft; }); }); }
      var r = o.apply(this, arguments);
      if (pin) pin._stTab = tab;
      if (kayit){ var geri = function(){ if (pan.scrollTop !== kayit.y) pan.scrollTop = kayit.y;
          SEC.forEach(function(c){ [].forEach.call(document.querySelectorAll('#panel .' + c), function(e, i){ var v = kayit.x[c + i]; if (v && e.scrollLeft !== v) e.scrollLeft = v; }); }); };
        geri(); requestAnimationFrame(geri); setTimeout(geri, 180); }
      return r; };
  })(0);
  /* açıklamaya dokun: aç / kapat */
  document.addEventListener('click', function(e){ var n = e.target.closest && e.target.closest('#panelIn .note'); if (n && !e.target.closest('a,button,input,select,label')) n.classList.toggle('ac'); });
  /* tek bölümlü panel (ör. HSL › Kırmızı): başlık akordeon olmaz, ayarları renk seçimiyle aynı sayfada kalır.
     Kök neden: tek ara başlık bolumle()'de akordeona dönüşüyor, sayfalama başlığı bir sayfada, kaydırıcılarını öbüründe bırakıyordu. */
  (function sar(n){ var o = window.bolumle; if (typeof o !== 'function'){ if (n < 60) setTimeout(function(){ sar(n + 1); }, 250); return; }
    window.bolumle = function(P_){
      try { var sh = [].filter.call(P_.children, function(c){ return c.classList && c.classList.contains('subhead'); });
        if (sh.length === 1){ sh[0].className = 'yz-ara'; } } catch(e){}
      return o.apply(this, arguments); };
  })(0);

  function sekme(t){ return t.charAt(0) === '#' ? document.getElementById(t.slice(1)) : document.querySelector('#tabs .tab[data-t="' + t + '"]'); }
  function gorunur(t){ if (t === '@kaydet') return true; var e = sekme(t); return !!e && !e.hidden && e.style.display !== 'none'; }
  function aktifT(){ var a = document.querySelector('#tabs .tab.on'); return a ? (a.dataset.t || (a.id ? '#' + a.id : '')) : ''; }
  function kapali(){ return document.body.classList.contains('ed-kapali'); }
  function grupBul(t){ for (var i = 0; i < G.length; i++) for (var j = 0; j < G[i].h.length; j++) if (G[i].h[j][0] === t) return G[i]; return null; }

  async function kitapligaKaydet(){
    try { if (typeof ED === 'undefined' || !ED.rec) return;
      ED.rec.state = ED.state; ED.rec.updated = Date.now(); try { ED.rec.thumb = thumbFromGL(); } catch(e){}
      await DB.put(ED.rec); try { toast('Kitaplığa kaydedildi'); } catch(e){}
    } catch(e){ try { toast('Kaydedilemedi: ' + (e && e.message || e)); } catch(_){} }
  }
  function ac(t){
    if (t === '@kaydet'){ kitapligaKaydet(); return; }
    var e = sekme(t); if (!e) return;
    if (e.classList.contains('on')){ if (kapali()) e.click(); }   /* açık araca dokun: panel kapalıysa açılır */
    else e.click();
  }

  var kok = document.createElement('div'); kok.id = 'stFGrup';
  kok.innerHTML = '<div class="st-hap" role="tablist"></div><div class="st-bol"></div>';
  var hapK = kok.firstChild, bolK = kok.lastChild;
  function ciz(){
    var t = aktifT(), g = grupBul(t); if (g) secG = g.id;
    document.documentElement.classList.toggle('st-filtre', t === 'filters');
    var G0 = G.filter(function(x){ return x.id === secG; })[0] || G[0];
    hapK.innerHTML = '';
    G0.h.forEach(function(h, j){ if (!gorunur(h[0])) return;
      var b = document.createElement('button'); b.type = 'button'; b.textContent = h[1]; b.setAttribute('role', 'tab');
      var on = h[0] === t; if (on) b.classList.add('on'); b.setAttribute('aria-selected', on);
      var e = sekme(h[0]); if (e && e.querySelector && e.querySelector('.dot')) b.appendChild(document.createElement('i'));
      b.onclick = function(){ son[G0.id] = j; try { localStorage.setItem('studyo.fGrup', JSON.stringify(son)); } catch(_){} ac(h[0]); };
      hapK.appendChild(b); });
    bolK.innerHTML = '';
    G.forEach(function(x){
      var b = document.createElement('button'); b.type = 'button'; b.innerHTML = x.ic + '<span>' + x.ad + '</span>';
      if (x.id === secG) b.classList.add('on');
      if (x.h.some(function(h){ var e = sekme(h[0]); return e && e.querySelector && e.querySelector('.dot'); })) b.appendChild(document.createElement('i'));
      b.onclick = function(){
        if (x.id === secG){ var a = document.querySelector('#tabs .tab.on'); if (a && !kapali()) { a.click(); return; } }
        secG = x.id;
        var j = son[x.id] || 0, h = x.h[j] && gorunur(x.h[j][0]) && x.h[j][0] !== '@kaydet' && x.h[j][0] !== '#stKesTab' && x.h[j][0] !== 'haber' ? x.h[j] : x.h.filter(function(y){ return gorunur(y[0]) && y[0].charAt(0) !== '@' && y[0].charAt(0) !== '#' && y[0] !== 'haber'; })[0];
        if (h) ac(h[0]); else ciz();
      };
      bolK.appendChild(b); });
  }
  (function kur(n){
    var tabs = document.getElementById('tabs'); if (!tabs || !tabs.querySelector('.tab')){ if (n < 80) setTimeout(function(){ kur(n + 1); }, 250); return; }
    var w = document.getElementById('tabsWrap') || tabs; (w === tabs ? tabs.parentNode : w).insertBefore(kok, w === tabs ? tabs.nextSibling : null);
    var z = 0; new MutationObserver(function(){ clearTimeout(z); z = setTimeout(ciz, 30); }).observe(tabs, {subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden', 'style']});
    new MutationObserver(function(){ clearTimeout(z); z = setTimeout(ciz, 30); }).observe(document.body, {attributes: true, attributeFilter: ['class']});
    ciz();
  })(0);
})();
