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
    EbFonts.serif = FontFamily(f("instrument_serif_regular.ttf", FontWeight.Normal))
    EbFonts.serifItalic = FontFamily(f("instrument_serif_italic.ttf", FontWeight.Normal, FontStyle.Italic))
    EbFonts.sans = FontFamily(
        f("instrument_sans_regular.ttf", FontWeight.Normal), f("instrument_sans_medium.ttf", FontWeight.Medium),
        f("instrument_sans_semibold.ttf", FontWeight.SemiBold), f("instrument_sans_bold.ttf", FontWeight.Bold),
    )
    EbFonts.mono = FontFamily(f("dm_mono_regular.ttf", FontWeight.Normal), f("dm_mono_medium.ttf", FontWeight.Medium))
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
    shot("home") { EyeBlinkApp(Sample.state(), NoActions, en) }
    shot("home-tr") { EyeBlinkApp(Sample.state(Language.TR), NoActions, Strings.TR) }
    shot("home-off") { EyeBlinkApp(Sample.state(running = false, notReady = true), NoActions, en) }
    shot("rhythm") { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.RHYTHM) }
    shot("rhythm-tall", h = 3400) { EyeBlinkApp(Sample.state().let { it.copy(settings = it.settings.copy(advanced = true)) }, NoActions, en, initialTab = Tab.RHYTHM) }
    shot("calib-intro", h = 2000) { EyeBlinkApp(Sample.calibration(CalibrationStage.INTRO), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-video", h = 1700) { EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.VIDEO), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-read", h = 1700) { EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.READING), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-blink", h = 1700) { EyeBlinkApp(Sample.calibration(CalibrationStage.RUNNING, Phase.DELIBERATE), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("calib-result", h = 2300) { EyeBlinkApp(Sample.calibration(CalibrationStage.RESULT), NoActions, en, initialPage = Page.CALIBRATION) }
    shot("apps") { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.APPS) }
    shot("more") { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.MORE) }
    shot("studio") { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.BLINK) }
    shot("studio-tall", h = 3000) { EyeBlinkApp(Sample.state(), NoActions, en, initialTab = Tab.BLINK) }
    shot("night", h = 2500) { EyeBlinkApp(Sample.state().let { it.copy(settings = it.settings.copy(night = it.settings.night.copy(enabled = true))) }, NoActions, en, initialPage = Page.NIGHT) }
    shot("power", h = 4300) { EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.POWER) }
    shot("diag", h = 3300) { EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.DIAGNOSTICS) }
    shot("stats", h = 2900) { EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.STATS) }
    shot("privacy", h = 3600) { EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.PRIVACY) }
    shot("about", h = 2300) { EyeBlinkApp(Sample.state(), NoActions, en, initialPage = Page.ABOUT) }
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
    for (i in 0..4) shot("onboard-$i", h = 1560) { EyeBlinkApp(fresh, NoActions, en, onboardingStep = i) }
    shot("onboard-1-tr", h = 1560) { EyeBlinkApp(fresh.let { it.copy(settings = it.settings.copy(language = Language.TR)) }, NoActions, Strings.TR, onboardingStep = 1) }
    shot("gallery", h = 2200) { KitGallery() }
    println("ok")
}
