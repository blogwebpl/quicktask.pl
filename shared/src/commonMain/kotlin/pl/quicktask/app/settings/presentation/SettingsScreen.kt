package pl.quicktask.app.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.main.platform.BackHandler
import pl.quicktask.app.platform.getBuildInfo
import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.ui.theme.ThemeManager
import pl.quicktask.app.ui.theme.ThemeMode
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.contacts_settings_subtitle
import todo.shared.generated.resources.contacts_title
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.ic_settings
import todo.shared.generated.resources.ic_sun
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
import todo.shared.generated.resources.settings_saving
import todo.shared.generated.resources.theme_mode_dark
import todo.shared.generated.resources.theme_mode_light
import todo.shared.generated.resources.theme_mode_system

private enum class SettingsCategory {
    CONTACTS,
    GENERAL,
    THEME,
    LOGS,
    ABOUT,
}

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
