package com.eyeblink.app.ui.screens

import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.core.engine.BreakOutcome
import com.eyeblink.core.engine.BreakReport
import com.eyeblink.core.engine.PauseSummary
import com.eyeblink.core.engine.ResumeSummary

/**
 * What a finished break did to the video, in a sentence a person can act on, or "" when there is nothing to say.
 * [pausingOn] is whether pausing was switched on for the app the break was for.
 */
internal fun pauseExplanation(br: BreakReport, s: Strings, pausingOn: Boolean): String {
    if (br.outcome == BreakOutcome.ABORTED) return ""
    return when (br.pause) {
        PauseSummary.PAUSED -> when (br.resume) {
            ResumeSummary.RESUMED -> s.t("Video paused, verified, and resumed where it was.", "Video duraklatıldı, doğrulandı ve kaldığı yerden devam etti.")
            ResumeSummary.ALREADY_PLAYING -> s.t("Video paused for the break; you had already started it again, so it was left alone.", "Video mola için duraklatıldı; sen zaten yeniden başlatmıştın, bu yüzden dokunulmadı.")
            ResumeSummary.REFUSED -> s.t("Paused, then left alone because you moved on.", "Duraklatıldı; sen devam ettiğin için dokunulmadı.")
            ResumeSummary.FAILED -> s.t("Paused, but it would not start again by itself. Press play.", "Duraklatıldı ama kendiliğinden yeniden başlamadı. Oynat'a bas.")
            ResumeSummary.NOT_ATTEMPTED -> s.t("Paused. Resuming is switched off, so press play when you are back.", "Duraklatıldı. Devam ettirme kapalı; döndüğünde Oynat'a bas.")
        }
        PauseSummary.UNVERIFIED -> when (br.resume) {
            ResumeSummary.RESUMED -> s.t("The pause took a while to show up, but it landed, and the video was resumed.", "Duraklatma geç görüldü ama gerçekleşti; video devam ettirildi.")
            else -> s.t("The pause could not be verified, so it is not counted as working.", "Duraklatma doğrulanamadı; bu yüzden çalışıyor sayılmadı.")
        }
        PauseSummary.NOT_PLAYING -> s.t("Nothing was playing, so nothing was touched.", "Oynatılan bir şey yoktu, hiçbir şeye dokunulmadı.")
        PauseSummary.NO_SESSION -> s.t(
            "This app shows no media session, so EyeBlink cannot see or pause what it plays. Nothing was touched.",
            "Bu uygulama medya oturumu göstermiyor; EyeBlink neyin oynadığını göremez ve duraklatamaz. Hiçbir şeye dokunulmadı.",
        )
        PauseSummary.UNREADABLE -> s.t(
            "EyeBlink could not read the player (the Root/Shizuku shell did not answer), so nothing was touched.",
            "EyeBlink oynatıcıyı okuyamadı (Root/Shizuku kabuğu yanıt vermedi), bu yüzden hiçbir şeye dokunulmadı.",
        )
        PauseSummary.FAILED -> s.t("The pause command could not be delivered, so the video kept playing.", "Duraklatma komutu iletilemedi; video oynamaya devam etti.")
        PauseSummary.NOT_ATTEMPTED -> when {
            !pausingOn -> s.t("Pausing is switched off for this app, so the video kept playing underneath.", "Bu uygulama için duraklatma kapalı; video altta oynamaya devam etti.")
            else -> s.t(
                "EyeBlink has no way to pause this app's video yet. Allow notification access (More → Diagnostics → Permissions) or use Shizuku/Root (More → Power modes).",
                "EyeBlink'in bu uygulamanın videosunu duraklatacak bir yolu henüz yok. Bildirim erişimine izin ver (Diğer → Tanılama → İzinler) ya da Shizuku/Root kullan (Diğer → Güç modları).",
            )
        }
    }
}
