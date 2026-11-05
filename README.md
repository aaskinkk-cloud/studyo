# Stüdyo

Fotoğraf ve video düzenleyici. **Karanlık Oda** ile **Kareden Videoya**'yı tek uygulamada birleştirir. Tasarımda InShot'un sade düzeni örnek alındı. Tarayıcıda çalışır (PWA). Dosyalar telefondan dışarı gönderilmez.

**Güncel sürüm:** 1.6 (APK 2.0) · 06.11.2026. İçinde Karanlık Oda 6.67 ve Kareden Videoya 9.15 var.

## 1.6 (06.11.2026): video editöründe yapay zekâ ile "Nesne" katmanı
- Video editörünün alt menüsünde yeni araç: **Nesne** (Metin'in yanında).
- **Fotoğraftan nesne kes ve ekle:**
  - Telefondan bir fotoğraf seçilir.
  - Yapay zekâ (ISNet) kişiyi ya da nesneyi arka planından ayırır, boş kenarları kırpar.
  - Sonuç videonun üstüne katman olarak konur. Örnek: dikey bir fotoğraftaki kişi yatay videoya.
- **Katman ayarları:**
  - Önizlemede sürükleyerek taşıma; ortaya gelince yapışır.
  - İki parmakla büyütme, küçültme ve döndürme.
  - Kaydırıcılar: Boyut, Açı, Görünürlük.
  - Gölge, Ayna (yatay çevirme), Ortala, Gizle (silmeden kapatma).
  - Giriş hareketi: Sabit, Belirerek, Kayarak (yakın kenardan), Yaklaşarak, Süzülerek. Hareketli girişlerde nesne bitişte yumuşakça kaybolur.
  - Zaman: Başlangıç ve Bitiş için "şu an" (zaman çizelgesindeki yer), Baştan, Kliplerin sonuna. Varsayılan: videonun başından kliplerin sonuna. Kapanış ekranının üstüne binmez.
  - Birden çok nesne eklenebilir.
  - PNG kaydet: kesilmiş nesne saydam arka planlı olarak indirilir.
  - Sil: iki dokunuşla.
- Nesne, klip ve geçişlerin üstünde, yazı, altyazı ve logonun altında çizilir. Önizleme ile dışa aktarılan video aynıdır.
- **Motor:** `yz/motor.js` (deneme sayfasıyla aynı model ve aynı önbellek; deneme sayfasında model indirildiyse yeniden inmez).
  - Önce grafik birimi, olmazsa işlemci.
  - Model yalnız ekleme sırasında yüklenir, sonra bellekten çıkar. Önizleme ve dışa aktarma modele ihtiyaç duymaz.
- **Kayıt:**
  - Katmanlar ve kesilmiş görüntüler ayrı bir veritabanında (`studyo-nesne`), proje kimliğine bağlı.
  - Kareden Videoya'nın kayıt biçimi değişmedi. Bu yüzden projeyi eski Kareden Videoya uygulamasında açarsan nesneler orada görünmez.
- **Cloudflare adresinde:** motor ve model GitHub adresinden yüklenir. Motor dosyası Cloudflare'in 25 MB sınırını aşıyor.
- **Kod:**
  - `kaynak/video-nesne.js`.
  - Kareden Videoya kopyasına 2 küçük kanca (`esitle.py`): çizim döngüsünde nesne çağrısı; nesne görünürken kare önbelleği kullanılmaz.
  - `window.__kv`'ye proje kimliği, oynatma konumu, kaydetme ve yenileme erişimi eklendi.
- **Test (sunucuda, grafik birimi yok):**
  - Ekleme, işlemciyle 15–22 sn sürdü; model indirme dahil.
  - Sürükleme, kayıt, projeyi kapatıp yeniden açınca geri gelme, başka araç açılınca panelin kapanması denendi.
  - 1920×1080 MP4 dışa aktarıldı. Karelerde nesne gölgesiyle görünüyor, ilk saniyede beliriyor, kapanış ekranında yok.
- **Henüz yok (sonraki adımlar):**
  - Videodaki hareketli nesneyi takip etme.
  - Nesne kenarından sanatsal geçişler.
  - Fotoğraf editöründe kesme (fırçayla ekle/çıkar, saydam PNG).
  - Nesne katmanlarının zaman çizelgesinde ayrı satırı.

## Yapay zekâ deneme sayfası (05.11.2026): `yz/`
**Adres:** https://aaskinkk-cloud.github.io/studyo/yz/
- Amaç: nesne kesme modelini telefonda gerçekten denemek (hız, kenar kalitesi), sonra editörlere eklemek. Ana sayfada bağlantısı yok; editörlere ve kitaplığa dokunmaz.
- Model seçimi (ölçüldü):
  - **ISNet (DIS, Apache-2.0):** asıl model. 1024×1024 girdi. Ağırlıklar fp16 olarak 88 MB (5 parça), tarayıcıda fp32'ye açılır; sonuç fp32 modelle aynı (ortalama alfa farkı 0,001).
  - **U²-Net küçük (Apache-2.0):** 5 MB, hızlı ama kenarları zayıf; karşılaştırma için.
  - BiRefNet (MIT) kenarda daha iyi ama elenmiş: en küçük sürümü bile 1024 girdide 6 GB'tan fazla bellek istedi (sunucuda bellek yetmedi). Telefonda çalışması gerçekçi değil.
  - RVM (GPL) ve RMBG-1.4 (ticari olmayan lisans) lisans yüzünden kullanılmadı.
- Motor: ONNX Runtime Web 1.30.0. Önce telefonun grafik birimi (WebGPU), olmazsa işlemci (WASM).
- Model yalnız "Fotoğraf seç"e basınca yüklenir, iş bitince bellekten çıkarılır. İndirilen model `yz-model-1` önbelleğinde saklanır; Stüdyo güncellemeleri onu silmez. Sayfadaki düğmeyle silinebilir.
- Önbellek (`sw.js`): `/yz/` altındaki dosyalar Stüdyo önbelleğine kopyalanmaz.
- Sunucu testi (grafik birimi yok, 2 çekirdek): işlemciyle kesme 12–13 sn. Tarayıcı sonucu Python'daki referansla aynı (ortalama alfa farkı 0,5 / 255). WebGPU yolu doğru çalışıyor; hızı ancak telefonda ölçülebilir.
- Cloudflare'e konmadı: motor dosyası 26,8 MB, Cloudflare'in dosya başına 25 MB sınırını aşıyor.

## 1.5 (05.11.2026): "Sarı Sonbahar" filtresi
- Fotoğraf editörü › Filtreler › **Lightroom tarzı** şeridinin sonunda yeni filtre: **Sarı Sonbahar**. Yeşil yaprakları altın sarısına, yerdeki yaprakları kızıl-kahveye çevirir; gölgeler hafif açık ve sıcak.
- Nasıl yapıldı: aynı fotoğrafın yeşil aslı ile senin altın düzenlemen piksel piksel eşlendi, aradaki renk dönüşümü 33×33×33 bir **3B LUT** olarak çıkarıldı. Karanlık Oda'nın HSL kaydırıcıları yeşili altına çeviremediği için (ton kaydırma −100'de bile yetmiyor) LUT kullanıldı.
- Yoğunluk kaydırıcısı ve "Nerede uygulansın" maskesi diğer filtrelerdeki gibi çalışır; filtre hazır ayara da kaydedilebilir.
- Filtrenin LUT verisi sayfanın içinde gömülü (`kaynak/foto-lut.js`); kitaplığa ya da veritabanına yazılmaz.
- Sınırlar:
  - Dönüşüm tek bir fotoğraftan öğrenildi. Başka orman ve yeşil sahnelerde benzer sonuç verir; o fotoğrafta bulunmayan renklerde (mavi gökyüzü, ten) etki daha tahminîdir.
  - Bu filtreyle düzenlenen fotoğrafı eski Karanlık Oda uygulamasında açarsan filtre orada görünmez; öbür ayarlar korunur.
