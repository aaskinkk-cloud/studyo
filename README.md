# Stüdyo

Fotoğraf ve video düzenleyici. **Karanlık Oda** ile **Kareden Videoya**'yı tek uygulamada birleştirir. Tasarımda InShot'un sade düzeni örnek alındı. Tarayıcıda çalışır (PWA). Dosyalar telefondan dışarı gönderilmez.

**Güncel sürüm:** 1.1 · 01.11.2026. İçinde Karanlık Oda 6.67 ve Kareden Videoya 9.15 var.

## 1.1 (01.11.2026): fotoğraf editörü InShot düzeninde, fotoğraftan videoya geçiş
- **Fotoğraf editörü** (yalnız görünüm değişti; araçlar, paneller ve algoritmalar aynı):
  - Altta tek sıra, kaydırılabilir araç çubuğu: Otomatik, Kırp, Filtreler, Işık, Renk, HSL, Eğriler, Detay, Efektler, Selfi, Yazı·İmza, Katman, HDR, Rötuş, Fırça, Haber, Geçmiş, Kaydet. Kategori çubuğu kaldırıldı.
  - Fotoğraf açılınca önizleme tam boy. Araca dokununca panel açılır; başlığında araç adı ve **✓** var. ✓ paneli kapatır; Kırp, Rötuş ve Fırça'da kipten de çıkar, kırpma o anda kaydedilir.
  - Üst çubuk: ← · orijinal · geri al / ileri al · karşılaştır · bölünmüş görünüm · Basit · 🎬 · **Dışa Aktar**.
- **Bu fotoğrafla video yap** (🎬 üst çubukta ve Dışa Aktar panelinin başında):
  - Düzenlenmiş fotoğraf tam çözünürlükte JPG yapılır, düzenleme önce kitaplığa kaydedilir, ardından video editörü bu fotoğrafla açılır.
  - Geri tuşu yine ana sayfaya döner; arada fazladan adım kalmaz.
- Ana sayfa kartlarındaki başlıklar kısaldı (Fotoğraf / Video); iki satıra bölünmüyor.
- Görünüm katmanı ayrı bir dosyada: `kaynak/foto-arayuz.js`. Karanlık Oda'nın kodu değişmedi.

## 1.0 (01.11.2026): ilk sürüm
- **Ana sayfa:** Fotoğraf düzenle, Video düzenle, Kareden Videoya, Son çalışmalar, Fotoğraf araçları (Kamera, Toplu düzenle, Kitaplık · hazır ayarlar, Kabataş Haber, Hızlı haber, Bloğum, Logolar).
- **Alt menü:**
  - **Ana sayfa**
  - **Projelerim:** fotoğraf ve video projeleri birlikte; Tümü / Fotoğraf / Video / ★ Favoriler süzgeci.
  - **Taslaklar:** dışa aktarılmamış videolar ve düzenlemesi süren fotoğraflar.
  - **Ayarlar:** gece/gündüz teması, depolama, eski uygulamalara bağlantı.
- **Projeler:**
  - Dokununca açılır ve kaldığı yerden devam eder.
  - ✕ ile silinir (iki dokunuş). Video taslakları Kareden Videoya'nın kendi silme işleviyle silinir, artık dosyalar da temizlenir.
- **Geri tuşu:** önce uygulamanın içinde bir adım geri gider (editör → kitaplık / taslaklar), sonra Stüdyo ana sayfasına döner.
- **Tema:** Stüdyo'da seçilen gece/gündüz, fotoğraf ve video ekranlarına da uygulanır.

## Nasıl çalışır (özellik kaybı olmaması için)
- `foto/` = Karanlık Oda'nın, `video/` = Kareden Videoya'nın **değiştirilmemiş** kopyası. Hiçbir araç, algoritma ya da kayıt biçimi yeniden yazılmadı. Kopyalara yalnız şu küçük eklemeler yapılır (`kaynak/esitle.py`):
  1. `window.STUDYO=1` bayrağı.
  2. Uygulamanın kendi önbellek (service worker) kaydı Stüdyo içinde atlanır; önbelleği Stüdyo yönetir.
  3. Karanlık Oda'nın GitHub → Cloudflare yönlendirmesi Stüdyo içinde atlanır.
  4. Kareden Videoya'da proje açma/silme işlevlerine dışarıdan erişim (`window.__kv`).
  5. Sayfa sonunda **Stüdyo köprüsü** (`kaynak/kopru.js`): tema, seçilen dosyaları uygulamanın kendi dosya seçicisine verme, proje açma, geri tuşu, "‹ Stüdyo" düğmesi.
- Ana sayfada seçilen dosyalar uygulamanın kendi seçicisine verilir. Böylece içe aktarma da her zamanki yoldan geçer (birden çok fotoğraf → toplu düzenleme).
- Aynı anda yalnız bir modül açık tutulur, ana sayfaya dönünce bellekten çıkar.

## Veriler
- Stüdyo, eski uygulamalarla **aynı adreste** durur: GitHub'da `aaskinkk-cloud.github.io`, Cloudflare'de `ahmet-uygulamalar.pages.dev`.
- Bu yüzden fotoğraf kitaplığı (`karanlik-oda` veritabanı) ve video taslakları (`kareden-videoya` veritabanı) eski uygulamalarla **ortaktır**. Hiçbir şey taşınmaz ya da kopyalanmaz; birinde yaptığın değişiklik diğerinde de görünür.
- Stüdyo ana sayfası bu veritabanlarını yalnız okur. Veritabanı yoksa boş bir tane oluşturmaz.
- Android uygulamaları (APK) kendi ayrı verisini tutar.

## Eski uygulamalar
Karanlık Oda (`/karanlik-oda/`) ve Kareden Videoya (`/kareden-videoya/`) olduğu gibi, ayrı uygulama olarak çalışmaya devam eder. Stüdyo onların dosyalarına yazmaz.

## Güncelleme (eski uygulamalarda yapılan değişiklikleri Stüdyo'ya almak)
```
python3 kaynak/esitle.py <karanlik-oda klasörü> <kareden-videoya klasörü> . <sürüm> <tarih>
```
Betik, beklenen yerler değişmişse hata verip durur; yarım ya da bozuk kopya üretmez.

## Geliştirme kuralı
Eski iki uygulama dondurulmuştur (Karanlık Oda 6.67, Kareden Videoya 9.15). Yeni geliştirmeler yalnız Stüdyo'da yapılır. Kayıt biçimine yalnız ekleme yapılır, var olan alanlar değiştirilmez; böylece eski uygulamalar aynı projeleri açmaya devam eder.

## Henüz yok (sonraki aşamalar)
- Stüdyo için Android uygulaması (APK).
- Kolaj (en son).
