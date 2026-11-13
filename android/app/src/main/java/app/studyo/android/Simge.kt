package app.studyo.android

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** Uygulamanın kendi çizgi simgeleri (24×24). Ek kitaplık yok; APK büyümez. */
object Simgeler {
    private val cizgi = mapOf(
        "geri" to "M19 12H5M12 19l-7-7 7-7",
        "kaydet" to "M5 3h11l4 4v14H5zM8 3v5h8V3M8 21v-7h9v7",
        "disa" to "M12 15V3M7 8l5-5 5 5M5 13v8h14v-8",
        "asagi" to "M6 9l6 6 6-6",
        "makas" to "M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM20 4L8.1 15.9M14.5 14.5L20 20M8.1 8.1L12 12",
        "nota" to "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
        "metin" to "M4 7V4h16v3M9 20h6M12 4v16",
        "yildiz" to "M12 2l3.1 6.3 6.9 1-5 4.9 1.2 6.8-6.2-3.2-6.2 3.2 1.2-6.8-5-4.9 6.9-1z",
        "sihir" to "M15 4V2M15 16v-2M8 9h2M20 9h2M17.8 11.8L19 13M17.8 6.2L19 5M3 21l9-9M12.2 6.2L11 5",
        "gecis" to "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3",
        "kadraj" to "M6 2v14a2 2 0 0 0 2 2h14M18 22V8a2 2 0 0 0-2-2H2",
        "hareket" to "M5 9l-3 3 3 3M9 5l3-3 3 3M15 19l-3 3-3-3M19 9l3 3-3 3M2 12h20M12 2v20",
        "klip" to "M3 5h18v14H3zM3 10h18M8 5v5M13 5v5M18 5v5",
        "ekle" to "M12 5v14M5 12h14",
        "katman" to "M12 2L2 7l10 5 10-5zM2 17l10 5 10-5M2 12l10 5 10-5",
        "bicim" to "M3 6h18v12H3zM7 10v4M17 10v4",
        "ses" to "M11 5L6 9H2v6h4l5 4zM19 5a10 10 0 0 1 0 14M15.5 8.5a5 5 0 0 1 0 7",
        "mikrofon" to "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v2a7 7 0 0 1-14 0v-2M12 19v3M8 22h8",
        "takip" to "M12 2v4M12 18v4M2 12h4M18 12h4M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
        "altyazi" to "M3 5h18v14H3zM7 15h4M13 15h4M7 11h2M11 11h6",
        "logo" to "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6.2 18.5a7 7 0 0 1 11.6 0",
        "kapak" to "M3 3h18v18H3zM8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM21 15l-5-5L5 21",
        "uygula" to "M20 6L9 17l-5-5",
        "ileri" to "M9 6l6 6-6 6",
        "gorunum" to "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 2v20",
        "menu" to "M12 6h.01M12 12h.01M12 18h.01",
        "basa" to "M5 5v14",
        "tuval" to "M4 8V4h4M16 4h4v4M20 16v4h-4M8 20H4v-4M8 9h8v6H8z",
        "prompter" to "M4 4h16v12H4zM8 20h8M12 16v4M7 8h10M7 11h7",
        "sag" to "M5 12h14M12 5l7 7-7 7",
        "cift" to "M1.5 12.5l4.5 4.5L16 7M10.5 16l1 1L22 7",
        "gerial" to "M9 14L4 9l5-5M4 9h11a5 5 0 0 1 0 10h-3",
        "yinele" to "M15 14l5-5-5-5M20 9H9a5 5 0 0 0 0 10h3",
        "cogalt" to "M9 9h11v11H9zM5 15H4V4h11v1",
        "sil" to "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14",
        "kapat" to "M18 6L6 18M6 6l12 12",
        "degistir" to "M21 12a9 9 0 0 1-15 6.7L3 16M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M3 21v-5h5",
        "yukle" to "M12 3v12M7 10l5 5 5-5M5 21h14",
        "pil" to "M3 8h16v8H3zM21 11v2M6 11v2",
        "kisi" to "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0M3 7V3h4M21 7V3h-4",
        "baslik" to "M4 5h9v4H4zM4 11h14v4H4zM4 17h7v3H4z",
        "zil" to "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0",
        "kalp" to "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z",
    )
    private val dolu = mapOf(
        "oynat" to "M8 5v14l11-7z",
        "durdur" to "M6 5h4v14H6zM14 5h4v14h-4z",
        "onceki" to "M6 6h2v12H6zM9.5 12L18 18V6z",
        "sonraki" to "M16 6h2v12h-2zM6 18l8.5-6L6 6z",
        "yarim" to "M12 2a10 10 0 0 0 0 20z",
        "basa" to "M19 6l-10 6 10 6z",
    )
    private val onbellek = HashMap<String, ImageVector>()

    fun al(ad: String): ImageVector = onbellek.getOrPut(ad) {
        val b = ImageVector.Builder(name = ad, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        dolu[ad]?.let { b.addPath(pathData = addPathNodes(it), fill = SolidColor(Color.White)) }
        cizgi[ad]?.let {
            b.addPath(pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        if (ad == "gorunum") b.addPath(pathData = addPathNodes(dolu.getValue("yarim")), fill = SolidColor(Color.White))
        b.build()
    }
}

@Composable
fun Simge(ad: String, renk: Color, boy: Dp = 22.dp, modifier: Modifier = Modifier) =
    Icon(Simgeler.al(ad), contentDescription = null, tint = renk, modifier = modifier.size(boy))
