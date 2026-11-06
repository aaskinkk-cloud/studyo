/* ===== STÜDYO · VİDEO EDİTÖRÜ: YAPAY ZEKÂ NESNE KATMANI =====
   Alt menüde "Nesne": telefondan bir fotoğraf seçilir, nesne yapay zekâ ile kesilir (yz/motor.js, ISNet), videonun üstüne katman olarak konur.
   Katman: konum (sürükle), boyut ve açı (iki parmak ya da kaydırıcı), saydamlık, gölge, ayna, giriş hareketi, başlangıç/bitiş zamanı.
   Çizim Kareden Videoya'nın kendi çizim döngüsünde yapılır (esitle.py kancası) → önizleme ve dışa aktarma aynı.
   Veriler: 'studyo-nesne' veritabanı (png: kesilmiş görüntüler, proje: proje kimliğine göre katman listesi). Kareden Videoya'nın kayıt biçimine dokunulmaz. */
(function(){
  if (!window.STUDYO) return;
  var B = document.body, KV = function(){ return window.__kv || null; };
  var N = {pid:null, liste:[], img:{}, sec:null, acik:false, W:1920, H:1080, geo:{}, ayar:'s'};
  var HAREKET = [['sabit','Sabit'],['belir','Belirerek'],['kay','Kayarak'],['yaklas','Yaklaşarak'],['suz','Süzülerek']];
  var AYAR = [['s','Boyut',10,150,'%'],['r','Açı',-180,180,'°'],['op','Görünürlük',10,100,'%']];

  /* ---------- veritabanı ---------- */
  var dbp = null;
  function db(){ return dbp || (dbp = new Promise(function(ok, no){ var r = indexedDB.open('studyo-nesne', 1);
    r.onupgradeneeded = function(){ r.result.createObjectStore('png'); r.result.createObjectStore('proje'); };
    r.onsuccess = function(){ ok(r.result); }; r.onerror = function(){ dbp = null; no(r.error); }; })); }
  function idb(st, mod, f){ return db().then(function(d){ return new Promise(function(ok, no){ var tx = d.transaction(st, mod), q = f(tx.objectStore(st));
    tx.oncomplete = function(){ ok(q && q.result); }; tx.onerror = function(){ no(tx.error); }; tx.onabort = function(){ no(tx.error); }; }); }); }
  var kayT = 0;
  function kaydet(){ clearTimeout(kayT); var pid = N.pid, l = N.liste.map(function(o){ var c = {}; for (var k in o) c[k] = o[k]; return c; });
    if (!pid) return; kayT = setTimeout(function(){ idb('proje', 'readwrite', function(s){ return s.put(l, pid); }).catch(function(e){ console.warn('nesne kaydı', e); }); }, 300); }
  async function yukle(pid){
    N.liste = []; N.sec = null; N.geo = {}; N.gecmis = []; N.ileriL = []; ciz();
    if (!pid) return;
    var l = []; try { l = (await idb('proje', 'readonly', function(s){ return s.get(pid); })) || []; } catch(e){ console.warn('nesne listesi', e); }
    if (N.pid !== pid) return;
    for (var i = 0; i < l.length; i++){ var o = l[i]; if (!N.img[o.id]){ try { var b = await idb('png', 'readonly', function(s){ return s.get(o.id); }); if (b) N.img[o.id] = await createImageBitmap(b); } catch(e){ console.warn('nesne görüntüsü', e); } }
      if (o.arka === 'foto' && !N.img[o.id + '_af']){ try { var ab = await idb('png', 'readonly', function(s){ return s.get(o.id + '_af'); }); if (ab) N.img[o.id + '_af'] = await createImageBitmap(ab); } catch(e){} }
      if (o.klip && !N.img[o.id + '_bg']){ try { var bb = await idb('png', 'readonly', function(s){ return s.get(o.id + '_bg'); }); if (bb) N.img[o.id + '_bg'] = await createImageBitmap(bb); } catch(e){} } }
    if (N.pid !== pid) return;
    N.liste = l.filter(function(o){ return N.img[o.id]; }); ciz(); yenile(); }
  setInterval(function(){ var k = KV(); if (!k || !k.pid) return; var p = k.pid() || null; if (p !== N.pid){ N.pid = p; yukle(p); } }, 700);
  function yenile(){ try { KV() && KV().yenile(); } catch(e){} }

  /* ---------- çizim (Kareden Videoya kancası) ---------- */
  var ease = function(p){ p = Math.max(0, Math.min(1, p)); return p * p * (3 - 2 * p); };
  /* bitiş boşsa: kliplerin sonu (kapanış ekranının üstüne binmez) */
  function bitis(o){ if (o.t1 != null) return o.t1; var k = KV(); try { return k && k.klipSonu ? k.klipSonu() : Infinity; } catch(e){ return Infinity; } }
  /* zaman aralığı: klipten kesilen nesne o klibin aralığını izler (klip taşınsa, kısaltılsa da) */
  function aralik(o){ if (o.klip){ var k = KV(); try { return k && k.klipAralik ? k.klipAralik(o.klip) : null; } catch(e){ return null; } } return [o.t0 || 0, bitis(o), 0]; }
  function gorunur(o, t){ if (o.pasif || !N.img[o.id]) return false; var r = aralik(o); return !!r && t >= r[0] - 1e-4 && t < r[1]; }
  window.__kvNesneVar = function(t){ for (var i = 0; i < N.liste.length; i++) if (gorunur(N.liste[i], t)) return true; return false; };
  function durum(o, W, H, t){
    var im = N.img[o.id], h = o.s / 100 * H, w = h * im.width / im.height, cx = o.x * W, cy = o.y * H, sc = 1, a = (o.op == null ? 100 : o.op) / 100;
    var ar = aralik(o) || [0, Infinity, 0], el = t - ar[0], kal = ar[1] - t, G = 0.7, p = ease(el / G), kenar = 1;
    if (o.klip){ var f = Math.max(0.25, ar[2] || 0); kenar = Math.max(0, Math.min(1, ar[0] < 0.01 ? 1 : el / f, kal / f)); a *= kenar; }   /* klip sınırında yumuşak giriş/çıkış */
    if (o.hareket === 'belir') a *= p;
    else if (o.hareket === 'kay'){ var yon = o.x < 0.5 ? -1 : 1; cx += (1 - p) * yon * (W * 0.5 + w * 0.6); }
    else if (o.hareket === 'yaklas'){ sc = 0.55 + 0.45 * p; a *= p; }
    else if (o.hareket === 'suz'){ a *= ease(el / 0.5); cy += Math.sin(el * 1.7) * H * 0.009; sc = 1 + 0.012 * Math.sin(el * 0.9); }
    if (o.hareket && o.hareket !== 'sabit' && isFinite(kal)) a *= Math.min(1, Math.max(0, kal / 0.45));
    return {im:im, w:w, h:h, cx:cx, cy:cy, sc:sc, a:a, kenar:kenar, rot:(o.r || 0) * Math.PI / 180}; }
  /* arka plan: aynı fotoğrafın bulanık hâli, ekranı doldurur (dikey çerçevenin kenarları görünmez) */
  /* arka = true: bulanık aynı fotoğraf (klipten kesilende) · 'foto': kullanıcının seçtiği fotoğraf, ekranı doldurur */
  function arkaCiz(g, W, H, o, a){ var b = N.img[o.id + (o.arka === 'foto' ? '_af' : '_bg')]; if (!b || a <= 0.003) return; var k = Math.max(W / b.width, H / b.height), w = b.width * k, h = b.height * k;
    g.save(); g.globalAlpha = a; g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high'; g.drawImage(b, (W - w) / 2, (H - h) / 2, w, h); g.fillStyle = 'rgba(0,0,0,0.12)'; g.fillRect(0, 0, W, H); g.restore(); }
  window.__kvNesne = function(g, W, H, t, exp){
    if (!N.liste.length) return;
    if (!exp){ N.W = W; N.H = H; }
    for (var i = 0; i < N.liste.length; i++){ var o = N.liste[i]; if (!gorunur(o, t)) continue;
      var d = durum(o, W, H, t); if (!exp) N.geo[o.id] = {cx:d.cx, cy:d.cy, w:d.w * d.sc, h:d.h * d.sc, rot:d.rot};
      if (o.arka){ var ka = d.kenar; if (!o.klip){ var ar0 = aralik(o) || [0, Infinity], e0 = t - ar0[0], k0 = ar0[1] - t; ka = Math.max(0, Math.min(1, ar0[0] < 0.01 ? 1 : e0 / 0.4, k0 / 0.4)); } arkaCiz(g, W, H, o, ka); }
      if (d.a <= 0.003) continue;
      g.save(); g.globalAlpha = d.a; g.translate(d.cx, d.cy); g.rotate(d.rot); g.scale(d.sc * (o.ayna ? -1 : 1), d.sc);
      if (o.golge){ g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = H * 0.025; g.shadowOffsetY = H * 0.012; }
      g.imageSmoothingEnabled = true; g.imageSmoothingQuality = exp ? 'high' : 'medium';
      g.drawImage(d.im, -d.w / 2, -d.h / 2, d.w, d.h); g.restore();
      if (!exp){
        if (N.acik && o.id === N.sec){ g.save(); g.translate(d.cx, d.cy); g.rotate(d.rot); g.lineWidth = Math.max(2, H * 0.004); g.setLineDash([H * 0.014, H * 0.01]);
          g.strokeStyle = 'rgba(255,255,255,.9)'; g.strokeRect(-d.w * d.sc / 2, -d.h * d.sc / 2, d.w * d.sc, d.h * d.sc); g.restore(); } } }
  };

  /* ---------- arayüz ---------- */
  var css = document.createElement('style'); css.id = 'st-nesne-css';
  css.textContent = [
    '#stNesne{display:none}',
    'body.kv-studyo.is-mod #stNesne:not([hidden]){display:block;position:fixed;left:0;right:0;z-index:33;background:var(--is-bg2);color:var(--is-ink);border-radius:16px 16px 0 0;padding:4px 12px 10px;box-shadow:0 -8px 24px rgba(0,0,0,.25)}',
    '#stNesne .nb{display:flex;align-items:center;justify-content:space-between;font-size:15px;padding:2px 0 6px}',
    '#stNesne .nb button{background:none;border:0;color:var(--is-ink);width:40px;height:36px;display:grid;place-items:center;padding:0}',
    '#stNesne svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}',
    '#stNesne .ns{display:flex;gap:8px;overflow-x:auto;scrollbar-width:none;padding:2px 0 8px}',
    '#stNesne .ns::-webkit-scrollbar{display:none}',
    '#stNesne .ns>button{flex:none;width:58px;height:58px;border-radius:10px;border:2px solid transparent;padding:0;background:repeating-conic-gradient(#555 0 25%,#777 0 50%) 0 0/10px 10px;overflow:hidden;position:relative}',
    '#stNesne .ns>button img{width:100%;height:100%;object-fit:contain;display:block}',
    '#stNesne .ns>button.on{border-color:var(--is-ink)}',
    '#stNesne .ns>button.pas img{opacity:.35}',
    '#stNesne .ns>button.ekle{background:var(--is-bg);border:1.5px dashed color-mix(in srgb,var(--is-ink) 40%,transparent);color:var(--is-ink);display:grid;place-items:center}',
    '#stNesne .bos{display:flex;gap:12px;align-items:center;width:100%;background:var(--is-bg);border:1.5px dashed color-mix(in srgb,var(--is-ink) 35%,transparent);border-radius:14px;color:var(--is-ink);padding:12px;text-align:left;font:inherit}',
    '#stNesne .bos b{display:block;font-size:15px}#stNesne .bos small{display:block;color:var(--is-mute);font-size:12px;margin-top:2px}',
    '#stNesne .cp{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;margin:0 0 6px}',
    '#stNesne .cp::-webkit-scrollbar{display:none}',
    '#stNesne .cp button{flex:none;border:1.5px solid color-mix(in srgb,var(--is-ink) 16%,transparent);background:none;color:var(--is-mute);border-radius:999px;padding:6px 12px;font:inherit;font-size:13px}',
    '#stNesne .cp button.on{background:var(--is-ink);color:var(--is-bg);border-color:var(--is-ink)}',
    '#stNesne .kr{display:flex;align-items:center;gap:10px;margin:0 0 6px}',
    '#stNesne .kr input{flex:1;min-width:0;accent-color:var(--is-ink)}',
    '#stNesne .kr output{width:52px;text-align:right;font-variant-numeric:tabular-nums;font-size:13px;color:var(--is-mute)}',
    '#stNesne .zs{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:0 0 4px}',
    '#stNesne .zs button{background:var(--is-bg);border:0;border-radius:10px;color:var(--is-ink);padding:7px 8px;font:inherit;font-size:13px;text-align:left}',
    '#stNesne .zs button small{display:block;color:var(--is-mute);font-size:11px}',
    '#stNesne .not{color:var(--is-mute);font-size:11.5px;margin:2px 0 0}',
    '#stNesne button.sil.on{background:#e5534b!important;color:#fff!important;border-color:#e5534b!important}',
    '#stNesneIs{position:fixed;inset:0;z-index:60;background:rgba(0,0,0,.6);display:grid;place-items:center;padding:24px}',
    '#stNesneIs[hidden]{display:none}',
    '#stNesneIs>div{background:var(--is-bg2,#2a2a2a);color:var(--is-ink,#f3f3f3);border-radius:16px;padding:16px;width:min(360px,100%);font-size:14px}',
    '#stNesneIs b{display:block;font-size:16px;margin:0 0 6px}',
    '#stNesneIs .cb{height:6px;border-radius:3px;background:rgba(255,255,255,.15);overflow:hidden;margin:10px 0 4px}',
    '#stNesneIs .cb i{display:block;height:100%;width:0;background:#e8762b;transition:width .15s}',
    '#stNIsSec{display:grid;gap:8px;margin-top:12px}#stNIsSec button{margin:0!important}',
    '#stNesneIs button{margin-top:12px;width:100%;border:0;border-radius:10px;padding:10px;font:600 15px system-ui,sans-serif;background:var(--is-ink,#fff);color:var(--is-bg,#111)}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);
  var ic = function(p){ return '<svg viewBox="0 0 24 24" aria-hidden="true">' + p + '</svg>'; };
  var IC_KES = '<circle cx="6" cy="7" r="2.6"/><circle cx="6" cy="17" r="2.6"/><path d="M8.2 8.4L20 17M8.2 15.6L20 7"/>';
  var IC_NESNE = '<path d="M7 20c0-3 2-5 5-5s5 2 5 5"/><circle cx="12" cy="9" r="3.4"/><path d="M3 7V4h3M21 7V4h-3M3 17v3h3M21 17v3h-3" opacity=".7"/>';

  var sh = document.createElement('div'); sh.id = 'stNesne'; sh.hidden = true; document.body.appendChild(sh);
  var gir = document.createElement('input'); gir.type = 'file'; gir.accept = 'image/*'; gir.hidden = true; document.body.appendChild(gir);
  var is = document.createElement('div'); is.id = 'stNesneIs'; is.hidden = true; is.innerHTML = '<div><b>Nesne kesiliyor</b><div id="stNIsM">Hazırlanıyor…</div><div class="cb"><i id="stNIsC"></i></div><div class="not" id="stNIsA"></div><button type="button" id="stNIsT" hidden>Tamam</button></div>';
  document.body.appendChild(is);
  is.querySelector('#stNIsT').onclick = function(){ is.hidden = true; };

  function secili(){ for (var i = 0; i < N.liste.length; i++) if (N.liste[i].id === N.sec) return N.liste[i]; return null; }
  function zaman(t){ if (t == null || !isFinite(t)) return 'kliplerin sonu'; var m = Math.floor(t / 60), s = t - m * 60; return String(m).padStart(2, '0') + ':' + s.toFixed(1).padStart(4, '0').replace('.', ','); }
  var urlOnb = {};
  function kucuk(o){ if (urlOnb[o.id]) return urlOnb[o.id]; var im = N.img[o.id]; if (!im) return ''; var c = document.createElement('canvas'), k = 116 / Math.max(im.width, im.height);
    c.width = Math.max(1, Math.round(im.width * k)); c.height = Math.max(1, Math.round(im.height * k)); c.getContext('2d').drawImage(im, 0, 0, c.width, c.height); return urlOnb[o.id] = c.toDataURL('image/png'); }
  var silOnay = 0;
  function ciz(){
    if (!N.acik) return; var o = secili(), h = '';
    h += '<div class="nb"><b style="flex:1">Nesne</b><button type="button" data-a="geri" aria-label="Geri al"' + (N.gecmis.length ? '' : ' disabled style="opacity:.3"') + '>' + ic('<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 010 12h-3"/>') + '</button><button type="button" data-a="ileri" aria-label="İleri al"' + (N.ileriL.length ? '' : ' disabled style="opacity:.3"') + '>' + ic('<path d="M15 14l5-5-5-5"/><path d="M20 9H10a6 6 0 000 12h3"/>') + '</button><button type="button" data-a="kapat" aria-label="Tamam">' + ic('<path d="M5 12.5l4.5 4.5L19 7.5"/>') + '</button></div>';
    if (!N.liste.length){
      h += '<button type="button" class="bos" data-a="ekle"><span style="width:44px;height:44px;flex:none">' + ic(IC_NESNE).replace('<svg', '<svg style="width:44px;height:44px"') + '</span><span><b>Fotoğraftan nesne kes ve ekle</b><small>Bir fotoğraf seç; yapay zekâ nesneyi ya da kişiyi arka planından ayırır, videonun üstüne katman olarak koyar.</small></span></button>';
      h += '<button type="button" class="bos" data-a="klip" style="margin-top:8px"><span style="width:44px;height:44px;flex:none">' + ic(IC_KES).replace('<svg', '<svg style="width:44px;height:44px"') + '</span><span><b>Seçili klipteki kişiyi kes</b><small>Zaman çizelgesinde seçtiğin fotoğraftaki kişi yerinde kalır, arkası aynı fotoğrafın bulanık hâliyle dolar; dikey çerçeve kaybolur.</small></span></button>';
      h += '<p class="not">İlk kullanımda model iner (yaklaşık 90 MB, Wi-Fi önerilir); sonra telefonda kalır. Fotoğraf telefondan dışarı gönderilmez.</p>';
    } else {
      h += '<div class="ns">' + N.liste.map(function(x){ return '<button type="button" data-n="' + x.id + '" class="' + (x.id === N.sec ? 'on' : '') + (x.pasif ? ' pas' : '') + '"><img alt="" src="' + kucuk(x) + '"></button>'; }).join('')
        + '<button type="button" class="ekle" data-a="ekle" aria-label="Fotoğraftan nesne ekle">' + ic('<path d="M12 5v14M5 12h14"/>') + '</button>'
        + '<button type="button" class="ekle" data-a="klip" aria-label="Seçili klipteki kişiyi kes">' + ic(IC_KES) + '</button></div>';
      if (o){
        var A = AYAR.filter(function(a){ return a[0] === N.ayar; })[0] || AYAR[0], v = o[A[0]] == null ? (A[0] === 'op' ? 100 : 0) : o[A[0]];
        h += '<div class="cp">' + AYAR.map(function(a){ return '<button type="button" data-ay="' + a[0] + '" class="' + (a[0] === N.ayar ? 'on' : '') + '">' + a[1] + '</button>'; }).join('')
          + '<button type="button" data-t="golge" class="' + (o.golge ? 'on' : '') + '">Gölge</button><button type="button" data-t="ayna" class="' + (o.ayna ? 'on' : '') + '">Ayna</button>'
          + '<button type="button" data-a="ortala">Ortala</button><button type="button" data-t="pasif" class="' + (o.pasif ? 'on' : '') + '">Gizle</button></div>';
        h += '<div class="kr"><input type="range" id="stNk" min="' + A[2] + '" max="' + A[3] + '" step="1" value="' + Math.round(v) + '"><output id="stNo">' + Math.round(v) + A[4] + '</output></div>';
        h += '<div class="cp">' + HAREKET.map(function(a){ return '<button type="button" data-h="' + a[0] + '" class="' + ((o.hareket || 'sabit') === a[0] ? 'on' : '') + '">' + a[1] + '</button>'; }).join('') + '</div>';
        if (o.klip){
          h += '<div class="cp"><button type="button" data-ark="yok" class="' + (!o.arka ? 'on' : '') + '">Arka plan yok</button>' + (o.klip ? '<button type="button" data-ark="bulanik" class="' + (o.arka === true ? 'on' : '') + '">Bulanık</button>' : '') + '<button type="button" data-ark="foto" class="' + (o.arka === 'foto' ? 'on' : '') + '">' + (o.arka === 'foto' ? 'Fotoğraf · değiştir' : 'Arkaya fotoğraf') + '</button></div>';
          h += '<div class="cp"><button type="button" data-a="png">PNG kaydet</button><button type="button" data-a="sil" class="sil' + (silOnay > Date.now() ? ' on' : '') + '">' + (silOnay > Date.now() ? 'Silmek için yeniden dokun' : 'Sil') + '</button></div>';
          h += '<p class="not">Kendi klibi boyunca görünür; klip taşınır ya da kısaltılırsa birlikte gider. Sürükle, iki parmakla büyüt / döndür.</p>';
        } else {
        h += '<div class="zs"><button type="button" data-a="t0"><small>Başlangıç · dokun: şu an</small>' + zaman(o.t0 || 0) + '</button><button type="button" data-a="t1"><small>Bitiş · dokun: şu an</small>' + zaman(o.t1) + '</button></div>';
        h += '<div class="cp"><button type="button" data-ark="yok" class="' + (!o.arka ? 'on' : '') + '">Arka plan yok</button>' + (o.klip ? '<button type="button" data-ark="bulanik" class="' + (o.arka === true ? 'on' : '') + '">Bulanık</button>' : '') + '<button type="button" data-ark="foto" class="' + (o.arka === 'foto' ? 'on' : '') + '">' + (o.arka === 'foto' ? 'Fotoğraf · değiştir' : 'Arkaya fotoğraf') + '</button></div>';
        h += '<div class="cp"><button type="button" data-a="bastan">Baştan</button><button type="button" data-a="sona">Kliplerin sonuna</button><button type="button" data-a="png">PNG kaydet</button><button type="button" data-a="sil" class="sil' + (silOnay > Date.now() ? ' on' : '') + '">' + (silOnay > Date.now() ? 'Silmek için yeniden dokun' : 'Sil') + '</button></div>';
        h += '<p class="not">Önizlemede nesneyi sürükleyerek taşı; iki parmakla büyüt, küçült, döndür.</p>';
        }
      }
    }
    sh.innerHTML = h; yerles();
  }
  function degisti(yenidenCiz){ kaydet(); yenile(); if (yenidenCiz) ciz(); }
  sh.addEventListener('input', function(e){ if (e.target.id !== 'stNk') return; var o = secili(); if (!o) return; var A = AYAR.filter(function(a){ return a[0] === N.ayar; })[0];
    anlik(false); o[A[0]] = +e.target.value; var out = sh.querySelector('#stNo'); if (out) out.textContent = e.target.value + A[4]; degisti(false); });
  sh.addEventListener('click', function(e){
    var b = e.target.closest('button'); if (!b) return; var o = secili(), k = KV();
    if ((b.dataset.ark && b.dataset.ark !== 'foto') || b.dataset.h || b.dataset.t || ['ortala','t0','t1','bastan','sona'].indexOf(b.dataset.a) >= 0) if (o) anlik(true);
    if (b.dataset.a === 'geri'){ geriAl(false); return; } if (b.dataset.a === 'ileri'){ geriAl(true); return; }
    if (b.dataset.n){ N.sec = b.dataset.n; silOnay = 0; ciz(); yenile(); return; }
    if (b.dataset.ay){ N.ayar = b.dataset.ay; ciz(); return; }
    if (b.dataset.h && o){ o.hareket = b.dataset.h; degisti(true); return; }
    if (b.dataset.ark && o){ if (b.dataset.ark === 'foto'){ arkaSec(o); return; } o.arka = b.dataset.ark === 'bulanik'; degisti(true); return; }
    if (b.dataset.t && o){ o[b.dataset.t] = !o[b.dataset.t]; degisti(true); return; }
    var a = b.dataset.a;
    if (a === 'kapat'){ kapat(); return; }
    if (a === 'ekle'){ ekleBasla(); return; }
    if (a === 'klip'){ klipKes(); return; }
    if (!o) return;
    if (a === 'ortala'){ o.x = 0.5; o.y = 0.5; o.r = 0; degisti(true); }
    else if (a === 't0'){ var t = k ? k.an() : 0; o.t0 = Math.round(t * 10) / 10; if (o.t1 != null && o.t1 <= o.t0 + 0.2) o.t1 = null; degisti(true); }
    else if (a === 't1'){ var t1 = k ? k.an() : 0; if (t1 <= (o.t0 || 0) + 0.2){ uyari('Bitiş, başlangıçtan sonra olmalı. Önce zaman çizelgesinde ileri git.'); return; } o.t1 = Math.round(t1 * 10) / 10; degisti(true); }
    else if (a === 'bastan'){ o.t0 = 0; degisti(true); }
    else if (a === 'sona'){ o.t1 = null; degisti(true); }
    else if (a === 'png'){ pngKaydet(o); }
    else if (a === 'sil'){ if (silOnay > Date.now()){ silOnay = 0; anlik(true); N.liste = N.liste.filter(function(x){ return x !== o; }); N.sec = N.liste.length ? N.liste[N.liste.length - 1].id : null;
        N.silinen[o.id] = 1; degisti(true); }
      else { silOnay = Date.now() + 3000; ciz(); setTimeout(ciz, 3100); } }
  });
  function uyari(m){ is.hidden = false; is.querySelector('b').textContent = 'Nesne'; is.querySelector('#stNIsM').textContent = m; is.querySelector('#stNIsC').style.width = '0'; is.querySelector('#stNIsA').textContent = ''; is.querySelector('#stNIsT').hidden = false; }
  async function pngKaydet(o){ try { var b = await idb('png', 'readonly', function(s){ return s.get(o.id); }); if (!b) return;
    var a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = (o.ad || 'nesne') + '-kesik.png'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(function(){ URL.revokeObjectURL(a.href); }, 4000); } catch(e){ uyari('Kaydedilemedi: ' + e.message); } }

  /* ---------- ekleme: fotoğraf seç → kes → katman ---------- */
  var motorSoz = null;
  function motorYukle(){ if (window.stYZ) return Promise.resolve(window.stYZ); if (motorSoz) return motorSoz;
    motorSoz = new Promise(function(ok, no){ var s = document.createElement('script');
      /* Cloudflare'de motor dosyası (26,8 MB) 25 MB sınırını aştığı için konmadı: motor ve model GitHub adresinden yüklenir (CORS açık) */
      s.src = /pages\.dev$/.test(location.hostname) ? 'https://aaskinkk-cloud.github.io/studyo/yz/motor.js' : new URL('../yz/motor.js', location.href).href;
      s.onload = function(){ window.stYZ ? ok(window.stYZ) : no(new Error('motor yok')); }; s.onerror = function(){ motorSoz = null; no(new Error('Yapay zekâ motoru yüklenemedi. İnternet bağlantısını kontrol et.')); };
      document.head.appendChild(s); }); return motorSoz; }
  async function ekleBasla(){
    var k = KV(); if (!k){ uyari('Video editörü hazır değil.'); return; }
    if (!k.klipVar()){ uyari('Önce videoya en az bir fotoğraf ya da video ekle; nesne onun üstüne konur.'); return; }
    k.durdur(); gir.value = ''; gir.click(); }
  /* arkaya fotoğraf: seçilen fotoğraf küçültülüp (uzun kenar 1920) saklanır, ekranı doldurarak nesnenin arkasına çizilir */
  var arkaGir = document.createElement('input'); arkaGir.type = 'file'; arkaGir.accept = 'image/*'; arkaGir.hidden = true; document.body.appendChild(arkaGir); var arkaHedef = null;
  function arkaSec(o){ var k = KV(); if (k) k.durdur(); arkaHedef = o; arkaGir.value = ''; arkaGir.click(); }
  arkaGir.addEventListener('change', async function(){ var f = arkaGir.files && arkaGir.files[0], o = arkaHedef; arkaGir.value = ''; arkaHedef = null; if (!f || !o) return;
    try { var b = await createImageBitmap(f, {imageOrientation:'from-image'}), q = Math.min(1, 1920 / Math.max(b.width, b.height)), c = document.createElement('canvas');
      c.width = Math.round(b.width * q); c.height = Math.round(b.height * q); var x = c.getContext('2d'); x.imageSmoothingQuality = 'high'; x.drawImage(b, 0, 0, c.width, c.height); try { b.close(); } catch(e){}
      var jb = await new Promise(function(r){ c.toBlob(r, 'image/jpeg', 0.9); }); c.width = 0;
      await idb('png', 'readwrite', function(s){ return s.put(jb, o.id + '_af'); }); N.img[o.id + '_af'] = await createImageBitmap(jb); anlik(true); o.arka = 'foto'; degisti(true);
    } catch(e){ console.error(e); uyari('Arka plan fotoğrafı açılamadı: ' + (e && e.message || e)); } });
  /* seçili klipteki kişiyi kes: nesne yerinde kalır, arkası aynı fotoğrafın bulanık hâli */
  function isAc(baslik){ is.hidden = false; is.querySelector('b').textContent = baslik; is.querySelector('#stNIsT').hidden = true; is.querySelector('#stNIsA').textContent = '';
    var m = is.querySelector('#stNIsM'), c = is.querySelector('#stNIsC'); return function(t, o){ m.textContent = t; if (o != null) c.style.width = Math.round(o * 100) + '%'; }; }
  async function bulanik(dosya){ var b = await createImageBitmap(dosya, {imageOrientation:'from-image'}), k = 420 / Math.max(b.width, b.height), w = Math.max(8, Math.round(b.width * k)), h = Math.max(8, Math.round(b.height * k));
    var c = document.createElement('canvas'); c.width = w; c.height = h; var x = c.getContext('2d'); x.filter = 'blur(' + Math.round(Math.max(w, h) * 0.035) + 'px)';
    x.drawImage(b, -w * 0.08, -h * 0.08, w * 1.16, h * 1.16); x.filter = 'none'; try { b.close(); } catch(e){}
    return await new Promise(function(r){ c.toBlob(r, 'image/jpeg', 0.85); }); }
  async function klipKes(){
    var k = KV(); if (!k){ uyari('Video editörü hazır değil.'); return; }
    k.durdur(); var sk = k.secKlip();
    if (!sk){ uyari('Önce zaman çizelgesinde bir fotoğraf klibine dokun.'); return; }
    if (sk.kind !== 'photo'){ uyari('Seçili klip bir video. Videoda kişiyi kare kare takip ederek kesme sonraki adımda gelecek; şimdilik fotoğraf kliplerinde çalışır.'); return; }
    if (N.liste.some(function(o){ return o.klip && o.klip === sk.fkey; })){ uyari('Bu klipten zaten kesilmiş bir nesne var. Nesne panelinden düzenleyebilir ya da silip yeniden kesebilirsin.'); return; }
    var bildir = isAc('Klipteki kişi kesiliyor');
    try {
      bildir('Proje kaydediliyor…', 0.01); await k.kaydet(); N.pid = k.pid() || N.pid; sk = k.secKlip();
      if (!N.pid || !sk || !sk.fkey || !sk.file) throw new Error('Proje kaydedilemedi; yeniden dene.');
      /* klibin ekrandaki yatay konumu (Kareden Videoya'nın kendi çiziminden, varsa) */
      var geo = null; try { geo = k.klipGeo ? k.klipGeo(sk.i) : null; } catch(e){ geo = null; }
      var YZ = await motorYukle();
      var r = await YZ.kes(sk.file, bildir);
      bildir('Arka plan hazırlanıyor…', 0.97);
      var id = 'n' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6), bg = await bulanik(sk.file);
      await idb('png', 'readwrite', function(s){ s.put(r.png, id); return s.put(bg, id + '_bg'); });
      N.img[id] = await createImageBitmap(r.png); N.img[id + '_bg'] = await createImageBitmap(bg);
      /* klibin ekrandaki yeri (Kareden Videoya'nın çizimiyle aynı hesap, Ken Burns hariç) → nesne tam yerine oturur */
      /* yerleşim: kişinin tamamı görünsün (Ken Burns / Doldur yakınlaştırması kişiyi kesmesin).
         Yükseklik ekranın %85'i (geniş nesnede ekrana sığacak kadar), yatayda klipteki yerine yakın, fotoğrafın altına değiyorsa ekranın altına oturur. */
      var W = N.W || 1920, H = N.H || 1080, oran = r.w / r.h, s = 85, wf = s / 100 * H * oran / W;
      if (wf > 0.9){ s = Math.max(10, Math.round(0.9 * W / (H * oran) * 100)); wf = s / 100 * H * oran / W; }
      var mx = geo ? geo.cx + (r.x + r.w / r.W / 2 - 0.5) * geo.fw : 0.5; mx = Math.max(wf / 2 + 0.02, Math.min(1 - wf / 2 - 0.02, mx));
      var alta = r.y + r.h / r.H > 0.97, my = alta ? 1 - s / 200 : 0.5;
      var o = {id:id, ad:'Klip ' + (sk.i + 1), klip:sk.fkey, arka:true, x:mx, y:my, s:s, r:0, op:100, golge:true, ayna:false, hareket:'sabit'};
      anlik(true); N.liste.push(o); N.sec = id; kaydet(); yenile();
      is.querySelector('b').textContent = 'Kişi kesildi';
      bildir((r.motor === 'grafik birimi' ? 'Grafik birimiyle' : 'İşlemciyle') + ' ' + (r.ms / 1000).toFixed(1).replace('.', ',') + ' sn. Kişi tam görünür, arkası bulanık; sürükleyip büyütebilirsin.', 1);
      is.querySelector('#stNIsT').hidden = false; setTimeout(function(){ if (is.querySelector('b').textContent === 'Kişi kesildi') is.hidden = true; }, 2500);
      if (!N.acik) ac(); else ciz();
    } catch(e){ console.error(e); is.querySelector('b').textContent = 'Olmadı'; bildir(String(e && e.message || e), 0); is.querySelector('#stNIsT').hidden = false; }
  }
  /* seçili klip araç çubuğuna "Kişiyi kes" */
  function klipButonKur(){ var ka = document.getElementById('isKlipAr'); if (!ka || ka.querySelector('#stKesBtn')) return;
    var b = document.createElement('button'); b.type = 'button'; b.id = 'stKesBtn'; b.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" style="width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round">' + IC_KES + '</svg><span>Kişiyi kes</span>';
    b.addEventListener('click', function(e){ e.stopPropagation(); klipKes(); });
    var ilk = ka.querySelector('button'); ilk ? ilk.after(b) : ka.prepend(b); }
  (function bagla2(n){ var ka = document.getElementById('isKlipAr'); if (!ka){ if (n < 150) setTimeout(function(){ bagla2(n + 1); }, 400); return; }
    klipButonKur(); new MutationObserver(klipButonKur).observe(ka, {childList:true}); })(0);
  gir.addEventListener('change', async function(){
    var f = gir.files && gir.files[0]; gir.value = ''; if (!f) return; var k = KV();
    is.hidden = false; is.querySelector('b').textContent = 'Nesne kesiliyor'; is.querySelector('#stNIsT').hidden = true; is.querySelector('#stNIsA').textContent = '';
    var m = is.querySelector('#stNIsM'), c = is.querySelector('#stNIsC'); var bildir = function(t, o){ m.textContent = t; if (o != null) c.style.width = Math.round(o * 100) + '%'; };
    try {
      bildir('Proje kaydediliyor…', 0.01);
      if (!N.pid){ await k.kaydet(); N.pid = k.pid() || null; }
      if (!N.pid) throw new Error('Proje kaydedilemedi; bir klip ekleyip yeniden dene.');
      var YZ = await motorYukle();
      var r = await YZ.kes(f, bildir, {parcalar:true});
      if (r.parcalar && r.parcalar.length > 1){
        var sec = await secim(r.parcalar.length);
        if (sec === 'ayri'){ await ayriEkle(r, f); return; } }
      var id = 'n' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
      await idb('png', 'readwrite', function(s){ return s.put(r.png, id); });
      N.img[id] = await createImageBitmap(r.png);
      var W = N.W || 1920, H = N.H || 1080, oran = r.w / r.h, s = 80;
      if (oran * s / 100 * H > W * 0.9) s = Math.max(10, Math.round(W * 0.9 / oran / H * 100));   /* geniş nesne ekrana sığsın */
      var o = {id:id, ad:(f.name || 'nesne').replace(/\.[^.]+$/, '').slice(0, 40), x:0.5, y:Math.min(0.5, 0.97 - s / 200), s:s, r:0, op:100, golge:true, ayna:false, hareket:'belir', t0:0, t1:null};   /* varsayılan: videonun başından sonuna; Başlangıç/Bitiş ile daraltılır */
      anlik(true); N.liste.push(o); N.sec = id; degisti(true);
      is.querySelector('b').textContent = 'Nesne eklendi';
      bildir((r.motor === 'grafik birimi' ? 'Grafik birimiyle' : 'İşlemciyle') + ' ' + (r.ms / 1000).toFixed(1).replace('.', ',') + ' sn' + (r.indi ? ' (model indirme dahil)' : '') + '. Önizlemede sürükleyerek yerleştir.', 1);
      is.querySelector('#stNIsT').hidden = false; setTimeout(function(){ if (!is.querySelector('#stNIsT').hidden && is.querySelector('b').textContent === 'Nesne eklendi') is.hidden = true; }, 2500);
    } catch(e){ console.error(e); is.querySelector('b').textContent = 'Olmadı'; bildir(String(e && e.message || e), 0); is.querySelector('#stNIsT').hidden = false; }
  });

  /* fotoğrafta birden çok nesne: tek katman mı, her biri ayrı katman mı */
  function secim(n){ return new Promise(function(ok){ var t = is.querySelector('#stNIsT'), kutu = document.createElement('div'); kutu.id = 'stNIsSec';
    is.querySelector('b').textContent = 'Fotoğrafta ' + n + ' ayrı nesne bulundu'; is.querySelector('#stNIsM').textContent = 'Hepsi tek katman olarak mı eklensin, yoksa her biri ayrı katman mı (ayrı ayrı taşınır, büyütülür, zamanlanır)?';
    t.hidden = true; kutu.innerHTML = '<button type="button" data-s="ayri">' + n + ' ayrı katman</button><button type="button" data-s="tek" style="background:transparent;color:inherit;border:1.5px solid currentColor">Tek katman</button>';
    is.querySelector('#stNIsT').after(kutu);
    kutu.addEventListener('click', function(e){ var b = e.target.closest('button'); if (!b) return; kutu.remove(); ok(b.dataset.s); }); }); }
  async function ayriEkle(r, f){
    var W = N.W || 1920, H = N.H || 1080, ad = (f.name || 'nesne').replace(/\.[^.]+$/, '').slice(0, 34), k = KV();
    /* fotoğrafın sanal çerçevesi: yüksekliğin %85'i, ortada, altta hafif boşluk; parçalar fotoğraftaki yerlerine göre dizilir */
    var PH = 0.85 * H, PW = PH * r.W / r.H; if (PW > 0.95 * W){ PW = 0.95 * W; PH = PW * r.H / r.W; } var ust = 0.97 - PH / H, ilk = null;
    anlik(true);
    for (var i = 0; i < r.parcalar.length; i++){ var p = r.parcalar[i], id = 'n' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6) + i;
      await idb('png', 'readwrite', function(s){ return s.put(p.png, id); }); N.img[id] = await createImageBitmap(p.png);
      var wf = p.w / r.W, hf = p.h / r.H;
      N.liste.push({id:id, ad:ad + ' ' + (i + 1), x:0.5 + (p.x + wf / 2 - 0.5) * PW / W, y:ust + (p.y + hf / 2) * PH / H, s:Math.max(3, hf * PH / H * 100), r:0, op:100, golge:true, ayna:false, hareket:'belir', t0:0, t1:null});
      if (!ilk) ilk = id; }
    N.sec = ilk; degisti(true);
    is.querySelector('b').textContent = 'Nesne eklendi';
    is.querySelector('#stNIsM').textContent = r.parcalar.length + ' nesne ayrı katman olarak eklendi (' + (r.motor === 'grafik birimi' ? 'grafik birimiyle' : 'işlemciyle') + ' ' + (r.ms / 1000).toFixed(1).replace('.', ',') + ' sn). Panelde her birini seçip ayrı ayarla.';
    is.querySelector('#stNIsT').hidden = false; }

  /* ---------- geri al / ileri al (katman ayarları) ---------- */
  N.gecmis = []; N.ileriL = []; N.sonAnlik = 0; N.silinen = {};
  function anlik(zorla){ var t = Date.now(); if (!zorla && t - N.sonAnlik < 700){ N.sonAnlik = t; return; } N.sonAnlik = t;
    N.gecmis.push(JSON.stringify(N.liste)); if (N.gecmis.length > 30) N.gecmis.shift(); N.ileriL = []; }
  function geriAl(ileri){ var A = ileri ? N.ileriL : N.gecmis, B = ileri ? N.gecmis : N.ileriL; if (!A.length) return;
    B.push(JSON.stringify(N.liste)); N.liste = JSON.parse(A.pop()).filter(function(o){ return N.img[o.id]; });
    if (!N.liste.some(function(o){ return o.id === N.sec; })) N.sec = N.liste.length ? N.liste[N.liste.length - 1].id : null; N.sonAnlik = 0; degisti(true); }
  /* silinen görüntüler panel kapanınca veritabanından silinir (o zamana kadar geri alınabilir) */
  function copTopla(){ var ids = Object.keys(N.silinen); N.silinen = {}; ids.forEach(function(id){ if (N.liste.some(function(o){ return o.id === id; })) return;
    idb('png', 'readwrite', function(s){ s.delete(id + '_bg'); s.delete(id + '_af'); return s.delete(id); }).catch(function(){}); delete N.img[id]; delete N.img[id + '_bg']; delete N.img[id + '_af']; delete urlOnb[id]; }); }

  /* ---------- aç / kapat / yerleşim ---------- */
  var altSarili = false;
  function altSar(){ if (altSarili || typeof window.isAltEk !== 'function') return; altSarili = true; var eski = window.isAltEk;
    window.isAltEk = function(){ var v = eski.apply(this, arguments) || 0; if (N.acik && !sh.hidden && B.classList.contains('kv-kapali')) v += sh.offsetHeight; return v; }; }
  function yerles(){ var mn = document.getElementById('mininav'), zs = parseFloat(getComputedStyle(B).getPropertyValue('--isZsH')) || 0;
    sh.style.bottom = (zs + (mn && !mn.hidden ? mn.offsetHeight : 0)) + 'px'; try { window.kvStudyo && kvStudyo.yerlesim(); } catch(e){} }
  function ac(){ var k = KV(); if (k) k.durdur(); try { window.kvStudyo && kvStudyo.kapat(true); } catch(e){}
    ['isTuval', 'isMetin', 'isOdak'].forEach(function(id){ var x = document.getElementById(id); if (x && !x.hidden && id !== 'isOdak') x.hidden = true; });
    altSar(); N.acik = true; sh.hidden = false; if (!N.sec && N.liste.length) N.sec = N.liste[N.liste.length - 1].id; ciz(); butonDurum(); yenile(); setTimeout(yerles, 60); }
  function kapat(){ copTopla(); N.acik = false; sh.hidden = true; silOnay = 0; butonDurum(); yenile(); setTimeout(yerles, 30); }
  function butonDurum(){ var b = document.getElementById('stNsBtn'); if (b) b.setAttribute('aria-current', N.acik ? 'true' : 'false'); }
  function butonKur(){ var mn = document.getElementById('mininav'); if (!mn || mn.querySelector('#stNsBtn')) return;
    var b = document.createElement('button'); b.type = 'button'; b.id = 'stNsBtn'; b.innerHTML = ic(IC_NESNE) + '<span>Nesne</span>';
    b.addEventListener('click', function(e){ e.stopPropagation(); N.acik ? kapat() : ac(); });
    var mt = mn.querySelector('#isMtBtn'); mt ? mt.after(b) : mn.prepend(b); }
  (function bagla(n){ var mn = document.getElementById('mininav'); if (!mn){ if (n < 150) setTimeout(function(){ bagla(n + 1); }, 400); return; }
    butonKur(); new MutationObserver(butonKur).observe(mn, {childList:true});
    /* başka bir araç açılınca Nesne kapanır */
    mn.addEventListener('click', function(e){ var b = e.target.closest('button'); if (b && b.id !== 'stNsBtn' && N.acik) kapat(); }, true);
    addEventListener('resize', function(){ if (N.acik) yerles(); }); })(0);
  new MutationObserver(function(){ if (N.acik && (!B.classList.contains('kv-kapali') || B.classList.contains('kv-klv'))) kapat(); }).observe(B, {attributes:true, attributeFilter:['class']});

  /* ---------- önizlemede sürükle / iki parmakla boyut ve açı ---------- */
  var P = {}, surukle = null;
  function noktaDonustur(e){ var cv = document.getElementById('pv'); if (!cv) return null; var r = cv.getBoundingClientRect(), W = N.W, H = N.H, k = Math.min(r.width / W, r.height / H);
    var ox = r.left + (r.width - W * k) / 2, oy = r.top + (r.height - H * k) / 2; return {x:(e.clientX - ox) / k, y:(e.clientY - oy) / k}; }
  function isabet(p){ var k = KV(), t = k ? k.an() : 0;
    for (var i = N.liste.length - 1; i >= 0; i--){ var o = N.liste[i], g = N.geo[o.id]; if (!g || !gorunur(o, t)) continue;
      var dx = p.x - g.cx, dy = p.y - g.cy, c = Math.cos(-g.rot), s = Math.sin(-g.rot), lx = dx * c - dy * s, ly = dx * s + dy * c, pay = Math.max(g.w, g.h) * 0.06;
      if (Math.abs(lx) <= g.w / 2 + pay && Math.abs(ly) <= g.h / 2 + pay) return o; }
    return null; }
  function icinde(e){ var v = document.getElementById('viewer'); return v && v.contains(e.target); }
  addEventListener('pointerdown', function(e){
    if (!N.acik || !icinde(e)) return; var p = noktaDonustur(e); if (!p) return;
    if (surukle && surukle.o){ P[e.pointerId] = p; if (Object.keys(P).length === 2) ikiBasla(); e.stopImmediatePropagation(); e.preventDefault(); return; }
    var o = isabet(p); if (!o) return;
    e.stopImmediatePropagation(); e.preventDefault(); var k = KV(); if (k) k.durdur();
    if (N.sec !== o.id){ N.sec = o.id; ciz(); }
    anlik(true); P = {}; P[e.pointerId] = p; surukle = {o:o, p0:p, x0:o.x, y0:o.y, oy:false}; N._yut = true;
    try { e.target.setPointerCapture && e.target.setPointerCapture(e.pointerId); } catch(_){}
  }, true);
  function ikiBasla(){ var ks = Object.keys(P), a = P[ks[0]], b = P[ks[1]]; surukle.iki = {d:Math.hypot(b.x - a.x, b.y - a.y) || 1, aci:Math.atan2(b.y - a.y, b.x - a.x), s0:surukle.o.s, r0:surukle.o.r || 0, mx:(a.x + b.x) / 2, my:(a.y + b.y) / 2, x0:surukle.o.x, y0:surukle.o.y}; }
  addEventListener('pointermove', function(e){
    if (!surukle || !(e.pointerId in P)) return; var p = noktaDonustur(e); if (!p) return; P[e.pointerId] = p; e.stopImmediatePropagation(); e.preventDefault();
    var o = surukle.o, W = N.W, H = N.H, ks = Object.keys(P);
    if (ks.length >= 2 && surukle.iki){ var a = P[ks[0]], b = P[ks[1]], I = surukle.iki, d = Math.hypot(b.x - a.x, b.y - a.y);
      o.s = Math.max(10, Math.min(150, I.s0 * d / I.d)); var r = I.r0 + (Math.atan2(b.y - a.y, b.x - a.x) - I.aci) * 180 / Math.PI; r = ((r + 540) % 360) - 180; if (Math.abs(r) < 3) r = 0; o.r = Math.round(r);
      o.x = I.x0 + ((a.x + b.x) / 2 - I.mx) / W; o.y = I.y0 + ((a.y + b.y) / 2 - I.my) / H; }
    else { o.x = surukle.x0 + (p.x - surukle.p0.x) / W; o.y = surukle.y0 + (p.y - surukle.p0.y) / H;
      if (Math.abs(o.x - 0.5) < 0.012) o.x = 0.5; }   /* ortaya yapışır */
    o.x = Math.max(-0.4, Math.min(1.4, o.x)); o.y = Math.max(-0.4, Math.min(1.4, o.y)); surukle.oy = true; yenile();
  }, true);
  function birak(e){ if (!surukle || !(e.pointerId in P)) return; delete P[e.pointerId]; e.stopImmediatePropagation();
    if (Object.keys(P).length === 1 && surukle.iki){ var k0 = Object.keys(P)[0]; surukle.iki = null; surukle.p0 = P[k0]; surukle.x0 = surukle.o.x; surukle.y0 = surukle.o.y; return; }
    if (!Object.keys(P).length){ if (surukle.oy){ kaydet(); ciz(); } surukle = null; } }
  addEventListener('pointerup', birak, true); addEventListener('pointercancel', birak, true);
  /* sürüklerken önizlemenin kendi tıklaması (oynat/durdur) çalışmasın */
  addEventListener('click', function(e){ if (N.acik && icinde(e) && N._yut){ e.stopImmediatePropagation(); e.preventDefault(); } N._yut = false; }, true);
})();
