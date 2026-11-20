package app.studyo.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 3.61: "Son ayarlarımı sakla" — projenin görünüm ayarları yeni videoların varsayılanı olur (Yayın › Ayarlar). */
@Serializable
data class SakliAyar(
    val giris: Giris, val hatirlatici: Hatirlatici, val kapanis: Kapanis, val gecis: Gecis,
    val logo: Logo? = null, val altyaziStil: AltyaziStil = AltyaziStil(), val kapak: Kapak = Kapak(), val bicim: String = "16:9",
    val renk: RenkAyar? = null, val filtre: String = "yok", val filtreGuc: Float = 0.8f, val efekt: String = "yok", val efektGuc: Float = 0.7f,
    val muzikTur: String? = null, val zaman: Long = System.currentTimeMillis(),
)

object Ayarlar {
    private const val ANAHTAR = "sakliAyar"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ayar", 0)

    fun sakla(ctx: Context, p: Proje, k: Klip?) {
        val s = anlik(p, k)
        prefs(ctx).edit().putString(ANAHTAR, JSON.encodeToString(SakliAyar.serializer(), s)).apply()
        otoYedek(ctx)
    }

    // ---------- 3.70: kayıtlı ayarlarım (adlı; "ayar" tercihinde → yedeğe de girer) ----------
    private const val KAYITLI = "kayitliAyarlar"
    fun kayitlilar(ctx: Context): List<Pair<String, SakliAyar>> = try {
        val o = JSONObject(prefs(ctx).getString(KAYITLI, null) ?: "{}")
        o.keys().asSequence().mapNotNull { ad -> runCatching { ad to JSON.decodeFromString(SakliAyar.serializer(), o.getString(ad)) }.getOrNull() }
            .sortedByDescending { it.second.zaman }.toList()
    } catch (e: Exception) { Kayit.hata("kayıtlı ayarlar", e); emptyList() }
    fun kayitliEkle(ctx: Context, ad: String, p: Proje, k: Klip?) {
        val o = try { JSONObject(prefs(ctx).getString(KAYITLI, null) ?: "{}") } catch (e: Exception) { JSONObject() }
        o.put(ad, JSON.encodeToString(SakliAyar.serializer(), anlik(p, k)))
        prefs(ctx).edit().putString(KAYITLI, o.toString()).apply(); otoYedek(ctx)
    }
    fun kayitliSil(ctx: Context, ad: String) {
        val o = try { JSONObject(prefs(ctx).getString(KAYITLI, null) ?: "{}") } catch (e: Exception) { JSONObject() }
        o.remove(ad); prefs(ctx).edit().putString(KAYITLI, o.toString()).apply(); otoYedek(ctx)
    }

    /** Projenin o anki görünüm ayarları (metinler ve kaynak dosyalar hariç). */
    fun anlik(p: Proje, k: Klip?): SakliAyar {
        val muzikTur = p.muzik?.let { m -> MuzikSentez.TURLER.firstOrNull { m.ad == "Hazır müzik · ${it.ad}" }?.anahtar }
        return SakliAyar(
            giris = p.giris.copy(baslik = "", alt = "", bant = ""), hatirlatici = p.hatirlatici, kapanis = p.kapanis, gecis = p.gecis,
            logo = p.logo?.copy(yol = ""), altyaziStil = p.altyaziStil, kapak = p.kapak.copy(kaynakUri = "", kaynakMs = 0, yazi = "", bant = ""), bicim = p.bicim,
            renk = k?.renk, filtre = k?.filtre ?: "yok", filtreGuc = k?.filtreGuc ?: 0.8f, efekt = k?.efekt ?: "yok", efektGuc = k?.efektGuc ?: 0.7f,
            muzikTur = muzikTur,
        )
    }
    fun sakli(ctx: Context): SakliAyar? = try { prefs(ctx).getString(ANAHTAR, null)?.let { JSON.decodeFromString(SakliAyar.serializer(), it) } } catch (e: Exception) { Kayit.hata("saklı ayar", e); null }
    fun sil(ctx: Context) { prefs(ctx).edit().remove(ANAHTAR).apply(); otoYedek(ctx) }

