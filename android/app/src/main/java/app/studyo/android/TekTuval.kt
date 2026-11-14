package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.DefaultGlObjectsProvider
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlMatrixTransformation
import androidx.media3.effect.GlShaderProgram
import java.io.File
import java.util.concurrent.Executor

/* ============================================================================================================
   3.32 — TEK TUVAL GEÇİŞ (web Kareden Videoya yöntemi)
   Üst üste binen geçiş (erime / yumuşak / kaydır / büyüyerek), GİDEN klibin kendi öğesinin son T ms'sinde, tek bir GL
   geçişinde çizilir. Gelen klibin kareleri aynı GL bağlamında, aynı zaman damgasıyla bu sınıfın kendi kaynağından gelir
   (video: MediaCodec → SurfaceTexture; fotoğraf/kapanış: bitmap). Ana videoda ek dizi, ek öğe, önceden kodlanmış parça yok:
   dizi, cihazda sorunsuz çalıştığı kayıtla doğrulanan "geçişsiz proje" biçimindedir → Media3'ün çok girişli birleştiricisi
   (göz kırpma / geri sarma) ve tek dizide resim↔kısa video nöbetleşmesi (▶'da çözücü "Released state" çökmesi) devre dışı.
   ============================================================================================================ */

/** Gelen klibin (ya da kapanış kartının) tarifi. Kompozisyon kurulurken (arka plan iş parçacığı) oluşturulur. */
class GelenTarif(
    val etiket: String,                 // "3→4" (kayıt için)
    val tur: String,                    // erime | capraz | kaydir | yakincapraz
    val gecisMs: Long,                  // T
    val ogeMs: Long,                    // giden öğenin uzunluğu; geçiş penceresi = [ogeMs − T, ogeMs)
    val cizelgeBasMs: Long,             // giden öğenin zaman çizelgesindeki başlangıcı
    val W: Int, val H: Int,
    val klip: Klip?,                    // null → kapanış kartı
    val resimYol: String?,              // fotoğraf klibinin ya da kapanış kartının dosyası
    val etkiler: List<GlMatrixTransformation>,   // gelen klibin kadraj/hareket + sunum dönüşümleri (klip içi zamanla çalışır)
    val yazi: Yerlesim?,                // gelen klibin kendi yazısı (geçişle birlikte belirir)
    val hdrCozucudeCevir: Boolean,      // HDR → SDR çevirisini çözücüden iste (ana hatla aynı yöntem)
    val onizleme: Boolean,              // ▶ akıcı önizleme (damgalar kompozisyon zamanı) / dışa aktarma (ilk kare = öğenin başı)
)

class TekTuvalGecis(private val t: GelenTarif) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = TekTuvalProgram(context.applicationContext, useHdr, t)
}

// 3.34: gelen klip geçişten 2 sn önce kurulur ve hazırlığı karelere bölünür (bkz. GelenKaynak.onHazirla). Öğenin ilk 300 ms'sinde
// kurulmaz: o anlar bir önceki geçişin hemen devamıdır, orada duraksama görünür.
private const val ON_HAZIRLIK_US = 2_000_000L
private const val OGE_BASI_US = 300_000L

