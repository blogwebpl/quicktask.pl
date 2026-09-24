package pl.quicktask.app.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.LogCategory
import pl.quicktask.app.common.LogEntry
import pl.quicktask.app.common.LogLevel
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.main.platform.BackHandler
import pl.quicktask.app.platform.BuildInfo
import pl.quicktask.app.platform.getBuildInfo
import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.ui.theme.ThemeManager
import pl.quicktask.app.ui.theme.ThemeMode
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.about_app_section_title
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.app_build_number_label
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.app_version_label
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.ic_settings
import todo.shared.generated.resources.ic_sun
import todo.shared.generated.resources.logs_clear_button
import todo.shared.generated.resources.logs_count
import todo.shared.generated.resources.logs_disabled_label
import todo.shared.generated.resources.logs_empty
import todo.shared.generated.resources.logs_enabled_label
import todo.shared.generated.resources.logs_filter_all
import todo.shared.generated.resources.logs_filter_api
import todo.shared.generated.resources.logs_filter_func
import todo.shared.generated.resources.logs_filter_refresh
import todo.shared.generated.resources.logs_filter_state
import todo.shared.generated.resources.logs_search_hint
import todo.shared.generated.resources.logs_section_title
import todo.shared.generated.resources.save_settings_button
import todo.shared.generated.resources.search_timezone_placeholder
import todo.shared.generated.resources.select_timezone_title
import todo.shared.generated.resources.settings
import todo.shared.generated.resources.settings_category_about_subtitle_format
import todo.shared.generated.resources.settings_category_about_title
import todo.shared.generated.resources.settings_category_account_subtitle_format
import todo.shared.generated.resources.settings_category_account_title
import todo.shared.generated.resources.settings_category_logs_subtitle_disabled
import todo.shared.generated.resources.settings_category_logs_subtitle_enabled
import todo.shared.generated.resources.settings_category_logs_title
import todo.shared.generated.resources.settings_category_theme_subtitle_format
import todo.shared.generated.resources.settings_category_theme_title
import todo.shared.generated.resources.settings_saved_message
import todo.shared.generated.resources.settings_saving
import todo.shared.generated.resources.theme_mode_dark
import todo.shared.generated.resources.theme_mode_light
import todo.shared.generated.resources.theme_mode_system
import todo.shared.generated.resources.theme_section_title
import todo.shared.generated.resources.timezone_hint
import todo.shared.generated.resources.timezone_label
import todo.shared.generated.resources.user_settings_section_title
import todo.shared.generated.resources.contacts_title
import todo.shared.generated.resources.contacts_settings_subtitle
import androidx.compose.material.icons.filled.Person

private enum class SettingsCategory {
    CONTACTS,
    GENERAL,
    THEME,
    LOGS,
    ABOUT,
}

