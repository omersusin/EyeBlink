package com.eyeblink.core.privileged

/**
 * Everything that reaches a privileged shell passes through here. Values are quoted for `sh`,
 * and identifiers that Android restricts to a known alphabet are validated before use, so a
 * crafted app label or package name can never become a command.
 */
object ShellQuote {

    /** Single-quotes [value] for POSIX sh. A literal single quote becomes `'\''`. */
    fun arg(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val CLASS = Regex("^[A-Za-z0-9_.$]+$")
    private val PERMISSION = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val SETTING_KEY = Regex("^[a-z][a-z0-9_]*$")

    fun isPackage(s: String?): Boolean = s != null && s.length <= 255 && PACKAGE.matches(s)
    fun isPermission(s: String?): Boolean = s != null && s.length <= 255 && PERMISSION.matches(s)
    fun isSettingKey(s: String?): Boolean = s != null && s.length <= 128 && SETTING_KEY.matches(s)

    /** `com.pkg/com.pkg.Cls` or `com.pkg/.Cls`. */
    fun isComponent(s: String?): Boolean {
        if (s == null) return false
        val parts = s.split('/')
        return parts.size == 2 && isPackage(parts[0]) && parts[1].isNotEmpty() && CLASS.matches(parts[1])
    }
}
