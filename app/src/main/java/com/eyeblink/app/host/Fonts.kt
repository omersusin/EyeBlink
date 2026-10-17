package com.eyeblink.app.host

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.eyeblink.app.R
import com.eyeblink.app.ui.kit.EbFonts

/** Hands the bundled typefaces to the design kit, which stays free of Android resources. */
fun installFonts() {
    EbFonts.serif = FontFamily(Font(R.font.instrument_serif_regular, FontWeight.Normal))
    EbFonts.serifItalic = FontFamily(Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic))
    EbFonts.sans = FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
        Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
        Font(R.font.instrument_sans_bold, FontWeight.Bold),
    )
    EbFonts.mono = FontFamily(
        Font(R.font.dm_mono_regular, FontWeight.Normal),
        Font(R.font.dm_mono_medium, FontWeight.Medium),
    )
}
