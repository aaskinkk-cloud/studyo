/* ===== 2.16 · YENİ DÜZEN (Kareden Videoya tasarım paketi, 8 ekran) — yalnız görünüm ve gezinme =====
   - Alt çubuk 14 düğme yerine 5 bölüm: Düzenle · Görünüm · Ses · Metin · Yayın. Seçili bölümün alt başlıkları
     hemen üstte hap düğmeler olarak hep görünür (gizli menü yok). Her hap, Kareden Videoya'nın KENDİ düğmesine basar.
   - Üst satır: Kaydet (anında kaydet) + Dışa Aktar. Oynatıcı satırı: önceki klip · oynat · sonraki klip.
   - Paneller sakin koyu kartlar; Dışa aktarmada çözünürlük büyük kartlar, "Videoyu oluştur" belirgin.
   - Video ana sayfasında "Son çalışmalarım": aç · çoğalt · sil.
   - Önceki düzen silinmedi: ⋯ menüsü › "Önceki düzene geç" (localStorage 'studyo.vYeni' = '0').
   Hiçbir işlev, algoritma ya da kayıt biçimi değişmedi; mevcut düğmeler gizlenir ama yerinde çalışır. */
(function(){
  if (!window.STUDYO) return;
  var B = document.body, K = 'studyo.vYeni', $ = function(s){ return document.querySelector(s); };
  function yeni(){ try { return localStorage.getItem(K) !== '0'; } catch(e){ return true; } }
  function ic(d){ return '<svg viewBox="0 0 24 24" aria-hidden="true">' + d + '</svg>'; }
  function bildir(m, ms){ var t = document.getElementById('toast'); if (!t) return; t.textContent = m; t.hidden = false; clearTimeout(bildir.t); bildir.t = setTimeout(function(){ t.hidden = true; }, ms || 2200); }
  function gorunur(el){ return !!el && !el.hidden && getComputedStyle(el).display !== 'none'; }
  var Y = 'body.st-yeni.kv-studyo.is-mod';

  var css = document.createElement('style'); css.id = 'st-yeni-css';
  css.textContent = [
    'body.st-yeni{--st-tq:#2ec5d3;--st-or:#f5a524;--st-or-ink:#1b1204}',
    /* ---- alt çubuk: eski 14 düğme gizli (yerinde duruyor, haplar onlara basar) ---- */
    Y + ' #mininav#mininav#mininav>button{display:none!important}',
    Y + ' #mininav#mininav#mininav{display:block!important;padding:0 0 calc(2px + env(safe-area-inset-bottom,0px))!important;overflow:visible!important;height:auto!important}',
    '#stGrup{display:none}',
    Y + ' #stGrup{display:block;width:100%}',
    '#stGrup .st-hap{display:flex;gap:6px;padding:7px 10px 4px;overflow-x:auto;scrollbar-width:none}',
    '#stGrup .st-hap::-webkit-scrollbar{display:none}',
    /* Kareden Videoya'nın "#mininav button" kuralları buradaki düğmelere de uyuyordu: daha güçlü seçici + !important */
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-hap button{flex:1 0 auto!important;flex-direction:row!important;min-width:0!important;min-height:34px!important;max-height:34px!important;height:34px!important;padding:0 14px!important;border-radius:17px!important;border:1.5px solid color-mix(in srgb,var(--is-ink) 18%,transparent)!important;background:none!important;box-shadow:none!important;color:var(--is-ink)!important;font:600 13.5px/1 var(--ui,system-ui),sans-serif!important;white-space:nowrap}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-hap button::after{display:none!important}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-hap button.on{background:var(--st-or)!important;border-color:var(--st-or)!important;color:var(--st-or-ink)!important}',
    '#stGrup .st-bol{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));padding:2px 4px 0}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-bol button{display:flex!important;flex-direction:column!important;align-items:center!important;justify-content:center!important;gap:4px;min-width:0!important;min-height:54px!important;max-height:60px!important;padding:4px 0!important;border:0!important;background:none!important;box-shadow:none!important;color:var(--is-mute)!important;font:500 11.5px/1.1 var(--ui,system-ui),sans-serif!important;border-radius:12px!important}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-bol button svg{display:block!important;width:24px!important;height:24px!important;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-bol button span{display:block!important;color:inherit!important;font-weight:inherit!important;max-width:100%!important;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-bol button::after{display:none!important}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-bol button.on{color:var(--st-or)!important;font-weight:700!important}',
    'body.st-yeni.kv-studyo.is-mod #mininav#mininav #stGrup .st-bol button:active{background:color-mix(in srgb,var(--is-ink) 8%,transparent)!important}',
    /* ---- üst satır ---- */
    'body:not(.st-yeni) #stKaydet,body:not(.st-yeni) #stOnceki,body:not(.st-yeni) #stSonraki{display:none!important}',
    Y + ' #isUst #stKaydet{display:inline-flex;align-items:center;justify-content:center;width:auto;flex:none;height:36px;padding:0 14px;border-radius:999px;border:1.5px solid var(--st-tq);background:none;color:var(--st-tq);font:700 14px/1 var(--ui,system-ui),sans-serif}',
    Y + ' #isUst #stKaydet.ok{background:var(--st-tq);color:#04262a}',
    Y + ' #isAktar{background:var(--st-or)!important;color:var(--st-or-ink)!important;border-color:var(--st-or)!important}',
    /* ---- oynatıcı satırı: önceki / sonraki klip ---- */

    Y + ' #isKont{grid-template-columns:38px minmax(0,1fr) 40px 50px 40px 40px 40px!important}',
    Y + ' #isKont #isGeriAl{grid-column:1!important}',
    Y + ' #isKont .is-zt{grid-column:2!important;font-size:10.5px!important;overflow:hidden;text-overflow:clip;letter-spacing:-.2px}',
    Y + ' #isKont #stOnceki{grid-column:3!important}',
    Y + ' #isKont #isOynat{grid-column:4!important}',
    Y + ' #isKont #stSonraki{grid-column:5!important}',
    Y + ' #isKont #isDaha{grid-column:6!important}',
    Y + ' #isKont #isTam{grid-column:7!important}',
    Y + ' #stOnceki,' + Y + ' #stSonraki{display:grid;place-items:center;width:40px;height:40px;border:0;background:none;color:var(--is-ink);padding:0;border-radius:12px}',
    Y + ' #stOnceki svg,' + Y + ' #stSonraki svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}',
    Y + ' #stOnceki:active,' + Y + ' #stSonraki:active{background:color-mix(in srgb,var(--is-ink) 9%,transparent)}',
    /* ---- paneller: sakin kartlar (turuncu dolgu başlıklar yerine) ---- */
    Y + ' .kv-pan{--sect-hd:color-mix(in srgb,var(--is-ink) 7%,var(--is-bg));--sect-ink:var(--is-ink)}',
    Y + ' .kv-pan .panel>.sect{border-radius:14px!important;border:1px solid color-mix(in srgb,var(--is-ink) 10%,transparent)!important}',
    Y + ' .kv-pan .panel>.sect>h3:first-child{text-transform:none!important;letter-spacing:0!important;font-size:14.5px!important;font-weight:650!important;padding:13px 14px!important;border-radius:14px 14px 0 0!important}',
    Y + ' .kv-pan .panel>.sect.kapali>h3:first-child{border-radius:14px!important}',
    Y + ' .kv-pan .panel>.sect:not(.kapali)>h3:first-child{color:var(--st-or)!important;box-shadow:inset 3px 0 0 var(--st-or)}',
    Y + ' .kv-pan .seg>button[aria-pressed="true"],' + Y + ' .kv-pan .chips>[aria-pressed="true"]{box-shadow:0 0 0 1.5px var(--st-or) inset}',
    Y + ' .kv-pan .btn.primary{background:var(--st-or)!important;border-color:var(--st-or)!important;color:var(--st-or-ink)!important}',
    /* ---- dışa aktarma: büyük çözünürlük kartları, belirgin "Videoyu oluştur", taşıma en altta ---- */
    Y + ' [data-panel="out"]{display:flex!important;flex-direction:column}',
    Y + ' [data-panel="out"][hidden]{display:none!important}',
    Y + ' [data-panel="out"]>#pkSect{order:20}',
    Y + ' [data-panel="out"] .seg[data-k="res"]{display:grid!important;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;flex-wrap:nowrap!important;overflow:visible!important}',
    Y + ' [data-panel="out"] .seg[data-k="res"]>button{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;min-height:66px;border-radius:14px!important;font-size:17px!important;font-weight:750!important;flex:none!important}',
    Y + ' [data-panel="out"] .seg[data-k="res"]>button small{display:block;font-size:11px;font-weight:500;opacity:.8}',
    Y + ' [data-panel="out"] .seg[data-k="res"]>button[aria-pressed="true"]{box-shadow:0 0 0 2px var(--st-or) inset!important;color:var(--st-or)!important}',
    Y + ' #expGo{width:100%;min-height:52px;font-size:16px!important;border-radius:26px!important}',
    /* ---- video ana sayfası: son çalışmalarım ---- */
    '#stSon{display:none}',
    'body.st-yeni #isAna #stSon:not([hidden]){display:block;margin:4px 0 2px}',
    '#stSon .st-son-bas{display:flex;align-items:center;justify-content:space-between;margin:0 2px 8px}',
    '#stSon .st-son-bas b{font-size:16px}',
    '#stSon .st-son-bas button{border:0;background:none;color:var(--is-mute,#aaa);font:500 13px/1 var(--ui,system-ui),sans-serif;padding:6px 2px}',
    '#stSon .st-son-kay{display:flex;gap:10px;overflow-x:auto;scrollbar-width:none;padding-bottom:4px}',
    '#stSon .st-son-kay::-webkit-scrollbar{display:none}',
    '#stSon .st-son-k{position:relative;flex:none;width:132px}',
    '#stSon .st-son-ac{display:block;width:100%;border:0;padding:0;background:none;color:inherit;text-align:left}',
    '#stSon .st-son-th{position:relative;height:84px;border-radius:12px;background:#222 center/cover no-repeat;display:grid;place-items:center;color:#fff}',
    '#stSon .st-son-th i{width:34px;height:34px;border-radius:50%;background:rgba(0,0,0,.45);display:grid;place-items:center;border:1.5px solid rgba(255,255,255,.85)}',
    '#stSon .st-son-th i svg{width:15px;height:15px;fill:#fff;stroke:none;margin-left:2px}',
    '#stSon .st-son-th em{position:absolute;left:6px;bottom:6px;font:600 10.5px/1 var(--mono,monospace);font-style:normal;background:rgba(0,0,0,.6);padding:3px 5px;border-radius:5px}',
    '#stSon .st-son-ad{display:block;font:600 12.5px/1.25 var(--ui,system-ui),sans-serif;margin-top:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding-right:26px}',
    '#stSon .st-son-ts{display:block;font-size:11px;color:var(--is-mute,#999);margin-top:2px}',
    '#stSon .st-son-m{position:absolute;right:-4px;bottom:6px;width:32px;height:32px;border:0;background:none;color:var(--is-mute,#aaa);border-radius:10px;display:grid;place-items:center;padding:0}',
    '#stSon .st-son-m svg{width:20px;height:20px;fill:currentColor}',
    '#stSonMenu{position:fixed;inset:0;z-index:300;background:rgba(0,0,0,.45);display:flex;align-items:flex-end}',
    '#stSonMenu[hidden]{display:none}',
    '#stSonMenu .st-sm-k{width:100%;background:var(--is-bg,#1f1f1f);color:var(--is-ink,#fff);border-radius:18px 18px 0 0;padding:14px 14px calc(14px + env(safe-area-inset-bottom,0px))}',
    '#stSonMenu .st-sm-ad{font-weight:700;font-size:15px;margin:2px 4px 10px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
    '#stSonMenu .st-sm-d{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}',
    '#stSonMenu .st-sm-d button{height:48px;border-radius:12px;border:1.5px solid color-mix(in srgb,var(--is-ink,#fff) 18%,transparent);background:none;color:inherit;font:600 14px/1 var(--ui,system-ui),sans-serif}',
    '#stSonMenu .st-sm-d button[data-a="ac"]{background:var(--st-or,#f5a524);border-color:var(--st-or,#f5a524);color:#1b1204}',
    '#stSonMenu .st-sm-d button[data-a="sil"].arm{background:#d93a3a;border-color:#d93a3a;color:#fff}',
    '#stSonMenu .st-sm-v{display:block;width:100%;height:44px;margin-top:8px;border:0;background:none;color:var(--is-mute,#aaa);font:500 14px/1 var(--ui,system-ui),sans-serif}'
  ].join('\n');
  (document.head || document.documentElement).appendChild(css);

  /* ================= alt çubuk: 5 bölüm + haplar ================= */
  var IK = {
    duz: '<circle cx="6" cy="6" r="2.6"/><circle cx="6" cy="18" r="2.6"/><path d="M20 4L8.1 15.9M14.5 14.5L20 20M8.1 8.1L12 12"/>',
    gor: '<path d="M12 3l1.8 4.6L18.5 9l-4.7 1.5L12 15l-1.8-4.5L5.5 9l4.7-1.4z"/><path d="M18.5 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>',
    ses: '<path d="M9 18V5l11-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="17" cy="16" r="3"/>',
    met: '<path d="M5 7V4.5h14V7M12 4.5v15M9 19.5h6"/>',
    yay: '<path d="M12 15V3.5M7 8.5l5-5 5 5"/><path d="M5 14v4.5A2.5 2.5 0 007.5 21h9a2.5 2.5 0 002.5-2.5V14"/>'
  };
  /* her hap: tab = Kareden Videoya bölümü (panel açılır) · btn = mevcut araç düğmesi · kapat = paneli kapat (zaman çizelgesi) */
  var G = [
    {id: 'duz', ad: 'Düzenle', h: [{ad: 'Çizelge', kapat: 1}, {ad: 'Klipler', tab: 'photo'}, {ad: 'Tuval', btn: '#isTvBtn'}, {ad: 'Nesne', btn: '#stNsBtn'}]},
    {id: 'gor', ad: 'Görünüm', h: [{ad: 'Şablon', tab: 'tpl'}, {ad: 'Efekt · Geçiş', tab: 'look'}]},
    {id: 'ses', ad: 'Ses', h: [{ad: 'Müzik', tab: 'music'}, {ad: 'Seslendirme', tab: 'voice'}]},
    {id: 'met', ad: 'Metin', h: [{ad: 'Yazı', btn: '#isMtBtn'}, {ad: 'Altyazı', tab: 'subs'}]},
    {id: 'yay', ad: 'Yayın', h: [{ad: 'Logo', tab: 'logo'}, {ad: 'Kapak', tab: 'cover'}, {ad: 'Bilgiler', tab: 'pub'}, {ad: 'Dışa aktar', tab: 'out'}]}
  ];
  var son = {}; try { son = JSON.parse(localStorage.getItem('studyo.vGrup') || '{}') || {}; } catch(e){}
  var secG = null;   /* kullanıcının en son seçtiği bölüm (durum belirsizken) */
  function nesneAcik(){ return B.classList.contains('st-ns-acik'); }
  function tuvalAcik(){ return gorunur($('#isTuval')); }
  function metinAcik(){ var m = $('#isMetin'); return !!m && !m.hidden; }
  function durum(){
    if (nesneAcik()) return ['duz', 3];
    if (tuvalAcik()) return ['duz', 2];
    if (metinAcik()) return ['met', 0];
    if (B.classList.contains('kv-kapali')) return secG && secG !== 'duz' ? [secG, -1] : ['duz', 0];   /* panel kapalı: zaman çizelgesi */
    var t = B.dataset.kvtab;
    for (var i = 0; i < G.length; i++) for (var j = 0; j < G[i].h.length; j++) if (G[i].h[j].tab === t) return [G[i].id, j];
    return [secG || 'duz', -1];
  }
  function araclariKapat(haric){
    if (haric !== 'tv' && tuvalAcik()){ var t = $('#isTvTmm'); if (t) t.click(); }
    if (haric !== 'mt' && metinAcik()){ var m = $('#isMtBtn'); if (m) m.click(); }
    if (haric !== 'ns' && nesneAcik()){ var n = $('#stNsBtn'); if (n) n.click(); }
  }
  function hapCalistir(g, j){
    var h = g.h[j]; if (!h) return; secG = g.id; son[g.id] = j; try { localStorage.setItem('studyo.vGrup', JSON.stringify(son)); } catch(e){}
    if (h.kapat){ araclariKapat(); try { window.kvStudyo && kvStudyo.kapat(true); } catch(e){} }
    else if (h.tab){
      araclariKapat(); var b = $('#mininav button[data-tab="' + h.tab + '"]');
      if (b) b.click();
      setTimeout(function(){ if (B.classList.contains('kv-kapali')) try { window.kvStudyo && kvStudyo.kapat(false); } catch(e){} if (h.tab === 'out') disaAc(); ciz(); }, 40);
    } else if (h.btn){
      var ac = h.btn === '#isTvBtn' ? tuvalAcik() : h.btn === '#isMtBtn' ? metinAcik() : nesneAcik();
      araclariKapat(h.btn === '#isTvBtn' ? 'tv' : h.btn === '#isMtBtn' ? 'mt' : 'ns');
      var x = $(h.btn); if (x && !ac) x.click();
    }
    setTimeout(ciz, 80); setTimeout(ciz, 400);
  }
  var kutu = null, imza = '';
  function kur(){
    var mn = document.getElementById('mininav'); if (!mn) return;
    if (!kutu){
      kutu = document.createElement('div'); kutu.id = 'stGrup';
      kutu.innerHTML = '<div class="st-hap" role="tablist" aria-label="Alt bölümler"></div><div class="st-bol" role="tablist" aria-label="Bölümler">'
        + G.map(function(g){ return '<button type="button" data-g="' + g.id + '">' + ic(IK[g.id]) + '<span>' + g.ad + '</span></button>'; }).join('') + '</div>';
      kutu.addEventListener('click', function(e){
        e.stopPropagation();
        var hb = e.target.closest('.st-hap [data-h]'), gb = hb ? null : e.target.closest('.st-bol [data-g]');
        if (gb){ var g = G.filter(function(x){ return x.id === gb.dataset.g; })[0], d = durum();
          if (d[0] === g.id && g.id !== 'duz' && !B.classList.contains('kv-kapali') && !tuvalAcik() && !metinAcik() && !nesneAcik()){   /* açık bölüme yine dokun: paneli kapat */
            secG = g.id; try { window.kvStudyo && kvStudyo.kapat(true); } catch(x){} setTimeout(ciz, 60); return; }
          hapCalistir(g, g.id === 'duz' ? 0 : (son[g.id] != null && g.h[son[g.id]] ? son[g.id] : 0)); return; }
        if (hb){ var g2 = G.filter(function(x){ return x.id === hb.dataset.gg; })[0]; hapCalistir(g2, +hb.dataset.h); }
      });
    }
    if (kutu.parentElement !== mn) mn.appendChild(kutu);
  }
  function ciz(){
    if (!kutu || !B.classList.contains('st-yeni')) return;
    var d = durum(), g = G.filter(function(x){ return x.id === d[0]; })[0] || G[0];
    var yeniImza = d.join(':') + '|' + secG;
    [].forEach.call(kutu.querySelectorAll('.st-bol button'), function(b){ var on = b.dataset.g === g.id; b.classList.toggle('on', on); b.setAttribute('aria-selected', on ? 'true' : 'false'); });
    var hp = kutu.querySelector('.st-hap');
    if (hp.dataset.gs !== g.id){ hp.dataset.gs = g.id;
      hp.innerHTML = g.h.map(function(h, j){ return '<button type="button" role="tab" data-gg="' + g.id + '" data-h="' + j + '">' + h.ad + '</button>'; }).join('');
      setTimeout(yerlesimYenile, 20); }
    [].forEach.call(hp.children, function(b, j){ var on = j === d[1]; b.classList.toggle('on', on); b.setAttribute('aria-selected', on ? 'true' : 'false'); });
    if (yeniImza !== imza){ imza = yeniImza; var o = hp.querySelector('.on'); if (o && o.scrollIntoView) try { o.scrollIntoView({block: 'nearest', inline: 'nearest'}); } catch(e){} }
  }
  function yerlesimYenile(){ try { window.kvStudyo && kvStudyo.yerlesim(); } catch(e){} window.dispatchEvent(new Event('resize')); }

  /* dışa aktar bölümüne girince temel kartlar açık gelsin (bir kez; kullanıcı kapatırsa kapalı kalır) */
  var disaAcildi = false;
  function disaAc(){
    if (disaAcildi) return; disaAcildi = true;
    var p = $('[data-panel="out"]'); if (!p) return;
    [].forEach.call(p.querySelectorAll(':scope>.sect.kapali>h3:first-child'), function(h){ if (/^(Çözünürlük|Kalite|Video oluştur)/.test(h.textContent.trim())) h.click(); });
  }

  /* ================= üst satır: Kaydet ================= */
  function kaydetKur(){
    var a = document.getElementById('isAktar'); if (!a || document.getElementById('stKaydet')) return !!a;
    var k = document.createElement('button'); k.type = 'button'; k.id = 'stKaydet'; k.textContent = 'Kaydet'; k.title = 'Projeyi bu cihaza şimdi kaydet (otomatik kayıt da sürüyor)';
    k.addEventListener('click', function(){
      var kv = window.__kv; if (!kv || !kv.kaydet) return;
      if (kv.klipVar && !kv.klipVar()){ bildir('Kaydedilecek klip yok'); return; }
      k.disabled = true;
      Promise.resolve(kv.kaydet()).then(function(ok){ k.disabled = false;
        if (ok === false){ bildir('Kaydedilemedi · cihazda yer olmayabilir', 3200); return; }
        k.classList.add('ok'); k.textContent = 'Kaydedildi'; bildir('Proje bu cihaza kaydedildi');
        setTimeout(function(){ k.classList.remove('ok'); k.textContent = 'Kaydet'; }, 1600); })
      .catch(function(){ k.disabled = false; bildir('Kaydedilemedi', 3000); });
    });
    a.before(k); return true;
  }

  /* ================= oynatıcı satırı: önceki / sonraki klip ================= */
  function atla(yon){
    var kv = window.__kv; if (!kv || !kv.tl || !kv.setPT) return;
    try {
      if (kv.oynuyor && kv.oynuyor()) kv.durdur();
      var tl = kv.tl(), st = tl.starts || [], an = kv.an(), hedef = null;
      if (!st.length) return;
      if (yon < 0){ for (var i = st.length - 1; i >= 0; i--) if (st[i] < an - 0.3){ hedef = st[i]; break; } if (hedef == null) hedef = 0; }
      else { for (var j = 0; j < st.length; j++) if (st[j] > an + 0.05){ hedef = st[j]; break; } if (hedef == null) hedef = Math.max(0, (tl.total || 0) - 0.05); }
      kv.setPT(hedef);
    } catch(e){ console.warn('Stüdyo atla', e); }
  }
  function oynaticiKur(){
    var o = document.getElementById('isOynat'); if (!o || document.getElementById('stOnceki')) return !!o;
    var a = document.createElement('button'); a.type = 'button'; a.id = 'stOnceki'; a.setAttribute('aria-label', 'Önceki klip'); a.title = 'Önceki klibin başına';
    a.innerHTML = ic('<path d="M6 5v14"/><path d="M19 5.5L9.5 12l9.5 6.5z"/>');
    var b = document.createElement('button'); b.type = 'button'; b.id = 'stSonraki'; b.setAttribute('aria-label', 'Sonraki klip'); b.title = 'Sonraki klibin başına';
    b.innerHTML = ic('<path d="M18 5v14"/><path d="M5 5.5l9.5 6.5L5 18.5z"/>');
    a.addEventListener('click', function(){ atla(-1); }); b.addEventListener('click', function(){ atla(1); });
    o.before(a); o.after(b); return true;
  }

  /* ================= video ana sayfası: son çalışmalarım ================= */
  var dbS = null;
  function vt(){
    if (dbS) return dbS;
    dbS = new Promise(function(res, rej){ try {
      var r = indexedDB.open('kareden-videoya', 1);
      r.onupgradeneeded = function(){ try { r.result.createObjectStore('files'); r.result.createObjectStore('meta'); } catch(e){} };   /* Kareden Videoya ile aynı yapı */
      r.onsuccess = function(){ res(r.result); }; r.onerror = function(){ rej(r.error); };
    } catch(e){ rej(e); } });
    return dbS;
  }
  function metaAl(fn, yaz){ return vt().then(function(d){ return new Promise(function(res, rej){ var tx = d.transaction('meta', yaz ? 'readwrite' : 'readonly'), st = tx.objectStore('meta'), out = fn(st);
    tx.oncomplete = function(){ res(out && out.result !== undefined ? out.result : out); }; tx.onerror = function(){ rej(tx.error); }; }); }); }
  function ozetler(){ return metaAl(function(st){ return st.getAll(IDBKeyRange.bound('s:', 's:\uffff')); }); }
  function sure(s){ s = Math.max(0, Math.round(s || 0)); return Math.floor(s/60) + ':' + String(s % 60).padStart(2, '0'); }
  function tarih(t){ var d = new Date(t), p = function(n){ return String(n).padStart(2, '0'); }; return p(d.getDate()) + '.' + p(d.getMonth() + 1) + '.' + d.getFullYear(); }
  function kac(x){ return String(x == null ? '' : x).replace(/[&<>"']/g, function(c){ return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]; }); }
  var sonKutu = null, menu = null, menuId = null, sonListe = [];
  function sonKur(){
    var ana = document.getElementById('isAna'); if (!ana) return false;
    if (!sonKutu){
      sonKutu = document.createElement('div'); sonKutu.id = 'stSon'; sonKutu.hidden = true;
      sonKutu.innerHTML = '<div class="st-son-bas"><b>Son çalışmalarım</b><button type="button" id="stSonTum">Tümünü gör ›</button></div><div class="st-son-kay"></div>';
      sonKutu.querySelector('#stSonTum').addEventListener('click', function(){ var t = ana.querySelector('[data-a="taslak"]'); if (t) t.click(); });
      sonKutu.addEventListener('click', function(e){
        var m = e.target.closest('.st-son-m'); if (m){ e.stopPropagation(); menuAc(m.dataset.id); return; }
        var a = e.target.closest('.st-son-ac'); if (a && window.__kv && __kv.projOpen){ e.stopPropagation(); __kv.projOpen(a.dataset.id); }
      });
    }
    var ref = ana.querySelector('.is-kart-ana');
    if (ref && sonKutu.previousElementSibling !== ref) ref.after(sonKutu);
    else if (!ref && !sonKutu.parentElement) ana.appendChild(sonKutu);
    return true;
  }
  function sonCiz(){
    if (!sonKutu) return;
    ozetler().then(function(l){
      l = (l || []).filter(function(s){ return s && s.id; }).sort(function(a, b){ return b.when - a.when; }); sonListe = l;
      sonKutu.hidden = !l.length;
      sonKutu.querySelector('.st-son-kay').innerHTML = l.slice(0, 12).map(function(s){
        return '<div class="st-son-k"><button type="button" class="st-son-ac" data-id="' + kac(s.id) + '" aria-label="' + kac(s.name) + ' projesini aç">'
          + '<span class="st-son-th"' + (s.thumb ? ' style="background-image:url(\'' + s.thumb + '\')"' : '') + '><i>' + ic('<path d="M7 4.5l12 7.5-12 7.5z"/>') + '</i><em>' + sure(s.dur) + '</em></span>'
          + '<span class="st-son-ad">' + kac(s.name || 'Adsız taslak') + '</span><span class="st-son-ts">' + (s.n || 0) + ' klip · ' + tarih(s.when) + '</span></button>'
          + '<button type="button" class="st-son-m" data-id="' + kac(s.id) + '" aria-label="Diğer işlemler">' + ic('<circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/>') + '</button></div>';
      }).join('');
    }).catch(function(){ sonKutu.hidden = true; });
  }
  function menuAc(id){
    var s = sonListe.filter(function(x){ return x.id === id; })[0]; if (!s) return;
    if (!menu){
      menu = document.createElement('div'); menu.id = 'stSonMenu'; menu.hidden = true;
      menu.innerHTML = '<div class="st-sm-k" role="dialog" aria-label="Proje işlemleri"><div class="st-sm-ad"></div><div class="st-sm-d"><button type="button" data-a="ac">Aç</button><button type="button" data-a="cog">Çoğalt</button><button type="button" data-a="sil">Sil</button></div><button type="button" class="st-sm-v" data-a="vaz">Vazgeç</button></div>';
      menu.addEventListener('click', function(e){
        if (e.target === menu){ menuKapat(); return; }
        var b = e.target.closest('[data-a]'); if (!b) return; var a = b.dataset.a, pid = menuId;
        if (a === 'vaz'){ menuKapat(); return; }
        if (a === 'ac'){ menuKapat(); if (window.__kv && __kv.projOpen) __kv.projOpen(pid); return; }
        if (a === 'cog'){ menuKapat(); cogalt(pid); return; }
        if (a === 'sil'){
          if (!b.classList.contains('arm')){ b.classList.add('arm'); b.textContent = 'Silinsin mi?'; return; }
          menuKapat(); if (window.__kv && __kv.projDelete) Promise.resolve(__kv.projDelete(pid)).then(function(){ setTimeout(sonCiz, 120); }); }
      });
      document.body.appendChild(menu);
    }
    menuId = id; menu.querySelector('.st-sm-ad').textContent = s.name || 'Adsız taslak';
    var sb = menu.querySelector('[data-a="sil"]'); sb.classList.remove('arm'); sb.textContent = 'Sil';
    menu.hidden = false;
  }
  function menuKapat(){ if (menu) menu.hidden = true; menuId = null; }
  /* çoğalt: aynı dosyaları kullanan yeni bir taslak kaydı (dosyalar kopyalanmaz, yer kaplamaz; silinince diğeri etkilenmez) */
  function cogalt(id){
    var yid = Date.now().toString(36) + 'k';
    metaAl(function(st){ var r1 = st.get('p:' + id), r2 = st.get('s:' + id);
      r2.onsuccess = function(){ var p = r1.result, s = r2.result; if (!p || !s) return;
        p.id = yid; s = Object.assign({}, s, {id: yid, when: Date.now(), name: (s.name || 'Adsız taslak') + ' (kopya)'});
        st.put(p, 'p:' + yid); st.put(s, 's:' + yid); };
      return null; }, true)
    .then(function(){ bildir('Proje çoğaltıldı'); try { window.__kv && __kv.taslakYenile && __kv.taslakYenile(); } catch(e){} setTimeout(sonCiz, 150); })
    .catch(function(){ bildir('Çoğaltılamadı', 3000); });
  }

  /* ================= ⋯ menüsü: önceki düzene geç ================= */
  function uygula(){
    var on = yeni(); B.classList.toggle('st-yeni', on);
    var b = document.getElementById('stYeniSec'); if (b) b.dataset.lb = on ? 'Önceki düzene geç' : 'Yeni düzene geç';
    if (on){ kur(); ciz(); }
    setTimeout(yerlesimYenile, 30);
  }
  (function bagla(n){
    var mn = document.getElementById('mininav'), m = document.getElementById('pvDahaMenu');
    var hazir = mn && m && kaydetKur() && oynaticiKur();
    sonKur() && sonCiz();
    if (!hazir){ if (n < 150) setTimeout(function(){ bagla(n + 1); }, 400); return; }
    if (!document.getElementById('stYeniSec')){ var b = document.createElement('button'); b.type = 'button'; b.id = 'stYeniSec';
      b.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" style="fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M3 15h18M9.5 15v5M14.5 15v5"/></svg>';
      b.addEventListener('click', function(){ try { localStorage.setItem(K, yeni() ? '0' : '1'); } catch(e){} uygula(); bildir(yeni() ? 'Yeni düzen' : 'Önceki düzen'); });
      m.appendChild(b); }
    /* Kareden Videoya alt çubuğu yeniden kurunca (içini boşaltınca) bölüm çubuğu geri eklenir */
    new MutationObserver(function(){ if (B.classList.contains('st-yeni') && kutu && kutu.parentElement !== mn){ kur(); ciz(); } }).observe(mn, {childList: true});
    /* durum değişince (bölüm, panel aç/kapa, araçlar) haplar güncellenir */
    var zz = 0, sonra = function(){ if (zz) return; zz = setTimeout(function(){ zz = 0; ciz(); }, 60); };
    new MutationObserver(sonra).observe(B, {attributes: true, attributeFilter: ['class', 'data-kvtab']});
    ['isTuval', 'isMetin'].forEach(function(id){ var e = document.getElementById(id); if (e) new MutationObserver(sonra).observe(e, {attributes: true, attributeFilter: ['hidden', 'style', 'class']}); });
    /* taslak listesi değişince son çalışmalar da yenilenir */
    var dg = document.getElementById('draftGrid'); if (dg) new MutationObserver(function(){ clearTimeout(sonCiz.t); sonCiz.t = setTimeout(sonCiz, 150); }).observe(dg, {childList: true});
    uygula();
  })(0);
})();
