package androidx.media3.transformer

/** Stüdyo (3.23): CompositionPlayer'da video oluşturucu "ön ısıtma" (prewarming) kapatılır.
 *  Cihaz kaydı 14.11 12:35: tek dizide resim ↔ video (geçiş parçası) değişirken Media3 1.8.1, ön ısıtılan video
 *  oluşturucusunu kapatırken serbest bırakılmış çözücüye flush() çağırıyor → IllegalStateException → uygulama çöküyordu
 *  (RendererHolder.disablePrewarming → MediaCodecRenderer.onDisabled). Ayar Media3'te paket içi olduğu için bu dosya
 *  aynı pakettedir. */
object StudyoOynatici {
    fun isinmasiz(b: CompositionPlayer.Builder): CompositionPlayer.Builder = b.setVideoPrewarmingEnabled(false)
}
