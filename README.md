# Stüdyo

Fotoğraf ve video düzenleyici. **Karanlık Oda** ile **Kareden Videoya**'yı tek uygulamada birleştirir. Tasarımda InShot'un sade düzeni örnek alındı. Tarayıcıda çalışır (PWA). Dosyalar telefondan dışarı gönderilmez.

**Güncel sürüm:** 2.16 · 08.11.2026. İçinde Karanlık Oda 6.67 ve Kareden Videoya 9.15 var.

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
