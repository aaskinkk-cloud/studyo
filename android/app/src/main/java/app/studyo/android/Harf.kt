package app.studyo.android

import androidx.compose.ui.text.input.KeyboardCapitalization

/** 3.77: yazıların harf biçimi — çizimde uygulanır (klavyeyle ya da sesle yazılmış olsun, sonuç aynı). Türkçe kurallarla (i → İ, I → ı). */
object Harf {
    val SECENEK = listOf("" to "Olduğu gibi", "cumle" to "Cümle başı", "bas" to "Baş Harfler", "buyuk" to "BÜYÜK", "kucuk" to "küçük")

    fun uygula(s: String, kip: String): String = when (kip) {
        "buyuk" -> s.uppercase(TR)
        "kucuk" -> s.lowercase(TR)
        "bas" -> buyukBasla(s) { o, h -> o == null || o.isWhitespace() || o == '-' || o == '(' || o == '"' || o == '|' || h }
        "cumle" -> buyukBasla(s) { o, h -> h }
        else -> s
    }

    /** Her harfin önüne bakılır; kural doğruysa o harf büyütülür. cümle: metnin, satırın ya da . ! ? … sonrasının ilk harfi. */
    private inline fun buyukBasla(s: String, kural: (Char?, Boolean) -> Boolean): String {
        val sb = StringBuilder(s.length)
        var cumleBasi = true; var onceki: Char? = null
        for (c in s) {
            if (c.isLetter()) {
                sb.append(if (kural(onceki, cumleBasi)) c.toString().uppercase(TR) else c.toString())
                cumleBasi = false
            } else {
                sb.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '…' || c == '\n' || c == '|') cumleBasi = true
            }
            onceki = c
        }
        return sb.toString()
    }

    /** Klavye de aynı biçimde açılsın (sesle yazmada da ilk harf buna göre gelir). */
    fun klavye(kip: String): KeyboardCapitalization = when (kip) {
        "buyuk" -> KeyboardCapitalization.Characters
        "bas" -> KeyboardCapitalization.Words
        "kucuk" -> KeyboardCapitalization.None
        else -> KeyboardCapitalization.Sentences
    }
}
