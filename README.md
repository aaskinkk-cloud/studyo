# Stüdyo

Fotoğraf ve video düzenleyici. **Karanlık Oda** ile **Kareden Videoya**'yı tek uygulamada birleştirir. Tasarımda InShot'un sade düzeni örnek alındı. Tarayıcıda çalışır (PWA). Dosyalar telefondan dışarı gönderilmez.

**Güncel sürüm:** Android uygulaması 3.1 (yerel video editörü) · 13.11.2026. Web: 2.16 · 08.11.2026 (Karanlık Oda 6.67, Kareden Videoya 9.15).

**Android APK:** https://github.com/aaskinkk-cloud/studyo/releases/download/android/studyo.apk

## 3.19 (14.11.2026, deneme · `yerel-android` dalı): geçişte "göz kırpma" — A/B düzeni
**Test:** derlendi; cihaz testi bekleniyor.
- **Belirti (Ahmet):** geçişte göz kırpar gibi; **kaydedilen videoda da**. Demek ki sorun önizlemede değil, kompozisyonun kendisinde.
- **Kök neden [Muhtemel]:** üst üste binen geçişte giden klip ikiye bölünüyordu: gövdesi alt dizide, son T ms'si ("kuyruk") üst dizide ayrı çözücüyle. Pencerenin ilk karesinde alt dizi gelen klibe geçerken kuyruk tam görünür olmak zorundaydı; bir karelik gecikme = gelen klip bir an görünür.
- **Çözüm:** klipler bölünmez. Her üst üste binen geçişte sıradaki klip öbür diziye konur (A/B). Üstteki klip görünmezken başlar (solarak/eriyerek girer) ya da görünmez olarak biter; dizi değişimi hiçbir zaman görünür bir karede olmaz. `UstBirlestirici`, `UstBolum`, `EriyenMaske(giriş, çıkış)`. Geçişsiz projelerde tek dizi yolu aynen.
- Akıcı önizleme aynı kompozisyonu kullandığı için önizleme de aynı şekilde düzelir. (Duraklatılmış düzenleme önizlemesi eski yöntemle çizer.)

## 3.18 (14.11.2026, deneme · `yerel-android` dalı): dışa aktarma hatası düzeltildi
- **Hata (Ahmet, 02:19):** "Görüntü işlenemedi · ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED · IllegalArgumentException".
- **Kök neden [Kesin]:** 3.12'deki canlı "Abone ol" kartı soldan kayarak girerken bindirme çapası −1'in altına iniyordu; Media3 `StaticOverlaySettings` çapanın −1…1 aralığında olmasını şart koşar (`checkArgument`) → kartın göründüğü ilk karede dışa aktarma duruyordu. Önizleme Compose ile çizildiği için etkilenmiyordu.
- **Çözüm:** kart taşarken karenin sol kenarı sabit çapa olur, kayma kartın kendi çapasıyla yapılır (görünüş aynı); dokunma noktası çapası ve saydamlık da aralığa sabitlendi. Diğer bindirmelerin çapaları gözden geçirildi (hepsi aralıkta).

## 3.17 (14.11.2026, deneme · `yerel-android` dalı): akıcı önizleme kalıcı ve kesintisiz
Ahmet: "bir kere hatasız döner gibi oluyor, sonra tekrar aynı hata". Kök nedenler [Muhtemel] ve çözüm:
- **Her ▶'da sıfırdan kurulum:** her oynatmada kompozisyon + çözücüler baştan kuruluyordu; büyük HDR projelerde hazırlık uzun sürüyor, bekçi bunu takılma sayıp oturum boyunca titreyen eski önizlemeye geçiriyordu. Artık akıcı oynatıcı **duraklatınca kapatılmaz**; bir sonraki ▶ anında aynı yerden başlar. Yalnız proje değişince (her düzenlemede) yeniden kurulur.
- **Bekçi:** hazır olmayı en çok 12 sn bekler, sonra ilerleme arar. Tek bir başarısızlık oturumu kapatmaz; ancak **üst üste iki** başarısızlıkta standart önizlemeye geçilir. Her durum hata kaydına yazılır (kuruluş süresi, hazırlık süresi, neden).
- **Siyah ekran yok:** akıcı yüzey en altta açılır; ilk kare çizilene kadar düzenleme önizlemesi üstte kalır, ilk kare gelince geçilir.
- Hazırlanırken ▶ düğmesi duraklat simgesine döner (iki kez basıp iptal etmemek için).

## 3.16 (14.11.2026, deneme · `yerel-android` dalı): hata kaydı paylaşımı + bekçi hazırlığı bekler
- Hata kaydı paylaşımı: alan uygulama metni ~20 KB'de kesiyordu, en yeni satırlar (akıcı önizleme kayıtları) gitmiyordu → yalnız son 16 KB paylaşılır.
- Akıcı önizleme bekçisi önce hazırlanmayı bekler (en çok 10 sn), hazır olduktan sonra 1,5 sn ilerleme yoksa standart önizlemeye geçer. Kayda "Akıcı önizleme hazır: N ms" yazılır. (3.15'te ilk açılışta büyük HDR klipler 2 sn'yi geçince yanlışlıkla "takıldı" sayılıp eski, titreyen önizlemeye dönülüyor olabilirdi [Muhtemel].)

## 3.15 (14.11.2026, deneme · `yerel-android` dalı): web Kareden Videoya yöntemi + klipler arası geçiş simgesi
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — **Test:** derlendi; cihaz testi bekleniyor.

**Referans (web Kareden Videoya, `render(t)`):** her karede `frameState(t)` → geçiş penceresindeyse giden klip (a) ve gelen klip (b) aynı anda, **tek tuvale** çizilir; erimede b, gürültü maskesiyle (destination-in) a'nın üstüne konur; ease = easeInOutQuad. İki klip ayrı oynatıcı/katman değil, tek döngü + tek saat. Stüdyo'nun akıcı önizlemesi (3.13) bu yöntemin yerel karşılığıdır: dışa aktarmadaki kompozisyon tek boru hattında.

- **İlk karede kalma (3.13) kökü giderildi:** üst dizideki boşluklar ("gap") kare üretmiyordu; birleştirici bütün girişlerden kare beklediği için akış duruyordu. Önizlemede boşluklar saydam bir resimle doldurulur (birleştirici pencere dışında zaten görünmez yapar). Dışa aktarma değişmedi. 3.14'teki takılma bekçisi güvenlik olarak duruyor.
- **Klipler arası geçiş simgesi:** klip şeridinde iki klibin arasında yuvarlak simge (turuncu = geçiş var, beyaz çerçeve = bu araya özel). Dokun → Erime · Yumuşak · Kaydırarak · Büyüyerek · Siyahtan · Yakınlaşma · Yok; **Bu araya** ya da **Tümüne uygula**. Beyaz parlama bu listede yok.
- Her ara kendi türünü kullanır (önizleme, akıcı önizleme ve dışa aktarma): `Klip.gecis`, `Proje.gecisAt(i)`, `Gecisler.hesapla(giriş, çıkış)`, `GecisBirlestirici(turlar)`.
- Proje geçişi değişince önizleme listesi yeniden kurulur (süreler değiştiği için).

## 3.14 (14.11.2026, deneme · `yerel-android` dalı): akıcı önizleme ilk karede kalıyordu → takılma bekçisi
- Ahmet'in cihazında (3.13) ▶ sonrası görüntü ilk karede kaldı; hata da vermedi. Olası neden [Muhtemel]: çok girişli birleştirici tüm dizilerden kare bekliyor, boşluklu (gap) dizi kare üretmeyince bekleyip kalıyor (Media3 1.8 sınırı).
- Çözüm: ▶ sonrası 1,8 sn içinde ilerleme yoksa o oturumda **standart önizlemeye** geçilir ve oynatma oradan kesintisiz sürer; hata kaydına durum yazılır. Oynatma artık hiçbir durumda ilk karede kalmaz.

## 3.13 (14.11.2026, deneme · `yerel-android` dalı): akıcı önizleme — geçişteki sıçramanın asıl kökü
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — 3.12'nin üstüne kurulur. **Test:** derlendi; cihaz testi bekleniyor.

- **Asıl kök neden:** oynatırken önizleme; ana klip oynatıcısı + giden klibin kuyruğu için **ikinci oynatıcı** + üstte Compose çizimleri (yazı, katman, kart, erime maskesi) ile **ayrı ayrı** kuruluyordu. Bunlar ayrı saatlerle çalışır, kare kare eşlenemez → geçişte sıçrama/takılma. 3.5–3.12'deki eşleme düzeltmeleri bunu kökten çözemezdi. Web Kareden Videoya her kareyi **tek tuvalde** çizdiği için akıcıdır.
- **Çözüm:** ▶ basınca önizleme, dışa aktarmadaki **kompozisyonun aynısını tek boru hattında** oynatır (Media3 CompositionPlayer, 720p): klipler, erime/üst üste geçişler, hareket, yazılar, katmanlar, "Abone ol" kartı, müzik, seslendirme — hepsi tek saatle. Önizlemede gördüğün = videoda çıkacak olan.
- Duraklatınca (ya da sarınca, bir şeyi değiştirince) düzenleme önizlemesine dönülür (dokunarak taşıma, seçme vb. orada); çizgi kaldığı yerde durur.
- Bu yol hata verirse o oturumda kendiliğinden standart önizlemeye geçilir ve "Akıcı önizleme açılamadı" yazılır (hata kaydına ayrıntı düşer); aynı yol tekrar denenmez.
- Zaman damgaları: dışa aktarmada eski (cihazda doğrulanmış) yol aynen korundu; önizlemede kompozisyon zamanı kullanılır (`Saat`, `ZamanKipi`, iş parçacığına özel).
- Kod: `EditorDurum.kt` (`kompBaslat`, `kompBitir`), `Disa.kt` (`kompozisyon(onizleme)`, öğe süreleri), `Cizim.kt` (`Saat`, `ZamanKipi`), `Erime.kt`, `Paneller.kt` (SurfaceView).

## 3.12 (14.11.2026, deneme · `yerel-android` dalı): web'deki "Erime" geçişi + canlı "Abone ol" kartı (Ahmet'in örnek videosuna göre)
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — 3.11'in üstüne kurulur. **Test:** derlendi; cihaz testi bekleniyor.

**Geçiş — kök nedenler ve çözüm**
1. *Önizlemede video → fotoğraf geçişi kesme gibiydi:* gelen fotoğraf (opak) giden videonun kuyruğunun **üstüne** çiziliyordu; kuyruk hiç görünmüyordu. Çizim sırası düzeltildi: gelen klip altta, giden üstte.
2. *Önizlemede geçişte sıçrama/takılma:* ikinci oynatıcı (giden klibin kuyruğu) her 150 ms sapmada **atlıyordu (seek)**; atlama çözücüyü durdurup görüntüyü sıçratıyordu. Artık oynarken atlamaz; küçük kaymalar hızla (0,9×–1,12×) düzeltilir, yalnız 0,7 sn'yi aşan kaymada atlanır.
3. *Web'deki yumuşak geçiş yerel uygulamada yoktu:* web Kareden Videoya'nın **melt** geçişi birebir taşındı → Görünüm › Geçiş › **Erime** (varsayılan ayarlarda da artık Erime). 192×108 değer gürültüsü (4 kat, yukarıdan aşağı akış eğilimi), eşik = ease(k)·(1+0,14), yumuşak kenar 0,14 — web'deki sayılar. Giden klip lekeler hâlinde eriyerek kaybolur, iki klip geçiş boyunca oynamaya devam eder.
   - Dışa aktarma: giden klibin kuyruğuna GL gölgelendirici (`EriyenMaske`, aynı gürültü dokusu) → çıkış alfası; Media3 birleştiricisi kaynak alfasına göre karıştırır.
   - Önizleme: aynı gürültüden maske bitmapi, Compose'ta DstIn ile.

**"Abone ol" kartı (kart stili) — örnekteki gibi canlı**
- Sol ortadan **kayarak açılır** (hafif esneyerek), ~1,3 sn'de beyaz **dokunma noktası** düğmenin üstünde belirir, basar (küçülür), düğme kırmızı **ABONE OL**'dan koyu **🔔 Abone olundu**'ya döner, nokta kaybolur; sonunda kart solar.
- Önizleme ve dışa aktarma aynı hesap (`HatAnim`). Klasik bant stili değişmedi.

Kod: `Erime.kt` (gürültü, maske, gölgelendirici), `Cizim.kt` (`hatirlaticiKartSet`, `HatAnim`, `HatirlaticiBindirme`), `Paneller.kt` (çizim sırası, `erimeMaskesi`, canlı kart), `EditorDurum.kt` (`kuyrukEsle` hız eşlemesi), `Disa.kt`.

## 3.11 (14.11.2026, deneme · `yerel-android` dalı): katmanlar yalnız Katman bölümünde, tek yerde
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — 3.10'un üstüne kurulur. **Test:** derlendi; cihaz testi bekleniyor.

