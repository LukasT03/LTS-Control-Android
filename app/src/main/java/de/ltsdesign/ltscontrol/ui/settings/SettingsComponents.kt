package de.ltsdesign.ltscontrol.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ltsdesign.ltscontrol.R

// Measured from the Settings app on Android 17 (SettingsLib "expressive" style).
private val GroupMargin = 16.dp
private val ItemPadding = 16.dp
private val ItemGap = 2.dp
private val OuterRadius = 20.dp
private val InnerRadius = 4.dp
private val ItemMinHeight = 72.dp
private val BannerRadius = 28.dp
// Status cards sit a bit further apart than group items, with slightly rounder inner corners.
private val BannerGap = 4.dp
private val BannerInnerRadius = 8.dp
private val DisabledAlpha = 0.38f

// Text sizes of the system Settings: titles 16 sp, summaries and footers 14 sp, page title 22 sp.
/** Item titles like "Completion Sound", also used for the status texts on the control tab. */
val SettingsTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
private val TitleStyle: TextStyle
    @Composable get() = SettingsTitleStyle
private val SummaryStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium

/** Page background behind the setting groups. */
val SettingsBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

/** Background of a single setting item. */
val SettingsItemColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceBright

// region Page

/**
 * A settings page: a top bar with the title next to a round back button, like the system
 * Settings app, and a list of groups. Without [onBack] it's a top-level page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    title: String,
    onBack: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        containerColor = SettingsBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        modifier = Modifier.padding(start = if (onBack == null) 8.dp else 12.dp),
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.padding(start = 20.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SettingsBackground,
                    scrolledContainerColor = SettingsBackground,
                ),
                windowInsets = WindowInsets(0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            content = content,
        )
    }
}

/** Intro text at the top of a page, like on the "Dark theme" page. */
fun LazyListScope.settingsIntro(@StringRes text: Int) {
    item {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        )
    }
}

/** A group of items with an optional header and footer. */
fun LazyListScope.settingsGroup(
    @StringRes header: Int? = null,
    @StringRes footer: Int? = null,
    content: SettingsGroupScope.() -> Unit,
) {
    settingsGroup(headerText = null, header = header, footer = footer, content = content)
}

/** A group whose header is a plain text instead of a string resource, e.g. a version number. */
fun LazyListScope.settingsGroup(
    headerText: String?,
    @StringRes header: Int? = null,
    @StringRes footer: Int? = null,
    content: SettingsGroupScope.() -> Unit,
) {
    val items = SettingsGroupScope().apply(content).items
    if (items.isEmpty()) return
    item {
        if (headerText != null) {
            SettingsHeader(headerText)
        } else if (header != null) {
            SettingsHeader(stringResource(header))
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
    items.forEachIndexed { index, itemContent ->
        item {
            SettingsItemSurface(isFirst = index == 0, isLast = index == items.lastIndex) { itemContent() }
        }
    }
    if (footer != null) settingsFooter(footer)
}

/** Explanation below the groups, with the info icon of the system Settings. Paragraphs are separated by a blank line. */
fun LazyListScope.settingsFooter(@StringRes vararg paragraphs: Int) {
    item { SettingsFooter(paragraphs.map { stringResource(it) }.joinToString("\n\n")) }
}

class SettingsGroupScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
    )
}

/** Note below a group, with the info icon of the system Settings. */
@Composable
fun SettingsFooter(text: String) {
    Column(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = SummaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Items of a group touch with a small gap; only the group's outer corners are fully rounded. */
@Composable
private fun SettingsItemSurface(isFirst: Boolean, isLast: Boolean, content: @Composable () -> Unit) {
    val top = if (isFirst) OuterRadius else InnerRadius
    val bottom = if (isLast) OuterRadius else InnerRadius
    Surface(
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = SettingsItemColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GroupMargin)
            .padding(bottom = if (isLast) 0.dp else ItemGap),
        content = content,
    )
}

/** A full-width highlighted card, like the main switch at the top of a system settings page. */
class Banner(
    @StringRes val title: Int,
    val summary: String? = null,
    val icon: ImageVector? = null,
    val iconTint: Color? = null,
)

/**
 * Full-width highlighted cards, like the main switch at the top of a system settings page.
 * Several banners share the row side by side with equal height and touch like the items of a
 * group: a small gap and small corners where they meet.
 */
fun LazyListScope.settingsBanner(vararg banners: Banner) {
    item {
        BannerGrid(
            banners.toList(),
            columns = banners.size,
            modifier = Modifier.padding(horizontal = GroupMargin).padding(top = 8.dp),
        )
    }
}

/**
 * Status cards in rows of [columns], touching like the items of a group: a small gap and small
 * corners where they meet, the large radius only on the outer corners of the whole grid.
 */
@Composable
fun BannerGrid(banners: List<Banner>, columns: Int, modifier: Modifier = Modifier) {
    val rows = banners.chunked(columns)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BannerGap)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BannerGap),
            ) {
                row.forEachIndexed { columnIndex, banner ->
                    fun corner(isOuterRow: Boolean, isOuterColumn: Boolean) =
                        if (isOuterRow && isOuterColumn) BannerRadius else BannerInnerRadius
                    val top = rowIndex == 0
                    val bottom = rowIndex == rows.lastIndex
                    val start = columnIndex == 0
                    val end = columnIndex == row.lastIndex
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = corner(top, start),
                            topEnd = corner(top, end),
                            bottomStart = corner(bottom, start),
                            bottomEnd = corner(bottom, end),
                        ),
                        // The track color of the progress bar, so the cards match the control tab.
                        color = ProgressIndicatorDefaults.linearTrackColor,
                        contentColor = contentColorFor(ProgressIndicatorDefaults.linearTrackColor),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        ItemRow(
                            title = stringResource(banner.title),
                            summary = banner.summary,
                            icon = banner.icon,
                            iconTint = banner.iconTint,
                            summaryColor = LocalContentColor.current.copy(alpha = 0.8f),
                            titleStyle = TitleStyle.copy(fontSize = 18.sp, lineHeight = 24.sp),
                        )
                    }
                }
            }
        }
    }
}

