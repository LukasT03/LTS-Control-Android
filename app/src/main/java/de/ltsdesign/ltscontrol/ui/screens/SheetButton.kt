package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground
import de.ltsdesign.ltscontrol.ui.theme.blueTonalButtonColors

/** The main button at the bottom of a sheet, the same in every sheet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SheetButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = blueTonalButtonColors(SettingsBackground),
        shapes = ButtonDefaults.shapesFor(buttonHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp)
            .heightIn(min = buttonHeight),
    ) {
        Text(text, style = ButtonDefaults.textStyleFor(buttonHeight))
    }
}
