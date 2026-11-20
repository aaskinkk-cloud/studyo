package app.studyo.android

import android.content.Context
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** 3.61: yerleşik müzikler — web Kareden Videoya'daki genMusic / klasikUret'in (Web Audio) Android karşılığı.
 *  Müzik dosyası yok; telifsiz, cihazda üretilir (uygulama silinip kurulsa da hep vardır). Çıktı: WAV (32 kHz stereo). */
object MuzikSentez {
    class Tur(val anahtar: String, val ad: String, val aciklama: String, val simge: String, val renk: Long, val klasik: Boolean = false)
    val TURLER = listOf(
        // 3.71: "Sakin" kaldırıldı (TV'de arka planda hırıltı) → "Huzur": aynı akor yürüyüşü, saf sinüs tınılar, notalar sıfıra iner
        Tur("huzur", "Huzur", "doğa · manzara", "nota", 0xFF5DCAA5),
        Tur("happy", "Neşeli", "insan · kutlama", "yildiz", 0xFFFAC775),
        Tur("warm", "Duygusal", "çay · sofra · anı", "kalp", 0xFFED93B1),
        Tur("drive", "Tempolu", "yol · araç · spor", "hareket", 0xFFF0997B),
        Tur("k_adag", "Klasik · Kanon", "yavaş · zarif", "nota", 0xFFAFA9EC, true),
        Tur("k_alleg", "Klasik · Hareketli", "canlı yaylılar", "nota", 0xFF85B7EB, true),
        Tur("k_dram", "Klasik · Dramatik", "güçlü · gergin", "nota", 0xFFE24B4A, true),
        Tur("k_bach", "Klasik · Piyano", "Bach prelüdü", "nota", 0xFF97C459, true),
    )
    fun tur(k: String) = TURLER.firstOrNull { it.anahtar == k } ?: TURLER[0]

    private const val SR = 32000
    private fun mtof(m: Float) = 440f * 2f.pow((m - 69f) / 12f)

    /** Üretilmiş müzik dosyası (önbellekte varsa onu verir). */
    fun dosya(ctx: Context, anahtar: String, saniye: Int): File {
        val sn = saniye.coerceIn(30, 300)
        val dz = File(ctx.filesDir, "muzik").apply { mkdirs() }
        val f = File(dz, "hazir2_${anahtar}_$sn.wav")   // 3.71: temiz üretim (tık/hırıltı düzeltmesi) — eski önbellek kullanılmaz
        if (f.exists() && f.length() > 1000) return f
        val (L, R) = if (tur(anahtar).klasik) klasik(anahtar, sn.toFloat()) else mod(anahtar, sn.toFloat())
        wavYaz(f, L, R)
        return f
    }

    // ===================== temel parçalar =====================
    private class Tampon(n: Int) { val L = FloatArray(n); val R = FloatArray(n) }
    private fun panG(pan: Float): Pair<Float, Float> { val x = ((pan.coerceIn(-1f, 1f) + 1f) / 2f) * (PI.toFloat() / 2f); return cos(x) to sin(x) }

    /** Bant sınırlı testere (polyBLEP) / üçgen / sinüs. */
    private class Osc(val tur: Int) {   // 0 sinüs, 1 üçgen, 2 testere
        var faz = 0.0; var tri = 0f
        fun sonraki(f: Float): Float {
            val dt = (f / SR).toDouble()
            faz += dt; if (faz >= 1.0) faz -= 1.0
            val t = faz
            return when (tur) {
                0 -> sin(2 * PI * t).toFloat()
                1 -> (if (t < 0.5) 4 * t - 1 else 3 - 4 * t).toFloat()
                else -> { var v = 2 * t - 1; v -= blep(t, dt); v.toFloat() }
            }
        }
        private fun blep(t0: Double, dt: Double): Double {
            if (t0 < dt) { val t = t0 / dt; return t + t - t * t - 1 }
            if (t0 > 1 - dt) { val t = (t0 - 1) / dt; return t * t + t + t + 1 }
            return 0.0
        }
    }

