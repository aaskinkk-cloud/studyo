package app.studyo.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class MedyaBilgi(
    val sureMs: Long, val gen: Int, val yuk: Int, val kodek: String,
    val hdr: Boolean, val sesVar: Boolean, val sorun: String?,
)

object Medya {
    fun mb(b: Long): String = when {
        b < 0 -> "?"
        b >= 1024L * 1024 * 1024 -> String.format(TR, "%.2f GB", b / 1073741824.0)
        else -> "${b / 1048576} MB"
    }

    fun adBoyut(ctx: Context, uri: Uri): Pair<String, Long> {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val f = File(uri.path ?: ""); return f.name to f.length()
        }
        var ad = uri.lastPathSegment ?: "dosya"; var boy = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && !c.isNull(i)) ad = c.getString(i)
                    val j = c.getColumnIndex(OpenableColumns.SIZE)
                    if (j >= 0 && !c.isNull(j)) boy = c.getLong(j)
                }
            }
        } catch (e: Exception) {
            Kayit.hata("ad/boyut", e)
        }
        return ad to boy
    }

    private fun kodekAdi(mime: String) = when {
        mime.contains("avc") -> "H.264"
        mime.contains("hevc") -> "H.265"
        mime.contains("dolby") -> "Dolby Vision"
        mime.contains("av01") -> "AV1"
        mime.contains("vp9") -> "VP9"
        mime.contains("vp8") -> "VP8"
        else -> mime
    }

    /** Videoyu belleğe almadan inceler: süre, boyut, kodek, HDR ve bu telefonda çözülüp çözülemeyeceği. */
    fun incele(ctx: Context, uri: Uri): MedyaBilgi {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            var sure = 0L; var g = 0; var y = 0; var kod = ""; var hdr = false; var ses = false
            var sorun: String? = null; var videoVar = false
            val liste = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (f.containsKey(MediaFormat.KEY_DURATION)) sure = maxOf(sure, f.getLong(MediaFormat.KEY_DURATION) / 1000)
                if (mime.startsWith("video/") && !videoVar) {
                    videoVar = true
                    g = f.getInteger(MediaFormat.KEY_WIDTH); y = f.getInteger(MediaFormat.KEY_HEIGHT)
                    if (f.containsKey(MediaFormat.KEY_ROTATION)) {
                        val r = f.getInteger(MediaFormat.KEY_ROTATION)
                        if (r == 90 || r == 270) { val t = g; g = y; y = t }
                    }
                    kod = kodekAdi(mime)
                    if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        val tr = f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        hdr = tr == MediaFormat.COLOR_TRANSFER_ST2084 || tr == MediaFormat.COLOR_TRANSFER_HLG
                    }
                    var dec = try { liste.findDecoderForFormat(f) } catch (e: Exception) { null }
                    if (dec == null && mime == "video/dolby-vision") {
                        // Dolby Vision dosyalarında HEVC yedek izi vardır; oynatıcı ve dönüştürücü onu kullanır
                        val yedek = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, g, y)
                        dec = try { liste.findDecoderForFormat(yedek) } catch (e: Exception) { null }
                    }
                    if (dec == null) sorun = "Bu telefon $kod ${g}×$y videoyu çözemiyor (desteklenmeyen biçim)."
                } else if (mime.startsWith("audio/") && !ses) {
                    val dec = try { liste.findDecoderForFormat(f) } catch (e: Exception) { null }
                    ses = dec != null
                    if (dec == null) Kayit.yaz("Ses izi çözülemiyor: $mime (klip sessiz eklenir)")
                }
            }
            if (!videoVar && sorun == null) sorun = "Dosyada görüntü yok ya da biçim tanınmadı."
            if (sure <= 0) sure = retrieverSure(ctx, uri)
            if (sure <= 0 && sorun == null) sorun = "Videonun süresi okunamadı (dosya bozuk olabilir)."
            return MedyaBilgi(sure, g, y, kod, hdr, ses, sorun)
        } catch (e: Exception) {
            Kayit.hata("incele $uri", e)
            return MedyaBilgi(0, 0, 0, "", false, false, "Dosya açılamadı: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            ex.release()
        }
    }

    fun retrieverSure(ctx: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    // ---- küçük resimler (bellek sınırlı önbellek) ----
    private val onbellek = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun kucukResim(ctx: Context, k: Klip, boy: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        val anahtar = "${k.uri}#${if (k.foto) 0 else k.basMs / 1000}"
        onbellek.get(anahtar)?.let { return@withContext it }
        val bmp = try {
            if (k.foto) ornekleCoz(File(Uri.parse(k.uri).path ?: ""), boy)
            else kare(ctx, Uri.parse(k.uri), k.basMs + minOf(800L, k.sureMs / 3), boy)
        } catch (e: Exception) {
            Kayit.hata("küçük resim", e); null
        }
        bmp?.also { onbellek.put(anahtar, it) }
    }

    fun kare(ctx: Context, uri: Uri, ms: Long, boy: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val t = ms * 1000
            return if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, boy, boy)
            else r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { olcekle(it, boy) }
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    /** Tam o andaki kare (kapak için; anahtar kareye yuvarlamaz). */
    fun kareTam(ctx: Context, uri: Uri, ms: Long, boy: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val t = ms * 1000
            return if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST, boy, boy)
            else r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)?.let { olcekle(it, boy) }
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    /** JPEG'i galeriye (Pictures/Stüdyo) yazar ve boyutunu doğrular. */
    fun resimKaydet(ctx: Context, b: Bitmap, ad: String): Uri {
        val cr = ctx.contentResolver
        val cv = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, ad)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Stüdyo")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val d = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Stüdyo").apply { mkdirs() }
                @Suppress("DEPRECATION")
                put(android.provider.MediaStore.Images.Media.DATA, File(d, ad).path)
            }
        }
        val kol = if (Build.VERSION.SDK_INT >= 29) android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val u = cr.insert(kol, cv) ?: throw IllegalStateException("Galeride kayıt oluşturulamadı.")
        try {
            val o = cr.openOutputStream(u, "w") ?: throw IllegalStateException("Dosya açılamadı.")
            o.use { if (!b.compress(Bitmap.CompressFormat.JPEG, 94, it)) throw IllegalStateException("JPEG yazılamadı.") }
            val boy = cr.openFileDescriptor(u, "r")?.use { it.statSize } ?: 0L
            if (boy <= 0) throw IllegalStateException("Dosya boş kaldı.")
            if (Build.VERSION.SDK_INT >= 29) cr.update(u, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            try { cr.delete(u, null, null) } catch (_: Exception) {}
            throw e
        }
        return u
    }

    fun olcekle(b: Bitmap, boy: Int): Bitmap {
        val s = boy.toFloat() / maxOf(b.width, b.height)
        if (s >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    fun ornekleCoz(f: File, boy: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= boy) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.let { olcekle(it, boy) }
    }

    /** Fotoğrafı en fazla [enUzun] piksele küçültüp yönünü düzelterek uygulama içine yazar.
     *  200 MP fotoğraf bile örnekleyerek açılır; bellek taşmaz. */
    fun fotoHazirla(ctx: Context, uri: Uri, enUzun: Int = 3840, png: Boolean = false, dizinAd: String = "foto"): Triple<File, Int, Int>? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o); true } ?: return null
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= enUzun) s *= 2
        var b = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
        val yon = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val m = Matrix()
        val kat = enUzun.toFloat() / maxOf(b.width, b.height)
        if (kat < 1f) m.postScale(kat, kat)
        when (yon) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        if (!m.isIdentity) {
            val n = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            if (n !== b) b.recycle()
            b = n
        }
        val f = File(Depo.dizin(dizinAd), yeniId() + if (png) ".png" else ".jpg")
        f.outputStream().use { b.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
        val sonuc = Triple(f, b.width, b.height)
        b.recycle()
        return sonuc
    }

    /** Kalıcı izni olmayan (paylaşımla gelen) dosyayı parça parça uygulamaya kopyalar; yer yoksa açık hata verir. */
    fun kopyala(ctx: Context, uri: Uri, ad: String, boyut: Long): Uri? {
        val d = Depo.dizin("medya")
        val bos = StatFs(d.path).availableBytes
        if (boyut > 0 && boyut + 300L * 1024 * 1024 > bos)
            throw IllegalStateException("yer yetersiz (${mb(boyut)} gerekiyor, ${mb(bos)} boş)")
        val uz = ad.substringAfterLast('.', "mp4").take(5)
        val f = File(d, yeniId() + "." + uz)
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o, 4 * 1024 * 1024) } } ?: return null
        } catch (e: Exception) {
            f.delete(); throw e
        }
        return Uri.fromFile(f)
    }

    /** Bu telefonda H.264 kodlayıcı bu çözünürlüğü destekliyor mu? */
    fun kodlayiciVar(w: Int, h: Int): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { ci ->
            ci.isEncoder && ci.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
                ci.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities.let { it.isSizeSupported(w, h) || it.isSizeSupported(h, w) }
        }
    } catch (e: Exception) {
        true
    }
}
