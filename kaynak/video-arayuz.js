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
