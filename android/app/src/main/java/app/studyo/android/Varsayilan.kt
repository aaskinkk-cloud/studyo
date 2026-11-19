package app.studyo.android

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/** Varsayılan ayarlar sayfasındaki maddeler; hangisinin uygulanacağı telefonda saklanır (hepsi açık gelir). */
object Varsayilan {
    /** anahtar, ad, simge, açıklama */
    val MADDELER = listOf(
        listOf("kadraj", "Kadrajı doldur", "kadraj", "Kare dolar · yüz/nesne kesilmez"),
        listOf("yuz", "Yüze yaklaş", "kisi", "Fotoğraf ve videoda yüz kadrajda kalır"),
        listOf("giris", "Giriş başlığı", "baslik", "Renkli kutular · tarih · konum"),
        listOf("abone", "Abone ol", "zil", "Kanalıma abone ol · zili aç"),
        listOf("kapanis", "Kapanış", "kalp", "Cam kartlar · teşekkür"),
        listOf("logo", "Logo", "logo", "Sol üst · %14 · %70 · son kullanılan"),   // 3.55
        listOf("muzik", "Müzik", "nota", "Konuya göre yerleşik müzik"),   // 3.61
    )
    /** Sonradan eklenen maddeler: eski kayıtlı seçime bir kez eklenir (kullanıcı sonra kapatabilir). */
    private val YENI_MADDELER = listOf("logo", "muzik")
    fun secili(ctx: Context): Set<String> {
        val ay = ctx.getSharedPreferences("ayar", 0)
        val kayitli = ay.getStringSet("varsayilan", null)?.toSet() ?: return MADDELER.map { it[0] }.toSet()
        val bilinen = ay.getStringSet("varsayilanBilinen", null)?.toSet() ?: emptySet()
        val eksik = YENI_MADDELER.filter { it !in bilinen }
        if (eksik.isEmpty()) return kayitli
        val s = kayitli + eksik
        ay.edit().putStringSet("varsayilan", s).putStringSet("varsayilanBilinen", bilinen + YENI_MADDELER).apply()
        return s
    }
    /** Varsayılan logo: en son projeye uygulanan kütüphane logosu (yoksa en yeni eklenen). */
    fun sonLogo(ctx: Context): String? = ctx.getSharedPreferences("ayar", 0).getString("sonLogo", null)
    fun sonLogoYaz(ctx: Context, id: String) = ctx.getSharedPreferences("ayar", 0).edit().putString("sonLogo", id).apply()
    fun degistir(ctx: Context, anahtar: String) {
        val s = secili(ctx).toMutableSet(); if (!s.add(anahtar)) s.remove(anahtar)
        ctx.getSharedPreferences("ayar", 0).edit().putStringSet("varsayilan", s).apply()
    }
}

/** Çekim tarihi: fotoğrafta EXIF, videoda üstveri. Bilinmiyorsa 0. Giriş başlığının tarih bandı için. */
object Tarih {
    fun foto(ctx: Context, uri: Uri): Long = try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val e = ExifInterface(s)
            val m = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
            m?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).parse(it)?.time }
        } ?: 0L
    } catch (_: Exception) { 0L }

    /** MediaMetadataRetriever tarihi "20260926T143012.000Z" biçiminde verir. */
    fun video(ctx: Context, uri: Uri): Long = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val m = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.trim().orEmpty()
            if (m.length >= 8 && !m.startsWith("1904") && !m.startsWith("1970")) {
                val f = SimpleDateFormat(if (m.length >= 15) "yyyyMMdd'T'HHmmss" else "yyyyMMdd", java.util.Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                f.parse(m.take(if (m.length >= 15) 15 else 8))?.time ?: 0L
            } else 0L
        } finally { r.release() }
    } catch (_: Exception) { 0L }

    /** "26 EYLÜL 2026" */
    fun yaz(ms: Long): String = SimpleDateFormat("d MMMM yyyy", TR).format(Date(if (ms > 0) ms else System.currentTimeMillis())).uppercase(TR)
}