- Çok satırlı çizelge **yalnız Katman bölümünde**; diğer bölümlerde önceki klip şeridi (3.9 düzeni).
- Katman bölümünün **başlığı** ("Katmanlar · N"): dokun = çizelgeyi aç/kapat. Çizelgenin altındaki turuncu **tutamaç**: sürükle = büyüt/küçült (120–560 dp, hatırlanır), çift dokun = varsayılan.
- Katmanla ilgili her şey bu sayfada: çizelge (seç, sar, yakınlaştır, başı/sonu, kaydır, satır değiştir) + **tek işlem sırası** (Böl · Çoğalt · Öne al · Arkaya al · Gizle/Göster · Kilitle/Kilidi aç · Sil – iki adım) + altında Katman ekle ve seçili katmanın ayarları.
- **Tekrarlar kaldırıldı:** ayarlar kartındaki işlem sırası, çizelgedeki "＋ Katman" ve klip düğmeleri, 3.9'daki satır listesi. Çizelgedeki Video satırı yalnız başvuru (kareler); klip işleri Düzenle › Klip'te — klibi bölme oraya taşındı ("Böl").
- Ana sayfa ⋯'daki "Zaman çizelgesi" seçeneği kalktı (diğer bölümler zaten önceki şeritte).

## 3.10 (14.11.2026, deneme · `yerel-android` dalı): çok satırlı zaman çizelgesi (InShot tarzı, Ahmet'in görseline göre)
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — 3.9'un üstüne kurulur. **Test:** derlendi; cihaz testi bekleniyor.

