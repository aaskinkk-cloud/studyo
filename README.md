# Stüdyo

Fotoğraf ve video düzenleyici. **Karanlık Oda** ile **Kareden Videoya**'yı tek uygulamada birleştirir. Tasarımda InShot'un sade düzeni örnek alındı. Tarayıcıda çalışır (PWA). Dosyalar telefondan dışarı gönderilmez.

**Güncel sürüm:** Android uygulaması 4.34 (yerel, deneme) · önceki not: 3.1 (yerel video editörü) · 13.11.2026. Web: 2.16 · 08.11.2026 (Karanlık Oda 6.67, Kareden Videoya 9.15).

**Android APK:** https://github.com/aaskinkk-cloud/studyo/releases/download/android/studyo.apk

## 4.34 (27.11.2026, deneme · `yerel-android` dalı): logo (talep 6)
- Logo kütüphanesinde **Boş**: ana logo kaldırılır, video boyunca sol üstte logo görünmez.
- Varsayılan logo boyutu **%12**, saydamlık **%60**.
- Son logo ayarları (yer, boyut, saydamlık, serbest konum, yuvarlak) her değişiklikte saklanır; yeni projelerde ve Varsayılan ayarlarda bunlar kullanılır.
- **Büyük logo geçişi** Varsayılan ayarlar maddelerine eklendi (Logo geçişi · baş/orta/son; "Ayarla ›" → Efekt › Geçiş).

## 4.33 (27.11.2026, deneme · `yerel-android` dalı): filtre çökmesi — asıl kök neden
- 4.29'daki üst üste filtre kodu ("a:0.80|b:0.15") her piksel için yeniden ayrıştırılıyordu: tam boy fotoğrafta (ör. 2880×3840 ≈ 11 milyon piksel) on milyonlarca geçici nesne → bellek/işlemci tıkanıp uygulama kapanıyordu (kayıtta hata satırı olmadan). Artık kod bir kez çözülür, piksel döngüsünde nesne oluşmaz (`RenkLut.filtreListe`).

## 4.32 (26.11.2026, deneme · `yerel-android` dalı): filtre çökmesi / renk görünmemesi, nesne kopyası
- **Kök neden:** renk/filtre önbelleği Media3 `SingleColorLut` nesnesini saklıyordu; nesne kendi GL dokusunu tutar ve kompozisyon kapanınca doku silinir. Ayar değişince kurulan yeni kompozisyon aynı nesneyi silinmiş dokuyla kullanıyordu → oynatınca çökme ya da büyük oynatıcıda renk/filtre görünmemesi. Artık yalnız LUT küpü (sayılar) saklanıyor, her seferinde yeni LUT nesnesi.
- **Kesilen nesne kopyası:** "Kopyala (önüne)" — aynı zamanda, bir üst katmanda, hafif kaydırılmış (çizelge menüsü ve Kesilenler).
- 4.31'deki geçiş düzeltmesi (fotoğrafa fazladan renk çevirisi yok) kalıcı; geçiş kodunda kök neden açıklamasıyla işaretli.

## 4.31 (26.11.2026, deneme · `yerel-android` dalı): geçiş sonu aydınlanması — kök neden giderildi
- **Ölçüm (Ahmet'in kaydı):** geçişin son karesi Y=65 → gelen fotoğrafın kendi ilk karesi Y=80 (ikinci geçiş 142 → 151). 80'e sRGB→SMPTE 170M çevirisi uygulanınca tam 65 çıkıyor (151 → 140 ≈ 142). Yani Media3 bu hatta fotoğrafı çevirmeden çiziyor, geçiş kodu (TekTuval) çeviriyordu; gelen fotoğraf geçiş boyunca ~%20 koyu kalıyor, el değiştirince aydınlanıyordu.
- **Düzeltme:** geçiş kodundaki fotoğraf renk çevirisi kaldırıldı (`uSrgb = 0`). Işık ölçümü varsayılan kapalı (Ayarlar'dan açılabilir).

## 4.30 (26.11.2026, deneme · `yerel-android` dalı): geçiş sonu aydınlanması — ölçüm
- Ahmet'in denemesi: boş projede 2 fotoğraf + Erime'de de oluyor → sorun üst üste binen geçişin "el değiştirme" anında (gelen fotoğrafı geçişte bizim kod, sonra Media3 çiziyor). Renk hesabı Media3 1.8.1 kaynağıyla karşılaştırıldı, aynı; kör yama yapılmadı.
- **Teşhis:** her öğenin ilk ve son 350 ms'sinde karenin ortasının parlaklığı Hata kaydına yazılır (`IsikOlcer`, görüntüyü değiştirmez). Geçişin son karesi ile gelen klibin ilk karesi karşılaştırılıp kök neden bulunacak. Ayarlar › Menü görünümü'nden kapatılabilir; sorun çözülünce kaldırılacak.

## 4.29 (26.11.2026, deneme · `yerel-android` dalı): Nesne ana menüsü, nesneler Katmanlar'da, üst üste filtreler, Orange & Teal
- **Nesne ayrı ana menü** (Fotoğraftan · Videodan · Kesilenler); Düzenle ve Katman'daki kopyaları kaldırıldı — tek yer.
- **Kesilen nesneler Katmanlar çizelgesinde:** her biri kendi satırında (fotoğraf/hareketli). Dokun = seç (önizlemede çerçeve) · seçiliyken sürükle = taşı (başka klibin üstüne de; o klibe bağlanır) · uçtan sürükle = kısalt/uzat · uzun bas = menü.
- **Devre dışı bırak / Etkinleştir · Kesildiği klibe geri gönder · Ekrana sabitle** (fotoğrafın hareketini izlemez, video boyunca aynı noktada) — çizelge menüsünde ve Kesilenler'de. Başka klibe taşınan fotoğraf nesnesi ekrandaki yerinde kalır; "geri gönder" fotoğraf bağını geri getirir.
- **Filtreler üst üste:** her filtre ayrı güçte; yeni filtre öncekini silmez (dokun = ekle/çıkar, her etkin filtre için ayrı Güç). Önizleme, akıcı önizleme ve dışa aktarma aynı tek LUT kodunu kullanır (`Klip.filtreKod`).
- **Orange & Teal** filtresi: gölgeler camgöbeği/mavi, ten ve aydınlıklar sıcak turuncu (ten bölgesi maskeli), hafif S eğrisi.

## 4.28 (23.11.2026, deneme · `yerel-android` dalı): sistem satırı sade, tema listesinde ✓
- Editörün üst satırı: Geri yalnız simge; "Ekran açık" yalnız soluk simge; arama ve ☰ Ayarlar küçük soluk simgeler olarak buraya taşındı (alttaki menüden kalktı).
- Ana sayfa ⋯ listesinde bütün temalar görünür, geçerli tema ✓ ve tema renginde.

## 4.27 (23.11.2026, deneme · `yerel-android` dalı): gruplu duraklar gerçekten grup
- **Kök neden:** grup durağında da turun ek yaklaşması uygulanıyor, sığdırma payı dar olduğu için uçtaki yüzler kadraj dışına düşüyordu (tek tek yaklaşıyor gibi). Grup durağı artık %15 payla sığar, ek yaklaşma yok (`OdakNoktasi.grup`); grupta 3 sn, tek kişide 2 sn.
- **Otomatik gruplama:** 1–3 kişi tek durak · 4 → 2+2 · 5 → 3+2 · 6 → 3+3 · 7 → 3+4 · 8 → 3+3+2 · sonrası üçer (artan 1 son gruba, artan 2 ayrı durak).
- **Tümüne (✓✓):** son seçilen gruplama yüzleri bulunmuş bütün fotoğraflara uygulanır. Var olan ayarlar değişmez; yalnız düğmeye basınca uygulanır.

## 4.26 (23.11.2026, deneme · `yerel-android` dalı): tema rengi her temada, ana sayfa kartları
- **Tema rengi (kök neden):** seçilen renk yalnız InShot temasında kullanılıyordu; diğer temalarda tıklamak hiçbir şey değiştirmiyordu. Artık her temada geçerli (menüler, onay işaretleri, oynatıcı, çerçeveler); "Tema" seçeneği temanın kendi rengine döndürür.
- **Ana sayfa proje kartları:** sabit küçük kapak (96×54), ad / klip·süre·biçim / tarih tek satırda (taşarsa …), sağda dar simge sütunu (TV üstte, menü altta) — yazılar artık kelime kelime alt alta kırılmıyor.

## 4.25 (23.11.2026, deneme · `yerel-android` dalı): Ayarlar sabit düğme
- Ayarlar sayfası (Yayın › Ayarlar: tema rengi, menü boyutu ve görünümü, oynatıcı, hızlı düğmeler, yedek) hiç silinmemişti; Yayın ana menüde yana kaydırınca görünüyordu. Artık alttaki menünün sağında, aramanın yanında sabit ☰ Ayarlar düğmesi.

## 4.24 (23.11.2026, deneme · `yerel-android` dalı): 4.20 + sonraki istekler
- Temel 4.20 (4.23 ile aynı) ve üstüne 4.21'deki istekler: Ayarlar › Menü görünümü › **Renkli** (InShot gibi renkli yazılı alt başlıklar, tek düzende — hiçbir özellik gizlenmez), "Ekran açık" simgesi kaldırıldı.
- Eski ayrı "yazılı" düzen yok; eski seçim açılışta bir kez Renkli görünüme çevrilir.

## 4.23 (23.11.2026, deneme · `yerel-android` dalı): 4.20'ye geri dönüş (Ahmet'in isteği)
- Kod 4.20 ile aynı (4.21 ve 4.22 geri alındı: Renkli menü görünümü, "Ekran açık" simgesinin kaldırılması, eski düzeni renkli görünüme çevirme).
- Menüleri bozan eski "Alt başlıklar: yazılı (önceki)" seçimi açık kaldıysa açılışta bir kez kapatılır; ana sayfadaki bu seçenek kaldırıldı (eski düzen arama, Yüzler, oynatıcı gibi özellikleri gizliyordu).
- Sürüm numarası telefona üstüne kurulabilsin diye 4.23 (Android eski numaralı sürümü üstüne kurmaz).

## 4.22 (23.11.2026, deneme · `yerel-android` dalı): tek menü düzeni — hiçbir özellik kaybolmaz
- **Kök neden:** ana sayfadaki "Alt başlıklar: yazılı (önceki)" ayrı ve bakımı bırakılmış eski bir ekran düzenine geçiriyordu; 3.70'ten sonraki her şey (arama, Yüzler/Yüz gizle gibi alt sayfalar, oynatıcı, sabit düğmeler) yalnız yeni düzende vardı. Bu seçenek açılınca hepsi kayboluyordu.
- **Kalıcı çözüm:** tek düzen. "Yazılı" seçimi artık yeni düzenin renkli yazılı görünümü (Ayarlar › Menü görünümü › Renkli ile aynı); kayıtlı eski seçim açılışta bir kez buna çevrilir. Görünüm değişir, özellikler aynı kalır.

## 4.21 (23.11.2026, deneme · `yerel-android` dalı): oynatıcı kaybolması, renkli alt başlıklar
- **Kök neden:** yazılı menü seçiliyken siyah-beyaz temada hiçbir oynatıcı çizilmiyordu (oynat dahil). Artık her menü düzeninde var.
- **Ayarlar › Menü görünümü › Renkli:** önceki yazılı renkli alt başlıklar (InShot gibi) yeni düzende ve her temada — alt menüler kendi renginde dolgulu yazılı haplar, seçili tam renk; ana menü ve sayfa düğmeleri yazılı.
- "Ekran açık" yanındaki simge kaldırıldı.

## 4.20 (23.11.2026, deneme · `yerel-android` dalı): Katmanlar çizelgesi — klip ekle, geçiş düğmeleri, katman yap / klip yap, sıra
- Katman'a dokununca önce **Katmanlar** alt sayfası (ilk düğme).
- Çizelgede video satırının başında **+ Klip ekle**; klipler arasında boşluk ve **geçiş düğmesi** (hep görünür; geçiş varsa tema renginde) → dokun = Geçişler penceresi.
- **Katman yap:** seçili fotoğraf klibi üst katman (görsel) olur — PNG saydamlığı korunur, başka klibin üstünde durur. **Klip yap:** görsel katman yeniden fotoğraf klibi olur (başladığı yere, katmanın süresiyle).
- **Üste al / Alta al:** seçili katmanın sırası (üstteki öndedir) araç sırasında.
- Katman bölümünde yazı ekle/düzenle kutusu çizelgenin altında (kliplerin üstünü örtmez).

## 4.19 (23.11.2026, deneme · `yerel-android` dalı): harf düğmeleri, oynatıcı düzenlemeleri
- **Yazı kutusu:** küçük · BÜYÜK · Baş Harf düğmeleri — seçili kısma (seçim yoksa bütün yazıya), Türkçe harf kurallarıyla.
- **Önizleme büyütülünce videonun üstü sabit** (video üstte hizalı; ✕ sil düğmesi de videonun tepesinde kalır).
- **Tema rengi:** oynatma düğmeleri ve kare çizgisi varsayılan olarak tema renginde (alttaki oynatıcı satırı dahil; önceden yalnız ayar açıkken ve yalnız üstteki oynatıcıda). Ayarlar'dan kapatılabilir.
- Videonun üstündeki oynatıcıda düğmeler net görünsün diye altta silik siyah bant.
- **Oynatıcı sade** (Ayarlar › Menü görünümü): Başa · Önceki · Sonraki gizlenir, düğmeler rahat sığar.

## 4.18 (23.11.2026, deneme · `yerel-android` dalı): yazı düzenleme her yerde, hızlı düğmeler gizle/yer, menü görünümü
- **Yazı düzenleme (kök neden):** 4.13'te yazı kutusu Katman sayfasından kalkmıştı; çift dokunuş yazıyı seçiyor ama kutu görünmüyordu. Standart: seçili yazı varken düzenleme kutusu her bölümde önizlemenin hemen altında (Metin › Yazı'da zaten var, tekrarlanmaz); çift dokunuş / ✎ / "Yazıyı düzenle" klavyeyi açar, "Bitti" kapatır.
- **Önizlemedeki hızlı düğmeler:** "Gizle" (sol üstte göz düğmesiyle geri gelir) ve yer: Otomatik · Üstte · Altta · Solda dikey · Sağda dikey (düğmeyle sırayla); hatırlanır. Ayarlar'dan da gizlenebilir.
- **Ayarlar › Menü görünümü:** menü boyutu 5 seçenek (En küçük … En büyük); menü görünümü Simge + yazı · Yalnız simge · Yalnız yazı (ana, alt menü ve sayfa düğmeleri); oynatma düğmeleri ve kare çizgisi tema renginde (seçenek).

## 4.17 (23.11.2026, deneme · `yerel-android` dalı): çizelge dokunuş kuralı, sesi ayır, menü boyutu
- **Çizelge (kalıcı kural, bütün katmanlar):** dokun = yalnız seç (uçlarda kalın tutamaçlar) · seçiliyken sürükle = taşı, uçtan sürükle = kısalt/uzat · uzun bas = menü (başına git, klip/video başı-sonu, başa taşı, ses düzeyi, Sil; basılan uca göre sıralı) · çift dokun = ayar (ses → ses penceresi, yazı → düzenle, diğer → başına git). Müzik / ses kaydı çubuğunda dokununca pencere açılmaz; uzun bas / çift dokun açar.
- **Sesi ayır** (Katman › Ekle): seçili video klibin sesi klibe bağlı ayrı ses katmanı olur, klibin sesi kapanır (geri al çalışır). Dışa aktarmada video kaynaklı ses katmanı `setRemoveVideo` ile.
- **Menü boyutu** (Ayarlar › Menü görünümü): Küçük · Orta (varsayılan, bir tık küçük) · Büyük · Daha büyük — ana menü, alt menü ve sayfa düğmeleri. Alt menü satırının başındaki bölüm rozeti yalnız simge.
- Düzenle'ye dokununca alt menüde önce Klip açılır.

## 4.16 (23.11.2026, deneme · `yerel-android` dalı): ayrı satır varsayılan, Fotoğraftan/Videodan yeniden
- **Menü:** sayfa düğmeleri yine ayrı satırda (4.14 düzeni, varsayılan). Tek satır seçenek: Yayın › Ayarlar › Menü görünümü › "Sayfa düğmeleri tek satırda (alt menünün yanında)".
- **Fotoğraftan / Videodan:** üstte ekrandaki klibin durumu (klip no · fotoğraf/video; uygun değilse ne yapılacağı). İki eşit kutu: "Ekrandaki …" (tema renginde, uygun değilse sönük ve nedenini yazar — artık uyarı penceresi açmaz) · "Galeriden …". Arka plan bulanıklığı ekrandaki fotoğraf klibine uygulanır (önceden seçili klibe uygulanıyordu; ekrandaki klip farklıysa yanlış klip değişiyordu). Videodan: Süre tek kaydırıcı (tahmini kesme süresiyle), ilerleme satırında İptal.
- **Fazlalık:** Katman › Hareketli'deki ikinci "Kesilenler" çipi kaldırıldı (Kesilenler yalnız Nesne kes'te); Hareketli tek sayfa.

## 4.15 (23.11.2026, deneme · `yerel-android` dalı): tek satır alt menü, yeni Kesilenler, nesne oynarken yerinde
- **Tek satır:** yeni menü düzeninde 3. katman sayfaları (ör. Nesne › Fotoğraftan · Videodan · Kesilenler, Şablon › Giriş · Abone · Kapanış + bağlantılar) ayrı satırda değil, alt menü satırında seçili alt menünün hemen yanında hafif zeminli bir grup olarak; satır sağa kayar. Ayar alanı bir satır büyüdü.
- **Nesne oynarken kayıyordu (kök neden):** 4.12'de kesimde fotoğrafın otomatik hareketi kapatılmıyordu, Varsayılan ayarlar da yakınlaşmayı yeniden açıyordu; oynarken fotoğraf yakınlaştıkça yapışık nesne de kayıyordu. Kural (`sabitArkaPlan`, her değişiklikte ve açılışta): üstünde kesilmiş nesne olan fotoğraf klibi hareketsiz → nesne durunca, oynarken ve dışa aktarmada ayarlandığı yerde. Yalnız elle kurulmuş yüz turu varsa arka plan hareket eder, nesne ona yapışık gider.
- **Kesilenler yeniden:** üstte nesne kutucukları (yana kayar, seçili tema renginde çerçeve, hareketli olanda rozet), altta seçili nesnenin düzenli ayarları: süre (Baş = şu an · Son = şu an · Klip boyunca), Boyut, simge satırı (Gölge · Cam fırçası · En üste · Sil, iki dokunuş). Önceki liste: Ayarlar › Menü görünümü › "Kesilenler: önceki liste görünümü".

## 4.14 (23.11.2026, deneme · `yerel-android` dalı): alt menüler üstte, onay işaretleri tema renginde
- **Yeni menü düzeni:** ana menü hep altta ve serbest kayar; seçili bölüm tema renginde (altında çizgi). Bölümün alt menüleri hemen üstünde küçük düğmeler (simge + ad yan yana); satırın başında bölüm adı tema renginde sabit, alt menüler yanında kayar. 3. katman çipleri en üstte. Önceki düzen (alt menüler ana menünün yerinde, solda "‹ Bölüm"): Yayın › Ayarlar › Menü görünümü › "Alt menüler üstte…" kapalı.
- **Onay işaretleri tema renginde:** alt menü/çip/varsayılan ✓ rozetleri ve kart başlıklarındaki ✓✓ (tümüne) / ✓ (bu klip) işaretleri artık Ayarlar › Tema rengi'nde (eskiden sabit yeşil); bütün bölümlerde.

## 4.13 (23.11.2026, deneme · `yerel-android` dalı): Katman alt menüleri, Şablon'da varsayılan bağlantıları
- **Katman alt menüleri:** Ekle · Ses · görünür · Nesne kes · Hareketli · Katmanlar. Çizelge yalnız Katmanlar sayfasında (seçili katmanın ayarlarıyla); Ekle'de Görsel/Logo/Çıkartma/Ses; Ses · görünür = "Ses ve görünürlük · tümü".
- **Tekrar yok:** Nesne kes / Hareketli, Düzenle › Nesne sayfasının kendisini açar (Fotoğraftan / Videodan + Kesilenler); Katman sayfasındaki ayrı nesne bölümü kaldırıldı. Yazı, hazır yazılar ve takip yazısı Metin bölümünde: Katman › Ekle'de zarif bağlantılar ("ad ›"), sayfa Katman'ın içinde açılır, alttaki Katman menüsü yerinde kalır; başlıkta "‹ Katman" ve "Orada aç ›".
- **Görünüm › Şablon:** Giriş · Abone · Kapanış çiplerinin yanında, ince ayırıcıdan sonra Varsayılan ayarlar'ın bu bölümde olmayan maddelerine bağlantılar: Kadraj · Yüze yaklaş · Logo · Müzik.
- Eski yazılı menü düzeninde Katman tek sayfa olarak aynen kalır.