private class TekTuvalProgram(private val ctx: Context, private val useHdr: Boolean, private val t: GelenTarif) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VS, FS)
    private val gurultu: Int = Erime.dokuYukle()
    private val turNo = when (t.tur) { "erime" -> 1; "kaydir" -> 2; "yakincapraz" -> 3; else -> 0 }
    private var t0 = -1L
    private var kaynak: GelenKaynak? = null
    private var kaynakDenendi = false
    private var hataYazildi = false
    // 3.33 tanı izi (yalnız önizleme): ilk karelerin duvar saati ve çizim süresi → ▶ geç başlarsa nerede beklediği kayıtta görünür
    private val iz = StringBuilder()
    private var izAdet = 0
    private var ilkCizim = -1L
    private var gecHazirUs = -1L        // gelen klip geçiş başladıktan sonra hazır olduysa o an (ani belirmesin diye yumuşak yetişir)
    private var bVardi = false
    private val fb = IntArray(1)
    private val vp = IntArray(4)

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val yerelUs = if (t.onizleme) presentationTimeUs - t.cizelgeBasMs * 1000 else { if (t0 < 0) t0 = presentationTimeUs; presentationTimeUs - t0 }
        val tauUs = yerelUs - (t.ogeMs - t.gecisMs) * 1000            // geçişin içindeki an (negatif: geçiş başlamadı)
        var bTex = -1
        val c0 = SystemClock.elapsedRealtime()
        if (ilkCizim < 0) ilkCizim = c0
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fb, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        try {
            if (tauUs < -3_000_000L && kaynak != null) kaynakKapat()   // önizlemede geri sarıldı: çözücü boşuna açık kalmasın
            if (tauUs >= -ON_HAZIRLIK_US && (yerelUs >= OGE_BASI_US || tauUs >= 0) && kaynak == null && !kaynakDenendi) {
                kaynakDenendi = true
                val k0 = SystemClock.elapsedRealtime()
                kaynak = GelenKaynak(ctx, useHdr, t).also { it.kurulumMs = SystemClock.elapsedRealtime() - k0 }
            } else if (tauUs < 0) kaynak?.onHazirla()      // geçişten önceki her karede küçük bir hazırlık adımı
            if (tauUs >= 0) bTex = kaynak?.kare(tauUs.coerceAtMost(t.gecisMs * 1000)) ?: -1
        } catch (e: Throwable) {
            // Gelen klip çizilemedi: giden klip olduğu gibi gösterilir (yanlış kare / siyah kare asla basılmaz), nedeni kayda yazılır
            if (!hataYazildi) { hataYazildi = true; Kayit.hata("Geçiş ${t.etiket} gelen kare", e) }
            bTex = -1
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
        var f = if (bTex < 0) 0f else (tauUs.toFloat() / (t.gecisMs * 1000f)).coerceIn(0f, 1f)
        if (bTex >= 0 && !bVardi) { bVardi = true; if (tauUs > 50_000L) gecHazirUs = tauUs }
        // Gelen klip geç hazır olduysa (▶ geçişin içinden başlatıldı): 200 ms içinde olması gereken karışıma yumuşakça yetişir
        if (bTex >= 0 && gecHazirUs >= 0) f *= ((tauUs - gecHazirUs) / 200_000f).coerceIn(0f, 1f)
        val e = f * f * (3f - 2f * f)
        GLES20.glDisable(GLES20.GL_BLEND)
        program.use()
        program.setSamplerTexIdUniform("uA", inputTexId, 0)
        program.setSamplerTexIdUniform("uB", if (bTex < 0) inputTexId else bTex, 1)
        program.setSamplerTexIdUniform("uGurultu", gurultu, 2)
        program.setIntUniform("uTur", if (bTex < 0) 0 else turNo)
        program.setFloatUniform("uE", e)
        program.setFloatUniform("uEsik", Erime.esik(f))
        program.setFloatUniform("uYum", Erime.E)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        try { kaynak?.birak() } catch (_: Throwable) {}
        if (t.onizleme && tauUs >= 0 && izAdet < 8) {
            izAdet++
            val c1 = SystemClock.elapsedRealtime()
            iz.append(" [τ=").append(tauUs / 1000).append(" +").append(c0 - ilkCizim).append("ms çizim=").append(c1 - c0).append("ms").append(if (bTex < 0) " B yok" else "").append(']')
        }
    }

    private fun kaynakKapat() {
        try { kaynak?.kapat() } catch (e: Throwable) { Kayit.hata("Geçiş ${t.etiket} kapat", e) }
        if (iz.isNotEmpty()) { Kayit.yaz("    iz ${t.etiket}:$iz"); iz.setLength(0) }
        izAdet = 0; ilkCizim = -1L; gecHazirUs = -1L; bVardi = false
        kaynak = null; kaynakDenendi = false
    }

    override fun signalEndOfCurrentInputStream() {
        kaynakKapat()           // öğe bitti: ikinci çözücü hemen bırakılır (sıradaki klibin ana çözücüsüne yer açılır)
        super.signalEndOfCurrentInputStream()
    }

    override fun release() {
        kaynakKapat()
        try { program.delete() } catch (_: Exception) {}
        try { GLES20.glDeleteTextures(1, intArrayOf(gurultu), 0) } catch (_: Exception) {}
        super.release()
    }

    companion object {
        const val VS = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""
        // uA: giden klip (bu öğenin karesi), uB: gelen klip. Karışım, eski birleştiricideki sayılarla aynıdır
        // (Gecisler.ustKatman + Erime): çapraz 1−e · kaydır sola e · büyüyerek 1+0,25e ve 1−e · erime gürültü maskesi.
        const val FS = """
precision highp float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform sampler2D uGurultu;
uniform int uTur;
uniform float uE;
uniform float uEsik;
uniform float uYum;
varying vec2 vUv;
void main() {
  vec3 b = texture2D(uB, vUv).rgb;
  vec3 c;
  if (uTur == 1) {
    float n = texture2D(uGurultu, vec2(vUv.x, 1.0 - vUv.y)).r;
    float m = clamp((uEsik - n) / uYum, 0.0, 1.0);
    c = mix(texture2D(uA, vUv).rgb, b, m);
  } else if (uTur == 2) {
    vec2 ua = vec2(vUv.x + uE, vUv.y);
    c = ua.x <= 1.0 ? texture2D(uA, ua).rgb : b;
  } else if (uTur == 3) {
    vec2 ua = (vUv - 0.5) / (1.0 + 0.25 * uE) + 0.5;
    c = mix(texture2D(uA, ua).rgb, b, uE);
  } else {
    c = mix(texture2D(uA, vUv).rgb, b, uE);
  }
  gl_FragColor = vec4(c, 1.0);
}
"""
    }
}