    // ---------------- yedek (Belgeler/… — uygulama silinse de kalır) ----------------
    private val PREFLER = listOf("ayar", "duzen")
    fun otoYedekUri(ctx: Context): Uri? = prefs(ctx).getString("yedekUri", null)?.let { Uri.parse(it) }

    /** Yedek dosyası: ayarlar (JSON) + logo kütüphanesi. uri kalıcı izinle tutulur; sonraki değişikliklerde kendiliğinden güncellenir. */
    fun yedekYaz(ctx: Context, uri: Uri, kalici: Boolean = true) {
        if (kalici) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            prefs(ctx).edit().putString("yedekUri", uri.toString()).apply()
        }
        val kok = JSONObject()
        for (ad in PREFLER) {
            val o = JSONObject()
            for ((k, v) in ctx.getSharedPreferences(ad, 0).all) {
                if (ad == "ayar" && k == "yedekUri") continue
                val e = JSONObject()
                when (v) {
                    is Boolean -> { e.put("t", "b"); e.put("v", v) }
                    is Int -> { e.put("t", "i"); e.put("v", v) }
                    is Long -> { e.put("t", "l"); e.put("v", v) }
                    is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                    is String -> { e.put("t", "s"); e.put("v", v) }
                    is Set<*> -> { e.put("t", "ss"); e.put("v", JSONArray(v.map { it.toString() })) }
                    else -> continue
                }
                o.put(k, e)
            }
            kok.put(ad, o)
        }
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                z.putNextEntry(ZipEntry("ayarlar.json")); z.write(kok.toString().toByteArray()); z.closeEntry()
                val dz = Depo.dizin("logolar")
                dz.listFiles()?.filter { it.isFile }?.forEach { f -> z.putNextEntry(ZipEntry("logolar/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        } ?: error("Yedek dosyası açılamadı")
    }

    /** Ayarlar değişince (saklama, logo ekleme) yedek kendiliğinden güncellenir. */
    fun otoYedek(ctx: Context) {
        val u = otoYedekUri(ctx) ?: return
        Thread { try { yedekYaz(ctx, u, kalici = false) } catch (e: Exception) { Kayit.hata("otomatik yedek", e) } }.start()
    }

    /** Yedekten geri yükler (ayarlar + logolar). Uygulama yeniden açılınca tümü etkin olur. */
    fun yedekOku(ctx: Context, uri: Uri): Int {
        var logo = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "ayarlar.json") {
                        val kok = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                        for (ad in PREFLER) {
                            val o = kok.optJSONObject(ad) ?: continue
                            val ed = ctx.getSharedPreferences(ad, 0).edit()
                            for (k in o.keys()) {
                                val x = o.getJSONObject(k)
                                when (x.getString("t")) {
                                    "b" -> ed.putBoolean(k, x.getBoolean("v")); "i" -> ed.putInt(k, x.getInt("v")); "l" -> ed.putLong(k, x.getLong("v"))
                                    "f" -> ed.putFloat(k, x.getDouble("v").toFloat()); "s" -> ed.putString(k, x.getString("v"))
                                    "ss" -> { val a = x.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                                }
                            }
                            ed.commit()
                        }
                    } else if (e.name.startsWith("logolar/") && !e.isDirectory) {
                        val ad = e.name.removePrefix("logolar/").substringAfterLast('/')
                        if (ad.isNotEmpty() && !ad.contains("..")) {
                            val hedef = File(Depo.dizin("logolar"), ad)
                            hedef.outputStream().use { z.copyTo(it) }
                            if (ad.endsWith(".png")) logo++
                        }
                    }
                    e = z.nextEntry
                }
            }
        } ?: error("Yedek dosyası açılamadı")
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs(ctx).edit().putString("yedekUri", uri.toString()).apply() } catch (_: Exception) {}
        LogoKutup.yenidenYukle()
        return logo
    }
}