private val STANDARD_TIME_ZONES = listOf(
    "UTC",
    "Europe/Warsaw",
    "Europe/London",
    "Europe/Paris",
    "Europe/Berlin",
    "Europe/Prague",
    "Europe/Vienna",
    "Europe/Rome",
    "Europe/Madrid",
    "Europe/Amsterdam",
    "Europe/Brussels",
    "Europe/Zurich",
    "Europe/Stockholm",
    "Europe/Oslo",
    "Europe/Copenhagen",
    "Europe/Helsinki",
    "Europe/Athens",
    "Europe/Bucharest",
    "Europe/Budapest",
    "Europe/Lisbon",
    "Europe/Dublin",
    "Europe/Kyiv",
    "Europe/Moscow",
    "America/New_York",
    "America/Chicago",
    "America/Denver",
    "America/Los_Angeles",
    "America/Phoenix",
    "America/Anchorage",
    "America/Honolulu",
    "America/Toronto",
    "America/Vancouver",
    "America/Mexico_City",
    "America/Sao_Paulo",
    "America/Buenos_Aires",
    "America/Bogota",
    "America/Santiago",
    "Asia/Tokyo",
    "Asia/Shanghai",
    "Asia/Hong_Kong",
    "Asia/Singapore",
    "Asia/Seoul",
    "Asia/Bangkok",
    "Asia/Jakarta",
    "Asia/Kolkata",
    "Asia/Dubai",
    "Asia/Riyadh",
    "Asia/Jerusalem",
    "Australia/Sydney",
    "Australia/Melbourne",
    "Australia/Brisbane",
    "Australia/Adelaide",
    "Australia/Perth",
    "Pacific/Auckland",
    "Pacific/Fiji",
    "Africa/Cairo",
    "Africa/Johannesburg",
    "Africa/Lagos",
    "Africa/Nairobi",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(module.settings) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentThemeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    val allLogs by AppLoggerManager.logs.collectAsStateWithLifecycle()
    val settingsTitle = stringResource(Res.string.settings)
    val buildInfo = remember { getBuildInfo() }

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    if (selectedCategory != null) {
        BackHandler {
            selectedCategory = null
        }
    }

    Scaffold(
        topBar = {
            if (selectedCategory == null) {
                AppTopBar(
                    title = settingsTitle,
                    onOpenDrawer = onOpenDrawer,
                )
            } else {
                val categoryTitle = when (selectedCategory!!) {
                    SettingsCategory.CONTACTS -> stringResource(Res.string.contacts_title)
                    SettingsCategory.GENERAL -> stringResource(Res.string.settings_category_account_title)
                    SettingsCategory.THEME -> stringResource(Res.string.settings_category_theme_title)
                    SettingsCategory.LOGS -> stringResource(Res.string.settings_category_logs_title)
                    SettingsCategory.ABOUT -> stringResource(Res.string.settings_category_about_title)
                }
                TopAppBar(
                    title = { Text(categoryTitle) },
                    navigationIcon = {
                        IconButton(onClick = { selectedCategory = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.action_close),
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        if (selectedCategory == SettingsCategory.CONTACTS) {
            pl.quicktask.app.contacts.ContactsSettingsContent(module, Modifier.padding(paddingValues))
        } else
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedCategory) {
                null -> {
                    val accountSubtitle = if (uiState.isLoading) {
                        stringResource(Res.string.settings_saving)
                    } else {
                        stringResource(Res.string.settings_category_account_subtitle_format, uiState.timeZone)
                    }

                    val themeSubtitleMode = when (currentThemeMode) {
                        ThemeMode.LIGHT -> stringResource(Res.string.theme_mode_light)
                        ThemeMode.DARK -> stringResource(Res.string.theme_mode_dark)
                        ThemeMode.SYSTEM -> stringResource(Res.string.theme_mode_system)
                    }
                    val themeSubtitle = stringResource(Res.string.settings_category_theme_subtitle_format, themeSubtitleMode)

                    val logsSubtitle = if (AppLoggerManager.isLoggingEnabled) {
                        stringResource(Res.string.settings_category_logs_subtitle_enabled, allLogs.size)
                    } else {
                        stringResource(Res.string.settings_category_logs_subtitle_disabled)
                    }

                    val aboutSubtitle = stringResource(
                        Res.string.settings_category_about_subtitle_format,
                        buildInfo.versionName,
                        buildInfo.buildNumber,
                    )

                    SettingsCategoryItem(
                        title = stringResource(Res.string.settings_category_account_title),
                        subtitle = accountSubtitle,
                        iconPainter = painterResource(Res.drawable.ic_settings),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { selectedCategory = SettingsCategory.GENERAL },
                    )

                    SettingsCategoryItem(
                        title = stringResource(Res.string.settings_category_theme_title),
                        subtitle = themeSubtitle,
                        iconPainter = painterResource(Res.drawable.ic_sun),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { selectedCategory = SettingsCategory.THEME },
                    )

                    SettingsCategoryItem(
                        title = stringResource(Res.string.contacts_title),
                        subtitle = stringResource(Res.string.contacts_settings_subtitle),
                        iconVector = Icons.Default.Person,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = { selectedCategory = SettingsCategory.CONTACTS },
                    )

                    SettingsCategoryItem(
                        title = stringResource(Res.string.settings_category_logs_title),
                        subtitle = logsSubtitle,
                        iconPainter = painterResource(Res.drawable.ic_list),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = { selectedCategory = SettingsCategory.LOGS },
                    )

                    SettingsCategoryItem(
                        title = stringResource(Res.string.settings_category_about_title),
                        subtitle = aboutSubtitle,
                        iconVector = Icons.Default.Info,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { selectedCategory = SettingsCategory.ABOUT },
                    )
                }

                SettingsCategory.GENERAL -> {
                    UserSettingsCard(
                        uiState = uiState,
                        onTimeZoneChange = { viewModel.updateTimeZoneInput(it) },
                        onSaveClick = { viewModel.saveSettings(uiState.timeZone) },
                    )
                }

                SettingsCategory.CONTACTS -> Unit

                SettingsCategory.THEME -> {
                    ThemeSettingsCard(
                        currentMode = currentThemeMode,
                        onModeSelected = { mode -> ThemeManager.setThemeMode(mode) },
                    )
                }

                SettingsCategory.LOGS -> {
                    LogsCard()
                }

                SettingsCategory.ABOUT -> {
                    AboutAppCard(
                        buildInfo = buildInfo,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconVector: ImageVector? = null,
    iconPainter: Painter? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(containerColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                } else iconPainter?.let {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogsCard(
    modifier: Modifier = Modifier,
) {
    val allLogs by AppLoggerManager.logs.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(AppLoggerManager.isLoggingEnabled) }
    var selectedCategoryFilter by remember { mutableStateOf<LogCategoryFilter?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(allLogs, selectedCategoryFilter, searchQuery) {
        allLogs.filter { entry ->
            val categoryMatches = when (selectedCategoryFilter) {
                null -> true
                LogCategoryFilter.API -> ((entry.category == LogCategory.API_REQUEST) || (entry.category == LogCategory.API_RESPONSE))
                LogCategoryFilter.STATE -> entry.category == LogCategory.STATE_CHANGE
                LogCategoryFilter.REFRESH -> entry.category == LogCategory.REFRESH
                LogCategoryFilter.FUNC -> entry.category == LogCategory.FUNCTION
            }
            val queryMatches = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                (entry.details?.contains(searchQuery, ignoreCase = true) == true)

            categoryMatches && queryMatches
        }.reversed()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.logs_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isEnabled) stringResource(Res.string.logs_enabled_label) else stringResource(Res.string.logs_disabled_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            AppLoggerManager.isLoggingEnabled = checked
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.logs_count, filteredLogs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = { AppLoggerManager.clearLogs() },
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.logs_clear_button),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_all),
                    selected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_api),
                    selected = selectedCategoryFilter == LogCategoryFilter.API,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.API },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_state),
                    selected = selectedCategoryFilter == LogCategoryFilter.STATE,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.STATE },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_refresh),
                    selected = selectedCategoryFilter == LogCategoryFilter.REFRESH,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.REFRESH },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_func),
                    selected = selectedCategoryFilter == LogCategoryFilter.FUNC,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.FUNC },
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.logs_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredLogs.forEach { entry ->
                        LogItemView(entry = entry)
                    }
                }
            }
        }
    }
}

