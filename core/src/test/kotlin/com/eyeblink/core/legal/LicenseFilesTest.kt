package com.eyeblink.core.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The licence is a promise to the people who use the app and to the author. These tests keep the files that make it
 * honest from drifting apart: the text, the notice that has to travel with it, and the copies that ship inside the APK.
 */
class LicenseFilesTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "LICENSE").isFile && File(it, "app").isDirectory }

    private fun text(path: String) = File(root, path).also { assertTrue("$path is missing", it.isFile) }.readText()

    @Test
    fun `the licence is the PolyForm Noncommercial text, whole`() {
        val license = text("LICENSE")
        assertTrue(license.startsWith("# PolyForm Noncommercial License 1.0.0"))
        for (clause in listOf(
            "## Noncommercial Purposes", "Any noncommercial purpose is a permitted purpose.",
            "## Patent License", "## Violations", "## No Liability", "## Definitions", "**Your licenses**",
            "In order to get any license under these terms, you must agree to them as both strict obligations and conditions to all your licenses.",
        )) assertTrue("LICENSE lacks: $clause", license.contains(clause))
        assertTrue("the licence must not have been softened to allow commercial use", !license.contains("Commercial Purposes"))
    }

    @Test
    fun `the notice that must travel with the software names the author`() {
        val notice = text("NOTICE")
        val required = notice.lines().filter { it.startsWith("Required Notice:") }
        assertEquals(1, required.size)
        assertTrue(required.single().contains("omersusin"))
        assertTrue(text("LICENSE").contains("`Required Notice:`"))
    }

    @Test
    fun `third party licences are shipped with the notice that lists them`() {
        val notice = text("NOTICE")
        assertTrue(text("licenses/Apache-2.0.txt").contains("Apache License"))
        assertTrue(text("licenses/MIT-Shizuku-API.txt").startsWith("MIT License"))
        for (name in listOf("MediaPipe", "Shizuku", "Bricolage Grotesque", "Figtree", "DM Mono", "CameraX", "Compose", "licenses/Apache-2.0.txt", "licenses/MIT-Shizuku-API.txt")) {
            assertTrue("NOTICE does not mention $name", notice.contains(name))
        }
        assertTrue("Shizuku API is MIT, not Apache", Regex("Shizuku API[^\\n]*\\(MIT\\)").containsMatchIn(text("README.md")))
    }

    @Test
    fun `the copies inside the app are the same files`() {
        assertEquals(text("LICENSE"), text("app/src/main/assets/LICENSE.txt"))
        assertEquals(text("NOTICE"), text("app/src/main/assets/NOTICE.txt"))
        assertEquals(text("licenses/Apache-2.0.txt"), text("app/src/main/assets/Apache-2.0.txt"))
        assertEquals(text("licenses/MIT-Shizuku-API.txt"), text("app/src/main/assets/MIT-Shizuku-API.txt"))
    }

    @Test
    fun `the readme says what the licence allows and does not claim there is none`() {
        val readme = text("README.md")
        assertTrue(readme.contains("PolyForm Noncommercial License 1.0.0"))
        assertTrue(readme.contains("## License"))
        assertTrue("the old 'no license yet' note must be gone", !readme.contains("no license file yet"))
    }
}
