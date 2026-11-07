/* ===== STÜDYO 2.8 · ÖNİZLEMEDE YAZIYA DOKUN → EKRANDA DÜZENLE · RASTGELE HAREKET YÖNÜ =====
   Kareden Videoya'nın kapanışının İÇİNDE çalışır (esitle.py boot() satırından hemen önce ekler).
   1) Önizlemede bir yazıya (başlık, ek yazılar, vurgu bantları, alt başlık, kanca, abone hatırlatıcı, kapanış, altyazı,
      Metin yazıları) tek dokunuş: ilgili bölüm açılır (sekme, ara sekme ve sayfa seçilir, alan parlar) ve önizlemenin
      üstünde düzenleme kutusu çıkar; yazdıkça hem alan hem görüntü anında değişir. Çift dokunuş eskisi gibi tam ekran.
      Hangi yazıya dokunulduğu, her aday yazı geçici olarak değiştirilip dokunulan yerdeki piksel farkına bakılarak bulunur
      (çizim yolundan bağımsız). Kayıt biçimi değişmez.
   2) Hareket efekti › Otomatik: klip başına (kimliğinden türeyen, önizleme ve dışa aktarmada aynı) rastgele yön.
      Dikey fotoğraf: aşağı / yukarı kayma (kadrajına varış ya da kadrajından ayrılış); yüzlü dikey fotoğraf: yüze üstten ya da alttan varış (iki yanda yer varsa);
      yatay fotoğraf: yaklaşma ya da uzaklaşma. Diğer modlar (Aşağıdan yukarı, Yukarıdan aşağı, Hep yaklaş, Sırayla) aynen. */