/** Gelen klibin o andaki karesini, ana hattaki AYNI Media3 dönüşüm programlarıyla (kadraj/hareket + sunum) W×H dokuya çizer.
 *  Bütün işlevler GL iş parçacığında çağrılır. */
private class GelenKaynak(private val ctx: Context, private val useHdr: Boolean, private val t: GelenTarif) {
    private val gl: GlObjectsProvider = DefaultGlObjectsProvider()
    private val programlar = ArrayList<BaseGlShaderProgram>()
    private val ciktilar = ArrayList<GlTextureInfo?>()
    private val kullanilan = ArrayList<Pair<BaseGlShaderProgram, GlTextureInfo>>()
    private var video: VideoCozucu? = null
    private var resim: ResimYukleyici? = null
    private var resimSrgb = true
    private var hamDoku: GlTextureInfo? = null       // dönüşümlere giren doku (video: her karede güncellenir; resim: bir kez)
    private var resimHazir = false
    private var oesProgram: GlProgram? = null
    private var bmpProgram: GlProgram? = null
    private var yaziProgram: GlProgram? = null
    private var yaziDoku = -1
    private val texM = FloatArray(16)
    private val kurulus = SystemClock.elapsedRealtime()
    private var ilkUs = -1L; private var sonUs = -1L; private var kareSayi = 0; private var bosSayi = 0
    private var yedekKullanildi = false
    private var kapandi = false
    // 3.34: hazırlık karelere bölünür. 3.33 kaydında her geçişin İLK karesi 47–73 ms sürüyordu (fotoğrafın ~20 MB'lık dokuya
    // tek seferde yüklenmesi + gölgelendiricilerin ilk kullanımı) → geçişin başında ~2 karelik duraksama. Artık doku 24 bantta,
    // her karede bir bant yüklenir; dönüşümler geçişten önce bir kez "ısıtılır"; τ=0'da yalnız karışım çizilir.
    private var bantlar: List<Bitmap>? = null
    private var bantNo = 0
    private var bantY = 0
    private var yukDoku = -1
    private var isindi = false
    var kurulumMs = 0L
    private var enUzunAdimMs = 0L
    private var adimSayi = 0

    init {
        val k = t.klip
        if (k != null && !k.foto) {
            video = VideoCozucu(ctx, Uri.parse(k.uri), k.basMs * 1000, t.hdrCozucudeCevir, t.etiket)
        } else {
            val yol = t.resimYol ?: ""
            val boy = (maxOf(t.W, t.H) * 2).coerceIn(1280, 4096)
            resim = ResimYukleyici { Medya.ornekleCoz(File(yol), boy) }
        }
    }

    /** Dönüşüm programlarından sıradakini kurar. true: hepsi kurulu. */
    private fun programAdim(): Boolean {
        val i = programlar.size
        if (i >= t.etkiler.size) return true
        val pr = t.etkiler[i].toGlShaderProgram(ctx, useHdr)
        ciktilar += null
        pr.setOutputListener(object : GlShaderProgram.OutputListener {
            override fun onOutputFrameAvailable(outputTexture: GlTextureInfo, presentationTimeUs: Long) { ciktilar[i] = outputTexture }
        })
        pr.setErrorListener(Executor { it.run() }, GlShaderProgram.ErrorListener { ex -> Kayit.hata("Geçiş ${t.etiket} dönüşüm", ex) })
        programlar += pr
        return false
    }
    private fun programlarKur() { while (!programAdim()) { } }

