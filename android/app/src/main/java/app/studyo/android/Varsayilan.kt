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
        listOf("kadraj", "Kadrajı doldur", "kadraj", "Bütün klipler ekranı doldurur"),
        listOf("yuz", "Yüze yaklaş", "kisi", "Dikey kişi fotoğrafında yüze odaklanır"),
        listOf("giris", "Giriş başlığı", "baslik", "Renkli kutular · tarih · konum"),
        listOf("abone", "Abone ol", "zil", "Kanalıma abone ol · zili aç"),
        listOf("kapanis", "Kapanış", "kalp", "Cam kartlar · teşekkür"),
    )
    fun secili(ctx: Context): Set<String> =
        ctx.getSharedPreferences("ayar", 0).getStringSet("varsayilan", null)?.toSet() ?: MADDELER.map { it[0] }.toSet()
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
