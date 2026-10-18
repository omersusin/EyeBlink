package com.eyeblink.core.privileged

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Exercises the real framing protocol against a real `/bin/sh`. Real time, real processes:
 * this is the one place the privileged plumbing can be proven without a device.
 */
class ShellSessionTest {

    private lateinit var session: ShellSession

    @Before
    fun setUp() {
        assumeTrue("needs /bin/sh", File("/bin/sh").exists())
        session = ShellSession(ProcessShellLauncher(listOf("/bin/sh")))
    }

    @After
    fun tearDown() {
        if (::session.isInitialized) session.close()
    }

    private fun run(cmd: String, timeout: Long = 5_000) = runBlocking { session.run(cmd, timeout) }

    @Test
    fun `output and a zero exit code come back`() {
        val r = run("echo hello")
        assertTrue(r.ok)
        assertEquals("hello", r.output)
    }

    @Test
    fun `non zero exit codes are reported`() {
        assertEquals(1, run("false").exitCode)
        assertEquals(42, run("(exit 42)").exitCode)
    }

    @Test
    fun `a command that calls exit does not kill the session`() {
        assertEquals(7, run("exit 7").exitCode)
        val r = run("echo still alive")
        assertEquals("still alive", r.output)
        assertTrue(session.isOpen)
    }

    @Test
    fun `stderr is merged into the output`() {
        val r = run("echo out; echo err 1>&2")
        assertEquals("out\nerr", r.output)
    }

    @Test
    fun `output without a trailing newline is kept intact`() {
        assertEquals("abc", run("printf abc").output)
        assertEquals("", run("true").output)
    }

    @Test
    fun `multi line output is preserved exactly`() {
        assertEquals("a\nb\n\nc", run("printf 'a\\nb\\n\\nc'").output)
    }

    @Test
    fun `a command cannot steal the next command by reading stdin`() {
        val r = run("cat")
        assertTrue(r.ok)
        assertEquals("", r.output)
        assertEquals("next", run("echo next").output)
    }

    @Test
    fun `text that looks like a marker does not confuse the framing`() {
        val r = run("echo '__EB_0000000000000000_0__'; echo after")
        assertEquals("__EB_0000000000000000_0__\nafter", r.output)
        assertEquals(0, r.exitCode)
    }

    @Test
    fun `commands are independent - nothing leaks between them`() {
        run("cd /tmp; FOO=bar")
        val r = run("pwd; echo \"foo=\$FOO\"")
        assertFalse(r.output.startsWith("/tmp"))
        assertTrue(r.output.endsWith("foo="))
    }

    @Test
    fun `many concurrent callers each get their own answer`() {
        val results = runBlocking {
            (1..25).map { i -> async { i to session.run("echo value-$i", 8_000) } }.awaitAll()
        }
        for ((i, r) in results) assertEquals("value-$i", r.output)
    }

    @Test
    fun `a hanging command times out and the session recovers`() {
        val r = run("sleep 10", timeout = 400)
        assertTrue(r.timedOut)
        assertFalse(r.ok)
        val next = run("echo recovered")
        assertEquals("recovered", next.output)
    }

    @Test
    fun `a shell that dies mid command is reported and replaced`() {
        val r = run("kill -9 \$\$")
        assertTrue(r.failedToRun || r.timedOut)
        assertEquals("fresh", run("echo fresh").output)
    }

    @Test
    fun `large output is returned whole`() {
        val r = run("head -c 300000 /dev/zero | tr '\\0' 'x'")
        assertEquals(300_000, r.output.length)
    }

    @Test
    fun `unicode survives the round trip`() {
        assertEquals("göz kırpma 👁", run("printf '%s' ${ShellQuote.arg("göz kırpma 👁")}").output)
    }

    @Test
    fun `a launcher that fails yields a failure result not an exception`() {
        val broken = ShellSession(ShellLauncher { error("su: not found") })
        val r = runBlocking { broken.run("id", 1_000) }
        assertTrue(r.failedToRun)
        assertFalse(r.ok)
        assertTrue(r.output.contains("su: not found"))
    }

    @Test
    fun `closing stops the process and a later run starts a new one`() {
        run("true")
        session.close()
        assertFalse(session.isOpen)
        assertEquals("again", run("echo again").output)
    }

    @Test
    fun `every quoted argument survives sh unchanged`() {
        val nasty = listOf(
            "plain", "with space", "it's", "a'b'c", "\$(echo pwned)", "`echo pwned`", "x; echo pwned",
            "line1\nline2", "back\\slash", "\"dq\"", "*", "~", "--flag", "", "ÇŞĞİÖÜ",
        )
        for (s in nasty) {
            val r = run("printf '%s' ${ShellQuote.arg(s)}")
            assertEquals("quoting failed for [$s]", s, r.output)
        }
    }
}
