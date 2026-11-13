package de.ltsdesign.ltscontrol.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.ltsdesign.ltscontrol.R
import de.ltsdesign.ltscontrol.bluetooth.BleManager
import de.ltsdesign.ltscontrol.bluetooth.BoardVariant
import de.ltsdesign.ltscontrol.ui.settings.SettingsBackground
import de.ltsdesign.ltscontrol.ui.settings.SettingsCard
import de.ltsdesign.ltscontrol.ui.settings.SettingsFooter
import kotlinx.coroutines.launch

@Composable
internal fun variantLabel(variant: BoardVariant): String =
    stringResource(if (variant == BoardVariant.Pro) R.string.respooler_pro else R.string.respooler_v4)

/**
 * Asks for the variant of a connected board that has none stored, like the sheet on iOS.
 * Unlike the settings page it needs an explicit "Save"; swiped away it asks again on the next connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantSheet(bleManager: BleManager, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<BoardVariant?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SettingsBackground,
    ) {
        // Laid out like a settings page: cards 16 dp from the edge, the title and note a bit further in.
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.select_variant),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            SettingsCard(Modifier.padding(horizontal = 16.dp)) {
                VariantOptions(selected) { selected = it }
            }
            SettingsFooter(stringResource(R.string.variant_note_initial))
            Spacer(Modifier.height(16.dp))
            SheetButton(stringResource(R.string.save), enabled = selected != null) {
                val variant = selected ?: return@SheetButton
                // Saved once the sheet is down, otherwise it would vanish without its animation.
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    bleManager.setBoardVariant(variant)
                    onDismiss()
                }
            }
        }
    }
}

/**
 * Standard and Pro side by side, for the settings page and the sheet. Each option can be tapped
 * up to the card's edge: the item's 16 dp inset lies inside the tap areas, and 6 dp on each side
 * make up the 12 dp gap between them.
 */
@Composable
internal fun VariantOptions(selected: BoardVariant?, onSelect: (BoardVariant) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        for ((variant, image) in listOf(BoardVariant.Standard to R.drawable.respooler, BoardVariant.Pro to R.drawable.respooler_pro)) {
            val isFirst = variant == BoardVariant.Standard
            VariantOption(
                image = image,
                label = variantLabel(variant),
                selected = selected == variant,
                contentPadding = PaddingValues(
                    start = if (isFirst) 16.dp else 6.dp,
                    end = if (isFirst) 6.dp else 16.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { onSelect(variant) }
        }
    }
}

@Composable
private fun VariantOption(
    image: Int,
    label: String,
    selected: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(image), label, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Fit)
        Text(label, style = MaterialTheme.typography.titleMedium)
        RadioButton(selected = selected, onClick = null)
    }
}
