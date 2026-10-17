package com.eyeblink.app.host

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eyeblink.app.R
import com.eyeblink.app.ui.kit.EbFonts

/** Hands the bundled typefaces to the design kit, which stays free of Android resources. */
fun installFonts() {
    EbFonts.display = FontFamily(
        Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
        Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
    )
    EbFonts.sans = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
    )
    EbFonts.mono = FontFamily(Font(R.font.dm_mono_regular, FontWeight.Normal))
}
