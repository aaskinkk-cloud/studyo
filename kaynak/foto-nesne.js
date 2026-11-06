/* ===== STÜDYO · FOTOĞRAF EDİTÖRÜ: YAPAY ZEKÂ İLE NESNE KESME =====
   Araç çubuğunda "Kes": düzenlenmiş fotoğraf (Karanlık Oda'nın kendi tam çözünürlük çizimi) yapay zekâ ile ayrıştırılır (yz/motor.js, ISNet),
   sonra ayrı bir ekranda düzeltilir: dokunarak nesne seç, fırçayla koru / sil, geri al. Çıktı: saydam PNG ya da yeni arka planla JPG,
   istenirse kitaplığa yeni fotoğraf olarak eklenir. Karanlık Oda'nın kodu, kitaplığı ve WebGL önizlemesi değişmez (kesme ekranı yalnız 2B tuval). */
(function(){
  if (!window.STUDYO) return;
  var EN = 2048;   /* uzun kenar sınırı: telefonda bellek ve fırça hızı için */
  var K = null;    /* açık oturum */

  /* ---------- görünüm ---------- */
  var css = document.createElement('style'); css.id = 'st-kes-css';
  css.textContent = [
    '#stKes{position:fixed;inset:0;z-index:9000;background:#111;color:#f2f2f2;display:flex;flex-direction:column;font:14px/1.35 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-user-select:none;user-select:none}',
    '#stKes[hidden]{display:none}',
    '#stKes .ku{display:flex;align-items:center;gap:4px;padding:calc(env(safe-area-inset-top,0px) + 6px) 8px 6px}',
    '#stKes .ku b{flex:1;font-size:16px;text-align:center}',
    '#stKes button{font:inherit;color:inherit}',
    '#stKes .ib{background:none;border:0;width:42px;height:40px;display:grid;place-items:center;border-radius:10px;padding:0}',
    '#stKes .ib:disabled{opacity:.35}',
    '#stKes svg{width:23px;height:23px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}',
    '#stKes .kaydet{background:#e8762b;border:0;border-radius:999px;padding:9px 16px;font-weight:700;color:#fff}',
    '#stKes .sahne{flex:1;min-height:0;position:relative;overflow:hidden;touch-action:none;background-color:#bbb;background-image:linear-gradient(45deg,#888 25%,transparent 25%,transparent 75%,#888 75%),linear-gradient(45deg,#888 25%,transparent 25%,transparent 75%,#888 75%);background-size:22px 22px;background-position:0 0,11px 11px}',
    '#stKes .sahne.z-siyah{background:#000}#stKes .sahne.z-beyaz{background:#fff}#stKes .sahne.z-yesil{background:#18b85a}',
    '#stKes #stKesC{position:absolute;left:0;top:0;transform-origin:0 0;image-rendering:auto}',
    '#stKes .imlec{position:absolute;border:2px solid #fff;border-radius:50%;box-shadow:0 0 0 1px rgba(0,0,0,.5);pointer-events:none;display:none}',
    '#stKes .alt{background:#1c1c1c;padding:8px 10px calc(env(safe-area-inset-bottom,0px) + 8px);border-radius:16px 16px 0 0}',
    '#stKes .sira{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;margin:0 0 8px}',
    '#stKes .sira::-webkit-scrollbar{display:none}',
    '#stKes .sira button{flex:none;border:1.5px solid #3a3a3a;background:none;border-radius:999px;padding:7px 13px;font-size:13.5px;color:#bbb}',
    '#stKes .sira button.on{background:#f2f2f2;color:#111;border-color:#f2f2f2}',
    '#stKes .araclar{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:6px;margin:0 0 8px}',
    '#stKes .araclar button{background:#262626;border:1.5px solid transparent;border-radius:12px;padding:8px 4px;display:flex;flex-direction:column;align-items:center;gap:3px;font-size:12.5px}',
    '#stKes .araclar button small{color:#999;font-size:10.5px}',
    '#stKes .araclar button.on{border-color:#e8762b;background:#2e2219}',
    '#stKes .kr{display:flex;align-items:center;gap:10px;margin:0 0 8px;font-size:13px;color:#bbb}',
    '#stKes .kr input{flex:1;min-width:0;accent-color:#e8762b}',
    '#stKes .not{color:#999;font-size:12px;margin:0}',
    '#stKes .parca{display:flex;gap:8px;overflow-x:auto;scrollbar-width:none;margin:0 0 8px}',
    '#stKes .parca button{flex:none;width:64px;height:64px;border-radius:10px;border:1.5px solid #3a3a3a;padding:0;overflow:hidden;background:repeating-conic-gradient(#555 0 25%,#777 0 50%) 0 0/10px 10px}',
    '#stKes .parca img{width:100%;height:100%;object-fit:contain;display:block}',
    '#stKes .is{position:absolute;inset:0;display:grid;place-items:center;background:rgba(0,0,0,.55);z-index:3;padding:24px}',
    '#stKes .is[hidden]{display:none}',
    '#stKes .is>div{background:#222;border-radius:16px;padding:16px;width:min(360px,100%)}',
    '#stKes .is b{display:block;font-size:16px;margin:0 0 6px}',
    '#stKes .cb{height:6px;border-radius:3px;background:#3a3a3a;overflow:hidden;margin:10px 0 2px}',
    '#stKes .cb i{display:block;height:100%;width:0;background:#e8762b;transition:width .15s}',
    '#stKes .is button.tm{margin-top:12px;width:100%;border:0;border-radius:10px;padding:10px;font-weight:700;background:#f2f2f2;color:#111}',
    '#stKes .sayfa{position:absolute;left:0;right:0;bottom:0;z-index:4;max-height:100%;overflow-y:auto;overscroll-behavior:contain;background:#1c1c1c;border-radius:16px 16px 0 0;padding:12px 12px calc(env(safe-area-inset-bottom,0px) + 12px);box-shadow:0 -8px 24px rgba(0,0,0,.4)}',
    '#stKes .sayfa[hidden]{display:none}',
    '#stKes .sayfa h3{margin:0 0 8px;font-size:15px}',
    '#stKes .sayfa .bt{display:grid;gap:8px;margin-top:10px}',
    '#stKes .sayfa .bt button{border:0;border-radius:12px;padding:12px;font-weight:600;background:#2b2b2b;text-align:left}',
    '#stKes .sayfa .bt button.ana{background:#e8762b;color:#fff}',
    '#stKes .sayfa .bt button small{display:block;font-weight:400;color:#aaa;font-size:12px;margin-top:2px}',
    '#stKes .sayfa .bt button.ana small{color:#ffe3cf}',
    'html.st-is #editor #tabs .tab#stKesTab{order:3}',
    '#stKesTab svg{fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);
  var ic = function(p){ return '<svg viewBox="0 0 24 24" aria-hidden="true">' + p + '</svg>'; };
  var I = {
    kes:'<circle cx="6" cy="7" r="2.6"/><circle cx="6" cy="17" r="2.6"/><path d="M8.2 8.4L20 17M8.2 15.6L20 7"/>',
    kapat:'<path d="M6 6l12 12M18 6L6 18"/>', geri:'<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 010 12h-3"/>', ileri:'<path d="M15 14l5-5-5-5"/><path d="M20 9H10a6 6 0 000 12h3"/>',
    sec:'<path d="M5 3l12 7-5 1.5L10 17z"/><path d="M13 13l5 5"/>', koru:'<path d="M18 4l2 2-9 9-3 1 1-3z"/><path d="M4 20c1.5 0 3-1 3-3"/><path d="M12 4v3M12 4H9" opacity=".0"/>',
    kenar:'<rect x="4" y="4" width="16" height="16" rx="4" stroke-dasharray="3 3"/><rect x="8" y="8" width="8" height="8" rx="2"/>', goz:'<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    sil:'<path d="M7 20h10"/><path d="M5 14l7-7 5 5-7 7H7z"/>', sifir:'<path d="M4 4v6h6"/><path d="M4.5 15a8 8 0 102-8.5L4 10"/>'
  };

  var kok = document.createElement('div'); kok.id = 'stKes'; kok.hidden = true;
  kok.innerHTML = '<div class="ku"><button type="button" class="ib" data-a="kapat" aria-label="Kapat">' + ic(I.kapat) + '</button>'
    + '<button type="button" class="ib" data-a="geri" aria-label="Geri al" disabled>' + ic(I.geri) + '</button><button type="button" class="ib" data-a="ileri" aria-label="İleri al" disabled>' + ic(I.ileri) + '</button>'
    + '<b>Kes</b><button type="button" class="ib" data-a="kars" aria-label="Basılı tut: orijinal">' + ic(I.goz) + '</button><button type="button" class="ib" data-a="sifir" aria-label="Yapay zekâ sonucuna dön">' + ic(I.sifir) + '</button><button type="button" class="kaydet" data-a="bitti">Bitti</button></div>'
    + '<div class="sahne" id="stKesS"><canvas id="stKesC"></canvas><div class="imlec" id="stKesI"></div>'
    + '<div class="is" id="stKesIs" hidden><div><b id="stKesIsB">Nesne kesiliyor</b><div id="stKesIsM">Hazırlanıyor…</div><div class="cb"><i id="stKesIsC"></i></div><button type="button" class="tm" id="stKesIsT" hidden>Tamam</button></div></div>'
    + '<div class="sayfa" id="stKesSf" hidden></div></div>'
    + '<div class="alt"><div class="araclar">'
    + '<button type="button" data-m="sec" class="on">' + ic(I.sec) + 'Dokun, seç<small>o nesneyi tut</small></button>'
    + '<button type="button" data-m="koru">' + ic(I.koru) + 'Koru<small>fırçayla geri getir</small></button>'
    + '<button type="button" data-m="sil">' + ic(I.sil) + 'Sil<small>fırçayla kaldır</small></button>'
    + '<button type="button" data-m="kenar">' + ic(I.kenar) + 'Kenar · cam<small>daralt, saydam</small></button></div>'
    + '<div id="stKesKn" hidden><div class="kr"><span style="width:76px">Daralt ⟷ Genişlet</span><input type="range" id="stKesKk" min="-10" max="10" step="1" value="0"><span id="stKesKkO" style="width:34px;text-align:right">0</span></div>'
    + '<div class="kr"><span style="width:76px">Yumuşaklık</span><input type="range" id="stKesKy" min="0" max="10" step="1" value="0"><span id="stKesKyO" style="width:34px;text-align:right">0</span></div>'
    + '<div class="sira" style="margin:2px 0 6px"><button type="button" data-g="cam">Saydam nesne (cam, bardak)</button></div>'
    + '<div class="kr" id="stKesCamR" hidden><span style="width:76px">Saydamlık</span><input type="range" id="stKesCam" min="10" max="100" step="5" value="60"><span id="stKesCamO" style="width:34px;text-align:right">60</span></div></div>'
    + '<div class="kr" id="stKesFr" hidden><span>Fırça</span><input type="range" id="stKesBoy" min="1" max="20" step="0.5" value="5"><span id="stKesBoyO">5%</span></div>'
    + '<div class="sira" id="stKesZ"><button type="button" data-z="dama" class="on">Dama</button><button type="button" data-z="siyah">Siyah</button><button type="button" data-z="beyaz">Beyaz</button><button type="button" data-z="yesil">Yeşil</button><button type="button" data-g="maske">Maskeyi göster</button><button type="button" data-g="kenar" class="on">Yumuşak fırça</button><button type="button" data-g="temiz" class="on">Kenar rengini temizle</button></div>'
    + '<p class="not" id="stKesNot">Tutmak istediğin nesneye dokun; diğerleri silinir. Yakınlaştırmak için iki parmak.</p></div>';
  document.body.appendChild(kok);
  var $ = function(id){ return document.getElementById(id); };
  var cv = $('stKesC'), sahne = $('stKesS');

  /* ---------- iş göstergesi ---------- */
  function isGoster(b, m, o){ $('stKesIs').hidden = false; $('stKesIsB').textContent = b; $('stKesIsM').textContent = m; $('stKesIsT').hidden = true; if (o != null) $('stKesIsC').style.width = Math.round(o * 100) + '%'; }
  function isBitir(b, m){ $('stKesIsB').textContent = b; $('stKesIsM').textContent = m; $('stKesIsT').hidden = false; }
  $('stKesIsT').onclick = function(){ $('stKesIs').hidden = true; if (!K || !K.alfa) kapat(); };

  /* ---------- aç: düzenlenmiş fotoğraf → yapay zekâ ---------- */
  var motorSoz = null;
  function motorYukle(){ if (window.stYZ) return Promise.resolve(window.stYZ); if (motorSoz) return motorSoz;
    motorSoz = new Promise(function(ok, no){ var s = document.createElement('script');
      s.src = /pages\.dev$/.test(location.hostname) ? 'https://aaskinkk-cloud.github.io/studyo/yz/motor.js' : new URL('../yz/motor.js', location.href).href;
      s.onload = function(){ window.stYZ ? ok(window.stYZ) : no(new Error('motor yok')); }; s.onerror = function(){ motorSoz = null; no(new Error('Yapay zekâ motoru yüklenemedi. İnternet bağlantısını kontrol et.')); };
      document.head.appendChild(s); }); return motorSoz; }
  async function ac(){
    if (K) return; if (typeof ED === 'undefined' || !ED.rec){ try { toast('Önce bir fotoğraf aç.'); } catch(e){} return; }
    K = {mod:'sec', yigin:[], ileri:[], zemin:'dama', maske:false, yumusak:true, boy:5, ad:String(ED.rec.name || 'foto').replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|]+/g, '_')};
    kok.hidden = false; arayuz();
    isGoster('Nesne kesiliyor', 'Düzenlenmiş fotoğraf hazırlanıyor…', 0.01);
    var pipe = null;
    try {
      /* Karanlık Oda'nın kendi tam çözünürlük çizimi (bütün düzenlemeler dahil); grafik birimi işi bitince serbest bırakılır */
      pipe = new Pipeline(document.createElement('canvas'));
      var out = await renderFull(ED.rec.blob, ED.state, null, pipe, function(nw, nh){ var q = Math.min(1, EN / Math.max(nw, nh)); return [Math.round(nw * q), Math.round(nh * q)]; }); try { pipe.destroy(); } catch(e){} pipe = null;
      var k = Math.min(1, EN / Math.max(out.width, out.height)), W = Math.round(out.width * k), H = Math.round(out.height * k);
      var src = document.createElement('canvas'); src.width = W; src.height = H; var sx = src.getContext('2d', {willReadFrequently:true}); sx.imageSmoothingQuality = 'high';
      sx.fillStyle = '#fff'; sx.fillRect(0, 0, W, H); sx.drawImage(out, 0, 0, W, H); out.width = 0;
      if (!K) return;
      var blob = await new Promise(function(r){ src.toBlob(r, 'image/png'); });
      var YZ = await motorYukle();
      var r = await YZ.kes(blob, function(t, o){ isGoster('Nesne kesiliyor', t, o); }, {ham:true, enBoy:EN});
      if (!K) return;
      if (r.W !== W || r.H !== H) throw new Error('Boyut uyuşmadı.');
      K.W = W; K.H = H; K.src = src; K.px = sx.getImageData(0, 0, W, H).data; K.ai = r.alfa; K.alfa = new Uint8ClampedArray(r.alfa);
      K.comp = document.createElement('canvas'); K.comp.width = W; K.comp.height = H; K.cx = K.comp.getContext('2d'); K.img = K.cx.createImageData(W, H);
      cv.width = W; cv.height = H; K.cg = cv.getContext('2d');
      yenidenCiz(0, 0, W, H); sigdir();
      isBitir('Kesildi', (r.motor === 'grafik birimi' ? 'Grafik birimiyle ' : 'İşlemciyle ') + (r.ms / 1000).toFixed(1).replace('.', ',') + ' sn' + (r.indi ? ' (model indirme dahil)' : '') + '. Kenarları fırçayla düzeltebilirsin.');
      setTimeout(function(){ if ($('stKesIsB').textContent === 'Kesildi') $('stKesIs').hidden = true; }, 2200);
    } catch(e){ console.error(e); if (pipe) try { pipe.destroy(); } catch(_){} isBitir('Olmadı', String(e && e.message || e)); }
  }
  function kapat(){ if (!K) return; K = null; kok.hidden = true; $('stKesSf').hidden = true; $('stKesIs').hidden = true; cv.width = 1; cv.height = 1; }
  /* Android geri tuşu: önce kesme ekranı kapanır */
  (function geriSar(n){ var k = window.studyoKopru; if (!k){ if (n < 40) setTimeout(function(){ geriSar(n + 1); }, 250); return; }
    var eski = k.geri; k.geri = function(){ if (K){ if (!$('stKesSf').hidden){ $('stKesSf').hidden = true; return true; } kapat(); return true; } return eski.apply(this, arguments); }; })(0);

  /* ---------- çizim ---------- */
  function yenidenCiz(x0, y0, x1, y1){
    if (!K || !K.alfa) return; K.surum = (K.surum || 0) + 1; var W = K.W, d = K.img.data, p = K.px, a = K.alfa, m = K.maske;
    x0 = Math.max(0, x0 | 0); y0 = Math.max(0, y0 | 0); x1 = Math.min(K.W, Math.ceil(x1)); y1 = Math.min(K.H, Math.ceil(y1)); if (x1 <= x0 || y1 <= y0) return;
    for (var y = y0; y < y1; y++){ for (var x = x0, i = y * W + x, j = i * 4; x < x1; x++, i++, j += 4){
      if (m){ d[j] = d[j + 1] = d[j + 2] = a[i]; d[j + 3] = 255; } else { d[j] = p[j]; d[j + 1] = p[j + 1]; d[j + 2] = p[j + 2]; d[j + 3] = a[i]; } } }
    K.cg.putImageData(K.img, 0, 0, x0, y0, x1 - x0, y1 - y0); }
  /* görünüm: sığdır / yakınlaştır */
  var V = {s:1, x:0, y:0};
  function uygula(){ cv.style.transform = 'translate(' + V.x + 'px,' + V.y + 'px) scale(' + V.s + ')'; }
  function sigdir(){ if (!K || !K.W) return; var r = sahne.getBoundingClientRect(), s = Math.min(r.width / K.W, r.height / K.H) * 0.96; V.s = s; V.x = (r.width - K.W * s) / 2; V.y = (r.height - K.H * s) / 2; uygula(); }
  addEventListener('resize', function(){ if (K && K.W) sigdir(); });
  function resimNokta(cx, cy){ var r = sahne.getBoundingClientRect(); return {x:(cx - r.left - V.x) / V.s, y:(cy - r.top - V.y) / V.s}; }

  /* ---------- geri al ---------- */
  function kayitAl(){ if (!K || !K.alfa) return; K.yigin.push(new Uint8ClampedArray(K.alfa)); if (K.yigin.length > 12) K.yigin.shift(); K.ileri = []; dugmeler(); }
  function dugmeler(){ var g = kok.querySelector('[data-a=geri]'), i = kok.querySelector('[data-a=ileri]'); if (g) g.disabled = !(K && K.yigin.length); if (i) i.disabled = !(K && K.ileri.length); }

  /* ---------- dokun, seç: o nesnenin bağlı alanı kalır, diğerleri silinir ---------- */
  function sec(p){
    var W = K.W, H = K.H, a = K.alfa, x = Math.round(p.x), y = Math.round(p.y); if (x < 0 || y < 0 || x >= W || y >= H) return;
    /* dokunulan yer boşsa yakındaki en güçlü noktayı ara (parmak hassas değil) */
    var R = Math.round(Math.max(W, H) * 0.03), bx = -1, by = -1, en = 127;
    for (var yy = Math.max(0, y - R); yy <= Math.min(H - 1, y + R); yy += 2) for (var xx = Math.max(0, x - R); xx <= Math.min(W - 1, x + R); xx += 2){ var v = a[yy * W + xx]; if (v > en){ en = v; bx = xx; by = yy; } }
    if (bx < 0){ not('Dokunduğun yerde nesne yok. Silinmiş bir şeyi geri getirmek için Koru fırçasını kullan.'); return; }
    kayitAl();
    var esik = 40, gor = new Uint8Array(W * H), yig = new Int32Array(W * H), n = 0; yig[n++] = by * W + bx; gor[by * W + bx] = 1;
    while (n){ var i = yig[--n], ix = i % W, iy = (i - ix) / W;
      if (ix > 0 && !gor[i - 1] && a[i - 1] > esik){ gor[i - 1] = 1; yig[n++] = i - 1; } if (ix < W - 1 && !gor[i + 1] && a[i + 1] > esik){ gor[i + 1] = 1; yig[n++] = i + 1; }
      if (iy > 0 && !gor[i - W] && a[i - W] > esik){ gor[i - W] = 1; yig[n++] = i - W; } if (iy < H - 1 && !gor[i + W] && a[i + W] > esik){ gor[i + W] = 1; yig[n++] = i + W; } }
    /* seçilen alanın 6 px çevresi korunur (yumuşak kenar kaybolmasın) */
    var gen = Math.max(2, Math.round(Math.max(W, H) / 340)), tut = new Uint8Array(gor);
    for (var g = 0; g < gen; g++){ var yeni = new Uint8Array(tut); for (i = 0; i < W * H; i++) if (tut[i]){ if (i % W > 0) yeni[i - 1] = 1; if (i % W < W - 1) yeni[i + 1] = 1; if (i >= W) yeni[i - W] = 1; if (i < W * H - W) yeni[i + W] = 1; } tut = yeni; }
    var sayi = 0; for (i = 0; i < W * H; i++){ if (!tut[i] && a[i]){ a[i] = 0; sayi++; } }
    yenidenCiz(0, 0, W, H); not(sayi ? 'Seçilen nesne kaldı, diğerleri silindi. Eksik ya da fazla yeri fırçayla düzelt.' : 'Fotoğrafta yalnız bu nesne vardı.'); }

  /* ---------- fırça ---------- */
  var F = null;
  function boyPx(){ return Math.max(K.W, K.H) * K.boy / 100 / 2; }
  function firca(p){
    var r = boyPx(), W = K.W, H = K.H, a = K.alfa, ai = K.ai, koru = K.mod === 'koru', ic0 = K.yumusak ? 0.55 : 0.97;
    var x0 = Math.max(0, Math.floor(p.x - r)), x1 = Math.min(W - 1, Math.ceil(p.x + r)), y0 = Math.max(0, Math.floor(p.y - r)), y1 = Math.min(H - 1, Math.ceil(p.y + r)), r2 = r * r;
    for (var y = y0; y <= y1; y++) for (var x = x0; x <= x1; x++){ var dx = x - p.x, dy = y - p.y, d2 = dx * dx + dy * dy; if (d2 > r2) continue;
      var d = Math.sqrt(d2) / r, w = d <= ic0 ? 1 : 1 - (d - ic0) / (1 - ic0), i = y * W + x, v = a[i];
      if (koru){ var hedef = 255; v = v + (hedef - v) * w; if (w >= 1) v = 255; } else { v = v * (1 - w); if (w >= 1) v = 0; }
      a[i] = v; }
    yenidenCiz(x0, y0, x1 + 1, y1 + 1); }
  function cizgi(a, b){ var r = boyPx(), adim = Math.max(1, r * 0.3), d = Math.hypot(b.x - a.x, b.y - a.y), n = Math.ceil(d / adim);
    for (var k = 1; k <= n; k++) firca({x:a.x + (b.x - a.x) * k / n, y:a.y + (b.y - a.y) * k / n}); }
  var imlec = $('stKesI');
  function imlecGoster(cx, cy){ if (!K || K.mod === 'sec'){ imlec.style.display = 'none'; return; } var r = sahne.getBoundingClientRect(), d = boyPx() * 2 * V.s;
    imlec.style.display = 'block'; imlec.style.width = imlec.style.height = d + 'px'; imlec.style.left = (cx - r.left - d / 2) + 'px'; imlec.style.top = (cy - r.top - d / 2) + 'px'; }

  /* ---------- dokunma: tek parmak araç, iki parmak yakınlaştır / kaydır ---------- */
  var P = {}, iki = null, tek = null;
  sahne.addEventListener('pointerdown', function(e){
    if (!K || !K.alfa || e.target.closest('.is,.sayfa')) return; e.preventDefault(); try { sahne.setPointerCapture(e.pointerId); } catch(_){}
    P[e.pointerId] = {x:e.clientX, y:e.clientY}; var ks = Object.keys(P);
    if (ks.length === 2){ if (tek && tek.boyadi){ /* fırça yeni başlamıştı: geri al */ if (K.yigin.length){ K.alfa.set(K.yigin.pop()); yenidenCiz(0, 0, K.W, K.H); dugmeler(); } } tek = null; imlec.style.display = 'none';
      var a = P[ks[0]], b = P[ks[1]]; iki = {d:Math.hypot(b.x - a.x, b.y - a.y) || 1, mx:(a.x + b.x) / 2, my:(a.y + b.y) / 2, s:V.s, x:V.x, y:V.y}; return; }
    if (ks.length > 2) return;
    tek = {p:resimNokta(e.clientX, e.clientY), cx:e.clientX, cy:e.clientY, t:Date.now(), boyadi:false};
    if (K.mod !== 'sec'){ kayitAl(); tek.boyadi = true; firca(tek.p); imlecGoster(e.clientX, e.clientY); }
  });
  sahne.addEventListener('pointermove', function(e){
    if (!(e.pointerId in P)) return; P[e.pointerId] = {x:e.clientX, y:e.clientY}; var ks = Object.keys(P);
    if (iki && ks.length >= 2){ var a = P[ks[0]], b = P[ks[1]], r = sahne.getBoundingClientRect(), d = Math.hypot(b.x - a.x, b.y - a.y), s = Math.max(0.05, Math.min(30, iki.s * d / iki.d));
      var mx = (a.x + b.x) / 2 - r.left, my = (a.y + b.y) / 2 - r.top, ox = iki.mx - r.left, oy = iki.my - r.top;
      V.s = s; V.x = mx - (ox - iki.x) * s / iki.s; V.y = my - (oy - iki.y) * s / iki.s; uygula(); return; }
    if (tek && K.mod !== 'sec'){ var p = resimNokta(e.clientX, e.clientY); cizgi(tek.p, p); tek.p = p; imlecGoster(e.clientX, e.clientY); }
  });
  function birak(e){ if (!(e.pointerId in P)) return; delete P[e.pointerId];
    if (iki){ if (Object.keys(P).length < 2) iki = null; tek = null; return; }
    if (tek && K && K.mod === 'sec' && Math.hypot(e.clientX - tek.cx, e.clientY - tek.cy) < 12 && Date.now() - tek.t < 600) sec(tek.p);
    tek = null; imlec.style.display = 'none'; }
  sahne.addEventListener('pointerup', birak); sahne.addEventListener('pointercancel', birak);

  /* ---------- kenar: daralt / genişlet / yumuşat (ortak motor: stYZ.incelt) ---------- */
  function kenarBasla(kayitsiz){ if (!K || !K.alfa) return; if (!kayitsiz) kayitAl(); K.kb = new Uint8ClampedArray(K.alfa); $('stKesKk').value = 0; $('stKesKy').value = 0; $('stKesKkO').textContent = '0'; $('stKesKyO').textContent = '0'; }
  var knT = 0;
  function kenarUygula(){ clearTimeout(knT); knT = setTimeout(function(){ if (!K || !K.kb || !window.stYZ) return; var o = Math.max(K.W, K.H) / 1000, kk = +$('stKesKk').value, ky = +$('stKesKy').value;
    K.alfa.set(kk || ky ? stYZ.incelt(K.kb, K.W, K.H, Math.round(kk * o * 1.5), Math.round(ky * o)) : K.kb); yenidenCiz(0, 0, K.W, K.H); }, 60); }
  $('stKesKk').addEventListener('input', function(){ $('stKesKkO').textContent = this.value; kenarUygula(); });
  $('stKesKy').addEventListener('input', function(){ $('stKesKyO').textContent = this.value; kenarUygula(); });
  /* saydam nesne (cam): camın içinden görünen eski arka plan saydamlaşır, renk ayrıştırılır (ortak motor: stYZ.saydamlastir) */
  function camAyarla(ac){ if (!K || !K.alfa || !window.stYZ) return;
    if (ac && !K.cam){ K.camB = new Uint8ClampedArray(K.alfa); K.px0 = K.px; K.cam = true; camUygula(); not('Cam modu: nesnenin içinden görünen arka plan saydamlaştı. Kaydetmeden önce "Saydamlık" ile ayarla; yeni arka plan camın içinden görünür.'); }
    else if (!ac && K.cam){ K.alfa.set(K.camB); K.px = K.px0; K.cam = false; K.nesS = -1; yenidenCiz(0, 0, K.W, K.H); not('Cam modu kapatıldı.'); }
    var cb = kok.querySelector('[data-g=cam]'); if (cb) cb.classList.toggle('on', !!K.cam); $('stKesCamR').hidden = !K.cam; }
  var camT = 0;
  function camUygula(){ clearTimeout(camT); camT = setTimeout(function(){ if (!K || !K.cam) return; var r = stYZ.saydamlastir(K.px0, K.camB, K.W, K.H, +$('stKesCam').value, true);
    K.alfa.set(r.alfa); K.px = r.px; K.nesS = -1; if (K.kb){ K.kb = new Uint8ClampedArray(K.alfa); $('stKesKk').value = 0; $('stKesKy').value = 0; $('stKesKkO').textContent = '0'; $('stKesKyO').textContent = '0'; } yenidenCiz(0, 0, K.W, K.H); }, 80); }
  $('stKesCam').addEventListener('input', function(){ $('stKesCamO').textContent = this.value; camUygula(); });
  /* karşılaştır: göz düğmesine basılı tutunca orijinal fotoğraf */
  (function(){ var gb = kok.querySelector('[data-a=kars]'), on = false;
    var bas = function(e){ if (!K || !K.alfa) return; e.preventDefault(); on = true; K.cg.clearRect(0, 0, K.W, K.H); K.cg.drawImage(K.src, 0, 0); };
    var bit = function(){ if (!on) return; on = false; if (K && K.alfa) yenidenCiz(0, 0, K.W, K.H); };
    gb.addEventListener('pointerdown', bas); gb.addEventListener('pointerup', bit); gb.addEventListener('pointerleave', bit); gb.addEventListener('pointercancel', bit);
    gb.addEventListener('contextmenu', function(e){ e.preventDefault(); }); })();
  /* ---------- düğmeler ---------- */
  function not(m){ $('stKesNot').textContent = m; }
  function arayuz(){ kok.querySelectorAll('[data-m]').forEach(function(b){ b.classList.toggle('on', K && b.dataset.m === K.mod); }); $('stKesFr').hidden = !K || !(K.mod === 'koru' || K.mod === 'sil'); $('stKesKn').hidden = !K || K.mod !== 'kenar';
    kok.querySelectorAll('[data-z]').forEach(function(b){ b.classList.toggle('on', K && b.dataset.z === K.zemin); }); sahne.className = 'sahne' + (K && K.zemin !== 'dama' ? ' z-' + K.zemin : '');
    var mk = kok.querySelector('[data-g=maske]'), kn = kok.querySelector('[data-g=kenar]'); if (mk) mk.classList.toggle('on', !!(K && K.maske)); if (kn) kn.classList.toggle('on', !!(K && K.yumusak)); var tz = kok.querySelector('[data-g=temiz]'); if (tz) tz.classList.toggle('on', !!(K && K.temiz !== false)); var cm = kok.querySelector('[data-g=cam]'); if (cm) cm.classList.toggle('on', !!(K && K.cam)); $('stKesCamR').hidden = !(K && K.cam); dugmeler(); }
  kok.addEventListener('click', function(e){
    var b = e.target.closest('button'); if (!b || !K) return;
    if (b.dataset.m){ K.mod = b.dataset.m; if (K.mod === 'kenar') kenarBasla(); else K.kb = null; arayuz(); not(K.mod === 'kenar' ? 'Kenarı daralt (arka plan kalıntısı) ya da genişlet; yumuşaklığı ayarla. Karşılaştırmak için üstteki göze basılı tut.' : K.mod === 'sec' ? 'Tutmak istediğin nesneye dokun; diğerleri silinir.' : K.mod === 'koru' ? 'Silinen yerin üstünden geç: geri gelir. İnce iş için iki parmakla yakınlaştır.' : 'Kalmasını istemediğin yerin üstünden geç. İnce iş için iki parmakla yakınlaştır.'); return; }
    if (b.dataset.z){ K.zemin = b.dataset.z; arayuz(); return; }
    if (b.dataset.g === 'maske'){ K.maske = !K.maske; arayuz(); yenidenCiz(0, 0, K.W, K.H); return; }
    if (b.dataset.g === 'kenar'){ K.yumusak = !K.yumusak; arayuz(); return; }
    if (b.dataset.g === 'cam'){ camAyarla(!K.cam); return; }
    if (b.dataset.g === 'temiz'){ K.temiz = K.temiz === false; K.nesS = -1; arayuz(); not(K.temiz !== false ? 'Kenar rengi temizlenecek: eski arka planın rengi kenarda hale bırakmaz (kaydederken uygulanır).' : 'Kenar rengi olduğu gibi kalacak.'); return; }
    var a = b.dataset.a; if (!a) return;
    if (a === 'kapat'){ kapat(); return; }
    if (!K.alfa) return;
    if ((a === 'geri' || a === 'ileri' || a === 'sifir') && K.cam){ K.px = K.px0; K.cam = false; var cb0 = kok.querySelector('[data-g=cam]'); if (cb0) cb0.classList.remove('on'); $('stKesCamR').hidden = true; }
    if (a === 'geri' && K.yigin.length){ K.ileri.push(new Uint8ClampedArray(K.alfa)); K.alfa.set(K.yigin.pop()); yenidenCiz(0, 0, K.W, K.H); dugmeler(); }
    else if (a === 'ileri' && K.ileri.length){ K.yigin.push(new Uint8ClampedArray(K.alfa)); K.alfa.set(K.ileri.pop()); yenidenCiz(0, 0, K.W, K.H); dugmeler(); }
    else if (a === 'sifir'){ kayitAl(); K.alfa.set(K.ai); yenidenCiz(0, 0, K.W, K.H); not('Yapay zekânın ilk sonucuna dönüldü.'); }
    if ((a === 'geri' || a === 'ileri' || a === 'sifir') && K.mod === 'kenar') kenarBasla(true);
    else if (a === 'bitti') sayfaAc();
  });
  $('stKesBoy').addEventListener('input', function(){ if (!K) return; K.boy = +this.value; $('stKesBoyO').textContent = this.value + '%'; });

  /* ---------- çıktı ---------- */
  var AP = [['saydam','Saydam'],['foto','Fotoğraf'],['beyaz','Beyaz'],['siyah','Siyah'],['bulanik','Bulanık'],['renk','Renk']];
  /* arkaya fotoğraf: seçilen fotoğraf tuvalin kendisi olur (uzun kenar en çok 2048); nesne sürüklenip büyütülerek yerleştirilir */
  var afGir = document.createElement('input'); afGir.type = 'file'; afGir.accept = 'image/*'; afGir.hidden = true; kok.appendChild(afGir);
  afGir.addEventListener('change', async function(){ var f = afGir.files && afGir.files[0]; afGir.value = ''; if (!f || !K) return;
    try { var b = await createImageBitmap(f, {imageOrientation:'from-image'}), q = Math.min(1, EN / Math.max(b.width, b.height)), c = document.createElement('canvas');
      c.width = Math.round(b.width * q); c.height = Math.round(b.height * q); var x = c.getContext('2d'); x.imageSmoothingQuality = 'high'; x.drawImage(b, 0, 0, c.width, c.height); try { b.close(); } catch(e){}
      K.af = c; K.fs = 1; K.fx = 0; K.fy = 0; K.ap = 'foto'; sayfaAc(); } catch(e){ hata(e); } });
  function sayfaAc(){ var sf = $('stKesSf'); K.ap = K.ap || 'saydam'; if (K.ap === 'foto' && !K.af) K.ap = 'saydam'; K.kirp = K.kirp == null ? true : K.kirp; K.renk = K.renk || '#1e6fd9';
    var foto = K.ap === 'foto';
    sf.innerHTML = '<h3>Kaydet</h3><div class="sira" id="stKesAp">' + AP.map(function(x){ return '<button type="button" data-ap="' + x[0] + '" class="' + (K.ap === x[0] ? 'on' : '') + '">' + (x[0] === 'foto' && K.af ? 'Fotoğraf · değiştir' : x[1]) + '</button>'; }).join('')
      + (foto ? '' : '<button type="button" data-kp="1" class="' + (K.kirp ? 'on' : '') + '">Boşlukları kırp</button>') + '</div>'
      + '<div style="display:grid;place-items:center;margin:0 0 8px"><canvas id="stKesOn" style="max-width:100%;max-height:28vh;border-radius:8px;touch-action:none;background-color:#bbb;background-image:linear-gradient(45deg,#888 25%,transparent 25%,transparent 75%,#888 75%),linear-gradient(45deg,#888 25%,transparent 25%,transparent 75%,#888 75%);background-size:16px 16px;background-position:0 0,8px 8px"></canvas></div>'
      + (foto ? '<div class="kr"><span>Nesne boyu</span><input type="range" id="stKesFs" min="20" max="250" step="1" value="' + Math.round(K.fs * 100) + '"><span id="stKesFsO">' + Math.round(K.fs * 100) + '%</span></div><p class="not" style="margin:-4px 0 6px">Önizlemede nesneyi sürükle; iki parmakla büyüt.</p>' : '')
      + '<div class="kr" id="stKesRk"' + (K.ap === 'renk' ? '' : ' hidden') + '><span>Arka plan rengi</span><input type="color" id="stKesRenk" value="' + K.renk + '" style="flex:none;width:56px;height:34px;border:0;background:none"></div>'
      + '<div class="bt"><button type="button" class="ana" data-c="indir">' + (K.ap === 'saydam' ? 'Saydam PNG indir' : 'JPG indir') + '<small>' + (K.ap === 'saydam' ? 'Arka planı boş; başka yerde üstüne koymak için' : foto ? 'Kesilen nesne seçtiğin fotoğrafın üstünde' : 'Kesilen nesne seçtiğin arka planla') + '</small></button>'
      + '<button type="button" data-c="kitap">Kitaplığa yeni fotoğraf olarak ekle<small>' + (K.ap === 'saydam' ? 'Saydam yerler beyaz olur; ' : '') + 'sonra Karanlık Oda araçlarıyla düzenlemeye devam et</small></button>'
      + '<button type="button" data-c="don">Düzeltmeye dön</button></div>';
    /* ayrı nesneler: fotoğrafta birden çok nesne kaldıysa her biri ayrı saydam PNG */
    var PL = parcaListe();
    if (PL.length > 1) sf.querySelector('.bt').insertAdjacentHTML('beforebegin', '<h3 style="margin-top:4px">Ayrı nesneler (' + PL.length + ')</h3><div class="parca">'
      + PL.map(function(x, i){ return '<button type="button" data-pr="' + i + '" aria-label="' + (i + 1) + '. nesneyi PNG indir"><img alt="" src="' + x.kucuk + '"></button>'; }).join('') + '</div>'
      + '<div class="bt" style="margin:0 0 4px"><button type="button" data-c="parcalar">Her nesneyi ayrı saydam PNG indir (' + PL.length + ')<small>Tek tek indirmek için küçük resme dokun</small></button></div>');
    sf.hidden = false; onizle(); }
  function parcaListe(){ if (K.plS === K.surum && K.pl) return K.pl; var W = K.W, H = K.H, P = stYZ.parcalar(K.alfa, W, H, {oran:0.01, enCok:8});
    K.pP = P; K.pl = P.liste.length > 1 ? P.liste.map(function(p, i){ var c = parcaTuval(i, P), k = c.getContext('2d'), t = document.createElement('canvas'), q = 128 / Math.max(c.width, c.height);
      t.width = Math.max(1, Math.round(c.width * q)); t.height = Math.max(1, Math.round(c.height * q)); t.getContext('2d').drawImage(c, 0, 0, t.width, t.height); var u = t.toDataURL('image/png'); c.width = 0; return {kucuk:u}; }) : [];
    K.plS = K.surum; return K.pl; }
  function parcaTuval(i, P){ P = P || K.pP; var M = stYZ.parcaMaske(K.alfa, P, P.liste[i]), nes = nesneTuval(), c = document.createElement('canvas'); c.width = M.w; c.height = M.h;
    var g = c.getContext('2d', {willReadFrequently:true}); g.drawImage(nes, M.x, M.y, M.w, M.h, 0, 0, M.w, M.h); var im = g.getImageData(0, 0, M.w, M.h);
    for (var z = 0; z < M.w * M.h; z++) im.data[z * 4 + 3] = M.alfa[z]; g.putImageData(im, 0, 0); return c; }
  async function parcaIndir(i){ var c = parcaTuval(i), b = await new Promise(function(r){ c.toBlob(r, 'image/png'); }); c.width = 0; indir(b, K.ad + '-nesne-' + (i + 1) + '.png'); }
  /* küçük önizleme (çıktının aynısı, ölçekli) */
  var onzT = 0;
  function onizle(){ cancelAnimationFrame(onzT); onzT = requestAnimationFrame(function(){ var c = $('stKesOn'); if (!c || !K) return;
    try { var t = ciktiTuval(false, 360); c.width = t.width; c.height = t.height; c.getContext('2d').drawImage(t, 0, 0); t.width = 0; } catch(e){ c.width = 1; c.height = 1; } }); }
  $('stKesSf').addEventListener('click', function(e){ var b = e.target.closest('button'); if (!b || !K) return;
    if (b.dataset.ap){ if (b.dataset.ap === 'foto' && (!K.af || K.ap === 'foto')){ afGir.click(); return; } K.ap = b.dataset.ap; sayfaAc(); return; }
    if (b.dataset.kp){ K.kirp = !K.kirp; sayfaAc(); return; }
    if (b.dataset.pr != null){ parcaIndir(+b.dataset.pr).catch(hata); return; }
    if (b.dataset.c === 'parcalar'){ (async function(){ for (var i = 0; i < K.pl.length; i++){ await parcaIndir(i); await new Promise(function(r){ setTimeout(r, 400); }); } })().catch(hata); return; }
    if (b.dataset.c === 'don'){ $('stKesSf').hidden = true; return; }
    if (b.dataset.c === 'indir') cikti().then(function(o){ indir(o.blob, K.ad + (K.ap === 'saydam' ? '-kesik.png' : '-arka-plan.jpg')); }).catch(hata);
    if (b.dataset.c === 'kitap') cikti(true).then(function(o){ kitapligaEkle(o.blob); }).catch(hata); });
  $('stKesSf').addEventListener('input', function(e){ if (!K) return;
    if (e.target.id === 'stKesRenk'){ K.renk = e.target.value; onizle(); }
    if (e.target.id === 'stKesFs'){ K.fs = +e.target.value / 100; $('stKesFsO').textContent = e.target.value + '%'; onizle(); } });
  /* önizlemede sürükle / iki parmakla büyüt (yalnız arkaya fotoğrafta) */
  var OP = {}, oiki = null, otek = null;
  $('stKesSf').addEventListener('pointerdown', function(e){ if (!K || K.ap !== 'foto' || e.target.id !== 'stKesOn') return; e.preventDefault(); try { e.target.setPointerCapture(e.pointerId); } catch(_){}
    OP[e.pointerId] = {x:e.clientX, y:e.clientY}; var ks = Object.keys(OP);
    if (ks.length === 2){ var a = OP[ks[0]], b = OP[ks[1]]; oiki = {d:Math.hypot(b.x - a.x, b.y - a.y) || 1, fs:K.fs}; otek = null; } else { otek = {x:e.clientX, y:e.clientY, fx:K.fx, fy:K.fy}; } });
  $('stKesSf').addEventListener('pointermove', function(e){ if (!(e.pointerId in OP)) return; OP[e.pointerId] = {x:e.clientX, y:e.clientY}; var c = $('stKesOn'), r = c.getBoundingClientRect(), ks = Object.keys(OP);
    if (oiki && ks.length >= 2){ var a = OP[ks[0]], b = OP[ks[1]]; K.fs = Math.max(0.2, Math.min(2.5, oiki.fs * Math.hypot(b.x - a.x, b.y - a.y) / oiki.d)); var sl = $('stKesFs'); if (sl){ sl.value = Math.round(K.fs * 100); $('stKesFsO').textContent = sl.value + '%'; } }
    else if (otek){ K.fx = otek.fx + (e.clientX - otek.x) / r.width; K.fy = otek.fy + (e.clientY - otek.y) / r.height; }
    onizle(); });
  function obirak(e){ if (!(e.pointerId in OP)) return; delete OP[e.pointerId]; if (Object.keys(OP).length < 2) oiki = null; if (!Object.keys(OP).length) otek = null; }
  $('stKesSf').addEventListener('pointerup', obirak); $('stKesSf').addEventListener('pointercancel', obirak);
  function hata(e){ console.error(e); isGoster('Olmadı', String(e && e.message || e), 0); $('stKesIsT').hidden = false; }
  /* nesne katmanı (piksel + alfa), alfa değişene kadar önbellekte */
  function nesneTuval(){ if (K.nesC && K.nesS === K.surum) return K.nesC; var W = K.W, H = K.H, a = K.alfa;
    var nes = K.nesC || document.createElement('canvas'); nes.width = W; nes.height = H; var nx = nes.getContext('2d'), im = nx.createImageData(W, H), d = im.data, p = K.temiz !== false && window.stYZ ? stYZ.renkTemizle(K.px, a, W, H) : K.px;
    for (var i = 0, j = 0; i < W * H; i++, j += 4){ d[j] = p[j]; d[j + 1] = p[j + 1]; d[j + 2] = p[j + 2]; d[j + 3] = a[i]; } nx.putImageData(im, 0, 0);
    K.nesC = nes; K.nesS = K.surum; return nes; }
  function altaDeger(){ if (K.altS === K.surum) return K.altV; var W = K.W, a = K.alfa, r0 = (K.H - 2) * W, n = 0; for (var x = 0; x < W; x++) if (a[r0 + x] > 128) n++; K.altS = K.surum; return K.altV = n > W * 0.03; }
  function ciktiTuval(jpgZorla, enBoy){
    var W = K.W, H = K.H, x0 = 0, y0 = 0, x1 = W - 1, y1 = H - 1, a = K.alfa, ap = jpgZorla && K.ap === 'saydam' ? 'beyaz' : K.ap, nes = nesneTuval();
    if (ap === 'foto' && K.af){
      var bw = K.af.width, bh = K.af.height, q = enBoy ? Math.min(1, enBoy / Math.max(bw, bh)) : 1, c0 = document.createElement('canvas'); c0.width = Math.round(bw * q); c0.height = Math.round(bh * q);
      var g0 = c0.getContext('2d'); g0.imageSmoothingQuality = 'high'; g0.drawImage(K.af, 0, 0, c0.width, c0.height);
      var k0 = Math.max(c0.width / W, c0.height / H) * K.fs, ow = W * k0, oh = H * k0;
      /* fotoğrafın altına değen nesne (insan, gövde) yeni fotoğrafın da altına oturur; havada kesik kenar kalmaz */
      var alt = altaDeger(), oy = alt ? c0.height - oh : c0.height / 2 - oh / 2;
      g0.drawImage(nes, c0.width / 2 + K.fx * c0.width - ow / 2, oy + K.fy * c0.height, ow, oh); return c0; }
    if (K.kirp){ x0 = W; y0 = H; x1 = -1; y1 = -1; for (var y = 0; y < H; y++){ var r0 = y * W; for (var x = 0; x < W; x++) if (a[r0 + x] > 8){ if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; } }
      if (x1 < 0) throw new Error('Kalan nesne yok; Koru fırçasıyla geri getir ya da sıfırla.');
      var pd = Math.round(Math.max(W, H) * 0.02); x0 = Math.max(0, x0 - pd); y0 = Math.max(0, y0 - pd); x1 = Math.min(W - 1, x1 + pd); y1 = Math.min(H - 1, y1 + pd); }
    var kw = x1 - x0 + 1, kh = y1 - y0 + 1, s = enBoy ? Math.min(1, enBoy / Math.max(kw, kh)) : 1, cw = Math.max(1, Math.round(kw * s)), ch = Math.max(1, Math.round(kh * s));
    var c = document.createElement('canvas'); c.width = cw; c.height = ch; var g = c.getContext('2d'); g.imageSmoothingQuality = 'high';
    if (ap === 'beyaz' || ap === 'siyah' || ap === 'renk'){ g.fillStyle = ap === 'beyaz' ? '#fff' : ap === 'siyah' ? '#000' : K.renk; g.fillRect(0, 0, cw, ch); }
    else if (ap === 'bulanik'){ g.filter = 'blur(' + Math.round(Math.max(cw, ch) * 0.03) + 'px)'; g.drawImage(K.src, x0 - kw * 0.05, y0 - kh * 0.05, kw * 1.1, kh * 1.1, -cw * 0.05, -ch * 0.05, cw * 1.1, ch * 1.1); g.filter = 'none'; g.fillStyle = 'rgba(0,0,0,.1)'; g.fillRect(0, 0, cw, ch); }
    g.drawImage(nes, x0, y0, kw, kh, 0, 0, cw, ch); return c; }
  async function cikti(jpgZorla){
    var c = ciktiTuval(jpgZorla), png = !jpgZorla && K.ap === 'saydam';
    var blob = await new Promise(function(r){ c.toBlob(r, png ? 'image/png' : 'image/jpeg', 0.95); }); c.width = 0;
    return {blob:blob, png:png}; }
  function indir(b, ad){ var a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = ad; document.body.appendChild(a); a.click(); a.remove(); setTimeout(function(){ URL.revokeObjectURL(a.href); }, 6000);
    try { toast('Kaydedildi: ' + ad); } catch(e){} }
  function kitapligaEkle(b){
    var inp = document.getElementById('pickGallery'); if (!inp){ hata(new Error('Kitaplığa eklenemedi.')); return; }
    var f = new File([b], K.ad + '-kesik.jpg', {type:'image/jpeg', lastModified:Date.now()}), dt = new DataTransfer(); dt.items.add(f);
    kapat(); try { closeEditor(); } catch(e){}
    setTimeout(function(){ inp.files = dt.files; inp.dispatchEvent(new Event('change', {bubbles:true})); }, 300); }

  /* ---------- Efektler › Akıllı bulanıklaştırma: ana nesne seçimi yapay zekâ ile ----------
     Karanlık Oda'nın "Ana nesneyi otomatik seç"i renk/netlik/yüz ipuçlarıyla tahmin ediyordu. Artık aynı seçim (s.sel, aynı biçim) ISNet maskesinden yapılır;
     çerçeveyle seçim ve fırçayla ekle/çıkar aynen çalışır. Yapay zekâ yüklenemezse eski yöntem kullanılır. */
  async function yzOzne(etiket){
    var cv = ED.prevCanvas; if (!cv) throw new Error('önizleme yok');
    var sz = selSize(cv.width, cv.height), W = sz[0], H = sz[1], b = busy('Yapay zekâ ana nesneyi ayırıyor…'), ot = null;
    try {
      var YZ = await motorYukle(); ot = await YZ.oturum(function(){});
      var a = await ot.calistir(cv), S = ot.boyut; await ot.kapat(); ot = null;
      var k = document.createElement('canvas'); k.width = S; k.height = S; var kx = k.getContext('2d'), im = kx.createImageData(S, S);
      for (var i = 0; i < S * S; i++){ var v = Math.round(a[i] * 255); im.data[i * 4] = im.data[i * 4 + 1] = im.data[i * 4 + 2] = v; im.data[i * 4 + 3] = 255; } kx.putImageData(im, 0, 0);
      var o = document.createElement('canvas'); o.width = W; o.height = H; var ox = o.getContext('2d', {willReadFrequently:true}); ox.imageSmoothingQuality = 'high'; ox.drawImage(k, 0, 0, W, H);
      var d = ox.getImageData(0, 0, W, H).data, M = new Float32Array(W * H), top = 0; for (i = 0; i < W * H; i++){ M[i] = d[i * 4] / 255; top += M[i]; }
      var st = ED.state; st.sel = {m:maskePng(M, W, H), ops:[], src:'otomatik'}; ED.selOran = top / (W * H); _ants.key = null;
      commit(etiket || 'Ana nesne seçildi (yapay zekâ)'); rebuildBase(true); renderPanel(); requestRender();
      toast(ED.selOran < .01 ? 'Belirgin bir nesne bulunamadı — fırçayla seçime ekleyebilirsin.' : 'Yapay zekâ ile seçildi · kenarları fırçayla düzeltebilirsin');
    } finally { if (ot) try { await ot.kapat(); } catch(e){} b.close(); } }
  (function ozneSar(n){ if (typeof window.ozneSec !== 'function'){ if (n < 60) setTimeout(function(){ ozneSar(n + 1); }, 300); return; }
    var eski = window.ozneSec;
    window.ozneSec = async function(rect, etiket){ if (rect) return eski.apply(this, arguments);   /* çerçeveyle seçim: eski yol (çerçevenin içi) */
      try { await yzOzne(etiket); } catch(e){ console.warn('Yapay zekâ seçimi olmadı, eski yöntem', e); try { toast('Yapay zekâ yüklenemedi; eski yöntemle seçiliyor.'); } catch(_){} return eski.apply(this, arguments); } }; })(0);

  /* ---------- araç çubuğu: "Kes" ---------- */
  function sekmeKur(){ var tb = document.getElementById('tabs'); if (!tb || document.getElementById('stKesTab')) return;
    var ilk = tb.querySelector('.tab'); var b = document.createElement('button'); b.type = 'button'; b.id = 'stKesTab'; b.className = ilk ? ilk.className.replace(/\b(on|gizli)\b/g, '').trim() : 'tab';
    b.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true">' + I.kes + '</svg><span>Kes</span>'; b.setAttribute('aria-label', 'Yapay zekâ ile nesne kes');
    b.addEventListener('click', function(e){ e.preventDefault(); e.stopPropagation(); ac(); }, true); tb.appendChild(b); }
  (function bagla(n){ var tb = document.getElementById('tabs'); if (!tb){ if (n < 80) setTimeout(function(){ bagla(n + 1); }, 300); return; }
    sekmeKur(); new MutationObserver(sekmeKur).observe(tb, {childList:true}); })(0);
})();
