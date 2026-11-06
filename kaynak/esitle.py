#!/usr/bin/env python3
"""Stüdyo derleme betiği.
Karanlık Oda ve Kareden Videoya'nın GÜNCEL index.html'lerini değiştirmeden kopyalar, yalnız şu küçük eklemeleri yapar:
  1) window.STUDYO=1 bayrağı (uygulama Stüdyo'nun içinde olduğunu bilir)
  2) uygulamanın kendi service worker kaydı Stüdyo içinde atlanır (önbelleği Stüdyo yönetir)
  3) Karanlık Oda'nın GitHub→Cloudflare yönlendirmesi Stüdyo içinde atlanır
  4) Kareden Videoya: boot() ve proje aç/sil işlevlerine dışarıdan erişim (window.__kvBoot, window.__kv)
  5) sayfa sonuna Stüdyo köprüsü (kopru.js) ve varsa görünüm katmanı (foto-arayuz.js / video-arayuz.js)
Kullanım: python3 esitle.py <karanlik-oda klasörü> <kareden-videoya klasörü> <çıktı klasörü> <sürüm> <tarih>
"""
import sys, os, re, shutil, json
KO, KV, OUT, SURUM, TARIH = sys.argv[1:6]
BURA = os.path.dirname(os.path.abspath(__file__))
oku = lambda p: open(p, encoding='utf-8').read()
def yaz(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True); open(p, 'w', encoding='utf-8').write(s)
def degistir(s, eski, yeni, ad):
    n = s.count(eski)
    if n != 1: raise SystemExit(f'HATA ({ad}): beklenen 1 eşleşme, bulunan {n}: {eski[:70]!r}')
    return s.replace(eski, yeni)
def surum(s):
    m = re.search(r'<meta name="surum" content="([^"]+)"', s); return m.group(1) if m else '?'
kopru = oku(os.path.join(BURA, 'kopru.js'))
BAYRAK = '<head>\n<script>window.STUDYO=1;</script>'
SW_ESKI = "if(!ok)return;\n  var had=!!navigator.serviceWorker.controller;"
SW_YENI = "if(!ok||window.STUDYO)return;\n  var had=!!navigator.serviceWorker.controller;"
def govdeye(s, app):
    i = s.rfind('</body>')
    if i < 0: raise SystemExit('HATA: </body> yok')
    ek = '<script>\n' + kopru.replace('%APP%', app) + '\n</script>\n'
    say = os.path.join(BURA, 'sayfala.js')        # ortak sayfalama (bölümler sekme, kaydırıcılar tek tek, uzun bölümler sayfa)
    if os.path.exists(say): ek += '<script>\n' + oku(say) + '\n</script>\n'
    nz = os.path.join(BURA, app + '-nesne.js')     # yapay zekâ nesne katmanı (varsa)
    if os.path.exists(nz): ek += '<script>\n' + oku(nz) + '\n</script>\n'
    lt = os.path.join(BURA, app + '-lut.js')       # yerleşik LUT filtreleri (varsa)
    if os.path.exists(lt): ek += '<script>\n' + oku(lt) + '\n</script>\n'
    ara = os.path.join(BURA, app + '-arayuz.js')   # uygulamaya özel görünüm katmanı (varsa)
    if os.path.exists(ara): ek += '<script>\n' + oku(ara) + '\n</script>\n'
    return s[:i] + ek + s[i:]

# ---- Karanlık Oda → foto/
ko = oku(os.path.join(KO, 'index.html')); ko_s = surum(ko)
ko = degistir(ko, '<head>', BAYRAK, 'ko bayrak')
ko = degistir(ko, '(function(){ try{ var q=location.search;', '(function(){ try{ if(window.STUDYO) return; var q=location.search;', 'ko yönlendirme')
ko = degistir(ko, SW_ESKI, SW_YENI, 'ko sw')
ko = govdeye(ko, 'foto')
yaz(os.path.join(OUT, 'foto', 'index.html'), ko)
for f in ['icon.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable.png', 'manifest.webmanifest', 'ses-deneme.html']:
    if os.path.exists(os.path.join(KO, f)): shutil.copy(os.path.join(KO, f), os.path.join(OUT, 'foto', f))

