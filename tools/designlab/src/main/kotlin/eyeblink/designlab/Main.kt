package eyeblink.designlab

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import com.eyeblink.app.ui.EyeBlinkApp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.kit.EbFonts
import com.eyeblink.app.ui.kit.EbTheme
import com.eyeblink.app.ui.rest.RestScene
import com.eyeblink.core.model.ConfirmMode
import com.eyeblink.core.model.RestStyle
import com.eyeblink.core.rest.RestPhase
import com.eyeblink.core.rest.RestView
import com.eyeblink.app.ui.state.CalibrationStage
import com.eyeblink.app.ui.state.NoActions
import com.eyeblink.app.ui.state.Page
import com.eyeblink.core.calibration.CalibrationSession.Phase
import com.eyeblink.app.ui.state.Tab
import com.eyeblink.core.model.Language
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private val fontDir = File("app/src/main/res/font").let { if (it.exists()) it else File("../../app/src/main/res/font") }

fun loadFonts() {
    fun f(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(File(fontDir, name), weight, style)
    EbFonts.display = FontFamily(f("bricolage_grotesque_bold.ttf", FontWeight.Bold), f("bricolage_grotesque_extrabold.ttf", FontWeight.ExtraBold))
    EbFonts.sans = FontFamily(
        f("figtree_regular.ttf", FontWeight.Normal), f("figtree_medium.ttf", FontWeight.Medium),
        f("figtree_semibold.ttf", FontWeight.SemiBold), f("figtree_bold.ttf", FontWeight.Bold),
    )
    EbFonts.mono = FontFamily(f("dm_mono_regular.ttf", FontWeight.Normal))
}

fun main(args: Array<String>) {
    loadFonts()
    val out = File(args.firstOrNull() ?: "build/shots").apply { mkdirs() }
    val only = args.getOrNull(1)
    if (only == "tour") { renderTour(out); return }

    fun shot(name: String, timeMs: Long = 1_800, w: Int = 720, h: Int = 1560, content: @Composable () -> Unit) {
        if (only != null && only != name) return
        val scene = ImageComposeScene(width = w, height = h, density = Density(2f)) { content() }
        // The UI clocks read the frame time directly, so jumping straight to the instant is enough.
        scene.render(0L)
        if (timeMs > 64) scene.render((timeMs - 32) * 1_000_000L)
        val image = scene.render(timeMs * 1_000_000L)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }

    val en = Strings.EN
    // Every screen is rendered twice: bright Daylight and deep-indigo Moonlit ("-dark").
    fun both(name: String, timeMs: Long = 1_800, h: Int = 1560, content: @Composable (dark: Boolean) -> Unit) {
        shot(name, timeMs, h = h) { content(false) }
        shot("$name-dark", timeMs, h = h) { content(true) }
    }
    both("home") { d -> EyeBlinkApp(Sample.state(), NoActions, en, systemDark = d) }
    shot("home-tr") { EyeBlinkApp(Sample.state(Language.TR), NoActions, Strings.TR) }
    both("home-off") { d -> EyeBlinkApp(Sample.state(running = false, notReady = true), NoActions, en, systemDark = d) }
    both("rhythm") { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.RHYTHM, systemDark = d) }
    shot("rhythm-tall", h = 4000) { EyeBlinkApp(Sample.state().let { it.copy(settings = it.settings.copy(advanced = true)) }, NoActions, en, initialTab = Tab.RHYTHM) }
    both("calib-intro", h = 2000) { d -> EyeBlinkApp(Sample.calibration(CalibrationStage.INTRO), NoActions, en, initialPage = Page.CALIBRATION, systemDark = d) }
    shot("calib-video", h = 1700) { EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.VIDEO), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-read", h = 1700) { EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.READING), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-blink", h = 1900) { EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.DELIBERATE), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-blink-close-more", h = 1900) {
        EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.DELIBERATE, com.eyeblink.core.calibration.CalibrationSession.Hint.CLOSE_MORE), NoActions, en, initialPage = Page.CALIBRATION)
    }
    shot("calib-blink-eased-tr", h = 1900) {
        EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.DELIBERATE, com.eyeblink.core.calibration.CalibrationSession.Hint.EASED), NoActions, Strings.TR, initialPage = Page.CALIBRATION)
    }
    shot("eyetest-intro", h = 1500) { EyeBlinkApp(Sample.eyeTest(running = false), NoActions, en, initialPage = Page.EYE_TEST) }
    shot("eyetest-live", h = 2300) { EyeBlinkApp(Sample.eyeTest(running = true), NoActions, en, initialPage = Page.EYE_TEST) }
    shot("eyetest-live-tr", h = 2300) { EyeBlinkApp(Sample.eyeTest(running = true), NoActions, Strings.TR, initialPage = Page.EYE_TEST) }
    shot("calib-result", h = 2300) { EyeBlinkApp(Sample.calibration(CalibrationStage.RESULT), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-error", h = 2300) {
        val broken = Sample.calibration(CalibrationStage.INTRO)
        val why = "the face model could not be loaded (java.lang.IllegalStateException: Failed to open the model asset)"
        EyeBlinkApp(broken.copy(calibration = broken.calibration.copy(error = why)), NoActions, en, initialPage = Page.CALIBRATION)
    }
    shot("calib-denied", h = 2300) {
        val refused = Sample.calibration(CalibrationStage.INTRO)
        EyeBlinkApp(refused.copy(calibration = refused.calibration.copy(hasCameraPermission = false, cameraDenied = true)), NoActions, en, initialPage = Page.CALIBRATION)
    }
    shot("calib-denied-tr", h = 2300) {
        val refused = Sample.calibration(CalibrationStage.INTRO)
        EyeBlinkApp(refused.copy(calibration = refused.calibration.copy(hasCameraPermission = false, cameraDenied = true)), NoActions, Strings.TR, initialPage = Page.CALIBRATION)
    }
    both("apps") { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.APPS, systemDark = d) }
    both("more") { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.MORE, systemDark = d) }
    shot("more-tall", h = 2300) { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.MORE) }
    both("language", timeMs = 2_400) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.MORE, initialSheet = com.eyeblink.app.ui.Sheet.LANGUAGE, systemDark = d) }
    both("picker", timeMs = 2_400) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.APPS, initialSheet = com.eyeblink.app.ui.Sheet.APPS, systemDark = d) }
    both("studio") { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.BLINK, systemDark = d) }
    shot("studio-tall", h = 3000) { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.BLINK) }
    both("night", h = 2500) { d -> EyeBlinkApp(Sample.state().let { it.copy(settings = it.settings.copy(night = it.settings.night.copy(enabled = true))) }, NoActions, en, initialPage = Page.NIGHT, systemDark = d) }
    both("power", h = 4300) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.POWER, systemDark = d) }
    both("diag", h = 3300) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.DIAGNOSTICS, systemDark = d) }
    both("stats", h = 2900) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.STATS, systemDark = d) }
    both("privacy", h = 3600) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.PRIVACY, systemDark = d) }
    both("about", h = 2300) { d -> EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.ABOUT, systemDark = d) }
    fun rest(style: RestStyle, view: RestView, tr: Boolean = false) = @Composable {
        EbTheme(if (tr) Strings.TR else en) { RestScene(view, style, onSkip = {}, onTap = {}) }
    }
    val resting = RestView(RestPhase.RESTING, 0.42f, 11_600, null, null, null)
    val waiting = RestView(RestPhase.AWAITING_CONFIRM, 1f, 0, ConfirmMode.BLINK, null, null)
    shot("rest-focus", timeMs = 2_400, content = rest(RestStyle.DISTANCE_FOCUS, resting))
    shot("rest-focus-tr", timeMs = 2_400, content = rest(RestStyle.DISTANCE_FOCUS, resting, tr = true))
    shot("rest-focus-done", timeMs = 2_400, content = rest(RestStyle.DISTANCE_FOCUS, waiting))
    shot("rest-black", timeMs = 2_400, content = rest(RestStyle.BLACKOUT, resting))
    shot("rest-black-done", timeMs = 2_400, content = rest(RestStyle.BLACKOUT, waiting))
    val fresh = Sample.state().let { it.copy(settings = it.settings.copy(onboardingDone = false, apps = emptyList()), perms = com.eyeblink.app.ui.state.PermissionUi()) }
    for (i in 0..4) both("onboard-$i") { d -> EyeBlinkApp(fresh, NoActions, en, onboardingStep = i, systemDark = d) }
    shot("onboard-1-tr", h = 1560) { EyeBlinkApp(fresh.let { it.copy(settings = it.settings.copy(language = Language.TR)) }, NoActions, Strings.TR, onboardingStep = 1) }
    shot("gallery", h = 2400) { KitGallery() }
    shot("gallery-dark", h = 2400) { EbTheme(en, dark = true) { KitGallery() } }
    println("ok")
}
