/* ===== STÜDYO 2.7 · SIRA VE SEÇİLİ KLİP TEK EKRANDA =====
   Kareden Videoya'nın kapanışının İÇİNDE çalışır (esitle.py boot() satırından hemen önce ekler);
   bu yüzden S, cur(), klipCogalt(), klipBol(), renderStrip() gibi iç işlevlere doğrudan erişir.
   - Klipler › "Sıra ve klip" sekmesi: katmanlar + seçili klip araç çubuğu + büyük klip şeridi + seçili klibin ayarları
     (birleştirme isAltYap içindeki küçük kancayla yapılır, bkz. esitle.py)
   - Şeritte her klipte: ses aç/kapat (yalnız video), çoğalt, sil (sil: önce klip seçili olmalı → yanlışlıkla silme olmaz)
   - Seçili klip çubuğu: Sola · Sağa · Ses · Çoğalt · Böl · Sil + videoda ses kaydırıcısı (%0–150)
   - Klip katmanında büyük, yan yana tekrarlanan klip resimleri; sesi kapalı / kısık videoda simge
   Kayıt biçimi değişmez (c.mute, c.vol zaten vardı). */
if (window.STUDYO) (function(){
  const IK = {
    ses: '<path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16.5 8.5a5 5 0 010 7M19 6a8.5 8.5 0 010 12"/>',
    kis: '<path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16.5 9.5a3.5 3.5 0 010 5"/>',
    kapali: '<path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M17 9.5l5 5M22 9.5l-5 5"/>',
    cog: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5.5A1.5 1.5 0 0014.5 4h-9A1.5 1.5 0 004 5.5v9A1.5 1.5 0 005.5 16H8"/>',
    sil: '<path d="M4 7h16M9 7V4.5h6V7M6.5 7l1 12.5h9l1-12.5M10 11v5M14 11v5"/>',
    sol: '<path d="M14 6l-6 6 6 6M20 12H8"/>',
    sag: '<path d="M10 6l6 6-6 6M4 12h12"/>',
    bol: '<path d="M12 3v18"/><rect x="3" y="7" width="6" height="10" rx="1.5"/><rect x="15" y="7" width="6" height="10" rx="1.5"/>'
  };
  const sv = k => '<svg viewBox="0 0 24 24" aria-hidden="true">' + IK[k] + '</svg>';
  const sesIk = c => c.mute || !(c.vol > 0) ? 'kapali' : c.vol < 100 ? 'kis' : 'ses';
  const kaydet = () => { try { updateSummary(); } catch(e){} try { saveLast(); projSave(); } catch(e){} };

  const css = document.createElement('style'); css.id = 'st-sira-css';
  css.textContent = `
/* --- büyük klip şeridi --- */
body.kv-studyo .reel{gap:8px;padding-block:4px}
body.kv-studyo .frame{height:118px;min-width:92px;border-radius:9px;border-width:2.5px}
body.kv-studyo .frame canvas{width:100%;height:100%;object-fit:cover}
body.kv-studyo .frame .xdup,body.kv-studyo .frame .xdel{display:none!important}
body.kv-studyo .frame .no{font-size:11px;top:4px;left:4px;padding:1px 5px}
body.kv-studyo .frame .du{bottom:auto;top:4px;right:4px;font-size:11px;padding:1px 5px}
body.kv-studyo .frame .vid{bottom:auto;top:24px;left:4px}
body.kv-studyo .frame .smp{top:24px;right:4px}
.st-akt{position:absolute;left:0;right:0;bottom:0;display:flex;justify-content:space-evenly;align-items:flex-end;padding:16px 2px 4px;background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.8));pointer-events:none}
.st-akt>span{pointer-events:auto;position:relative;width:27px;height:27px;border-radius:50%;display:grid;place-items:center;background:rgba(0,0,0,.55);border:1px solid rgba(255,255,255,.3);color:#fff}
.st-akt>span::after{content:"";position:absolute;inset:-5px}
.st-akt svg{width:15px;height:15px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.st-akt>span.kapali{background:#e0645a;border-color:transparent}
.st-akt>span b{position:absolute;top:-13px;left:50%;transform:translateX(-50%);font:700 9px/1 system-ui,sans-serif;background:rgba(0,0,0,.75);padding:1px 3px;border-radius:3px;white-space:nowrap}
.frame[aria-current="true"] .st-akt>[data-a="sil"]{background:#e0645a;border-color:transparent}
.frame.msel .st-akt{display:none}
/* --- klip katmanı: büyük resimler --- */
body.kv-studyo .km-satir[data-s="klip"],body.kv-studyo .km-et[data-s="klip"]{height:60px}
body.kv-studyo .km-satir[data-s="klip"] .km-oge.k{top:3px;height:54px;line-height:15px;padding:3px 5px;border-radius:7px;font-size:11px}
body.kv-studyo.dikey-sik .km-satir[data-s="klip"],body.kv-studyo.dikey-sik .km-et[data-s="klip"]{height:48px}
body.kv-studyo.dikey-sik .km-satir[data-s="klip"] .km-oge.k{top:2px;height:44px}
body.kv-studyo .km-et[data-s="klip"]{font-weight:700;color:var(--ink)}
.km-oge.k .st-kms{position:absolute;right:3px;bottom:3px;width:18px;height:18px;border-radius:50%;display:grid;place-items:center;background:rgba(0,0,0,.65);color:#fff;pointer-events:none}
.km-oge.k .st-kms.kapali{background:#e0645a}
.km-oge.k .st-kms svg{width:12px;height:12px;fill:none;stroke:currentColor;stroke-width:2.2;stroke-linecap:round;stroke-linejoin:round}
/* --- "Sıra ve klip" sekmesi: büyük şerit → seçili klip çubuğu → klibin ayarları → katmanlar (kaydırılır) --- */
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan{display:flex;flex-direction:column}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan>*{flex:none;order:0}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan>.strip{order:1;margin-top:4px}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan>#stKlipBar{order:2}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan>section.panel{order:3}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan>#katman{order:4;margin-top:12px}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] .kv-pan>nav{order:5}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] #clipSect>.cliphead{display:none}
body.kv-studyo.is-mod.st-sk[data-kvtab="photo"] #clipSect>.cgrp:has(#cLeft){display:none!important}
#stKlipBar .st-bas .st-ad{flex:1;min-width:0}
#stKlipBar .st-bas button{border:1.5px solid rgba(127,127,127,.4);background:transparent;color:var(--ink);border-radius:999px;padding:4px 10px;font:600 11.5px/1.1 inherit}
#stKlipBar .st-bas button[aria-pressed="true"]{background:var(--accent);color:var(--accent-ink);border-color:transparent}
/* --- seçili klip çubuğu --- */
#stKlipBar{display:none}
body.kv-studyo[data-kvtab="photo"] .kv-pan>#stKlipBar:not([hidden]){display:block;margin:8px 0 2px;padding:8px 6px 6px;border-radius:12px;background:var(--panel,rgba(127,127,127,.08))}
#stKlipBar .st-bas{display:flex;align-items:center;gap:8px;font-size:12.5px;font-weight:700;padding:0 4px 6px;color:var(--ink)}
#stKlipBar .st-bas small{font-weight:500;opacity:.7}
#stKlipBar .st-dug{display:grid;gap:4px}
#stKlipBar .st-dug button{display:flex;flex-direction:column;align-items:center;gap:3px;padding:7px 2px 5px;min-width:0;border:0;border-radius:10px;background:transparent;color:var(--ink);font:600 11px/1.1 inherit;cursor:pointer}
#stKlipBar .st-dug button:active{background:rgba(127,127,127,.18)}
#stKlipBar .st-dug button[hidden]{display:none}
#stKlipBar .st-dug button:disabled{opacity:.32}
#stKlipBar .st-dug svg{width:23px;height:23px;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}
#stKlipBar .st-dug button.kirmizi{color:#ff7a70}
#stKlipBar .st-dug button.on{color:#ff7a70}
#stKlipBar .st-ses{display:flex;align-items:center;gap:10px;padding:6px 6px 2px}
#stKlipBar .st-ses[hidden]{display:none}
#stKlipBar .st-ses span{font-size:12px;white-space:nowrap}
#stKlipBar .st-ses input{flex:1;min-width:0}
#stKlipBar .st-ses output{font:700 12px/1 ui-monospace,monospace;min-width:40px;text-align:right}
`;
  (document.head || document.documentElement).appendChild(css);

  /* ---------- şerit: her klipte ses / çoğalt / sil ---------- */
  function kare(c){
    const el = c.el; if (!el) return;
    const cv = el.querySelector('canvas');
    if (cv && c.thumb && !el._stHD){ el._stHD = 1;   /* 74 px küçük resim büyük karede bulanık kalmasın */
      try { const h = Math.round(118*Math.min(2, devicePixelRatio || 1)), w = Math.max(1, Math.round(c.thumb.width*h/c.thumb.height));
        cv.width = w; cv.height = h; cv.getContext('2d').drawImage(c.thumb, 0, 0, w, h); } catch(e){} }
    let a = el.querySelector('.st-akt');
    if (!a){
      a = document.createElement('span'); a.className = 'st-akt';
      a.innerHTML = '<span role="button" data-a="ses"></span><span role="button" data-a="cog" aria-label="Çoğalt" title="Çoğalt: sağına bir kopya ekler">' + sv('cog') + '</span><span role="button" data-a="sil" aria-label="Sil" title="Sil (önce klibi seç)">' + sv('sil') + '</span>';
      a.addEventListener('click', e => {
        const s = e.target.closest('[data-a]'); if (!s) return;
        e.stopPropagation(); e.preventDefault(); if (suppressClick) return;
        const idx = S.clips.indexOf(c); if (idx < 0) return;
        const k = s.dataset.a;
        if (k === 'cog'){ klipCogalt(c); return; }
        if (k === 'sil'){
          if (idx !== S.sel){ el.click(); toast('Klip ' + (idx + 1) + ' seçildi · silmek için çöp kutusuna bir daha dokun'); return; }
          if (playing) pause(); const x = el.querySelector('.xdel'); if (x) x.click(); return; }
        if (k === 'ses' && c.kind === 'video'){ sesDegis(c, !c.mute && c.vol > 0); return; }
      });
      el.appendChild(a);
    }
    const ses = a.querySelector('[data-a="ses"]'), v = c.kind === 'video';
    ses.hidden = !v; ses.style.display = v ? '' : 'none';
    if (v){ const ik = sesIk(c), sk = ik + '|' + c.vol;
      if (ses.dataset.d !== sk){ ses.dataset.d = sk; ses.className = ik === 'kapali' ? 'kapali' : '';
        ses.innerHTML = sv(ik) + (ik === 'kapali' ? '' : c.vol !== 100 ? '<b>%' + Math.round(c.vol) + '</b>' : '');
        ses.setAttribute('aria-label', ik === 'kapali' ? 'Sesi aç' : 'Sesi kapat'); ses.title = ik === 'kapali' ? 'Ses kapalı · dokun: aç' : 'Ses %' + Math.round(c.vol) + ' · dokun: kapat'; } }
  }
  function kareler(){ S.clips.forEach(kare); }
  function sesDegis(c, kapat){
    if (playing) pause();
    c.mute = !!kapat; if (!c.mute && !(c.vol > 0)) c.vol = 100;
    if (c === cur()) try { syncClipUI(); } catch(e){}
    kare(c); bar(); KM.sig = ''; kmPlan(); kaydet();
    toast((c.mute ? 'Ses kapatıldı' : 'Ses açıldı · %' + Math.round(c.vol)) + ' · klip ' + (S.clips.indexOf(c) + 1), 1800);
  }

  /* ---------- seçili klip çubuğu (katmanların hemen altında) ---------- */
  let B = null;
  function barKur(){
    B = document.createElement('div'); B.id = 'stKlipBar'; B.setAttribute('role', 'group'); B.setAttribute('aria-label', 'Seçili klip');
    B.innerHTML = '<div class="st-bas"><span class="st-ad"></span><small class="st-ek"></small><button type="button" class="st-msel" aria-pressed="false" title="Birden fazla klibi seçip birlikte düzenle">☐ Çoklu seçim</button></div>'
      + '<div class="st-dug">'
      + '<button type="button" data-a="sol">' + sv('sol') + 'Sola</button>'
      + '<button type="button" data-a="sag">' + sv('sag') + 'Sağa</button>'
      + '<button type="button" data-a="ses"></button>'
      + '<button type="button" data-a="cog">' + sv('cog') + 'Çoğalt</button>'
      + '<button type="button" data-a="bol" title="Klibi oynatma çizgisinden ikiye böler">' + sv('bol') + 'Böl</button>'
      + '<button type="button" data-a="sil" class="kirmizi">' + sv('sil') + 'Sil</button></div>'
      + '<div class="st-ses" hidden><span>Klibin sesi</span><input type="range" min="0" max="150" step="5" aria-label="Klibin sesi"><output></output></div>';
    B.querySelector('.st-msel').addEventListener('click', () => { const m = $('#mselBtn'); if (m) m.click(); setTimeout(bar, 0); });
    B.querySelector('.st-dug').addEventListener('click', e => {
      const b = e.target.closest('button[data-a]'); if (!b || b.disabled) return; const c = cur(); if (!c) return;
      const k = b.dataset.a;
      if (k === 'sol') $('#cLeft').click();
      else if (k === 'sag') $('#cRight').click();
      else if (k === 'ses' && c.kind === 'video') sesDegis(c, !c.mute && c.vol > 0);
      else if (k === 'cog') klipCogalt(c);
      else if (k === 'bol'){ const tl = timeline(), a0 = tl.starts[S.sel], i0 = S.sel;   /* çizgi seçili klibin dışındaysa klibin ortasından böl */
        if (!(PT > a0 + 0.3 && PT < a0 + c.dur - 0.3)){ if (playing) pause(); PT = a0 + c.dur/2; }
        klipBol(); void i0; }
      else if (k === 'sil'){ if (playing) pause(); const x = c.el && c.el.querySelector('.xdel'); if (x) x.click(); }
    });
    const r = B.querySelector('.st-ses input'), o = B.querySelector('.st-ses output');
    r.addEventListener('input', () => { const c = cur(); if (!c || c.kind !== 'video') return; c.vol = +r.value; if (c.vol > 0) c.mute = false; o.textContent = '%' + c.vol;
      const vv = $('#vVol'); if (vv){ vv.value = c.vol; const vo = $('#vVolO'); if (vo) vo.textContent = trNum(c.vol, 0) + '%'; } kare(c); });
    r.addEventListener('change', () => { const c = cur(); if (!c) return; if (!(c.vol > 0)) c.mute = true; try { syncClipUI(); } catch(e){} kare(c); KM.sig = ''; kmPlan(); kaydet(); });
  }
  function bar(){
    const km = $('#katman'); if (!km) return;
    if (!B) barKur();
    if (B.previousElementSibling !== km) km.after(B);
    const c = cur(); B.hidden = !c || !S.clips.length; if (B.hidden) return;
    const i = S.sel, n = S.clips.length, v = c.kind === 'video';
    B.querySelector('.st-ad').textContent = (v ? 'Video ' : 'Fotoğraf ') + (i + 1) + ' / ' + n;
    B.querySelector('.st-ek').textContent = trNum(c.dur, 1) + ' sn' + (MSEL && selClips().length > 1 ? ' · çoklu seçim açık' : '');
    const mb = $('#mselBtn'), on = !!MSEL, ms = B.querySelector('.st-msel'); ms.setAttribute('aria-pressed', String(on)); ms.textContent = (on ? '☑ ' : '☐ ') + 'Çoklu seçim'; ms.hidden = !mb;
    const q = k => B.querySelector('.st-dug [data-a="' + k + '"]');
    q('sol').disabled = i === 0; q('sag').disabled = i === n - 1;
    const sb = q('ses'); sb.hidden = !v;
    if (v){ const ik = sesIk(c); sb.innerHTML = sv(ik) + (ik === 'kapali' ? 'Sesi aç' : 'Sessiz'); sb.classList.toggle('on', ik === 'kapali'); }
    B.querySelector('.st-dug').style.gridTemplateColumns = 'repeat(' + (v ? 6 : 5) + ',minmax(0,1fr))';
    const sr = B.querySelector('.st-ses'); sr.hidden = !v;
    if (v){ const r = sr.querySelector('input'); if (document.activeElement !== r) r.value = c.mute ? 0 : c.vol; sr.querySelector('output').textContent = c.mute ? 'kapalı' : '%' + Math.round(c.vol); }
  }

  /* ---------- klip katmanı: büyük resim + ses simgesi ---------- */
  function katman(){
    const ic = $('#kmIc'); if (!ic || !ic._O || !ic._O.klip) return;
    ic.querySelectorAll('.km-oge.k').forEach(b => {
      const o = ic._O.klip[+b.dataset.j], c = o && o.img; if (!c || !c.thumb) return;
      if (!c._stKm){ try { const h = 120, cv = mk(Math.max(1, Math.round(h*c.thumb.width/c.thumb.height)), h); cv.getContext('2d').drawImage(c.thumb, 0, 0, cv.width, cv.height); c._stKm = cv.toDataURL('image/jpeg', 0.72); cv.width = 0; } catch(e){ c._stKm = ''; } }
      if (c._stKm) b.style.backgroundImage = 'url(' + c._stKm + ')';
      let s = b.querySelector('.st-kms');
      if (c.kind === 'video' && (c.mute || c.vol !== 100)){
        if (!s){ s = document.createElement('i'); s.className = 'st-kms'; b.appendChild(s); }
        const ik = sesIk(c); s.className = 'st-kms' + (ik === 'kapali' ? ' kapali' : ''); s.innerHTML = sv(ik);
      } else if (s) s.remove();
    });
  }

  /* iç işlevlere ekle (aynı kapanıştaki bağları değiştirir; öncekiler aynen çalışır) */
  const _rs = renderStrip; renderStrip = function(){ const r = _rs.apply(this, arguments); try { kareler(); bar(); } catch(e){ console.warn('Stüdyo şerit', e); } return r; };
  const _km = kmCiz; kmCiz = function(){ const r = _km.apply(this, arguments); try { katman(); bar(); } catch(e){ console.warn('Stüdyo katman', e); } return r; };
  const _sc = syncClipUI; syncClipUI = function(){ const r = _sc.apply(this, arguments); try { bar(); const c = cur(); if (c) kare(c); } catch(e){} return r; };
  const vm = $('#vMute'), vv = $('#vVol');
  if (vm) vm.addEventListener('change', () => { const c = cur(); if (c) kare(c); KM.sig = ''; kmPlan(); });
  if (vv) vv.addEventListener('change', () => { const c = cur(); if (c) kare(c); bar(); KM.sig = ''; kmPlan(); });
})();