if (window.STUDYO) (function(){
  /* ---------- 2) rastgele hareket yönü (Otomatik) ---------- */
  const kbRnd = (c, s) => { let h = (Math.imul(c.id | 0, 374761393) + Math.imul(s | 0, 668265263)) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h = Math.imul(h ^ (h >>> 16), 2246822507); h ^= h >>> 13; return (h >>> 0)/4294967296; };
  /* sıradaki kliplerde yön: rastgele, ama aynı yön üst üste üçten fazla gelmez (hepsi aynı yöne gidiyormuş gibi görünmesin) */
  const kbYon = (c, s) => { const n = S.clips.indexOf(c); if (n < 0) return kbRnd(c, s) < 0.5 ? 1 : -1;
    let p1 = 0, p2 = 0, m = 1; for (let i = 0; i <= n; i++){ m = kbRnd(S.clips[i], s) < 0.5 ? 1 : -1; if (m === p1 && m === p2) m = -m; p2 = p1; p1 = m; } return m; };
  const _pm = panMode;
  panMode = function(c, i){
    const k = KB(c);
    if (k.on && k.mode === 'auto' && c.kind === 'photo' && !faceZoomOn(c) && !tallFace(c) && isTall(c)) return kbRnd(c, 1) < 0.5 ? 'up' : 'down';
    return _pm(c, i);
  };
  const _po = panOy;
  panOy = function(c, pm, mxY, p, T){
    if (KB(c).mode !== 'auto') return _po(c, pm, mxY, p, T);
    if (pm === 'up' || pm === 'down'){   /* kayma: boş olan yana (ikisi de boşsa rastgele); kullanıcının kadrajına varış ya da oradan ayrılış rastgele */
      const tr = Math.min(2*mxY, 0.06*KB(c).rate*(c.dur + T)), f = clamp(c.oy, -mxY, mxY), up = f + mxY, dn = mxY - f;
      let yan = up >= dn ? -1 : 1; if (Math.min(up, dn) >= Math.min(tr, 0.12)) yan = kbRnd(c, 1) < 0.5 ? -1 : 1;
      const m = kbYon(c, 4), ters = -yan !== m;   /* m: görüntünün kayacağı yön */
      const a = clamp(f + yan*tr, -mxY, mxY), q = ters ? 1 - p : p, e = q*q*(3 - 2*q);
      return a + (f - a)*e;
    }
    if (pm !== 'face') return _po(c, pm, mxY, p, T);
    const tr = Math.min(2*mxY, 0.06*KB(c).rate*(c.dur + T)), f = clamp(c.oy, -mxY, mxY), d = Math.max(tr, 0.12);
    const roomUp = f + mxY, roomDn = mxY - f;
    let ust = roomUp >= roomDn; if (Math.min(roomUp, roomDn) >= d*0.6) ust = kbRnd(c, 2) < 0.5;   /* iki yanda da yer varsa yön rastgele */
    const a = ust ? Math.max(-mxY, f - d) : Math.min(mxY, f + d);
    const q = clamp(p/FACE_STOP, 0, 1), e = 1 - Math.pow(1 - q, 3); return a + (f - a)*e;
  };
  const _ks = kbScale;
  kbScale = function(c, i, local, T){
    const k = KB(c);
    if (k.on && k.mode === 'auto' && c.kind === 'photo' && !faceZoomOn(c) && !panMode(c, i) && kbYon(c, 3) < 0){   /* yatay fotoğraf: yaklaş ya da uzaklaş */
      const r = k.rate/100, el = clamp(local + T, 0, c.dur + T), full = Math.min(KBCAP, r*(c.dur + T)), amt = Math.min(KBCAP, r*el);
      return 1 + full - amt; }
    return _ks(c, i, local, T);
  };
  try { const n = document.querySelector('#kbSect [data-k="kb.mode"]'); const p = n && n.nextElementSibling;
    if (p && p.matches('p.note')) p.insertAdjacentHTML('afterbegin', '<b>Otomatik:</b> her fotoğraf kendi rastgele yönünde hareket eder (dikeyler aşağı ya da yukarı kayar, yataylar yaklaşır ya da uzaklaşır; önizleme ve video aynı). Hepsinin aynı yöne gitmesini istersen diğer seçenekleri kullan. '); } catch(e){}

  /* ---------- 1) önizlemede yazıya dokun ---------- */
  const css = document.createElement('style'); css.id = 'st-yz-css';
  css.textContent = `
#stYzKutu{position:fixed;z-index:210;left:12px;right:12px;max-width:560px;margin:0 auto;padding:6px 6px 6px 10px;border-radius:12px;background:rgba(18,22,28,.84);backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);box-shadow:0 8px 30px rgba(0,0,0,.45);border:1px solid rgba(255,255,255,.14);color:#fff;display:flex;flex-direction:column;gap:4px}
#stYzKutu[hidden]{display:none}
#stYzKutu .ust{display:flex;align-items:center;gap:8px;font:600 11.5px/1.2 system-ui,sans-serif;color:rgba(255,255,255,.75)}
#stYzKutu .ust span{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#stYzKutu .ust button{border:0;background:#e8a33d;color:#1a1206;font:700 13px/1 system-ui,sans-serif;border-radius:999px;padding:6px 12px}
#stYzKutu textarea{width:100%;box-sizing:border-box;resize:none;border:0;outline:0;border-radius:9px;background:rgba(255,255,255,.1);color:#fff;font:700 15px/1.3 system-ui,sans-serif;padding:6px 9px;min-height:34px;max-height:96px}
#stYzKutu textarea:focus{background:rgba(255,255,255,.16)}`;
  (document.head || document.documentElement).appendChild(css);

  const engel = () => exporting || playing || tkSecim || KF.duzen || activeTab === 'logo' || document.body.classList.contains('st-ns-acik')
    || [$('#isMetin'), $('#isTuval'), $('#isOdak')].some(x => x && !x.hidden) || !S.clips.length;
  /* aynı ayara bağlı birden fazla alan varsa (Hızlı yol'daki kopyalar) asıl bölümdekini seç */
  const enIyiEl = k => { const l = [...document.querySelectorAll('#editor [data-k="' + k + '"]')]; return l.find(e => !/^hz/.test(e.id) && e.closest('section.panel')) || l[0]; };
  const etiket = el => { const id = el.id, lb = id && document.querySelector('label[for="' + id + '"]'); let t = lb ? lb.textContent : (el.getAttribute('aria-label') || el.placeholder || 'Yazı'); return t.replace(/\(.*?\)/g, '').trim(); };
  function adaylar(){
    const out = [], gor = new Set();
    document.querySelectorAll('#editor input[type=text][data-k], #editor textarea[data-k]').forEach(el => {
      const k = el.dataset.k; if (gor.has(k)) return; gor.add(k);
      let v; try { v = getP(S, k); } catch(e){ return; } if (typeof v !== 'string' || !v.trim()) return;
      const e2 = enIyiEl(k); out.push({ad: etiket(e2), el: e2, get: () => getP(S, k), set: x => setP(S, k, x)});
    });
    const sb = activeSub(PT);
    if (sb && (sb.text || '').trim()) out.push({ad: 'Altyazı', sub: sb, get: () => sb.text, set: x => { sb.text = x; }, el: () => document.querySelector('#subList [data-sid="' + sb.id + '"] input[data-f="text"]')});
    return out;
  }
  /* dokunulan yerde hangi yazı var: her adayı geçici değiştir, piksel farkı en büyük olan */
  function yokla(cx, cy){
    const h = Math.max(8, Math.round(pv.width*0.03)), x0 = clamp(Math.round(cx - h), 0, pv.width - 2), y0 = clamp(Math.round(cy - h), 0, pv.height - 2);
    const w = Math.min(2*h, pv.width - x0), hh = Math.min(2*h, pv.height - y0);
    const al = () => pctx.getImageData(x0, y0, w, hh).data;
    const tut = EDIT.until; let en = null, enD = 0;
    try {
      drawPreview(); const taban = al();
      for (const a of adaylar()){
        const v = a.get(); a.set(v.replace(/\S/g, 'X'));
        try { drawPreview(); const d = al(); let s = 0; for (let i = 0; i < d.length; i += 4) s += Math.abs(d[i] - taban[i]) + Math.abs(d[i + 1] - taban[i + 1]) + Math.abs(d[i + 2] - taban[i + 2]); if (s > enD){ enD = s; en = a; } }
        finally { a.set(v); }
      }
    } finally { EDIT.until = tut; drawPreview(); }
    return enD > w*hh*3*0.8 ? en : null;   /* aynı karede çizim kararlı: yazı olmayan yerde fark 0 */
  }
  /* alanı göster: panel sekmesi, ara sekme, ikinci sıra, sayfa; sonra alanı ortala ve parlat */
  function elGoster(el){
    const pn = el && el.closest('section.panel[data-panel]'); if (!pn) return;
    const t = pn.dataset.panel, B = document.body;
    if (B.dataset.kvtab !== t || B.classList.contains('kv-kapali')){
      const b = document.querySelector('#mininav button[data-tab="' + t + '"]');
      if (b && B.dataset.kvtab !== t) b.click(); else { showTab(t); try { window.kvStudyo && kvStudyo.kapat(false); } catch(e){} }
    }
    setTimeout(() => {
      try {
        try { isAltKur(); } catch(e){}
        const r1 = $('#isAltSek'), gizli = () => !!el.closest('.is-gizle'), t0 = B.dataset.kvtab;
        if (r1 && !r1.hidden && gizli()){
          const n1 = r1.querySelectorAll('button[data-i]').length; let ok = false;
          for (let i = 0; i < n1 && !ok; i++){
            IS.alt = IS.alt || {}; IS.alt[t0] = i; isAltYap();
            const r2 = $('#isAltSek2');
            if (r2 && !r2.hidden && gizli()){ const n2 = r2.querySelectorAll('button[data-j]').length, an = r2.dataset.anahtar;
              for (let j = 0; j < n2; j++){ IS.alt2 = IS.alt2 || {}; IS.alt2[an] = j; isAltYap(); if (!gizli()){ ok = true; break; } } }
            else if (!gizli()) ok = true;
          }
          try { LS.set('kv2.isAlt2', IS.alt); LS.set('kv2.isAlt3', IS.alt2 || {}); } catch(e){}
        }
        if (window.stVideoPas){ window.stVideoPas(true); const pr = $('#stVsayfa');
          if (el.closest('.st-gz') && pr && !pr.hidden){ const bs = [...pr.querySelectorAll('button[data-i]')]; for (const b of bs){ b.click(); if (!el.closest('.st-gz')) break; } } }
        try { sectAc(el); } catch(e){}
        const pan = document.querySelector('.kv-pan');
        if (pan && pan.contains(el)){ const pr = pan.getBoundingClientRect(), r = el.getBoundingClientRect(); pan.scrollTop += r.top - pr.top - pan.clientHeight*0.45; }
        el.classList.remove('km-isik'); void el.offsetWidth; el.classList.add('km-isik');
      } catch(e){ console.warn('Stüdyo yazı bölümü', e); }
    }, 140);
  }
  /* düzenleme kutusu */
  let K = null, H = null;   /* H: {ad, yaz(v), oku(), bitir(), gy (dokunulan yer, önizleme yüksekliğine oran)} */
  function kutuKur(){
    K = document.createElement('div'); K.id = 'stYzKutu'; K.hidden = true; K.setAttribute('role', 'dialog'); K.setAttribute('aria-label', 'Yazıyı düzenle');
    K.innerHTML = '<div class="ust"><span></span><button type="button" aria-label="Bitti">✓ Bitti</button></div><textarea rows="1" enterkeyhint="done" autocomplete="off" spellcheck="true"></textarea>';
    document.body.appendChild(K);
    const ta = K.querySelector('textarea');
    ta.addEventListener('input', () => { if (!H) return; H.yaz(ta.value); boy(); });
    ta.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey){ e.preventDefault(); kapat(); } if (e.key === 'Escape') kapat(); });
    K.querySelector('.ust button').addEventListener('click', kapat);
    document.addEventListener('pointerdown', e => { if (!K.hidden && !K.contains(e.target) && !e.target.closest('#pv')) kapat(); }, true);
    if (window.visualViewport){ visualViewport.addEventListener('resize', yerles); visualViewport.addEventListener('scroll', yerles); }
    addEventListener('resize', yerles);
  }
  function boy(){ const ta = K.querySelector('textarea'); ta.style.height = 'auto'; ta.style.height = Math.min(96, ta.scrollHeight) + 'px'; }
  function yerles(){
    if (!K || K.hidden || !H) return;
    const r = pv.getBoundingClientRect(), vv = window.visualViewport, vH = vv ? vv.height + vv.offsetTop : innerHeight, kh = K.offsetHeight, ty = r.top + H.gy*r.height;
    let top = H.gy < 0.6 ? Math.max(ty + 26, r.bottom - kh - 6) : Math.min(ty - kh - 26, r.top + 6);   /* yazıyı örtmesin: önizlemenin alt ya da üst kenarına */
    if (top + kh > r.bottom - 2 && H.gy < 0.6) top = r.bottom - kh - 6;
    top = Math.max((vv ? vv.offsetTop : 0) + 6, Math.min(top, vH - kh - 6));      /* klavyenin altında kalmasın */
    K.style.top = Math.round(top) + 'px';
  }
  function ac(h){
    if (!K) kutuKur();
    H = h; K.querySelector('.ust span').textContent = '✎ ' + h.ad + ' · ekranda yaz';
    const ta = K.querySelector('textarea'); ta.value = h.oku(); K.hidden = false; boy(); yerles();
    setTimeout(() => { try { ta.focus({preventScroll: true}); const n = ta.value.length; ta.setSelectionRange(n, n); } catch(e){} yerles(); }, 30);
    setTimeout(yerles, 400);
  }
  function kapat(){ if (!K || K.hidden) return; K.hidden = true; const h = H; H = null; try { K.querySelector('textarea').blur(); } catch(e){} if (h && h.bitir) h.bitir(); }
  window.stYaziKapat = kapat;

  async function dokun(ex, ey){
    if (engel()) return;
    const r = pv.getBoundingClientRect(); if (!r.width) return;
    const gx = (ex - r.left)/r.width, gy = (ey - r.top)/r.height; if (gx < 0 || gx > 1 || gy < 0 || gy > 1) return;
    /* Metin aracıyla eklenen yazılar: kendi kutuları var */
    const y = (S.yazilar || []).filter(o => o._bb && PT >= o.t0 && PT <= o.t1 && gx >= o._bb.x && gx <= o._bb.x + o._bb.w && gy >= o._bb.y && gy <= o._bb.y + o._bb.h).pop();
    if (y){
      try { window.isMetinAc && isMetinAc(y.id); } catch(e){}
      ac({ad: 'Metin', gy, oku: () => y.text || '', yaz: v => { y.text = v; requestRender(); }, bitir: () => { projSave(); saveLast(); try { requestRender(); } catch(e){} }});
      return;
    }
    const a = yokla(gx*pv.width, gy*pv.height); if (!a) return;
    const el = typeof a.el === 'function' ? a.el() : a.el;
    if (a.sub){ if (!el){ try { showTab('subs'); } catch(e){} } }
    if (el) elGoster(el);
    const pt0 = PT;
    ac({ad: a.ad, gy, oku: () => a.get() || '',
      yaz: v => { const e2 = typeof a.el === 'function' ? a.el() : a.el;
        if (e2){ e2.value = v; e2.dispatchEvent(new Event('input', {bubbles: true})); } else { a.set(v); requestRender(); }
        if (!playing && Math.abs(PT - pt0) > 0.01){ PT = pt0; requestRender(); } },   /* yazarken oynatma yeri dokunduğun karede kalsın */
      bitir: () => { const e2 = typeof a.el === 'function' ? a.el() : a.el; if (e2) e2.dispatchEvent(new Event('change', {bubbles: true})); try { if (a.sub) renderSubs(); } catch(e){} saveLast(); projSave(); }});
  }
  /* tek dokunuşu yakala; çift dokunuş (tam ekran) ya da sürükleme ise karışma */
  let ilk = null, bek = 0, sonTik = 0, coklu = 0;
  pv.addEventListener('pointerdown', e => { if (!e.isPrimary){ coklu = 1; return; } coklu = 0; ilk = {x: e.clientX, y: e.clientY, t: performance.now(), oyn: playing}; });
  pv.addEventListener('pointerup', e => {
    if (!ilk || !e.isPrimary || coklu) return; const d = Math.hypot(e.clientX - ilk.x, e.clientY - ilk.y), dt = performance.now() - ilk.t, oyn = ilk.oyn; ilk = null;
    if (d > 8 || dt > 350 || oyn) return;
    const now = performance.now();
    if (now - sonTik < 380){ clearTimeout(bek); sonTik = 0; kapat(); return; }   /* çift dokunuş: tam ekran */
    sonTik = now; const x = e.clientX, y = e.clientY;
    clearTimeout(bek); bek = setTimeout(() => { try { dokun(x, y); } catch(err){ console.warn('Stüdyo yazı dokunuşu', err); } }, 330);
  });
})();