    private fun oes(): GlProgram = oesProgram ?: GlProgram(VS_OES, FS_OES).also {
        it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE); oesProgram = it
    }
    private fun bmp(): GlProgram = bmpProgram ?: GlProgram(TekTuvalProgram.VS, FS_BMP).also {
        it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE); bmpProgram = it
    }

    /** Geçişten önceki karelerde çağrılır (GL iş parçacığı): her çağrıda yalnız bir küçük adım. */
    fun onHazirla() {
        if (kapandi || isindi) return
        val c0 = SystemClock.elapsedRealtime()
        try {
            if (!programAdim()) return
            if (video != null) {
                if (oesProgram == null) { oes(); return }
                val v = video ?: return
                if (!v.kare(t.klip!!.basMs * 1000, 0L)) return       // ilk kare çözülmedi: sonraki karede yeniden
                if (v.yeniKare || hamDoku == null) { videoKopyala(v); return }
                zincir(hamDoku!!, 0L); birak(); isindi = true
            } else {
                if (bmpProgram == null) { bmp(); return }
                if (resimHazir) { zincir(hamDoku!!, 0L); birak(); isindi = true; return }
                resimAdim(0L)
            }
        } finally {
            adimSayi++
            enUzunAdimMs = maxOf(enUzunAdimMs, SystemClock.elapsedRealtime() - c0)
        }
    }

    /** Resmin bir sonraki hazırlık adımı. true: resim dokusu hazır. */
    private fun resimAdim(bekleMs: Long): Boolean {
        if (resimHazir) return true
        val r = resim ?: return false
        val l = bantlar ?: run {
            val b = r.bantlar(bekleMs) ?: return false
            bantlar = b; bantNo = 0; bantY = 0
            yukDoku = GlUtil.createTexture(b[0].width, b.sumOf { it.height }, false)
            return false
        }
        if (bantNo < l.size) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, yukDoku)
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, bantY, l[bantNo])
            bantY += l[bantNo].height
            l[bantNo].recycle()
            bantNo++
            return false
        }
        // tüm bantlar yüklendi: sRGB → SMPTE 170M çevrilmiş kopya (Media3 resim girdisiyle aynı)
        val h = dokuAc(l[0].width, bantY)
        val p = bmp()
        GlUtil.focusFramebufferUsingCurrentContext(h.fboId, h.width, h.height)
        GLES20.glDisable(GLES20.GL_BLEND)
        p.use()
        p.setSamplerTexIdUniform("uTex", yukDoku, 0)
        p.setIntUniform("uSrgb", if (resimSrgb) 1 else 0)
        p.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.deleteTexture(yukDoku); yukDoku = -1
        bantlar = null
        hamSil(); hamDoku = h; resimHazir = true
        return true
    }

    private fun videoKopyala(v: VideoCozucu) {
        val (dw, dh) = v.boyut(maxOf(t.W, t.H) * 2)
        val h = hamDoku?.takeIf { it.width == dw && it.height == dh } ?: run { hamSil(); dokuAc(dw, dh).also { hamDoku = it } }
        val p = oes()
        v.matris(texM)
        GlUtil.focusFramebufferUsingCurrentContext(h.fboId, h.width, h.height)
        GLES20.glDisable(GLES20.GL_BLEND)
        p.use()
        p.setSamplerTexIdUniform("uTex", v.texId, 0)
        p.setFloatsUniform("uTexM", texM)
        p.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** tauUs: geçişin içindeki an. Dönen: W×H doku kimliği; hazır değilse −1 (çağıran giden klibi olduğu gibi çizer). */
    fun kare(tauUs: Long): Int {
        if (kapandi) return -1
        val v = video
        if (v != null) {
            val k = t.klip!!
            val hedef = k.basMs * 1000 + tauUs
            val varMi = v.kare(hedef, if (t.onizleme) 40L else 8000L)      // önizlemede GL iş parçacığı bekletilmez (3.33)
            if (!varMi && v.hata != null && !yedekKullanildi) {
                // İkinci çözücü açılamadı (ör. telefon aynı anda iki büyük çözücüye izin vermedi): gelen klip geçiş boyunca
                // geçiş SONUNDAKİ karesiyle bekler (web'deki "bekleme"); gövdeye geçerken sıçrama olmaz.
                yedekKullanildi = true
                Kayit.yaz("  Geçiş ${t.etiket}: gelen video çözülemedi (${v.hata}) → durağan kareyle sürüyor")
                try { v.kapat() } catch (_: Throwable) {}
                video = null
                val boy = maxOf(t.W, t.H).coerceIn(1280, 4096)
                resimSrgb = false          // video karesi: renkler zaten video (BT.709) eğrisinde
                resim = ResimYukleyici { Medya.kareTam(ctx, Uri.parse(k.uri), k.basMs + t.gecisMs, boy) }
                return resimKare(tauUs)
            }
            if (!varMi) { bosSayi++; return -1 }
            if (v.yeniKare || hamDoku == null) {
                videoKopyala(v)
                if (ilkUs < 0) ilkUs = v.gosterilenUs - k.basMs * 1000
                sonUs = v.gosterilenUs - k.basMs * 1000
            }
            kareSayi++
            programlarKur()
            return zincir(hamDoku!!, tauUs)
        }
        return resimKare(tauUs)
    }

    private fun resimKare(tauUs: Long): Int {
        val r = resim ?: return -1
        if (r.bitmedi(if (t.onizleme) 0L else 15_000L)) { bosSayi++; return -1 }   // önizlemede beklenmez (3.33)
        // geç kalındıysa (▶ geçişin içinden başladı / kayıt): kalan adımlar şimdi tamamlanır
        while (!resimAdim(0L)) { if (bantlar == null && !resimHazir) { bosSayi++; return -1 } }
        if (ilkUs < 0) ilkUs = tauUs
        sonUs = tauUs; kareSayi++
        programlarKur()
        return zincir(hamDoku!!, tauUs)
    }

    /** Ham dokuyu gelen klibin dönüşümlerinden geçirir (ana hatla aynı programlar) ve yazısını üstüne çizer. */
    private fun zincir(giris: GlTextureInfo, tauUs: Long): Int {
        var tex = giris
        for ((i, pr) in programlar.withIndex()) {
            ciktilar[i] = null
            pr.queueInputFrame(gl, tex, tauUs)
            val c = ciktilar[i] ?: return -1
            kullanilan += pr to c
            tex = c
        }
        val y = t.yazi
        if (y != null && tex !== giris) {
            if (yaziDoku < 0) yaziDoku = GlUtil.createTexture(y.bmp)
            val p = yaziProgram ?: GlProgram(VS_YAZI, FS_YAZI).also {
                it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE); yaziProgram = it
            }
            val yx = y.bmp.width.toFloat() / tex.width; val yy = y.bmp.height.toFloat() / tex.height
            GlUtil.focusFramebufferUsingCurrentContext(tex.fboId, tex.width, tex.height)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)      // bitmap dokusu ön çarpımlı alfa taşır
            p.use()
            p.setSamplerTexIdUniform("uTex", yaziDoku, 0)
            p.setFloatsUniform("uMerkez", floatArrayOf(y.zx - y.kx * yx, y.zy - y.ky * yy))
            p.setFloatsUniform("uYari", floatArrayOf(yx, yy))
            p.setFloatUniform("uAlfa", y.alfa)
            p.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
        return tex.texId
    }

    /** Karışım çizildikten sonra: dönüşüm programlarının çıktı dokuları havuza geri verilir. */
    fun birak() {
        for ((pr, c) in kullanilan) try { pr.releaseOutputFrame(c) } catch (_: Throwable) {}
        kullanilan.clear()
    }

    private fun dokuAc(w: Int, h: Int): GlTextureInfo {
        val tex = GlUtil.createTexture(w, h, false)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return GlTextureInfo(tex, GlUtil.createFboForTexture(tex), -1, w, h)
    }

    private fun hamSil() {
        hamDoku?.let { try { GlUtil.deleteFbo(it.fboId); GlUtil.deleteTexture(it.texId) } catch (_: Throwable) {} }
        hamDoku = null
    }

    fun kapat() {
        if (kapandi) return
        kapandi = true
        val v = video
        val k = t.klip
        // Kayıt: tek denemede sorun görülebilsin diye her geçişin özeti
        if (kareSayi > 0 || bosSayi > 0 || v?.hata != null) {
            val kay = when {
                k == null -> "kapanış kartı"
                k.foto -> "foto ${k.gen}x${k.yuk}"
                else -> "video ${k.gen}x${k.yuk} ${k.kodek} hdr=${k.hdr} · çözücü=${v?.cozucuAd ?: "yok"} · renk=${v?.renk ?: "-"} · ön çözme=${v?.onCozmeMs ?: 0} ms/${v?.onAtlanan ?: 0} kare" +
                    if (yedekKullanildi) " · DURAĞAN KARE (yedek)" else ""
            }
            Kayit.yaz("  Geçiş ${t.etiket} ${t.tur} ${t.gecisMs} ms [${if (t.onizleme) "önizleme" else "kayıt"}]: gelen=$kay · pre-roll=0 (gelen klip kendi ilk ${t.gecisMs} ms'sini 1x oynar) · " +
                "ilk kare=${ilkUs / 1000} ms, son kare=${sonUs / 1000} ms, çizilen=$kareSayi, hazır değil=$bosSayi, hazırlık=${SystemClock.elapsedRealtime() - kurulus} ms" +
                " · kurulum=$kurulumMs ms, ön hazırlık=$adimSayi adım (en uzun ${enUzunAdimMs} ms)${if (isindi) "" else ", GEÇ KALDI"}" +
                (v?.hata?.let { " · HATA: $it" } ?: ""))
        }
        birak()
        try { v?.kapat() } catch (_: Throwable) {}
        try { resim?.kapat() } catch (_: Throwable) {}
        bantlar?.forEach { try { if (!it.isRecycled) it.recycle() } catch (_: Throwable) {} }
        bantlar = null
        if (yukDoku >= 0) try { GlUtil.deleteTexture(yukDoku) } catch (_: Throwable) {}
        for (pr in programlar) try { pr.release() } catch (_: Throwable) {}
        programlar.clear()
        hamSil()
        try { oesProgram?.delete() } catch (_: Throwable) {}
        try { bmpProgram?.delete() } catch (_: Throwable) {}
        try { yaziProgram?.delete() } catch (_: Throwable) {}
        if (yaziDoku >= 0) try { GlUtil.deleteTexture(yaziDoku) } catch (_: Throwable) {}
    }

    companion object {
        const val VS_OES = """
attribute vec4 aFramePosition;
uniform mat4 uTexM;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = (uTexM * vec4(aFramePosition.xy * 0.5 + 0.5, 0.0, 1.0)).xy;
}
"""
        // Video: Media3'ün SDR dış doku yolu gibi, örneklenen renk olduğu gibi kullanılır (dönüştürme yok).
        const val FS_OES = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uTex;
varying vec2 vUv;
void main() {
  gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0);
}
"""
        // Resim: Media3'ün resim girdisinde yaptığı gibi dikey çevrilir ve sRGB → SMPTE 170M çevrilir
        // (fragment_shader_transformation_sdr_internal_es2.glsl ile aynı formüller) → geçişteki fotoğraf ile gövdesi aynı tonda.
        const val FS_BMP = """