private enum class LogCategoryFilter {
    API,
    STATE,
    REFRESH,
    FUNC,
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LogItemView(
    entry: LogEntry,
    modifier: Modifier = Modifier,
) {
    val categoryBg = when (entry.category) {
        LogCategory.API_REQUEST -> MaterialTheme.colorScheme.tertiaryContainer
        LogCategory.API_RESPONSE -> MaterialTheme.colorScheme.secondaryContainer
        LogCategory.STATE_CHANGE -> MaterialTheme.colorScheme.primaryContainer
        LogCategory.REFRESH -> MaterialTheme.colorScheme.surfaceContainerHigh
        LogCategory.FUNCTION -> MaterialTheme.colorScheme.surfaceContainerHighest
        LogCategory.GENERAL -> MaterialTheme.colorScheme.surface
    }

    val categoryFg = when (entry.category) {
        LogCategory.API_REQUEST -> MaterialTheme.colorScheme.onTertiaryContainer
        LogCategory.API_RESPONSE -> MaterialTheme.colorScheme.onSecondaryContainer
        LogCategory.STATE_CHANGE -> MaterialTheme.colorScheme.onPrimaryContainer
        LogCategory.REFRESH -> MaterialTheme.colorScheme.onSurface
        LogCategory.FUNCTION -> MaterialTheme.colorScheme.onSurface
        LogCategory.GENERAL -> MaterialTheme.colorScheme.onSurface
    }

    val isError = entry.level == LogLevel.ERROR

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(categoryBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = categoryFg,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )

            entry.details?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UserSettingsCard(
    uiState: SettingsUiState,
    onTimeZoneChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTimeZonePicker by remember { mutableStateOf(false) }

    if (showTimeZonePicker) {
        TimeZoneSelectionBottomSheet(
            selectedTimeZone = uiState.timeZone,
            onTimeZoneSelected = { selected ->
                onTimeZoneChange(selected)
            },
            onDismiss = { showTimeZonePicker = false },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.user_settings_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimeZonePicker = true },
                ) {
                    OutlinedTextField(
                        value = uiState.timeZone,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(Res.string.timezone_label)) },
                        placeholder = { Text(stringResource(Res.string.timezone_hint)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        isError = (uiState.errorMessage != null) || (uiState.errorMessageRes != null),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                uiState.errorMessageRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (uiState.isSuccess) {
                    Text(
                        text = stringResource(Res.string.settings_saved_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Button(
                    onClick = onSaveClick,
                    enabled = !uiState.isSaving && !uiState.isLoading,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.settings_saving))
                    } else {
                        Text(stringResource(Res.string.save_settings_button))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZoneSelectionBottomSheet(
    selectedTimeZone: String,
    onTimeZoneSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val allTimeZones = remember(selectedTimeZone) {
        if (selectedTimeZone.isNotBlank() && (selectedTimeZone !in STANDARD_TIME_ZONES)) {
            listOf(selectedTimeZone) + STANDARD_TIME_ZONES
        } else {
            STANDARD_TIME_ZONES
        }
    }

    val filteredTimeZones = remember(allTimeZones, searchQuery) {
        if (searchQuery.isBlank()) {
            allTimeZones
        } else {
            val query = searchQuery.trim()
            val filtered = allTimeZones.filter { it.contains(query, ignoreCase = true) }
            if (filtered.isEmpty() && query.isNotBlank()) {
                listOf(query)
            } else {
                filtered
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.select_timezone_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.search_timezone_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredTimeZones) { tz ->
                    val isSelected = tz == selectedTimeZone
                    Surface(
                        onClick = {
                            onTimeZoneSelected(tz)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tz,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsCard(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.theme_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChip(
                    text = stringResource(Res.string.theme_mode_light),
                    selected = currentMode == ThemeMode.LIGHT,
                    onClick = { onModeSelected(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChip(
                    text = stringResource(Res.string.theme_mode_dark),
                    selected = currentMode == ThemeMode.DARK,
                    onClick = { onModeSelected(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChip(
                    text = stringResource(Res.string.theme_mode_system),
                    selected = currentMode == ThemeMode.SYSTEM,
                    onClick = { onModeSelected(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AboutAppCard(
    buildInfo: BuildInfo,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource(Res.string.app_name)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.about_app_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_version_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildInfo.versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_build_number_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildInfo.buildNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
