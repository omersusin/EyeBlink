package eyeblink.designlab

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.eyeblink.app.ui.EyeBlinkApp
import com.eyeblink.app.ui.i18n.Strings
import com.eyeblink.app.ui.state.NoActions
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Records a walk through the app: tap each tab of the dock in turn and write every frame of the page
 * transitions (the lids closing over one screen and opening on the next) as numbered PNGs. A script
 * turns them into the animated preview in the README. The taps are real pointer events sent to the real
 * UI, so this is the app's own motion, not a mock-up.
 */
fun renderTour(out: File) {
    val width = 720
    val height = 1560
    val density = 2f
    val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
        EyeBlinkApp(Sample.state(), NoActions, Strings.EN)
    }
    out.mkdirs()
    out.listFiles { f -> f.name.startsWith("tour-") }?.forEach { it.delete() }

    var clockMs = 0L
    var index = 0
    fun capture(holdMs: Long) {
        val image = scene.render(clockMs * 1_000_000L)
        File(out, "tour-%04d-%d.png".format(index++, holdMs)).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    fun advance(ms: Long, frameMs: Long = 33, record: Boolean = false) {
        var elapsed = 0L
        while (elapsed < ms) {
            clockMs += frameMs
            elapsed += frameMs
            if (record) capture(frameMs) else scene.render(clockMs * 1_000_000L)
        }
    }

    // The dock: 18 dp of margin each side, five equal items, 14 dp above the bottom edge.
    fun dockPoint(i: Int): Offset {
        val itemDp = (360f - 36f) / 5f
        return Offset((18f + itemDp * (i + 0.5f)) * density, (780f - 14f - 35f) * density)
    }
    fun tap(i: Int) {
        val p = dockPoint(i)
        scene.sendPointerEvent(PointerEventType.Press, p)
        advance(66)
        scene.sendPointerEvent(PointerEventType.Release, p)
    }

    advance(2_000)                       // let the first screen arrive
    capture(1_400)
    for (tab in listOf(1, 2, 3, 4, 0)) {
        tap(tab)
        advance(700, record = true)      // the blink transition and the dock seam sliding over
        advance(1_300)                   // settle (reveal animation finishes)
        capture(1_300)
    }
    scene.close()
    println("tour frames: $index")
}