## 4.12 (22.11.2026, deneme · `yerel-android` dalı): kesilen nesne arka plandaki fotoğrafa yapışık
- **Zaman (kök neden):** nesnenin zamanı mutlak zaman çizelgesi ms'siydi; yüzler bulunup öndeki fotoğrafların süresi uzayınca nesne yerinde kalıp başka klibin üstüne düşüyordu. Artık her nesne kendi klibine bağlı (`Nesne.klipId`): klip kayınca birlikte kayar; klibin tamamını kaplayan nesne klip uzayınca/kısalınca onunla uzar/kısalır. Klip silinirse bağ çözülür.
- **Konum (kök neden):** nesne kare üzerinde sabitti, arka plandaki bulanık fotoğraf yüz turu / yakınlaşma ile hareket edince ayrılıyordu. Ekrandaki fotoğraftan kesilen nesne artık fotoğrafın kendi karesinde tutulur (`kx, ky, kw`); her karede fotoğrafın hareketiyle aynı yer ve ölçeği alır (önizleme, akıcı önizleme ve dışa aktarma: `BagliNesneBindirme`, klibe ait → geçişte klip ile birlikte gider). Kesimde klibin hareketi artık kapatılmaz.
- Taşıma/büyütme ve Boyut kaydırıcısı fotoğrafa göre yazılır (boyut = fotoğraf genişliğine oran).
- Eski projeler açılışta bir kez bağlanır: fotoğraftan kesilmiş nesneler en yakın bulanık arka planlı fotoğraf klibine yapışır ve klibin tamamını kaplar; diğerleri başladıkları klibe zaman olarak bağlanır.

## 4.11 (22.11.2026, deneme · `yerel-android` dalı): nesne bırakılan yerde kalır, önizleme boyu korunur, TV ağ taraması
- **Nesne (kök neden):** nesnenin merkezi karenin içine (0…1) kısıtlanıyordu; uzun nesne (ör. boy portresi) aşağı çekilip bırakılınca geri yukarı itiliyordu. Artık nesne yarısı kadar kare dışına taşabilir, bırakılan yerde kalır.
- **Önizleme boyu (kök neden):** tutamaçla verilen boy; tam ekrandan, dışa aktarmadan, kapaktan dönünce ve her açılışta videonun oranına yeniden oturtuluyordu. Artık elle verilen boy korunur (biçim değişirse ya da tutamaca çift dokununca yeniden oturtulur). Üst sınır %75 → %85.
- **TV:** 10 sn'de TV bulunamazsa uygulama Cast kitaplığından bağımsız kendi mDNS (_googlecast._tcp) taramasını yapar: ağda TV görünüyorsa sorun Play Hizmetleri tarafında (telefonu yeniden başlat), görünmüyorsa modem duyuruyu iletmiyor (farklı bant/misafir ağı). Sonuç pencerede ve Kayıt'ta.

## 4.10 (22.11.2026, deneme · `yerel-android` dalı): seçili nesne önizlemede görünür
- **Kök neden:** Kesilenler'de nesneye dokununca nesnenin başına (başlangıç + 1 ms) gidiliyordu; orada giriş solması ~0 olduğu için nesne önizlemede görünmüyordu (0,5 sn gibi kısa nesnelerde hiç tam görünmüyordu). Artık Nesne panelinde seçili nesne, kendi süresi içindeyken ve oynatma dururken tam görünür (çerçeve ve tutamaçlarla). Oynatmada ve dışa aktarmada solma aynen kalır.

## 4.09 (22.11.2026, deneme · `yerel-android` dalı): Nesne çerçevesi ve tutamaçlar, önizlemede klip silme
- **Düzenle › Nesne:** seçili nesnenin çevresinde kesikli çerçeve, 4 köşede büyüt/küçült tutamacı (köşeyi tek parmakla sürükle), ortada taşıma simgesi. Nesneyi sürükle = taşı, iki parmak = büyüt/küçült (önceki gibi). Projede nesne varken arka plan (bulanık zemin dahil) hiç kaymaz. Katman bölümünde de aynı çerçeve.
- **Önizlemede klip silme:** önizlemenin sağ üstünde ✕ (oynatma duruyorken). 1. dokunuş kırmızı "Sil?", 2. dokunuş (3 sn içinde) ekrandaki klibi siler; klip şeridinden de kalkar, geri al ile geri gelir.

## 4.08 (22.11.2026, deneme · `yerel-android` dalı): TV'ye yeniden bağlanma, Varsayılan ayarlarda bölüm bağlantıları
- **Televizyonda izle — yeniden bağlanamama (kök nedenler):** (1) MediaRouter'ın etkin taraması ~30 sn sonra bitiyordu; TV sonradan açılsa da listeye gelmiyordu → TV bulunana kadar 15 sn'de bir yeniden tarama, ayrıca "Yeniden ara" düğmesi. (2) Önceki oturumdan seçili kalan TV rotasına dokunmak hiçbir şey yapmıyordu → pencere açılınca seçili kalan rota bırakılır, dokununca gerekirse bırakılıp yeniden seçilir. (3) Asılı kalan eski oturum kapatılır. 20 sn'de bağlanmazsa uyarı.
- 10 sn'de TV bulunamazsa kontrol listesi ve telefon tarafı teşhis (Wi-Fi'de mi, VPN açık mı, ağ adresi). Tarama/bağlanma adımları Kayıt'a yazılır.
- **Varsayılan ayarlar:** her düğmenin altında "Ayarla ›" bağlantısı ilgili bölümü açar (Kadraj → Düzenle › Klip › Kadraj, Yüze yaklaş → Yüzler, Giriş/Abone/Kapanış → Şablon, Logo → Yayın › Logo, Müzik → Ses › Müzik › Hazır). Açıklamalar 3 satıra sabit, bağlantılar tek hizada.

## 4.07 (22.11.2026, deneme · `yerel-android` dalı): Yüzler düğme satırı, durak süreleri, küçük şeritte klip silme
- Yüzler düğmeleri (Yüzleri bul · Önizle · Temizle · Yakınlaşma yok) tek satıra eşit genişlikte sığar (`YuvarlakSecim` modifier alır).
- **Durak süreleri (kök neden):** klip yalnız uzatılıyor, hiç kısalmıyordu; tur klip süresine yayıldığı için durak azalınca her durak uzuyordu. Artık tur planı değişince klip tam plan süresine ayarlanır (kısalır da).
- **Gruplu duraklarda kalma süresi kişi sayısına göre:** 1 kişi 2 sn · 2–3 kişi 3 sn · 4–5 kişi 4 sn (en çok 5). `OdakNoktasi.bekle` (0 = turun bekleme süresi); bekleme kaydırıcısı oynatılınca duraklar yine ortak süreye döner. Geçiş süresi ayrıca eklenir (Yavaş 2,2 · Orta 1,4 · Hızlı 0,85 sn).
- **Küçük şeritte klip silme:** seçili klibin sağ üstünde ✕ — 1. dokunuş kırmızı "Sil?", 2. dokunuş siler (3 sn içinde; geri al ile geri gelir).

## 4.06 (22.11.2026, deneme · `yerel-android` dalı): Yüzler — gruplu duraklar, seçerek yanaşma, yakınlaşma yok; sade Düzenle düğmesi
- **Duraklarını grupla** (Yüzler): Tek tek · 2'li · 3'lü · 4'lü · 5'li. Yüzler soldan sağa gruplanır; her grup tek durak olur (grubun ortası, grup kadraja sığacak yakınlık). Ör. 15 kişi 5'li → 3 durak.
- **Seçerek yanaş:** "Seçerek yanaş" → numaralara (durak listesinde ya da önizlemede) dokun → "Yalnız bunlar": yalnız seçilen duraklar kalır (ör. 1, 3, 5, 8). Geri al ile eski hâli.
- **Yakınlaşma yok:** aktif fotoğrafta durakları siler ve yaklaşma hareketini kapatır (fotoğraf sabit). Otomatik yüz bulma bu fotoğrafa yeniden tur kurmaz.
- **Sabit Düzenle düğmesi sade:** kutu ve renkli çerçeve kaldırıldı, diğer menülerle aynı ölçüde; yalnız açıkken vurgu renginde, yanında ince ayırıcı. Önceki kutulu görünüm: Yayın › Ayarlar › Menü görünümü.

## 4.05 (22.11.2026, deneme · `yerel-android` dalı): Yüzler — tümüne uygula ve otomatik yüz bulma
- **Düzenle › Klip › Yüzler** başlığında ✓✓ (tümüne) ve ✓ (yalnız bu klip). ✓✓: bu fotoğrafın yüz ayarları (bekleme, geçiş, yaklaşma, ışık/çizgi açık-kapalı ve biçimi, isim kutusu, baş/son tamamı, kavis) bütün fotoğraflara kopyalanır; durağı olmayan fotoğraflarda yüzler bulunup numaralanır (gerekirse fotoğraf süresi uzar). Var olan duraklar korunur.
- **"Yüzleri otomatik bul (bütün fotoğraflar)" anahtarı** (proje ayarı `yuzOto`): açınca bütün fotoğraflarda yüzler bulunur ve yaklaşma turu kendiliğinden kurulur; açık kaldıkça sonradan eklenen fotoğraflarda da. Ayarlar seçili fotoğraftan (yoksa ayarlanmış ilk fotoğraftan) alınır. İlerleme çubuğu gösterilir; silüet ışığı arkadan sırayla hazırlanır.
- ✓✓ / ✓ ayrıca **Süre · klip** (fotoğraf süresi bütün fotoğraflara) ve **Kadraj · klip** sayfalarına eklendi.

## 4.04 (22.11.2026, deneme · `yerel-android` dalı): okuyucu çökmesi düzeltildi
- Kök neden: sherpa-onnx yerel kodu geri çağrıda `invoke([F)Ljava/lang/Integer;` yöntemini arıyor; Kotlin 2.0 lambdaları invokedynamic ile derlediğinden bu yöntem yoktu → yerel kodda bekleyen istisnayla JNI çağrısı → uygulama kapanıyordu (Dinle ve Ekle ikisi de). Geri çağrı gerçek sınıf (`GeriCagri`) oldu.

## 4.03 (22.11.2026, deneme · `yerel-android` dalı): okuyucu "Invalid audio buffer size" düzeltmesi
- Kök neden: Dinle'deki AudioTrack tamponu örnek hızı kadar bayt (22050) veriliyordu; float mono kare 4 bayt, tampon 4'ün katı olmalı. Şimdi en az 0,5 sn ve 4'ün katı.

## 4.02 (22.11.2026, deneme · `yerel-android` dalı): sesli okuyucu (metinden ses, DFKI)
- **Ses › Seslendirme › Okuyucu** (Prompter'ın yanında): metni Okur / Karanlık Oda'daki DFKI sesiyle (Piper `tr_TR-dfki-medium`, Türkçe · erkek) okur. Motor: sherpa-onnx v1.12.40 (Apache-2.0), tamamen telefonda, ücretsiz. Ses modeli ilk kullanımda bir kez GitHub'dan iner (~67 MB, `filesDir/okuyucu`); yerel kitaplıklar (arm64) derlemede Actions tarafından indirilip APK'ya konur (repoya girmez).
- **Ses tonu:** İnce · Normal · Tok · Daha tok (perde, süre değişmez). **Okuma hızı:** 0,8–1,25×. **Eklenirken efekt:** Doğal/Net/Temiz/Radyo/Kalın (varsayılan Tok + Radyo = tok anlatıcı). Seçimler hatırlanır.
- **Dinle:** cümle cümle üretilip hemen çalınır; motor sayfa açılınca önceden yüklenir (bekleme kısalır). **Seslendirme olarak ekle:** okunan ses oynatma çizgisindeki andan itibaren `SesKaydi` olur (WAV); Kayıtlar'daki efekt, hız, kırpma, kaydırma aynen çalışır. Metin Prompter metniyle ortak.
- `SesEfekt`: kendi WAV dosyalarını çözücüsüz okur (`wavOku`), `tonla`/`wavKaydet` okuyucuya açıldı. Menü aramasına "okuyucu / metin oku / tts" eklendi.

## 4.01 (22.11.2026, deneme · `yerel-android` dalı): kelime bazında menü araması, Sabitle kaldırıldı, tema rengi
- **Menü araması kelime bazında** (`AramaPenceresi`): her kelime ayrı aranır, hepsi geçmeli; Türkçe harfler önemsiz (dogal = doğal); son bölümü kelimeyle başlayanlar önce. Sayfa içindeki seçenekler de aranır ve seçilince o bölüm açılır: Renk hazır ayarları (Doğal, Canlı…), Filtreler, Efektler, Geçişler, Giriş stilleri, Ses efektleri, Tema rengi; ek anahtar kelimeler (takip yazısı, büyük logo geçişi, kamera…).
- **Sabitle düğmesi kaldırıldı:** Düzenle hep sabit.
- **Tema rengi** (Yayın › Ayarlar › Tema rengi ve ana sayfa › Ayarlar): InShot temasında turkuaz vurgunun yerine Turkuaz · Mavi · Mor · Yeşil · Turuncu · Kırmızı · Pembe · Sarı · Beyaz (`Renk.vurgu`, "vurguIns"); vurgu üstündeki yazı rengi açık/koyu vurguya göre otomatik.

## 4.00 (22.11.2026, deneme · `yerel-android` dalı): Facebook eklentisi geri alındı
- 3.97–3.99'daki uygulama içi Facebook tarayıcısı kaldırıldı (Ahmet'in kararı): Facebook gömülü tarayıcıya sayfayı boş gönderiyordu (cihaz kaydı: giriş sayfası yükleniyor, içerik yok). `FacebookTarayici.kt`, klip şeridindeki Facebook kutusu ve androidx.webkit bağımlılığı çıkarıldı. Diğer özellikler (Albüm, Kamera, 3.93–3.96) aynen duruyor.

## 3.99 (22.11.2026, deneme · `yerel-android` dalı): Facebook giriş sayfası beyaz kalıyordu — ikinci düzeltme
- **Cihaz kaydı (3.98):** sayfalar yükleniyor ve giriş sayfasına yönleniyordu (`m.facebook.com/login/…refsrc=deprecated`), hata yok; içerik çizilmiyordu.
- **Çözüm:** (1) uygulama içi tarayıcının her istekte gönderdiği `X-Requested-With: app.studyo.android` başlığı kapatıldı (androidx.webkit, WebView 110+) — Facebook gömülü tarayıcıyı bundan tanıyıp formu boş bırakabiliyor; (2) kullanımdan kalkan m.facebook.com yerine www.facebook.com (Fotoğraflarım: /me/photos, Albümlerim: /me/photos_albums).
- **Tanı:** her sayfa yüklenince başlık, görünen yazı miktarı ve form sayısı kayda yazılır ("Facebook yüklendi: … → başlık | yazı=… | form=…").
- **Kaçış yolu:** üstte "Chrome'da" — açık sayfayı Chrome'da açar (oradan kaydedip Paylaş › Stüdyo).

