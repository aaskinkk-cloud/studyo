/* ===== STÜDYO · VİDEO EDİTÖRÜ: BÖLÜMLER ÜSTTE, ALTTA YALNIZ SEÇİLEN (yalnız görünüm) =====
   Kareden Videoya'nın InShot görünümünde 1. sıra (simgeli bölümler) ve 2. sıra (ara başlıklar) zaten var.
   Bu katman: 2. sıraya simge ekler; seçili bölüm ekrana sığmıyorsa sayfalara böler (3. sıra), 3+ kaydırıcıyı
   InShot "Ayarlama" gibi tek tek gösterir, uzun seçenek gruplarını yatay şerit yapar. Öğeler taşınmaz, silinmez. */
(function(){
  if (!window.STUDYO || !window.stSayfala) return;
  var B = document.body;
  var css = document.createElement('style'); css.id = 'st-video-css';
  css.textContent = [
    'body.kv-studyo.is-mod #isAltSek2 button{display:inline-flex;align-items:center;gap:6px}',
    'body.kv-studyo.is-mod #isAltSek2 button svg{width:16px;height:16px;flex:none;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}',
    '#stVsayfa{display:none}',
    'body.kv-studyo.is-mod #stVsayfa:not([hidden]){display:flex;position:sticky;z-index:5;background:var(--is-bg);padding:2px 2px 8px;margin:-2px -4px 6px}',
    'body.kv-studyo.is-mod .st-satir button{border:1.5px solid color-mix(in srgb,var(--is-ink) 16%,transparent);color:var(--is-mute)}',
    'body.kv-studyo.is-mod .st-satir button.on{background:var(--is-ink);color:var(--is-bg);border-color:var(--is-ink)}',
    /* seçenek grupları tek satır, yana kayar (InShot gibi) */
    'body.kv-studyo.is-mod .kv-pan .seg{flex-wrap:nowrap;overflow-x:auto;scrollbar-width:none;padding-bottom:2px;overscroll-behavior-x:contain}',
    'body.kv-studyo.is-mod .kv-pan .seg::-webkit-scrollbar{display:none}',
    'body.kv-studyo.is-mod .kv-pan .seg>button{flex:none}',
    'body.kv-studyo.is-mod .kv-pan .chips{display:flex;flex-wrap:nowrap!important;overflow-x:auto;scrollbar-width:none;padding-bottom:2px}',
    'body.kv-studyo.is-mod .kv-pan .chips::-webkit-scrollbar{display:none}',
    'body.kv-studyo.is-mod .kv-pan .chips>*{flex:none!important}',
    /* yatay şeritler bölüm kutusunu genişletmesin (grid sütunu ekran genişliğinde kalsın) */
    'body.kv-studyo.is-mod .kv-pan section.panel,body.kv-studyo.is-mod .kv-pan .sect{grid-template-columns:minmax(0,1fr)!important;min-width:0;max-width:100%}',
    'body.kv-studyo.is-mod .kv-pan .sect>*,body.kv-studyo.is-mod .kv-pan section.panel>*{min-width:0;max-width:100%}',
    /* Kolaj iptal edildi: video modülünün ana ekranındaki "Kolaj · Yakında" kartı gösterilmez, Fotoğraf kartı tam genişlik */
    '#isAna .is-kart[data-a="kolaj"]{display:none!important}',
    '#isAna .is-kart-ikili{grid-template-columns:minmax(0,1fr)!important}',
    /* stil / kapak önerileri: yatay şerit */
    'body.kv-studyo.is-mod .kv-pan .cands{display:flex!important;flex-wrap:nowrap!important;overflow-x:auto;gap:8px;scrollbar-width:none;grid-template-columns:none!important;padding-bottom:4px}',
    'body.kv-studyo.is-mod .kv-pan .cands::-webkit-scrollbar{display:none}',
    'body.kv-studyo.is-mod .kv-pan .cands>*{flex:none!important;width:150px!important}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);

  /* ⋯ (Diğer) menüsü önizleme bölümünün içinde duruyordu; o bölüm alttaki panelin arkasında çizildiği için menü panelin altında kalıyordu.
     Menü sayfanın en üst katmanına (body) taşınır; konumu ve düğmeleri aynı kalır (kendi sınıfıyla biçimlenir, bağlı olduğu bölüm yok). */
  (function menuTasi(n){ var m = document.getElementById('pvDahaMenu'); if (!m){ if (n < 100) setTimeout(function(){ menuTasi(n + 1); }, 300); return; }
    if (m.parentElement !== document.body) document.body.appendChild(m); m.style.zIndex = '200'; })(0);
  var row = null, mesgul = false, gozcu = null, sonImza = '';
  function aktif(){ return B.classList.contains('kv-studyo') && B.classList.contains('is-mod') && !B.classList.contains('kv-kapali') && !B.classList.contains('kv-klv') && !B.classList.contains('st-sade'); }
  function ikinciSiraSimge(){
    var r2 = document.getElementById('isAltSek2'); if (!r2 || r2.hidden) return;
    [].forEach.call(r2.querySelectorAll('button'), function(b){ if (b.querySelector('svg')) return; var ad = b.textContent.trim();
      b.insertAdjacentHTML('afterbegin', '<svg viewBox="0 0 24 24" aria-hidden="true">' + window.stIkon(ad) + '</svg>'); });
  }
  function satirKur(){
    var r1 = document.getElementById('isAltSek'), r2 = document.getElementById('isAltSek2'); if (!r1) return null;
    if (!row){ row = document.createElement('div'); row.id = 'stVsayfa'; row.hidden = true; }
    var once = r2 || r1; if (row.previousElementSibling !== once) once.after(row);
    return row;
  }
  function temizle(){ var pan = document.querySelector('.kv-pan'); if (pan) [].forEach.call(pan.querySelectorAll('.st-gz'), function(e){ e.classList.remove('st-gz'); });
    sonImza = ''; if (row) row.hidden = true; var ks = document.querySelector('.kv-pan .st-ksat'); if (ks) ks.remove(); }
  function pas(yeniSecim){
    if (mesgul) return; mesgul = true;
    try {
      if (!aktif()){ temizle(); return; }
      var pan = document.querySelector('.kv-pan'), t = B.dataset.kvtab, pn = pan && document.querySelector('#editor [data-panel="' + t + '"]');
      if (!pan || !pn){ temizle(); return; }
      if (t === 'photo' && B.classList.contains('st-sk')){ temizle(); return; }   /* 2.7: Sıra ve klip sekmesi kaydırılır, sayfalanmaz */
      ikinciSiraSimge();
      var r = satirKur(); if (!r) return;
      var birimler = [].filter.call(pn.children, function(c){ return !c.matches('.phead') && getComputedStyle(c).display !== 'none'; });
      if (!birimler.length){ temizle(); return; }
      /* sayfa sırası, üstteki yapışkan sıraların hemen altında dursun */
      var r1 = document.getElementById('isAltSek'), r2 = document.getElementById('isAltSek2'), ust = 46;
      if (r1 && !r1.hidden) ust += r1.offsetHeight + 6; if (r2 && !r2.hidden) ust += r2.offsetHeight + 4;
      r.style.top = ust + 'px';
      var a1 = (document.querySelector('#isAltSek button.on') || {}).textContent || '', a2 = (document.querySelector('#isAltSek2:not([hidden]) button.on') || {}).textContent || '';
      var o = {anahtar: 'kv|' + t + '|' + a1.trim() + '|' + a2.trim(), birimler: birimler, satir: r, kok: pan, yenile: function(){ pas('sayfa'); }};
      /* 2.8: yalnız içerik değiştiyse (liste doldu, süre yazısı değişti…) sayfalar yeniden kurulmaz: düğmeler kendi kendine kaymaz, kaydırma yeri korunur */
      var imza = o.anahtar + '|' + birimler.length + '|' + pan.clientHeight + '|' + innerWidth + '|' + ust;
      if (yeniSecim !== 'sayfa' && imza === sonImza) return;
      var yeni = yeniSecim === 'sayfa' || imza.split('|').slice(0, 4).join('|') !== sonImza.split('|').slice(0, 4).join('|');   /* başka bölüm / sayfa seçildi */
      sonImza = imza; var kay = yeni ? 0 : pan.scrollTop;
      var hesap = function(){ var pr = pan.getBoundingClientRect(), ps = getComputedStyle(pan), alt = parseFloat(ps.paddingBottom) || 0;
        var ilk = birimler.filter(function(e){ return getComputedStyle(e).display !== 'none'; })[0]; var ust2 = ilk ? ilk.getBoundingClientRect().top + pan.scrollTop : pr.top + 160;
        return pr.bottom - alt - ust2 - (r.hidden ? 46 : 0) - 10; };
      pan.scrollTop = 0; o.maxH = hesap();
      /* sınır seçili sayfadan bağımsız: sayfa sırası yerleştikten sonra kalan yükseklik bir kez ölçülür */
      window.stSayfala(o);
      var pr = pan.getBoundingClientRect(), alt = parseFloat(getComputedStyle(pan).paddingBottom) || 0, ustK = o._ilkUst;
      if (ustK == null) ustK = pr.top + 160;
      if (!o._satirOnce && !r.hidden) ustK += r.offsetHeight + 8;
      var gercek = pr.bottom - alt - ustK - 8;
      if (Math.abs(gercek - o.maxH) > 4){ o.maxH = gercek; window.stSayfala(o); }
      pan.scrollTop = kay;
    } catch(e){ console.warn('Stüdyo video bölümleri', e); }
    finally { if (gozcu) gozcu.takeRecords(); mesgul = false; }
  }
  window.stVideoPas = pas;
  /* kısma (debounce değil): sürekli değişiklik olsa da en geç 150 ms'de bir çalışır */
  var zz = 0, bekYeni = false; function sonra(yeni){ bekYeni = bekYeni || !!yeni; if (zz) return; zz = setTimeout(function(){ zz = 0; var y = bekYeni; bekYeni = false; pas(y); }, 150); }
  (function bagla(n){
    var pan = document.querySelector('.kv-pan'), r1 = document.getElementById('isAltSek');
    if (!pan || !r1){ if (n < 120) setTimeout(function(){ bagla(n + 1); }, 400); return; }
    gozcu = new MutationObserver(function(ml){
      if (mesgul) return;
      for (var i = 0; i < ml.length; i++){ var tg = ml[i].target; if (tg.nodeType !== 1) tg = tg.parentElement; if (!tg || (tg.closest && tg.closest('#stVsayfa,.st-ksat,.kv-panb'))) continue;   /* panel başlığı her karede yeniden yazılıyor: yok say */
        var secim = tg.id === 'isAltSek' || tg.id === 'isAltSek2' || (tg.parentElement && /^isAltSek2?$/.test(tg.parentElement.id));
        sonra(secim); return; }
    });
    gozcu.observe(pan, {childList: true, subtree: true, attributes: true, attributeFilter: ['hidden']});
    gozcu.observe(r1, {attributes: true, subtree: true, childList: true, attributeFilter: ['class', 'hidden']});
    var r2 = document.getElementById('isAltSek2'); if (r2) gozcu.observe(r2, {attributes: true, subtree: true, childList: true, attributeFilter: ['class', 'hidden']});
    new MutationObserver(function(ml){ sonra(ml.some(function(m){ return m.attributeName === 'data-kvtab'; })); }).observe(B, {attributes: true, attributeFilter: ['data-kvtab', 'class']});
    addEventListener('resize', function(){ sonra(false); });
    if (window.ResizeObserver) new ResizeObserver(function(){ sonra(false); }).observe(pan);
    sonra(true);
  })(0);
})();
/* 2.13: önizleme gizliyken oynatıcı satırındaki ⛶ düğmesi turuncu "göz" olur → dokununca önizleme geri gelir.
   (Eskiden "Önizlemeyi göster" yalnız Katman bölümünün üstünde ince yazıydı; başka bölümdeyken bulunamıyordu.)
   Önizleme açıkken düğme eskisi gibi tam ekrandır. */
(function(){
  if (!window.STUDYO) return;
  var css = document.createElement('style'); css.id = 'st-pvgoster-css';
  css.textContent = '#isTam.st-goz{color:var(--accent,#e07b2e)!important;background:color-mix(in srgb,var(--accent,#e07b2e) 16%,transparent)!important;border-radius:12px}';
  (document.head || document.documentElement).appendChild(css);
  var TAM = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg>';
  var GOZ = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12S6 6 12 6s9.5 6 9.5 6-3.5 6-9.5 6S2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/></svg>';
  (function kur(n){
    var b = document.getElementById('isTam'), stg = document.getElementById('viewer') && document.getElementById('viewer').closest('.stage');
    if (!b || !stg){ if (n < 100) setTimeout(function(){ kur(n + 1); }, 300); return; }
    var gizli = function(){ return stg.classList.contains('pvhide'); };
    var yaz = function(){ var g = gizli(); if (b.classList.contains('st-goz') === g) return;
      b.classList.toggle('st-goz', g); b.innerHTML = g ? GOZ : TAM; var t = g ? 'Önizlemeyi göster' : 'Tam ekran'; b.title = t; b.setAttribute('aria-label', t); };
    b.addEventListener('click', function(e){
      if (gizli()){ e.stopImmediatePropagation(); e.preventDefault();
        if (window.pvShow) window.pvShow(); else { var p = document.getElementById('pvHide'); if (p) p.click(); }
        setTimeout(function(){ yaz(); window.dispatchEvent(new Event('resize')); }, 30); }
    }, true);
    new MutationObserver(yaz).observe(stg, {attributes: true, attributeFilter: ['class']});
    yaz();
  })(0);
})();
/* 2.14: SADE DÜZEN (varsayılan). Alttan bir bölüme dokununca o bölümün tüm ayarları tek sayfada, alt alta durur:
   bölüm içi sekme sıraları (Hızlı yol / Sıra ve klip / … / Tümü), ara başlık sırası, sayfa sırası ve tek tek kaydırıcı yok;
   uzun seçenek şeritleri yana kaymaz, satırlara sarılır. Bölümler başlıklarıyla açılır-kapanır (Kareden Videoya'nın akordeonu).
   Eski sekmeli düzen silinmedi: ⋯ menüsü › "Sekmeli düzene geç" (seçim hatırlanır, localStorage 'studyo.vSade' = '0'). */
(function(){
  if (!window.STUDYO) return;
  var B = document.body, K = 'studyo.vSade';
  function sade(){ try { return localStorage.getItem(K) !== '0'; } catch(e){ return true; } }
  var css = document.createElement('style'); css.id = 'st-sade-css';
  css.textContent = [
    'body.st-sade #isAltSek,body.st-sade #isAltSek2,body.st-sade #stVsayfa,body.st-sade .kv-pan .st-ksat{display:none!important}',
    'body.st-sade.kv-studyo.is-mod .kv-pan .seg,body.st-sade.kv-studyo.is-mod .kv-pan .chips{flex-wrap:wrap!important;overflow-x:visible!important}',
    'body.st-sade.kv-studyo.is-mod .kv-pan .seg>button,body.st-sade.kv-studyo.is-mod .kv-pan .chips>*{flex:1 1 auto!important}',
    /* bölüm başlığı kartın iki kenarına kadar uzansın (kenar boşluğuna taşan başlık %100 sınırına takılıp sağda boşluk bırakıyordu) */
    'body.st-sade.kv-studyo.is-mod .kv-pan .sect>h3{max-width:none!important}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);
  /* sekmeli düzenin gizlediği öğeler geri açılır (öğeler taşınmaz, silinmez; yalnız gizleme sınıfları kaldırılır) */
  function ac(){ if (!B.classList.contains('st-sade')) return; var pan = document.querySelector('.kv-pan'); if (!pan) return;
    [].forEach.call(pan.querySelectorAll('.is-gizle,.is-tek,.is-alt2,.st-gz'), function(e){ e.classList.remove('is-gizle', 'is-tek', 'is-alt2', 'st-gz'); }); }
  function uygula(){ B.classList.toggle('st-sade', sade()); ac();
    var b = document.getElementById('stSadeSec'); if (b) b.dataset.lb = sade() ? 'Sekmeli düzene geç' : 'Sade düzene geç';
    try { window.stVideoPas && window.stVideoPas(true); } catch(e){} window.dispatchEvent(new Event('resize')); }
  (function bagla(n){
    var pan = document.querySelector('.kv-pan'), m = document.getElementById('pvDahaMenu');
    if (!pan || !m){ if (n < 150) setTimeout(function(){ bagla(n + 1); }, 400); return; }
    new MutationObserver(function(){ ac(); }).observe(pan, {subtree: true, attributes: true, attributeFilter: ['class']});
    if (!document.getElementById('stSadeSec')){ var b = document.createElement('button'); b.type = 'button'; b.id = 'stSadeSec';
      b.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" style="fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round"><path d="M4 6h16M4 12h16M4 18h10"/></svg>';
      b.addEventListener('click', function(){ try { localStorage.setItem(K, sade() ? '0' : '1'); } catch(e){} uygula();
        if (window.toast) try { toast(sade() ? 'Sade düzen: her bölüm tek sayfa' : 'Sekmeli düzen'); } catch(e){} });
      m.appendChild(b); }
    uygula();
  })(0);
})();
/* 2.15: (1) TAKİP EDEN YAZI — noktaya dokununca yazı kutusu kendiliğinden açılır, klavye gelir; takip arkada sürer.
         (eskiden yazı alanı Klip bölümünün altında, kapalı bir akordeonun içinde kalıyordu; bulunamıyordu)
   (2) ÖNİZLEME BOYU — oynatıcı satırının altındaki tutamacı sürükle: aşağı = büyüt, yukarı = küçült; çift dokun = varsayılan.
   (3) ALT MENÜLER — araç çubuğu ve klip araçları yana kayan tek sıra yerine iki düzgün sıra; yarım kesik düğme yok.
   Kareden Videoya'nın işlevleri değişmedi: kutu, takip bölümündeki #tkT / #tkT2 alanlarına yazar. */
(function(){
  if (!window.STUDYO) return;
  var B = document.body, $ = function(s){ return document.querySelector(s); };
  var css = document.createElement('style'); css.id = 'st-215-css';
  css.textContent = [
    /* (1) takip yazısı kutusu */
    '#stTkKutu{position:fixed;left:8px;right:8px;z-index:260;background:var(--is-bg,#1f1f1f);color:var(--is-ink,#fff);border-radius:16px;padding:12px 12px 10px;box-shadow:0 8px 30px rgba(0,0,0,.5);border:1px solid color-mix(in srgb,var(--is-ink,#fff) 14%,transparent)}',
    '#stTkKutu[hidden]{display:none}',
    '#stTkKutu .st-tk-bas{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:6px}',
    '#stTkKutu .st-tk-bas b{font-size:15px}',
    '#stTkKutu .st-tk-not{font-size:12px;color:var(--is-mute,#aaa);min-height:16px;margin-bottom:8px}',
    '#stTkKutu input{display:block;width:100%;box-sizing:border-box;height:44px;border-radius:10px;border:1.5px solid color-mix(in srgb,var(--is-ink,#fff) 22%,transparent);background:color-mix(in srgb,var(--is-ink,#fff) 6%,transparent);color:inherit;font:500 16px/1 var(--ui,system-ui),sans-serif;padding:0 12px;margin-bottom:8px}',
    '#stTkKutu input:focus{outline:none;border-color:var(--accent,#e07b2e)}',
    '#stTkKutu .st-tk-dug{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:2px}',
    '#stTkKutu .st-tk-dug button{height:44px;border-radius:12px;border:1.5px solid color-mix(in srgb,var(--is-ink,#fff) 22%,transparent);background:none;color:inherit;font:600 14.5px/1 var(--ui,system-ui),sans-serif}',
    '#stTkKutu .st-tk-dug button.ana{background:var(--is-ink,#fff);color:var(--is-bg,#111);border-color:var(--is-ink,#fff)}',
    /* (2) önizleme boyu tutamacı */
    '#stPvTut{display:none}',
    'body.kv-studyo:not(.kv-kapali):not(.kv-klv):not(.pvfs) .stage:not(.pvhide)>#stPvTut{display:flex;align-items:center;justify-content:center;gap:8px;height:24px;margin:0 auto -2px;width:100%;touch-action:none;cursor:ns-resize;color:var(--is-mute,#999);font:500 11px/1 var(--ui,system-ui),sans-serif;user-select:none;-webkit-user-select:none}',
    '#stPvTut i{display:block;width:54px;height:5px;border-radius:5px;background:currentColor;opacity:.75}',
    '#stPvTut.tut{color:var(--accent,#e07b2e)}',
    'body.st-pv-sur .viewer{transition:none!important}',
    /* (3) alt menüler: iki düzgün sıra (kimlik iki kez yazıldı: Kareden Videoya'nın sonradan gelen tek sıra kurallarını geçsin) */
    'body.kv-studyo.is-mod #mininav#mininav{flex-wrap:wrap!important;overflow:hidden!important;justify-content:stretch!important;height:auto!important;max-height:none!important}',
    'body.kv-studyo.is-mod.kv-kapali #isKlipAr#isKlipAr:not([hidden]){flex-wrap:wrap!important;overflow:hidden!important;height:auto!important;max-height:none!important}',
    'body.kv-studyo.is-mod #mininav#mininav>button,body.kv-studyo.is-mod #isKlipAr#isKlipAr>button{flex:1 1 calc(100% / var(--stSut,7))!important;min-width:0!important;max-width:none!important;min-height:52px!important;max-height:none!important;padding:3px 1px!important;margin:0!important;white-space:normal!important;text-align:center;line-height:1.1}',
    'body.kv-studyo.is-mod #mininav#mininav>button span,body.kv-studyo.is-mod #isKlipAr#isKlipAr>button span{font-size:11px!important;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%!important}',
    'body.kv-studyo.is-mod #isKlipAr#isKlipAr>button[data-is="tmm"]{margin-left:0!important}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);

  /* ---------- (1) takip eden yazı kutusu ---------- */
  var kutu = null, notGozcu = null;
  function kutuKur(){
    if (kutu) return kutu;
    kutu = document.createElement('div'); kutu.id = 'stTkKutu'; kutu.hidden = true; kutu.setAttribute('role', 'dialog'); kutu.setAttribute('aria-label', 'Takip eden yazı');
    kutu.innerHTML = '<div class="st-tk-bas"><b>Takip eden yazı</b></div><div class="st-tk-not" id="stTkNot" aria-live="polite"></div>'
      + '<input type="text" id="stTkT" maxlength="60" placeholder="Yazı (ör. Kızılcık yaprağı)" autocomplete="off" enterkeyhint="next">'
      + '<input type="text" id="stTkT2" maxlength="60" placeholder="Alt satır (isteğe bağlı)" autocomplete="off" enterkeyhint="done">'
      + '<div class="st-tk-dug"><button type="button" id="stTkAyar">Yazı stili, renk…</button><button type="button" class="ana" id="stTkTamam">Tamam</button></div>';
    document.body.appendChild(kutu);
    var bagla = function(kendi, asil){ kendi.addEventListener('input', function(){ var a = $(asil); if (!a) return; a.value = kendi.value; a.dispatchEvent(new Event('input', {bubbles: true})); }); };
    bagla(kutu.querySelector('#stTkT'), '#tkT'); bagla(kutu.querySelector('#stTkT2'), '#tkT2');
    kutu.querySelector('#stTkT').addEventListener('keydown', function(e){ if (e.key === 'Enter'){ e.preventDefault(); kutu.querySelector('#stTkT2').focus(); } });
    kutu.querySelector('#stTkT2').addEventListener('keydown', function(e){ if (e.key === 'Enter'){ e.preventDefault(); kapat(); } });
    kutu.querySelector('#stTkTamam').addEventListener('click', kapat);
    kutu.querySelector('#stTkAyar').addEventListener('click', function(){ kapat();
      setTimeout(function(){ var g = $('#tkGrp'); if (!g) return; try { window.showTab && showTab('photo'); } catch(e){} try { window.kvStudyo && kvStudyo.kapat(false); } catch(e){}
        try { window.sectAc && sectAc(g); } catch(e){}
        setTimeout(function(){ try { g.scrollIntoView({block: 'start', behavior: 'smooth'}); } catch(e){} }, 250); }, 60); });
    if (window.visualViewport){ visualViewport.addEventListener('resize', yerles); visualViewport.addEventListener('scroll', yerles); }
    addEventListener('resize', yerles);
    return kutu;
  }
  /* kutu klavyenin hemen üstünde durur; önizleme üstte görünür kalır */
  function yerles(){ if (!kutu || kutu.hidden) return; var vv = window.visualViewport, alt = vv ? Math.max(0, innerHeight - (vv.height + vv.offsetTop)) : 0;
    kutu.style.bottom = (alt + 8) + 'px'; kutu.style.top = 'auto'; }
  function notAktar(){ var n = $('#tkNot'), k = $('#stTkNot'); if (n && k) k.textContent = n.textContent; }
  function ac(){
    kutuKur(); var t = $('#tkT'), t2 = $('#tkT2');
    kutu.querySelector('#stTkT').value = t ? t.value : ''; kutu.querySelector('#stTkT2').value = t2 ? t2.value : '';
    kutu.hidden = false; yerles(); notAktar();
    var n = $('#tkNot'); if (n && !notGozcu){ notGozcu = new MutationObserver(notAktar); notGozcu.observe(n, {childList: true, characterData: true, subtree: true}); }
    var g = kutu.querySelector('#stTkT'); try { g.focus({preventScroll: true}); } catch(e){ g.focus(); }
    setTimeout(function(){ if (!kutu.hidden && document.activeElement !== g){ try { g.focus({preventScroll: true}); } catch(e){} } yerles(); }, 120);
  }
  function kapat(){ if (!kutu) return; var a = document.activeElement; if (a && kutu.contains(a)) a.blur(); kutu.hidden = true;
    try { window.requestRender && requestRender(); } catch(e){} }
  /* noktaya dokunuş: Kareden Videoya'nın kendi dinleyicisi noktayı kabul edince (seçim modu kapanır) kutu açılır.
     Aynı dokunuşun içinde açıldığı için telefonda klavye de gelir. */
  addEventListener('pointerdown', function(e){
    var v = document.getElementById('viewer'); if (!v || !v.classList.contains('tk-secim') || !v.contains(e.target) || e.button > 0) return;
    setTimeout(function(){ if (!v.classList.contains('tk-secim')) ac(); }, 0);
  }, true);
  /* yanıltıcı ipucu düzeltildi: yazı artık "sonra bölümde" değil, dokununca açılan kutuda girilir */
  (function ipucu(n){ if (typeof window.toast !== 'function'){ if (n < 100) setTimeout(function(){ ipucu(n + 1); }, 300); return; }
    var eski = window.toast; window.toast = function(m){ if (typeof m === 'string' && /yazıyı sonra .Takip eden yazı/.test(m)) m = 'Takip edilecek noktaya dokun · yazı kutusu kendiliğinden açılır';
      return eski.apply(this, [m].concat([].slice.call(arguments, 1))); }; })(0);

  /* ---------- (2) önizleme boyu tutamacı ---------- */
  var KP = 'studyo.pvk', kBoy = 1;
  try { var s = parseFloat(localStorage.getItem(KP)); if (s >= .45 && s <= 1.6) kBoy = s; } catch(e){}
  /* Kareden Videoya her yeniden boyutlanmada __pvBuyuk'u kendi 6 basamaklı değerine çekiyordu; Stüdyo'da tutamacın değeri geçerli */
  try { Object.defineProperty(window, '__pvBuyuk', {configurable: true, get: function(){ return kBoy; }, set: function(){}}); } catch(e){}
  function yenile(){ try { window.kvStudyo && kvStudyo.yerlesim(); } catch(e){} try { window.requestRender && requestRender(); } catch(e){} }
  (function tutKur(n){
    var k = document.getElementById('isKont'); if (!k){ if (n < 150) setTimeout(function(){ tutKur(n + 1); }, 300); return; }
    if (document.getElementById('stPvTut')) return;
    var t = document.createElement('div'); t.id = 'stPvTut'; t.setAttribute('role', 'slider'); t.setAttribute('aria-label', 'Önizleme boyu: sürükle');
    t.title = 'Sürükle: önizlemeyi büyüt / küçült · çift dokun: varsayılan'; t.innerHTML = '<i></i>';
    k.after(t);
    var y0 = 0, k0 = 1, rq = 0, son = 0, aktif = false;
    t.addEventListener('pointerdown', function(e){ e.preventDefault(); aktif = true; y0 = e.clientY; k0 = kBoy; t.classList.add('tut'); B.classList.add('st-pv-sur');
      try { t.setPointerCapture(e.pointerId); } catch(x){} });
    t.addEventListener('pointermove', function(e){ if (!aktif) return; var H = window.visualViewport ? visualViewport.height : innerHeight;
      kBoy = Math.max(.45, Math.min(1.6, k0 + (e.clientY - y0)/(H*.42)));
      if (!rq) rq = requestAnimationFrame(function(){ rq = 0; yenile(); }); });
    var bit = function(){ if (!aktif) return; aktif = false; t.classList.remove('tut'); B.classList.remove('st-pv-sur');
      try { localStorage.setItem(KP, String(+kBoy.toFixed(3))); } catch(x){} yenile(); };
    t.addEventListener('pointerup', bit); t.addEventListener('pointercancel', bit);
    t.addEventListener('click', function(){ var z = Date.now(); if (z - son < 350){ kBoy = 1; try { localStorage.setItem(KP, '1'); } catch(x){} yenile(); if (window.toast) try { toast('Önizleme varsayılan boyda'); } catch(x){} } son = z; });
    yenile();
  })(0);

  /* ---------- (3) alt menüler: iki düzgün sıra (görünen düğme sayısının yarısı kadar sütun) ---------- */
  function sutun(el){ if (!el) return; var n = [].filter.call(el.children, function(b){ return b.tagName === 'BUTTON' && !b.hidden && getComputedStyle(b).display !== 'none'; }).length;
    var s = n <= 7 ? Math.max(1, n) : Math.ceil(n/2); if (el.style.getPropertyValue('--stSut') !== String(s)){ el.style.setProperty('--stSut', s); setTimeout(yenile, 30); } }
  (function mKur(n){
    var m = document.getElementById('mininav'), ka = document.getElementById('isKlipAr');
    if (!m || !ka){ if (n < 150) setTimeout(function(){ mKur(n + 1); }, 300); return; }
    var hepsi = function(){ sutun(m); sutun(ka); };
    [m, ka].forEach(function(el){ new MutationObserver(hepsi).observe(el, {childList: true, subtree: true, attributes: true, attributeFilter: ['hidden', 'style', 'class']}); });
    addEventListener('resize', hepsi); hepsi();
  })(0);
})();