    /** RBJ ikinci derece süzgeç (alçak / yüksek geçiren). */
    private class Biquad(var tip: Int, f: Float, q: Float) {   // 0 alçak, 1 yüksek
        var b0 = 0f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        init { ayarla(f, q) }
        fun ayarla(f: Float, q: Float) {
            val w = 2 * PI * min(f, SR * 0.45f) / SR; val al = sin(w) / (2 * q); val cw = cos(w)
            val a0 = 1 + al
            if (tip == 0) { b0 = ((1 - cw) / 2 / a0).toFloat(); b1 = ((1 - cw) / a0).toFloat(); b2 = b0 }
            else { b0 = ((1 + cw) / 2 / a0).toFloat(); b1 = (-(1 + cw) / a0).toFloat(); b2 = b0 }
            a1 = (-2 * cw / a0).toFloat(); a2 = ((1 - al) / a0).toFloat()
        }
        fun isle(x: Float): Float { val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2; x2 = x1; x1 = x; y2 = y1; y1 = y; return y }
    }

    /** Bir sesi tampona yazar: t (sn) başlangıç, sure (sn) toplam, ornek(i, tSn) → mono değer. */
    private inline fun yaz(b: Tampon, t: Float, sure: Float, pan: Float, kazanc: Float, ornek: (Int, Float) -> Float, ek: Tampon? = null, ekKazanc: Float = 0f) {
        val i0 = (t * SR).toInt(); val n = (sure * SR).toInt()
        val (gl, gr) = panG(pan)
        for (j in 0 until n) {
            val i = i0 + j; if (i < 0) continue; if (i >= b.L.size) break
            val v = ornek(j, j.toFloat() / SR) * kazanc
            b.L[i] += v * gl; b.R[i] += v * gr
            if (ek != null) { ek.L[i] += v * gl * ekKazanc; ek.R[i] += v * gr * ekKazanc }
        }
    }

