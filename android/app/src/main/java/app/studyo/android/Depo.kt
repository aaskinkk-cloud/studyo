package app.studyo.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uygulama içi hata/işlem kaydı (Ana sayfa ⋯ › Hata kaydını paylaş). */
object Kayit {
    private var dosya: File? = null
    fun baslat(ctx: Context) { dosya = File(ctx.filesDir, "kayit.txt") }

    @Synchronized
    fun yaz(s: String) {
        Log.i("Studyo", s)
        val f = dosya ?: return
        try {
            if (f.length() > 512 * 1024) f.writeText(f.readText().takeLast(256 * 1024))
            val z = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())
            f.appendText("$z  $s\n")
        } catch (_: Exception) {
        }
    }

    fun hata(s: String, e: Throwable) =
        yaz("HATA $s: ${e.javaClass.simpleName}: ${e.message}\n" + e.stackTraceToString().lines().take(14).joinToString("\n"))

    fun metin(): String = try { dosya?.readText()?.takeLast(60_000) ?: "" } catch (_: Exception) { "" }
}

/** Projeler: filesDir/projeler/<id>.json (atomik yazma). Video dosyaları kopyalanmaz; telefondaki yerinden okunur. */
object Depo {
    lateinit var app: Context
        private set

    fun baslat(ctx: Context) {
        app = ctx.applicationContext
        Kayit.baslat(app)
    }

    fun dizin(ad: String) = File(app.filesDir, ad).apply { mkdirs() }
    private fun dosya(id: String) = File(dizin("projeler"), "$id.json")

    fun listele(): List<Proje> =
        (dizin("projeler").listFiles() ?: emptyArray()).filter { it.name.endsWith(".json") }
            .mapNotNull { oku(it) }.sortedByDescending { it.guncelleme }

    private fun oku(f: File): Proje? = try {
        JSON.decodeFromString(Proje.serializer(), f.readText())
    } catch (e: Exception) {
        Kayit.hata("Proje okunamadı ${f.name}", e); null
    }

    fun yukle(id: String): Proje? = dosya(id).takeIf { it.exists() }?.let { oku(it) }

    @Synchronized
    fun kaydet(p: Proje) {
        try {
            val f = dosya(p.id)
            val t = File(f.path + ".tmp")
            t.writeText(JSON.encodeToString(Proje.serializer(), p))
            if (!t.renameTo(f)) { f.delete(); t.renameTo(f) }
        } catch (e: Exception) {
            Kayit.hata("Proje kaydedilemedi", e)
        }
    }

    fun cogalt(p: Proje): Proje {
        val n = System.currentTimeMillis()
        val k = p.copy(id = yeniId(), ad = p.ad + " (kopya)", olusturma = n, guncelleme = n)
        kaydet(k); return k
    }

    fun sil(p: Proje) { dosya(p.id).delete(); temizle() }

    /** Hiçbir projenin kullanmadığı uygulama içi dosyaları ve kalıcı dosya izinlerini bırakır. */
    fun temizle() {
        try {
            val yollar = HashSet<String>(); val uriler = HashSet<String>()
            for (p in listele()) {
                p.klipler.forEach { k -> uriler += k.uri; Uri.parse(k.uri).path?.let { yollar += it } }
                p.muzik?.let { uriler += it.uri }
                p.sesler.forEach { yollar += it.yol }
                p.logo?.let { yollar += it.yol }
                p.nesneler.forEach { yollar += it.yol; if (it.kareDizin.isNotEmpty()) yollar += it.kareDizin }
            }
            for (d in listOf("foto", "ses", "logo", "medya", "nesne")) dizin(d).listFiles()?.forEach { if (it.path !in yollar) { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
            val cr = app.contentResolver
            for (izin in cr.persistedUriPermissions) if (izin.uri.toString() !in uriler) {
                try { cr.releasePersistableUriPermission(izin.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Kayit.hata("temizle", e)
        }
    }
}
