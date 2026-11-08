/* ===== STÜDYO KÖPRÜSÜ — bu uygulama Stüdyo'nun içinde çalışırken eklenir. Uygulamanın kendi kodu değişmez. ===== */
(function(){
  var APP = '%APP%', q = new URLSearchParams(location.search), P = null;
  try { if (window.parent !== window && window.parent.STUDYO_KABUK) P = window.parent; } catch(e){}
  var st = document.createElement('style');
  st.textContent = '#kaynakEt{display:none!important}' +
    '.studyo-ev{display:inline-flex;align-items:center;gap:6px;border:1px solid rgba(128,140,150,.35);background:rgba(128,140,150,.12);color:inherit;border-radius:999px;padding:8px 14px 8px 10px;font:600 14px/1 system-ui,sans-serif;cursor:pointer;margin:0 0 10px}' +
    '.studyo-ev svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}' +
    '#isAna .studyo-ev{margin:0;padding:0;width:40px;height:40px;justify-content:center;border-radius:50%}#isAna .studyo-ev span{display:none}';
  (document.head || document.documentElement).appendChild(st);
  function tema(t){ if (!t) return;
    if (APP === 'foto'){ try { applyTheme(t === 'gece' ? 'dark' : 'light'); } catch(e){} }
    else { document.documentElement.dataset.tema = t === 'gece' ? 'gece' : 'gunduz'; } }
  function bekle(fn, ms){ return new Promise(function(res){ var t0 = Date.now(); (function d(){ var v = null; try { v = fn(); } catch(e){} if (v) return res(v); if (Date.now() - t0 > (ms || 20000)) return res(null); setTimeout(d, 120); })(); }); }
  function dosyaVer(sel, files){ var inp = document.querySelector(sel); if (!inp || !files || !files.length) return false;
    var dt = new DataTransfer(); files.forEach(function(f){ dt.items.add(f); }); inp.files = dt.files; inp.dispatchEvent(new Event('change', {bubbles: true})); return true; }
  function eve(){ if (P && P.studyoEve) P.studyoEve(); else location.href = '../'; }
  var EV = '<svg viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></svg><span>Stüdyo</span>';
  function eveDugmesi(){
    if (APP === 'foto'){ var h = document.querySelector('#home .home-wrap'); if (h && !h.querySelector('.studyo-ev')){ var b = document.createElement('button'); b.type = 'button'; b.className = 'studyo-ev'; b.innerHTML = EV; b.onclick = eve; h.prepend(b); } }
    else { var u = document.querySelector('#isAna .is-ana-ust'), s = document.querySelector('#start');
      if (u && s){ var eski = s.querySelector(':scope > .studyo-ev'); if (eski) eski.remove(); }
      var hedef = u || s; if (hedef && !hedef.querySelector(':scope > .studyo-ev')){ var b2 = document.createElement('button'); b2.type = 'button'; b2.className = 'studyo-ev'; b2.innerHTML = EV; b2.setAttribute('aria-label', 'Stüdyo ana sayfası'); b2.onclick = eve; hedef.prepend(b2); } }
  }
  /* Android geri tuşu: önce uygulamanın içinde bir adım geri; işlem yoksa Stüdyo'ya döner */
  function geri(){
    try {
      if (APP === 'foto'){
        var sc = document.querySelectorAll('.scrim'); if (sc.length){ sc[sc.length - 1].remove(); return true; }
        var ed = document.querySelector('#editor'); if (ed && !ed.hidden){ closeEditor(); return true; }
      } else {
        var e2 = document.querySelector('#editor'); if (e2 && !e2.hidden){ var n = document.querySelector('#newProj'); if (n){ n.click(); return true; } }
      }
    } catch(e){}
    return false;
  }
  /* Android uygulaması (APK): uygulamanın kendi indirme kodu yalnız ana sayfaya eklenir; fotoğraf/video bu çerçevede
     kaydedildiği için aynı yakalama burada da kurulur (blob → 1 MB parçalar → İndirilenler). Chrome'da hiçbir şey değişmez. */
  try { if (window.OkurAndroid && OkurAndroid.parcaBasla && !window.__okurIndir){ window.__okurIndir = 1; var oc = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function(){ var h = this.href || '', ad = this.getAttribute('download');
      if (ad !== null && /^(blob:|data:)/.test(h)){ fetch(h).then(function(r){ return r.blob(); }).then(function(b){
        var id = Date.now() + '' + Math.random(), PB = 1048576, ofs = 0; OkurAndroid.parcaBasla(id);
        var git = function(){ if (ofs >= b.size){ OkurAndroid.parcaBitir(id, ad || 'studyo', b.type || ''); return; } var fr = new FileReader();
          fr.onload = function(){ var x = String(fr.result); OkurAndroid.parca(id, x.slice(x.indexOf(',') + 1)); ofs += PB; setTimeout(git, 0); }; fr.readAsDataURL(b.slice(ofs, ofs + PB)); }; git(); }); return; }
      return oc.apply(this, arguments); }; } } catch(e){}
  window.studyoKopru = {tema: tema, geri: geri, eve: eve};
  tema(q.get('tema'));
  async function basla(){
    try {
      if (APP === 'video'){
        try { await window.__kvBoot; } catch(e){}
        await bekle(function(){ return window.__kv; }, 15000);
        if (q.get('sil')){ var ok = false; try { await window.__kv.projDelete(q.get('sil')); ok = true; } catch(e){} try { window.parent.postMessage({studyo: 'silindi', ok: ok}, '*'); } catch(e){} return; }
      }
      tema(q.get('tema') || (P && P.studyoTema && P.studyoTema()));
      var b = P && P.studyoAl ? P.studyoAl() : null;
      if (APP === 'foto'){
        await bekle(function(){ return typeof openRecord === 'function' && document.querySelector('#pickGallery') && document.querySelector('#khAcBtn'); }, 15000);
        eveDugmesi();
        if (q.get('ac')){ var id = q.get('ac'), hepsi = await DB.all(), r = (hepsi || []).find(function(x){ return x.id === id; }); if (r) await openRecord(r); }
        else if (b && b.dosyalar) dosyaVer('#pickGallery', b.dosyalar);
        else if (b && b.tikla){ var el = await bekle(function(){ return document.querySelector(b.tikla); }, 8000); if (el) el.click(); }
      } else {
        eveDugmesi(); setInterval(eveDugmesi, 1500);
        if (q.get('ac')) await window.__kv.projOpen(q.get('ac'));
        else if (b && b.dosyalar) dosyaVer('#fileIn', b.dosyalar);
      }
    } catch(e){ console.warn('Stüdyo köprüsü', e); }
    if (P && P.studyoHazir) P.studyoHazir();
  }
  if (document.readyState === 'complete') basla(); else addEventListener('load', basla);
})();
/* 2.13: APK (Trusted Web Activity) ya da kurulu uygulama: sayfa zaten tam ekran. Burada hiçbir tam ekran isteği (sayfa ya da öğe)
   Chrome'a gönderilmez → "Tam ekrandan çıkmak için yukarıdan sürükleyip…" uyarısı hiç çıkmaz. Önizleme tam ekranı CSS ile (pvfs) çalışmaya devam eder.
   Chrome sekmesinde (kurulu değilken) eski davranış aynen kalır. */
(function(){
  if (window.__stApk) return; window.__stApk = 1;
  function apk(){
    try { var w = window.top;
      if (/^android-app:\/\//.test(w.document.referrer || '')) { try { w.sessionStorage.setItem('studyo.apk', '1'); } catch(e){} return true; }
      try { if (w.sessionStorage.getItem('studyo.apk') === '1') return true; } catch(e){}
      return ['fullscreen', 'standalone', 'minimal-ui'].some(function(m){ return w.matchMedia('(display-mode: ' + m + ')').matches; });
    } catch(e){ return false; }
  }
  window.studyoApk = apk;
  ['requestFullscreen', 'webkitRequestFullscreen'].forEach(function(k){
    var o = Element.prototype[k]; if (!o) return;
    Element.prototype[k] = function(){ if (apk()) return Promise.resolve(); return o.apply(this, arguments); };
  });
})();
