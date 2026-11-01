/* ===== STÜDYO · SAYFALAMA (fotoğraf ve video editörü için ortak) =====
   Amaç: panelde aşağı doğru kaydırma olmasın. Seçili bölümün içeriği:
   1) 3+ kaydırıcı varsa InShot "Ayarlama" gibi: simgeli adlar sırası + altta tek kaydırıcı
   2) yine ekrana sığmıyorsa sayfalara bölünür; sayfalar üstte simgeli bir sırada, içindeki ilk ayarın adıyla
   Uygulamanın öğeleri taşınmaz, silinmez, yeniden yazılmaz: yalnız .st-gz sınıfıyla gizlenip gösterilir. */
(function(){
  if (!window.STUDYO || window.stSayfala) return;
  var css = document.createElement('style'); css.id = 'st-sayfa-css';
  css.textContent = [
    /* gizleme display:none ile DEĞİL: uygulamaların kendi görünürlük denetimleri (ör. ikinci sıra ara başlıkları) bozulmasın */
    '.st-gz{position:absolute!important;width:1px!important;height:1px!important;min-height:0!important;overflow:hidden!important;clip-path:inset(50%)!important;visibility:hidden!important;margin:0!important;padding:0!important;border:0!important;pointer-events:none!important}',
    '.st-satir{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;-webkit-overflow-scrolling:touch;scroll-behavior:smooth}',
    '.st-satir::-webkit-scrollbar{display:none}',
    '.st-satir button{flex:none;display:inline-flex;align-items:center;gap:6px;border:1px solid var(--line,#3334);background:transparent;color:var(--muted,var(--mute,#888));border-radius:999px;padding:7px 12px 7px 9px;font:600 12.5px/1 var(--f-ui,var(--ui,system-ui)),sans-serif;white-space:nowrap;cursor:pointer;-webkit-tap-highlight-color:transparent}',
    '.st-satir button svg{width:17px;height:17px;flex:none;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}',
    '.st-satir button.on{background:var(--text,var(--ink,#111));color:var(--surface,var(--panel,#fff));border-color:transparent}',
    '.st-satir button i{width:6px;height:6px;border-radius:50%;background:var(--accent,#e80);margin-left:-2px}',
    '.st-satir button.on i{background:var(--accent,#e80)}',
    '.st-ksat{margin:2px 0 4px}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);

  /* ---------- simgeler: bölüm / ayar adına göre ---------- */
  var P = {
    gunes: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    yarim: '<circle cx="12" cy="12" r="9"/><path d="M12 3v18" /><path d="M12 3a9 9 0 010 18z" fill="currentColor" stroke="none"/>',
    sicak: '<path d="M14 14.8V5a2 2 0 10-4 0v9.8a4 4 0 104 0z"/>',
    damla: '<path d="M12 3s6 6.5 6 11a6 6 0 01-12 0c0-4.5 6-11 6-11z"/>',
    palet: '<path d="M12 3a9 9 0 100 18c1 0 1.6-.8 1.6-1.6 0-.4-.2-.8-.4-1.1-.3-.3-.4-.7-.4-1.1 0-.9.7-1.6 1.6-1.6H16a5 5 0 005-5c0-4.1-4-7.6-9-7.6z"/><circle cx="7.5" cy="11.5" r="1"/><circle cx="10.5" cy="7.5" r="1"/><circle cx="15" cy="7.5" r="1"/>',
    ince: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
    yildiz: '<path d="M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6L12 16.4 6.7 19.4l1.2-6L3.4 9.3l6-.7z"/>',
    kisi: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0116 0"/>',
    resim: '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M4 18l5-5 4 3 3-2 4 4"/>',
    yaprak: '<path d="M5 21c0-9 5-15 15-16-1 10-7 15-15 16z"/><path d="M5 21l8-8"/>',
    bulut: '<path d="M7 18a4 4 0 010-8 6 6 0 0111.5 1.5A3.5 3.5 0 0118 18z"/>',
    cicek: '<circle cx="12" cy="12" r="2.5"/><path d="M12 9.5V4M12 20v-5.5M9.5 12H4M20 12h-5.5M10.2 10.2L6.3 6.3M17.7 17.7l-3.9-3.9M13.8 10.2l3.9-3.9M6.3 17.7l3.9-3.9"/>',
    cadir: '<path d="M3 20L12 4l9 16zM12 4v16M8.5 20l3.5-6 3.5 6"/>',
    yer: '<path d="M6 3h12v18l-6-4-6 4z"/>',
    bulanik: '<circle cx="12" cy="12" r="8" stroke-dasharray="2 3"/><circle cx="12" cy="12" r="3"/>',
    hedef: '<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/>',
    konum: '<path d="M12 21s-6-5.5-6-11a6 6 0 0112 0c0 5.5-6 11-6 11z"/><circle cx="12" cy="10" r="2.2"/>',
    kalem: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
    yazi: '<path d="M5 6V4h14v2M12 4v16M9 20h6"/>',
    cetvel: '<rect x="2" y="8" width="20" height="8" rx="1.5"/><path d="M6 8v3M10 8v4M14 8v3M18 8v4"/>',
    boyut: '<path d="M4 9V4h5M20 15v5h-5M4 4l7 7M20 20l-7-7"/>',
    egri: '<path d="M3 21C9 21 9 3 21 3"/><path d="M3 3v18h18"/>',
    sakal: '<path d="M7 4c0 6 10 6 10 0M5 13a7 7 0 0014 0"/>',
    sac: '<path d="M4 20V10a8 8 0 0116 0v10M8 20v-8M12 20v-9M16 20v-8"/>',
    goz: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    kalite: '<path d="M12 3l2.5 5 5.5.8-4 3.9 1 5.5L12 15.6 7 18.2l1-5.5-4-3.9 5.5-.8z"/>',
    dosya: '<path d="M14 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V9z"/><path d="M14 3v6h6"/>',
    format: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/>',
    muzik: '<path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/>',
    ses: '<path d="M4 9v6h4l5 4V5L8 9z"/><path d="M16 9a4 4 0 010 6M19 6a8 8 0 010 12"/>',
    mikrofon: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0014 0M12 18v3"/>',
    saat: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    hiz: '<path d="M12 13l4-4"/><path d="M3.5 17a9 9 0 1117 0"/>',
    hareket: '<path d="M4 12h12M12 6l6 6-6 6"/><path d="M4 7h4M4 17h4"/>',
    gecis: '<rect x="3" y="6" width="8" height="12" rx="1.5"/><rect x="13" y="6" width="8" height="12" rx="1.5"/><path d="M11 12h2"/>',
    kirp: '<path d="M6 2v16h16M2 6h16v16"/>',
    dondur: '<path d="M20 12a8 8 0 11-2.3-5.6M20 4v5h-5"/>',
    sablon: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/>',
    paket: '<path d="M21 8l-9-5-9 5 9 5zM3 8v8l9 5 9-5V8"/>',
    efekt: '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6"/>',
    logo: '<circle cx="12" cy="12" r="8.5"/><path d="M9 9h6v6H9z"/>',
    kapak: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 15l3-3 3 3 2-2 3 3"/>',
    bilgi: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.1"/>',
    liste: '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    arti: '<path d="M12 5v14M5 12h14"/>',
    kaydet: '<path d="M12 3v12M7 10l5 5 5-5M4 20h16"/>',
    katman: '<path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5"/>',
    boncuk: '<circle cx="7" cy="8" r="2"/><circle cx="16" cy="6" r="1.5"/><circle cx="17" cy="15" r="2.5"/><circle cx="8" cy="17" r="1.5"/>',
    zil: '<path d="M6 16V11a6 6 0 0112 0v5l2 2H4z"/><path d="M10 21h4"/>',
    kanca: '<path d="M14 4v8a4 4 0 11-8 0"/><circle cx="14" cy="4" r="1.6"/>',
    zar: '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8.5" cy="8.5" r="1.2"/><circle cx="15.5" cy="15.5" r="1.2"/><circle cx="12" cy="12" r="1.2"/>',
    tik: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    ayar: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/>',
    nokta: '<circle cx="12" cy="12" r="3.2"/>'
  };
  var KURAL = [
    [/pozlama|parlaklık|parlak alan|aydınlat|ışık|beyazlar/i, 'gunes'], [/kontrast|siyahlar|gölge|karart|hdr/i, 'yarim'],
    [/sıcaklık|beyaz ayarı|ton\b|renk tonu/i, 'sicak'], [/doygunluk|canlılık|yoğunluk|koruma/i, 'damla'],
    [/renk|hsl|palet|denge|tint/i, 'palet'], [/keskin|netlik|detay|gürültü|doku|ince ayar|berrak/i, 'ince'],
    [/güzellik|cilt|pürüz|parlat/i, 'yildiz'], [/kişi|yüz|portre|selfi|insan/i, 'kisi'], [/göz|diş/i, 'goz'], [/saç/i, 'sac'],
    [/arka plan|fotoğraf|görsel|resim|alttaki/i, 'resim'], [/sonbahar|yaprak|doğa/i, 'yaprak'], [/mevsim|hava|bulut/i, 'bulut'],
    [/çiçek|gül|meyve/i, 'cicek'], [/kamp|çay|kahve/i, 'cadir'], [/hazır ayar|kayıtlı|favori|yer imi/i, 'yer'],
    [/bulanık|odak|bokeh|yumuşa/i, 'bulanik'], [/seçim|hedef|nesne|leke/i, 'hedef'], [/nerede|konum|yer\b|köşe/i, 'konum'],
    [/imza|kalem|fırça|boya/i, 'kalem'], [/yazı|metin|başlık|altyazı|font|bant/i, 'yazi'], [/ölçü|piksel|cetvel|oran/i, 'cetvel'],
    [/boyut|büyüklük|ölçek|yakınlaş|zoom/i, 'boyut'], [/eğri/i, 'egri'], [/kalite|bit hızı/i, 'kalite'], [/dosya|ad\b|isim/i, 'dosya'],
    [/format|biçim|jpg|png|webp|mp4/i, 'format'], [/müzik|şarkı|melodi/i, 'muzik'], [/ses|düzey|seviye|kıs/i, 'ses'],
    [/seslendir|okuma|okur|konuş|mikrofon/i, 'mikrofon'], [/süre|zaman|saniye|bekle/i, 'saat'], [/hız|fps|kare hızı/i, 'hiz'],
    [/hareket|kay|animasyon|giriş hareketi|ken burns|yaklaş/i, 'hareket'], [/geçiş/i, 'gecis'], [/kırp|kadraj|doldur/i, 'kirp'],
    [/döndür|çevir|açı|eğim/i, 'dondur'], [/şablon|stil|tarz|görünüm|tema/i, 'sablon'], [/paket/i, 'paket'],
    [/efekt|parıltı|ışıltı|vinyet|gren|tane/i, 'efekt'], [/logo|kanal/i, 'logo'], [/kapak/i, 'kapak'], [/bilgi|açıklama|not|yardım|nasıl/i, 'bilgi'],
    [/liste|sıra|klip/i, 'liste'], [/ekle|yeni/i, 'arti'], [/kaydet|oluştur|dışa|indir|paylaş/i, 'kaydet'], [/katman/i, 'katman'],
    [/boncuk|parçacık/i, 'boncuk'], [/abone|zil|hatırlat/i, 'zil'], [/kanca/i, 'kanca'], [/rastgele|karışık/i, 'zar'],
    [/otomatik|akıllı|genel/i, 'ayar']
  ];
  function ikon(ad){ for (var i = 0; i < KURAL.length; i++) if (KURAL[i][0].test(ad)) return P[KURAL[i][1]]; return P.nokta; }
  window.stIkon = ikon;

  function ilkMetin(a){
    var w = document.createTreeWalker(a, NodeFilter.SHOW_TEXT), n;
    while ((n = w.nextNode())){
      var p = n.parentElement; if (p && (p.closest('.st-satir') || /^(OUTPUT|OPTION|SCRIPT|STYLE)$/.test(p.tagName) || p.closest('.val,.rst,output'))) continue;
      var t = n.textContent.replace(/\s+/g, ' ').trim(); if (t.length > 1 && !/^[\d%+\-−.,×x:°\s]+$/.test(t)) return t;
    }
    return '';
  }
  function kisa(t){
    t = String(t || '').replace(/[\u{1F000}-\u{1FAFF}☀-➿️]/gu, '').replace(/\s+/g, ' ').trim();
    t = t.replace(/^[\d\s·.\-–—:•]+(?=\p{L})/u, '');
    t = t.split(/ — | – | · |: | \(|\(|, |\. |\?/)[0].trim().replace(/[:.]+$/, '');
    if (t.length > 18){ var k = t.slice(0, 18); var b = k.lastIndexOf(' '); t = (b > 8 ? k.slice(0, b) : k) + '…'; }
    return t ? t.charAt(0).toLocaleUpperCase('tr') + t.slice(1) : '';
  }
  window.stKisa = kisa;
  var ETK = '.sl-top,.lab label,.lab,label,h4,h3,legend,.subhead,b,strong,.note,p,span,button';
  function etiket(el){
    if (!el || el.nodeType !== 1) return '';
    var c = [el].concat([].slice.call(el.querySelectorAll(ETK)));
    for (var i = 0; i < c.length; i++){ var t = ilkMetin(c[i]); if (t) return kisa(t); }
    return '';
  }
  function gor(e){ return !!(e && e.nodeType === 1 && !e.classList.contains('st-gz') && !e.hidden && getComputedStyle(e).display !== 'none' && e.getClientRects().length); }
  var ATOM = '.sl,.row,.seg,.chips,.cands,#panelIn .presets,.eff-row,.hsl-dots,canvas,.yz-sab,.im-sab,.btn-row,.btns,.fields,.field,.swatches,label,.check,.big-one,button,input,select,textarea,.hist-list,.dcard,.info-card,p,h4,.st-ksat,.st-satir,video,audio,img,.strip,.inline';
  function kaydiriciMi(e){ return e.matches('.sl') || (e.matches('.row') && !!e.querySelector('input[type=range]') && e.querySelectorAll('input[type=range]').length === 1); }
  function kaydiriciSay(e){ return e.querySelectorAll('input[type=range]').length; }

  var SEC = {}; try { SEC = JSON.parse(localStorage.getItem('st.sayfa') || '{}') || {}; } catch(e){ SEC = {}; }
  function secKaydet(){ try { var k = Object.keys(SEC); if (k.length > 400) k.slice(0, k.length - 400).forEach(function(x){ delete SEC[x]; }); localStorage.setItem('st.sayfa', JSON.stringify(SEC)); } catch(e){} }

  function satirCiz(row, adlar, sec, isaret){
    var imza = adlar.join('|') + '#' + (isaret || []).join('');
    if (row.dataset.imza !== imza){
      row.dataset.imza = imza;
      row.innerHTML = adlar.map(function(a, i){ return '<button type="button" data-i="' + i + '"><svg viewBox="0 0 24 24" aria-hidden="true">' + ikon(a) + '</svg><span>' + a.replace(/[&<>]/g, '') + '</span>' + (isaret && isaret[i] ? '<i></i>' : '') + '</button>'; }).join('');
    }
    [].forEach.call(row.children, function(b, i){ b.classList.toggle('on', i === sec); });
    var on = row.children[sec]; if (on && row.dataset.son !== String(sec)){ row.dataset.son = String(sec); row.scrollLeft = Math.max(0, on.offsetLeft - row.clientWidth / 2 + on.offsetWidth / 2); }
  }
  var KS = null;   /* kaydırıcı sırası (tek) */
  function benzersiz(adlar){ var say = {}; return adlar.map(function(a, i){ a = a || ('Bölüm ' + (i + 1)); if (say[a]){ say[a]++; return a + ' ' + say[a]; } say[a] = 1; return a; }); }

  /* o: {anahtar, birimler:[öğe], satir:sayfa sırası öğesi, maxH, kok:temizlik kökü, yenile:fn} */
  window.stSayfala = function(o){
    var kok = o.kok || document;
    [].forEach.call(kok.querySelectorAll('.st-gz'), function(e){ e.classList.remove('st-gz'); });
    var maxH = Math.max(120, o.maxH || 300), kaplar = [], birim = [];
    (function ac(list){ list.forEach(function(e){ if (!gor(e) || e.classList.contains('st-satir')) return;
      var k = [].filter.call(e.children, gor);
      var ks2 = kaydiriciSay(e), acilir = k.length >= 1 && ((!e.matches(ATOM) && (e.offsetHeight > maxH || ks2 >= 2)) || (e.matches('.two') && ks2 >= 2));   /* iki sütunlu kaydırıcı kabı da açılır */
      if (acilir){ kaplar.push(e); ac(k); } else birim.push(e); }); })(o.birimler || []);
    /* 1) kaydırıcılar: 3 ve üstü → tek tek */
    var sl = birim.filter(kaydiriciMi);
    if (sl.length >= 3){
      if (!KS){ KS = document.createElement('div'); KS.className = 'st-satir st-ksat'; KS.addEventListener('click', function(e){ var b = e.target.closest('button[data-i]'); if (!b) return; SEC[KS.dataset.anahtar] = +b.dataset.i; secKaydet(); if (KS._yenile) KS._yenile(); }); }
      var adK = benzersiz(sl.map(etiket)), kk = o.anahtar + '|k|' + adK.join(','), s = SEC[kk]; if (!(s >= 0 && s < sl.length)) s = 0;
      KS.dataset.anahtar = kk; KS._yenile = o.yenile;
      if (KS.nextElementSibling !== sl[0] || !KS.isConnected) sl[0].parentNode.insertBefore(KS, sl[0]);
      var isaret = sl.map(function(x){ return !!x.querySelector('.val.ch,.rst.show'); });
      satirCiz(KS, adK, s, isaret);
      sl.forEach(function(x, i){ if (i !== s) x.classList.add('st-gz'); });
      birim = birim.filter(function(x){ return !x.classList.contains('st-gz'); });
      var i0 = birim.indexOf(sl[s]); KS._yapis = true; birim.splice(i0, 0, KS);
    } else if (KS && KS.isConnected && (!o.kok || o.kok.contains(KS))) KS.remove();
    /* içeriğin başladığı yer (her şey görünürken, seçimden bağımsız) — çağıran sınırı buna göre hesaplar */
    o._ilkUst = birim.length ? birim[0].getBoundingClientRect().top : null; o._satirOnce = !!(o.satir && !o.satir.hidden);
    /* 2) sığmıyorsa sayfalara böl */
    var sayfalar = [], cur = [], bas = null, gruplar = [];
    /* birlikte kalması gerekenler tek grup: kaydırıcı sırası + seçili kaydırıcı, ayar + altındaki açıklama notu */
    birim.forEach(function(u, i){ var yap = i > 0 && (birim[i - 1] === KS || u.matches('p.note,.note,.empty,p.hint,small'));
      var g = gruplar[gruplar.length - 1];
      /* not, grubu ekrana sığmaz yapıyorsa ayrı sayfaya kalsın */
      if (yap && g && u.matches('p.note,.note,.empty,p.hint,small') && u.getBoundingClientRect().bottom - g[0].getBoundingClientRect().top > maxH) yap = false;
      if (yap && g) g.push(u); else gruplar.push([u]); });
    gruplar.forEach(function(g){
      var ust = g[0].getBoundingClientRect().top, alt = g[g.length - 1].getBoundingClientRect().bottom;
      if (bas === null) bas = ust;
      if (cur.length && alt - bas > maxH){ sayfalar.push(cur); cur = []; bas = ust; }
      cur = cur.concat(g);
    });
    if (cur.length) sayfalar.push(cur);
    var row = o.satir;
    if (sayfalar.length >= 2 && row){
      var onceki = '', adS = benzersiz(sayfalar.map(function(p){ var t = ''; for (var j = 0; j < p.length && !t; j++){ if (p[j] === KS || p[j].matches('p.note,.note')) continue; t = etiket(p[j]); } if (!t) for (var j2 = 0; j2 < p.length && !t; j2++) t = etiket(p[j2]); if (!t) t = onceki || 'Devam'; onceki = t; return t; }));
      var ks = o.anahtar + '|s', sec = SEC[ks];   /* seçim sayfa sırasıyla saklanır: sayfa sınırı ölçümle değişse de seçilen yer kaybolmaz */ if (!(sec >= 0 && sec < sayfalar.length)) sec = 0;
      row.hidden = false; row.dataset.anahtar = ks; row._yenile = o.yenile;
      if (!row._kuruldu){ row._kuruldu = 1; row.classList.add('st-satir'); row.addEventListener('click', function(e){ var b = e.target.closest('button[data-i]'); if (!b) return; SEC[row.dataset.anahtar] = +b.dataset.i; secKaydet(); if (row._yenile) row._yenile(true); }); }
      satirCiz(row, adS, sec);
      sayfalar.forEach(function(p, j){ if (j !== sec) p.forEach(function(x){ x.classList.add('st-gz'); }); });
    } else if (row){ row.hidden = true; row.dataset.imza = ''; }
    /* içi tamamen gizlenen kapları da gizle (boş çerçeve kalmasın) */
    kaplar.slice().reverse().forEach(function(k){ if (![].some.call(k.children, gor)) k.classList.add('st-gz'); });
    return sayfalar.length;
  };
})();
