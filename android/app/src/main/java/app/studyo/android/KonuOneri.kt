package app.studyo.android

/** 3.72: giriş başlığı için konuya göre örnek yazılar. Konu, müzik seçimindeki ML Kit görüntü etiketlemesinden gelir
 *  (huzur = doğa · happy = insan/hayvan · warm = çay/sofra · drive = yol · k_adag = yapı · k_dram = sis/gece · k_bach = iç mekân).
 *  "Kamp" bilerek yok (her video kamp değil). */
object KonuOneri {
    private val BASLIK = mapOf(
        "huzur" to listOf("Doğanın Sessizliğinde Bir Gün", "Yaylada Huzurlu Saatler", "Ormanın Derinliklerinde", "Karadeniz'in Yeşilinde", "Dağların Arasında Bir Mola"),
        "happy" to listOf("Güzel Bir Gün", "Neşeli Anlar", "Dostlarla Bir Gün", "Hayatın Küçük Mutlulukları"),
        "warm" to listOf("Bir Bardak Çayın Hikâyesi", "Ocak Başında Çay Keyfi", "Demlikte Taze Çay", "Sofrada Paylaşılanlar"),
        "drive" to listOf("Yola Düştük", "Kıvrım Kıvrım Yollarda", "Yol Hikâyesi", "Rotamız Belli"),
        "k_adag" to listOf("Taşların Hafızası", "Eski Zamanlardan Kalanlar", "Tarihin İzinde"),
        "k_dram" to listOf("Fırtınadan Önce", "Sisli Bir Akşam", "Gecenin Sessizliği"),
        "k_bach" to listOf("Sessiz Bir Köşe", "Kitaplar Arasında", "İçeriden Bir Gün"),
    )
    fun basliklar(tur: String): List<String> = BASLIK[tur] ?: BASLIK.getValue("huzur")
}