precision highp float;
uniform sampler2D uTex;
uniform int uSrgb;
varying vec2 vUv;
float eotf(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float oetf(float c) { return c < 0.018 ? c * 4.5 : 1.099 * pow(c, 0.45) - 0.099; }
void main() {
  vec4 s = texture2D(uTex, vec2(vUv.x, 1.0 - vUv.y));
  vec3 c = s.rgb;
  if (uSrgb == 1) c = vec3(oetf(eotf(c.r)), oetf(eotf(c.g)), oetf(eotf(c.b)));
  gl_FragColor = vec4(c, 1.0);
}
"""
        const val VS_YAZI = """
attribute vec4 aFramePosition;
uniform vec2 uMerkez;
uniform vec2 uYari;
varying vec2 vUv;
void main() {
  gl_Position = vec4(aFramePosition.xy * uYari + uMerkez, 0.0, 1.0);
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""
        const val FS_YAZI = """
precision mediump float;
uniform sampler2D uTex;
uniform float uAlfa;
varying vec2 vUv;
void main() {
  gl_FragColor = texture2D(uTex, vec2(vUv.x, 1.0 - vUv.y)) * uAlfa;
}
"""
    }
}

/** Fotoğrafı / kapanış kartını arka planda açar (GL iş parçacığı beklemez). */
private class ResimYukleyici(private val ac: () -> Bitmap?) {
    private val kilit = Object()
    private var bitti = false
    private var bant: List<Bitmap>? = null
    private var alindi = false
    @Volatile private var iptal = false
    private val isP = Thread({
        val l = try {
            var b = ac()
            if (b != null && b.config != Bitmap.Config.ARGB_8888) { val c = b.copy(Bitmap.Config.ARGB_8888, false); b.recycle(); b = c }
            if (b == null) null else {
                // 24 yatay bant: GL iş parçacığı her karede birini yükler (her biri ~1 MB → birkaç ms)
                val bh = ((b.height + 23) / 24).coerceAtLeast(16)
                val o = ArrayList<Bitmap>()
                var y = 0
                while (y < b.height && !iptal) { val h = minOf(bh, b.height - y); o += Bitmap.createBitmap(b, 0, y, b.width, h); y += h }
                b.recycle()
                if (iptal) { o.forEach { it.recycle() }; null } else o
            }
        } catch (e: Throwable) { Kayit.hata("Geçiş resmi", e); null }
        synchronized(kilit) { bant = l; bitti = true; kilit.notifyAll() }
    }, "studyo-gecis-resim").apply { start() }

