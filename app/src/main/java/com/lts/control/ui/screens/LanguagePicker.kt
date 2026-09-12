package com.lts.control.ui.screens

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.lts.control.R

enum class AppLanguage(val tag: String?, @param:StringRes val labelRes: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german);

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales()
                .takeUnless { it.isEmpty }?.get(0)?.language
            return entries.firstOrNull { it.tag == tag } ?: SYSTEM
        }
    }
}

fun AppLanguage.applyToApp() = AppCompatDelegate.setApplicationLocales(
    tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
)

@Composable
internal fun LanguageRow() {
    var showDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(AppLanguage.current()) }

    RowSetting(
        title = stringResource(R.string.set_language),
        subtitle = stringResource(selected.labelRes),
        clickable = true,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.language_dialog_title)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        val choose = {
                            selected = lang
                            showDialog = false
                            lang.applyToApp()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = choose)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = lang == selected, onClick = null)
                            Text(stringResource(lang.labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
