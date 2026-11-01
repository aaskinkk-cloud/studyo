/* ===== STÜDYO · FOTOĞRAF EDİTÖRÜ InShot DÜZENİ (yalnız görünüm) =====
   Karanlık Oda'nın araçları, panelleri ve işlevleri aynen kalır. Değişen yalnız yerleşim:
   - altta tek sıra kaydırılabilir araç çubuğu (bütün sekmeler, kategori çubuğu gizli)
   - araç seçilmeden önizleme tam boy; araca dokununca panel açılır, başlığında araç adı ve ✓ (kapat)
   - üstte: ← · geri al / ileri al / orijinal / karşılaştır · Dışa Aktar
   Paneli açıp kapatma, Karanlık Oda 6.3'ün kendi "aynı sekmeye dokun → kapan" mekanizmasıyla yapılır. */
(function(){
  if (!window.STUDYO) return;
  var R = document.documentElement; R.classList.add('st-is');
  var css = document.createElement('style'); css.id = 'st-foto-css';
  css.textContent = [
    /* üst çubuk */
    'html.st-is #editor .topbar{gap:2px;padding:6px 8px;border-bottom:0;background:var(--bg)}',
    'html.st-is #editor .topbar .info{flex:1;min-width:0;text-align:center}',
    'html.st-is #editor .topbar .info b{font-size:13px;font-weight:600}',
    'html.st-is #editor .topbar .info small{display:none}',
    'html.st-is #saveBtn{border-radius:999px;padding:9px 16px;font-weight:700;margin-left:4px}',
    'html.st-is #saveBtn:not(.tel){font-size:0}',
    'html.st-is #saveBtn:not(.tel)::after{content:"Dışa Aktar";font-size:14.5px}',
    /* kategori çubuğu yok; bütün araçlar tek sırada */
    'html.st-is #grpBar{display:none!important}',
    'html.st-is #tabsWrap #tabs .tab.gizli{display:flex!important}',
    'html.st-is #tabsWrap #tabs,html.st-is #editor #tabs{justify-content:flex-start!important;flex-wrap:nowrap!important;overflow-x:auto;scrollbar-width:none;gap:2px;padding:8px 6px calc(env(safe-area-inset-bottom,0px) + 8px)!important;scroll-snap-type:x proximity}',
    'html.st-is #editor #tabs::-webkit-scrollbar{display:none}',
    'html.st-is #tabsWrap #tabs .tab,html.st-is #editor #tabs .tab{flex:none!important;min-width:68px;max-width:none!important;padding:6px 6px;gap:6px;font-size:11.5px;font-weight:600;border-radius:14px;scroll-snap-align:start}',
    'html.st-is #editor #tabs .tab svg{width:25px;height:25px}',
    'html.st-is #editor #tabs .tab.on{background:transparent;color:var(--text)}',
    'html.st-is #editor #tabs .tab.on svg{color:var(--accent)}',
    'html.st-is body.ed-kapali #editor #tabs .tab.on{color:var(--muted);box-shadow:none}',
    'html.st-is body.ed-kapali #editor #tabs .tab.on svg{color:inherit}',
    /* InShot sırası: önce en sık kullanılanlar */
    '#tabs .tab[data-t=auto]{order:1}#tabs .tab[data-t=crop]{order:2}#tabs .tab[data-t=filters]{order:3}#tabs .tab[data-t=light]{order:4}#tabs .tab[data-t=color]{order:5}#tabs .tab[data-t=hsl]{order:6}#tabs .tab[data-t=curves]{order:7}#tabs .tab[data-t=detail]{order:8}#tabs .tab[data-t=fx]{order:9}#tabs .tab[data-t=selfi]{order:10}#tabs .tab[data-t=text]{order:11}#tabs .tab[data-t=overlay]{order:12}#tabs .tab[data-t=hdr]{order:13}#tabs .tab[data-t=retouch]{order:14}#tabs .tab[data-t=brush]{order:15}#tabs .tab[data-t=haber]{order:16}#tabs .tab[data-t=history]{order:17}#tabs .tab[data-t=export]{order:18}',
    /* panel kapalıyken tamamen gizli: önizleme büyür */
    '@media (max-width:899px){html.st-is body.ed-kapali #editor .panel{height:0!important;border-top:0}}',
    /* panel başlığı: araç adı + ✓ */
    'html.st-is .st-pbas{display:none}',
    '@media (max-width:899px){html.st-is .st-pbas{display:flex;align-items:center;gap:10px;position:sticky;top:22px;z-index:6;background:var(--surface);padding:2px 12px 6px 18px}',
    ' html.st-is .st-pbas b{flex:1;text-align:center;font-size:16px;font-weight:700;padding-left:40px}',
    ' html.st-is .st-pbas button{width:40px;height:40px;border:0;border-radius:50%;background:transparent;color:var(--text);display:grid;place-items:center;padding:0}',
    ' html.st-is .st-pbas svg{width:26px;height:26px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}',
    ' html.st-is #editor .panel{border-radius:22px 22px 0 0;box-shadow:0 -8px 24px rgba(0,0,0,.18)}}'
  ].join('\n');
  (document.head || R).appendChild(css);

  var KAPAT = ['crop', 'retouch', 'brush'];   /* kapatınca kırpma / fırça kipinden de çıkılsın (kırpma bu sırada kaydedilir) */
  function tel(){ return matchMedia('(max-width:899px)').matches && !matchMedia('(orientation:landscape) and (max-height:560px)').matches; }
  function aktif(){ return document.querySelector('#tabs .tab.on'); }
  function kapali(){ return document.body.classList.contains('ed-kapali'); }
  function kapat(){
    if (!tel()) return;
    try { if (typeof ED !== 'undefined' && KAPAT.indexOf(ED.tab) >= 0) setTab('auto'); } catch(e){}
    if (!kapali()){ var a = aktif(); if (a) a.click(); }   /* 6.3: açık sekmeye dokunmak paneli kapatır */
  }
  function baslik(){ var b = document.querySelector('.st-pbas b'), a = aktif(); if (b) b.textContent = a ? (a.querySelector('span') || a).textContent : ''; }
  function kur(){
    var pan = document.getElementById('panel'), tabs = document.getElementById('tabs'), ed = document.getElementById('editor');
    if (!pan || !tabs || !ed) return false;
    if (!pan.querySelector('.st-pbas')){
      var h = document.createElement('div'); h.className = 'st-pbas';
      h.innerHTML = '<b></b><button type="button" aria-label="Tamam, paneli kapat" title="Tamam"><svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg></button>';
      h.querySelector('button').addEventListener('click', kapat);
      var tut = pan.querySelector('.ed-tut'); if (tut) tut.after(h); else pan.prepend(h);
    }
    new MutationObserver(baslik).observe(tabs, {subtree: true, attributes: true, attributeFilter: ['class'], childList: true});
    baslik();
    /* editör her açıldığında araç çubuğu ve tam boy önizleme ile başla (InShot gibi) */
    var gorunur = !ed.hidden;
    new MutationObserver(function(){ var g = !ed.hidden; if (g && !gorunur) setTimeout(function(){ if (tel() && !kapali()) kapat(); }, 120); gorunur = g; }).observe(ed, {attributes: true, attributeFilter: ['hidden']});
    if (gorunur && tel() && !kapali()) setTimeout(kapat, 150);
    return true;
  }
  (function dene(n){ if (!kur() && n < 60) setTimeout(function(){ dene(n + 1); }, 250); })(0);

  /* ---------- 2. adım: BU FOTOĞRAFLA VİDEO YAP ----------
     Düzenlenmiş fotoğraf, Karanlık Oda'nın kendi tam çözünürlük işleviyle (renderFull) JPG yapılır,
     düzenleme önce kitaplığa kaydedilir, sonra Stüdyo video editörünü bu fotoğrafla açar. */
  var P = null; try { if (window.parent !== window && window.parent.studyoVideoya) P = window.parent; } catch(e){}
  var KLAKET = '<svg viewBox="0 0 24 24"><path d="M4 10h16v9a2 2 0 01-2 2H6a2 2 0 01-2-2z"/><path d="M4 10l1.5-5.5 15 3.5-.5 2"/><path d="M8.5 5.5l2.5 4M13.5 6.6l2.5 4"/></svg>';
  var calisiyor = false;
  async function videoYap(){
    if (calisiyor || !P) return; if (typeof ED === 'undefined' || !ED.rec){ try { toast('Önce bir fotoğraf aç.'); } catch(e){} return; }
    calisiyor = true; var b = null, pipe = null;
    try {
      b = busy('Fotoğraf videoya hazırlanıyor…');
      try { ED.rec.state = ED.state; ED.rec.updated = Date.now(); ED.rec.thumb = thumbFromGL(); await DB.put(ED.rec); } catch(e){}
      pipe = new Pipeline(document.createElement('canvas'));
      var E = ED.exp || {}, out = await renderFull(ED.rec.blob, ED.state, E.w && E.h ? {w: E.w, h: E.h} : null, pipe);
      var x = out.getContext('2d'); x.globalCompositeOperation = 'destination-over'; x.fillStyle = '#fff'; x.fillRect(0, 0, out.width, out.height);
      var blob = await toBlob(out, 'jpeg', 0.95); out.width = 0;
      var ad = String(ED.rec.name || 'foto').replace(/[\\/:*?"<>|]+/g, '_') + '.jpg';
      var dosya = new File([blob], ad, {type: 'image/jpeg', lastModified: Date.now()});
      b.close(); b = null;
      P.studyoVideoya([dosya]);
    } catch(e){ console.error(e); if (b) b.close(); try { toast('Video için hazırlanamadı. Fotoğrafı Dışa Aktar ile kaydedip Video düzenle\'den seçebilirsin.'); } catch(_){} }
    finally { if (pipe) try { pipe.destroy(); } catch(e){} calisiyor = false; }
  }
  if (P){
    var css2 = document.createElement('style');
    css2.textContent = 'html.st-is #stVideoBtn svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}' +
      '.st-vkart{display:flex;align-items:center;gap:12px;width:100%;border:0;border-radius:16px;padding:12px 14px;text-align:left;cursor:pointer;color:#1b0f05;background:linear-gradient(135deg,#ffb26b,#ff8a3d);font:inherit}' +
      '.st-vkart svg{width:30px;height:30px;flex:none;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.st-vkart b{display:block;font-size:15px}.st-vkart small{font-size:12px;opacity:.8}';
    (document.head || R).appendChild(css2);
    (function ekle(n){
      var top = document.querySelector('#editor .topbar'), kay = document.getElementById('saveBtn');
      if (!top || !kay){ if (n < 60) setTimeout(function(){ ekle(n + 1); }, 250); return; }
      if (!document.getElementById('stVideoBtn')){ var v = document.createElement('button'); v.className = 'icon-btn'; v.id = 'stVideoBtn'; v.type = 'button';
        v.setAttribute('aria-label', 'Bu fotoğrafla video yap'); v.title = 'Bu fotoğrafla video yap'; v.innerHTML = KLAKET; v.addEventListener('click', videoYap); kay.before(v); }
      var pin = document.getElementById('panelIn');
      if (pin) new MutationObserver(function(){
        if (typeof ED === 'undefined' || ED.tab !== 'export' || pin.querySelector('.st-vkart')) return;
        var k = document.createElement('button'); k.type = 'button'; k.className = 'st-vkart';
        k.innerHTML = KLAKET + '<span><b>Bu fotoğrafla video yap</b><small>Düzenlenmiş hâli video editörüne geçer</small></span>';
        k.addEventListener('click', videoYap); pin.prepend(k);
      }).observe(pin, {childList: true});
    })(0);
  }
})();