    private fun bekle(bekleMs: Long) {
        val son = SystemClock.elapsedRealtime() + bekleMs
        while (!bitti) { val kalan = son - SystemClock.elapsedRealtime(); if (kalan <= 0) break; kilit.wait(kalan) }
    }

    /** true: resim hâlâ açılıyor (bekleMs kadar beklendikten sonra). */
    fun bitmedi(bekleMs: Long): Boolean = synchronized(kilit) { bekle(bekleMs); !bitti }

    fun bantlar(bekleMs: Long): List<Bitmap>? = synchronized(kilit) {
        bekle(bekleMs)
        if (!bitti || alindi) null else { alindi = true; bant }
    }

    fun kapat() { iptal = true; synchronized(kilit) { if (!alindi) { bant?.forEach { it.recycle() }; bant = null } } }
}

/** Gelen videonun ikinci çözücüsü: kendi iş parçacığında çözer, istenen ana ait kareyi SurfaceTexture'a basar.
 *  Kare seçimi "bir sonraki karenin damgası hedefi geçiyorsa bu kare" kuralıyladır (değişken kare hızında da doğru; kare
 *  tekrarı/atlaması üretmez). Açılırken klibin başlangıcına kadar olan kareler (önceki anahtar kareden) arka planda atlanır. */
private class VideoCozucu(private val ctx: Context, private val uri: Uri, private val basUs: Long, private val sdrIste: Boolean, private val etiket: String) {
    val texId: Int = GlUtil.createExternalTexture()
    private val st = SurfaceTexture(texId)
    private val yuzey = Surface(st)
    private val kilit = Object()
    private val dinleyici = HandlerThread("studyo-gecis-kare").apply { start() }
    private var kareGeldi = false
    private var istekUs = -1L; private var istekNo = 0; private var yanitNo = 0
    @Volatile private var dur = false
    @Volatile var hata: String? = null
        private set
    @Volatile var cozucuAd: String = ""
        private set
    @Volatile var renk: String = ""
        private set
    @Volatile var onCozmeMs = 0L
        private set
    @Volatile var onAtlanan = 0
        private set
    private var atlanan = 0
    @Volatile private var gen = 0
    @Volatile private var yuk = 0
    /** Son kare() çağrısı dokuyu yeniledi mi (GL iş parçacığı). */
    var yeniKare = false
        private set
    /** Dokuda duran karenin kaynak damgası (µs). */
    var gosterilenUs = -1L
        private set
    @Volatile private var basilanUs = -1L
    private var dokudaKareVar = false

    private class Kare(val idx: Int, val us: Long)

