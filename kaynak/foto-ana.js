/* ===== 2.26 STÜDYO · Karanlık Oda yeni ana ekranı (yerel Stüdyo ana ekranına benzer) =====
   Üstte başlık + ⋯ yerine sade "Video ›" düğmesi; iki büyük kart: Yeni fotoğraf (sarı) · Video (yerel Stüdyo / Kareden Videoya);
   "Son çalışmalarım": Karanlık Oda kitaplığındaki son fotoğraflar (Kareden Videoya taslakları gibi satırlar, dokun = aç).
   Önceki ana ekranın tamamı altta aynen durur. Önceki görünüm: ⚙ Ayarlar › "Önceki ana ekran" (localStorage['studyo.fAnaEski']). */
(function(){
  if (window.__fAna) return; window.__fAna = 1;
  var ESKI = 'studyo.fAnaEski';
  function eski(){ try { return localStorage.getItem(ESKI) === '1'; } catch(e){ return false; } }
  var css = document.createElement('style');
  css.textContent =
    '.fa-ust{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin:4px 0 18px}' +
    '.fa-ust h1{margin:0;font-size:30px;line-height:1.1;font-weight:800;letter-spacing:-.5px}' +
    '.fa-ust p{margin:6px 0 0;font-size:13px;color:var(--muted)}' +
    '.fa-vid{flex:none;display:inline-flex;align-items:center;gap:6px;height:40px;padding:0 14px;border-radius:20px;border:1px solid rgba(128,140,150,.35);background:rgba(128,140,150,.12);color:inherit;font:600 13px/1 inherit;cursor:pointer}' +
    '.fa-vid svg,.fa-kart svg{fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}' +
    '.fa-kartlar{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:22px}' +
    '.fa-kart{min-height:128px;border:0;border-radius:22px;padding:18px;text-align:left;display:flex;flex-direction:column;justify-content:space-between;cursor:pointer;font:inherit}' +
    '.fa-yeni{background:#E8C860;color:#141414}.fa-yeni b{font-size:19px}' +
    '.fa-video{background:var(--surface-2,#26323f);color:inherit}.fa-video b{font-size:17px}.fa-video small{color:var(--muted);font-size:12px;margin-top:4px;display:block}' +
    '.fa-bas{display:flex;align-items:center;justify-content:space-between;margin:0 0 10px;padding-bottom:10px;border-bottom:1px solid rgba(128,140,150,.25)}' +
    '.fa-bas h2{margin:0;font-size:17px}.fa-bas button{height:34px;padding:0 14px;border-radius:17px;border:1px solid #c9a94a;background:none;color:#e0c25e;font:13px inherit;cursor:pointer}' +
    '.fa-liste{display:flex;flex-direction:column;gap:10px;margin-bottom:26px}' +
    '.fa-satir{display:flex;gap:14px;align-items:center;padding:12px;border-radius:18px;background:var(--surface,#1b2833);border:0;color:inherit;text-align:left;cursor:pointer;font:inherit;width:100%}' +
    '.fa-satir img,.fa-satir .fa-bos{width:120px;height:68px;border-radius:12px;object-fit:cover;flex:none;background:var(--stage,#000)}' +
    '.fa-satir b{display:block;font-size:15px;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}' +
    '.fa-satir span{display:block;font-size:12px;color:var(--muted)}' +
    '.fa-not{font-size:13px;color:var(--muted);margin:0 0 22px}' +
    '.fa-diger{font-size:12px;letter-spacing:2px;color:var(--muted);margin:6px 0 10px;font-weight:700}' +
    'body.fa-acik #home .home-top{display:none}body.fa-dog #home .home-wrap>.studyo-ev{display:none}';
  document.head.appendChild(css);

  /* Video'ya geçiş: yerelden doğrudan açıldıysa yerel Stüdyo'ya döner; web Stüdyo içinde Kareden Videoya'yı açar */
  function videoya(){
    var P = null; try { if (window.parent !== window && window.parent.STUDYO_KABUK) P = window.parent; } catch(e){}
    if (P && P.studyoVideoAc) { P.studyoVideoAc(); return; }
    location.href = '../video/';
  }
  var SV = function(d, s){ return '<svg width="' + s + '" height="' + s + '" viewBox="0 0 24 24" aria-hidden="true"><path d="' + d + '"/></svg>'; };
  var VID = 'M3 6h12v12H3zM15 10l6-3v10l-6-3';
  function tarih(t){ try { return new Date(t).toLocaleString('tr-TR', {day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'}); } catch(e){ return ''; } }

  async function liste(kutu){
    var recs = []; try { recs = (await DB.all()) || []; } catch(e){}
    recs.sort(function(a, b){ return (b.updated || 0) - (a.updated || 0); });
    kutu.innerHTML = '';
    if (!recs.length){ var n = document.createElement('p'); n.className = 'fa-not'; n.textContent = 'Henüz düzenlenmiş fotoğraf yok. “Yeni fotoğraf” ile başla.'; kutu.appendChild(n); return; }
    recs.slice(0, 6).forEach(function(r){
      var b = document.createElement('button'); b.type = 'button'; b.className = 'fa-satir'; b.setAttribute('aria-label', (r.name || 'Fotoğraf') + ' aç');
      var g = r.thumb ? '<img alt="" src="' + r.thumb + '">' : '<div class="fa-bos"></div>';
      var ad = String(r.name || 'Fotoğraf').replace(/[<>&]/g, function(c){ return {'<': '&lt;', '>': '&gt;', '&': '&amp;'}[c]; });
      b.innerHTML = g + '<div style="min-width:0;flex:1"><b>' + ad + '</b><span>' + (r.w && r.h ? r.w + '×' + r.h + ' · ' : '') + (r.fav ? '★ · ' : '') + '</span><span>' + tarih(r.updated) + '</span></div>';
      b.onclick = function(){ try { openRecord(r); } catch(e){} };
      kutu.appendChild(b);
    });
  }

  function kur(){
    var wrap = document.querySelector('#home .home-wrap'); if (!wrap) return false;
    var var_ = wrap.querySelector('.fa-blok');
    var tb = document.querySelector('#themeBtn'), ht = wrap.querySelector('.home-top');
    if (eski()){ if (var_) var_.remove(); document.body.classList.remove('fa-acik'); if (tb && ht && tb.parentNode !== ht) ht.appendChild(tb); return true; }
    if (var_) { liste(var_.querySelector('.fa-liste')); return true; }
    var blok = document.createElement('div'); blok.className = 'fa-blok';
    var surum = (document.querySelector('#home .sub') || {}).textContent || '';
    blok.innerHTML =
      '<div class="fa-ust"><div><h1>Karanlık Oda</h1><p>Fotoğraf düzenleyici · ' + (surum.match(/sürüm [0-9.]+/) || [''])[0] + '</p></div>' +
      '<div class="fa-sag" style="display:flex;gap:8px;align-items:center"><button type="button" class="fa-vid">' + SV(VID, 18) + 'Video ›</button></div></div>' +
      '<div class="fa-kartlar"><button type="button" class="fa-kart fa-yeni">' + SV('M12 5v14M5 12h14', 34) + '<b>Yeni fotoğraf</b></button>' +
      '<button type="button" class="fa-kart fa-video">' + SV(VID, 22) + '<div><b>Video</b><small>Stüdyo · Kareden Videoya</small></div></button></div>' +
      '<div class="fa-bas"><h2>Son çalışmalarım</h2><button type="button" class="fa-tumu">Tümü</button></div>' +
      '<div class="fa-liste"></div><div class="fa-diger">DİĞER</div>';
    blok.querySelector('.fa-vid').onclick = videoya;
    blok.querySelector('.fa-video').onclick = videoya;
    blok.querySelector('.fa-yeni').onclick = function(){ var p = document.querySelector('#pickGallery'); if (p) p.click(); };
    blok.querySelector('.fa-tumu').onclick = function(){ var l = document.querySelector('#libBody') || document.querySelector('#libTabs'); if (l) l.scrollIntoView({behavior: 'smooth', block: 'start'}); };
    var dog = false; try { dog = !!(window.parent !== window && window.parent.STUDYO_DOGRUDAN); } catch(e){}
    if (dog) { blok.querySelector('.fa-vid').innerHTML = SV('M15 18l-6-6 6-6', 18) + 'Stüdyo · Video'; document.body.classList.add('fa-dog'); }
    var top = wrap.querySelector('.studyo-ev');   // köprünün "‹ Stüdyo" düğmesi en üstte kalır
    if (top && top.parentNode === wrap) top.after(blok); else wrap.prepend(blok);
    if (tb) blok.querySelector('.fa-sag').appendChild(tb);   // tema düğmesi yeni başlığın sağında (boş satır kalmaz)
    document.body.classList.add('fa-acik');
    liste(blok.querySelector('.fa-liste'));
    return true;
  }
  /* Ayarlar kutusuna "Önceki ana ekran" anahtarı */
  function ayar(){
    var k = document.querySelector('#ayarKutu'); if (!k || k.querySelector('.fa-ayar')) return;
    var l = document.createElement('label'); l.className = 'fa-ayar'; l.style.cssText = 'display:flex;align-items:center;gap:8px;padding:10px 4px;font-size:14px';
    l.innerHTML = '<input type="checkbox"> Önceki ana ekran (yeni başlık, kartlar ve son çalışmalar gizlenir)';
    var c = l.querySelector('input'); c.checked = eski();
    c.onchange = function(){ try { localStorage.setItem(ESKI, c.checked ? '1' : '0'); } catch(e){} kur(); };
    k.appendChild(l);
  }
  function dene(){ if (typeof DB === 'undefined' || !document.querySelector('#home .home-wrap')) return setTimeout(dene, 300); kur(); ayar(); }
  dene();
  /* editörden ana ekrana dönünce liste yenilenir */
  try { var oRL = window.renderLibrary; if (typeof oRL === 'function') window.renderLibrary = function(){ var r = oRL.apply(this, arguments); try { kur(); } catch(e){} return r; }; } catch(e){}
  var ev = document.querySelector('#home');
  if (ev && window.MutationObserver) new MutationObserver(function(){ if (!ev.hidden) kur(); }).observe(ev, {attributes: true, attributeFilter: ['hidden']});
})();
