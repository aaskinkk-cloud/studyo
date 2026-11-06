/* ===== STÜDYO · YAPAY ZEKÂ KESME MOTORU (ortak) =====
   ISNet (DIS, Apache-2.0) + ONNX Runtime Web 1.30.0 (MIT). Önce grafik birimi (WebGPU), olmazsa işlemci (WASM).
   Model yalnız çağrılınca yüklenir; iş bitince oturum kapatılır (bellek ve grafik birimi serbest kalır).
   İndirilen model 'yz-model-1' önbelleğinde durur (deneme sayfasıyla ortak, Stüdyo güncellemeleri silmez).
   Kullanım: const r = await stYZ.kes(dosya, (metin, oran) => …)  →  {png, w, h, x, y, W, H, motor, ms} */
(function(){
  if (window.stYZ) return;
  var KOK = (function(){ var s = document.currentScript && document.currentScript.src; return s ? s.replace(/[^/]*$/, '') : new URL('../yz/', location.href).href; })();
  var ONB = 'yz-model-1';
  var M = {boyut:1024, ort:[0.5,0.5,0.5], sap:[1,1,1], grafik:'model/isnet.onnx',
    parca:['model/isnet.f16.part0','model/isnet.f16.part1','model/isnet.f16.part2','model/isnet.f16.part3','model/isnet.f16.part4'], bayt:88026496, ad:'w.bin'};
  var ortSoz = null, mesgul = false;
  function ortYukle(){ if (ortSoz) return ortSoz;
    ortSoz = new Promise(function(ok, no){ if (window.ort) return ok(window.ort); var s = document.createElement('script'); s.src = KOK + 'ort/ort.webgpu.min.js';
      s.onload = function(){ ort.env.wasm.wasmPaths = KOK + 'ort/'; ort.env.wasm.numThreads = 1; ok(window.ort); };
      s.onerror = function(){ ortSoz = null; no(new Error('Yapay zekâ motoru yüklenemedi. İnternet bağlantısını kontrol et.')); };
      document.head.appendChild(s); });
    return ortSoz; }
  async function dosyaAl(yol, ilerle){
    var url = KOK + yol, c = null;
    try { c = await caches.open(ONB); var h = await c.match(url); if (h){ var b0 = await h.arrayBuffer(); ilerle(b0.byteLength, true); return new Uint8Array(b0); } } catch(e){ c = null; }
    var r = await fetch(url, {cache:'no-cache'}); if (!r.ok) throw new Error('Model indirilemedi (HTTP ' + r.status + ').');
    var okuyan = r.body.getReader(), par = [], n = 0;
    for (;;){ var x = await okuyan.read(); if (x.done) break; par.push(x.value); n += x.value.length; ilerle(n, false); }
    var u = new Uint8Array(n), o = 0; par.forEach(function(p){ u.set(p, o); o += p.length; });
    if (c) try { await c.put(url, new Response(u.slice().buffer, {headers:{'content-type':'application/octet-stream'}})); } catch(e){}
    return u; }
  function f16to32(u8){ var h = new Uint16Array(u8.buffer, u8.byteOffset, u8.byteLength >> 1), f = new Float32Array(h.length), u = new Uint32Array(f.buffer);
    for (var i = 0; i < h.length; i++){ var x = h[i], s = (x & 0x8000) << 16, e = (x >> 10) & 0x1f, m = x & 0x3ff;
      if (e === 0){ if (m === 0){ u[i] = s; continue; } e = 1; while (!(m & 0x400)){ m <<= 1; e--; } m &= 0x3ff; u[i] = s | ((e + 112) << 23) | (m << 13); }
      else if (e === 31) u[i] = s | 0x7f800000 | (m << 13); else u[i] = s | ((e + 112) << 23) | (m << 13); }
    return new Uint8Array(f.buffer); }
  /* ---------- ortak maske işlevleri (fotoğraf ve video aynı motoru kullanır) ---------- */
  /* ayrılabilir kutu bulanıklığı (koşan toplam, O(N)); a: Float32Array W*H */
  function kutu(a, W, H, r){ if (r < 1) return a; r = Math.round(r); var t = new Float32Array(W * H), o = new Float32Array(W * H), x, y, s, i, n = 2 * r + 1;
    for (y = 0; y < H; y++){ var b = y * W; s = 0; for (x = -r; x <= r; x++) s += a[b + Math.min(W - 1, Math.max(0, x))];
      for (x = 0; x < W; x++){ t[b + x] = s / n; s += a[b + Math.min(W - 1, x + r + 1)] - a[b + Math.max(0, x - r)]; } }
    for (x = 0; x < W; x++){ s = 0; for (y = -r; y <= r; y++) s += t[Math.min(H - 1, Math.max(0, y)) * W + x];
      for (y = 0; y < H; y++){ o[y * W + x] = s / n; s += t[Math.min(H - 1, y + r + 1) * W + x] - t[Math.max(0, y - r) * W + x]; } }
    return o; }
  /* kenar: kaydir < 0 daraltır, > 0 genişletir (piksel); yumusak: kenar yumuşatma yarıçapı */
  function incelt(alfa, W, H, kaydir, yumusak){ var N = W * H, a = new Float32Array(N), i; for (i = 0; i < N; i++) a[i] = alfa[i];
    if (kaydir){ var b = kutu(kutu(a, W, H, Math.abs(kaydir) / 2), W, H, Math.abs(kaydir) / 2);
      if (kaydir > 0) for (i = 0; i < N; i++) a[i] = Math.min(255, Math.max(a[i], b[i] * 2));
      else for (i = 0; i < N; i++) a[i] = Math.max(0, Math.min(a[i], b[i] * 2 - 255)); }
    if (yumusak) a = kutu(kutu(a, W, H, yumusak / 2), W, H, yumusak / 2);
    var o = new Uint8ClampedArray(N); for (i = 0; i < N; i++) o[i] = a[i]; return o; }
  /* renk taşmasını temizle: yarı saydam kenar piksellerinin rengi, yakındaki dolu nesne pikselinin rengine çekilir
     (eski arka planın rengi kenarda hale/kontur bırakmasın). px: RGBA (değişmez), dönüş: yeni RGBA */
  function renkTemizle(px, alfa, W, H, r){ var N = W * H, w = new Float32Array(N), cr = new Float32Array(N), cg = new Float32Array(N), cb = new Float32Array(N), i;
    r = r || Math.max(3, Math.round(Math.max(W, H) / 300));
    for (i = 0; i < N; i++){ var q = alfa[i] / 255; q = q * q * q * q; w[i] = q; cr[i] = px[i * 4] * q; cg[i] = px[i * 4 + 1] * q; cb[i] = px[i * 4 + 2] * q; }
    w = kutu(w, W, H, r); cr = kutu(cr, W, H, r); cg = kutu(cg, W, H, r); cb = kutu(cb, W, H, r);
    var o = new Uint8ClampedArray(px);
    for (i = 0; i < N; i++){ var a = alfa[i]; if (a === 0 || a >= 250 || w[i] < 1e-4) continue; var m = Math.min(1, (1 - a / 255) * 1.6), j = i * 4;
      o[j] += (cr[i] / w[i] - o[j]) * m; o[j + 1] += (cg[i] / w[i] - o[j + 1]) * m; o[j + 2] += (cb[i] / w[i] - o[j + 2]) * m; }
    return o; }
  /* saydam nesne (cam, bardak): nesnenin arkasındaki temiz arka plan, çevredeki arka plan piksellerinden tahmin edilir (düşük çözünürlükte
     içe doğru doldurma). Nesne pikseli bu tahmine ne kadar benziyorsa o kadar saydamlaşır (camın içinden görünen arka plan); farklı olanlar (çay,
     kenar, parlama) kalır. Ön plan rengi karışımdan ayrıştırılır: F = (I − (1−a)·B) / a. guc 0–100: saydamlaştırma gücü. Dönüş {alfa, px} */
  function saydamlastir(px, alfa, W, H, guc, renkAyir){
    guc = guc == null ? 60 : guc; var Ls = 160, k = Ls / Math.max(W, H), lw = Math.max(4, Math.round(W * k)), lh = Math.max(4, Math.round(H * k)), N = lw * lh, i, x, y;
    var br = new Float32Array(N), bg = new Float32Array(N), bb = new Float32Array(N), bw = new Float32Array(N);
    for (y = 0; y < H; y++){ var ly = Math.min(lh - 1, Math.floor(y * k)); for (x = 0; x < W; x++){ var j = y * W + x, a = alfa[j] / 255, w = a < 0.1 ? 1 : (a < 0.5 ? 0.3 : 0); if (!w) continue;
      var li = ly * lw + Math.min(lw - 1, Math.floor(x * k)), p = j * 4; br[li] += px[p] * w; bg[li] += px[p + 1] * w; bb[li] += px[p + 2] * w; bw[li] += w; } }
    /* doldurma: ağırlığı olmayan hücreler büyüyen yarıçaplarla çevreden doldurulur */
    var R = br, G = bg, Bl = bb, Wt = bw;
    for (var it = 0, r = 2; it < 7; it++, r *= 2){ var r2 = kutu(R, lw, lh, r), g2 = kutu(G, lw, lh, r), b2 = kutu(Bl, lw, lh, r), w2 = kutu(Wt, lw, lh, r), eks = 0;
      for (i = 0; i < N; i++){ if (Wt[i] > 1e-3) continue; if (w2[i] > 1e-6){ R[i] = r2[i]; G[i] = g2[i]; Bl[i] = b2[i]; Wt[i] = w2[i]; } else eks++; }
      if (!eks) break; }
    var tr = new Float32Array(N), tg = new Float32Array(N), tb = new Float32Array(N); for (i = 0; i < N; i++){ var q = Wt[i] || 1; tr[i] = R[i] / q; tg[i] = G[i] / q; tb[i] = Bl[i] / q; }
    tr = kutu(tr, lw, lh, 1); tg = kutu(tg, lw, lh, 1); tb = kutu(tb, lw, lh, 1);
    var oa = new Uint8ClampedArray(alfa), op = renkAyir ? new Uint8ClampedArray(px) : null, d0 = 8 + (100 - guc) * 0.35, d1 = d0 + 30 + (100 - guc) * 0.6, taban = 0.06 + (100 - guc) / 100 * 0.3;
    for (y = 0; y < H; y++){ var fy = Math.min(lh - 1.001, y * k), y0 = Math.floor(fy), yy = fy - y0, y1 = Math.min(lh - 1, y0 + 1);
      for (x = 0; x < W; x++){ var j2 = y * W + x, a0 = alfa[j2]; if (a0 < 8) continue; var fx = Math.min(lw - 1.001, x * k), x0 = Math.floor(fx), xx = fx - x0, x1 = Math.min(lw - 1, x0 + 1);
        var i00 = y0 * lw + x0, i01 = y0 * lw + x1, i10 = y1 * lw + x0, i11 = y1 * lw + x1;
        var Br = (tr[i00] * (1 - xx) + tr[i01] * xx) * (1 - yy) + (tr[i10] * (1 - xx) + tr[i11] * xx) * yy, Bg = (tg[i00] * (1 - xx) + tg[i01] * xx) * (1 - yy) + (tg[i10] * (1 - xx) + tg[i11] * xx) * yy, Bb = (tb[i00] * (1 - xx) + tb[i01] * xx) * (1 - yy) + (tb[i10] * (1 - xx) + tb[i11] * xx) * yy;
        var p2 = j2 * 4, dr = px[p2] - Br, dg = px[p2 + 1] - Bg, db = px[p2 + 2] - Bb, d = Math.sqrt(dr * dr + dg * dg + db * db), t = d <= d0 ? 0 : d >= d1 ? 1 : (d - d0) / (d1 - d0);
        var an = a0 / 255 * (taban + (1 - taban) * t); oa[j2] = an * 255;
        if (op && an > 0.04){ op[p2] = (px[p2] - (1 - an) * Br) / an; op[p2 + 1] = (px[p2 + 1] - (1 - an) * Bg) / an; op[p2 + 2] = (px[p2 + 2] - (1 - an) * Bb) / an; } } }
    return {alfa:oa, px:op}; }
  /* ayrı nesneler: bağlı alanlar (alfa > esik), küçük kırıntılar elenir, büyükten küçüğe. Yumuşak kenar pikselleri en yakın nesneye katılır. */
  function parcalar(alfa, W, H, sec){ sec = sec || {}; var esik = sec.esik || 40, N = W * H, et = new Int32Array(N), yig = new Int32Array(N), L = [], top = 0, i;
    for (i = 0; i < N; i++) if (alfa[i] > esik) top++;
    for (var s0 = 0; s0 < N; s0++){ if (et[s0] || alfa[s0] <= esik) continue; var no = L.length + 1, n = 0, alan = 0, x0 = W, y0 = H, x1 = -1, y1 = -1; yig[n++] = s0; et[s0] = no;
      while (n){ i = yig[--n]; alan++; var x = i % W, y = (i - x) / W; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        if (x > 0 && !et[i - 1] && alfa[i - 1] > esik){ et[i - 1] = no; yig[n++] = i - 1; } if (x < W - 1 && !et[i + 1] && alfa[i + 1] > esik){ et[i + 1] = no; yig[n++] = i + 1; }
        if (y > 0 && !et[i - W] && alfa[i - W] > esik){ et[i - W] = no; yig[n++] = i - W; } if (y < H - 1 && !et[i + W] && alfa[i + W] > esik){ et[i + W] = no; yig[n++] = i + W; } }
      L.push({no:no, alan:alan, x0:x0, y0:y0, x1:x1, y1:y1}); }
    var enAz = Math.max(sec.enAz || 0, top * (sec.oran || 0.006)), liste = L.filter(function(p){ return p.alan >= enAz; }).sort(function(a, b){ return b.alan - a.alan; }).slice(0, sec.enCok || 12);
    return {etiket:et, liste:liste, W:W, H:H}; }
  /* bir parçanın alfa maskesi (kutusu ve yumuşak kenarıyla); dönüş: {alfa (kutu boyunda), x, y, w, h} */
  function parcaMaske(alfa, P, p, pay){ var W = P.W, H = P.H, et = P.etiket; pay = pay == null ? Math.max(3, Math.round(Math.max(W, H) * 0.01)) : pay;
    var x0 = Math.max(0, p.x0 - pay), y0 = Math.max(0, p.y0 - pay), x1 = Math.min(W - 1, p.x1 + pay), y1 = Math.min(H - 1, p.y1 + pay), w = x1 - x0 + 1, h = y1 - y0 + 1;
    var uye = new Float32Array(w * h), x, y; for (y = 0; y < h; y++) for (x = 0; x < w; x++) uye[y * w + x] = et[(y + y0) * W + x + x0] === p.no ? 1 : 0;
    var gen = kutu(uye, w, h, 3), o = new Uint8ClampedArray(w * h);
    for (y = 0; y < h; y++) for (x = 0; x < w; x++){ var i = (y + y0) * W + x + x0, e = et[i], k = y * w + x; if (e === p.no || (!e && gen[k] > 0)) o[k] = alfa[i]; }
    return {alfa:o, x:x0, y:y0, w:w, h:h}; }
  var bekle = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };

  /* model oturumu: indir/önbellekten oku, fp16→fp32 aç, grafik birimi olmazsa işlemci */
  async function modelAc(bildir, sec){ sec = sec || {}; var sess = null;
    bildir('Yapay zekâ motoru yükleniyor…', 0.05); await ortYukle();
    var top = M.bayt + 2602833, sayac = {}, kayitli = true;
    var ilerle = function(ad){ return function(n, onb){ sayac[ad] = n; if (!onb) kayitli = false; var s = 0; for (var q in sayac) s += sayac[q];
      bildir((onb ? 'Model hazırlanıyor… ' : 'Model indiriliyor… ') + Math.min(100, Math.round(s / top * 100)) + '%', 0.06 + 0.6 * Math.min(1, s / top)); }; };
    var grafik = await dosyaAl(M.grafik, ilerle('g'));
    var pr = await Promise.all(M.parca.map(function(p, i){ return dosyaAl(p, ilerle('p' + i)); }));
    var w = new Uint8Array(M.bayt), o = 0; pr.forEach(function(p){ w.set(p, o); o += p.length; }); pr = null;
    if (o !== M.bayt){ try { await caches.delete(ONB); } catch(e){} throw new Error('Model eksik indi; silindi. Yeniden dene.'); }
    bildir('Model hazırlanıyor…', 0.7); await bekle(20);
    var ek = [{path:M.ad, data:f16to32(w)}]; w = null;
    var motor = 'işlemci', gpu = false;
    try { gpu = !!(navigator.gpu && await navigator.gpu.requestAdapter()); } catch(e){ gpu = false; }
    var ayar = function(ep){ return {executionProviders:[ep], graphOptimizationLevel:'all', externalData:ek}; };
    if (gpu && !sec.yalnizIslemci){ try { sess = await ort.InferenceSession.create(grafik, ayar('webgpu')); motor = 'grafik birimi'; } catch(e){ console.warn('WebGPU olmadı, işlemciye geçiliyor', e); sess = null; } }
    if (!sess) sess = await ort.InferenceSession.create(grafik, ayar('wasm'));
    grafik = null; ek = null;
    return {sess:sess, motor:motor, kayitli:kayitli}; }
  async function kes(dosya, bildir, sec){
    sec = sec || {}; bildir = bildir || function(){};
    if (mesgul) throw new Error('Başka bir kesme sürüyor.'); mesgul = true; var sess = null, t0 = performance.now();
    try {
      bildir('Fotoğraf okunuyor…', 0.02);
      var bmp = await createImageBitmap(dosya, {imageOrientation:'from-image'});
      /* bellek için uzun kenar en çok 2048 px */
      var EN = sec.enBoy || 2048, k0 = Math.min(1, EN / Math.max(bmp.width, bmp.height)), W = Math.round(bmp.width * k0), H = Math.round(bmp.height * k0);
      var Y = await modelAc(bildir, sec); sess = Y.sess; var motor = Y.motor, kayitli = Y.kayitli;
      var S = M.boyut, cv = document.createElement('canvas'); cv.width = S; cv.height = S; var cx = cv.getContext('2d', {willReadFrequently:true});
      cx.imageSmoothingQuality = 'high'; cx.drawImage(bmp, 0, 0, S, S); var px = cx.getImageData(0, 0, S, S).data, N = S * S, x = new Float32Array(3 * N);
      for (var i = 0; i < N; i++) for (var c = 0; c < 3; c++) x[c * N + i] = (px[i * 4 + c] / 255 - M.ort[c]) / M.sap[c];
      bildir('Nesne ayrıştırılıyor (' + motor + ')…', 0.8); await bekle(30);
      var gir = {}; gir[sess.inputNames[0]] = new ort.Tensor('float32', x, [1, 3, S, S]);
      var y = (await sess.run(gir))[sess.outputNames[0]].data;
      try { await sess.release(); } catch(e){} sess = null;
      bildir('Kenarlar hazırlanıyor…', 0.92); await bekle(10);
      /* alfa: en küçük–en büyük ölçekleme (modelin önerdiği), çok zayıf sisi temizle */
      var mn = Infinity, mx = -Infinity; for (i = 0; i < N; i++){ var v = y[i]; if (v < mn) mn = v; if (v > mx) mx = v; }
      var ac = document.createElement('canvas'); ac.width = S; ac.height = S; var ax = ac.getContext('2d'), ad = ax.createImageData(S, S), kk = 1 / (mx - mn || 1);
      for (i = 0; i < N; i++){ var a = (y[i] - mn) * kk; a = a < 0.04 ? 0 : (a - 0.04) / 0.96; ad.data[i * 4] = ad.data[i * 4 + 1] = ad.data[i * 4 + 2] = 255; ad.data[i * 4 + 3] = Math.round(a * 255); }
      ax.putImageData(ad, 0, 0);
      var out = document.createElement('canvas'); out.width = W; out.height = H; var oc = out.getContext('2d', {willReadFrequently:true});
      oc.imageSmoothingQuality = 'high'; oc.drawImage(bmp, 0, 0, W, H); oc.globalCompositeOperation = 'destination-in'; oc.drawImage(ac, 0, 0, W, H); oc.globalCompositeOperation = 'source-over';
      try { bmp.close(); } catch(e){}
      /* ham: tam boy kaynak ve alfa (fotoğraf editöründe fırçayla düzeltmek için) */
      if (sec.ham){
        var tam = document.createElement('canvas'); tam.width = W; tam.height = H; var tx = tam.getContext('2d', {willReadFrequently:true}); tx.imageSmoothingQuality = 'high'; tx.drawImage(ac, 0, 0, W, H);
        var td = tx.getImageData(0, 0, W, H).data, al = new Uint8ClampedArray(W * H); for (i = 0; i < W * H; i++) al[i] = td[i * 4 + 3];
        bildir('Bitti.', 1); return {alfa:al, W:W, H:H, motor:motor, ms:Math.round(performance.now() - t0), indi:!kayitli}; }
      /* nesnenin kutusu: boş kenarlar kırpılır (yerleştirmesi kolay, dosya küçük) */
      /* kenar rengini temizle (yeni arka plan önünde hale kalmasın) */
      var idt = oc.getImageData(0, 0, W, H), al0 = new Uint8ClampedArray(W * H); for (i = 0; i < W * H; i++) al0[i] = idt.data[i * 4 + 3];
      if (sec.saydam){ bildir('Saydam bölgeler ayrıştırılıyor…', 0.94); await bekle(10); var sy = saydamlastir(idt.data, al0, W, H, sec.saydam === true ? 60 : sec.saydam, true); al0 = sy.alfa; for (i = 0; i < W * H; i++) sy.px[i * 4 + 3] = al0[i]; idt.data.set(sy.px); oc.putImageData(idt, 0, 0); }
      if (sec.temiz !== false){ bildir('Kenarlar temizleniyor…', 0.95); await bekle(10); idt.data.set(renkTemizle(idt.data, al0, W, H)); oc.putImageData(idt, 0, 0); }
      var dt = idt.data, x0 = W, y0 = H, x1 = -1, y1 = -1;
      for (var yy = 0; yy < H; yy++){ var r0 = yy * W * 4; for (var xx = 0; xx < W; xx++) if (dt[r0 + xx * 4 + 3] > 10){ if (xx < x0) x0 = xx; if (xx > x1) x1 = xx; if (yy < y0) y0 = yy; if (yy > y1) y1 = yy; } }
      if (x1 < 0) throw new Error('Fotoğrafta belirgin bir nesne bulunamadı.');
      var pad = Math.round(Math.max(W, H) * 0.01); x0 = Math.max(0, x0 - pad); y0 = Math.max(0, y0 - pad); x1 = Math.min(W - 1, x1 + pad); y1 = Math.min(H - 1, y1 + pad);
      var kw = x1 - x0 + 1, kh = y1 - y0 + 1, kc = document.createElement('canvas'); kc.width = kw; kc.height = kh; kc.getContext('2d').drawImage(out, x0, y0, kw, kh, 0, 0, kw, kh);
      var png = await new Promise(function(r){ kc.toBlob(r, 'image/png'); });
      /* ayrı nesneler (istenirse): her biri kendi kutusunda saydam PNG */
      var par = [];
      if (sec.parcalar){ var P = parcalar(al0, W, H, {oran:0.01, enCok:8});
        if (P.liste.length > 1) for (var q = 0; q < P.liste.length; q++){ var PM = parcaMaske(al0, P, P.liste[q]), pc = document.createElement('canvas'); pc.width = PM.w; pc.height = PM.h;
          var pcx = pc.getContext('2d'), pim = oc.getImageData(PM.x, PM.y, PM.w, PM.h); for (var z = 0; z < PM.w * PM.h; z++) pim.data[z * 4 + 3] = PM.alfa[z]; pcx.putImageData(pim, 0, 0);
          par.push({png:await new Promise(function(r){ pc.toBlob(r, 'image/png'); }), w:PM.w, h:PM.h, x:PM.x / W, y:PM.y / H}); pc.width = 0; } }
      bildir('Bitti.', 1);
      return {png:png, parcalar:par, w:kw, h:kh, x:x0 / W, y:y0 / H, W:W, H:H, motor:motor, ms:Math.round(performance.now() - t0), indi:!kayitli};
    } finally { if (sess) try { await sess.release(); } catch(e){} mesgul = false; }
  }
  /* çok kare (video): model bir kez yüklenir; her karede S×S ham çıktı (0–1) döner. İş bitince kapat() çağrılmalı. */
  async function oturum(bildir, sec){
    if (mesgul) throw new Error('Başka bir kesme sürüyor.'); mesgul = true; var Y;
    try { Y = await modelAc(bildir || function(){}, sec); } catch(e){ mesgul = false; throw e; }
    var S = M.boyut, cv = document.createElement('canvas'); cv.width = S; cv.height = S; var cx = cv.getContext('2d', {willReadFrequently:true}); cx.imageSmoothingQuality = 'high';
    var x = new Float32Array(3 * S * S), acik = true;
    return {motor:Y.motor, boyut:S, kayitli:Y.kayitli,
      calistir: async function(kaynak){ if (!acik) throw new Error('oturum kapalı'); cx.drawImage(kaynak, 0, 0, S, S); var px = cx.getImageData(0, 0, S, S).data, N = S * S;
        for (var i = 0; i < N; i++) for (var c = 0; c < 3; c++) x[c * N + i] = (px[i * 4 + c] / 255 - M.ort[c]) / M.sap[c];
        var gir = {}; gir[Y.sess.inputNames[0]] = new ort.Tensor('float32', x, [1, 3, S, S]); var y = (await Y.sess.run(gir))[Y.sess.outputNames[0]].data;
        var mn = Infinity, mx = -Infinity; for (i = 0; i < N; i++){ var v = y[i]; if (v < mn) mn = v; if (v > mx) mx = v; }
        var o = new Float32Array(N), k = 1 / (mx - mn || 1); for (i = 0; i < N; i++){ var a = (y[i] - mn) * k; o[i] = a < 0.04 ? 0 : (a - 0.04) / 0.96; } return o; },
      kapat: async function(){ if (!acik) return; acik = false; try { await Y.sess.release(); } catch(e){} mesgul = false; } }; }
  /* ---------- video takibi (ortak): kare kare ham çıktıdan seçili nesnenin maskesi ----------
     İlk karede en büyük nesne (ya da verilen üyelik) seçilir; sonraki karelerde önceki maskeyle örtüşen parçalar alınır,
     çok uzaktaki parçalar atılır (yapışık arka plan), boşalan yer hemen boşalır, küçük titreşim yumuşatılır, kenar hafif daraltılır. */
  function takipci(sec){ sec = sec || {}; var S0 = sec.boyut || 1024, L = 256, Mx = sec.maske || 384, kenar = sec.kenar == null ? 1 : sec.kenar;
    function kucult(a, S, D){ var o = new Float32Array(D * D), k = S / D; for (var y = 0; y < D; y++) for (var x = 0; x < D; x++){ var s = 0, n = 0;
      for (var yy = Math.floor(y * k); yy < Math.floor((y + 1) * k); yy++) for (var xx = Math.floor(x * k); xx < Math.floor((x + 1) * k); xx++){ s += a[yy * S + xx]; n++; } o[y * D + x] = s / n; } return o; }
    function bilesen(a, D, esik){ var et = new Int32Array(D * D), yig = new Int32Array(D * D), Lb = [], i;
      for (var s0 = 0; s0 < D * D; s0++){ if (et[s0] || a[s0] <= esik) continue; var no = Lb.length + 1, n = 0, alan = 0; yig[n++] = s0; et[s0] = no;
        while (n){ i = yig[--n]; alan++; var x = i % D, y = (i - x) / D;
          if (x > 0 && !et[i - 1] && a[i - 1] > esik){ et[i - 1] = no; yig[n++] = i - 1; } if (x < D - 1 && !et[i + 1] && a[i + 1] > esik){ et[i + 1] = no; yig[n++] = i + 1; }
          if (y > 0 && !et[i - D] && a[i - D] > esik){ et[i - D] = no; yig[n++] = i - D; } if (y < D - 1 && !et[i + D] && a[i + D] > esik){ et[i + D] = no; yig[n++] = i + D; } }
        Lb.push({no:no, alan:alan}); }
      return {et:et, L:Lb}; }
    function genislet(m, D, r){ var o = new Uint8Array(D * D); for (var y = 0; y < D; y++) for (var x = 0; x < D; x++){ if (!m[y * D + x]) continue;
      for (var yy = Math.max(0, y - r); yy <= Math.min(D - 1, y + r); yy++) for (var xx = Math.max(0, x - r); xx <= Math.min(D - 1, x + r); xx++) o[yy * D + xx] = 1; } return o; }
    var onceki = null, onM = null, kayip = 0;
    function ekle(a){
      var kk = kucult(a, S0, L), B = bilesen(kk, L, 0.5), uye = null, i;
      if (!onceki){ var en = B.L.slice().sort(function(p, q){ return q.alan - p.alan; })[0]; if (en){ uye = new Uint8Array(L * L); for (i = 0; i < L * L; i++) if (B.et[i] === en.no) uye[i] = 1; } }
      else { var g = genislet(onceki, L, 6), ort = {}, var_ = false; for (i = 0; i < L * L; i++){ var e = B.et[i]; if (e && g[i]) ort[e] = (ort[e] || 0) + 1; }
        B.L.forEach(function(c){ if ((ort[c.no] || 0) > Math.max(4, c.alan * 0.15)){ c.sec = true; var_ = true; } });
        if (var_){ uye = new Uint8Array(L * L); for (i = 0; i < L * L; i++){ var e2 = B.et[i]; if (e2 && B.L[e2 - 1].sec) uye[i] = 1; }
          var kap = genislet(onceki, L, 10), at = 0, top0 = 0; for (i = 0; i < L * L; i++) if (uye[i]){ top0++; if (!kap[i]){ uye[i] = 0; at++; } } if (at > top0 * 0.6) uye = null; } }
      if (!uye){ kayip++; uye = new Uint8Array(L * L); } else onceki = uye;
      var a5 = kucult(a, S0, Mx), ug = genislet(uye, L, 2), m8 = new Uint8ClampedArray(Mx * Mx), r = L / Mx, x, y;
      for (y = 0; y < Mx; y++) for (x = 0; x < Mx; x++){ i = y * Mx + x; m8[i] = ug[Math.floor(y * r) * L + Math.floor(x * r)] ? a5[i] * 255 : 0; }
      for (var kd = 0; kd < kenar; kd++){ var e8 = new Uint8ClampedArray(m8); for (y = 1; y < Mx - 1; y++) for (x = 1; x < Mx - 1; x++){ i = y * Mx + x; var v0 = m8[i]; if (!v0) continue; e8[i] = Math.min(v0, m8[i - 1], m8[i + 1], m8[i - Mx], m8[i + Mx]); } m8 = e8; }
      if (onM) for (i = 0; i < Mx * Mx; i++){ var yeni = m8[i], d = yeni - onM[i]; if (yeni > 40 && onM[i] > 40 && Math.abs(d) < 50) m8[i] = onM[i] + d * 0.55; }
      onM = m8; return m8; }
    return {ekle:ekle, boyut:Mx, kayip:function(){ return kayip; }}; }
  window.stYZ = {kes:kes, oturum:oturum, takipci:takipci, saydamlastir:saydamlastir, kok:KOK, model:'ISNet (DIS)', kutu:kutu, incelt:incelt, renkTemizle:renkTemizle, parcalar:parcalar, parcaMaske:parcaMaske};
})();