Klip şeridinin yerinde **hep açık** çok satırlı zaman çizelgesi (panel açıkken üstte, altında ayar alanı):
- **Satırlar:** 1. satır Video (kliplerin kareleri yan yana, numaralı, sonda ＋ Ekle) · altında her katman ayrı satır (üstteki satır = en üstteki katman; metin mor, görsel turkuaz + küçük resim, çıkartma turuncu, ses yeşil + ses dalgası) · Müzik (dalga; döngüde başa sarar) · Ses kaydı (seslendirmeler, dalga).
- **Oynatma çizgisi ortada sabit**, çizelge altından kayar; üstte saniye cetveli, çizginin yanında anlık zaman.
- **Hareket:** tek parmak yatay = sar (oynuyorsa durur) · iki parmak = yakınlaştır/uzaklaştır (4–400 dp/sn, hatırlanır) · dikey = satırlar arasında kaydır.
- **Seç:** çubuğa dokun. Katmana çift dokun = ayarları aç. Video satırının adına dokun = Klip ayarı; Müzik/Ses kaydı satırına dokun = ilgili panel.
- **Seçili video klibi:** beyaz tutamaçlar; sol ucu sürükle = baştan kırp, sağ ucu = sondan kırp (fotoğrafta süre).
- **Seçili katman:** uçları sürükle = başı/sonu (ses katmanında baştan kırpınca sesin başladığı yer de kayar); gövdeyi sürükle = zamanda kaydır, **yukarı/aşağı sürükle = satır (katman sırası) değiştir**.
- **Temel gereçler** (çizelgenin üstünde, seçime göre tek sıra): katman seçiliyken Böl · Çoğalt · Gizle · Kilitle · Sil · Ayarlar; klip seçiliyken Böl · Çoğalt · Sessiz · Katman ekle · Klip ayarı. **Böl** oynatma çizgisinin olduğu yerden ikiye ayırır.
- Önceki görünüm silinmedi: Ana sayfa ⋯ › "Zaman çizelgesi: klip şeridi (önceki)".
- Kod: `Cizelge.kt` (çizelge), `Dalga.kt` (ses dalgası: çözülür, 50 ms'lik tepe değerleri, `cache/dalga`), `EditorDurum.kt` (`klipBol`, `katmanBol`), `Tema.kt` (`Duzen.eskiSerit`, `Duzen.cizelgePps`).

## 3.9 (14.11.2026, deneme · `yerel-android` dalı): çok katmanlı düzenleme (1. aşama)
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — 3.8'in üstüne kurulur. **Test:** derlendi; cihaz testi bekleniyor.

Yeni ana bölüm **Katman** (alt çubukta). Klip dizisinin üstünde serbest, sınırsız sayıda katman:
- **Ekle:** Metin · Görsel (telefondan, çoklu; PNG/WebP saydamlığı korunur) · Logo (kütüphaneden) · Çıkartma (40 hazır) · Ses (müzik, efekt, seslendirme dosyası). Yeni katman oynatıcının olduğu andan başlar.
- **Önizlemede:** katmana dokun = seç (çerçeve) · sürükle = taşı · iki parmak = büyüt/küçült + döndür (bırakınca kaydedilir). Kilitli katman hareket etmez.
- **Zaman çizelgesi:** her katman bir satır (üstteki satır = en üstteki katman). ≡ tutup sürükle = sıra · çubuğun ucunu sürükle = başı/sonu · ortasını sürükle = kaydır · dokun = oraya git. Turuncu çizgi = oynatıcının yeri.
- **Ayarlar (seçili katman):** Başı/Sonu = şimdi, Başına git, Tüm video · Boyut, Döndür (±180°), Opaklık, Ortala, Düz, Boyut sıfırla · Giriş/çıkış: Yok, Solma, Kayma, Büyüme, Zıplama · Filtre (görsel/çıkartma): S/B, Sepya, Canlı, Soğuk, Sıcak · Kırp (görsel; 4 kenar) · Metin: yazı, 8 renk, arka kutu · Ses: seviye %0–150, hız 0,5–2×, sesin başladığı yer · Öne al, Arkaya al, Çoğalt, Gizle/Göster, Kilitle, Sil (iki adım).
- **Dışa aktarma:** görsel katmanlar her klibe bindirme olarak (önizlemeyle aynı bitmap, konum, açı, animasyon), ses katmanları ayrı ses dizisi (hız: SpeedChangingAudioProcessor). Altyazı katmanların üstünde kalır.
- **Henüz yok (2. aşama):** video katmanı (resim içinde resim) ve video katmanında hız — iki videoyu aynı anda çözmek telefonun çözücü sınırına bağlı; ayrı sürümde.
- **Test edilmesi gerekenler:** döndürülmüş katmanın dışa aktarmada aynı yöne döndüğü; hızı 1× olmayan ses katmanının dışa aktarması.

Kod: `Katmanlar.kt` (çizim, animasyon, filtre, dışa aktarma bindirmesi), `KatmanPanel.kt` (panel + zaman çizelgesi), `Paneller.kt` (önizleme çizimi ve dokunma), `EditorDurum.kt` (katman işlemleri, ses önizleme oynatıcıları), `Disa.kt`, `Model.kt` (`KatmanOge`, `Proje.katmanlar`), `Depo.kt` (`katman` klasörü temizliği).

## 3.8 (13.11.2026, deneme · `yerel-android` dalı): akıcı geçişler, taslak toplu seçim + arşiv, logo kütüphanesi
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — 3.7'nin üstüne kurulur, projeler korunur. **Test:** derlendi; cihaz testi bekleniyor.

1. **Geçişlerde titreme/takılma (kök nedenler):**
   - *Önizleme:* zaman 40 ms'lik döngüyle (~25 kez/sn) güncelleniyordu; kararma/parlama/yakınlaşma ve üst üste binen geçişin solması basamak basamak ilerliyordu. Artık zaman **her ekran karesinde** oynatıcıdan okunur; klip sınırındaki birkaç ms'lik geri değer yok sayılır (sıçrama yok). Giriş, hatırlatıcı, altyazı, nesne ve takip yazısı da aynı zamanı kullanır.
   - *Dışa aktarma:* fotoğraf, kapanış kartı ve geçiş kuyruğu sabit 30 fps üretiliyordu; 60 fps videolarla karışınca klip sınırında kare temposu değişiyordu. Artık çıkış kare hızı videoların **en sık kare hızı** (24–60); hepsi bu hızda (hata kaydında "Kare hızı: N fps").
   - *Eğri:* tek dizi geçişleri (siyah/beyaz/yakınlaşma, siyahtan açılış) doğrusal yerine yumuşak eğriyle. Geçiş türleri ve görünüm aynı.
   - *Sınır:* aynı projede 30 ve 60 fps videolar varsa her video kendi hızında kalır (kare atma/çoğaltma yapılmaz).
2. **Taslaklar (Son çalışmalarım):** "Seç" ya da karta uzun bas → Tümünü seç · Seçimi kaldır · Arşivle · Sil · Bitti. Silme onaylı ("geri alınamaz"); dışa aktarılan proje silinmez. Arşivlenenler ana listede görünmez, başlıktaki "Arşiv · N" ile açılır (orada Arşivden çıkar / Sil). Kart ⋯ menüsünde Arşivle ve Seç. Geri tuşu seçimi kapatır.
3. **Logo kütüphanesi** (Yayın › Logo): çoklu yükleme, damalı zeminde önizleme (şeffaf PNG), dokun = projeye ekle, uzun bas = adını değiştir / sil (onaylı). İlk açılışta önceki projelerin logoları kütüphaneye alınır. Projedeki logo: 4 köşe ya da **önizlemede parmakla sürükle** (serbest konum, dışa aktarmada aynı yer), boyut %6–40, opaklık %10–100, yuvarlak kırpma, projeden kaldır. Kütüphaneden silinen logo projelerde bozulmaz (proje kendi kopyasını tutar). Dosyalar: `filesDir/logolar/` (+ `liste.json`), proje kopyası `filesDir/logo/kutup_<id>.png`.
4. **Çok katmanlı düzenleme:** sonraki sürüm.

Kod: `Logolar.kt`, `LogoPanel.kt`, `Ana.kt`, `Paneller.kt`, `Cizim.kt`, `Disa.kt`, `Medya.kt`, `Model.kt` (`Proje.arsiv`, `Logo.kutupId/x/y`, `Klip.kareHizi`), `Tema.kt` (`SimgeDugme`).

> Not (13.11.2026): main dalından yanlışlıkla yapılan 3.2.30 derlemesi geri alındı; güncel kod bu daldadır.

## 3.7 (13.11.2026, deneme · `yerel-android` dalı): şeffaf oynatıcı, zarif takip yazısı
- **Oynatıcı videonun üstünde:** kontroller (oynat · başa · kare geri/ileri · ince çubuk · süre) videonun altında, şeffaf koyu geçişli şeritte; ayrı satır kalktı, bir satır yer kazanıldı. Eski satır ⋯ › "yazılı (önceki)" ile döner.
- **Açılışta tam oturma:** önizleme açılışta videonun oranına göre boyutlanır (üstte/altta boşluk yok); tutamaç durur, çift dokunuş yine tam oturur.
- **Önizlemede yarım ekran / siyah bant (kök neden):** önizlemede oynatıcı görünümü önce kareye kırpılıyor, hareket (yukarı-aşağı kayma, yaklaşma) SONRA bu kırpılmış görünüme uygulanıyordu; taşan kısım hiç görünmediği için yerine siyah bant çıkıyordu. Artık görünüm görüntünün kendi tam boyutunda ortalanır, hareket tam görüntüye uygulanır, kare dışı kesilir — dışa aktarmadaki hesapla birebir. Geçişteki video kuyruğu da aynı yöntemle.
- **Fotoğraf geçişinde karanlık kare:** oynatıcı yeni fotoğrafı çözerken birkaç yüz ms siyah gösteriyordu; geçiş süresince gelen fotoğraf da (aynı hareketle) alta çizilir, giden fotoğraf üstünde hareketine devam ederek solar.
- **Takip eden yazı stilleri (yan yana, çizgi simgeli):** Zarif (halka + eğik + yatay çizgi, yazı çizginin üstünde), Köşe (dik açılı çizgi), İğne (dikey çizgi), Kutulu (önceki). Çizgi rengi ayrı seçilir; nokta sağ kenara yakınsa çizgi sola açılır. Önizleme ve dışa aktarma aynı bitmapi kullanır. Yeni takiplerde varsayılan: Zarif.

## 3.6 (13.11.2026, deneme · `yerel-android` dalı): varsayılan ayarlar yenilendi, yüze yaklaşma, üst boşluk
- **Varsayılan ayarlar sayfası (Görünüm › Şablon):** 3.5'teki düğme ve renk düzeninde. Beş madde arka plansız, üstte simge, altta küçük yazı, ton renkleriyle duruyor; dokun = dahil et / çıkar, seçim hatırlanır. Altta tek düğme: **Seçili N ayarı uygula**.
  1. **Kadrajı doldur:** bütün klipler (fotoğraf ve video, yatay ve dikey) ekranı doldurur, Sığdır değil.
  2. **Yüze yaklaş:** dikey kişi fotoğrafında yüz bulunur. Yavaş yakınlaşma yüze doğru yapılır: yüz baştan kadrajın ortasına alınır (doldurulmuş dikey fotoğrafta yüz kesilmez) ve 5 sn içinde ekran yüksekliğinin ~%38'ine kadar büyür. Kenar hiç boş kalmaz.
  3. **Giriş başlığı "Renkli kutular"** (gönderilen örnekteki gibi):
     - Sarı tarih bandı (takvim simgesi, çekim tarihi).
     - Manşet satırları sarı · deniz yeşili · mercan kutularda, dar ve yüksek **Anton** harfiyle, büyük harf. Satırlar genişliğe göre kendiliğinden bölünür; `|` ile elle de bölünebilir.
     - Siyah konum bandı ("KABATAŞ, ORDU").
     - Şeritler sırayla kayarak gelir.
     - Tarih ve konum "Tarih | konum" alanından değiştirilir. Tarih ilk klibin çekim tarihidir (fotoğrafta EXIF, videoda üstveri); bilinmiyorsa projenin tarihi.
  4. **Abone ol "Koyu hap":** solda yuvarlak logo (yoksa kanal adının baş harfi), "Kanalıma abone ol / Yeni videolar için zili aç", sağda kırmızı **ABONE OL**; sol üstte, videonun %80'inde. Önceki kırmızı bant "Kırmızı bant (önceki)" olarak seçilebilir; düğme yazısı değiştirilebilir.
  5. **Kapanış "Cam kartlar":** "İzlediğin için teşekkürler!", Beğen · **Abone olundu** · Paylaş.
- **Yüz bulma:** Android'in cihaz içi yüz bulucusuyla yapılır (ücretsiz, ağ yok). Yeni eklenen dikey fotoğraflarda eklerken; eski projelerde "Varsayılan ayarları uygula"da.
  - Klip › Görünüm › Hareket'te **Yüze odaklan ve yaklaş** (aç/kapat). Yüz yoksa bu yazılır.
  - Önizleme ve dışa aktarma aynı hesabı kullanır (`Yuz.kt`).
- **Üst boşluk:** editörde ve dışa aktarma ekranında sistem çubuğunun altındaki boş satır kaldırıldı; ekran yukarı kaydı (yalnız yanlardaki kesik payı kaldı).
- **Önizleme tutamacı:** yukarı sürükleyerek video ekranın %10'una kadar küçültülebilir (önce %25); ayar alanı o kadar büyür. Aşağı sürüklemeyle en çok %75; çift dokun = %50.
- Yeni dosyalar: `Yuz.kt` (yüz bulma + yakınlaşma hesabı), `Varsayilan.kt` (madde seçimi, çekim tarihi), `res/font/anton.ttf` (SIL OFL 1.1, lisans `android/OFL-Anton.txt`).

### 3.6 düzeltme: Varsayılan → Kadrajı doldur fotoğraflarda çalışmıyordu
- Kök neden: önizleme oynatıcısında Doldur yalnız video yüzeyine (resizeMode) uygulanıyordu; fotoğraflar PlayerView'in ayrı görüntü katmanında gösterilir ve hep sığdırılıyordu. Artık `setImageDisplayMode(FILL/FIT)` da ayarlanıyor (ana önizleme, katmanlı önizleme, geçiş kuyruğu).
- Varsayılan seçili iken yeni eklenen klipler doğrudan Doldur ile gelir.
- Asıl neden: dikey klip eklenince boş proje kendiliğinden 9:16'ya çevriliyordu; kare dikey olunca Doldur hiçbir şey değiştirmiyordu. Bu otomatik çevirme kaldırıldı. Proje YouTube 16:9 açılır; Varsayılan da biçimi 16:9 yapar. Dikey video ve fotoğraflar 16:9 kareyi doldurur (üstü/altı kesilir). 9:16 yalnız Biçim panelinden elle seçilir.

### 3.6 ek 3: varsayılan hareket = Kareden Videoya "otomatik" modu (birebir)
- Dikey (kareden taşan) fotoğraf + yüz: yüze doğru kayar, sürenin %70'inde yüze varır, kalan sürede başın biraz üstü sabit kalarak hafifçe yaklaşır.
- Dikey fotoğraf, yüz yok: sırayla bir yukarı bir aşağı kayar (+%4). Yatay fotoğraf + yüz: yüze yaklaşır, yüz ekranın %42 yüksekliğine gelir. Yatay fotoğraf: saniyede %3 yaklaşır (en çok %60). Video: saniyede %1,5.
- Hareket gelen geçişle başlar, geçiş boyunca sürer (durma/geri sıçrama yok). Önizleme, dışa aktarma ve takip eden yazı aynı hesabı kullanır.
- Akıllı kadraj Kareden Videoya smartFill: yüz üstten %36–50 (baş kesilmez, omuzlar görünür), yüz yoksa ana nesne (anaObje: netlik + renk + doygunluk, gökyüzü bastırılır) ortada.
- Giriş (renkli kutular), abone ol (koyu kart), kapanış (cam kartlar), YouTube 16:9, fotoğraf 5 sn, 0,8 sn yumuşak geçiş: bugünkü istekler gibi kaldı.

### 3.6 ek: kadraj sınırı, parmakla kaydırma, otomatik varsayılan
- Önizlemede kare sınırı görünür (dışı koyu gri, ince açık çizgi).
- İzlerken de fotoğraf/videoyu parmakla yukarı-aşağı-sağa-sola kaydır (Doldur'da taşan kısım kadar, kenar boş kalmaz); çift dokun = ortala. Kaydırma klipte saklanır, dışa aktarmaya, geçişe ve takip eden yazıya aynen yansır. Kadraj kartında "Ortala".
- Fotoğraf/video seçildiği anda varsayılanlar uygulanır (Doldur, 5 sn yüze yaklaşma); ilk seçimde giriş, abone ol, kapanış ve YouTube 16:9 da gelir.

## 3.5 (13.11.2026, deneme · `yerel-android` dalı): yumuşak geçiş, oynatıcı, menüler, prompter
- **Çapraz geçişte parlama (kök neden):** geçiş, MediaMetadataRetriever ile alınan donuk son kareyle yapılıyordu; bu kare HDR klipte farklı ton eşlemesiyle çıktığı için geçişte parlama oluyordu. Artık geçişin üst katmanı aynı klibin gerçek son T ms'lik parçası (aynı çözücü, aynı ton eşleme), zaman çizelgesi geçiş başına T kısalır (`Proje.ortusme/zKlip`). Seçenek adları: Yumuşak · Kaydırarak · Büyüyerek.
- **Oynatıcı:** önceki/sonraki kare düğmeleri yarı boyuta indi, "başa dön" eklendi, zaman çubuğu inceldi.
- **Menüler:** alt başlıklar üstte çizim ikon, altta küçük ince yazı (InShot tarzı); bölüm çubuğu ikon + küçük yazı. Eski yazılı haplar ⋯ menüsünden "Alt başlıklar: yazılı (önceki)" ile geri gelir.
- **Sade düğmeler ve seçimler:** panellerdeki tüm kutu düğmeler (Sola/Sağa/Çoğalt/Sil, Değiştir/Kaldır, SRT…) ve seçim kutuları (16:9 / 9:16 / 1:1, Sığdır/Doldur, paketler, kalite) arka plansız; üstte ikon, altta küçük ince yazı, alt menüyle aynı renk ailesinden farklı tonlar. Seçili olan tam renk + kalın + altında nokta; oranlar kendi şeklini çizer. Turuncu ana eylemler dolgulu kalır. Eski kutulu görünüm ⋯ › "Alt başlıklar: yazılı (önceki)" ile geri gelir.
- **Varsayılan ayarları uygula (Şablon bölümü):** tek dokunuşla standart başlangıç — yatay videoda dikey fotoğraflar ekranı doldurur (ortadan), her fotoğraf 5 sn yavaş yaklaşır; hareketli giriş başlığı, videonun %80'inde "Takip et" hatırlatıcı, sonda "Takip et · Beğen" kapanışı.
- **Geri al / Yinele:** üst çubukta ↶ ↷ (40 adım; ardışık yazma/sürükleme tek adım).
- **Görünüm inceltme:** renk paletleri yarı yükseklikte, kaydırıcılar 3 dp çizgi + küçük tutamaç, açma-kapama düğmeleri küçük ve ton renginde, seçenek kutuları arka plansız renkli yazı (seçili kalın + nokta), önizleme köşeleri kare, klip küçük resimlerinin kenarı ince. Hepsi ⋯ › "yazılı (önceki)" ile eski görünüme döner.
- **Önizlemede geçiş parlaması (kök neden):** önizleme, giden klibin son karesini MediaMetadataRetriever ile alıp üstte soldurur­du; HDR videoda bu kare soluk/parlak çıktığı için geçişte parlama görünüyordu. Artık önizlemede de dışa aktarmadaki gibi giden klibin gerçek son T ms'si ikinci bir oynatıcıda (pencereye 2 sn kala hazırlanır, ana oynatıcıyla eş zamanlı, sesi dahil) oynar. Fotoğraflarda son kare kullanılmaya devam eder (HDR sorunu yok).
- **Seçenek yazıları:** ince, yazıya uygun oval çerçeve; seçili olan tam renk çerçeve + hafif dolgu + kalın.
- **Kart başlığında onay işaretleri:** "Tüm kliplere uygula" düğmeleri kalktı. Kadraj, Fotoğraf hareketi ve Klip sesi başlıklarının başında ✓✓ = tüm kliplere uygula; sağda ✓ = yalnız bu klip (seçim zaten bu klibe uygulanır; onaylar ve kartı kapatır).
- **Varsayılan ayarlar:** yatay videoda dikey videolar da (yalnız fotoğraflar değil) kadrajı doldurur, ortada dar kalmaz.
- **Prompter (Ses › Seslendirme):** metni yaz, "Tam ekran oku ve kaydet". Metin kendiliğinden kayar (hız ve yazı boyutu ayarlı, ekran açık kalır); kırmızı düğme kaydı ve kaydırmayı başlatır, ses videonun o anki konumundan itibaren seslendirme olarak eklenir. Metin ve ayarlar projede saklanır.

## 3.4 (13.11.2026, deneme · `yerel-android` dalı): düzeltmeler + cam fırçası
- **Önizleme küçüktü, tutamaç yoktu:** "önceki düzen" seçiliyken önizleme panelin altında eziliyordu ve tutamaç o düzende yoktu. Artık önizleme her zaman büyük (varsayılan ekranın %50'si) ve altında turuncu **tutamaç** var (sürükle: %25–75, çift dokun: %50). Ayrıca 16:9 projede dikey klipler kenarlarda siyahla küçük görünüyordu: boş projeye çoğunlukla dikey klip eklenince biçim kendiliğinden **9:16** olur (Düzenle › Biçim'den değişir).
- **Üst çubuk:** ince küçük yazılar — ← Geri · proje adı ▾ · Kaydet | Dışa Aktar.
- **Alt menüler önceki (yazılı) düzene döndü;** simgeli yeni menü ⋯ › "Alt menü: simgeli (yeni)" ile seçilebilir.
- **Otomatik altyazı "Model eksik indi":** kök neden — model arşivinin ilk klasörü atılıyordu; arşiv yapısı farklı olunca dosyalar yanlış yere açılıyordu. Artık tam yolla açılır, model kökü (`am/final.mdl`) aranıp bulunur; eksik indirme (5 MB altı) ve yapı sorunu açıkça yazılır, hata kaydına dosya listesi düşer.
- **Cam fırçası** (Düzenle › Nesne › katman seçili › Cam fırçası): kesilmiş nesnede cam bölgeleri boya → özgün görüntü parlaklığa göre yarı saydam olur (yansımalar kalır, arkadaki video camdan görünür). "Geri getir" (kesimde kaybolan cam kenarı), "Sil", fırça boyutu, Geri al (8 adım). Yeni kesilen nesnelerde özgün görüntü aynı kırpımla saklanır.

## 3.3 (13.11.2026, deneme · `yerel-android` dalı): yeni görünüm (Ahmet'in tasarım görseline göre)
- **Üst çubuk:** simgeli geri; proje adı + ▾ (Adını değiştir · Projelere dön); simgeli **Kaydet** (turkuaz) ve **Dışa Aktar** (turuncu) yuvarlak düğmeler. Amaçsız logo konmadı.
- **Önizleme:** köşeleri yuvarlatılmış kart. Altında **tutamaç**: aşağı sürükle = büyüt, yukarı = küçült (ekranın %25–70'i; ayar alanı o kadar genişler), çift dokun = varsayılan (%42). Seçim hatırlanır.
- **Oynatıcı:** büyük turuncu ▶/❚❚, simgeli ⏮ ⏭, solda an, sağda toplam süre; altında **kalın zaman çubuğu** (dokun/sürükle = o ana git).
- **Klip şeridi:** yuvarlak köşeli büyük resimler, sol üstte yuvarlak sıra numarası, sağ altta süre; seçili klip turuncu çerçeve; kesik çizgili turuncu **＋ Ekle** kartı.
- **Haplar:** simge + ad, eşit genişlikte tek sıra; seçili olan turuncu ve altı çizgili; seçili hapa tekrar dokun = paneli kapat.
- **Alt çubuk:** 5 bölüm simgeli, aralarında ince ayırıcı, üst köşeleri yuvarlak.
- **"Tüm kliplere uygula ›"** geniş hap düğmesi (Kadraj, Hareket, Klip sesi).
- **Kapanış kartı "Cam kartlar"** (yeni varsayılan): son karenin bulanık hâli üstünde sarı halkalı logo (yoksa kanal adının baş harfi), "İzlediğin için teşekkürler!" + sarı kıvrım, Beğen · Abone ol · Paylaş cam kartları (yatayda yan yana, dikeyde alt alta). Eski "Renkli haplar" ve "Sade" seçenek olarak duruyor.
- Simgeler uygulamanın kendi çizimi (`Simge.kt`); ek kitaplık yok.
- Önceki düzen hâlâ ⋯ menüsünde.

## 3.2 (13.11.2026, deneme · `yerel-android` dalı): kalan dört özellik + sade düzen
**İndir (deneme):** https://github.com/aaskinkk-cloud/studyo/releases/download/yerel/studyo-yerel.apk — üstüne kurulur. Telefonda test edilmeden `main`'e alınmayacak.

- **Sade düzen (InShot gibi):** önizleme ekranın %42'si ve **her bölümde görünür** (eskiden açık panel önizlemeyi eziyordu; Düzenle'de hiç görünmüyordu). Panel açılınca klip şeridinin yerini alır; klip seçmek gereken panellerde küçük şerit üstte kalır. Önceki düzen silinmedi: Ana sayfa ⋯ › "Editör: önceki düzen".
- **Üst üste binen geçişler** (Görünüm › Geçiş): Çapraz · Kaydırarak · Büyüyerek. Media3'te iki video dizisi: üst dizide giden klibin **kırpılmış devamı** (yoksa son karesi) gelen klibin üstünde solar/kayar; birleştirici (`GecisBirlestirici`) pencere dışında üst diziyi görünmez yapar. Video süresi değişmez, seslendirme kaymaz. Önizlemede giden klibin son karesiyle gösterilir.
- **Otomatik altyazı** (Metin › Altyazı): Vosk + Türkçe küçük model (~35 MB, ilk kullanımda bir kez iner, sonra internetsiz). Kaynak: videoların sesi ya da seslendirme. Ses akışla çözülür, 16 kHz'e çevrilir; kelime zamanları saklanır. Satırlar: en çok 7 kelime / 38 harf / 3,5 sn, 0,7 sn duraklamada yeni satır. Elle yazılan satırlar korunur. Yeni görünüm **"Kelime vurgulu"**: o an söylenen kelime seçilen renkte.
- **Reels stilleri** (Görünüm › Şablon › Giriş başlığı): 12 stil (Beyaz şerit, Kırmızı-beyaz, Sarı ünlem, Yeşil tırnak, Sarı balon, Ünlem kutusu, Dikkat, Yırtık kâğıt, Köşe çerçeve, Üç renk, Kırmızı sekme, Kırmızı alıntı); başlık satırları `|`, vurgu bantları `|`; 5 giriş hareketi (Trend, Düşerek, Kayarak, Yükselerek, Büyüyerek), konum; **efekt sesleri** (vuuş · tok · pop; uygulama içinde sentezlenir, telif yok; önizlemede de çalar) ve seviye; **ışıklı boncuklar** (tüm stillerde). Kapakta da 12 reels stili.
- **Videodan hareketli nesne** (Düzenle › Nesne): önizlemede o andan 2–10 sn, saniyede 15 kare ML Kit ile kesilir, 720 px PNG kare dizisi olur; hareketli katman olarak istenen klibin üstüne konur (sürükle, boyut, başı/sonu).
- APK yalnız arm64 (Vosk/JNA yerel kitaplıkları için; boyut küçük kalır).
- Kod: `Altyazici.kt`, `Reels.kt` (stiller, hareket, boncuk, efekt sesleri), `Katman.kt` (`GecisBirlestirici`, `NesneKareBindirme`), `Nesneci.videodanKes`, `Cizim.pencereler/ustKatman/altyaziKelimeli`.

## 3.1 (13.11.2026): web editördeki özellikler yerel editöre taşındı
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/android/studyo.apk — üstüne kurulur.
**Test (13.11.2026, Galaxy S25 Ultra, Ahmet):** geçiş, şablon, altyazı, kapak, takip eden yazı, nesne kesme, gündüz teması ve dışa aktarma — başarılı.

Sırayla (önizlemede de, dışa aktarmada da aynı çizim ve aynı zaman hesabı):
1. **Geçiş** (Görünüm › Geçiş): Yok · Siyahtan · Beyaz parlama · Yakınlaşarak; süre 0,3–2 sn; "videoyu siyahtan açarak başlat". Klip sınırında giden klibin sonu ve gelen klibin başı efektlenir; videonun sonu asla karartılmaz. Zaman çizelgesi değişmez (seslendirme kaymaz). *Yok:* iki klibin üst üste bindiği geçişler (çapraz, kaydırma, silinme) — Media3'te çoklu video dizisi gerektiriyor; ayrı adımda.
2. **Şablon** (Görünüm › Şablon): hazır paketler (YouTube · Reels · Sinema); giriş başlığı (Sinematik, Alt bant, Kutulu, Neon, Renkli bant; başlık `|` ile satır, alt başlık, süre); abone hatırlatıcı (videonun %10–90'ında, 4 sn); kapanış kartı (aydınlık degrade, yuvarlak logo, kanal adı, Beğen · Abone ol · Paylaş ya da sade; 2–8 sn, videonun sonuna eklenir). *Yok:* web'deki 12 reels stili, efekt sesleri, ışıklı boncuklar, çekim tarihi bandı.
3. **Altyazı** (Metin › Altyazı): "şu anda ekle" (3 sn), her klibe bir satır, başı/sonu = şu an, git, sil; SRT yükle/kaydet; görünüm: koyu kutu / kenarlı / sarı kenarlı, alt/orta/üst, boyut. *Yok:* otomatik konuşma→yazı (ücretsiz cihaz içi Türkçe tanıma modeli gerekiyor).
4. **Kapak** (Yayın › Kapak): önizlemedeki kare ya da **6 öneri** (netlik + pozlamaya göre); yazı (`*vurgu*`, `|` satır), Kalın kenarlı / Kutulu / Renkli bant, konum, 5 vurgu rengi, boyut, canlandır + kenar karartma, logo; JPG → Pictures/Stüdyo (yazma doğrulanır).
5. **Takip eden yazı** (Metin › Takip): önizlemede noktaya dokun → nokta klibin sonuna kadar kare kare izlenir (şablon eşleme, ışık değişimine dayanıklı, titreme süzgeci; video belleğe alınmaz, kareler küçük gruplar halinde çözülür). Yazı + alt satır, boyut, renk, kutu. Zamanlar kaynak dosyaya göre tutulur; klibi sonradan kırpsan da kaymaz.
6. **Nesne kesme** (Düzenle › Nesne): fotoğraftan ana nesne Google ML Kit Subject Segmentation ile kesilir (ücretsiz, cihaz içinde; model Play Hizmetleri ile bir kez iner), saydam kenarlar kırpılır, videonun üstüne katman olarak eklenir; önizlemede parmakla sürükle, boyut, yumuşak gölge, başı/sonu = şu an. *Yok:* web'deki ISNet kalitesi ve videodan (hareketli) nesne kesme.
7. **Gündüz teması**: Ana sayfa ⋯ › Gündüz teması (gece teması silinmedi, aynı menüden geri dönülür; seçim hatırlanır).

Kod: `Cizim.kt` (başlık/hatırlatıcı/altyazı/kapanış/kapak çizimleri, geçiş hesabı, zamanlı bindirmeler), `Katman.kt` (geometri, takip ve nesne bindirmeleri), `Takipci.kt`, `Nesneci.kt`, `Paneller.kt` (yeni paneller + katmanlı önizleme), `res/layout/oynatici.xml` (TextureView: önizlemede ölçekleme çalışsın diye).

## 3.0 (13.11.2026): yerel Android uygulaması — büyük videolar
**İndir:** https://github.com/aaskinkk-cloud/studyo/releases/download/android/studyo.apk — mevcut Stüdyo'nun üstüne kurulur (aynı paket ve imza), kaldırmak gerekmez.

- **Kök neden (web sürümü):** dışa aktarma `Mp4Muxer.ArrayBufferTarget` + `fastStart: 'in-memory'` ile çıkan videonun TAMAMINI tarayıcı belleğinde biriktiriyor, sesi de tek parça `AudioBuffer` olarak karıştırıyordu; sonra Blob indirmesi yapılıyordu. Bu yüzden ~200 MB üstünde bellek/indirme sınırına takılıp siyah ekran, donma ya da kaydedilmeyen dosya oluşuyordu. Yama ile çözülmez; 3.0'da video yolu tarayıcıdan tamamen çıkarıldı.
- **Yeni mimari:** Kotlin + Jetpack Compose + Media3 1.8.1.
  - İçe aktarma: Storage Access Framework (kalıcı okuma izni). Videolar **kopyalanmaz, belleğe alınmaz**; yerinden akışla okunur. Boyut sınırı yok. Paylaşımla gelen ve kalıcı izni olmayan dosya, yer kontrolüyle parça parça uygulamaya kopyalanır.
  - Her videonun kodeki eklerken denetlenir (MediaExtractor + MediaCodecList); telefonun çözemediği biçim eklenmez, nedeni yazılır. HDR klipler dışa aktarmada SDR'ye çevrilir.
  - Fotoğraflar en fazla 3840 px'e örneklenerek açılır (200 MP fotoğraf bile belleği taşırmaz), yönü EXIF'ten düzeltilir.
  - Önizleme: ExoPlayer (klip sırası, kırpma, klip sesi, müzik, seslendirme eşzamanlı). Yazı ve logo önizlemede de dışa aktarmadaki bitmapin aynısıyla çizilir.
  - Dışa aktarma: Media3 Transformer, **ön plan hizmeti** (ekran kapansa da sürer, bildirimde % ve kalan süre, Duraklat). Çıktı doğrudan diske akar. H.264 + AAC MP4; 720p / 1080p / 2K / 4K (telefonun kodlayıcısı desteklemiyorsa kart kapalı ve yazılı).
  - **Kayıt güvenliği:** başlamadan önce boş alan denetlenir (video boyutunun iki katı + 300 MB). Bittikten sonra dosya **doğrulanır** (boyut > 0, görüntü izi var, süre beklenenin ≥ %95'i, ortadan kare okunuyor), galeriye (Movies/Stüdyo) kopyalanır, **kopya tekrar doğrulanır**; ancak o zaman "Kaydedildi" denir. Hatalı/eksik dosya galeride bırakılmaz.
  - **Yarıda kalma:** Duraklat ya da Android'in süre sınırı → dosya düzgün kapatılır; müzik/seslendirme yoksa **kaldığı yerden devam** (Transformer.resume), varsa baştan. Uygulama sistem tarafından öldürülürse yarım dosya silinir ve açılışta söylenir.
  - **3.0.1 — HDR düzeltmesi (13.11.2026):** Samsung'un HDR (HLG, 10 bit HEVC) videolarında "ERROR_CODE_DECODING_FAILED" alınıyordu. Kök neden: Media3 görüntü hattının renk ayarını **ilk klibe** göre kurar; OpenGL ile HDR→SDR çevirisi yalnız ilk klip HDR ise açılır. Fotoğraf/SDR klip + HDR klip karışınca HDR klip SDR hattına 10 bit girer ve çözücü düşer. Çözüm: her HDR klip **kendi çözücüsünde** SDR'ye çevrilir (tüm girişler aynı renk düzeninde). Telefon bunu yapamazsa hata gösterilmeden önce **bir kez, farklı yöntemle** (HDR'yi SDR gibi okuma) otomatik yeniden denenir; sonuç ekranında bu belirtilir. Aynı yöntem tekrar denenmez.
  - **3.0.2 — arka plan düzeltmesi (13.11.2026, hata kaydından):** Ön plan hizmeti hiç başlamıyordu: `InvalidForegroundServiceTypeException: Starting FGS with type none`. Kök neden: `androidx.core` ServiceCompat, Android 15'in `mediaProcessing` türünü tanımadığı için türü siliyor; Android 15+ türsüz hizmeti reddediyor. Sonuç: ekran kapanınca/arka planda uygulama dondurulabiliyor, uzun 4K işlerde `MUXING_TIMEOUT` (10 sn örnek yazılmadı) görülüyordu. Çözüm: `Service.startForeground(id, bildirim, tür)` doğrudan çağrılıyor (olmazsa `dataSync`); ön plan alınamazsa işlem **başlatılmıyor** ve açık hata veriliyor. Takılma bekçisi 10 → 60 sn (gerçek takılmayı yine yakalar).
  - Dışa aktarma sırasında önizleme oynatıcısı donanım çözücüleri bırakır (büyük 4K/HEVC dosyalarda çakışma olmasın diye).
  - Hata kaydı: Ana sayfa ⋯ › Hata kaydını paylaş (her ekleme ve dışa aktarmanın dosya boyutu, kodek, çözünürlük, hata kodu).
- **Video editörü (yerel):** üstte Kaydet (turkuaz) + Dışa Aktar (turuncu); ⏮ ▶ ⏭ + zaman çubuğu; büyük klip resimleri şeridi; altta 5 bölüm (Düzenle · Görünüm · Ses · Metin · Yayın) ve hap düğmeler:
  - Düzenle › Klip (kırp, fotoğraf süresi, sola/sağa, çoğalt, sil – iki adım) · Ekle · Biçim (16:9 / 9:16 / 1:1)
  - Görünüm › Kadraj (Sığdır/Doldur, tümüne uygula) · Hareket (fotoğrafta yavaş yakınlaşma)
  - Ses › Klip sesi (sessiz, %0–150) · Müzik (ses, tekrar) · Seslendirme (o andan kayıt, kayıt listesi, ses, sil)
  - Metin › Yazı (klip başına; üst/orta/alt, boyut, renk, arka kutu, tümüne kopyala)
  - Yayın › Logo (4 köşe, boyut, opaklık, yuvarlak) · Dışa aktar
  - Ana sayfa: Yeni video · **Fotoğraf + önceki Stüdyo (web)** · Son çalışmalarım (aç, çoğalt, adını değiştir, sil – onaylı).
- **Korunanlar:** fotoğraf editörü (Karanlık Oda), önceki web video editörü ve web'deki tüm projeler olduğu gibi duruyor; "Fotoğraf + önceki Stüdyo" düğmesi bunları 2.x'teki gibi Chrome ile açar (web projeleri Chrome'un deposunda, APK güncellemesinden etkilenmez). Paylaş menüsünden gelen dosya için "Yeni video projesi" ya da "Fotoğraf editörü (web)" seçilir.
- **Yerel editörde (henüz) olmayanlar:** web video editöründeki şablonlar, geçiş efektleri, altyazı, kapak, nesne kesme (yapay zekâ), takip eden yazı, gündüz teması. Bunlar web editöründe duruyor.
- Kod: `android/app/src/main/java/app/studyo/android/` — `Disa.kt` (kompozisyon, ön plan hizmeti, doğrulama, galeriye kayıt), `EditorDurum.kt` (proje + önizleme), `Medya.kt` (inceleme, küçük resim, fotoğraf hazırlama), `Kaplama.kt` (yazı/logo), `Editor.kt`, `DisaEkrani.kt`, `Ana.kt`, `Tema.kt`, `Depo.kt`, `Model.kt`. APK'yı GitHub Actions derler (`.github/workflows/android.yml`).
- **Test durumu (13.11.2026, Galaxy S25 Ultra, Ahmet):** geçti.
  - 11 klip (çoğu HDR HEVC + 224 MB H.264), 6:31, 2K → 939 MB, 46 sn.
  - 31 klip (24 HDR HEVC + 412/344/224/184 MB H.264), 26:03, 2K → 3,94 GB, 3 dk 27 sn; galeride oynadı.
  - Aynı 31 klip, 4K Yüksek (64 Mbit/sn) → 11,62 GiB (~12,5 GB), 25:56, 7 dk 15 sn; doğrulandı.
  - Oynatma, renk/ses, ekran kapalı çalışma, müzik/seslendirme, Duraklat → kaldığı yerden devam: başarılı.
  - Düzeltilenler: 3.0.1 HDR (karışık SDR/HDR projede çözücü hatası), 3.0.2 ön plan hizmeti türü + takılma bekçisi.

### 3.0 cihaz test listesi
Her boyut için (50 MB, 200 MB, 500 MB, 1 GB, 2 GB):
1. **İçe aktar:** Ekle › dosyayı seç. Klip şeritte görünmeli; Klip panelinde boyut/kodek doğru yazmalı.
2. **Düzenle:** kırp (başı ve sonu), yazı ekle, logo ekle, 5 sn seslendirme kaydet, müzik ekle. Önizleme takılmadan oynamalı.
3. **Kaydet:** Dışa aktar › 1080p › Videoyu oluştur. Ekranı kapat, 1 dk sonra aç: işlem sürmeli. Sonunda "✓ Kaydedildi ve doğrulandı".
4. **Yeniden oynat:** ▶ Oynat; Galeri › Movies/Stüdyo'da dosya oynamalı, süre ve ses doğru olmalı.
5. Ayrıca bir kez: müziksiz projede Duraklat → Kaldığı yerden devam.
Sorun çıkarsa: Ana sayfa ⋯ › **Hata kaydını paylaş** ve kaydı gönder.

## 2.16 (08.11.2026): video editöründe yeni düzen (tasarım paketi, 8 ekran)
- Plan: "Stüdyo Video — Ayrıntılı Sayfa Planı" belgesi (eskizdeki 8 ekran, Stüdyo'nun gerçek düğmelerine eşlenmiş). Kapsam yalnız video bölümü; fotoğraf bölümü değişmedi.
- **Alt çubuk 5 bölüm:** Düzenle · Görünüm · Ses · Metin · Yayın. Seçili bölümün alt başlıkları hemen üstte hap düğmeler olarak hep görünür (gizli menü yok):
  - Düzenle › Çizelge (panel kapalı, zaman çizelgesi) · Klipler · Tuval · Nesne
  - Görünüm › Şablon · Efekt ve geçiş
  - Ses › Müzik · Seslendirme
  - Metin › Yazı · Altyazı
  - Yayın › Logo · Kapak · Bilgiler · Dışa aktar
  - Açık bölümün simgesine yine dokununca panel kapanır. Her bölüm en son açılan hapı hatırlar (`localStorage['studyo.vGrup']`).
  - Haplar Kareden Videoya'nın kendi düğmelerine basar; eski 14 düğme gizli ama yerinde. Eski "Doldur" kısayolu klip araçlarındaki Doldur ile aynı işlev.
- **Üst çubuk:** **Kaydet** (turkuaz; projeyi hemen cihaza kaydeder, otomatik kayıt da sürer) + **Dışa Aktar** (turuncu).
- **Oynatıcı satırı:** ⏮ önceki klip · ▶ · ⏭ sonraki klip; zaman çizelgesi de o ana kayar. Tek satır.
- **Paneller:** turuncu dolgu başlıklar yerine sakin koyu kartlar; açık bölüm başlığı turuncu yazı + sol çizgi. Ana eylem düğmeleri turuncu.
- **Dışa aktarma:** çözünürlük 4 büyük kart; Çözünürlük, Kalite ve Video oluştur kartları ilk girişte açık; "Videoyu oluştur" tam genişlik; "Başka cihaza taşı" en altta. Motor ve seçenekler aynı.
- **Video ana sayfası:** "Son çalışmalarım" şeridi (kapak, süre, ad, klip sayısı, tarih). Karta dokun = aç; ⋯ › Aç · Çoğalt · Sil (iki adımlı onay). Çoğalt aynı dosyaları kullanan yeni bir taslak kaydı yazar, yer kaplamaz.
- **Önceki düzen silinmedi:** ⋯ menüsü › **Önceki düzene geç** (geri: **Yeni düzene geç**; `localStorage['studyo.vYeni']`).
- Eskizde olup eklenmeyenler (Stüdyo'da karşılığı yok, süs düğme konmadı): taç/premium, şablon galerisi ve arama, ses efektleri, yinele, YouTube'a doğrudan yükleme, su işareti, proje adı değiştirme.
- Kod: `kaynak/video-yeni.js` (yeni), `video/index.html` içindeki kopyası; `esitle.py` bu dosyayı ekler ve `__kv.taslakYenile` kancasını açar. Kareden Videoya'nın işlevleri ve kayıt biçimi değişmedi.
- Test (sunucuda, 412×915 ve 1280×800): her hap doğru paneli/aracı açıyor; Kaydet; ⏮/⏭ ve çizelge kayması; klip araçları; son çalışmalarda aç/çoğalt/sil; önceki düzene geçiş ve dönüş; gündüz teması; geniş ekranda eski görünüm etkilenmiyor. Hata yok. Telefonda (APK) denenmedi.

## 2.15 (08.11.2026): video editöründe üç düzeltme
- **Takip eden yazı:** önizlemede noktaya dokununca ekranın altında **yazı kutusu kendiliğinden açılır**, klavye gelir (Yazı + Alt satır). Takip arkada sürer, ilerlemesi kutuda görünür. **Tamam** ile kapanır; **Yazı stili, renk…** Klip bölümündeki takip ayarlarını açar. Eskiden yazı alanı Klip bölümünün altında, kapalı bir akordeonun içinde kalıyordu ve bulunamıyordu. "Yazıyı sonra bölümde gir" diyen yanıltıcı ipucu düzeltildi.
- **Önizleme boyu:** oynatıcı satırının (▶) altındaki **tutamacı sürükle**: aşağı = büyüt, yukarı = küçült; **çift dokun** = varsayılan boy. Seçim hatırlanır (`localStorage['studyo.pvk']`). Not: yatay video dik telefonda zaten tam genişlikteyse daha fazla büyüyemez; küçültme her zaman çalışır.
- **Alt menüler:** araç çubuğu (Tuval…Doldur) ve klip araçları (Düzenle…Tamam) yana kayan, yarım kesik tek sıra yerine **iki düzgün sıra**; boş hücre yok.
- Kod: `kaynak/video-arayuz.js` (sonundaki 2.15 bloğu), `video/index.html` içindeki kopyası. Kareden Videoya'nın işlevleri ve kayıt biçimi değişmedi.

## 2.14 (08.11.2026): video editöründe sade düzen (menü arkasında menü yok)
- **Neden:** video ekranında üç katman üst üste çalışıyordu: alttaki bölüm (Klip, Şablon…) → bölüm içi sekmeler (Hızlı yol / Sıra ve klip / Yeni klipler / Tümü) → ara başlık sırası → sayfa sırası ve tek tek kaydırıcı. Bir ayara 3-4 adımda ulaşılıyordu; bir yerde yazı da alttaki şeridin arkasında yarım kalıyordu.
- **Sade düzen (varsayılan):** alttan bir bölüme dokununca o bölümün tüm ayarları **tek sayfada, alt alta** durur. Bölüm içi sekmeler, ara başlık sırası, sayfa sırası ve tek tek kaydırıcı yok. Bölümler başlığına dokununca açılır-kapanır (akordeon). Uzun seçenek şeritleri yana kaymaz, düzgün satırlara sarılır. Bölüm başlığı kartın iki kenarına kadar uzanır.
- **Eski sekmeli düzen silinmedi:** ⋯ menüsü › **Sekmeli düzene geç** (geri: **Sade düzene geç**). Seçim hatırlanır (`localStorage['studyo.vSade']`).
- Hiçbir işlev kaldırılmadı; öğeler taşınmaz, yalnız gizleme kalktı. Fotoğraf editörü (Karanlık Oda) değişmedi.
- Kod: `kaynak/video-arayuz.js` (sonundaki blok; sayfalama `aktif()` sade düzende çalışmaz), `video/index.html` içindeki kopyası.
- Test (sunucuda, 412×915, iki fotoğraflı proje): Klip ve Şablon bölümlerinde sekme sırası yok, gizli öğe 0; Sekmeli düzene geçince sekmeler ve gizleme geri geldi, tekrar Sade'ye geçince kalktı. Hata yok. Telefonda denenmedi.

## 2.13 (08.11.2026): APK'da tam ekran uyarısı yok · gizli önizleme tek dokunuşla geri
- **"Tam ekrandan çıkmak için yukarıdan sürükleyip…" uyarısı:** Stüdyo APK'sı bir Trusted Web Activity'dir; sayfayı telefondaki Chrome'un kendisi açar (WebView değil). Uyarı, sayfa tam ekran isteği gönderince Chrome'un kendisinden çıkıyordu (önizleme gizliyken ⛶ düğmesi sayfa tam ekranı istiyordu). APK'da (ve kurulu uygulamada) sayfa zaten tam ekran olduğu için artık hiçbir tam ekran isteği gönderilmez → uyarı çıkmaz. Önizleme tam ekranı CSS ile aynen çalışır. Chrome sekmesinde eski davranış aynı.
  - Tanıma: açılışta `document.referrer` = `android-app://…` (oturum boyunca `sessionStorage['studyo.apk']`), ya da `display-mode` fullscreen/standalone. Kod: `kaynak/kopru.js` (sonundaki blok), foto ve video sayfalarındaki kopyası.
- **Gizlenen önizleme:** önizleme gizliyken oynatıcı satırındaki ⛶ düğmesi **turuncu göz** olur; dokununca önizleme geri gelir, düğme yine ⛶ olur. Eskiden "Önizlemeyi göster" yalnız Katman bölümünün üstünde ince yazıydı; Şablon vb. bölümdeyken bulunamıyordu. Kod: `kaynak/video-arayuz.js` (sonundaki blok), `video/index.html` içindeki kopyası.
- APK yeniden kurulmaz; sayfa güncellemesi yeter (Stüdyo'yu kapatıp aç).
- Test (sunucuda, 412×915): önizleme gizle → düğme "Önizlemeyi göster" (göz) → dokun → önizleme geri, düğme "Tam ekran". APK işaretiyle sayfa tam ekran isteği gönderilmedi. Hata yok. Telefonda denenmedi.

## 2.12 (08.11.2026): Kes › Sil ve Koru kenara sadık (akıllı kenar)
- **Akıllı kenar** (varsayılan açık, Sil ve Koru'da): fırçanın ortasındaki renk örneklenir. Dairenin içinde yalnız bu renge benzeyen ve keskin renk sıçraması (kenar çizgisi) aşılmadan ulaşılabilen yerler etkilenir. Sil arka planı alır, nesneye girmez; Koru nesneyi geri getirir, arka plana taşmaz. Benzerlik azaldıkça etki yumuşak azalır, kenar tırtıklı kalmaz.
  - Yapay zekânın emin olduğu sınır da duvar sayılır: arka plandan başlayan Sil, yapay zekânın kesin nesne dediği yere girmez (Koru tersi).
  - Kullanım: fırçanın **ortasını** silinecek/korunacak renkte tut; dairenin kenarı nesneye değse de o kısım etkilenmez. Akıllı kenar açıkken imleç turuncudur.
  - **Hassasiyet** (0–100, varsayılan 45): az siliyorsa artır, kenarı aşıyorsa azalt.
  - Sınır: arka planla aynı renkteki ya da çok bulanık kenarda (beyaz tabak + açık zemin gibi) ayıracak bir şey yoktur; orada yakınlaştırıp küçük fırça ya da Akıllı kenar kapalı kullan.
  - Çok renkli nesnede Koru o anki renge göre çalışır: desenin (kırmızı benek gibi) üstünden de geç.
- **Akıllı kenar kapalı** = eski davranış (dairenin içindeki her yer), seçenek olarak duruyor.
- **İmleç parmağın üstünde** (varsayılan açık): fırça parmağın biraz yukarısında çalışır, parmak gördüğün yeri kapatmaz. Parmağın yeri küçük beyaz noktayla gösterilir. İlk dokunuşta boyamaz: imleci görürsün, kaydırınca başlar; kısa dokunuş tek damga basar.
- **Büyüteç** (varsayılan açık): fırçalarken köşede fırçanın altındaki yer büyütülmüş görünür (parmak sol üstteyse sağ üste geçer).
- **Titreme süzgeci:** fırça parmağı hafif gecikmeyle izler, kenar boyunca düzgün iz bırakır.
- **Kenar rötuşu:** akıllı darbe bitince değişen yerdeki sert alfa basamakları 1 piksel yumuşatılır.
- Ayarlar (akıllı kenar, hassasiyet, imleç, büyüteç) hatırlanır. Cam fırçası ve Kenar aracı değişmedi.
- Kod: `kaynak/foto-nesne.js` (`akilliHarita`, `firca`, `rotus`, `buyutecCiz`), `foto/index.html` içindeki kopyası. Kayıt biçimi değişmedi.
- Test (sunucuda): yeşil zemin önünde bulanık kenarlı beyaz tabak, içinde kırmızı desen. Kenar boyunca Sil: nesneden 0 piksel gitti, arka plan kenara kadar temizlendi (kenar geçişi 1 piksel). Koru: kenar dışına 0 piksel taştı. En büyük fırçada damga başına ~22 ms (bilgisayarda). Telefonda denenmedi.

## 2.11 (08.11.2026): galeriden "Paylaş → Stüdyo"
- Telefonun galerisinde (ya da dosya yöneticisinde) fotoğraf/video seçip **Paylaş** deyince listede **Stüdyo** çıkar.
  - Yalnız fotoğraf: Karanlık Oda editöründe açılır (birden çok fotoğraf → toplu düzenleme).
  - İçinde video varsa: video editöründe açılır (fotoğraflar da klip olarak eklenir).
- **APK'yı yeniden kurmak gerekir:** Paylaş menüsündeki kayıt APK'nın içindedir. İndirme: https://github.com/aaskinkk-cloud/studyo/releases/download/android/studyo.apk
- Güncellemeden sonra Stüdyo'yu **bir kez normal aç** (yeni önbellek yüklensin), sonra paylaş.
- "Birlikte aç" menüsü eklenmedi: Chrome, Android'de web uygulamasına dosya ilişkilendirmeyi desteklemiyor.
- Nasıl çalışır: APK dosyayı `/studyo/paylas` adresine gönderir (Web Share Target). `sw.js` bunu yakalar, dosyaları ayrı bir önbelleğe (`studyo-paylas`) yazar, ana sayfayı `?paylas=1` ile açar. Ana sayfa dosyaları alır, önbelleği siler ve ilgili editörün kendi dosya seçicisine verir. Dosyalar telefondan çıkmaz.
- Kod: `android/…/AndroidManifest.xml` (SEND/SEND_MULTIPLE, `METADATA_SHARE_TARGET`), `strings.xml` (`share_target`), `kaynak/sw.js`, `kaynak/kabuk.html` (`paylasimAl`), `kaynak/esitle.py` (web manifestine `share_target`).
- Test (sunucuda): galeri paylaşımı gibi bir fotoğraf POST edildi → fotoğraf editörü açıldı, ara önbellek silindi, hata yok. Telefonda denenmedi.

## 2.10 (07.11.2026): menüler başlığın arkasında kalmıyor · videodan kişi kesme 2 kat hızlı
- **Hata:** Görünüm · Efekt (ve diğer bölümlerde) alt sekme sırası panel başlığının üstüne çıkıp arkasında yarım kalıyordu. Neden: telefon döndürülünce, tam ekrana girip çıkınca ya da pencere boyu değişince panel kabuğu yeniden kuruluyor, başlık en sona ekleniyordu. Başlık artık hep en üste eklenir (`esitle.py` kancası). Test: geniş ekrana geçip dönünce sıra başlık › sekmeler.
- **Videodan kişi kesme hızı** (Nesne panelinde ayar, varsayılan Hızlı):
  - **Hızlı · 5 kare/sn:** işlenecek kare yarıya iner, süre yaklaşık yarıya düşer. Aradaki kareler iki maskenin karışımıyla doldurulur, hareket akıcı kalır.
  - **Akıcı · 10 kare/sn:** eski davranış; hızlı hareketli kişide daha doğru.
  - İki modda da sıradaki kareye atlama artık yapay zekâ çalışırken yapılır; bu da her karede biraz zaman kazandırır.
  - Çizimde iki işlenmiş kare arasındaki maskeler karıştırılır (Akıcı modda da daha yumuşak).
- Model boyutu (1024) küçültülemedi: ISNet modelinde sabit boyutlu ara katmanlar var; 768 px'te çalışmadı (denendi).
- Test (sunucuda, grafik birimi yok): 3 sn'lik videoda Hızlı mod 16 kare işledi (Akıcı 31 kare), kesilen kişi önizlemede doğru göründü, hata yok.

## 2.9 (07.11.2026): cam fırçası otomatik
- Nesne kesilince cam bölgesi **kendiliğinden fırçalanır**: fotoğraf editörü › Kes ve video editörü › Nesne (fotoğraftan eklenen nesne, ayrı parçalar, fotoğraf klibinden kesilen). Videodan kesilen hareketli kişide uygulanmaz.
- **1) Çay bardağı:** nesnede çay bandı (koyu kırmızı-kehribar) aranır. Çay seviyesinin üstünde, çayın genişliği boyunca kalan kısım cam sayılır; ten rengi ve çay renkli pikseller hariç. Parlamalar ve kenar çizgileri fırçanın kendisi gibi korunur. Çay ve tabak olduğu gibi kalır.
- **2) Çay yoksa genel tahmin:** nesnenin iç kısmında (kenardan uzak) çevredeki arka plana çok benzeyen yerler cam sayılır. Böyle yerler nesnenin %6–85'i değilse hiçbir şey uygulanmaz. Genel tahmin daha çok hata yapar: arka planla aynı renkteki opak nesneyi (beyaz masada beyaz fincan gibi) cam sanabilir.
- **Düzeltme:** fotoğrafta ↶ Geri al (tek adım), fazla giden yer Koru, eksik yer Cam fırçası. Videoda Nesne › Cam › Geri al.
- **Ayar:** Cam fırçası seçiliyken **✨ Camı otomatik bul** (istediğin an yeniden uygula) ve **Kesince otomatik: açık/kapalı** (video: Yeni nesnede otomatik). Ayar iki editörde ortak, hatırlanır; varsayılan açık.
- Kod: `yz/motor.js` › `stYZ.camBul` (ortak motor), `kaynak/foto-nesne.js`, `kaynak/video-nesne.js`. Kayıt biçimi değişmedi (fotoğrafta alfa + cam haritası, videoda `_c` haritası).
- Test (sunucuda): yeşillik önünde ince belli çay bardağı (çay, tabak, parlama çizgisi). Fotoğraf editöründe ve video Nesne'de çayın üstü saydamlaştı; parlama, ağız çizgisi, çay ve tabak kaldı. Yapay sahnelerde: boş cam genel yöntemle bulundu, düz renkli opak nesneye uygulanmadı. Gerçek fotoğrafla telefonda denenmedi.

## 2.8 (07.11.2026): yazıya dokun → ekranda düzenle · rastgele hareket yönü · müzik düğmeleri sabit
- **Önizlemede yazıya tek dokunuş:** başlık, alt başlık, ek yazılar, vurgu bantları, kanca, abone hatırlatıcı, kapanış yazıları, altyazı ve Metin yazıları.
  - İlgili bölüm açılır: alt menü sekmesi, ara sekme, ikinci sıra ve sayfa kendiliğinden seçilir; alan ortalanıp parlar.
  - Önizlemenin üstünde **düzenleme kutusu** çıkar (yazıyı örtmez, klavyenin altında kalmaz). Yazdıkça hem alan hem görüntü anında değişir. **✓ Bitti**, Enter ya da dışarı dokunmak kapatır.
  - Çift dokunuş eskisi gibi tam ekran; sürükleme eskisi gibi kadraj. Nesne, Metin, Tuval, Odak panelleri açıkken ve logo sekmesinde devre dışı.
  - Nasıl bulunur: her aday yazı bir anlığına değiştirilir, dokunulan yerdeki piksel farkı en büyük olan seçilir (çizim yolundan bağımsız). Tarih rozeti gibi yazı alanı olmayan öğeler bulunmaz.
- **Hareket efekti › Otomatik artık rastgele yönlü** (klip başına sabit; önizleme ve video aynı):
  - Dikey fotoğraf aşağı ya da yukarı kayar. Kadrajın en üstteyse bile kadrajına varış ya da kadrajından ayrılış seçilerek iki yön de olur.
  - Yüzlü dikey fotoğraf: yüze iki yanda yer varsa üstten ya da alttan varır.
  - Yatay fotoğraf: yaklaşır ya da uzaklaşır.
  - Aynı yön üst üste üçten fazla gelmez. Hepsi aynı yöne gitsin istenirse Aşağıdan yukarı / Yukarıdan aşağı / Hep yaklaş seçenekleri aynen duruyor.
- **Müzik (ve diğer) bölümlerde düğmelerin kendi kendine kayması giderildi:** sayfa sırası yalnız bölüm/sayfa seçilince ya da panel boyu değişince yeniden kurulur; liste dolarken, süre yazısı değişirken kurulmaz. Panelin kaydırma yeri de artık sıfırlanmaz.
- Kod: `kaynak/video-ek.js` (yeni; Kareden Videoya kapanışının içine eklenir), `kaynak/video-arayuz.js` (sayfalama), `esitle.py` (ekleme).
- Test (sunucuda, 412×915): başlığa ve abone yazısına dokunma → doğru bölüm ve alan, kutudan yazınca alan ve görüntü değişti; çift dokunuşta kutu açılmadı; 8 dikey fotoğrafta yönler karışık; müzik panelinde içerik 10 kez değişti, sayfalar 0 kez yeniden kuruldu. Hata yok. Telefonda denenmedi.

## 2.7 (07.11.2026): Sıra ve seçili klip tek ekranda
- Video editörü › Klip: **Sıra** ile **Seçili klip** sekmeleri birleşti → **Sıra ve klip**. Yukarıdan aşağı: büyük klip şeridi · seçili klip çubuğu · klibin ayarları (Süre · Kadraj · Hareket · Takip yazı) · Katmanlar. Bu sekme sayfalara bölünmez, kaydırılır.
- **Büyük klip resimleri:** şeritteki kareler 74 → 118 px, net çizilir. Klip katmanındaki bloklar 30 → 60 px; klibin resmi blok boyunca yan yana tekrarlanır.
- **Her klipte düğmeler** (şeritte, karenin altında): 🔊 ses aç/kapat (yalnız video; kısıksa üstünde % yazar) · ⧉ çoğalt · 🗑 sil. Sil yanlışlıkla silmeye karşı iki dokunuş ister: seçili olmayan klipte ilk dokunuş klibi seçer, ikincisi siler. Hepsi ↶ Geri al ile geri alınır (ses hariç; ses tek dokunuşla geri açılır).
- **Seçili klip çubuğu:** Sola · Sağa · Sessiz/Sesi aç · Çoğalt · Böl · Sil, sağ üstte Çoklu seçim. Videoda altında **Klibin sesi** kaydırıcısı (%0–150; %0 = kapalı). Böl: oynatma çizgisi seçili klibin dışındaysa klibi ortasından böler.
- Klip katmanında sesi kapalı (kırmızı) ya da kısık videoda sağ altta ses simgesi.
- Eski "Sıra" ara başlığı (Sola al · Sağa al · Çoğalt · Kaldır) bu sekmede gizlendi; işlevleri çubukta. Kayıt biçimi değişmedi (`c.vol`, `c.mute` zaten vardı).
- Kod: `kaynak/video-sira.js` (Kareden Videoya kapanışının içine eklenir), `esitle.py` içinde iki küçük kanca (sekme birleştirme, `st-sk` sınıfı), `kaynak/video-arayuz.js` (bu sekmede sayfalama kapalı).
- Test (sunucuda, 412×915): 2 video + 2 fotoğraf; şeritten ses kapat, çubuktan %40, seçili olmayan klipte sil (önce seçti, sonra sildi), çoğalt, katman simgeleri, diğer sekmelerde şerit/çubuğun gizlenmesi. Hata yok. Telefonda denenmedi.

## 2.6 (06.11.2026): Nesne paneli sade, önizleme büyük
- Video editörü › Nesne paneli bölümlere ayrıldı: **Ayar · Efekt · Arka plan · Cam fırçası · Diğer**. Her seferinde yalnız seçili bölüm görünür; panel yaklaşık 650 px'ten 270 px'e indi, önizleme artık panelin altında kalmıyor.
- **▾ Paneli küçült:** yalnız başlık satırı kalır, önizleme ekranın ortasına iner. ▴ ile geri açılır.
- **Cam fırçası** açılınca nesne listesi gizlenir; panelde yalnız fırça düğmeleri ve boyut kaydırıcısı kalır.
- **Parmakla düzenleme:** panel açıkken önizlemenin herhangi bir yerinde sürükleyince seçili nesne taşınır (nesnenin tam üstüne basmak gerekmez). İki parmakla büyüt, küçült, döndür; **çift dokun: ortala**. Panel açıkken tarayıcının sayfa kaydırma/yakınlaştırması önizlemede kapalı, iki parmak hareketi nesneye gider.
- Son açılan bölüm hatırlanır. Kayıt biçimi değişmedi.
- Kod: `kaynak/video-nesne.js`.

## 2.5 (06.11.2026): cam fırçası videoda da
- Video editörü › Nesne panelinde seçili nesnede **Cam fırçası** var. Videodan kesilen kişi/nesne ile fotoğraftan eklenen nesnenin ikisinde de çalışır.
- Açıkken önizlemede nesnenin üstünden fırçayla geçilir. Fırça boyu %2–25; nesneyi sürüklemek için fırçayı kapat.
- Fırçalanan yerde arkadaki görüntü silinir, nötr cam tonu kalır. Opaklık %10'a iner; yeni arka plan camın içinden görünür.
- Parlamalar her karenin kendi ışığından hesaplanır, bu yüzden videoda kıpırdayan ışık doğal kalır.
- Harita nesnenin kendi kutusundadır (384×384). Nesne taşınır, büyütülür ya da döndürülürse fırçalanan yer onunla gider.
- Sabit duran bardakta tek fırçalama bütün karelere uygulanır. Bardak hareket ederse fırçalanan yer onu izlemez; bu durumda nesnenin kutusuna göre kalır.
- **Fırçayı geri al** (15 adım) ve **Cam fırçasını temizle** düğmeleri var. Harita projeyle saklanır (`_c`).
- Önizleme ve dışa aktarma aynı.
- Test: çay bardağı fotoğrafı videoya nesne olarak eklendi. Üst kısım fırçalanınca arkadaki çiçek ve yeşillik camın içinden göründü, parlamalar kaldı. Fırçayı geri al son darbeyi kaldırdı, kayıt doğrulandı. Önceki Nesne testleri tekrarlandı.

## 2.4 (06.11.2026): cam fırçası
- Fotoğraf editörü › Kes'te 5. araç: **Cam** (fırça).
- Camın arkasından manzara görünen kısmının üstünden geçilince o bölge cam sayılır:
  - Arkadaki görüntü silinir; renk nötr açık cam tonuna çekilir.
  - Parlamalar ve kenar çizgileri kalır (yerel ortalamadan belirgin aydınlık, beyazımsı pikseller). Geri kalanın opaklığı %10'a iner.
  - Böylece yeni arka plan camın içinden görünür.
- Yalnız azaltır: nesne olmayan yere etki etmez. Fazla gidilen yer **Koru** ile geri gelir.
- Geri al / ileri al / ↺ cam fırçasını da kapsar (alfa ve cam haritası birlikte saklanır).
- Kaydetme, ayrı nesne PNG'leri ve arka plan seçeneklerinin hepsi cam fırçasını uygular.
- Otomatik "Saydam nesne" modu (2.3) ayrıca durur. Camın arkası çevresine benziyorsa iş görür; benzemiyorsa (ağaç, eşya) cam fırçası kullanılır.
- Test: kullanıcının gönderdiği çay bardağı fotoğrafında, bardağın üstünde görünen ağaçlar fırçayla silindi. Gökyüzü arka planı camın içinden göründü, kenar parlamaları kaldı. Kenar, geri al, karşılaştır, ayrı nesne testleri tekrarlandı.
- Videoda cam fırçası 2.5'te eklendi.

## 2.3 (06.11.2026): saydam nesne (cam, bardak) modu
- **Sorun:** Yapay zekâ nesneyi "var / yok" diye ayırır; camın yarı saydam olduğunu bilmez. Camın içinden görünen eski arka plan kesilen parçada kalıyordu ve yeni arka plan camın içinden görünmüyordu.
- **Çözüm (ortak motor `saydamlastir`):**
  - Nesnenin arkasındaki temiz arka plan, çevredeki arka plan piksellerinden tahmin edilir; düşük çözünürlükte içe doğru doldurulur.
  - Nesnenin bu tahmine benzeyen pikselleri (camın boş kısmı) saydamlaşır. Farklı olanlar (çay, kenar, parlama) kalır.
  - Fotoğrafta camdan geçen arka plan rengi pikselden ayrıştırılır, böylece yeni arka plan camın içinden görünür.
  - Yalnız istenince açılır. Her zaman açık olsaydı arka plana benzeyen opak nesneleri (beyaz duvar önünde beyaz gömlek) yanlışlıkla saydam yapardı.
- **Fotoğraf editörü › Kes › Kenar · cam:**
  - **Saydam nesne (cam, bardak)** düğmesi ve **Saydamlık** kaydırıcısı (10–100).
  - Geri al, ileri al ya da sıfırla cam modunu kapatır.
- **Video editörü › Nesne panelinde "Yeni kesimlerde saydam nesne (cam, bardak)"** (açık/kapalı, hatırlanır):
  - Fotoğraftan ekleme, fotoğraf klibinden ve video klibinden kesme bu ayarla yapılır.
  - Videoda her karede uygulanır; renk ayrıştırması yapılmaz, yalnız saydamlık.
- **Test:**
  - Birim: yapay bardakta camın içi opaklığı %100 → %34; çay ve kenar tam; siyah zeminde camın içi karardı.
  - Uygulama: bardağın üstü opak olarak işaretlendiğinde cam modu opaklığı 255 → 46 (Saydamlık 90'da 23) yaptı; çay 254 kaldı. Fotoğraf arka planlı JPG indirildi.
  - Önceki fotoğraf ve video testleri tekrarlandı.
- **Sınır:** Camın arkasındaki arka plan, çevresindekinden çok farklıysa (camın arkasında ayrı bir nesne ya da gökyüzü parçası) o kısım kalır. Fotoğrafta **Sil** fırçasıyla giderilir; videoda fırça yok.

## 2.2 (06.11.2026): videoda kişi takibi editörde, arka plan bulanıklaştırmada yapay zekâ
- **Video editörü › video klip › Kişiyi kes:**
  - Video klipte de çalışır. Klibin kullanılan bölümü 10 kare/sn hızında kare kare kesilir.
  - İlk karedeki en büyük nesne izlenir. Takip yöntemi test sayfasıyla aynıdır: örtüşme, uzak parça eleme, yumuşatma, kenar hafif daraltma.
  - İlerleme ve kalan süre gösterilir. **İptal** edilirse o ana kadar işlenen kısım kullanılır; işlenmemiş kısımda kişi gösterilmez.
  - Maskeler projeyle saklanır (384×384, `studyo-nesne` veritabanında `_m`). Önizleme ve dışa aktarma modele ihtiyaç duymaz: her karede klibin canlı görüntüsü o anın maskesiyle kesilir.
  - Arkası varsayılan olarak aynı videonun bulanık hâlidir; Nesne panelinden fotoğraf da konabilir ya da kapatılabilir. Kişi taşınır, büyütülür, döndürülür.
  - En uzun 60 sn işlenir (600 kare).
  - Telefonda beklenen süre: kare başına ~1,1 sn (test sayfası ölçümü). 10 sn'lik klip yaklaşık 2 dakika sürer.
- **Fotoğraf editörü › Efektler › Akıllı bulanıklaştırma:**
  - "Ana nesneyi otomatik seç" artık yapay zekâ (ISNet) maskesiyle seçer. Eskiden renk, netlik ve yüz ipuçlarıyla tahmin ediyordu.
  - Seçim aynı biçimde kalır: fırçayla ekle/çıkar, karınca çizgisi, "Nerede bulanıklaşsın" seçenekleri.
  - "Nesneyi çerçevele" eski yolla çalışır. Yapay zekâ yüklenemezse eski yönteme döner.
- Test (sunucu, grafik birimi yok, deneme için 1 kare/sn):
  - 4 sn'lik videoda 5 kare 69 sn'de işlendi.
  - Önizlemede ve 1920×1080 dışa aktarmada nesne net, arkası aynı videonun bulanık hâli.
  - Veritabanı kaydı doğrulandı.
  - Fotoğrafta akıllı bulanıklaştırma: seçim yapay zekâ ile (%63 alan), saç kenarı net, arka plan bulanık.
- Sınır: video klipte izlenecek nesne seçilemiyor; ilk karedeki en büyük nesne alınıyor. Dokunarak seçme sonraki adım.
- Motor: `yz/motor.js`'e ortak `takipci` (video takibi) eklendi. Video editörü bunu kullanır.
- Kareden Videoya kopyasına kanca: nesne çizimine klibin kare kaynağı (`getSrc`) geçirilir; `__kv`'ye `klipKaynak` ve `klipAn` (klibin kaynak zamanı) eklendi.

## Video takip testi (06.11.2026): `yz/video.html`
**Adres:** https://aaskinkk-cloud.github.io/studyo/yz/video.html
- Amaç: videoda nesne takibini editöre eklemeden önce telefonda hızını ve kararlılığını ölçmek.
- Yöntem (gerçek model, kare kare):
  1. Her kare ISNet ile kesilir.
  2. İlk karede dokunulan nesne, sonraki karelerde önceki maskeyle örtüşen parçalar seçilerek izlenir.
  3. Nesne kaybolursa son bilinen yeri tutulur, geri gelince yeniden yakalanır.
  4. Küçük kare-kare farkları yumuşatılır (titreme azalır); büyük değişim (hareket) aynen alınır.
  5. Aradaki kareler komşu iki maskeden tamamlanır.
- Ayarlar: 5 / 10 / 15 kare/sn; ilk 5 / 10 / 20 sn.
- Ölçümler:
  - çalışan motor, kare başına süre, toplam süre,
  - 1 dakikalık video tahmini,
  - nesnenin bulunamadığı kare sayısı,
  - kareler arası kararlılık (IoU).
- Sonuç oynatılır; zemin dama / yeşil / siyah / maske / orijinal seçilebilir, yavaş oynatma var.
- Motor: `yz/motor.js`'e çok kareli `oturum` eklendi; model bir kez yüklenir, iş bitince kapatılır. Kesme fonksiyonu aynı yükleyiciyi kullanır.
- SAM 2.1 kullanılmadı: video bellek yapısı ve boyutuyla telefon tarayıcısında gerçekçi değil; bilgisayar ya da ücretli sunucu gerektirir.
- **Telefon ölçümü (06.11.2026, S25 Ultra, 1080×1920, 15 sn, 10 kare/sn):** grafik birimi; kare başına 1,1 sn; 101 kare 2 dk 21 sn; nesne kaybı 0/101; kararlılık %95. Kullanıcı gözlemi: kopma yok, ara sıra arka plan sızıyor.
- **Sızma düzeltmesi (aynı gün):**
  - Ara anlarda iki maske, kesişime yakın birleştirilir. Eski konumda yarı saydam iz kalmaz.
  - Boşalan yer hemen boşalır; yumuşatma yalnız iki karede de dolu olan yerlerde uygulanır.
  - Önceki maskeden çok uzaktaki parçalar (kişiye yapışık arka plan) atılır. Kare başına hareket payı yaklaşık %4 ekrandır. Sıçrayan yanlış eşleşmeler "kayıp" sayılır.
  - "Kenar: olduğu gibi / hafif daralt (varsayılan) / daralt" ayarı eklendi.
- Sunucu testi (grafik birimi yok): 4 karelik deneme videosunda kare başına 12,8 sn (işlemci), nesne kaybı 0/4, kararlılık %94. Telefonda grafik birimiyle çok daha hızlı olması beklenir; asıl ölçüm telefonda.

## 2.1 (06.11.2026): ⋯ menüsü panelin altında kalmıyor
- Video editöründe önizlemenin altındaki **⋯** (Diğer) menüsünde Klasik görünüm, Tema, Yeni video ve Basit mod var. Bu menü, bir bölüm paneli açıkken panelin arkasında kalıyordu.
- Neden: menü önizleme bölümünün içinde duruyordu ve o bölüm paneli üstte çiziyordu.
- Çözüm: menü sayfanın en üst katmanına taşındı. Konumu, düğmeleri ve davranışı aynı kaldı.
- Test: Klip paneli açıkken menüdeki 4 düğmenin de üstte olduğu doğrulandı.

## 2.0 (06.11.2026): talimattaki eksik küçük maddeler (kesme kalitesi ve kontroller)
Yapay zekâ talimatı madde madde yeniden denetlendi; takip, sanatsal geçişler ve saydam video dışındaki eksikler tamamlandı.
- **Ortak motor (`yz/motor.js`), fotoğraf ve video aynı işlevleri kullanır:**
  - `parcalar`: maskedeki ayrı nesneleri bulur. Kırıntılar elenir, en çok 8–12 nesne, büyükten küçüğe. Yumuşak kenar pikselleri en yakın nesneye katılır.
  - `incelt`: kenarı daraltır / genişletir ve yumuşatır.
  - `renkTemizle`: yarı saydam kenar piksellerinin rengini nesnenin kendi rengine çeker. Eski arka planın rengi yeni bir arka plan önünde hale ya da kontur bırakmaz.
  - Kesme sonucu (video Nesne) bu temizlikten geçer.
- **Fotoğraf editörü › Kes:**
  - **Kenar** aracı (4. araç): "Daralt ⟷ Genişlet" ve "Yumuşaklık". Daraltma arka plan kalıntısını yer, genişletme kesik kenarı geri verir. Geri alınabilir.
  - **Kenar rengini temizle** (açık gelir): kaydederken uygulanır.
  - **Karşılaştır** (üstte göz): basılı tutunca orijinal fotoğraf, bırakınca kesilmiş hâl.
  - Kaydet sayfasında **Ayrı nesneler (N)**: fotoğrafta birden çok nesne kaldıysa küçük resimleri görünür. Dokunulan nesne ayrı saydam PNG olarak iner; "Her nesneyi ayrı saydam PNG indir" ile hepsi iner.
- **Video editörü › Nesne:**
  - Fotoğrafta birden çok nesne bulunursa sorulur: **N ayrı katman** ya da **Tek katman**.
  - Ayrı katmanlar fotoğraftaki yerlerine göre dizilir. Her biri ayrı taşınır, büyütülür, döndürülür, zamanlanır.
  - **Geri al / İleri al** (panel başlığında, 30 adım): konum, boyut, açı, görünürlük, hareket, zaman, arka plan, gölge/ayna/gizle, ekleme ve silme. Sürükleme ve kaydırıcı hareketi tek adım sayılır.
  - Silinen nesnenin görüntüsü panel kapanınca veritabanından silinir; o zamana kadar geri alınabilir.
- **Test:**
  - Birim testi: iki kare ve bir kırıntı → iki nesne. Daraltma/genişletme piksel sayısı. Kenardaki yeşil taşma nesnenin rengine çekildi.
  - Fotoğraf: kenar daralt/genişlet/sıfır (piksel sayısı birebir geri geldi), geri al, karşılaştır, iki nesneli fotoğrafta 2 ayrı PNG ve tüm PNG.
  - Video: iki nesneli fotoğraftan "2 ayrı katman", hareket değiştir → geri al, sil → geri al → ileri al, panel kapanınca görüntü temizliği.
  - 1.9 testleri tekrarlandı; bütün sayfa betiklerinde sözdizimi hatası yok.
- **Hâlâ yok:**
  - Videoda takip.
  - Sanatsal geçişler.
  - Saydam video çıktısı.
  - Nesnelerin zaman çizelgesi satırı.
  - İnsan için özel matting modeli.
  - Kuş tüyü ve dal testleri: gerçek fotoğrafla yapılmadı.

## 1.9 (06.11.2026): kesilen nesnenin arkasına fotoğraf
- **Fotoğraf editörü › Kes › Bitti:**
  - Arka plan seçeneklerine **Fotoğraf** eklendi; telefondan bir fotoğraf seçilir. Tekrar dokununca "değiştir".
  - Seçilen fotoğraf çıktının tuvali olur (uzun kenar en çok 2048 px). Nesne onun üstüne konur.
  - Önizlemede nesne sürüklenir; iki parmakla ya da "Nesne boyu" kaydırıcısıyla (%20–250) büyütülür.
  - Asıl fotoğrafın altına değen nesne (insan, gövde) yeni fotoğrafın da altına oturur; havada kesik kenar kalmaz.
  - Bütün arka plan seçeneklerinde Kaydet sayfasında küçük bir **önizleme** var (çıktının aynısı).
  - Çıktı: JPG indir ya da Kitaplığa yeni fotoğraf olarak ekle.
- **Video editörü › Nesne:**
  - Her nesnede arka plan sırası: **Arka plan yok · Bulanık** (yalnız klipten kesilende) **· Arkaya fotoğraf**.
  - Seçilen fotoğraf ekranı doldurur (uzun kenar en çok 1920 px), nesnenin görünür olduğu süre boyunca arkasında durur. Girişte ve çıkışta yumuşak geçiş var.
  - Nesne silinince arka plan fotoğrafı da silinir.
- Düzeltme: Kaydet sayfası uzun olunca yukarıdan taşıyordu; artık kendi içinde kayar.
- Test:
  - Fotoğrafta arka fotoğraf seçme, sürükleme, boyut ve JPG indirme denendi.
  - Videoda arka fotoğraf seçme, kayıt (veritabanında `_af` görüntüsü), önizleme ve "Arka plan yok"a dönüş denendi.
  - Önceki kesme, nesne, kayıt ve yeniden açma testleri tekrarlandı.

## 1.8 (06.11.2026): fotoğraf editöründe yapay zekâ ile nesne kesme
- Fotoğraf editörünün araç çubuğunda yeni araç: **Kes** (Filtreler'in yanında).
- Kesilen görüntü **düzenlenmiş fotoğraftır**: ışık, renk, filtre ve kırpma dahil. Karanlık Oda'nın kendi tam çözünürlük çizimi kullanılır; uzun kenar en çok 2048 px.
- Kesme ayrı bir ekranda açılır:
  - **Dokun, seç:** tutmak istediğin nesneye dokun. Yalnız o nesne kalır, fotoğraftaki diğer ayrık parçalar silinir.
  - **Koru** (fırça): yanlışlıkla silinen yeri geri getirir.
  - **Sil** (fırça): kalmasın istediğin yeri kaldırır.
  - Fırça boyu %1–20 arası. "Yumuşak fırça" açıkken kenar geçişi yumuşak olur.
  - İki parmakla yakınlaştırma ve kaydırma; ince kenar işi için.
  - Geri al / İleri al (12 adım), ↺ yapay zekânın ilk sonucuna dönüş.
  - Zemin: Dama / Siyah / Beyaz / Yeşil. Kenar hatası farklı zeminde daha kolay görülür. "Maskeyi göster": siyah-beyaz maske.
- **Bitti → Kaydet:**
  - Arka plan: Saydam, Beyaz, Siyah, Bulanık (aynı fotoğrafın bulanık hâli) ya da Renk (renk seçici).
  - "Boşlukları kırp": nesnenin çevresi kırpılır (açık gelir).
  - **Saydam PNG indir** ya da **JPG indir**.
  - **Kitaplığa yeni fotoğraf olarak ekle:** sonuç yeni bir fotoğraf olarak açılır, Karanlık Oda araçlarıyla düzenlemeye devam edilir. Asıl fotoğraf ve düzenlemesi değişmez. Saydam seçiliyse saydam yerler beyaz olur.
- Geri tuşu önce Kaydet sayfasını, sonra kesme ekranını kapatır.
- Grafik birimi: kesme ekranı yalnız 2B tuval kullanır. Tam çözünürlük çizimi için açılan geçici grafik bağlamı, yapay zekâ başlamadan kapatılır. Model işi bitince bellekten çıkar.
- Kod: `kaynak/foto-nesne.js`. `yz/motor.js`'e tam boy maske seçeneği (`ham`) eklendi.
- Test (sunucuda, grafik birimi yok):
  - Kesme işlemciyle 19 sn sürdü (model indirme dahil).
  - Dokun-seç, sil fırçası, geri al (piksel sayısı birebir geri geldi) ve koru fırçası denendi.
  - Saydam PNG ve bulanık arka planlı JPG indirildi.
  - Kitaplığa ekleme sonrası yeni fotoğraf editörde açıldı.
  - Video Nesne aracının testleri tekrarlandı.
  - Bütün sayfa betiklerinin sözdizimi denetlendi.

## 1.7 (06.11.2026): seçili klipteki kişiyi kes
- Zaman çizelgesinde bir fotoğraf klibine dokununca açılan araç çubuğunda yeni düğme: **Kişiyi kes** (Düzenle'nin yanında). Aynı iş Nesne panelinde ✂ düğmesiyle de yapılır.
- Ne yapar:
  - Klibin fotoğrafındaki kişiyi ya da nesneyi yapay zekâ ile keser.
  - Klibin arkasını aynı fotoğrafın **bulanık hâliyle** doldurur. Dikey fotoğrafın çerçevesi ve kenar çizgileri kaybolur.
  - Kişi tam görünür: yükseklik ekranın %85'i, yatayda klipteki yerine yakın. Fotoğrafın altına değiyorsa ekranın altına oturur. Hafif gölge açık.
- Nesne **kendi klibine bağlıdır.** Klip taşınır, kısaltılır ya da uzatılırsa birlikte gider. Klip silinirse görünmez.
- Klip geçişlerinde yumuşakça belirir ve kaybolur.
- Nesne panelinde bu tür nesnede Başlangıç/Bitiş yerine **Bulanık arka plan** (aç/kapat) var. Kapatınca kişi klibin kendi görüntüsünün üstünde kalır.
- Sürükleme, iki parmakla boyut ve açı, Gölge, Ayna, Gizle, PNG kaydet, Sil: diğer nesnelerle aynı.
- Bir klipten bir kez kesilir. Yeniden kesmek için önce mevcut nesneyi sil.
- Video kliplerde çalışmaz; uyarı verir. Videoda kişiyi kare kare takip ederek kesme sonraki adım.
- Düzeltme: Stüdyo'nun `window.__kv` erişimi Kareden Videoya'nın kendi `__kv` nesnesinin üstüne yazılıyordu; artık birleştiriliyor.
- Test: iki fotoğraflı projede klip seçip kesme (işlemciyle 16–18 sn), önizleme karşılaştırması, 1920×1080 dışa aktarma (geçiş anı dahil). 1.6'nın serbest nesne, kayıt ve yeniden açma testleri tekrarlandı.

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
  - Nesne katmanlarının zaman çizelgesinde ayrı satırı.
  - (Seçili fotoğraf klibinden kesme 1.7'de eklendi.)

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
Kolaj iptal edildi. Yapay zekâ: videoya nesne yerleştirme (1.6), klipten kişi kesme (1.7), fotoğraf editöründe kesme (1.8) tamam. Sırada: videoda hareketli kişiyi takip, sonra nesne kenarından sanatsal geçişler.
