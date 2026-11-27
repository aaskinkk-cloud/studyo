/* ===== 2.18–2.19 · FOTOĞRAF EDİTÖRÜ YENİ DÜZEN (video editörünün 2.16 düzeni örnek alındı) — yalnız görünüm ve gezinme =====
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
    {id:'metin', ad:'Metin', ic:I.metin, h:[['text','Yazı · İmza'],['haber','Haber'],['overlay','Katman']]},
    {id:'kay', ad:'Kaydet', ic:I.kaydet, h:[['history','Geçmiş'],['@kaydet','Kitaplığa kaydet'],['export','Dışa aktar']]}
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
    /* üst çubuk: Dışa Aktar turuncu */
    'html.st-fy #saveBtn{background:var(--st-or)!important;color:var(--st-or-ink)!important;border-color:var(--st-or)!important}',
    /* panel: sakin kart, ferah boşluk */
    'html.st-fy #editor .panel{border-radius:22px 22px 0 0;border-top:1px solid color-mix(in srgb,var(--text) 9%,transparent)}',
    'html.st-fy #panelIn .bgrp,html.st-fy #panelIn .yz-grp{border-radius:14px}',
    'html.st-fy .st-bsek button.on svg{color:var(--st-or)!important}',
    'html.st-fy .st-bsek button.on::after{background:var(--st-or)!important}',
    'html.st-fy .st-satir button.on{background:var(--st-or)!important;color:var(--st-or-ink)!important}',
    'html.st-fy #panelIn .btn.pri,html.st-fy #panelIn .btn.primary{background:var(--st-or)!important;border-color:var(--st-or)!important;color:var(--st-or-ink)!important}',
    'html.st-fy #panelIn .chip.on,html.st-fy #panelIn .chips button.on{box-shadow:0 0 0 1.5px var(--st-or) inset}',
    /* 2.19 · açıklamalar tek satır, dokununca açılır/kapanır (ekranı kaplamaz) */
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
        var j = son[x.id] || 0, h = x.h[j] && gorunur(x.h[j][0]) && x.h[j][0] !== '@kaydet' && x.h[j][0] !== '#stKesTab' ? x.h[j] : x.h.filter(function(y){ return gorunur(y[0]) && y[0].charAt(0) !== '@' && y[0].charAt(0) !== '#'; })[0];
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