- Test: önizleme, filtre küçük resmi ve tam çözünürlüklü dışa aktarma ayrı ayrı denendi. Dışa aktarılan görüntü ile LUT'un hesaplandığı referans arasındaki ortalama fark 5,2 (0–255 ölçeğinde).

## 1.4 (01.11.2026): ana sayfada Karanlık Oda kartı
- Kareden Videoya kartının üstünde mavi **Karanlık Oda** kartı: dokununca galeri açılmaz, doğrudan Karanlık Oda'nın kendi ana sayfası (kitaplık, hazır ayarlar, toplu düzenleme, haber, blog, logolar) açılır.
- "Fotoğraf araçları"ndaki "Kitaplık · hazır ayarlar" aynı işi yaptığı için kaldırıldı (6 araç, 3×2).
- Fotoğraf / Video kartları galeriyi açıp doğrudan editöre götürür; Karanlık Oda / Kareden Videoya kartları uygulamanın kendi ana sayfasını açar.

## 1.3 (01.11.2026): bölümler üstte simgeli sekmeler, altta yalnız seçilen bölüm (iki editörde de)
Amaç: panellerde aşağı doğru kaydırma olmasın, ekran sakin kalsın. Yalnız görünüm değişti; hiçbir araç, ayar, algoritma ya da kayıt değişmedi. Öğeler taşınmaz ve silinmez, yalnız gizlenip gösterilir.
- **Fotoğraf editörü:**
  - Her panelin bölümleri panel başlığında (✓'nin yanında) simgeli sekmeler oldu, örneğin Selfi: Kadraj · Kişi ve arka plan · Arka plan · Güzellik · Saç. Altta yalnız seçilen bölüm görünür.
  - "Menü bölümleri: Tümü açık / Tümü kapalı / Akordeon" seçimi artık gerekmediği için Stüdyo'da gizli.
- **Video editörü:**
  - 1. sıradaki simgeli bölümler zaten vardı.
  - 2. sıradaki ara başlıklara (Genel · Ek yazı 1 · Ek yazı 2, Çözünürlük · Kare hızı · Kalite …) simge eklendi.
- **İkisinde ortak:**
  - **3 ya da daha fazla kaydırıcı** olan yerlerde InShot "Ayarlama" düzeni: simgeli adlar sırası, altta tek kaydırıcı. Örnekler: Işık (Pozlama · Parlaklık · Kontrast …), Detay › İnce ayar, Selfi › Güzellik, Müzik › Şarkılar. Değiştirilmiş kaydırıcının adında turuncu nokta görünür.
  - **Ekrana sığmayan bölüm otomatik sayfalara bölünür.** Sayfalar üstte simgeli bir sırada, içindeki ilk ayarın adıyla. Örnek: Şablon › Başlık › Genel, yaklaşık 5 ekran boyundan 6 sayfaya indi. Sayfa sınırı panelin gerçek yüksekliğine göre hesaplanır; paneli büyütürsen sayfa sayısı azalır.
  - Açıklama notu, ait olduğu ayarla aynı sayfada kalır.
  - **Uzun ızgaralar yatay şerit oldu:** filtre küçük resimleri, yazı/imza şablonları, stil ve kapak önerileri, oran ve hazır seçenek grupları.
  - Seçtiğin bölüm, sayfa ve kaydırıcı hatırlanır.
- **Kolaj iptal edildi:** video modülünün ana ekranındaki "Kolaj · Yakında" kartı gösterilmiyor.
- Kod: `kaynak/sayfala.js` (ortak), `kaynak/foto-arayuz.js`, `kaynak/video-arayuz.js`.
- Test: iki editörün bütün panel, bölüm ve sayfaları tek tek açılıp ölçüldü (fotoğraf 87, video 87 durum). Kalan küçük kaydırmalar:
  - Eğri tuvali (14 px).
  - Klip › Sıra (klip şeridi ve katman listesi; uygulamanın kendi zaman çizelgesi, sayfalanmadı).
  - Birkaç yerde 2–11 px.

## Android uygulaması (APK) — 2.0 (01.11.2026): Chrome motoruyla (Trusted Web Activity)
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/android/studyo.apk

- **Neden değişti:** 1.x, sayfayı Android'in yerleşik tarayıcısıyla (WebView) gösteriyordu. Fotoğraf editöründe "Telefonun grafik birimi önizlemeyi durdurdu" hatası çıkıyordu (Karanlık Oda APK'sındaki sorunun aynısı). 2.0 sayfayı **telefondaki Chrome'un kendisiyle** açıyor; fotoğraf motoru Chrome'daki gibi çalışır.
- **Veriler Chrome'daki Stüdyo ile ortak:** aynı kitaplık, aynı taslaklar. Yedekle taşımaya gerek yok.
- Kalıcı tam ekran (sticky-immersive): tuşlar kaydırınca görünür, sonra gizlenir.
- Kaydetme ve dosya seçme Chrome'un kendisiyle yapılır; dosyalar İndirilenler'e iner.
- GitHub'daki her güncelleme kendiliğinden gelir; APK'yı yeniden kurmak gerekmez.
- **Doğrulama:** `https://aaskinkk-cloud.github.io/.well-known/assetlinks.json` (`aaskinkk-cloud.github.io` deposu). Bu dosya imza anahtarının parmak izini içerir; doğrulama yapılamazsa sayfa yine açılır ama üstte ince bir adres çubuğu görünür.
- **Gereken:** telefonda Chrome yüklü olmalı. Cloudflare yedeğine kendiliğinden geçiş bu sürümde yok; GitHub açılmazsa Cloudflare adresi Chrome'dan açılır.
- 1.x'in üstüne kurulur (aynı imza). 1.x'in kendi (WebView) içinde yaptığın projeler 2.0'da görünmez; varsa önce 1.x'te dışa aktar.
- Kaynak `android/` (android-browser-helper 2.5.0); APK'yı GitHub Actions derler.

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
  6. Fotoğraf modülüne yerleşik LUT filtreleri (`kaynak/foto-lut.js`, 1.5: Sarı Sonbahar).
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

## Durum
Kolaj iptal edildi. Yapay zekâ: 1. adım (fotoğraftan kesilen nesneyi videoya yerleştirme) 1.6'da. Sırada: fotoğraf editöründe nesne kesme (fırça, saydam PNG), sonra videoda takip ve geçişler.