    init {
        st.setOnFrameAvailableListener({ synchronized(kilit) { kareGeldi = true; kilit.notifyAll() } }, Handler(dinleyici.looper))
    }
    private val isP = Thread({ calis() }, "studyo-gecis-cozucu").apply { start() }

    /** Kopya dokunun boyutu (yön uygulanmış görüntü boyutu; en uzun kenar sınırlanır). */
    fun boyut(enUzun: Int): Pair<Int, Int> {
        val w = gen.coerceAtLeast(2); val h = yuk.coerceAtLeast(2)
        val s = minOf(1f, enUzun.coerceIn(1280, 4096).toFloat() / maxOf(w, h))
        return (w * s).toInt().coerceAtLeast(2) to (h * s).toInt().coerceAtLeast(2)
    }

    fun matris(m: FloatArray) = st.getTransformMatrix(m)

    /** GL iş parçacığı: hedefUs anındaki kareyi dokuya getirir. true: dokuda geçerli bir kare var. */
    fun kare(hedefUs: Long, bekleMs: Long): Boolean {
        yeniKare = false
        var guncelle = false
        synchronized(kilit) {
            if (hata != null) return false
            istekUs = hedefUs; istekNo++
            val no = istekNo
            kilit.notifyAll()
            val son = SystemClock.elapsedRealtime() + bekleMs
            while (yanitNo != no && hata == null) { val kalan = son - SystemClock.elapsedRealtime(); if (kalan <= 0) break; kilit.wait(kalan) }
            if (hata != null) return false
            if (yanitNo == no && basilanUs != gosterilenUs && !kareGeldi) {
                // çözücü kareyi yüzeye bastı; doku bildirimi kısa süre içinde gelir
                val son2 = SystemClock.elapsedRealtime() + minOf(bekleMs, 1000L)
                while (!kareGeldi) { val kalan = son2 - SystemClock.elapsedRealtime(); if (kalan <= 0) break; kilit.wait(kalan) }
            }
            if (kareGeldi) { kareGeldi = false; guncelle = true }
        }
        if (guncelle) {
            st.updateTexImage()
            gosterilenUs = basilanUs
            dokudaKareVar = true
            yeniKare = true
        }
        return dokudaKareVar
    }

