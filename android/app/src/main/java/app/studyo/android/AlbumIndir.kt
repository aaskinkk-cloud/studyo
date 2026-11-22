package app.studyo.android

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3.81: Google Fotoğraflar paylaşılan albüm bağlantısından fotoğrafları (ve videoları) almak — ücretsiz, hesap/anahtar gerekmez.
 * Bağlantı (photos.app.goo.gl/… ya da photos.google.com/share/…) "bağlantıya sahip olan herkes" ile paylaşılmış olmalı.
 * Albüm sayfasının içindeki görsel adresleri (lh3.googleusercontent.com/pw/…) okunur; her biri özgün boyutunda (=d) indirilir.
 * Google'ın resmî bir arayüzü değildir: Google sayfa yapısını değiştirirse çalışmayabilir (o zaman Google Fotoğraflar'dan
 * fotoğrafları seçip Paylaş › Stüdyo yolu her zaman çalışır).
 */
object AlbumIndir {
    /** Paylaşılan metinden gelen albüm isteği (Ana ekran ↔ editör). */
    val istek = MutableStateFlow<String?>(null)

    private val LINK = Regex("""https?://(photos\.app\.goo\.gl|photos\.google\.com|goo\.gl/photos)/\S+""")
    private val GORSEL = Regex(""""(https://lh3\.googleusercontent\.com/pw/[A-Za-z0-9_\-]+)",(\d+),(\d+)""")
    private const val TARAYICI = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun bagla(metin: String?): String? = metin?.let { LINK.find(it)?.value?.trimEnd('.', ',', ')', '"') }
    /** 3.96: metindeki bütün albüm bağlantıları (sırayla, tekrarsız). */
    fun baglar(metin: String?): List<String> = metin?.let { m -> LINK.findAll(m).map { it.value.trimEnd('.', ',', ')', '"') }.distinct().toList() } ?: emptyList()

    private fun ac(adres: String): HttpURLConnection {
        var u = adres
        repeat(6) {          // yönlendirmeleri elle izle (goo.gl → photos.google.com)
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", TARAYICI); c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            val kod = c.responseCode
            if (kod in 300..399) { val y = c.getHeaderField("Location"); c.disconnect(); if (y.isNullOrEmpty()) throw Exception("yönlendirme boş"); u = URL(URL(u), y).toString() }
            else return c
        }
        throw Exception("çok fazla yönlendirme")
    }

    /** @return indirilen dosyaların adresleri ve (varsa) hata metni. ilerleme(sıra, toplam). */
    suspend fun indir(ctx: Context, link: String, ilerleme: (Int, Int) -> Unit): Pair<List<Uri>, String?> = withContext(Dispatchers.IO) {
        val html = try {
            val c = ac(link)
            try { if (c.responseCode != 200) return@withContext emptyList<Uri>() to "Albüm açılamadı (kod ${c.responseCode})."; c.inputStream.bufferedReader().readText() }
            finally { c.disconnect() }
        } catch (e: Exception) { Kayit.hata("albüm sayfası", e); return@withContext emptyList<Uri>() to "Albüm açılamadı: internet bağlantısını kontrol et." }
        val adresler = GORSEL.findAll(html).map { it.groupValues[1] }.distinct().toList()
        Kayit.yaz("Google Fotoğraflar albümü: ${adresler.size} öğe (${html.length} bayt sayfa)")
        if (adresler.isEmpty()) return@withContext emptyList<Uri>() to "Albümde fotoğraf bulunamadı. Bağlantı paylaşımı açık mı? (Google Fotoğraflar › albüm › Paylaş › Bağlantı oluştur)"
        val dizin = File(ctx.filesDir, "albumler/" + (link.hashCode().toLong() and 0xffffffffL).toString(16)).apply { mkdirs() }
        val sonuc = ArrayList<Uri>(); var hata = 0
        adresler.forEachIndexed { i, a ->
            ensureActive()
            ilerleme(i + 1, adresler.size)
            val hazir = dizin.listFiles()?.firstOrNull { it.nameWithoutExtension == "%03d".format(i + 1) && it.length() > 0 }
            if (hazir != null) { sonuc += Uri.fromFile(hazir); return@forEachIndexed }
            try {
                val c = ac("$a=d")          // =d: özgün dosya (çekim tarihi bilgisiyle)
                try {
                    val tur = c.contentType ?: ""
                    val uz = when {
                        tur.startsWith("video/") -> ".mp4"
                        tur.contains("png") -> ".png"
                        tur.contains("webp") -> ".webp"
                        tur.contains("heic") || tur.contains("heif") -> ".heic"
                        tur.startsWith("image/") -> ".jpg"
                        else -> null
                    }
                    if (c.responseCode != 200 || uz == null) { hata++; return@forEachIndexed }
                    val gecici = File(dizin, "%03d.yarim".format(i + 1))
                    c.inputStream.use { g -> gecici.outputStream().use { g.copyTo(it, 256 * 1024) } }
                    val f = File(dizin, "%03d$uz".format(i + 1))
                    if (gecici.length() > 0 && gecici.renameTo(f)) sonuc += Uri.fromFile(f) else { gecici.delete(); hata++ }
                } finally { c.disconnect() }
            } catch (e: Exception) { hata++; Kayit.hata("albüm öğesi ${i + 1}", e) }
        }
        sonuc to (if (hata > 0) "$hata öğe indirilemedi." else null)
    }
}