    // ===================== Sakin / Neşeli / Duygusal / Tempolu (genMusic) =====================
    private class Mod(val bpm: Int, val prog: List<IntArray>, val kick: Boolean, val dalga: Int, val kes: Float)
    private val MODLAR = mapOf(
        "huzur" to Mod(70, listOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62)), false, 0, 2000f),
        "calm" to Mod(72, listOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62)), false, 1, 1600f),
        "happy" to Mod(112, listOf(intArrayOf(60, 64, 67), intArrayOf(55, 59, 62), intArrayOf(57, 60, 64), intArrayOf(53, 57, 60)), true, 1, 2600f),
        "warm" to Mod(66, listOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(60, 64, 67), intArrayOf(55, 59, 62)), false, 1, 1300f),
        "drive" to Mod(124, listOf(intArrayOf(52, 55, 59), intArrayOf(48, 52, 55), intArrayOf(55, 59, 62), intArrayOf(50, 54, 57)), true, 2, 1900f),
    )

    private fun mod(anahtar: String, saniye: Float): Pair<FloatArray, FloatArray> {
        val M = MODLAR[anahtar] ?: MODLAR.getValue("calm")
        val beat = 60f / M.bpm; val bar = beat * 4; val cyc = bar * M.prog.size
        val len = max(cyc * 2, kotlin.math.ceil(saniye / cyc) * cyc)
        val n = (len * SR).toInt() + SR
        val ped = Tampon(n); val ana = Tampon(n); val gec = Tampon(n)
        val nb = Math.round(len / bar)
        for (b in 0 until nb) {
            val ch = M.prog[b % M.prog.size]; val t0 = b * bar
            ch.forEachIndexed { vi, m ->
                for (o in intArrayOf(-12, 0)) {
                    val osc = Osc(M.dalga); val f = mtof((m + o).toFloat()) * 2f.pow((vi - 1) * 6f / 1200f)
                    val v = (if (o != 0) 0.05f else 0.035f) * (if (M.dalga == 2) 0.6f else 1f)
                    val at = min(0.8f, bar * 0.3f); val sus = bar - 0.25f
                    yaz(ped, t0, bar + 0.2f, (vi - 1) * 0.4f, 1f, { _, t ->
                        val e = when { t < at -> v * t / at; t < sus -> v; t < bar + 0.15f -> v * (1f - (t - sus) / (bar + 0.15f - sus)); else -> 0f }
                        osc.sonraki(f) * e })
                }
            }
            val bs = Osc(0); val fb = mtof((ch[0] - 24).toFloat())
            yaz(ana, t0, bar + 0.1f, 0f, 1f, { _, t ->
                val e = when { t < 0.05f -> 0.16f * t / 0.05f; t < bar -> 0.16f * (0.05f / 0.16f).pow((t - 0.05f) / (bar - 0.05f)); t < bar + 0.05f -> 0.05f * (1f - (t - bar) / 0.05f); else -> 0f }
                bs.sonraki(fb) * e })
            val adim = if (M.bpm > 100) 8 else 4; val st = bar / adim
            val notalar = intArrayOf(ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24)
            for (k in 0 until adim) {
                val tt = t0 + k * st; val o = Osc(0); val f = mtof(notalar[(k + b) % notalar.size].toFloat()); val d = min(0.9f, st * 1.8f)
                // 3.71 KÖK DÜZELTME: zarf 0,001'de kalıp pencere sonunda kesiliyordu (her notada yankıyla çoğalan tık) → sonda 20 ms'de sıfıra iner
                yaz(ana, tt, d + 0.02f, 0f, 1f, { _, t -> val e = when { t < 0.01f -> 0.07f * t / 0.01f; t < d -> 0.07f * (0.001f / 0.07f).pow((t - 0.01f) / (d - 0.01f))
                    else -> 0.001f * (1f - (t - d) / 0.02f).coerceAtLeast(0f) }; o.sonraki(f) * e }, gec, 1f)
            }
            if (M.kick) for (k in 0 until 4) {
                val tt = t0 + k * beat; var faz = 0.0
                yaz(ana, tt, 0.3f, 0f, 1f, { _, t ->
                    val f = 120f * (42f / 120f).pow((t / 0.12f).coerceAtMost(1f)); faz += f / SR
                    val e = if (t < 0.25f) 0.35f * (0.001f / 0.35f).pow(t / 0.25f) else 0.001f * (1f - (t - 0.25f) / 0.05f).coerceAtLeast(0f)   // 3.71: sıfıra iner
                    sin(2 * PI * faz).toFloat() * e })
            }
        }
        // pedlar alçak geçiren süzgeçten (Q 0,4)
        val lpL = Biquad(0, M.kes, 0.4f); val lpR = Biquad(0, M.kes, 0.4f)
        for (i in 0 until n) { ana.L[i] += lpL.isle(ped.L[i]); ana.R[i] += lpR.isle(ped.R[i]) }
        // yankı (gecikme 0,75 vuruş, geri besleme 0,28, ıslak 0,25)
        val dn = (beat * 0.75f * SR).toInt().coerceAtLeast(1)
        val dlL = FloatArray(n); val dlR = FloatArray(n)
        for (i in 0 until n) {
            val gl = if (i >= dn) dlL[i - dn] else 0f; val gr = if (i >= dn) dlR[i - dn] else 0f
            dlL[i] = gec.L[i] + gl * 0.28f; dlR[i] = gec.R[i] + gr * 0.28f
            ana.L[i] += gl * 0.25f; ana.R[i] += gr * 0.25f
        }
        for (i in 0 until n) { ana.L[i] *= 0.5f; ana.R[i] *= 0.5f }
        val m = (len * SR).toInt()
        return normalle(ana.L.copyOf(m), ana.R.copyOf(m), 0.89f, 0.2f, 0f)
    }

    // ===================== Klasik (klasikUret) =====================
    private fun klasik(anahtar: String, saniye: Float): Pair<FloatArray, FloatArray> {
        val len = max(20f, saniye); val n = ((len + 3) * SR).toInt()
        val kuru = Tampon(n); val rev = Tampon(n)
        val rg = if (anahtar == "k_dram") 0.32f else 0.42f
        val rnd = Random(7)
        fun cik(t: Float, sure: Float, pan: Float, ornek: (Int, Float) -> Float) = yaz(kuru, t, sure, pan, 0.85f, ornek, rev, rg / 0.85f)

        /** yaylılar */
        fun yay(m: Int, t: Float, d: Float, v: Float, pan: Float, at0: Float? = null, rl0: Float? = null, parlak: Float = 2400f, sus: Float = 0.85f) {
            val at = at0 ?: min(0.25f, d * 0.3f); val rl = rl0 ?: min(0.35f, d * 0.5f)
            val tSus = max(at, d - rl * 0.3f); val son = d + rl
            val f = mtof(m.toFloat()); val ikili = d >= 0.35f
            val o1 = Osc(2); val o2 = Osc(2); val lp = Biquad(0, parlak, 0.5f); var lfoF = 0.0
            cik(t, son + 0.05f, pan) { _, tt ->
                val e = when { tt < at -> v * tt / at; tt < tSus -> v; tt < son -> v * sus * (1f - (tt - tSus) / (son - tSus)); else -> 0f }
                var fm = f
                if (d > 0.6f) { lfoF += 5.4 / SR; fm += sin(2 * PI * lfoF).toFloat() * f * 0.004f * (tt / min(0.6f, d * 0.5f)).coerceAtMost(1f) }
                val s = if (ikili) (o1.sonraki(fm * 2f.pow(-7f / 1200f)) + o2.sonraki(fm * 2f.pow(7f / 1200f))) else o1.sonraki(fm)
                lp.isle(s) * e
            }
        }
        /** piyano */
        fun piy(m: Int, t: Float, d: Float, v: Float, pan: Float) {
            val dec = max(0.6f, 2.6f - (m - 48) * 0.03f); val f = mtof(m.toFloat())
            val son = max(d, 0.3f) + dec * 0.6f
            val a = Osc(1); val b = Osc(0); val c = Osc(0)
            cik(t, son + 0.05f, pan) { _, tt ->
                val e = when { tt < 0.006f -> v * tt / 0.006f; tt < dec * 0.35f -> v * (0.25f).pow((tt - 0.006f) / (dec * 0.35f - 0.006f))
                    tt < son -> v * 0.25f * (0.0008f / (v * 0.25f)).pow((tt - dec * 0.35f) / (son - dec * 0.35f)); else -> 0.0008f * (1f - (tt - son) / 0.05f).coerceAtLeast(0f) }   // 3.71: sıfıra iner
                (a.sonraki(f) + b.sonraki(f * 2 * 1.00116f) * 0.35f + c.sonraki(f * 3 * 1.00116f) * 0.12f) * e
            }
        }
        /** klavsen */
        fun klv(m: Int, t: Float, v: Float, pan: Float) {
            val o = Osc(2); val hp = Biquad(1, 400f, 0.707f); val lp = Biquad(0, 3500f, 0.707f); val f = mtof(m.toFloat())   // 3.71: üst vızıltı süzülür
            cik(t, 0.75f, pan) { _, tt -> lp.isle(hp.isle(o.sonraki(f))) * (if (tt < 0.7f) v * (0.001f / v).pow(tt / 0.7f) else 0.001f * (1f - (tt - 0.7f) / 0.05f).coerceAtLeast(0f)) }
        }
        /** timpani */
        fun tim(m: Int, t: Float, v: Float) {
            val f = mtof(m.toFloat()); var faz = 0.0; val lp = Biquad(0, 700f, 0.707f)
            cik(t, 1.65f, 0f) { _, tt ->
                val ff = f * (1.04f * (1f / 1.04f).pow((tt / 0.12f).coerceAtMost(1f))); faz += ff / SR
                val e = if (tt < 1.6f) v * (0.001f / v).pow(tt / 1.6f) else 0.001f * (1f - (tt - 1.6f) / 0.05f).coerceAtLeast(0f)   // 3.71: sıfıra iner
                val gurultu = if (tt < 0.12f) lp.isle((rnd.nextFloat() * 2 - 1) * (1 - tt / 0.12f)) * 0.6f else 0f
                (sin(2 * PI * faz).toFloat() + gurultu) * e
            }
        }
        /** bakır vurgusu (akor) */
        fun bak(ms: IntArray, t: Float, d: Float, v: Float) {
            ms.forEachIndexed { i, m ->
                val o = Osc(2); val lp = Biquad(0, 500f, 0.707f); val f = mtof(m.toFloat())
                cik(t, d + 0.3f, (i - 1) * 0.25f) { j, tt ->
                    if (j % 64 == 0) lp.ayarla(if (tt < 0.08f) 500f + 2100f * tt / 0.08f else 2600f * (900f / 2600f).pow(((tt - 0.08f) / (d - 0.08f)).coerceIn(0f, 1f)), 0.707f)
                    val e = when { tt < 0.03f -> v * tt / 0.03f; tt < d -> v * (0.3f).pow((tt - 0.03f) / (d - 0.03f)); tt < d + 0.25f -> v * 0.3f * (1 - (tt - d) / 0.25f); else -> 0f }
                    lp.isle(o.sonraki(f)) * e
                }
            }
        }

        when (anahtar) {
            "k_alleg" -> {
                val b = 60f / 132; val bar = b * 4
                val P = listOf(50 to intArrayOf(62, 66, 69), 55 to intArrayOf(62, 67, 71), 57 to intArrayOf(61, 64, 69), 50 to intArrayOf(62, 66, 69),
                    47 to intArrayOf(59, 62, 66), 55 to intArrayOf(62, 67, 71), 52 to intArrayOf(59, 64, 67), 57 to intArrayOf(61, 64, 67))
                val nb = kotlin.math.ceil(len / bar).toInt()
                for (k in 0 until nb) {
                    val (bs, ch) = P[k % P.size]; val t0 = k * bar; val ileri = (k / P.size) % 2 == 1
                    for (q in 0 until 4) { yay(bs - 12, t0 + q * b, b * 0.7f, 0.05f, -0.3f, 0.015f, 0.08f); if (q % 2 == 0) klv(bs + 12, t0 + q * b, 0.03f, 0.2f) }
                    ch.forEachIndexed { i, m -> yay(m, t0, bar * 0.98f, 0.012f, (i - 1) * 0.35f, 0.12f) }
                    val sc = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12).map { it + 62 }; val up = intArrayOf(ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 12)
                    for (s in 0 until 16) {
                        val m = if (ileri && s < 8 && k % 2 == 0) sc[(s + (ch[0] - 62 + 24)) % 8] else up[s % 4] + (if (s >= 8 && k % 2 == 1) 12 else 0)
                        yay(m, t0 + s * b / 4, b / 4 * 0.8f, 0.026f, 0.25f, 0.008f, 0.05f, 3200f, 1f)
                    }
                    if (k % 4 == 3) for (s in 0 until 8) yay(ch[2] + 12 - s, t0 + bar - b * 2 + s * b / 4, b / 4 * 0.8f, 0.02f, -0.15f, 0.008f, 0.05f, 3000f)
                }
            }
            "k_adag" -> {
                val b = 60f / 54
                val P = listOf(50 to intArrayOf(62, 66, 69), 45 to intArrayOf(61, 64, 69), 47 to intArrayOf(62, 66, 71), 42 to intArrayOf(61, 66, 69),
                    43 to intArrayOf(62, 67, 71), 38 to intArrayOf(62, 66, 69), 43 to intArrayOf(62, 67, 71), 45 to intArrayOf(61, 64, 69))
                val ust = intArrayOf(78, 76, 74, 73, 71, 69, 71, 73); val cyc = b * 2 * P.size; val nc = kotlin.math.ceil(len / cyc).toInt()
                for (c in 0 until nc) P.forEachIndexed { i, (bs, ch) ->
                    val t0 = c * cyc + i * b * 2
                    yay(bs - 12, t0, b * 2 * 0.95f, 0.06f, -0.25f, 0.18f); ch.forEachIndexed { j, m -> yay(m, t0, b * 2 * 0.98f, 0.018f, (j - 1) * 0.4f, 0.35f, null, 1800f) }
                    if (c >= 1) yay(ust[i], t0, b * 2 * 0.96f, 0.03f, 0.15f, 0.3f, null, 2600f)
                    if (c >= 2) { val ar = intArrayOf(ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 12); for (s in 0 until 4) piy(ar[s], t0 + s * b / 2, b / 2, 0.05f, 0.3f) }
                }
            }
            "k_dram" -> {
                val b = 60f / 96; val bar = b * 4
                val P = listOf(38 to intArrayOf(62, 65, 69), 34 to intArrayOf(62, 65, 70), 43 to intArrayOf(62, 67, 70), 45 to intArrayOf(61, 64, 69),
                    38 to intArrayOf(62, 65, 69), 41 to intArrayOf(60, 65, 69), 43 to intArrayOf(62, 67, 70), 45 to intArrayOf(61, 64, 69))
                val mel = listOf(81 to 2, 82 to 1, 81 to 1, 79 to 2, 77 to 2, 76 to 3, 74 to 1); val nb = kotlin.math.ceil(len / bar).toInt()
                for (k in 0 until nb) {
                    val (bs, ch) = P[k % P.size]; val t0 = k * bar; val dongu = k / P.size; val guc = min(1f, 0.55f + 0.15f * dongu + 0.05f * (k % P.size))
                    for (s in 0 until 8) yay(bs, t0 + s * b / 2, b / 2 * 0.7f, (if (s % 2 == 1) 0.035f else 0.055f) * guc, -0.2f, 0.01f, 0.06f, 1400f)
                    for (s in 0 until 8) yay(bs + 12, t0 + s * b / 2, b / 2 * 0.6f, 0.02f * guc, 0.25f, 0.01f, 0.05f, 1600f)
                    ch.forEachIndexed { i, m ->   // titreşimli yaylı akor
                        val o = Osc(2); val lp = Biquad(0, 3200f, 0.707f); val f = mtof((m + 12).toFloat()); var lf = 0.0
                        cik(t0, bar + 0.1f, (i - 1) * 0.45f) { _, tt ->
                            lf += 12.0 / SR
                            val e = when { tt < 0.05f -> 0.006f * guc * tt / 0.05f; tt < bar - 0.05f -> 0.006f * guc; tt < bar + 0.05f -> 0.006f * guc * (1 - (tt - bar + 0.05f) / 0.1f); else -> 0f }
                            lp.isle(o.sonraki(f)) * (e + sin(2 * PI * lf).toFloat() * 0.004f * guc * (if (e > 0f) 1f else 0f))
                        }
                    }
                    val tb = if (bs < 40) bs + 12 else bs
                    tim(tb, t0, 0.5f * guc); if (k % 2 == 1) tim(tb, t0 + b * 2, 0.3f * guc)
                    if (k % 4 == 0) bak(ch.map { it - 12 }.toIntArray(), t0, 0.5f, 0.03f * guc)
                    if (k % P.size == P.size - 1) for (r in 0 until 12) tim(45, t0 + bar - b + r * b / 12, 0.06f + r * 0.02f)
                    if (dongu >= 1 && k % 4 == 0) { var tt = t0; mel.forEach { (m, d) -> yay(m - (if (k % 8 == 4) 5 else 0), tt, d * b * 0.95f, 0.032f * guc, 0.1f, 0.06f, null, 3000f); tt += d * b } }
                }
            }
            else -> {   // k_bach: BWV 846
                val s16 = 60f / 66 / 4; val bar = s16 * 16
                val A = listOf(intArrayOf(60, 64, 67, 72, 76), intArrayOf(60, 62, 69, 74, 77), intArrayOf(59, 62, 67, 74, 77), intArrayOf(60, 64, 67, 72, 76),
                    intArrayOf(60, 64, 69, 76, 81), intArrayOf(60, 62, 66, 69, 74), intArrayOf(59, 62, 67, 74, 79), intArrayOf(59, 60, 64, 67, 72),
                    intArrayOf(57, 60, 64, 67, 72), intArrayOf(60, 62, 66, 69, 74), intArrayOf(59, 62, 67, 74, 77), intArrayOf(60, 64, 67, 72, 76))
                val nb = kotlin.math.ceil(len / bar).toInt(); val pat = intArrayOf(0, 1, 2, 3, 4, 2, 3, 4)
                for (k in 0 until nb) { val nn = A[k % A.size]; val t0 = k * bar
                    for (h in 0 until 2) { piy(nn[0], t0 + h * s16 * 8, s16 * 8, 0.07f, -0.2f); piy(nn[1], t0 + h * s16 * 8 + s16, s16 * 7, 0.055f, -0.1f)
                        for (s in 2 until 8) piy(nn[pat[s]], t0 + h * s16 * 8 + s * s16, s16 * 1.6f, 0.05f, 0.15f) } }
            }
        }
        // salon yankısı (Freeverb benzeri: 4 tarak + 2 tümgeçiren, her kanal ayrı)
        val wL = yanki(rev.L, 0); val wR = yanki(rev.R, 23)
        val L = FloatArray(n) { (kuru.L[it] + wL[it]) * 0.62f }; val R = FloatArray(n) { (kuru.R[it] + wR[it]) * 0.62f }
        return normalle(L, R, 0.89f, 0.14f, 2.5f, (len * SR).toInt())
    }

    private fun yanki(x: FloatArray, kay: Int): FloatArray {
        val o = FloatArray(x.size)
        val taraklar = intArrayOf(1116, 1188, 1277, 1356).map { (it + kay) * SR / 44100 }
        for (d in taraklar) { val buf = FloatArray(d); var p = 0; var lp = 0f
            for (i in x.indices) { val y = buf[p]; lp = y * 0.8f + lp * 0.2f; buf[p] = x[i] + lp * 0.84f; p = (p + 1) % d; o[i] += y * 0.25f } }
        for (d0 in intArrayOf(556, 441)) { val d = (d0 + kay) * SR / 44100; val buf = FloatArray(d); var p = 0
            for (i in o.indices) { val b = buf[p]; val y = -o[i] + b; buf[p] = o[i] + b * 0.5f; p = (p + 1) % d; o[i] = y } }
        return o
    }

    /** Tepe ve RMS sınırına göre ölçekler; isteğe bağlı sonda yumuşak kısılma. */
    private fun normalle(L: FloatArray, R: FloatArray, tepe: Float, rms: Float, fadeSn: Float, uzunluk: Int = L.size): Pair<FloatArray, FloatArray> {
        var pk = 0f; var e = 0.0; var n = 0
        for (i in L.indices step 3) { val a = abs(L[i]); val b = abs(R[i]); if (a > pk) pk = a; if (b > pk) pk = b; e += (L[i] * L[i] + R[i] * R[i]).toDouble(); n += 2 }
        val k = min(tepe / max(1e-6f, pk), rms / max(1e-6f, sqrt(e / max(1, n)).toFloat()))
        val fadeN = (SR * fadeSn).toInt(); val st = max(0, uzunluk - fadeN)
        val m = min(uzunluk + (if (fadeN > 0) 0 else 0), L.size)
        val oL = FloatArray(m); val oR = FloatArray(m)
        for (i in 0 until m) { val f = if (fadeN <= 0 || i < st) 1f else max(0f, 1f - (i - st).toFloat() / fadeN); oL[i] = L[i] * k * f; oR[i] = R[i] * k * f }
        return oL to oR
    }

    private fun wavYaz(f: File, L: FloatArray, R: FloatArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        DataOutputStream(FileOutputStream(tmp).buffered(1 shl 16)).use { o ->
            fun i32(v: Int) { o.write(v and 255); o.write(v shr 8 and 255); o.write(v shr 16 and 255); o.write(v shr 24 and 255) }
            fun i16(v: Int) { o.write(v and 255); o.write(v shr 8 and 255) }
            val veri = L.size * 4
            o.writeBytes("RIFF"); i32(36 + veri); o.writeBytes("WAVE"); o.writeBytes("fmt "); i32(16); i16(1); i16(2); i32(SR); i32(SR * 4); i16(4); i16(16)
            o.writeBytes("data"); i32(veri)
            // 3.71: 16 bit'e yuvarlama + TPDF titreşim (önce kesme: sessizleşen kuyruklarda pürüzlü taneciklenme)
            val r = java.util.Random(11)
            fun q(x: Float): Int = Math.round(x.coerceIn(-1f, 1f) * 32766f + (r.nextFloat() - r.nextFloat())).coerceIn(-32768, 32767)
            for (i in L.indices) { i16(q(L[i])); i16(q(R[i])) }
        }
        tmp.renameTo(f)
    }

    @Suppress("unused") private fun kullanilmadi() = exp(ln(1.0))
}