## 3.98 (22.11.2026, deneme · `yerel-android` dalı): Facebook giriş sayfası gelmiyordu — düzeltme
- **Neden (muhtemel, iki):** (1) yalnız facebook.com alan adlarına izin veriliyordu; giriş artık meta.com vb. üzerinden yönlendirdiği için engelleniyor, sayfa boş kalıyordu. (2) Facebook uygulama içi tarayıcıyı kimlikteki "; wv" / "Version/4.0" işaretinden tanıyabiliyor.
- **Çözüm:** bütün http(s) sayfaları içeride açılır (yalnız intent:// fb:// gibi uygulamaya atan bağlantılar engellenir); tarayıcı kimliği Chrome mobil gibi (wv işaretsiz); açılış m.facebook.com ana sayfası (giriş formu).
- Sayfa açılamazsa hata ekranda görünür ("Yeniden dene") ve açılan adresler/hatalar hata kaydına yazılır.

## 3.97 (22.11.2026, deneme · `yerel-android` dalı): Stüdyo içinde Facebook — kendi fotoğraflarından ekleme
- Klip şeridinde **Facebook** kutusu → uygulama içi tarayıcı (`FacebookTarayici.kt`, WebView, m.facebook.com). Giriş bir kez yapılır, çerezler saklanır. Üstte: Kapat · Geri · Fotoğraflarım · Albümlerim · Sayfadakileri seç.
- Seçimi kullanıcı yapar (uygulama sayfaları kendi kendine taramaz): fotoğrafa **basılı tut** = seç/kaldır (açık fotoğraf sayfasında büyük boy); **Sayfadakileri seç** = o an yüklü ≥ 400 px fotoğraflar (srcset'in en büyüğü; küçük simgeler alınmaz).
- **Videoya ekle:** seçilenler uygulama içine (`albumler/facebook`) indirilir ve klip olarak eklenir; daha önce inenler yeniden indirilmez.
- Yalnız Facebook alan adları içeride açılır; Facebook uygulamasına yönlendirmeler (intent://, fb://) engellenir. Resmî arayüz değildir: Facebook sayfa yapısını değiştirirse seçim yolu etkilenebilir; basılı tutma her zaman çalışır.

## 3.96 (22.11.2026, deneme · `yerel-android` dalı): birden fazla Google Fotoğraflar albümü
- Albüm penceresi çok satırlı: her satıra bir bağlantı; "Panodan ekle" panodaki yeni bağlantıyı alt satıra ekler, bulunan albüm sayısı görünür (`AlbumIndir.baglar`). Sayı sınırı yok.
- "N albümü al": albümler sırayla indirilir (ilerleme "Albüm 2/4 · alınıyor 12/30"), hepsi albüm sırasıyla tek seferde projeye eklenir; alınamayan albüm diğerlerini durdurmaz, sonunda hangisinin alınamadığı yazılır. Daha önce indirilenler yeniden indirilmez; Vazgeç'te o ana kadar alınanlar eklenir.
- Düzeltme: seslendirme hız düğmeleri 0,9× · 1,1× · 1,5× gibi gereksiz sıfırsız yazılır.

## 3.95 (22.11.2026, deneme · `yerel-android` dalı): seslendirme hızı, sessizlik kırpma, yer kaydırma
- **Hız** (Ses › Seslendirme › Kayıtlar): 0,75× · 0,9× · 1× · 1,1× · 1,25× · 1,5× — ses tonu korunur (Media3 Sonic, süre değişir, kaydın süresi güncellenir). Efektle birlikte tek işlemde (`SesEfekt.isle(efekt, hiz, kirp)`).
- **Baştaki ve sondaki sessizliği kırp:** tepenin %3'ünün altı, 0,15 sn pay; konuşma zaman çizelgesinde aynı anda kalır (başlangıç kırpılan kadar ileri alınır, `kirpBasMs`; kapatınca geri döner).
- **Yeri:** −0,5 sn / +0,5 sn / Oynatıcının yerine taşı (`sesKaydir`). "Hızı ve kırpmayı bütün kayıtlara uygula".
- Özgün kayıt hiç değişmez (`ozgunMs` saklanır); Doğal + 1× + kırpma kapalı = özgün.

## 3.94 (22.11.2026, deneme · `yerel-android` dalı): Renk › Hazır ayarlara yoğunluk çubuğu
- Hazır ayar seçilince altında yoğunluk çubuğu (%5–200; %100 = hazır ayarın kendisi, "%100'e dön"). Bütün değerler aynı oranla ölçeklenir (`olcekle`); ölçeklenmiş ayar yeniden açılınca hangi hazır ayar olduğu ve oranı tanınır (`oranBul`). Efekt › Filtre/Efekt'te güç çubuğu zaten vardı.

## 3.93 (22.11.2026, deneme · `yerel-android` dalı): kamerayla prompter çekimi, prompter'da basılı tut = dur
- **Kamerayla çek** (`KameraPrompter.kt`, CameraX 1.4.1, ücretsiz): tam ekran kamera (ön/arka), okunacak metin üstte merceğe yakın yarı saydam bantta akar (Ahmet'in seçimi). 3 sn geri sayım → kayıt (FHD, olmazsa HD). Bitince video projeye klip olarak eklenir (uygulama içi dosya, `medya/`) ve kopyası galeriye (Movies/Stüdyo) yazılır. Hız prompter'la ortak.
  - Yerler: Ses › Seslendirme › Prompter › "Kamerayla çek" ve klip şeridinde Albüm'ün yanında "Kamera".
  - İzinler: CAMERA (+ RECORD_AUDIO); kamera zorunlu donanım değil.
- **Metne basılı tut = durur, bırak = kaldığı yerden devam; sürüklerken kayma bekler** (hem kameralı hem sesli prompter).

## 3.92 (21.11.2026, deneme · `yerel-android` dalı): şablon yazılar hareketli ve sesli (Yazı katmanı)
- 3.91'de Yazı katmanındaki şablon yazılar düz (durağan) görünüyordu. Artık kendi hareketiyle gelir (`KatmanCizim.hareketliStil`, kare kare): parçalar sırayla — ilk parça (pop) küçükten büyüyüp hafif taşarak, diğerleri yandan kayarak, saydamdan; giriş 1,3 sn, çıkışta ters yönde.
- **Efekt sesi:** her parça gelirken vuuş/pop, çıkışta vuuş (`KatmanCizim.sesAnlari`); dışa aktarma ve akıcı önizlemede girişin efekt sesleriyle aynı dizide, standart önizlemede ses havuzundan. Katman başına aç/kapat: "Efekt sesi (parçalar gelirken)" (`KatmanOge.efektSes`).
- Giriş başlığındaki şablonlar zaten giriş hareketi ve efekt sesleriyle geliyordu (Görünüm › Şablon › Giriş › Hareket / Efekt sesi).

## 3.91 (21.11.2026, deneme · `yerel-android` dalı): şablon yazılar (giriş + Yazı katmanı ortak), InShot teması temel
- **6 şablon yazı** (Ahmet'in videosundaki stiller; `SablonYazi` / `ReelsCiz`): Beyaz eğik kutu (TODAY) · Ateş balon (TITLE HERE: kırmızı→turuncu→sarı, kalın beyaz kontur) · Menü bandı (TODAY'S SPECIAL MENU: ortada sarı eğik bant) · Canlı bant (COOKING LIVE LAB) · Sarı-beyaz italik (BLACK FRIDAY + sarı etiket) · Yıpranmış (SHORT STORY: siyah, beyaz kontur, lekeler).
  - Tek çizim, üç yer: **Giriş başlığı** ve **Kapak** stil listesi (Reels.STILLER) ve **Metin › Yazı** "Hareketli ve şablon yazılar" satırı (katman `stil = r_*`, `KatmanCizim.sablonMetin`). Satırlar Enter ile; girişte bant satırları da sıradaki satır olarak kullanılır. Parçalar girişte sırayla gelir (mevcut giriş hareketleri).
- **InShot teması** (`Renk.tema = 4`, temel tema — bir kez otomatik geçilir): grafit zemin `#1C1C1C`, paneller `#252525/#2F2F2F`, beyaz yazı, gri simgeler `#B4B4B4` (seçili beyaz), turkuaz vurgu `#3FC1B8`; seçili alt menünün altında sekme çizgisi. Yerleşim VITA düzeniyle aynı (oynatıcı kontrolleri vb.). Önceki temalar (Siyah-beyaz, Gece, Gündüz, Karanlık Oda) Ayarlar'da / ⋯ menüsünde seçilebilir.
  - Not: simgeler çizgi biçiminde kaldı (InShot'taki dolu simge takımı yok).

## 3.90 (21.11.2026, deneme · `yerel-android` dalı): seslendirme efektleri (Doğal · Net · Temiz · Radyo · Kalın)
- **Yer:** Ses › Seslendirme › Kayıtlar — her kayıtta "Ses efekti" hapları + "Bu efekti bütün kayıtlara uygula". Mevcut ses düzeyi ayarı ve kayıt yöntemi değişmedi.
- **Özgün kayıt korunur:** efekt özgünden bir kez işlenip ayrı WAV yazılır (`SesKaydi.efekt`, `islenmis`, `calYol`); Doğal = özgün. Önizleme (sesPl) ve dışa aktarma aynı dosyayı çalar. İşlenmiş dosya temizlikte silinmez.
- **Zincir (`SesEfekt.kt`, cihazda, ücretsiz):** yüksek geçiren süzgeç (uğultu/rüzgâr) → gürültü kapısı (sözcük aralarındaki fon, tepe −28 dB altı) → RBJ EQ → RMS sıkıştırıcı → [Kalın: Media3 Sonic ile perde −1,5 yarım ton, süre aynı] → tepe −1 dBFS.
  - Net: HP 90 Hz, 250 Hz −2 dB, 3,2 kHz +4,5 dB, 8 kHz +1,5 dB, 3:1 · Temiz: HP 110 Hz, derin kapı, 3 kHz +2,5 dB · Radyo: HP 100 Hz, 180 Hz +3, 450 Hz −2, 3,5 kHz +5, 4,5:1 · Kalın: HP 70 Hz, 150 Hz +3,5, 2,8 kHz +2, perde −1,5.
- Diksiyon (telaffuz) düzeltilemez; efektler sesi temiz ve anlaşılır yapar.

## 3.89 (21.11.2026, deneme · `yerel-android` dalı): sabit Düzenle düğmesi, sabit menü arama, her klipte hareket düğmesi
- **Düzenle sabit = yalnız düğme** (3.87'deki alt menü satırı kaldırıldı): ana menü satırında ve başka bölümlerin alt menü satırında Düzenle solda kaymadan durur; daha küçük (62 dp, 21 dp simge). Varsayılan açık; Düzenle alt menüsündeki "Sabit/Sabitle" ile değişir (`duzenleSabit2`). Sabit değilken ana menüdeki Düzenle 84 → 70 dp.
- **Menü arama** (büyüteç) sağda sabit: hem ana menü satırında hem alt menü satırında; bölümler aradan kayar.
- **Hareket düğmesi her klip resminde:** seçili olmayan (ve küçük) klip kartlarında sol altta küçük hareket simgesi — hareket kapalı olsa da (Sabit simgesi). Dokun = klibi seç + yön menüsü. Seçili büyük kartta önceki düğme duruyor. Yön seçimi tek işlevde (`hareketSec`).

## 3.88 (21.11.2026, deneme · `yerel-android` dalı): Büyük logo geçişi Yayın › Logo'da — plan (kaç kez / hangi aralıkla), geçiş türünden bağımsız
- **Yer:** Yayın › Logo › **Büyük logo geçişi** (anahtarlı açılır kart). Efekt › Geçiş'te yalnız durum satırı kaldı (aynı ayar iki yerde olmasın).
- **Logo:** "Köşedeki" (köşe logosuyla aynı; köşe logosu değişince o da değişir) ya da kütüphaneden başka bir logo. Şekil: Yuvarlak · Yuvarlak kare · Özgün.
- **Ne zaman** (`Gecis.logoPlan`, `Proje.logoPlani()`): T = video süresi; logo girişten sonra (giriş açıksa giriş + 0,5 sn, en az 4 sn) ve bitime 4 sn kalana kadar.
  - Baş · orta · son: 15 sn, T/2, T−15 sn (T < 25 sn: yalnız T/2) — önceki davranış
  - Yalnız ortada: T/2
  - Belli arayla (A = 15/20/30/45/50/60/90/120 sn): A, 2A, 3A… (T−4 sn'ye kadar)
  - Kaç kez (N = 1…12): T·k/(N+1), eşit aralık
  - **Klip arasına denk getir** (varsayılan açık): hedefe ±3 sn içindeki en yakın klip arasına oturur → kesme logo tam ekrandayken olur (o arada geçiş yok). Yakın ara yoksa logo klibin üstünde belirir. Logolar arası en az 6 sn.
  - Yerler kartta dakika:saniye olarak listelenir; dokun = o yerin 1 sn öncesinden oynatır.
- **"Geçiş yok"ta logo gelmiyordu:** logo geçişi önceden yalnız klip aralarına bağlıydı ve ayarı Geçiş panelindeydi; artık plan zaman çizelgesindeki anlara göre kurulur, geçiş türüne bakılmaz (önizleme ve dışa aktarma aynı `logoGecisAnlari()`).

## 3.87 (21.11.2026, deneme · `yerel-android` dalı): silüet ışığı, öncü/sabitlenebilir Düzenle, sürüklerken kararma kök düzeltmesi, hareket yönü menüsü
- **Yüz turu › Silüet ışığı** (Ahmet'in gösterdiği Samsung Galeri efekti; varsayılan): ışık kişinin dış hattını **iki alt uçtan** (omuzlar/kollar) başlayıp başın tepesinde buluşacak şekilde izler, sonra kuyruk tepeye çekilip söner (~2 sn; durak kısaysa sıkışır). Yumuşak beyaz hale + parlak ince çekirdek + koşan ışık noktaları.
  - Kişi maskesi **ML Kit Selfie Segmentation** (ücretsiz, model uygulamada, cihazda; `com.google.mlkit:segmentation-selfie`) ile bir kez bulunur (`Siluet.kt`): yüze bağlı kişi bileşeni → Moore/radyal sınır izleme → bölge kesimindeki düz kenar atılır → soldan sağa açık hat, 160 noktaya örneklenir, yumuşatılır, 1–2 px dışa açılır. Veri: `OdakNoktasi.siluet`, `YuzTuru.vurguTur` ("siluet" | "cizgi"), `siluetArandi`.
  - Önizleme ve dışa aktarma aynı çizim (`TurVurgu.siluetCiz`); dışa aktarmada hattın kutusu (4K'da yarım çözünürlük + 2× ölçek, iki bitmap nöbetleşe, değişmeyen karede yeniden çizilmez).
  - Koruma: hata olursa o kişide baş çevresi çizgisi kullanılır; bulma sırasında uygulama kapanırsa bekçi işareti kalır ve bir daha denenmez. Yüzler sayfasında "Silüet ışığı / Baş çevresinde çizgi" seçimi.
- **Düzenle menüsü:** editör açılınca Düzenle'nin alt menüleri hemen görünür (önce ana menüye dokunmak gerekmiyordu → artık gerekmiyor). Ana menüde Düzenle öncü: geniş, vurgu renginde, ince çerçeveli. Düzenle alt menüsünde **Sabitle**: açıkken başka bölümlerde de Düzenle'nin menüsü (Nesne + Klip'in sayfaları: Klipler, Kırp, Kadraj, Hız, Yüzler, Yüz gizle, Ayrı video) ana menü gibi altta durur; ✕ ile kaldırılır.
- **Fotoğrafı sürükleyince kararma (kök neden):** durağan önizleme fotoğrafı oynatıcının yüzeyinden gösteriyordu; sürükleme bitince yapılan güncelleme akıcı oynatıcıyı bırakıp düzenleme oynatıcısını yeniden sardırıyor, o arada perde saydam olduğu için siyah zemin görünüyordu (Klip ve Katman bölümünde aynı önizleme). Artık duraklatılmışken fotoğraf klibi fotoğrafın kendisinden (aynı yerleşim, aynı kaydırma/yaklaşma, renk ayarlarıyla) çizilir; oynatıcının durumundan bağımsız. Videoda sürükleme sırasında ve bırakınca 1,5 sn karenin kendisi gösterilir.
- **Klip kartı › hareket simgesi:** dokun = küçük yön menüsü (3×3: Yakınlaş · Yukarı · Uzaklaş / Sola · Sabit · Sağa / Otomatik · Aşağı · Ayarlar; fotoğrafta Yüz turu satırı). Yön seçilince yüz turu varsa kapanır (geri al ile döner). Basılı tut = Hareket ayarları, çift dokun = Görünüm › Hareket.

## 3.86 (21.11.2026, deneme · `yerel-android` dalı): 3.85 açılmıyordu — kök düzeltme
- **Neden:** ana sayfa logosu `painterResource(R.mipmap.ic_launcher)` ile çiziliyordu; Android 8+ cihazda bu kaynak uyarlanabilir simge (XML `adaptive-icon`) ve Compose bunu çizemez → açılışta `IllegalArgumentException`, uygulama kapanıyordu.
- **Çözüm:** logo ayrı bir PNG olarak eklendi (`res/drawable-nodpi/studyo_logo.png`, yuvarlak köşeli kare, web Stüdyo logosu gibi) ve ondan çiziliyor.

## 3.85 (21.11.2026, deneme · `yerel-android` dalı): "Stüdyo" ana sayfası (web Stüdyo görünümü)
- Ahmet'in istediği tema: web Stüdyo 2.16 ana ekranının yerel hâli (`AnaStudyo.kt`). **Yalnız ana sayfa** — editör kendi temasında (Renk.tema) kalır.
- Renkler web'in değişkenleri (`AS`): gündüz zemin `#F3F5F7`, kart `#FFF`, çizgi `#DFE4E8`, yazı `#141A1F`, vurgu `#E8681C`; ay düğmesiyle gece (`#121A21`…). Seçim hatırlanır (`anaGece`).
- Ana sayfa: uygulama simgesi + harf aralıklı "STÜDYO", ay/güneş düğmesi; "Yaratıcılığını keşfet." · "Fotoğraf ve video, tek bir yerde."; Fotoğraf (web editörü) ve Video (dosya seç → yeni proje) kartları, sağ üstte soluk daire;
  mavi geçişli **Karanlık Oda** (web Karanlık Oda'yı açar) ve turuncu geçişli **Kareden Videoya** (varsayılanlarla yeni video) kartları; dışa aktarma şeridi; **Son çalışmalar** kenardan kenara şerit (150×190 küçük resim, "Video" rozeti, Bugün/Dün), "Tümünü gör".
  Simgeler web'deki SVG yollarıyla çizilir. Kamera bölgesi aynı zeminde. Web'deki "Fotoğraf araçları" (Kabataş Haber vb.) yerel uygulamada karşılığı olmadığı için konmadı.
- Alt gezinme: **Ana sayfa · Projelerim · Taslaklar · Ayarlar** (seçili simge turuncu). Projelerim: iki sütunlu kartlar (süre rozeti, ⋯ menüsü, TV düğmesi), çipler Tümü/YouTube/Reels/Kare/Arşiv/Seç, uzun bas = toplu seçim (arşivle, sil). Taslaklar: henüz dışa aktarılmamış projeler. Ayarlar: ana sayfa teması, editör teması, önceki görünüme dön, alt başlıklar, yedekten geri yükle, pil, hata kaydı, sürüm. Geri tuşu önce Ana sayfa sekmesine döner.
- Varsayılan açık (`Duzen.anaStudyo`). 3.84 Karanlık Oda ana sayfası ve önceki ana sayfa duruyor: Ayarlar › Ana sayfa görünümü; geri gelmek için ⋯ menüsü / Ayarlar kutusunda "Stüdyo ana sayfası".

## 3.84 (21.11.2026, deneme · `yerel-android` dalı): "Karanlık Oda" teması
- Dördüncü tema (`Renk.tema = 3`, `Renk.ko`): Karanlık Oda ve Kareden Videoya'nın ortak görünümü — grafit zemin `#0F1213`, yüzey `#171B1D/#21262A`,
  kehribar vurgu `#F0993C`, sıcak kırık beyaz yazı `#E8E6E0`, deniz yeşili ikincil `#6CB8AC`. Renkler bütün uygulamaya (editör dahil) uygulanır.
- Ana sayfa bu temada o uygulamaların düzeninde (`AnaKO.kt`): ışıldayan kehribar lamba imi + "Stüdyo" (Archivo), alt satırda sürüm;
  açılır **Ayarlar** kutusu (tema seçimi hap düğmelerle, alt başlık düzeni, yedekten geri yükle, pil, hata kaydı, sürüm — ⋯ menüsünün yerine);
  eylem kartları (birincil "Yeni video" kehribar, ışık gölgeli, tam genişlik; altında "Fotoğraf (web)" ve "Arşiv");
  hap sekmeler "Son çalışmalarım · Arşiv"; proje kartı büyük küçük resim + süre rozeti, biçim etiketi.
- Seçim: ⋯ › "Karanlık Oda teması (yeni)". Önceki üç tema ve ana sayfanın önceki görünümü aynen duruyor.

## 3.83 (21.11.2026, deneme · `yerel-android` dalı): Yüz gizleme (takipli bulanıklaştırma)
- **Düzenle › Klip › Yüz gizle** (Yüzler'in yanında; fotoğraf ve video). Yalnız **seçilen** yüzler gizlenir.
- **Bulma:** ML Kit yüz algılama (ücretsiz, cihaz içinde; küçük/arkadaki yüzler dahil). Videoda kareler sırayla çözülür, saniyede ~10'u taranır;
  yüzler konum + boyut benzerliğiyle kareden kareye bağlanır (takip izi), 3 sn'ye kadar kopuk izler birleşir, iz 3 noktalık ortalamayla yumuşatılır,
  yüz 1,5 sn'ye kadar görünmese (döndü/kapandı) de gizli kalır; izin başı/sonu 0,4 sn payla kapatılır. Her yüzün küçük resmi listede.
- **Elle ekleme:** önizlemede kaçan yüze basılı tut → fotoğrafta oval eklenir, videoda o andan itibaren şablon takibiyle (Takipci) izlenir ve gizlenir.
- **Görünüm:** yüz hattına uyan oval (alın-çene dahil), başın eğimine döner, kenarı yumuşak; dört geçişli maskeli Gauss (dışarısı hiç değişmez).
  Ayarlar: bulanıklık gücü (klip), alan genişliği (yüz başına), gizle/göster, listeden sil, Hepsini gizle / Hiçbiri / Kaldır.
- **Uygulama yeri:** kaynak karede, renkten sonra, kadraj/hareketten önce (`YuzGizleEtki`, `Disa.parca`) → yaklaşma/kaydırmada yüzle birlikte gider;
  geçişte gelen klipte de (tek tuval). Durağan önizlemede aynı ovaller işlemciyle (`YuzGizleme.uygula`). Veri: `Klip.yuzGizle` (`YuzGizle`, `GizliYuz`, `YuzIz`).

## 3.82 (21.11.2026, deneme · `yerel-android` dalı): "önce aydınlık, sonra koyu" kök düzeltmesi, bağımsız logo geçişi, Yıl etiketi stili, TV kopyası 6 Mbps
- **Kronik hata — fotoğraf aydınlık başlayıp koyulaşıyordu (kök neden):** üst üste binen geçişlerde (tek tuval) gelen klip yalnız kadraj/hareket + sunumla çiziliyordu; klibin **renk ayarı/Doğal**, **filtresi**, **efekti** (vinyet, sinema…), **keskinliği** ve **arka plan bulanıklığı** ancak geçiş bitip klibin kendi öğesi başlayınca uygulanıyordu → geçiş boyunca ham (daha aydınlık), hemen ardından ayarlı (koyu). Artık gelen klip kendi öğesindeki AYNI etkilerle, AYNI sırayla çizilir (`Disa.renkEtkisi`, `GelenTarif.etkiler: List<GlEffect>`). Dışa aktarma ve akıcı önizleme aynı yolu kullanır.
- **Logo geçişi (Efekt › Geçiş):** köşe logosundan **bağımsız** — kütüphaneden logo seçilir (`Gecis.logoYol`), köşe logosu kaldırılsa da geçişler kalır. Yerler: **15. saniyeye, ortaya ve bitime 15 sn kalaya en yakın** klip arası (25 sn'den kısa videoda yalnız orta). **Şekil:** Yuvarlak · Yuvarlak kare · Özgün (`Gecis.logoSekil`). Standart geçiş seçmek, "Tümüne", hazır paketler, varsayılan ve kayıtlı ayarlar logo geçişini değiştirmez. Logo ekrana doğru büyüyerek gelir (2,8 sn, 3.73'teki hareket). Köşe logosu ayrı tercih olarak duruyor.
- **Yeni başlık stili "Yıl etiketi"** (giriş ve kapak Reels stilleri): ilk satır sarı etiket (içindeki 4 haneli yıl kırmızı), sonraki satırlar beyaz kutular (son satır büyük), vurgu bandı kırmızı konum bandı (iğneli). Örnek: `2012 · 14 YIL ÖNCE ⏎ Kabataş Lisesi ⏎ TURİZM HAFTASI`, bant `Kabataş · Ordu`.
- **TV:** akıcı kopya 10 → 6 Mbps (Wi-Fi'da kısa duraksamalar için); eski kopyalar bir kez yeniden hazırlanır.

## 3.81 (21.11.2026, deneme · `yerel-android` dalı): Google Fotoğraflar albüm bağlantısından fotoğraf alma
- **Editörde:** klip şeridinde "+ Ekle"nin yanında **Albüm · Google Fotoğraflar** kutusu → pencere (panodaki albüm bağlantısı kendiliğinden yazılır) → "Fotoğrafları al": albümdeki fotoğraflar özgün boyutunda (çekim tarihiyle) indirilir ve projeye eklenir; ilerleme "Albümden alınıyor 12 / 30", "Vazgeç" ile durur. Daha önce indirilenler yeniden indirilmez.
- **Google Fotoğraflar'dan paylaşarak:** albüm › Paylaş › Bağlantı (Stüdyo'yu seç) → açık projeye eklenir; proje açık değilse "Albüm …" adlı yeni proje açılır.
- **Yöntem (`AlbumIndir.kt`):** ücretsiz, hesap/anahtar yok. Bağlantı yönlendirmeleri izlenir, paylaşılan albüm sayfasındaki görsel adresleri (lh3.googleusercontent.com/pw/…) okunur, her biri `=d` (özgün) ile indirilir; dosya türüne göre .jpg/.png/.heic/.mp4. Dosyalar uygulama içinde `albumler/` klasöründe. Resmî bir Google arayüzü değildir (Google Fotoğraflar'ın albüm okuma arayüzü 2025'ten beri yeni uygulamalara kapalı); Google sayfa yapısını değiştirirse bu yol çalışmayabilir — o zaman fotoğrafları seçip Paylaş › Stüdyo (mevcut özellik) her zaman çalışır.
- Bağlantı "bağlantıya sahip olan herkes" ile paylaşılmış olmalı.

## 3.80 (21.11.2026, deneme · `yerel-android` dalı): durdurunca siyah ekran — kök düzeltme (2. deneme, farklı yöntem)
- **3.77'deki düzeltme yetmedi** (yüzey hazır olunca yeniden sarma): düzenleme oynatıcısı kareyi çizemediğinde (çözücü akıcı oynatıcıda meşgulken ya da oynatıcı hata verip boşta kalınca) yine siyah kalıyordu; perdesi de siyahtı.
- **Yeni yöntem:** duraklatınca akıcı oynatıcının yüzeyi kaldırılmaz; **donmuş son kare** düzenleme önizlemesinin altında kalır (`kompDurgun`). Oynatıcı perdesi saydam (siyah değil). Düzenleme oynatıcısı kendi karesini çizince (`onRenderedFirstFrame`; fotoğrafta 1,2 sn sonra) alttaki yüzey bırakılır, akıcı oynatıcı ▶ için hazır bekler. Düzenleme oynatıcısı boşta kalmışsa durdururken yeniden hazırlanır; akıcı oynatıcı bırakılınca (bir düzenleme yapılınca) o anki kare yeniden çizilir.

## 3.79 (21.11.2026, deneme · `yerel-android` dalı): dönen ışık tamamen kaldırıldı
- Ahmet'in kararı: 3.77'deki "dönen ışık · gövde" özelliği iptal (istenen gövde hattını izleyen ışık değildi; yüz klibinde uygulama kapanıyordu). Kaldırılanlar: gövde bulma (`Govde.kt`, ML Kit çoklu nesne ayırma), ışık çizimi, `TurIsikBindirme`, biçim seçimi, `YuzTuru.vurguStil`, `OdakNoktasi.govde/govdeArandi`. Eski projelerdeki bu alanlar okunurken yok sayılır.
- Kalan: başın çevresinde ince çizgi (3.76, aç/kapat), isim kutusu, Ayrı video. 3.78'deki çizim korumaları duruyor.

## 3.78 (21.11.2026, deneme · `yerel-android` dalı): yüz klibinde kapanma için koruma, Yüzler › Ayrı video (Reels/yatay/kare)
- **Kapanma (yüzlerin olduğu klibe gelince):** cihaz kaydı olmadan kesin neden görülemedi; yüz turundaki bütün yeni çizim/hesap yolları korumaya alındı: `TurVurgu.durum` (sonlu olmayan değerler elenir), ışık ve çizgi çizimi, isim kutusu (koşullu `remember` yerine önbellek), dışa aktarma/akıcı önizleme bindirmeleri (hata olursa o kare boş, kayda bir kez yazılır). Gövde bulma (ML Kit nesne ayırma) için kapanma bekçisi: başlarken işaret konur, bitince kaldırılır; işaret kalmışsa (uygulama o sırada kapanmışsa) bir daha çalışmaz, ışık baş-omuz biçiminde döner. Kayda "Gövde bulma başladı" yazılır. Bozuk gövde değeri kaydedilmez.
- **Ayrı video (Düzenle › Klip › Yüzler'in sağında):** kişileri seç (numara + ad; Tümü/Hiçbiri), biçim: Reels · dikey (9:16) / Yatay / Kare → "Videoyu oluştur". Bu proje değişmez; yalnız bu fotoğraf ve seçilen duraklarla yeni bir taslak açılır ("… · Yüzler · Reels"), dışa aktarma ekranı hemen gelir. Yüz yakınlıkları yeni biçime çevrilir (baş kadrajda aynı payı kaplar); isim kutusu, öne çıkarma, müzik, logo, geçiş, renk korunur; giriş/abone/kapanış ve diğer kliplerin yazıları alınmaz.

## 3.77 (21.11.2026, deneme · `yerel-android` dalı): TV'de akıcı oynatma, gövdenin çevresinde dönen ışık, harf biçimi, durdurunca siyah ekran düzeltmesi, ortası açık klip kartı
- **TV'de takılma (kök nedenler):** (1) 4K · 64 Mbps dosya ev Wi-Fi'ında ve TV çözücüsünde zorlanıyordu → **Akıcı** (varsayılan): video bir kez kısa kenar 1080, H.264 ~10 Mbps kopyaya çevrilir (`TvYayin.akiciKopya`, ilerleme gösterilir, önbellekte son 3 kopya; zaten küçükse özgün kullanılır). **Özgün** seçeneği 4K dosyanın kendisi; seçim hatırlanır, bağlıyken değiştirince yeniden gönderir. (2) Ekran kararınca telefonun Wi-Fi'ı güç tasarrufuna geçiyordu → yayın sürerken Wi-Fi kilidi (düşük gecikme / yüksek başarım) ve işlemci uyanık; yayın bitince bırakılır. (3) Sunucu: 256 KB tampon, 1 MB gönderme tamponu, TCP_NODELAY.
- **Durdurunca siyah ekran (kök neden):** akıcı önizlemede düzenleme oynatıcısının görünümü ekrandan kalkıyor; durdurunca yeniden kuruluyor ama duraklatılmış oynatıcı kareyi yüzey (TextureView) hazır olmadan çiziyordu. Yüzey hazır olunca aynı konuma bir kez sarılır → fotoğraf/video karesi görünür (`PlayerView.kareyiYenile`, iki önizleme görünümünde).
- **Klip kartı:** ortası boş — fotoğraf görünür. Üstte numara · süre · ses · kırp · sil; altta iki satır: böl · çoğalt · nesne kes · kadraj · hareket · doğal renk / takip · yazı · hareketli yazı · giriş · oynat. ← → kenarlarda küçük. Karartma üstte ve altta koyu, ortada açık (dikey geçişli).
- **Ekran açık:** düğme görünümü yok; simge + "Ekran açık" yazısı — açıkken hafif beyaz, kapalıyken gri (temanın renkleriyle).
- **[3.79'da kaldırıldı] Yüz turu — dönen ışık:** kişinin gövdesinin çevresinde (yalnız yüz değil) parlak, kuyruklu bir ışık ~1,25 tur döner; çevrede çok soluk bir hat belirir, sonra solar (~3 sn; durak kısaysa sıkışır). Gövde çevresi ML Kit nesne ayırmayla (nesne kesmedeki model, ücretsiz, cihaz içinde) bir kez bulunur: durağın üstündeki kişinin maskesi → merkezden 120 ışın → yumuşatılmış, biraz dışa açılmış kapalı eğri (`Govde.kt`). Bulunamazsa baş-omuz kapsülü. Önceki ince çizgi seçenek olarak duruyor ("Dönen ışık · gövde" / "İnce çizgi · yüz"). Dışa aktarmada `TurIsikBindirme` (iki bitmap nöbetleşe, 4K'da yarım çözünürlük); önizleme aynı çizim.
- **Harf biçimi (yazılar):** yazı kartında Olduğu gibi · Cümle başı · Baş Harfler · BÜYÜK · küçük. Çizimde uygulanır (Türkçe i/İ kurallarıyla) — klavyeyle ya da sesle yazılsa da videoda aynı; klavye de bu biçimde açılır. Giriş/kapak yazılarında klavye cümle başını büyük açar.

## 3.76 (21.11.2026, deneme · `yerel-android` dalı): yazıyı klibine bağla, yüz turunda tek kişiye odak + öne çıkarma çizgisi + isim kutusu, Kırp'ta kes/böl
- **Klibine bağlı katman:** yazı (ve görsel, çıkartma, ses katmanı) ait olduğu klibe bağlanır; araya klip eklenince, klip taşınınca, silinince ya da önceki klip kırpılınca katman o klibin başlangıcı kadar kayar (`EditorDurum.bagliKatmanlar`, her güncellemede). Katman o adımda elle değiştirildiyse dokunulmaz; klibi silinirse bağ çözülür, katman yerinde kalır. Yeni yazılar (Yazı ekle, hazır/hareketli stiller, Kanca) bulunduğu klibe bağlı başlar. Anahtar: yazı kartında ve Seçili katman kartında "Klibine bağlı · klip N"; Videodaki yazılar'da "Hepsini klibine bağla". "Bu klip boyunca" = o klibe bağlar; "Tüm video / Şimdiden sona / Baştan şimdiye" bağı çözer. Kayıt: `KatmanOge.klipId`.
- **Yüz turu — tek kişiye odak:** varsayılan yakınlık artık başın kadraj yüksekliğinin %55'i (önce %32; bir kişiye yaklaşınca yanındakiler de kadrajda kalıyordu). Yakınlık aralığı 1×–6×. Seçili durakta "Yüzlere: Geniş · Yüz · Yakın" bütün yüz duraklarına birden uygulanır (eski projelerde de bununla düzelir).
- **Öne çıkarma çizgisi (aç/kapat):** kamera bir yüze varınca başın etrafında ince beyaz çizgi tepeden saat yönünde dolaşır (öndeki küçük ışık noktasıyla), kısa kalır, solarak kaybolur (~2,3 sn; durak kısaysa sıkışır). Bulanıklık/karartma yok.
- **İsim kutusu (aç/kapat):** Yüzler sayfasında numaraya dokun → "N. kişinin adı" alanına yaz (kelime başları büyük). Kameranın o kişiye vardığı anda başın altında (yer yoksa üstünde) yarı saydam koyu kutu, solda ince çizgi, Montserrat; yükselerek gelir, **2 saniye** ya da **durak boyunca** kalır. Ad numara şeridinde ve önizleme editöründe numaranın altında görünür.
- **Tek hesap:** `TurVurgu.durum()` — önizleme (Paneller) ve dışa aktarma/akıcı önizleme (`TurHalkaBindirme`, `TurIsimBindirme`, klibe ait bindirme) aynı. Çizgi bitmapi ilerleme adımlı ve yalnız görünürken çizilir; isim kutusu her durak için bir kez.
- **Kırp sayfası (klip kartındaki kırp simgesi ve Düzenle › Klip › Kırp):** sağdan/soldan kırpma kaydırıcısının altına "Çizgi" kaydırıcısı (klip içinde oynatıcıyı taşır) ve **Başı kes · Böl · Sonu kes** (çizgiden). Fotoğrafta süreyi kısaltır/böler. Klip kartındaki böl simgesi kısayol olarak kaldı.

## 3.75 (21.11.2026, deneme · `yerel-android` dalı): karartılmış klip kartı + büyük simgeler, tek yazı kartı, ekran sürekli açık, Katman Renk'in solunda
- **Klip kartı (Düzenle › Klip):** seçili kartın resmi karartıldı (%62; diğer kartlar %28) — üstteki oynatıcıyla yarışmaz. Simgeler 28 → 36 dp, ince açık kenarlı, dört satırda: **üst** numara · süre · ses · süre/kırp · sil; **ortanın üstü** ← · böl · çoğalt · nesne kes · kadraj · →; **orta** hareket · büyük oynat (46 dp) · doğal renk; **alt** takip (video) · yazı · hareketli yazı · giriş başlığı. Kart yüksekliği 176 → 216 dp. İşlevler, çift dokunuşlar ve sürükleyerek taşıma aynı.
- **Tek yazı kartı (ayrı pencere yok):** "Yazıyı düzenle" penceresi kaldırıldı. Ekle ve düzenle aynı kartta: Metin › Yazı'da "Yazı ekle / Yazıyı düzenle" kartı, Katman bölümünde "Katman ekle" kartının başı. Seçili yazıda: yazı alanı (Enter = alt satır), renkler, (Düz'de) arka kutu, Bitti.
- **Stil değiştirme:** ekrandaki yazıya dokun (yalnız seçer, klavye açılmaz) → bir stile dokun = aynı yazı o stile geçer (metin ve yer korunur; stilin boyutu ve rengi gelir). Seçili stil beyaz çerçeveli. Hazır stillere Kanca ve Düz eklendi; sabit ve hareketli stiller aynı kartta. Klavye yalnız ✎ (önizleme tutamacı, çizelge menüsü, listedeki Düzenle) ve yeni yazıda açılır.
- **Metin › Yazı alt sayfaları:** Yazı · Videodaki ("Hareketli" Yazı kartına katıldı). Seçili katmanın zaman/boyut/giriş-çıkış ayarları aynen (yazı/stil/renk satırları üstteki karta taşındı).
- **Ekran sürekli açık:** üst çubukta Kaydet'in solunda "Ekran" düğmesi; açıkken beyaz "Açık". Düzenleme ekranındayken telefon kararmaz (View.keepScreenOn); seçim hatırlanır, editörden çıkınca bırakılır.
- **Ana menü:** Katman, Renk'in soluna alındı (Düzenle · Görünüm · Katman · Renk · Efekt · Ses · Metin · Yayın).

## 3.74 (21.11.2026, deneme · `yerel-android` dalı): yüzlere sırayla yaklaşma (yüz turu), kanca yazısı
- **Yeri:** Düzenle › Klip › **Yüzler** (yeni alt sayfa; fotoğraf kliplerinde). Klip kartındaki hareket simgesi tur varken yüz simgesine döner; dokununca bu sayfa açılır.
- **Yüz turu:** aynı fotoğrafta numaralı duraklar sırayla gezilir. "Yüzleri bul" yüzleri bulur (ML Kit yüz algılama, ücretsiz, cihaz içinde; olmazsa Android FaceDetector), soldan sağa numaralandırır. Yüz olmasa da önizlemede istenen yere dokunarak durak eklenir (yüz halkasının üstüne dokunulursa yüze yapışır).
- **Önizleme (Yüzler sayfası açık, duraklatılmışken):** fotoğrafın tamamı; bulunan yüzler ince halka, duraklar numaralı daire, sıra çizgisi; seçili durağın görünecek alanı kesik çerçeve. Dokun = durak ekle · numaraya dokun = seç · numarayı sürükle = taşı.
- **Sayfa:** Yüzleri bul · Önizle (yalnız bu klibi oynatır, bitince durur) · Temizle; DURAKLAR şeridi (numara, yüz/nokta, yakınlık); seçili durakta Öne al / Sona al (sıra değişir) / Sil ve **Yakınlık** 1×–4× (Tümüne). AKIŞ: her durakta kalma 1,5–6 sn, geçiş Yavaş/Orta/Hızlı, durakta yavaş yaklaşma Yok/Hafif/Orta/Belirgin, "Önce fotoğrafın tamamı", "Sonda fotoğrafın tamamı", "Sinematik kavis".
- **Hareket (web Kareden Videoya kfPlan/kfDurum'un gelişmiş hâli, `YuzTur.kt`):** durakta kosinüs eğrisiyle çok yavaş ek yaklaşma (görüntü donmaz); duraklar arası 5. dereceden yumuşak eğri (başta/sonda hız sıfır, birden geçmez); yakınlık geometrik ara değerle (eşit algılanan hız); kavis: uzak duraklar arasında hafif geri çekilip yeniden yaklaşma; yüz durağında yüz kadrajın %44'ünde, elle noktada ortada. **Karartma/bulanıklık yok** — fotoğrafın geri kalanı olduğu gibi. Varsayılan yakınlık: baş kadraj yüksekliğinin ~%32'si.
- **Süre:** tur sığmazsa fotoğraf klibi kendiliğinden uzar (mesaj verir); Kırp sayfasından değişir. Klip uzunsa tur ona yayılır.
- **Tek hesap:** `Yuz.an()` içinde (`Klip.turEtkin`) — durağan önizleme, akıcı önizleme, geçişler ve dışa aktarma aynı kamerayı kullanır. Hareket "Sabit" yapılırsa tur durur, duraklar silinmez. Kayıt: `Klip.yuzTur` (`YuzTuru`, `OdakNoktasi`).
- **Kanca yazısı (web Kareden Videoya'dan taşındı):** Metin › Yazı › Ekle › "Kanca yazısı" — giriş başlığı bitince 3 sn, ortada (dikeyde biraz yukarıda): yarı saydam koyu yuvarlak kutu, Archivo 800, ilk satır altın, en çok 3 satır; 0,35 sn'de %6 büyükten yerine oturarak gelir (`pop`). Yazı stillerinde "Kanca" olarak da seçilir. Normal yazı katmanıdır (çizelgede taşınır, düzenlenir, silinir).
- Bağımlılık: play-services-mlkit-face-detection 17.1.0; manifestte ML Kit bağımlılığına `face` eklendi. Kod: `YuzTur.kt`, `YuzTurPanel.kt`, `Yuz.kt`, `EditorDurum.kt` (tur*), `Katmanlar.kt` (kanca, pop).

## 3.73 (20.11.2026, deneme · `yerel-android` dalı): Televizyonda izle (Google TV / Chromecast)
- **YouTube'a yüklemeden TV'de tam ekran:** dışa aktarma bitince "TV'de izle"; Projeler ekranında dışa aktarılmış videosu olan projede TV simgesi (ve ⋯ › Televizyonda izle).
- **Nasıl:** Google Cast (ücretsiz Varsayılan medya alıcısı; hesap, ücret, internet kotası yok). Telefon videoyu ev Wi-Fi'ında kendi küçük sunucusundan verir (yalnız o video, rastgele adres, ileri/geri sarma desteği); Google TV / Chromecast doğrudan telefondan okur. Telefon ve TV aynı Wi-Fi'da olmalı.
- **Pencere:** aynı ağdaki TV'ler listelenir → dokun = bağlan ve oynat. Bağlıyken oynat/duraklat, −10/+10 sn, sarma çubuğu, bağlantıyı kes. Pencere kapansa da TV oynamaya devam eder (Stüdyo açık kalmalı).
- Her projenin son dışa aktarılan videosu hatırlanır (galeriden silinmişse TV açamaz).
- Kod: `TvYayin.kt` (yerel HTTP sunucusu + Cast penceresi), `CastAyar` (manifestte OPTIONS_PROVIDER), bağımlılıklar play-services-cast-framework 21.5.0, androidx.mediarouter 1.7.0.

- **Klip şeridi oynatmayla paralel:** seçili klip kartının ortasında oynat/duraklat; alt kenarında ince turuncu çubuk = klibin neresindesin. Oynarken (akıcı önizlemede de) klip bitince şerit kendiliğinden sıradaki klibe geçer ve ortaya kayar.
- **Logo geçişi** (Efekt › Geçişler › "Logo geçişi (baş · orta · son)"): baştaki (%15), ortadaki (%50) ve sona yakın (%85) klip arasında geçiş yerine logo arka plandan büyüyerek gelir (0,6 sn, hafif taşmalı), ~1,6 sn kalır, izleyiciye doğru büyüyüp solarak kaybolur; arkada hafif koyu hale (kesme görünmez). Toplam 2,8 sn, kesme logonun tam ekranda olduğu ana denk gelir. Önizleme, akıcı önizleme ve dışa aktarma aynı hesap (`LogoGecis.kt`). "İlkini göster" = ilk logo geçişinin 1 sn öncesinden oynatır.
- **Logo netliği:** logo özgün PNG'den kademeli (yarıya yarıya) küçültmeyle çizilir — yazılar tırtıklı/bulanık olmaz. Yeni yüklenen logolar 2048 piksele kadar özgün boyutta saklanır (önce 1024'e küçültülüyordu).

## 3.72 (20.11.2026, deneme · `yerel-android` dalı): ince üst çubuk, kontroller videonun üstünde, ekranda yazı düzenleme, estetik nesne kesme
- **Üst çubuk (siyah-beyaz tema):** 34 dp'ye indi. "Kaydet" ve "Dışa aktar" artık düğme değil, küçük yazı (Dışa aktar beyaz, kalın).
- **Kontroller videonun üstünde (varsayılan):** oynat · başa · önceki · sonraki · zaman çubuğu · süre · tam ekran ⛶ şeffaf olarak videonun üstünde; geri al/yinele üst çubukta. Oynarken 2,5 sn sonra kendiliğinden solar, durunca (önizlemeye dokun) geri gelir. Önceki görünüm (kontroller videonun altında) ▾ ile seçilebilir, seçim hatırlanır.
- **Ekranda yazı düzenleme (Görünüm › Şablon):** Giriş / Abone / Kapanış sayfası açılınca ya da bir stile dokununca o öğe tam göründüğü ana gelir ve durur. Giriş başlığı boşsa videonun konusuna uygun örnek başlık yazılır (ML Kit görüntü etiketleri, cihaz içinde: doğa, çay/sofra, yol, yapı…; tarih bandı ve "Kabataş, Ordu" da dolar). Önizlemede yazıya dokun = önizlemenin hemen altında ince düzenleyici açılır, her harf anında videoya yansır (Enter = yeni satır/bant). Girişte iki parmak = yazı boyutu; "Başka öneri" = konuya uygun başka başlık. Paneldeki alanlar da eşzamanlı güncellenir.
- **Nesne kesme (Kareden Videoya estetiği):** kenar yumuşatma (sert eşik yerine yumuşak geçiş + içe doğru ince tüy; tırtık ve koyu hale yok), yumuşak gölge (%45, aşağı 1,2 %), arka plan bulanıklığı dört geçişli Gauss (daha kremsi, basamaksız) + %10 karartma; durağan önizleme de aynı görünümde. Varsayılan arka plan bulanıklığı %75.
- **Nesneyi parmakla yönet:** Düzenle › Nesne ve Katman'da nesneye dokun = seç (beyaz çerçeve) · sürükle = taşı · iki parmak = büyüt/küçült. Hareket sırasında görüntü yeniden çizilmez (akıcı), bırakınca tek adımda kaydedilir (geri al çalışır). Boşta sürükleme klibin kadraj kaydırmasına kalır.

- **Ek düzeltmeler (aynı sürüm, 20.11.2026 akşamı):**
  - **Oynatıcı kontrolleri:** siyah-beyaz temada son VITA dizilişi (süre · başa · önceki · büyük oynat · sonraki · geri al · yinele · tam ekran + ince çubuk) artık videonun üstünde, altta hafif koyu geçişle. Üst çubukta geri al/yinele yok (oynatıcı satırında).
  - **Kesilen nesneyi taşıma:** nesneyi taşırken alttaki bulanık arka plan artık kaymıyor (kök neden: aynı dokunuşla klibin kadraj kaydırması da çalışıyordu). Seçili nesne ekranın her yerinden sürüklenir, iki parmakla büyür/küçülür.
  - **Gerçek Gauss bulanıklığı:** Android 12+ durağan önizlemede arka plan RenderEffect Gauss ile bulanıklaşır (dışa aktarmadaki 4 geçişli Gauss'un karşılığı) + %10 karartma.
  - **Kesilenler listesi:** satıra dokun = nesne seçilir ve oynatıcı o nesnenin klibine gider. Boyut 5–200 %.
  - **Müzik:** "Yeni ekle" — mevcut müziği değiştirmeden/kaldırmadan yeni müzik ses katmanı olarak eklenir (birden fazla olabilir).
  - **Katman menüsü (bütün katmanlar):** çizelgede katmana dokun = menü (Katmanın başına git · Yazıyı düzenle · Klip/Video başı-sonu · Videonun başına taşı · ses düzeyi). Uzun bas = aynı menü + **Sil**.
  - **Açıklamalar kapalı:** bütün panellerdeki açıklama yazıları "Nasıl çalışır?" satırında kapalı gelir.
  - **Klip kartı = oynatıcı:** şeritteki klip resmi oynatıcıdaki görüntünün aynısı (video biçimi, Doldur/Sığdır, yüz odağı, elle kaydırma, hareketin ilk karesi, renk/filtre). Seçili kartta karenin tamamı görünür.
  - **Logo varsayılanı:** boyut %14, opaklık %70 (Varsayılan ayarlar ve logo katmanı); Logo sayfasında "Varsayılan · %14 · %70" düğmesi.

  - **Son düzeltme:** oynatıcı kontrolleri artık kendiliğinden gizlenmiyor (kontrollerin, ▾ alta alma okunun ve ⛶ tam ekranın kaybolduğu sanılıyordu). Duraklatılmışken ekrandaki giriş başlığına dokun = ekranda düzenleme, her bölümde (Görünüm › Şablon'da ekrana dokunmak yeterli). Üst çubukta kalın yazı yok ("Dışa aktar" ince).

  - **Kök düzeltme (oynatıcı satırı):** satırın sağındaki grup (geri al · yinele · ▴ üste al · ⛶ tam ekran) eşit paylı alana sığmıyordu; son iki düğme ekranın dışında kalıp hiç görünmüyordu (3.71'den beri). Gruplar artık kendi boyunda, aradaki boşluk esnek — hem videonun altındaki satırda hem videonun üstündeki kontrollerde. Kontrollerin yeri sıfırlandı: videonun üstünde açılır (▾ ile alta, ▴ ile üste).

## 3.71 (20.11.2026, deneme · `yerel-android` dalı): menüler hep altta, video tam ekran, şeffaf kontroller, temiz müzik
- **Menüler hep altta:** 3. katman çipleri panelin tepesinden alt menünün hemen üstüne indi; üstteki yol satırı kalktı (arama 🔍 alt menü satırının sağında). Ana menüye dönünce üstünde ayrı alt menü satırı yok — yalnız alt menü modu (‹ Bölüm · alt menüler) kullanılır. Önceki yazılı düzen aynen.
- **Video tam ekran** (kontrollerdeki ⛶): yalnız video ve kontroller; 16:9'da ekran yan döner. ⛶/geri = çık.
- **Kontroller videonun üstünde, şeffaf:** koyu şerit kalktı; düğmeler hafif saydam yuvarlaklarda. Siyah-beyaz temada VITA satırındaki ▴ ile kontroller videonun üstüne, üstteki ▾ ile yeniden alta alınır (seçim hatırlanır).
- **Açıklamalar kapalı:** Nesne, Hız, Kayıtlı ayarlarım'daki uzun açıklamalar "Nasıl çalışır?" satırında (dokun = aç). Bölüm aç/kapa oku yarı boyda ve silik.
- **Paket tek sayfa:** Biçim + Paketler birlikte.
- **Müzik:** "Sakin" kaldırıldı, yerine **Huzur** (aynı akor yürüyüşü, saf sinüs tınılar; doğa videolarında otomatik seçim). **Kök neden düzeltmeleri (bütün türlerde):** notaların zarfı en düşük seviyede kalıp pencere sonunda kesiliyordu → her notada yankıyla çoğalan tık/cızırtı; artık 20–50 ms'de sıfıra iner (arpej, davul, piyano, klavsen, timpani). 16 bit'e kesme yerine yuvarlama + TPDF titreşim (sessizleşen kuyruklarda taneciklenme kalkar). Klavsenin süzülmemiş testere vızıltısına alçak geçiren eklendi. Önbellek yenilendi (eski üretimler kullanılmaz).

## 3.70 (20.11.2026, deneme · `yerel-android` dalı): üç katmanlı menü, ayar arama, klip hızı, müziğin yumuşak giriş/çıkışı, kayıtlı ayarlarım
- **Üç katmanlı gezinme** (Ahmet'in onayladığı taslak): 1. ana menü · 2. alt menü satırı · 3. panelin tepesinde alt sayfa çipleri. En üstte yol satırı (Görünüm › Şablon › Giriş; ‹ ya da bölüm adı = ana menü). Her alt menünün son açılan alt sayfası hatırlanır. Ayarı değişen alt sayfada ✓.
  - Düzenle › Klip (Klipler · Kırp · Kadraj · Hız) · Nesne (Fotoğraftan · Videodan · Kesilenler)
  - Görünüm › Şablon (Giriş · Abone · Kapanış) · Paket (Paketler · Biçim) · Hazır (Varsayılan · Kayıtlı ayarlarım) · Hareket (Yakınlaştırma · Kaydırma · Odak)
  - Renk › Hazır · Işık (Parlaklık · Kontrast · Vurgular · Beyazlar · Gölgeler) · Renk (Doygunluk · Sıcaklık · Ton) · Detay (Keskinlik) · HSL
  - Ses › Müzik (Hazır · Dosyadan · Ayar) · Seslendirme (Kayıt · Prompter · Kayıtlar) · Metin › Yazı (Ekle · Hareketli · Videodaki) · Altyazı (Otomatik · Satırlar · Toplu düzelt · Görünüm) · Yayın › Kapak (Kare · Yazı · Stil · Efekt)
  - Önceki düzen (⋯ › "Alt başlıklar: yazılı (önceki)") aynen: orada sayfalar tek parça.
- **Ayar arama** (yol satırında 🔍): "giriş", "keskinlik", "yavaş", "müzik"… yaz → dokun = o sayfa açılır.
- **Klip hızı** (Düzenle › Klip › Hız, 0,25×–4×): kök tasarım — hız zaman çizgisine hesap olarak sokulmaz; klibin kırpılmış aralığı Media3'ün ses+görüntü hız etkisiyle (Effects.createExperimentalSpeedChangingEffect) bir kez yeni dosyaya işlenir, sonra her yer (önizleme, geçiş, katman, dışa aktarma) onu normal klip görür. Özgün dosya silinmez; 1× geri döndürür. Takip yazısı (kaynak zamanlı) hızda kaldırılır.
- **Müzik yumuşak giriş/çıkış** (Ses › Müzik › Ayar): 0–6 sn giriş, 0–8 sn çıkış; dışa aktarmada ses zarfında (çıktı zamanıyla, döngü ve sarmada doğru), önizlemede ses düzeyinde. "Videoda ses varken otomatik kıs" anahtarı da burada.
- **Kayıtlı ayarlarım** (Görünüm › Hazır): o anki görünüm ayarlarını adıyla sakla, başka projede tek dokunuşla uygula (giriş/abone/kapanış stili, geçiş, logo yeri, kapak stili, altyazı görünümü, renk/filtre/efekt, hazır müzik türü). Yedeğe de girer.

## 3.69 (20.11.2026, deneme · `yerel-android` dalı): kartta Giriş yazısı, çift dokun = ilgili bölüm, Renk › Hazır yatay
- **Giriş yazısı simgesi** (klip kartının alt satırında): dokun = giriş başlığını aç/kapat (açıkken sarı dolu); çift dokun = Görünüm › Şablon › Giriş sayfası.
- **Kart simgelerinde çift dokunuş = ilgili bölüm:** ses → Ses › Klip sesi · süre/kırp → Kırp sayfası · Nesne kes → Düzenle › Nesne · kadraj → Kadraj sayfası · böl/çoğalt → Düzenle › Klip · hareket → Görünüm › Hareket · Doğal → Renk › Hazır · takip → Metin › Takip · yazı ve hareketli yazı → Metin › Yazı. Tek dokunuş hızlı işlem olarak kalır. Sil'de çift dokunuş yok (güvenlik; iki ayrı dokunuşla silinir).
- **Renk › Hazır ayarlar:** iki sütunlu kartlar yerine yatay, yuvarlak simgeli seçenekler (yana kayar); başta "Yok" = ayarları sıfırla.

## 3.68 (20.11.2026, deneme · `yerel-android` dalı): hareket oku gerçek yönü gösterir, kartta Nesne kes · Doğal · Takip, dokun = dur, kalın başlık bandı
- **Hareket oku:** klibin gerçek yönünü gösterir (otomatik ve yakınlaş = yakınlaş oku; sihir değneği kalktı). Dokun = sıradaki yön (Yakınlaş → Uzaklaş → Sağa → Sola → Yukarı → Aşağı → Sabit), basılı tut = Hareket sayfası.
- **Kartın üst satırı:** yeşil kırp simgesi artık **Nesne kes** (Düzenle › Nesne: fotoğraftan ve videodan kesme). Kadraj (Sığdır/Doldur) ayrı tuval simgesinde. Sarı kırp = süre/kırpma (aynen).
- **Kartın alt satırı:** **Doğal** renk (Renk › Hazır › Doğal; dokun = uygula / kaldır, uygulanınca yeşil dolu) · **Takip yazısı** (yalnız videolarda; Metin › Takip açılır) · hareket · yazı · hareketli yazı. Kart 264 dp'ye büyüdü; alt bilgi kısaldı (süre, sessiz).
- **Önizlemeye dokun = dur / oynat** (akıcı oynatmada da). Yazıya/katmana dokunmak eskisi gibi onu seçer.
- **Bölüm başlıkları (Kart):** bant dikeyde daha kalın; "Göster/Gizle" yazılı hap yerine yuvarlak ok simgesi. Önceki yazılı hap ⋯ › "Alt başlıklar: yazılı (önceki)" seçiliyken kalır.

## 3.67 (20.11.2026, deneme · `yerel-android` dalı): seçili klip kartında hareket yönü, yazı ekle, hareketli yazı; sade ve sabit Geri
- **Seçili klip kartı** büyüdü (200 → 248 dp genişlik, 158 → 176 dp yükseklik). Alt satırda bilgi + yeni simgeler:
  - **Hareket yönü:** klibin nasıl hareket edeceğini gösteren ok (sağa, sola, yukarı, aşağı, yakınlaş, uzaklaş, sabit; otomatikte sihir değneği). Dokun = Hareket sayfası (Görünüm › Hareket'in aynısı) açılır pencerede.
  - **Yazı ekle (T):** oynatıcının olduğu andan yazı ekler, Metin › Yazı'ya geçer, düzenleme açılır.
  - **Hareketli yazı:** "Alıntı açılır" yazısını ekler, Metin › Yazı'ya geçer (stil ve renk oradan değişir).
- **Alt menüdeki Geri:** zeminsiz, soluk ince ok + bölüm adı; artık sabit — alt menüler yanında kayar, Geri yerinde kalır.

## 3.66 (20.11.2026, deneme · `yerel-android` dalı): önizlemede yazı tutamaçları, hızlı düğmeler, listeden düzenle/sil
- **Önizlemede seçili katman (InShot gibi):** ✕ sil (sol üst) · ✎ düzenle (sağ üst, yazılarda) · ⧉ çoğalt (sol alt) · ⤡ tek parmakla büyüt + döndür (sağ alt). Taşıma ve iki parmakla büyütme/döndürme aynen çalışır. Tutamaçlar önizlemenin içinde kalır.
- **Hızlı düğmeler** (önizlemenin üst kenarında; yazı üstteyse altta): Küçült · Büyüt · Ortala · Düzelt (açı 0) · Öne al · Kilitle (kilitliyken yalnız "Kilidi aç").
- **Metin › Yazı'da da çalışır:** önceden önizlemede seçme/taşıma yalnız Katman bölümündeydi; artık Metin › Yazı açıkken de yazıya dokun-sürükle-büyüt yapılır, dokununca hızlı düzenleme açılır.
- **Videodaki yazılar listesi:** her satırda ✎ Düzenle ve 🗑 Sil (iki dokunuş; geri al ile geri gelir). Satıra dokunmak seçer ve oraya gider.

## 3.65 (20.11.2026, deneme · `yerel-android` dalı): Katman bölümünde kaydırma sona gelince durmuyor
- **Kök neden:** çizelge (kendi satırlarını kaydıran) ile altındaki bölümler iki ayrı kayan alandı, sayfanın kendisi kaymıyordu; satırlar sona gelince kaydırma devredecek yer bulamıyordu.
- **Çözüm:** başlık sabit, altı tek kayan sayfa: çizelge kendi satırlarını kaydırır, sona gelince kalan kaydırma sayfaya geçer ve alttaki bölümler (hazır yazılar, katman ayarları) gelir. Çizelge yüksekliği tutamacı aynen çalışır.

## 3.64 (20.11.2026, deneme · `yerel-android` dalı): hareketli hazır yazılar
- **Metin › Yazı ve Katman › Yazı ekle › "Hareketli yazılar":** dokun = oynatıcının olduğu andan 4 sn'lik yazı eklenir (metin hemen düzenlenir; renk Renk ayarından değişir, beyaz seçilirse stilin kendi rengi).
- **Stiller:** Alıntı açılır (turuncu yuvarlak kare + tırnak; beyaz kutu karenin içinden sağa açılır, siyah yazı) · Kayan şerit (renkli şerit soldan süpürür, yazı içinden kayar; 2. satır beyaz şerit) · Daktilo (koyu kutuda harf harf, renkli imleç) · Çift kutu (renkli ve beyaz kutu sırayla soldan kayar) · Altı çizili (yazı aşağıdan yükselir, çizgi ortadan uzar). Farklı renk örnekleri hazır.
- **Yöntem:** hareket bitmapin kendisinde, kare kare çizilir (30 kare giriş, ters çıkış); bütün kareler aynı boyutta, konum kaymaz. Önizleme ve dışa aktarma aynı `KatmanCizim.bitmap(o, W, H, adim)` karesini kullanır; dışa aktarmada yalnız son kare bellekte tutulur. Seçili yazı duraklatılmışken tam görünür (düzenlenebilsin).

## 3.63 (20.11.2026, deneme · `yerel-android` dalı): siyah-beyaz tema (VITA düzeni) ve tam ekran Kapak düzenleyici
- **Siyah-beyaz tema:** ⋯ › "Siyah-beyaz tema (VITA düzeni)". Zemin siyah, yazı ve simgeler beyaz, vurgular beyaz (renkli simge tonları beyaza döner). Gece ve gündüz temaları aynen duruyor; seçim hatırlanır (`Renk.tema`: 0 gece · 1 gündüz · 2 siyah-beyaz).
- **VITA dizilişi (yalnız bu temada):** üstte ← · proje adı · Kaydet · beyaz "Dışa aktar"; önizlemenin altında süre · başa · önceki · oynat · sonraki · geri al · yinele satırı ve ince zaman çubuğu (videonun üstündeki şeffaf kontrol kalkar). Ana menü/alt menü mantığı (3.61) değişmedi.
- **Kapak kutusu:** büyük klip şeridinin en başında (bütün temalarda). Kapak seçilmişse kapak, değilse ilk klip görünür; dokununca tam ekran Kapak düzenleyici açılır.
- **Kapak düzenleyici:** üstte ✕ · geri al/yinele · Kaydet (JPG, Pictures/Stüdyo); ortada kapak; altında "Videodan seç" (videonun tamamının küçük resim şeridi, kaydır/dokun = o kare kapak olur) / "İçe aktar" (galeriden fotoğraf, uygulamaya kopyalanır); araçlar Şablon · Yazı · Efekt · Öneriler · Foto. Araç sayfalarında ✕ (vazgeç, önceki hale döner) · başlık · ✓ satırı altta.
- **Şablonlar:** Tümü · Klasik · Reels sekmeli ızgara; her şablon projenin kendi kapak karesi ve yazısıyla canlı çizilir (3 Klasik + 13 Reels stili). Yayın › Kapak paneli seçenek olarak duruyor.

## 3.62 (20.11.2026, deneme · `yerel-android` dalı): dışa aktarma çökmesi giderildi, nesne kesme düzeltildi, yerleşik logo
- **Kök neden (cihaz kaydı 3.61.89):** ekrandan kesilen nesne karenin kenarına taşınca bindirme çapası −1…1 dışına çıkıyor, Media3 `IllegalArgumentException` veriyordu → akıcı önizleme yeniden kuruluyor (görüntü titreyip kararıyordu) ve dışa aktarma duruyordu. `Yerlesim.ayar()`: arka plan çapası sınıra çekilir, fark bindirmenin kendi çapasına aktarılır (bitmap aynı yerde kalır); bütün bindirmelerde (zamanlı, logo, klip yazısı, altyazı, takip).
- **Nesne kesme:** saydamlık eşiği 12 → 110 (kırpım bütün fotoğraf oluyor, nesne kareyi kaplayıp bulanık arka planı örtüyordu); düşük saydamlıklı arka plan kalıntısı temizlenir.
- **Yerleşik logo:** "Şefin Doğası" logosu APK'ya gömüldü (`assets/logolar`); uygulama ilk açılışta (silip kursan da) kütüphaneye ekler, aynısı zaten varsa eklemez.

## 3.61 (20.11.2026, deneme · `yerel-android` dalı): alt menü düzeni, yerleşik müzikler, arka planı bulanık nesne kesme, ayarları sakla / yedekle, daha duyarlı takip
- **Alt menüler ana menünün yerinde:** ana menüde bir bölüme dokununca alttaki ana menü kaybolur, yerine o bölümün alt menüleri gelir (başta Geri). Geri: ana menü döner, alt menüler bir üst satıra çıkar. Renk'in ayar düğmeleri (Hazır, Parlaklık, Kontrast… HSL) de bu satırlarda — Renk düğmesinin hemen üstünde (önce panelin tepesindeydi). Tüm bölümlerde aynı.
- **Ekrandaki fotoğraftan nesne kesme:** arka plan kendiliğinden bulanıklaşır, kesilen nesne fotoğraftaki yerinde ve boyutunda ön plana katman olarak gelir. Nesnenin oturması için o klibin otomatik hareketi kapanır. Arka plan bulanıklığı: Düzenle › Nesne'de kaydırıcı (`Klip.arkaBulanik`; dışa aktarma iki geçişli Gauss GL `BulanikEtki`, durağan önizleme aynı görünüm).
- **Yerleşik müzikler (8):** Sakin, Neşeli, Duygusal, Tempolu, Klasik · Kanon / Hareketli / Dramatik / Piyano — web Kareden Videoya'daki sentezin Android karşılığı (`MuzikSentez.kt`); dosya yok, telifsiz, cihazda video süresi kadar üretilir; silip kursan da vardır. **Konuya göre otomatik:** ML Kit görüntü etiketleme (ücretsiz, cihaz içinde) kliplere bakar (doğa → Sakin, insan → Neşeli, çay/sofra → Duygusal, yol → Tempolu, yapı → Klasik) (`MuzikSecici.kt`). Ses › Müzik › Hazır müzikler; Varsayılan ayarlara "Müzik" maddesi (yeni videoya kendiliğinden). Video uzayınca hazır müzik de uzar.
- **Yayın › Ayarlar (yeni):** "Bu projedeki ayarları sakla" — giriş stili, abone, kapanış, geçiş, logo yeri, kapak stili, altyazı görünümü, renk/filtre/efekt ve müzik türü yeni videoların varsayılanı olur (`Ayarlar.kt`). **Yedek:** ayarlar + logo kütüphanesi seçilen bir dosyaya (ör. Belgeler/Stüdyo-yedek.zip) yazılır ve her değişiklikte kendiliğinden güncellenir; uygulama silinip kurulunca ana ekrandan / ⋯ menüsünden "Yedekten geri yükle". Yerleşik logo altyapısı: `assets/logolar/*.png` ilk kurulumda kütüphaneye eklenir.
- **Takip yazısı daha duyarlı:** 400 piksel çözünürlük (önce 256), kaba→ince arama, alt piksel konum, 3 karelik yumuşatma (önce 5; yazı nesnenin gerisinde kalıyordu), şablon daha hızlı uyum.

## 3.60 (20.11.2026, deneme · `yerel-android` dalı): giriş başlıkları profesyonel — web Kareden Videoya çizimi birebir
- **Kök neden:** başlıklar Anton dışında telefonun sistem yazısıyla (Roboto Black), gevşek iç boşluklarla, gölgesiz kutularla ve basitleştirilmiş süslerle çiziliyordu. Web Kareden Videoya ise Barlow Condensed, Montserrat, Anton, Archivo, Bebas Neue ve Playfair Display kullanıyor.
- **Yazı tipleri uygulamaya gömüldü** (SIL OFL, ücretsiz, internetsiz; `res/font`, lisanslar `android/lisanslar/YAZI_TIPLERI.txt`).
- **Reels stilleri (12):** web `drawReels` geometrisi birebir (`BaslikCizim.kt` › `ReelsCiz`): ölçüler, iç boşluklar, gölgeler, tırnak/ünlem/köşe süsleri, yazı sığdırma (satır ve genişliğe göre küçültme). Her parça kendi yerinde ayrı bitmap; 130 ms arayla gelir, süs parçaları (tırnak, ünlem, etiket, köşe) büyüyerek, satırlar sırayla iki yandan kayarak; çıkış geldiği yöne. Renkli kutular (Stüdyo'ya özgü) yeni yazı tipleri ve gölgelerle. Alt/üst konumda blok karenin dışına taşmaz.
- **Klasik stiller (6):** web `drawIntroBody` son karesi (`KlasikCiz`): Sinematik (Playfair, siyah bantlar, turuncu çizgili alt yazı), Alt bant (Archivo, turuncu şerit), Kutulu (Archivo, sol turuncu çizgi, son satır altın), Renkli bant (Anton renkli kutular), Neon (Bebas Neue, pembe ışıma), Sade (Bebas Neue, ince çerçeve).
- **Kapak:** Reels kapakları aynı çizimle; klasik kapak yazıları Montserrat 900 / Archivo 800.
- Önceki çizim kodu (`Reels.bitmapler`, `seritlerEski`) karşılaştırma için duruyor.

## 3.59 (20.11.2026, deneme · `yerel-android` dalı): Efekt bölümü — Filtre · Efekt · Geçiş (önizlemeli)
- **Yeni ana bölüm Efekt** (Renk'in yanında): üç alt sayfa; Geçiş Görünüm'den buraya taşındı (tek yerde). Küçük resimler yalnız üstteki videonun o anki karesinden üretilir; dokun = uygula (durağanken sonuç ana önizlemede, basılı tut = önce), Yok = kaldır, Tümüne = bütün klipler, Güç kaydırıcısı.
- **Filtre (5):** Sinematik, Altın saat, Vintage, Siyah-beyaz, Serin mat — renk ayarıyla aynı 3B LUT'ta (`RenkLut.filtrele`, `Klip.filtre/filtreGuc`), dışa aktarma ve önizleme aynı.
- **Efekt (5):** Vinyet, Film greni (kareden kareye değişir), Sinema şeritleri, Parıltı, Retro VHS — çıkış çözünürlüğünde GL (`Efekt.kt`, `EfektEtki`); küçük resim/durağan önizleme aynı formülün işlemci karşılığı (`Efektler.uygula`). `Klip.efekt/efektGuc`.
- **Geçiş › Popüler (5):** Döndür, Bulanık, Silme, Dalış, Işık sızması — üst üste binen geçiş motoruna dokunmadan, Siyahtan/Parlama/Yakınlaşma gibi klip sınırında (giden klibin sonu + gelen klibin başı, `Gecisler.hesapla`). Dışa aktarma: döndürme/dalış `MatrixTransformation` (piksel uzayında döndürme), bulanık/silme/sızma `KenarEtki` (GL); önizleme aynı değerlerle (bulanıklık Android 12+). Geçişler panelinde kategori hapları: Popüler · Temel · Akıcı · Işık ve hareket.
- Görünüm artık: Şablon · Hareket.

## 3.58 (20.11.2026, deneme · `yerel-android` dalı): Kapak yeniden, kısa metin düzenleyici, kayan ana menü, Kadraj klip simgesinde
- **Görünüm › Kadraj kaldırıldı:** Düzenle › Klip'te aynısı yoktu (yalnız önizlemede sürükleme/çift dokunuş). Özellik kaybolmasın diye Sığdır/Doldur, Tümüne ve Ortala seçili klibin resmindeki kadraj simgesinden açılan Geçişler biçimli sayfaya taşındı.
- **Kapak (Yayın):** üstte büyük kapak önizlemesi + "Kapağı JPG kaydet"; altında görsel alt düğmeler: Kare (önizlemedeki kare, öneriler yana kayan resimler) · Yazı (metin düzenleyici, vurgu rengi, boyut, yeri) · Stil (her stil kapağın kendisiyle çizilmiş küçük resim; Klasik + Reels bantları) · Efekt (Canlı renkler, Logo yuvarlak aç/kapa).
- **Kısa metin düzenleyici (`MetinEditoru`):** Enter = alt satır / yeni blok (| yazmaya gerek yok, kayıtta | olarak saklanır); "B Kalın · vurgu" seçili kelimeyi vurgular (yıldız yazmaya gerek yok, kutuda yıldız görünmez, vurgu kalın ve renkli görünür); A−/A+ yazı boyutu; renk daireleri. Kapak yazısı/bantları ve Giriş başlığı/bantlarında.
- **Giriş başlığı yazı boyutu (yeni):** `Giris.olcek` 0,6–1,6 (klasik ve Reels kutuları).
- **Ana menü:** yana kayar, düğmeler geniş (74 dp), büyük simge; seçili bölüm hafif renkli zeminli.

## 3.57 (20.11.2026, deneme · `yerel-android` dalı): Varsayılan ayarlar Geçişler düzeninde, belirgin Gizle/Göster düğmesi
- **Görünüm › Şablon › Varsayılan:** maddeler artık sıkışmıyor — Geçişler paneli gibi yuvarlak simgeler, yana (sağa/sola) kayar; seçili maddede beyaz halka + ✓, altta kısa açıklama.
- **Gizle/Göster:** bölüm başlıklarındaki küçük ▴/▾ işareti yerine renk tonlu çerçeveli hap (ok + "Gizle"/"Göster", `GizleGosterDugmesi`). Aynı düğme Katmanlar başlığında, Nesne kes satırında ve Düzenle › Klip'teki şerit gizle/göster'de de kullanılıyor.

## 3.56 (19.11.2026, deneme · `yerel-android` dalı): Renk sonucu ana önizlemede, ara kutu kaldırıldı
- **Kök neden:** durağan oynatıcı ham görüntüyü gösterdiği için Renk sayfasında ayrı bir önce/sonra kutusu vardı; üstteki önizleme ve klip şeridiyle birlikte gereksiz yer kaplıyordu. Artık Renk bölümündeyken ayarlı kare doğrudan ana önizlemede, oynatıcı görüntüsünün tam üstünde (aynı kadraj/hareket yerleşimi) gösterilir; videoda o anki kare (`Medya.kareTam`), fotoğrafta fotoğrafın kendisi. Önizlemeye basılı tut = önce. Oynarken akıcı önizleme rengi zaten uygular.
- Renk sayfası: başlık + ayar düğmeleri + seçili ayar; ara kutu yok.

## 3.55 (19.11.2026, deneme · `yerel-android` dalı): logo varsayılan ayarlarda, Altyazı alt sayfalara bölündü
- **Varsayılan ayarlar › Logo (yeni madde):** uygulanınca projede logo yoksa son kullanılan kütüphane logosu (yoksa en yeni eklenen) sol üst · %14 · opaklık %70 olarak eklenir; logo varsa konumu/boyutu/opaklığı bu varsayılana döner. Kütüphane boşsa uyarı verir. Eski kayıtlı varsayılan seçimine "Logo" bir kez kendiliğinden eklenir (istenirse kapatılır). Son kullanılan logo `ayar/sonLogo`'da tutulur.
- **Metin › Altyazı:** dört kart tek sayfada yığılmıyor — alt düğmeler: Otomatik · Satırlar · Toplu düzelt · Görünüm (Satırlar'da altyazı varken ✓). Otomatik altyazı bitince Satırlar'a geçer. İçerik değişmedi.

## 3.54 (19.11.2026, deneme · `yerel-android` dalı): Görünüm › Şablon alt sayfalara bölündü
- Şablon'daki beş kart artık tek sayfada alt alta yığılmıyor. Renk bölümündeki gibi yatay alt düğmeler var: Paketler (Biçim + hazır paketler) · Giriş · Abone · Kapanış · Varsayılan; her biri kendi sayfasında açılıyor. Giriş, Abone ve Kapanış açıkken düğmede ✓ görünüyor. İçerik ve ayarlar değişmedi. Eski yazılı menü görünümü (⋯) aynen duruyor.

## 3.53 (19.11.2026, deneme · `yerel-android` dalı): ferah menü düzeni, Renk ana bölümü, ortak yazı, ✓ işareti
- **Gezinme hatası (kök neden):** açık bir bölüme ya da alt düğmeye ikinci kez dokunmak paneli kapatıyordu; kapalı panel Düzenle › Klip görünümünü gösterdiği için ör. Renk'e ikinci dokunuş Klip'e gidiyordu. Artık her düğme her zaman yalnız kendi bölümünü açar (panel Geri tuşuyla kapanır).
- **Renk ana bölümü (alt çubukta, Görünüm'den çıktı):** üstte sabit Geçişler biçimli başlık (Tümüne · Renk ✓ · Sıfırla), önce/sonra görüntüsü ve yatay kayan ayar düğmeleri; her ayar kendi sayfasında (− kaydırıcı +, Sıfırla). Ayarlar: Hazır · Parlaklık · Kontrast · Vurgular · **Beyazlar (yeni)** · Gölgeler · **Keskinlik (yeni)** · Doygunluk · Sıcaklık · Ton · HSL. Aşağı kaydırınca önizleme ve düğmeler yerinde kalır (`RenkBolumu.kt`).
  - Beyazlar: LUT içinde, yalnız en parlak alanlar. Keskinlik: LUT'a giremez → çıkış çözünürlüğünde GL keskinleştirme (`Keskin.kt`, `KeskinEtki`); önce/sonra görüntüsü aynı formülle. Eski projeler değişmez (yeni alanlar 0).
- **✓ işareti:** bir menüde ayar değişince alt menü adının ve ana bölüm adının yanında yeşil ✓ (bu düzenleme oturumunda); Renk düğmelerinde değeri 0 olmayan ayarda kalıcı ✓.
- **Düzenle › Klip:** "Kırp ve ekle" kartı kalktı. Kırp (video) / Süre (fotoğraf) seçili klibin üstündeki kırp simgesinden açılan Geçişler biçimli sayfada. Ekleme şeritteki +'da.
- **Biçim** (YouTube · Reels · Kare · Boş proje) → Görünüm › Şablon › Hazır paketler'in ilk satırı.
- **Ortak yazı:** Metin › Yazı ile Katman › Yazı ekle aynı veriyi (yazı katmanları) ve aynı ayarları kullanır; ayrı yazı sayfası yok. Eski klip yazıları (`Klip.yazi`) silinmez, görünmeye devam eder; "Yazı katmanına taşı" ile tek dokunuşta katmana geçer (`klipYazisiniKatmanaTasi`).
- **Logo varsayılanı:** sol üst · %14 · opaklık %70 (yalnız yeni eklenen logolar; kayıtlı projelerdeki logo ayarı korunur).
- Rafa kalkan: Kapak › yapay zekâyla başlık/açıklama/etiket üretimi.

## 3.52 (15.11.2026, deneme · `yerel-android` dalı): katman olarak eklenen müzik de otomatik kısılır
- **Kök neden:** otomatik kısma yalnız Ses › Müzik'teki ana müziğe uygulanıyordu; "Ses" katmanı olarak eklenen müzik her zaman aynı seviyede çalıyordu. Artık ses katmanları da videonun kendi sesi (açık) ya da seslendirme varken %35'e iner — önizlemede (katman oynatıcısı) ve dışa aktarmada (zarf işleyicisi, katmanın başlangıç zamanına göre `ofsUs`).
- Ses penceresinde katman başına aç/kapat (`KatmanOge.otoKis`, varsayılan açık). Kapatılan ses katmanı (ör. konuşma kaydı) ise ana müziği kısar.

## 3.51 (15.11.2026, deneme · `yerel-android` dalı): eklenen müzikte (ses katmanı) menü geri geldi
- 3.48'de ses katmanı çubuğunun ortasına dokunmak menü yerine ses penceresini açıyordu → menü "kaybolmuştu". Artık ses dahil tüm katmanlarda ortaya/uca dokunmak aynı tam menüyü açar: Klip başı · Video başı · Klip sonu · Video sonu · Videonun başına taşı (dokunulan uca göre sıralı); ses katmanında ayrıca "Ses düzeyi" (pencereyi açar).

## 3.50 (15.11.2026, deneme · `yerel-android` dalı): uç menüsü her yerde, süre düğmeleri, ses katmanı da müziği kısar
- **Uçlar menüsü araç sırasında:** seçili katman için "Uçlar" düğmesi — Klip başı · Video başı · Klip sonu · Video sonu · Videonun başına taşı (her zaman erişilir). Çubuğun ucuna dokunmak da açar; artık seçili olmayan çubukta da tek dokunuşla (seç + menü).
- **Süreyi düğmeyle genişlet/daralt:** araç sırasında "Kısalt / Uzat" (±0,5 sn); seçili katman ayarlarında ve "Yazıyı düzenle" penceresinde Baş −/+ ve Son −/+ (±0,5 sn) (`katmanUcKaydir`). Çubuğun uçlarını sürükleyerek ayarlama aynen duruyor.
- **Müzik kısma:** eklenen ses katmanları (konuşma/ses dosyası) da müziği kısar (önizleme + dışa aktarma); kısma aralıkları hata kaydına yazılır (cihazda doğrulama için).

## 3.49 (15.11.2026, deneme · `yerel-android` dalı): Renk ve ışık (HSL), altyazı toplu düzeltme, ince bant altyazı
- **Görünüm › Renk (yeni):** parlaklık, kontrast, vurgular (sola = patlayan parlak alanları geri çeker), gölgeler, doygunluk, sıcaklık, ton; hazır ayarlar (Patlamayı azalt, Doğal, Canlı, Sıcak, Serin, Yeşili canlandır, Sıfırla); **HSL** — 8 renk bandı (kırmızı…pembe) için ton/doygunluk/ışık. Klip bazında, kart başlığındaki "tümüne uygula" ile bütün kliplere (`Klip.renk`, `RenkAyari.kt`).
  - Tek dönüşüm fonksiyonu: dışa aktarma ve akıcı önizlemede 33³ 3B LUT (Media3 `SingleColorLut`), panelde önce/sonra görüntüsü (basılı tut = önce) aynı fonksiyonla — birebir aynı sonuç.
- **Altyazı toplu düzelt:** tüm satırlar tek metin kutusunda (her satır bir altyazı, zamanlar korunur) — Kopyala → yapay zekâya düzelttir → Yapıştır → Uygula. Kelime zamanları satır süresine eşit dağıtılır.
- **İnce siyah bant altyazı (yeni varsayılan):** boydan boya yarı saydam siyah bant, en altta kenara sıfır; "Altyazı görünümü"nden diğer stiller seçilebilir.

## 3.48 (15.11.2026, deneme · `yerel-android` dalı): müzik kısma kök düzeltmesi, ses penceresi, çoğalt sağa, dalga titremesi
- **Konuşma varken müzik kısılmıyordu (kök neden):** oynatma akıcı önizlemede (CompositionPlayer) yapılıyor ve orada müzik sabit sesteydi; seslendirme (konuşma) aralıkları da hesaba katılmıyordu. Artık zarf işleyicisi önizlemede de çalışır (sarma anı `MuzikZarf` ile bildirilir), video sesi olan klipler + seslendirme aralıklarında müzik %35'e iner. Standart oynatıcı yolunda da her tikte uygulanır.
- **Müzik çubuğu yanıp sönüyordu (kök neden):** dalga, çubuğun başından 3 px'te bir örnekleniyordu; kaydırınca örnek noktaları kayıyordu. Örnekleme artık kaynak zamanına sabit (tüm ses dalgaları).
- **Müzik / ses çubuğuna dokun = ses penceresi:** düzey kaydırıcısı, Sesi kapat/aç, %50, otomatik kısma, "Müzik ayarları". Ses katmanında çubuğun ortası pencereyi, uçları Klip/Video sonu menüsünü açar.
- **Varsayılan müzik sesi %50.**
- **Çoğalt:** kopya orijinalin hemen sağına (bittiği yerden) aynı konumda eklenir, sırada hemen üstünde; ses kopyası kaldığı yerden devam eder.
- **Kesilen nesne kendi klibinde kalır:** fotoğraftan/videodan kesilen nesnenin süresi bulunduğu klibin sonunda biter.

## 3.47 (15.11.2026, deneme · `yerel-android` dalı): iki tür gizleme, başlıktan taşıma, geri al, ekrandan nesne kesme
- **İki tür gizleme:** "Videoda gizle" (katman videoda görünmez/çalmaz; satırı soluk durur) ve "Satırı gizle" (yalnız çizelgede gizlenir, videoda görünür/çalar — geniş çalışma alanı için; `KatmanOge.satirGizli`). 3.46'daki "gizlenince satır da kaybolur" davranışı bunun yerine geldi.
- **Gizli satırları göster:** çizelgenin sol üst köşesi ("Zaman") gizli satır varsa "◌ Gizli N" olur; dokun = göster/gizle (gizli satırlar ◌ ile soluk). "Ses ve görünürlük · tümü" kartında satır adına dokunmak da satırı geri açar.
- **Satır başlığından öne/arkaya:** katman satırının soldaki başlığını yukarı/aşağı sürükle — satır parmağı izler, bırakınca yukarı = öne, aşağı = arkaya.
- **Geri al / Yinele** Katmanlar başlığında.
- **Nesne kes:** "Ekrandaki fotoğraftan" (oynatıcının üstündeki fotoğraf klibi) · "Galeriden fotoğraf"; videoda "Ekrandaki videodan" (bu andan) · "Galeriden video" (baştan, seçilen süre kadar).

## 3.46 (15.11.2026, deneme · `yerel-android` dalı): sade Katman bölümü, klip taşıma, müzik otomatik kısma, katman altyazısı
- **Gereksiz "Katmanlar" satırı kaldırıldı:** tek alt başlığı olan bölümde (Katman) alt menünün üstündeki alt başlık satırı artık gösterilmez.
- **Çizelgede klibi parmakla taşı:** video klibine basılı tut, sağa/sola sürükle, bırak — bırakılan yerdeki sıraya geçer (sürüklerken klip turuncu çerçeveyle kalkar).
- **Müzik otomatik kısılır:** kendi sesi olan (ve sesi açık) kliplerde müzik %35'e iner; önizlemede oynatıcı, dışa aktarmada yumuşak geçişli zarf işleyicisi (`ZarfIsleyici`) yapar. "Ses ve görünürlük · tümü" kartında aç/kapat (`Muzik.otoKis`, varsayılan açık).
- **Gizlenen katmanın satırı da gizlenir;** geri açmak için "Ses ve görünürlük · tümü" › Göster.
- **Katman altyazısı (altyapı):** her görsel katmanın (yazı, logo, görsel, çıkartma) altına altyazı yazılabilir; katman bitmapine eklendiği için katmanla birlikte taşınır, büyür ve dışa aktarılır. Seçili katman ayarlarında ve "Yazıyı düzenle" penceresinde (`KatmanOge.altyazi`).

## 3.45 (15.11.2026, deneme · `yerel-android` dalı): katmanları parmakla taşıma, sıradakine geçme, nesne kesme, doldur
- **Önizlemede tek hareketle taşı:** katmanın (yazı, logo, görsel, çıkartma) üstüne bas ve hemen sürükle — önce seçmek gerekmez; dokunma alanı katmanın çevresinde genişletildi; iki parmak = büyüt/döndür; kısa dokunuş = seç (yazıysa düzenle); boş yerde sürükle = klibi kadrajda kaydır.
- **Çizelgede sürükleyerek öne/arkaya:** katman çubuğunu yukarı sürükle = öne al, aşağı = arkaya gönder. Seçiliyken doğrudan, seçili değilken basılı tutup sürükle (yatay kaydırma çizelgeyi sarmaya devam eder).
- **Videonun başına taşı:** çubuğun ucuna dokununca açılan menüde (Klip sonu · Video sonu · Videonun başına taşı) — müzik/ses dahil tüm katmanlar; süre korunur.
- **Simgeye her dokunuş = sıradaki öğe:** aynı türdeki katmanlar (yazılar, sesler, görseller…) arasında zamana göre sırayla geçer ve sahnesine gider; Klip yazısı, Takip yazısı ve Ses kaydı satırlarında da.
- **Nesne kes Katmanlar'da:** fotoğraftan/videodan nesne kesme paneli Katmanlar sayfasında açılır kapanır kart olarak; önizlemede seçili nesne sürüklenir.
- **Doldur / Sığdır:** araç sırasında oynatıcının üstündeki klibi tek dokunuşla kadraja doldurur, tekrar dokununca eski haline döner.
- **Araç sırası görünümü:** "Alt alta" düğmesi ile tek satır (sağa kayar) ↔ alt alta satırlar; seçim hatırlanır.

## 3.44 (15.11.2026, deneme · `yerel-android` dalı): katmanlarda gezinme ve hızlı düzenleme
- **Simgeye dokun = sahnesine git:** çizelgede soldaki katman simgesine/adına dokununca katman seçilir ve oynatıcı o katmanın başına gider (tüm katmanlar).
- **Ekrandaki yazıya dokun:** hangi bölümde olursan ol, önizlemedeki katmana dokununca Katman bölümü açılır ve o katman seçilir; yazıysa "Yazıyı düzenle" penceresi açılır (metin + stil).
- **Yazı ekle → hemen düzenle:** eklenen yazı için düzenleme penceresi kendiliğinden açılır; çizelgede seçili yazı çubuğunun ortasına dokunmak da açar.
- **Ekranda görünüp katmanlarda olmayan yazılar:** Klip yazısı ve Takip yazısı artık çizelgede kendi satırlarında; çubuğa dokun = o sahneye git ve ayar penceresi aç.
- **Takip yazısı Katmanlar bölümünde:** "Katman ekle" sırasında "Takip yazısı" düğmesi (bu klip için takip paneli; nokta seçimi için pencere kapanır, önizlemeye dokunulur).
- **Klip sonu / Video sonu (InShot tarzı):** seçili katman çubuğunun sağ ucuna dokun = Klip sonu · Video sonu; sol ucuna = Klip başı · Video başı. Klip sonu zaten seçiliyse bir sonraki klibin sonuna uzar.

## 3.43 (15.11.2026, deneme · `yerel-android` dalı): katman çökmesi, ses/görünürlük, otomatik baş-son
- **Metni iki parmakla büyütürken çökme (kök düzeltme):** iki parmak hareketi bazen NaN/sonsuz değer üretiyor, bu katmana yazılıp çizimde `roundToInt()` uygulamayı çökertiyordu; projeye kaydolduğu için tekrar açılışta da çökebiliyordu. Hareket değerleri süzülür, her katman yazımında tüm sayılar temizlenip sınırlanır (`katmanTemiz`), açılışta bozuk kayıtlar onarılır. Değişiklik işlevindeki hata da yakalanır.
- **Ses ve görünürlük · tümü kartı:** müzik ve her katman tek satırda — ses (müzik, ses katmanı) ya da opaklık (logo, görsel, yazı) kaydırıcısı, Kapat/Aç ya da Gizle/Göster, "Tüm video". Satır adına dokun = katmanı seç. Altta "Hepsi tüm video", "Hepsi bu klipte".
- **Otomatik baş/son:** seçili katmanda "Tüm video" (artık ses katmanında da; ses kaynağı yettiği kadar), "Bu klip boyunca", "Şimdiden sona", "Baştan şimdiye", Sessiz/Gizle. Çizelge araç sırasına da "Tüm video" eklendi.
- **Logo** eklenince varsayılan olarak tüm video boyunca kalır.

## 3.42 (15.11.2026, deneme · `yerel-android` dalı): takip yazısı bitişi, tasarımlı yazılar, klip şeridi önizlemeyle eşleşir
- **Takip yazısı kendiliğinden biter:** izlenen nesne ≈0,7 sn bulunamazsa (sahne/nesne bitti) izleme orada durur, yazı son görüldüğü yerde kaybolur; klip bitince de biter. Bitiş elle de ayarlanır: "Bitiş" kaydırıcısı, "Bitiş: şu an", "Otomatik bitiş" (`Takip.sonMs`, -1 = otomatik).
- **Yazılar artık altyazı gibi değil:** yeni metin stilleri — Başlık (kontur + vurgu çizgisi), Konum (iğne simgeli cam kart), Alt bant, Etiket (hap), Düz. 2. satır küçük alt satır olur; renk = vurgu rengi. Yeni yazı 3 sn kalır, kayarak girip solarak çıkar.
- **Hazır yazılar:** Konum (Kabataş, Ordu), Başlık + tarih, Alt bant, Etiket, Tarih, Yer adı — dokununca oynatıcının olduğu andan eklenir.
- **Sonu bitmeyen yazı düzeltmesi:** seçili katman süresi dışında önizlemede soluk "hayalet" olarak kalıyordu; artık yalnız kendi aralığında görünür.
- **Katman araç sırası:** başta "Yazı ekle"; katman için "Başı=şimdi", "Sonu=şimdi" eklendi; sıra sağa kayar. Katman ekle satırı da sağa kayar.
- **Düzenle › Klip:** seçili klip her zaman oynatıcının üstündeki klip (izlerken ve sararken); klip şeridi etkin klibi ortaya kaydırır.

## 3.41 (15.11.2026, deneme · `yerel-android` dalı): Katmanlar çizelgesi ve önizlemede sürükleme
- **Klip yarıda bitip arkası siyah görünüyordu (kök düzeltme):** uzun klip/katman/ses çubuğu ekrandan genişken Compose genişliği ekran genişliğine kırpıyordu. Artık yalnız ekranda görünen parça ölçülüp çiziliyor; kareler/ses dalgası gerçek başa göre diziliyor, kaydırınca birlikte kayıyor. Gizli katmanda büyük `graphicsLayer` yerine renk saydamlığı.
- **Video klibi seçilince araçlar soluk kalıyordu:** Katmanlar çizelgesinde video klibine dokununca klip seçilir (beyaz çerçeve) ve aynı araç sırası klibe uygulanır: Böl · Çoğalt · Öne al · Sona al · Sessiz/Sesi aç · Bırak · Sil (onaylı). Katman seçilince araçlar yeniden katmana döner.
- **Önizlemede görüntüyü sürükleyip kaydırma Katman bölümünde de çalışır** (katman seçili değilken; seçiliyse sürükleme katmanı taşır). Klip bölümündeki davranış aynı, kod ortak.

## 3.40 (15.11.2026, deneme · `yerel-android` dalı): önizleme fotoğraf renkleri kayıtla aynı
**Test:** derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Belirti (Ahmet, 3.39):** klip "beyazla" başlıyor, ~1 sn sonra doygunluk geliyor. 3.38 dışa aktarma testinde bu yoktu (Ahmet: "parlama da renklenme olmadı").
- **Kayıt (3.39.65):** hata yok; önizlemede ve iki 4K dışa aktarmada (Akış, Erime) bütün geçişler tam çizildi (24 kare). Kayıt rengi göremez.
- **Kök neden [Muhtemel]:** Media3 fotoğrafı dışa aktarmada sRGB'ye çevirerek, ▶ önizlemede çevirmeden (Display P3 bitmap) açıyor. 3.38'de geçiş kodu önizlemede de çevirmiyordu, ama P3 bitmapin GPU'ya yükleniş yolu Media3'ünkinden farklı (bantlı `texSubImage2D` / tek `texImage2D`); önizlemede geçişteki fotoğrafla gövdesi arasında renk farkı kalması bununla açıklanabilir. Dışa aktarmada iki taraf da sRGB olduğu için 3.38'de düzelmişti.
- **Çözüm:** ▶ önizlemenin fotoğraf çözücüsü de sRGB'ye çevirir (`srgbResimCozucu`); geçiş kodu her zaman sRGB açar. Önizleme, dışa aktarma ve geçiş fotoğrafı artık aynı renkte; P3 yükleme farkına yer kalmaz. Ek yararı: önizleme renkleri kaydedilen videoyla aynı.
- **Cihaz testi (Ahmet):** ▶ önizlemede ve kaydedilen videoda ayrı ayrı bak; kaydedilen videoda kalırsa 720p gönder.

## 3.39 (15.11.2026, deneme · `yerel-android` dalı): akıcı geçişler — Hafif erime ve Akış
**Test:** tür denetimi geçti; derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Ahmet (3.38 cihaz testi):** dışa aktarmada parlama da renklenme de olmadı.
- **İstek:** kesme hissi vermeyen, bir sonraki kare devamıymış gibi geçiş; hafif bir erime.
- **Geçişler paneline yeni "AKICI" satırı:**
  - **Hafif erime:** Erime'nin sakin hâli; gürültü karşıtlığı yarıya iner, kenar 0,35 genişliğinde yumuşak.
  - **Akış:** giden kare hafifçe yaklaşmayı sürdürür (%5), gelen kare hafif yakından (%4) gelip kendi kadrajına oturur. Karışım ışık değerinde (gamma 2,2) yapılır: sıradan çapraz geçişteki ortada solma/kararma olmaz. Eğri smootherstep: başı ve sonu çok yumuşak. Pencerenin ilk karesi yalnız giden, son karesi yalnız gelen (ölçeksiz) → gövdelerle kesintisiz.
- İkisi de tek tuval yolunda (TekTuval.kt) çizilir; önizleme ve dışa aktarma aynı.
- **Cihaz testi (Ahmet):** Geçişler › AKICI › Akış ve Hafif erime ile ▶ ve 720p dışa aktarma.

## 3.38 (15.11.2026, deneme · `yerel-android` dalı): geçiş bitince renk değişimi
**Test:** tür denetimi geçti; derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Belirti (Ahmet):** kaydedilen videoda geçiş bittikten kısa süre sonra görüntü "renkleniyor".
- **Ölçüm (Ahmet'in Erime 720p videosu, 780 kare, ffmpeg ile her karenin parlaklık/renk/doygunluk ortalaması) [Kesin]:**
  - Tek karede, parlaklık değişmeden yalnız renk sıçrıyor: doygunluk 8,7 → 10,4 (9,200 sn), 9,5 → 11,4 (13,400 sn), 10,1 → 11,9 (17,600 sn); ≈ %16–20 daha canlı.
  - Bu kareler tam geçiş penceresinin sonu (pencereler 8,4–9,2 / 12,6–13,4 / 16,8–17,6 sn); görünür erime pencerenin ortasında bittiği için göze "geçişten sonra" görünüyor.
  - Kodlayıcının anahtar kareleri tam saniyelerde; sıçramalar onlarla çakışmıyor (kodlayıcı kaynaklı değil).
  - Önceki ölçümlerde yalnız parlaklığa bakmıştım; renk sıçraması orada görünmüyordu.
- **Kök neden [Kesin, Media3 kaynağıyla]:** telefon fotoğrafları Display P3 renk profili taşıyor. Media3 dışa aktarmada fotoğrafı sRGB'ye çevirerek açıyor (`DefaultAssetLoaderFactory`: `inPreferredColorSpace = SRGB`); ▶ önizlemede çevirmeden açıyor (`BitmapFactoryImageDecoder`). Geçiş kodu fotoğrafı her iki durumda da çevirmeden açıyordu → dışa aktarmada geçişteki fotoğraf soluk, geçiş bitince Media3'ün açtığı gövde daha canlı.
- **Çözüm:** geçişteki gelen fotoğraf, ana hatla aynı biçimde açılır: dışa aktarmada sRGB'ye çevrilerek, önizlemede çevrilmeden.
- **Açık:** 18,53 sn'de (5. klibin içinde, geçişten uzakta) bir karelik küçük bir renk değişimi daha var (doygunluk 12,0 → 11,2); nedeni henüz belli değil [Tahmin yok]. Yeni videoda kalırsa ayrıca bakılacak.
- **Cihaz testi (Ahmet):** aynı projeyi Erime ile 720p dışa aktar, galeride izle; gönderirsen yeniden ölçerim.

## 3.37 (15.11.2026, deneme · `yerel-android` dalı): takılı kalan "Video oluşturuluyor" bildirimi
- **Belirti (Ahmet, ekran görüntüsü 00:38):** video kaydedildiği hâlde bildirim panelinde "Video oluşturuluyor · Doğrulanıyor…" ve "Duraklat" kalıcı duruyor.
- **Kök neden [Muhtemel]:** iş bitince ilerleme bildirimi hizmetten koparılıp (DETACH) aynı numarayla "bitti"ye güncelleniyordu. Android kısa sürede art arda gelen bildirim güncellemelerini sessizce düşürür; son güncelleme düşünce kaldırılamayan "sürüyor" bildirimi kalıyordu. Videoya etkisi yok [Kesin: kayıtta TAMAM, dosya doğrulandı].
- **Çözüm:** ilerleme bildirimi hizmetle birlikte kaldırılır; sonuç ("Kaydedildi: …") ayrı numarayla 1 sn sonra gösterilir. Uygulama açılırken süren iş yoksa eski takılı bildirim temizlenir.
- **Test:** cihazda: uygulamayı aç (eski bildirim gitmeli), bir video dışa aktar (bitince yalnız "Kaydedildi" kalmalı).

## 3.36 (15.11.2026, deneme · `yerel-android` dalı): ▶ ortadan başlatınca donma ve "göz kırpma"
**Test:** derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Belirti (Ahmet, 3.35.61):** göz kırpma. Kayıtta iki kez "Akıcı önizleme ilerlemedi" (00:06:42, 00:16:01) → standart önizlemeye düşüş. Baştan başlatılan her oynatma sorunsuz.
- **Kök neden:**
  - **[Kesin]** İki donma da ▶ videonun ortasından (43743 ve 21626 ms) başlatılınca oldu; konum 5 sn hiç ilerlemedi (durum hazır, oynuyor). Baştan başlatılan her oynatma çalıştı.
  - **[Kesin]** O projedeki geçiş sesi dizisi (gelen videonun ilk 0,8 sn'si) 18623 ms'de bitiyordu; iki başlangıç noktası da bunun sonrasındaydı.
  - **[Muhtemel, Media3 kaynağına göre]** Bitmiş noktasından başlatılan ses dizisinin ses girişi oluşmuyor; Media3'ün önizleme ses karıştırıcısı (`PlaybackAudioGraphWrapper.processData`) bütün girişler oluşmadan hiç ses işlemiyor → ses saati durur → görüntü donar.
  - **Göz kırpma:** donma sonrası standart önizlemeye geçiş ve standart önizlemenin eski geçiş çizimi [Muhtemel].
- **Çözüm:** her yalnız-ses dizisi (geçiş sesi, seslendirme, ses katmanları, reels efekt sesleri, döngüsüz kısa müzik) videonun sonuna kadar sessizlikle uzatılır; ▶ nereden başlatılırsa başlatılsın her dizi sesini (sessizliğini) verir. Dışa aktarmada sonuç değişmez.
- **Cihaz testi (Ahmet):** 1) Uygulamayı tümüyle kapatıp aç. 2) Bir geçişin ortasında duraklat, yeniden ▶. 3) Sondaki bir klibe dokun, ▶. 4) Göz kırpma kalırsa hata kaydı + 720p video.

## 3.35 (14.11.2026, deneme · `yerel-android` dalı): klip içi parlama giderildi; Hareket paneli; klip şeridini gizle
**Test:** derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Belirti (Ahmet):** kliplerde 1–2 sn sonra başlayan aydınlanma ("parlama"); fotoğraflarda da, kaydedilen videoda da.
- **Ölçüm (Ahmet'in 720p videosu, 1032 kare, ffmpeg ile her karenin ortalama parlaklığı) [Kesin]:**
  - Geçiş sonlarında sıçrama yok; geçişler yumuşak bitiyor, tek karelik parlama yok.
  - Klip içinde yavaş aydınlanma var: ör. 3. klipte 4 sn'de 81 → 131 (%60), 7. klipte 64 → 88.
  - Kareler gösteriyor ki aydınlanan her klipte görüntü otomatik hareketle **yerden gökyüzüne kayıyor**; aşağı kayan klipte parlaklık tersine düşüyor (90 → 80).
- **Kök neden [Kesin]:** dikey fotoğraf 16:9'a "doldur" ile girince yüksekliğinin ~%40'ı görünüyor; "otomatik" hareket 4 sn'de fotoğrafın neredeyse tamamını tarıyordu. Ekrana giren gökyüzü görüntüyü aydınlatıyordu.
- **Çözüm (Ahmet'in seçimi: 2):** otomatik hareket artık **kaydırmaz**, odak noktasına doğru yavaş yaklaşır (saniyede ~%3; yatay fotoğraf + yüz: yüze yaklaşma, Kareden Videoya'daki gibi). Video: eskisi gibi saniyede %1,5 yaklaşma. Mevcut projeler de kendiliğinden yeni otomatiğe geçer.
- **Odak noktası:** kendisi bulur (yüz → ana nesne → orta). Görünüm › Hareket açıkken önizlemede fotoğrafa **dokununca** yaklaşma oradan başlar; seçili nokta önizlemede halkayla gösterilir (turuncu = senin seçimin, beyaz = otomatik). "Otomatik" ile geri alınır. Nokta yaklaşırken hafifçe ortaya gelir; görüntü kare dışına taşmaz.
- **Hareket paneli (Geçişler paneli biçiminde, TON renkleri):** YAKINLAŞMA: Otomatik · Yakınlaş · Uzaklaş · Sabit; KAYDIRMA: Sağa · Sola · Yukarı · Aşağı (en çok karenin yarısı kadar; kaydırma payı yoksa %15 yakınlaştırıp kaydırır). Solda "Tümüne". Video klipler için de çalışır. Önizleme, dışa aktarma ve geçişteki gelen klip aynı hesabı (Yuz.an) kullanır.
- **Klip şeridini gizle:** Düzenle › Klip'te büyük klip şeridinin üstünde "Gizle / Göster" (göz simgesi); seçim hatırlanır. İzlerken seçili klip büyüyüp şerit kaydığı için göz yoruyordu.
- **Cihaz testi (Ahmet):** 1) Aynı projeyi aç, ▶: kliplerde aydınlanma olmamalı, görüntü yavaşça yaklaşmalı. 2) Görünüm › Hareket: bir fotoğrafta yüz/nesne dışında bir yere dokun, ▶: yaklaşma oraya doğru olmalı. 3) Uzaklaş ve Sağa'yı dene. 4) Düzenle › Klip'te "Gizle"ye bas. 5) 720p dışa aktar, istersen gönder; parlaklığı yeniden ölçerim.

## 3.34 (14.11.2026, deneme · `yerel-android` dalı): geçişin başındaki kısa duraksama
**Test:** tür denetimi geçti; derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Cihaz kaydı (Ahmet, 3.33.59, 14.11 22:00–22:10):** çökme yok, önizleme hiç düşmedi. Erime, Yumuşak (çapraz) ve Kaydır'ın her geçişinde 24 kare çizildi, "hazır değil=0". Sonraki denemelerde geçişler kapatılmış ("tek tuval geçiş: 0"); neyin kötü göründüğü kayıtta yok.
- **Ölçülen kusur [Kesin]:** tanı izinde her geçişin **ilk karesi 47–73 ms** sürüyor, sonraki kareler 0–1 ms. 30 fps'de bir kare 33 ms olduğu için her geçişin başında ~2 karelik duraksama (takılma) oluşuyor.
- **Kök neden [Muhtemel]:** gelen fotoğraf, ilk geçiş karesinde tek seferde ~20 MB'lık dokuya yükleniyor; gelen klibin dönüşüm gölgelendiricileri de ilk kez o karede çalışıyordu.
- **Çözüm:** gelen klip geçişten 2 sn önce kurulur (öğenin ilk 300 ms'si hariç: orası bir önceki geçişin devamı). Hazırlık karelere bölünür: her karede tek adım (bir gölgelendirici ya da fotoğrafın 24 bandından biri), ardından dönüşümler bir kez "ısıtılır". Geçişin ilk karesinde yalnız karışım çizilir. Video gelen klipte de ilk kare ve dönüşümler geçişten önce hazırlanır.
- **Kayıt:** geçiş özetine `kurulum=… ms, ön hazırlık=… adım (en uzun … ms)` eklendi; hazırlık yetişmezse `GEÇ KALDI` yazar. Tanı izi sürüyor.
- **Cihaz testi (Ahmet):** 1) Geçişi Erime yap, baştan ▶: geçişlerin başında takılma olmamalı. 2) Görüntüde başka bir sorun görürsen (ton, göz kırpma, yavaş açılış, kenar…) tek cümleyle yaz. 3) ⋯ › Hata kaydını paylaş.

## 3.33 (14.11.2026, deneme · `yerel-android` dalı): ▶ geçişin içinden başlatılınca önizlemenin düşmesi
**Test:** derleme GitHub Actions'ta; **cihaz testi bekleniyor.**
- **Cihaz kaydı (Ahmet, 3.32.58, 14.11 21:11–21:30):**
  - **Kayıt (dışa aktarma) çalıştı [Kesin]:** 4K, 7 geçişin hepsi 23–24 kare çizildi, "hazır değil=0", HDR H.265 gelen klip `c2.qti.hevc.decoder` ile SDR'ye çevrilerek çözüldü, 9 sn'de TAMAM. Çökme yok.
  - **▶ baştan oynatınca çalıştı [Kesin]:** 5 geçiş art arda 24'er kare, gerçek zamanlı, çökme yok.
  - **▶ bir klibin başından başlatılınca düştü:** "Akıcı önizleme ilerlemedi" (konum 8400 ve 4240), ikincide oturum boyunca kapandı → standart (eski) önizleme göründü.
- **Kök neden:**
  - **[Kesin]** Bir klibe dokununca konum, o klibin zaman çizelgesindeki başlangıcına gider; tek tuval yapısında bu an bir önceki klibin geçiş penceresinin **içidir**. İki düşüş de tam bu durumda oldu.
  - **[Kesin]** Takılma bekçisi 1,5 sn sonra tek ölçüm yapıyordu. İkinci düşüşte konum tam kıpırdamaya başlamıştı (4200 → 4240, 3 kare çizilmiş): oynatma takılmamış, **geç başlamıştı**; bekçi sağlıklı oynatmayı kesti.
  - **[Muhtemel]** Geç başlamanın nedeni: geçişin içinden başlanınca gelen klibin kaynağı ilk karede kuruluyor ve GL iş parçacığı onu bekliyordu (3.32'de 250 ms–1 sn bekleme izni vardı). Gecikmenin tamamının buradan gelip gelmediği kayıttan kesin okunamıyor; bu yüzden aşağıdaki iz eklendi.
- **Çözüm:**
  - Önizlemede GL iş parçacığı gelen klibi **beklemez**: kaynak hazır değilse giden klip çizilir; hazır olunca karışım 200 ms içinde olması gereken yere yumuşakça yetişir (ani belirme yok). Kayıtta (dışa aktarma) davranış aynı: kare kare beklenir.
  - Bekçi 5 sn boyunca ilerleme arar; ilerleme başlayınca bırakır. Geç başlarsa süresi kayda yazılır ("Akıcı önizleme geç başladı: … ms").
  - **Tanı izi:** önizlemede her geçişin ilk 8 karesi için duvar saati ve çizim süresi kayda yazılır (`iz 2→3: [τ=0 +0ms çizim=12ms] …`). Gecikme sürerse hangi adımda olduğu tek kayıtta görünür.
- **Cihaz testi (Ahmet):** 1) Uygulamayı tümüyle kapatıp aç (önizleme o oturumda kapatılmıştı). 2) Baştan ▶: sonuna kadar oynamalı. 3) 3. klibe dokun, ▶: 5 sn içinde oynamaya başlamalı, "standart önizleme" iletisi çıkmamalı. 4) 21:13'te kaydedilen 4K videoyu galeride izle: geçişlerde göz kırpma / geri sarma / parlama var mı? 5) ⋯ › Hata kaydını paylaş.

## 3.32 (14.11.2026, deneme · `yerel-android` dalı): geçişler tek tuvalde — web Kareden Videoya yapısı
**Test:** yeni geçiş dosyası (`TekTuval.kt`) Kotlin derleyicisiyle tür denetiminden geçti; tam derleme GitHub Actions'ta. **Cihaz testi bekleniyor** (aşağıda adımlar).
- **Belirti (Ahmet, 3.31.57 cihaz kaydı 14.11 13:29 / 19:24 / 20:43):** geçişli projede ▶ önizleme kurulur kurulmaz düşüyor: `flush() is valid only at Executing states; currently at Released state` (`CompositionPlaybackThread`, `disablePrewarming`) ve `queueInputBuffer() … Released state` (`ERROR_CODE_DECODING_FAILED`). Önizlemede karanlık nokta / parlama. Önceki sürümlerde göz kırpma ve klip sonunda geri sarma.
- **Kök neden:**
  - **[Kesin]** Kayıtta "geçiş parçası" olan her önizleme kurulumu aynı saniyede düştü; "0 geçiş parçası" olan her kurulum çalıştı, 4K dışa aktarma da tamamlandı. Hata Media3 oynatıcısının içinde: bırakılmış kod çözücü kullanılıyor. 3.24'teki yakalayıcı bunu çözmüyordu, yalnız uygulamayı açık tutuyordu.
  - **[Muhtemel]** Tetikleyen, 3.20'den beri tek diziye ayrı öğe olarak konan 0,8 sn'lik mp4 geçiş parçalarının fotoğraflarla sık nöbetleşmesi (oynatıcı sıradaki videoyu ön ısıtırken çözücüyü erken bırakıyor).
  - **[Muhtemel]** Parlama / ton farkı: parça ikinci kez kodlanmış bir mp4'tü, gövdeler doğrudan kaynaktan okunuyordu.
  - 3.12–3.19'daki göz kırpma ve geri sarma ise iki görüntü dizisi + Media3 çok girişli birleştiriciden geliyordu. Yani geçişi **ayrı dizi ya da ayrı öğe** yapan her yöntem Media3'ün bir sınırına takılıyordu.
- **Çözüm (web Kareden Videoya'daki tek tuval):**
  - Dizide yalnız klipler var: `[A: gövde + son T ms][B: T'den sonrası]`. Geçiş parçası, önbellek (`GecisHazir`, `cache/gecis`) ve "Geçişler hazırlanıyor k/n" beklemesi **kaldırıldı**. Dizi, cihazda çalıştığı kayıtla görülen parçasız biçimde.
  - Geçiş, giden klibin öğesinin son T ms'sinde tek bir GL geçişinde çizilir (`TekTuval.kt` › `TekTuvalGecis`). Gelen klibin kareleri aynı GL bağlamında, aynı saatle bu efektin kendi kaynağından gelir: video için ikinci bir MediaCodec (kendi iş parçacığında, geçişten ~1,5 sn önce hazırlanır), fotoğraf ve kapanış kartı için bitmap.
  - Gelen klip, ana hattaki **aynı Media3 dönüşüm programlarından** (akıllı kadraj + hareket + doldur/sığdır) geçer; geçişin son karesi ile gövdenin ilk karesi aynı geometridedir.
  - **Ton:** fotoğraf ve kapanış kartı Media3'ün resim girdisindeki aynı formülle sRGB → SMPTE 170M çevrilir; video karesi Media3'ün yaptığı gibi olduğu gibi kullanılır; HDR klipte ikinci çözücüden de aynı "SDR'ye çevir" isteği yapılır. Yeniden kodlama olmadığı için gövdeyle ton farkı kalmaz [Muhtemel; cihazda doğrulanacak].
  - Erime, Yumuşak (çapraz), Kaydır, Büyüyerek aynı gölgelendiricide, eski sayılarla (web'deki erime gürültüsü dahil).
  - Zaman çizelgesi (`Proje.ortusme`) değişmedi: logo, altyazı, katman, müzik zamanları aynı. Giden klibin sesi kendi öğesinde sürer; gelen klibin ilk T ms'sinin sesi yalnız-ses dizisiyle çalar (3.22'deki yol).
  - **Pre-roll kullanılmadı, gerek yok [Kesin]:** web'de gelen klip geçişte kendi başlangıcından *önceki* görüntüyü oynatır; Stüdyo'nun çizelgesinde ise gelen klip geçiş boyunca **kendi ilk T ms'sini 1x hızla** oynatır (donma, kare tekrarı yok), gövde T'den devam eder. Pre-roll eklemek çizelgeyi kaydırırdı.
  - Giden klibin kendi yazısı / takip yazısı klip ile birlikte kaybolur; gelen klibin yazısı geçişle birlikte belirir. Logo ve zaman çizelgesi katmanları en üstte sabit kalır.
- **Güvenceler:** gelen kare hazır değilse giden klip olduğu gibi çizilir (yanlış ya da siyah kare basılmaz). İkinci çözücü açılamazsa (ör. iki büyük 4K çözücüye izin verilmezse) sırayla diğer çözücüler denenir; olmazsa gelen klip geçiş sonundaki karesiyle bekler ve kayda "DURAĞAN KARE (yedek)" yazılır. Öğe bitince ikinci çözücü hemen bırakılır.
- **Bilinen sınır:** gelen klipte takip eden yazı varsa geçiş bitince belirir (geçiş sırasında çizilmiyor).
- **Kayıt:** kurulumda her geçiş için tür, süre, pencere, gelen klibin türü/çözünürlüğü/kodeği/HDR ve kaynak aralığı; geçiş bitince `Geçiş 6→7 erime 800 ms [önizleme|kayıt]: … çözücü=…, renk=…, ön çözme=… ms/… kare, ilk kare=…, son kare=…, çizilen=…, hazır değil=…`.
- **Cihaz testi (Ahmet):** 1) Aynı projede (6 fotoğraf + HDR H.265 video + kapanış) geçiş **Erime**, ▶: sonuna kadar oynamalı, "standart önizlemeye dönüldü" iletisi çıkmamalı. 2) 1080p dışa aktar; geçişlerde göz kırpma, geri sarma, parlama var mı bak. 3) Türü Kaydır / Büyüyerek yapıp ▶ ile yinele. 4) ⋯ › Hata kaydını paylaş.

## 3.31 (14.11.2026, deneme · `yerel-android` dalı): Klip görünümü bölüm kartları, kaydırma
- Şeridin altındaki işlevsiz "◎ logo" satırı kaldırıldı.
- Klip görünümü aşağı-yukarı **kayıyor**; alttaki bölümler artık kesilmiyor.
- Bölümler Geçişler paneli biçiminde koyu yuvarlak **kartlarda**, renk tonlu başlıkla: **KLİP** (turuncu; sıra · tür · dosya adı, süre/kırpma, turuncu Ekle) ve **BİÇİM** (sarı; YouTube 16:9 · Reels 9:16 · Kare 1:1 yuvarlak simgeler, oran çerçevesi kendi renginde, seçili beyaz halkalı; pembe **Boş proje**).

## 3.30 (14.11.2026, deneme · `yerel-android` dalı): Ekle ve Biçim, Klip bölümünde; boş YouTube projesi
- Düzenle'de "Ekle" ve "Biçim" alt bölümleri kaldırıldı (Düzenle: Klip · Nesne).
- Klip görünümünde süre/kırpma satırının yanında turuncu yuvarlak **Ekle** düğmesi (şeridin sonundaki + da duruyor).
- Altında **BİÇİM** satırı: 16:9 YouTube · 9:16 Reels · 1:1 Kare (oran çerçeveli simgeler) ve **Boş** — varsayılan ayarlar uygulanmayan yeni bir YouTube (16:9) projesi açar (giriş, abone, kapanış, kadraj, hareket kendiliğinden gelmez; her şeyi sen eklersin). `Proje.bos`.

## 3.29 (14.11.2026, deneme · `yerel-android` dalı): Düzenle › Klip tek sayfa
- **Sorun (Ahmet):** Düzenle'de "Klip" açıkken ve kapalıyken iki ayrı görünüm çıkıyordu (büyük şerit / küçük şerit + yalnız "Fotoğraf süresi" olan Klip kartı).
- **Çözüm:** ikisi tek görünüm: büyük şerit (üstünde ses · böl · çoğalt · sil, oklar) ve hemen altında seçili klibin **süresi** (fotoğraf: − kaydırıcı +, 0,5 sn adım) ya da **kırpma aralığı** (video). Ayrı Klip kartı kaldırıldı. (⋯ › yazılı menü açıkken eski düzen.)

## 3.28 (14.11.2026, deneme · `yerel-android` dalı): Dışa aktar ekranı görsel tasarım (Şablon sayfasıyla aynı)
- Bölümler TON renklerinde: Çözünürlük sarı, Kalite yeşil, Video oluştur turuncu, ilerleme/doğrulama turkuaz, Kesintisiz çalışma pembe, Kaydedildi yeşil.
- Çözünürlük tek satırda renk tonlu büyük yazı (720p · 1080p · 2K · 4K) + boyut, seçilende renkli ve noktalı; desteklenmeyen soluk. Kalite Varsayılan ayarlar biçiminde simgeli.
- Özet (süre, çıktı, tahmini boyut, boş alan) ve sonuç (boyut, süre, işlem) renkli değerli kutularda; yer yetersizse kutu kırmızı. Bitince Oynat · Paylaş · Yeni simgeli.
- Önceki düzen: ⋯ › yazılı menü açıkken eskisi gibi.

## 3.27 (14.11.2026, deneme · `yerel-android` dalı): Şablon sayfası görsel tasarım (Geçişler paneli biçiminde)
- Bölümler kendi renk tonunda, **Varsayılan ayarlardaki TON ailesiyle**: Hazır paketler turuncu, Giriş başlığı sarı, Abone hatırlatıcı pembe, Kapanış turkuaz, Varsayılan ayarlar yeşil. Başlıkta renkli **anahtar** (özelliği aç/kapat); başlığa dokunmak bölümü açar/kapatır.
- Seçenekler yazı yerine **simge ve önizleme**: hazır paketler ve giriş hareketi renk tonlu simgelerle (Varsayılan ayarlar biçimi: seçilende renkli + nokta); giriş başlığı, abone ve kapanış stilleri sağa kayan **önizleme bantlarında** (ilk klibin resmi üstünde gerçek çizim, seçili renk tonlu halkalı); başlık konumu üç minik karede; efekt sesi ve boncuk renkli haplar.
- Süreler/oranlar Geçişler'deki gibi **− kaydırıcı +** (renk tonlu değer).
- Önceki düzen: ⋯ › yazılı menü açıkken eskisi gibi.

## 3.26 (14.11.2026, deneme · `yerel-android` dalı): giriş başlıkları sağa kayan önizleme bandı
- Şablon › Giriş başlığı: bütün başlık stilleri (KLASİK 6 + REELS TARZI 13 — web'deki "Stil önerileri": Beyaz şerit, Kırmızı-beyaz, Sarı ünlem, Yeşil tırnak, Sarı balon, Ünlem kutusu, Dikkat, Yırtık kâğıt, Köşe çerçeve, Üç renk, Kırmızı sekme, Kırmızı alıntı, Renkli kutular) **sağa kayan bant** hâlinde; Geçişler paneli biçiminde (bölüm başlığı, seçili beyaz halkalı, adı altında).
- Her kutu, ilk klibin resmi üstünde **senin başlığınla** ve videodaki konum/boyutla önizlenir (proje biçimine göre yatay/dikey/kare).
- Önceki yazılı seçenek görünümü ⋯ › yazılı menü açıkken duruyor.

## 3.25 (14.11.2026, deneme · `yerel-android` dalı): yeni Geçişler paneli ve klip şeridi (Ahmet'in onayladığı taslak)
- **Geçişler paneli** (klipler arasındaki ⇄ simgesinden ve Görünüm › Geçiş'ten aynı panel): solda "Tümüne", ortada başlık (hangi ara olduğu), sağda ✓ Tamam; altında **süre** (− kaydırıcı +, 0,1 sn adım, 0,2–2,0 sn); TEMEL (Yok, Erime, Yumuşak, Kaydır) ve IŞIK VE HAREKET (Büyüyerek, Siyahtan, Parlama, Yakınlaşma) yuvarlak renkli simgelerle; seçili simge beyaz halkalı.
- **Standart geçiş süresi 0,8 sn** (Kareden Videoya "YouTube anı videosu" hazır ayarıyla aynı; önceki model varsayılanı 0,7 sn idi).
- **Klip şeridi:** seçili klip büyük (200 dp); resmin üstünde renkli küçük simgeler: ses aç/kapat (yalnız sesli video), böl (çizgi klibin içindeyse oradan, değilse ortadan), çoğalt, sil (iki dokunuş). Kenarlarda sola/sağa taşı okları. **Basılı tutup sürükleyerek** taşıma.
- Klip kartından Sola/Sağa/Böl/Çoğalt/Sil düğmeleri kaldırıldı (şeritte).

## 3.24 (14.11.2026, deneme · `yerel-android` dalı): ▶ önizleme Media3'ün varsayılan yoluyla
- **Cihaz kaydı (Ahmet, 13:08–13:09, 3.23):** çökme yok, dışa aktarma TAMAM (4K, 11 sn). Ama ▶ önizleme ~4. saniyede `ERROR_CODE_DECODING_FAILED` verip standart önizlemeye düşüyor; Ahmet önizlemede görüntünün kendisinde karanlık noktalar ve parlama görüyor (bozuk çözülen kareler + standart önizlemeye geçiş [Muhtemel]).
- **Kök neden [Muhtemel]:** 3.23'te kapatılan video ön ısıtması Media3'te yalnız eski Android/test için duran yol; gerçek cihazda kod çözücü hatası veriyor. 3.22'deki çökmeyse, tek görüntü dizisine (3.20'den beri) gereksiz yere **çok girişli** görüntü hattı dayatılmasıyla birlikte görülüyordu.
- **Çözüm:** akıcı önizleme Media3'ün varsayılan kurulumuyla: tek girişli görüntü hattı, ön ısıtma açık (Media3 örnek uygulamasındaki resim+video dizisi önizlemesinin aynısı). Ön ısıtma kapatma dosyası kaldırıldı.
- **Güvence:** önizleme oynatıcısının iç iş parçacığı yine de çökerse uygulama artık kapanmaz; kayda yazılır, standart önizlemeye dönülür. Önizleme hatalarında asıl neden zinciri de kayda yazılır.

## 3.23 (14.11.2026, deneme · `yerel-android` dalı): ▶'da uygulamanın çökmesi giderildi
- **Cihaz kaydı (Ahmet, 12:35, 3.22 çökme kaydı):** `IllegalStateException: flush() is valid only at Executing states; currently at Released state` — `CompositionPlaybackThread`, `RendererHolder.disablePrewarming` → `MediaCodecRenderer.onDisabled`.
- **Kök neden [Kesin]:** Media3 1.8.1 CompositionPlayer, sıradaki video klip için video oluşturucusunu önceden ısıtıyor (prewarming). 3.20'den beri dizide resim ↔ video (geçiş parçası) sık değiştiği için bu yol sürekli çalışıyor; ısıtılan oluşturucu kapatılırken serbest bırakılmış çözücüye `flush()` çağrılıyor ve oynatma iş parçacığında yakalanmayan hata uygulamayı kapatıyor.
- **Çözüm:** akıcı önizlemede video ön ısıtması kapalı (`StudyoOynatici.isinmasiz`; Media3'teki ayar paket içi olduğu için `androidx.media3.transformer` paketinde küçük bir dosya). Dışa aktarma (Transformer) ön ısıtma kullanmaz, etkilenmez.
- Dışa aktarma ekranına girerken geçiş önizleme oynatıcısı da durdurulur (çözücüler dışa aktarmaya kalır).

## 3.22 (14.11.2026, deneme · `yerel-android` dalı): geçiş parçası yalnız görüntü, geçiş sesi ayrı; çökme kaydı
- **Cihaz kaydı (Ahmet, 12:20):** geçiş parçasında `IllegalStateException` — `SequenceAssetLoader.onOutputFormat` (`checkStateNotNull`), `ImageAssetLoader` üzerinden. **Kök neden [Muhtemel]:** parçada iki dizi vardı ve her ikisine "zorunlu ses izi" konmuştu; resim klibinde Media3 bu sahte ses izini, öbür dizi hazır olmadan istiyor ve boş dönünce durumu bozuluyor (aynı geçiş bir kez geçti, bir kez düştü = yarış). Videolu projedeki "Asset loader error" da ses tarafında olabilir [Tahmin].
- **Çözüm:** geçiş parçası artık **yalnız görüntü** (ses izi yok, zorunlu ses yok). Geçiş süresince giden klibin son T ms'sinin ve gelen klibin ilk T ms'sinin sesi ana videoda iki ayrı **yalnız-ses dizisiyle** aynı anda çalar (müzik/seslendirmede yıllardır kullanılan yol). Önbellek anahtarı g322 (eski sesli parçalar yeniden işlenir).
- **Çökme kaydı:** uygulama beklenmedik biçimde kapanırsa nedeni artık hata kaydına "ÇÖKME" diye yazılır (önceden hiç yazılmıyordu; Ahmet'in "uygulama kapanıyor" bildirimi kayıtta görünmüyordu).

## 3.21 (14.11.2026, deneme · `yerel-android` dalı): geçiş parçası hatası — neden kaydı + yedek yöntem
- **Hata (Ahmet, 3.20):** "Geçiş hazırlanamadı: ERROR_CODE_FAILED_RUNTIME_CHECK". Bu kod Media3'te iç denetimde yakalanan her türlü çalışma hatasının ortak adıdır; asıl neden (cause) kayda yazılmıyordu, bu yüzden kök neden henüz bilinmiyor [Kesin].
- Artık asıl neden zinciri hem hata kaydına hem hata ekranındaki ayrıntıya yazılır; her parça için A/B klibin türü, çözünürlüğü, kodeği, HDR ve ses durumu da kaydedilir.
- Asıl yöntem (iki dizili, 0,7 sn) bir geçişte başarısız olursa o geçiş **yedek yöntemle** hazırlanır: tek dizi, A kararıp biter, B açılarak başlar (birleştirici yok). Video yine oluşur; kaç geçişin yedekle yapıldığı sonuç ekranında yazar.
- Önbellek anahtarı yenilendi (g321).

## 3.20 (14.11.2026, deneme · `yerel-android` dalı): geçiş web Kareden Videoya yöntemiyle — tek görüntü dizisi
**Test:** derlendi; cihaz testi bekleniyor.
- **Belirti (Ahmet, 3.19):** geçişte göz kırpma sürüyor; klip sonlarında görüntü ve ses 1–2 sn geri sarılıp tekrar ediyor.
- **Kök neden [Muhtemel]:** 3.12–3.19'da üst üste binen geçiş ana videoda **iki ayrı görüntü dizisiyle** (iki çözücü + Media3 çok girişli birleştirici) kuruluyordu. Birleştirici iki dizinin karelerini zaman damgasına göre eşler; dizide klip değişirken ya da boşluktan çıkarken eşleme kayınca kare atlanıyor ya da eski kareler yeniden geliyordu. 3.13–3.19 düzeltmeleri hep bu iki dizili yapının içindeydi; yapı kalınca sorun kalıyordu.
- **Referans (web Kareden Videoya):** iki klip her karede **tek yerde** birleştirilir; geçiş, iki klibin yalnız örtüşen kısmıdır.
- **Çözüm:** ana video artık **tek görüntü dizisi** (geçişsiz projelerde cihazda doğrulanan 3.1 yolu). Her geçiş, iki klibin örtüşen T ms'sinden kısa bir **geçiş parçası** olarak önceden işlenir (`GecisHazir`) ve dizide iki klibin arasına konur: `[A gövdesi][A→B geçiş parçası][B gövdesi]`. Parçanın ilk karesi A'nın, son karesi B'nin devamıdır (hareket, yüz kadrajı, klip yazısı, takip yazısı kesintisiz). Logo ve zaman çizelgesi katmanları parçanın üstüne ana videoda bir kez konur.
- Parçalar önbellekte tutulur (`cache/gecis`, anahtar: iki klibin tüm ayarları + süre + tür + çözünürlük); klip değişmedikçe yeniden işlenmez. Dışa aktarmada önce "Geçişler hazırlanıyor k/n", ▶ önizlemede ilk oynatmada aynı iş 720p'de yapılır.
- HDR klipte parça hazırlanamazsa dışa aktarma HDR yedek yöntemiyle kendiliğinden yeniden dener. Parça hazırlanırken Duraklat/İptal çalışır; hazırlanan parçalar saklanır.
- Kaldırılan: 3.19'daki A/B dizi düzeni (`UstBirlestirici`, `UstBolum`).

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