# ---- Kareden Videoya → video/
kv = oku(os.path.join(KV, 'index.html')); kv_s = surum(kv)
kv = degistir(kv, '<head>', BAYRAK, 'kv bayrak')
kv = degistir(kv, SW_ESKI, SW_YENI, 'kv sw')
kv = degistir(kv, '\nboot();\n})();\n', '\nwindow.__kvBoot = boot();\nwindow.__kv = Object.assign(window.__kv || {}, {projOpen: projOpen, projDelete: projDelete, pid: () => PID, yenile: () => requestRender(), kaydet: () => projSaveNow(), an: () => PT, sure: () => timeline().total, klipSonu: () => timeline().clipsTotal, secKlip: () => { const c = S.clips[S.sel]; return c ? {i: S.sel, kind: c.kind, file: c.file, fkey: c.fkey, aspect: c.aspect, zoom: c.zoom || 1, ox: c.ox || 0, oy: c.oy || 0, ad: c.name || ""} : null; }, klipGeo: i => { const g = pvR && pvR.geo(i); return g ? {cx: (g.cx - (g.dx || 0)) / PW, cy: g.cy / PH, fw: g.fw / PW, fh: g.fh / PH} : null; }, klipAralik: k => { const i = S.clips.findIndex(c => c.fkey === k); if (i < 0) return null; const tl = timeline(); return [tl.starts[i] - (i > 0 ? tl.T : 0), tl.starts[i] + S.clips[i].dur, tl.T]; }, oynuyor: () => playing, durdur: () => { if (playing) pause(); }, klipVar: () => S.clips.length > 0, bicim: () => S.fmt});\n})();\n', 'kv kanca')
# Stüdyo nesne katmanı (kaynak/video-nesne.js): yazıların altına, klip ve geçişlerin üstüne çizilir; görünürken kare önbelleği kullanılmaz
kv = degistir(kv, '    drawYazilar(ctx, W, H, t);\n', '    try { window.__kvNesne && window.__kvNesne(ctx, W, H, t, exporting); } catch(e){}\n    drawYazilar(ctx, W, H, t);\n', 'kv nesne çizim')
kv = degistir(kv, '  function signature(t, tl){\n', '  function signature(t, tl){\n    if (window.__kvNesneVar && window.__kvNesneVar(t)) return null;\n', 'kv nesne imza')
kv = govdeye(kv, 'video')
yaz(os.path.join(OUT, 'video', 'index.html'), kv)
for f in ['icon.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable.png', 'manifest.webmanifest']:
    if os.path.exists(os.path.join(KV, f)): shutil.copy(os.path.join(KV, f), os.path.join(OUT, 'video', f))

# ---- kabuk
kvsrc = oku(os.path.join(KV, 'index.html')).split('\n')
a = next(i for i, l in enumerate(kvsrc) if 'SESSİZ EKRAN' in l) - 1
b = next(i for i in range(a, len(kvsrc)) if '</script>' in kvsrc[i])
sessiz = '\n'.join(kvsrc[a:b + 1])
if not sessiz.lstrip().startswith('<script>'): raise SystemExit('HATA: SESSİZ EKRAN bloğu bulunamadı')
k = oku(os.path.join(BURA, 'kabuk.html'))
for x, y in [('%SESSIZ%', sessiz), ('%SURUM%', SURUM), ('%TARIH%', TARIH), ('%KO%', ko_s), ('%KV%', kv_s)]: k = k.replace(x, y)
yaz(os.path.join(OUT, 'index.html'), k)
for f in ['icon.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable.png']:
    shutil.copy(os.path.join(BURA, f), os.path.join(OUT, f))
yaz(os.path.join(OUT, 'manifest.webmanifest'), json.dumps({
  "id": "studyo", "name": "Stüdyo", "short_name": "Stüdyo", "description": "Fotoğraf ve video düzenleyici (Karanlık Oda + Kareden Videoya)",
  "start_url": "./", "scope": "./", "display": "fullscreen", "display_override": ["fullscreen", "standalone"], "orientation": "any",
  "background_color": "#121a21", "theme_color": "#121a21", "lang": "tr",
  "icons": [{"src": "icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any"},
            {"src": "icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any"},
            {"src": "icon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any"},
            {"src": "icon-maskable.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"}]}, ensure_ascii=False, indent=1))
yaz(os.path.join(OUT, 'sw.js'), oku(os.path.join(BURA, 'sw.js')).replace('%SURUM%', SURUM))
print(f'Stüdyo {SURUM}: Karanlık Oda {ko_s} + Kareden Videoya {kv_s}')