class CardAction(
    @StringRes val title: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Tappable cards side by side, touching like the items of a group. */
@Composable
fun ActionCards(actions: List<CardAction>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        actions.forEachIndexed { index, action ->
            val start = if (index == 0) BannerRadius else InnerRadius
            val end = if (index == actions.lastIndex) BannerRadius else InnerRadius
            Surface(
                onClick = action.onClick,
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = SettingsItemColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                ItemRow(
                    title = stringResource(action.title),
                    icon = action.icon,
                    iconTint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** A single card outside of a list, with the look of a one-item group. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(OuterRadius),
        color = SettingsItemColor,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

// endregion

// region Items

@Composable
private fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    styledSummary: AnnotatedString? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    enabled: Boolean = true,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleStyle: TextStyle = TitleStyle,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ItemMinHeight)
            .padding(horizontal = ItemPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentAlpha = if (enabled) 1f else DisabledAlpha
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint ?: LocalContentColor.current,
                modifier = Modifier.size(24.dp).alpha(contentAlpha),
            )
            Spacer(Modifier.width(ItemPadding))
        }
        if (leading != null) {
            // The radio buttons of the system Settings sit in a 40 dp column, the text starts 12 dp after it.
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
            Text(title, style = titleStyle)
            if (styledSummary != null) {
                Text(styledSummary, style = SummaryStyle, color = summaryColor)
            } else if (summary != null) {
                Text(summary, style = SummaryStyle, color = summaryColor)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(ItemPadding))
            trailing()
        }
    }
}

/** A plain item, optionally opening something on tap. */
@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    ItemRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
        trailing = trailing,
    )
}

/** An item whose summary has styled parts, e.g. bold words that show what a tap does. */
@Composable
fun SettingsItem(
    title: String,
    summary: AnnotatedString,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    ItemRow(
        title = title,
        styledSummary = summary,
        icon = icon,
        enabled = enabled,
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    )
}

@Composable
fun SwitchItem(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ItemRow(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = {
                Icon(
                    if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }
}

/**
 * Shows the chosen option as summary and opens a dialog with all options, like a ListPreference.
 * [description] explains the setting above the options in the dialog.
 */
@Composable
fun <T> ChoiceItem(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    description: String? = null,
    placeholder: String? = null,
    emptyText: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    SettingsItem(
        title = title,
        summary = options.firstOrNull { it.first == selected }?.second ?: placeholder,
        enabled = enabled,
        onClick = { showDialog = true },
        trailing = trailing,
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (options.isEmpty() && emptyText != null) {
                        Text(emptyText, style = MaterialTheme.typography.bodyMedium)
                    }
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = value == selected, role = Role.RadioButton) {
                                    onSelect(value)
                                    showDialog = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == selected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                val isHint = options.isEmpty() && emptyText != null
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(if (isHint) R.string.ok else R.string.cancel))
                }
            },
        )
    }
}

/** Radio button on the left, like the options on the "Dark theme" page. */
@Composable
fun RadioItem(
    title: String,
    selected: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ItemRow(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        leading = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    )
}

/**
 * Title with the value on the right and a slider below, like the volume sliders. The value is
 * only reported when the slider is released.
 */
@Composable
fun SliderItem(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean = true,
    valueText: (Int) -> String = { "$it %" },
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!isDragging) sliderValue = value.toFloat() }

    val shownValue = (Math.round(sliderValue / step) * step).coerceIn(range)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ItemMinHeight)
            .padding(start = ItemPadding, end = ItemPadding, top = 14.dp, bottom = 6.dp)
            .alpha(if (enabled) 1f else DisabledAlpha),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TitleStyle, modifier = Modifier.weight(1f))
            Text(valueText(shownValue), style = SummaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                sliderValue = shownValue.toFloat()
                if (shownValue != value) onValueChange(shownValue)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = enabled,
        )
    }
}

/** Value with minus and plus buttons, for values that need exact steps. */
@Composable
fun StepperItem(
    title: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    ItemRow(title = valueText.let { "$title: $it" }, summary = summary, enabled = enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onDecrease, enabled = enabled && canDecrease) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.decrease, title))
            }
            FilledTonalIconButton(onClick = onIncrease, enabled = enabled && canIncrease) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.increase, title))
            }
        }
    }
}

/** Two or three exclusive options side by side. */
@Composable
fun <T> SegmentedItem(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    Box(Modifier.fillMaxWidth().heightIn(min = ItemMinHeight).padding(ItemPadding), contentAlignment = Alignment.Center) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
    }
}

/** Free content inside an item, e.g. buttons or text fields. */
@Composable
fun SettingsCustomItem(
    topPadding: Dp = ItemPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = ItemPadding, end = ItemPadding, top = topPadding, bottom = ItemPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

// endregion
