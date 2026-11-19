package app.studyo.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Kişisel logo kütüphanesi: filesDir/logolar/<id>.png + liste.json. Projelerden bağımsızdır;
 *  Depo.temizle() bu klasöre dokunmaz. Projeye uygulanan logo "logo" klasörüne kopyalanır (kutup_<id>.png),
 *  böylece kütüphaneden silinen logo, onu kullanan projelerde bozulmaz. */
@Serializable
data class LogoKaydi(val id: String = yeniId(), val ad: String, val yol: String, val eklenme: Long = System.currentTimeMillis())

object LogoKutup {
    val ogeler = MutableStateFlow<List<LogoKaydi>>(emptyList())
    @Volatile private var yuklendi = false
    private val seri = ListSerializer(LogoKaydi.serializer())

    private fun listeDosyasi() = File(Depo.dizin("logolar"), "liste.json")

    /** İlk açılışta: liste yoksa projelerde kullanılmış logolar kütüphaneye alınır (hiçbir logo kaybolmaz). */
    @Synchronized
    fun yukle() {
        if (yuklendi) return
        val f = listeDosyasi()
        var l: List<LogoKaydi> = try {
            if (f.exists()) JSON.decodeFromString(seri, f.readText()) else emptyList()
        } catch (e: Exception) {
            Kayit.hata("logo listesi okunamadı", e); emptyList()
        }
        if (!f.exists()) {
            val goruldu = HashSet<String>()
            val yeni = ArrayList<LogoKaydi>()
            for (p in Depo.listele()) {
                val yol = p.logo?.yol ?: continue
                val kaynak = File(yol)
                if (yol.isEmpty() || !kaynak.exists() || !goruldu.add(ozet(kaynak))) continue
                try {
                    val id = yeniId()
                    val hedef = File(Depo.dizin("logolar"), "$id.png")
                    kaynak.copyTo(hedef, overwrite = true)
                    yeni += LogoKaydi(id = id, ad = "Logo ${yeni.size + 1}", yol = hedef.path)
                } catch (e: Exception) {
                    Kayit.hata("logo taşıma", e)
                }
            }
            l = yeni
            yaz(l)
        }
        l = l.filter { File(it.yol).exists() }
        val y = yerlesikleriAl(l); if (y.size != l.size) { l = y; yaz(l) }
        // yedekten gelen ama listede olmayan logo dosyaları da kütüphaneye
        val bilinen = l.map { File(it.yol).name }.toSet()
        val yetim = Depo.dizin("logolar").listFiles()?.filter { it.isFile && it.name.endsWith(".png") && it.name !in bilinen } ?: emptyList()
        if (yetim.isNotEmpty()) { l = l + yetim.mapIndexed { i, f -> LogoKaydi(id = f.nameWithoutExtension, ad = "Logo ${l.size + i + 1}", yol = f.path) }; yaz(l) }
        ogeler.value = l
        yuklendi = true
    }

    private fun ozet(f: File): String = try {
        val c = java.util.zip.CRC32(); c.update(f.readBytes()); "${f.length()}_${c.value}"
    } catch (_: Exception) { f.path }

    @Synchronized
    private fun yaz(l: List<LogoKaydi>) {
        try {
            val f = listeDosyasi()
            val t = File(f.path + ".tmp")
            t.writeText(JSON.encodeToString(seri, l))
            if (!t.renameTo(f)) { f.delete(); t.renameTo(f) }
        } catch (e: Exception) {
            Kayit.hata("logo listesi kaydedilemedi", e)
        }
        ogeler.value = l
    }

    /** 3.61: yedekten geri yüklemeden sonra listeyi yeniden okur. */
    @Synchronized fun yenidenYukle() { yuklendi = false; yukle() }

    /** 3.61: yerleşik logolar (APK içinde assets/logolar klasöründeki PNG dosyaları) — kütüphane ilk kez kurulurken eklenir (silip kursan da gelir). */
    private fun yerlesikleriAl(l: List<LogoKaydi>): List<LogoKaydi> {
        val ay = Depo.app.getSharedPreferences("ayar", 0)
        if (ay.getBoolean("yerlesikLogoAlindi", false)) return l
        val adlar = try { Depo.app.assets.list("logolar")?.filter { it.endsWith(".png", true) } ?: emptyList() } catch (_: Exception) { emptyList() }
        val yeni = ArrayList(l)
        val varOlan = l.map { ozet(File(it.yol)) }.toSet()
        for (ad in adlar) try {
            val id = "yerlesik_" + ad.substringBeforeLast('.')
            if (yeni.any { it.id == id }) continue
            val hedef = File(Depo.dizin("logolar"), "$id.png")
            Depo.app.assets.open("logolar/$ad").use { i -> hedef.outputStream().use { i.copyTo(it) } }
            if (ozet(hedef) in varOlan) { hedef.delete(); continue }   // aynı logo kütüphanede zaten var
            yeni += LogoKaydi(id = id, ad = ad.substringBeforeLast('.').replace('_', ' ').take(40), yol = hedef.path, eklenme = 0L)
        } catch (e: Exception) { Kayit.hata("yerleşik logo", e) }
        ay.edit().putBoolean("yerlesikLogoAlindi", true).apply()
        return yeni
    }

    /** f: logolar klasörüne yazılmış PNG. */
    @Synchronized
    fun ekle(f: File, ad: String): LogoKaydi {
        yukle()
        val k = LogoKaydi(ad = ad.substringBeforeLast('.').ifBlank { "Logo" }.take(40), yol = f.path)
        yaz(ogeler.value + k)
        Ayarlar.otoYedek(Depo.app)   // 3.61
        return k
    }

    @Synchronized
    fun adDegistir(id: String, ad: String) {
        if (ad.isBlank()) return
        yaz(ogeler.value.map { if (it.id == id) it.copy(ad = ad.trim().take(40)) else it })
    }

    @Synchronized
    fun sil(id: String) {
        ogeler.value.firstOrNull { it.id == id }?.let { File(it.yol).delete() }
        yaz(ogeler.value.filter { it.id != id })
    }

    /** Projede kullanılacak kopya (aynı logo için tek dosya; projeler paylaşır). */
    fun projeKopyasi(k: LogoKaydi): File? {
        val kaynak = File(k.yol)
        if (!kaynak.exists()) return null
        val hedef = File(Depo.dizin("logo"), "kutup_${k.id}.png")
        if (!hedef.exists() || hedef.length() != kaynak.length()) kaynak.copyTo(hedef, overwrite = true)
        return hedef
    }
}