    private fun calis() {
        var exKapat: MediaExtractor? = null
        var codecKapat: MediaCodec? = null
        try {
            val ex = MediaExtractor()
            exKapat = ex
            ex.setDataSource(ctx, uri, null)
            var iz = -1; var bulunan: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { iz = i; bulunan = f; break }
            }
            val fmt = bulunan ?: throw IllegalStateException("görüntü izi yok")
            ex.selectTrack(iz)
            val w = fmt.getInteger(MediaFormat.KEY_WIDTH); val h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
            val don = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
            if (don == 90 || don == 270) { gen = h; yuk = w } else { gen = w; yuk = h }
            if (sdrIste && Build.VERSION.SDK_INT >= 31) fmt.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            val codec = cozucuAc(fmt)
            codecKapat = codec
            cozucuAd = try { codec.name } catch (_: Exception) { "?" }
            ex.seekTo(basUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bilgi = MediaCodec.BufferInfo()
            var girisBitti = false; var cikisBitti = false
            var a: Kare? = null; var b: Kare? = null
            var islenen = 0

            fun al(): Kare? {
                if (!girisBitti) {
                    val gi = codec.dequeueInputBuffer(2000)
                    if (gi >= 0) {
                        val buf = codec.getInputBuffer(gi)
                        val n = if (buf != null) ex.readSampleData(buf, 0) else -1
                        if (n < 0) { codec.queueInputBuffer(gi, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); girisBitti = true }
                        else { codec.queueInputBuffer(gi, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val ci = codec.dequeueOutputBuffer(bilgi, 4000)
                if (ci >= 0) {
                    val eos = (bilgi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    if (eos) cikisBitti = true
                    if (bilgi.size <= 0 && eos) { codec.releaseOutputBuffer(ci, false); return null }
                    return Kare(ci, bilgi.presentationTimeUs)
                } else if (ci == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    val tr = if (of.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) of.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
                    renk = when (tr) { MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"; MediaFormat.COLOR_TRANSFER_HLG -> "HLG"; MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ"; 0 -> "belirtilmemiş"; else -> "tr$tr" } +
                        if (sdrIste) " (SDR istendi)" else ""
                }
                return null
            }
            fun sar(us: Long) {
                a?.let { codec.releaseOutputBuffer(it.idx, false) }; b?.let { codec.releaseOutputBuffer(it.idx, false) }
                a = null; b = null
                codec.flush()
                ex.seekTo(us, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                girisBitti = false; cikisBitti = false
            }
            /** hedefi kapsayan kareyi bulur; goster = true ise yüzeye basar. Dönen: iş bitti mi (false: yeniden çağır). */
            fun ilerle(hedef: Long, goster: Boolean): Boolean {
                val x = a
                if (x == null) { if (cikisBitti) return true; a = al(); return false }
                if (x.us > hedef + TOL_US) {
                    // sıradaki kare hedeften sonra: dokudaki kare hâlâ geçerli (ya da henüz hiçbir kare gösterilmedi)
                    if (goster && basilanUs < 0) { codec.releaseOutputBuffer(x.idx, true); basilanUs = x.us; a = b; b = null }
                    return true
                }
                val y = b
                if (y == null) {
                    if (cikisBitti) { if (goster) { codec.releaseOutputBuffer(x.idx, true); basilanUs = x.us; a = null }; return true }
                    b = al(); return false
                }
                if (y.us <= hedef + TOL_US) { codec.releaseOutputBuffer(x.idx, false); atlanan++; a = y; b = null; return false }
                if (goster) { codec.releaseOutputBuffer(x.idx, true); basilanUs = x.us; a = y; b = null }
                return true
            }

            // Ön çözme: klibin başlangıç karesine kadar ilerle (gösterme)
            val t0 = SystemClock.elapsedRealtime()
            while (!dur && !ilerle(basUs, false)) { if (SystemClock.elapsedRealtime() - t0 > 20_000) throw IllegalStateException("ön çözme 20 sn'de bitmedi") }
            onCozmeMs = SystemClock.elapsedRealtime() - t0
            onAtlanan = atlanan

            while (!dur) {
                var hedef = -1L; var no = 0
                synchronized(kilit) {
                    if (istekNo == islenen) {
                        // boşta: sıradaki iki kare hazır değilse hazırlamaya devam et, hazırsa istek bekle
                        if ((a != null && b != null) || cikisBitti) kilit.wait(40)
                    }
                    if (istekNo != islenen) { hedef = istekUs; no = istekNo }
                }
                if (dur) break
                if (no == 0) {
                    if (!cikisBitti) { if (a == null) a = al() else if (b == null) b = al() }
                    continue
                }
                // geri sarma (önizlemede) ya da çok ileri atlama: yeniden konumlan
                val enEski = a?.us ?: basilanUs
                if ((basilanUs >= 0 && hedef < basilanUs - TOL_US) || (enEski >= 0 && hedef > enEski + 1_500_000L)) { sar(hedef); basilanUs = -1L }
                val i0 = SystemClock.elapsedRealtime()
                while (!dur && !ilerle(hedef, true)) { if (SystemClock.elapsedRealtime() - i0 > 15_000) throw IllegalStateException("kare 15 sn'de çözülemedi") }
                synchronized(kilit) { yanitNo = no; kilit.notifyAll() }
                islenen = no
            }
        } catch (e: Throwable) {
            if (!dur) {
                hata = "${e.javaClass.simpleName}: ${e.message}"
                Kayit.hata("Geçiş $etiket ikinci çözücü", e)
            }
        } finally {
            try { codecKapat?.stop() } catch (_: Throwable) {}
            try { codecKapat?.release() } catch (_: Throwable) {}
            try { exKapat?.release() } catch (_: Throwable) {}
            synchronized(kilit) { kilit.notifyAll() }
        }
    }

    /** Önce telefonun bu tür için varsayılan çözücüsü; açılamazsa aynı türü çözen diğerleri (yazılım çözücü dahil). */
    private fun cozucuAc(fmt: MediaFormat): MediaCodec {
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("tür yok")
        val adaylar = ArrayList<String>()
        try { MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(fmt)?.let { adaylar += it } } catch (_: Throwable) {}
        try {
            for (ci in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (ci.isEncoder) continue
                if (ci.supportedTypes.any { it.equals(mime, true) } && ci.name !in adaylar) adaylar += ci.name
            }
        } catch (_: Throwable) {}
        var son: Throwable? = null
        for (ad in adaylar) {
            var c: MediaCodec? = null
            try {
                c = MediaCodec.createByCodecName(ad)
                c.configure(fmt, yuzey, null, 0)
                c.start()
                return c
            } catch (e: Throwable) {
                son = e
                try { c?.release() } catch (_: Throwable) {}
            }
        }
        if (adaylar.isEmpty()) {
            val c = MediaCodec.createDecoderByType(mime)
            try { c.configure(fmt, yuzey, null, 0); c.start(); return c } catch (e: Throwable) { try { c.release() } catch (_: Throwable) {}; throw e }
        }
        throw IllegalStateException("çözücü açılamadı ($mime): ${son?.javaClass?.simpleName}: ${son?.message}")
    }

    /** GL iş parçacığı. Önce çözücü durur (iş parçacığı biter), sonra yüzey bırakılır. */
    fun kapat() {
        dur = true
        synchronized(kilit) { kilit.notifyAll() }
        try { isP.join(1500) } catch (_: Throwable) {}
        try { st.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
        try { yuzey.release() } catch (_: Throwable) {}
        try { st.release() } catch (_: Throwable) {}
        try { GlUtil.deleteTexture(texId) } catch (_: Throwable) {}
        try { dinleyici.quitSafely() } catch (_: Throwable) {}
    }

    companion object { const val TOL_US = 2_000L }
}
