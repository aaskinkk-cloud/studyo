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
    ' html.st-is .st-pbas > button{width:40px;height:40px;border:0;border-radius:50%;background:transparent;color:var(--text);display:grid;place-items:center;padding:0}',
    ' html.st-is .st-pbas > button svg{width:26px;height:26px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}',
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

/* ===== 3. adım: PANEL BÖLÜMLERİ ÜSTTE SİMGELİ SEKMELER, ALTTA YALNIZ SEÇİLEN BÖLÜM =====
   Karanlık Oda her paneli çizdikten sonra bolumle() ile "başlık + grup" çiftleri kurar. Bu katman onu sarar:
   başlıklar üstte simgeli bir sıraya dönüşür, altta yalnız seçilen bölüm görünür; uzun bölümler sayfalanır,
   3+ kaydırıcı InShot "Ayarlama" gibi tek tek gösterilir. Öğeler taşınmaz, yalnız gizlenip gösterilir. */
(function(){
  if (!window.STUDYO || typeof bolumle !== 'function') return;
  var css = document.createElement('style');
  css.textContent = [
    'html.st-is #panelIn .bmod{display:none!important}',
    'html.st-is #panelIn>.subhead.bhead.st-bas-gz{display:none!important}',
    'html.st-is #panelIn>.panel-title{display:none!important}',      /* başlık panelin üst çubuğunda zaten var */
    'html.st-is #panelIn>.panel-title.st-pt-dug{display:flex!important;justify-content:flex-end;min-height:0;margin:0}',
    'html.st-is #panelIn>.panel-title.st-pt-dug>h3{display:none}',
    'html.st-is .st-bsek{display:flex;gap:2px;overflow-x:auto;scrollbar-width:none;flex:1;min-width:0;margin:0;padding:0 0 2px}',
    'html.st-is .st-pbas.st-sekmeli b{display:none}',
    'html.st-is .st-pbas.st-sekmeli{padding-left:8px!important}',
    'html.st-is .st-bsek::-webkit-scrollbar{display:none}',
    'html.st-is .st-bsek button{flex:none;min-width:58px;display:flex;flex-direction:column;align-items:center;gap:4px;border:0;background:transparent;color:var(--muted);font:600 11px/1.1 var(--f-ui);padding:5px 5px 7px;border-radius:12px;position:relative;white-space:nowrap}',
    'html.st-is .st-bsek button svg{width:21px;height:21px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}',
    'html.st-is .st-bsek button.on{color:var(--text)}',
    'html.st-is .st-bsek button.on svg{color:var(--accent)}',
    'html.st-is .st-bsek button.on::after{content:"";position:absolute;left:30%;right:30%;bottom:0;height:2.5px;border-radius:2px;background:var(--accent)}',
    'html.st-is .st-ssat{position:sticky;top:76px;z-index:5;background:var(--surface);margin:-4px -16px 0;padding:4px 16px 8px}',
    /* büyük ızgaralar yatay şerit olsun (aşağı kaydırma yok) */
    'html.st-is #panelIn .presets{display:flex!important;flex-wrap:nowrap!important;overflow-x:auto;gap:8px;scrollbar-width:none;padding-bottom:4px;grid-template-columns:none!important}',
    'html.st-is #panelIn .presets::-webkit-scrollbar{display:none}',
    'html.st-is #panelIn .presets>*{flex:none!important;width:92px!important}',
    'html.st-is #panelIn .yz-sab,html.st-is #panelIn .im-sab{display:flex!important;flex-wrap:nowrap!important;overflow-x:auto;gap:8px;scrollbar-width:none;grid-template-columns:none!important}',
    'html.st-is #panelIn .yz-sab>*,html.st-is #panelIn .im-sab>*{flex:none!important;width:128px!important}',
    'html.st-is #panelIn #curve{max-width:150px!important}',
    /* seçenek grupları (oranlar, hazırlar) tek satır, yana kayar */
    'html.st-is #panelIn .chips{flex-wrap:nowrap!important;overflow-x:auto;scrollbar-width:none;padding-bottom:2px}',
    'html.st-is #panelIn .chips::-webkit-scrollbar{display:none}',
    'html.st-is #panelIn .chips>*{flex:none!important}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);

  var GIRIS = {auto: 'Akıllı', filters: 'Stil kopyala', selfi: 'Kadraj', crop: 'Oran', curves: 'Eğri', retouch: 'Rötuş', export: 'Kaydet', fx: 'Efektler', brush: 'Fırça', hdr: 'HDR', overlay: 'Katman', light: 'Işık', history: 'Geçmiş', text: 'Yazı', detail: 'Detay', color: 'Renk'};
  var ATLA = {hsl: 1};      /* HSL zaten üstte renk noktalarıyla seçiliyor */
  var SEC = {}; try { SEC = JSON.parse(localStorage.getItem('st.bsek') || '{}') || {}; } catch(e){}
  var meşgul = false, sonP = null;

  function birimlerCikar(P_){
    var giris = {ad: GIRIS[ED.tab] || 'Genel', els: []}, list = [], cur = null;
    [].forEach.call(P_.children, function(n){
      if (n.classList.contains('st-bsek') || n.classList.contains('st-ssat') || n.classList.contains('bmod') || n.classList.contains('st-ksat')) return;
      if (n.classList.contains('panel-title')){ return; }
      if (n.classList.contains('subhead') && n.classList.contains('bhead')){ cur = {ad: window.stKisa(n.textContent) || 'Bölüm', h: n, els: []}; list.push(cur); return; }
      if (n.classList.contains('bgrp') && cur){ cur.g = n; cur.els = [].slice.call(n.children); return; }
      if (n.classList.contains('st-vkart')){ giris.els.push(n); return; }
      giris.els.push(n);
    });
    var anlamli = giris.els.filter(function(e){ return !e.matches('input[type=file],p.note') ; }).length;
    if (giris.els.length && (anlamli || !list.length)) list.unshift(giris); else if (giris.els.length && list.length) list[0].ekGiris = giris.els;
    return list;
  }
  function panelBosluk(ilk){
    var pan = document.getElementById('panel'), pin = document.getElementById('panelIn'); if (!pan || !pin) return 300;
    var ust = 0; [].forEach.call(pan.querySelectorAll('.ed-tut,.st-pbas'), function(e){ ust += e.offsetHeight; });
    var ps = getComputedStyle(pin), ic = (parseFloat(ps.paddingTop) || 0) + (parseFloat(ps.paddingBottom) || 0);
    return pan.clientHeight - ust - ic - 46 - 8;   /* 46: sayfa sırası için ayrılan yer */
  }
  /* sayfala, sonra panelde gerçekten taşma var mı ölç; varsa sınırı küçültüp yeniden (en çok 3 tur) */
  function olcerek(o){ var pan = document.getElementById('panel'), pin = document.getElementById('panelIn');
    window.stSayfala(o); if (!pan || !pin) return;
    /* sınır seçili sayfadan bağımsız: sayfa sırası yerleştikten sonra kalan yükseklik bir kez ölçülür */
    var pr = pan.getBoundingClientRect(), alt = parseFloat(getComputedStyle(pin).paddingBottom) || 0, ustK = o._ilkUst;
    if (ustK == null) return;
    if (!o._satirOnce && o.satir && !o.satir.hidden) ustK += o.satir.offsetHeight + (parseFloat(getComputedStyle(pin).rowGap) || 12);
    var gercek = pr.bottom - alt - ustK - 6;
    if (Math.abs(gercek - o.maxH) > 4){ o.maxH = gercek; window.stSayfala(o); } }
  function uygula(P_, yeniSecim){
    if (meşgul || !P_ || !P_.isConnected) return; meşgul = true;
    try {
      var tab = ED.tab, list = ATLA[tab] ? [] : birimlerCikar(P_);
      var row = document.querySelector('.st-pbas .st-bsek'), ss = P_.querySelector(':scope > .st-ssat');
      if (!ss){ ss = document.createElement('div'); ss.className = 'st-ssat'; ss.hidden = true; P_.prepend(ss); }
      var cok = list.length >= 2;
      if (cok){
        if (!row){ row = document.createElement('div'); row.className = 'st-bsek'; row.setAttribute('role', 'tablist');
          row.addEventListener('click', function(e){ var b = e.target.closest('button[data-i]'); if (!b) return; SEC[ED.tab] = b.dataset.ad; try { localStorage.setItem('st.bsek', JSON.stringify(SEC)); } catch(_){}
            var pn = document.getElementById('panel'); if (pn) pn.scrollTop = 0; uygula(document.getElementById('panelIn'), true); }); }
        var bas = document.querySelector('.st-pbas'), tamam = bas && bas.querySelector('button');
        if (bas && row.parentNode !== bas) bas.insertBefore(row, tamam);
        if (bas) bas.classList.add('st-sekmeli');
        var adlar = list.map(function(x){ return x.ad; }), imza = adlar.join('|');
        if (row.dataset.imza !== imza){ row.dataset.imza = imza;
          row.innerHTML = list.map(function(x, i){ var nokta = x.els.some(function(e){ return e.querySelector && e.querySelector('.val.ch,.rst.show,.dot'); });
            return '<button type="button" role="tab" data-i="' + i + '" data-ad="' + x.ad.replace(/"/g, '') + '"><svg viewBox="0 0 24 24" aria-hidden="true">' + window.stIkon(x.ad) + '</svg><span>' + x.ad + '</span></button>'; }).join(''); }
        var s = adlar.indexOf(SEC[tab]); if (s < 0) s = 0;
        [].forEach.call(row.children, function(b, i){ b.classList.toggle('on', i === s); b.setAttribute('aria-selected', i === s); });
        if (yeniSecim || row.dataset.son !== String(s)){ row.dataset.son = String(s); var on = row.children[s]; if (on) row.scrollLeft = Math.max(0, on.offsetLeft - row.clientWidth / 2 + on.offsetWidth / 2); }
        list.forEach(function(x, i){
          var goster = i === s;
          if (x.h){ x.h.classList.add('st-bas-gz'); }
          if (x.g){ x.g.hidden = false; x.g.classList.toggle('st-gz-b', !goster); x.g.style.display = goster ? '' : 'none'; }
          else x.els.forEach(function(e){ e.style.display = goster ? '' : 'none'; });
          if (x.ekGiris) x.ekGiris.forEach(function(e){ e.style.display = goster ? '' : 'none'; });
        });
        var pn0 = document.getElementById('panel'); if (pn0) pn0.scrollTop = 0;
        var G = list[s], els = (x => x.g ? [x.g] : x.els)(G).concat(G.ekGiris || []);
        olcerek({anahtar: 'ko|' + tab + '|' + G.ad, birimler: els, satir: ss, kok: P_, maxH: panelBosluk(), yenile: function(){ uygula(document.getElementById('panelIn')); }});
      } else {
        var bas2 = document.querySelector('.st-pbas'); if (bas2) bas2.classList.remove('st-sekmeli');
        var r2 = document.querySelector('.st-pbas .st-bsek'); if (r2) r2.remove();
        var hepsi = list.length ? (list[0].g ? [list[0].g] : list[0].els) : [].filter.call(P_.children, function(n){ return !n.matches('.st-ssat,.st-bsek,.panel-title,.bmod'); });
        if (list.length && list[0].h) list[0].h.classList.add('st-bas-gz');
        olcerek({anahtar: 'ko|' + tab, birimler: hepsi, satir: ss, kok: P_, maxH: panelBosluk(), yenile: function(){ uygula(document.getElementById('panelIn')); }});
      }
      /* başlık çubuğundaki "sıfırla" vb. düğmeler kaybolmasın: panel başlığında düğme varsa yalnız düğmeleri göster */
      var pt = P_.querySelector(':scope > .panel-title'); if (pt) pt.classList.toggle('st-pt-dug', !!pt.querySelector('button'));
    } catch(e){ console.warn('Stüdyo bölümler', e); }
    meşgul = false; sonP = P_;
  }
  var eski = window.bolumle;
  window.bolumle = function(P_){ var r = eski.apply(this, arguments); try { uygula(P_); } catch(e){} return r; };
  /* panel içeriği kısmen değişirse (yüz bulundu, seçenek açıldı) ya da panel boyutu değişirse yeniden düzenle */
  var t = 0; function sonra(){ clearTimeout(t); t = setTimeout(function(){ var p = document.getElementById('panelIn'); if (p && !meşgul && !document.getElementById('editor').hidden) uygula(p); }, 140); }
  (function bagla(n){ var pin = document.getElementById('panelIn'), pan = document.getElementById('panel');
    if (!pin || !pan){ if (n < 60) setTimeout(function(){ bagla(n + 1); }, 250); return; }
    var mo = new MutationObserver(function(ml){ if (meşgul) return; for (var i = 0; i < ml.length; i++){ var tg = ml[i].target; if (tg.closest && (tg.closest('.st-satir') || tg.closest('.st-bsek'))) continue; if (ml[i].type === 'attributes' && ml[i].attributeName === 'class' && tg.classList && (tg.classList.contains('st-gz') || ml[i].oldValue && /st-gz/.test(ml[i].oldValue))) continue; sonra(); return; } });
    mo.observe(pin, {childList: true, subtree: true, attributes: true, attributeFilter: ['hidden'], attributeOldValue: false});
    if (window.ResizeObserver) new ResizeObserver(sonra).observe(pan);
    if (pin.children.length && typeof ED !== 'undefined' && ED.state) uygula(pin);
  })(0);
})();
