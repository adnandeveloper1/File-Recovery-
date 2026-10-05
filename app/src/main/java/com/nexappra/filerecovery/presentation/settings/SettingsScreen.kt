package com.nexappra.filerecovery.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.core.ui.components.FlowHeader
import com.nexappra.filerecovery.BuildConfig
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nexappra.filerecovery.domain.model.AppLanguage
import com.nexappra.filerecovery.domain.model.AppThemeMode
import com.nexappra.filerecovery.domain.model.SupportedLanguages

private val SettingsBlue = Color(0xFF2563EB)

@Composable
fun SettingsRoute(
    onOpenPremium: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreen(
        uiState = uiState,
        onLanguageSelected = viewModel::selectLanguage,
        onThemeSelected = viewModel::selectTheme,
        onPermissionsClick = {

            val intent = Intent(
                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )

            context.startActivity(intent)
        },
        onOpenPremium = onOpenPremium,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onPermissionsClick: () -> Unit,
    onOpenPremium: () -> Unit = {},
) {

    var showLanguageSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showThemeSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showContactSupportSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowHeader("Settings", "Make File Recovery work for you")
        SettingsCard {
            SettingsRow(Icons.Rounded.WorkspacePremium, "Recovery Plus",
                "Premium tools, plans and restore purchases", onOpenPremium)
        }
        SettingsSectionTitle("PREFERENCES")
        SettingsCard {
            SettingsRow(Icons.Rounded.LightMode, "Appearance", themeSubtitle(uiState.selectedTheme)) { showThemeSheet = true }
            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(Icons.Rounded.Language, "Language preference", uiState.selectedLanguage.displayName) { showLanguageSheet = true }
        }
        SettingsSectionTitle("PRIVACY & ACCESS")
        SettingsCard {
            SettingsRow(Icons.Rounded.Lock, "Media permissions", "Manage the photos and videos this app can read", onPermissionsClick)
            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(Icons.Rounded.Shield, "Your data", "How scanning, recovery and export use your files") { showPrivacy = true }
        }
        SettingsSectionTitle("HELP")
        SettingsCard {
            SettingsRow(Icons.Rounded.HeadsetMic, "Contact support", "Get help with scanning or recovery") { showContactSupportSheet = true }
        }
        Text("File Recovery · ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · Debug" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp))
    }
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        title = { Text("Your data") },
        text = { Text("Scans read media that Android permits this app to access. Scan results and saved-file history are stored locally. Recovery and repair create new copies; source files are preserved.\n\nCloud export sends files only to the destination you select. Google Play handles payment, and purchase tokens are sent to the configured verification service to check Premium access.\n\nYou can change media access in Permissions. Android does not allow this app to read erased sectors or other apps’ private storage.") },
        confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Done") } },
    )

    /*
     * LANGUAGE BOTTOM SHEET
     */
    if (showLanguageSheet) {

        LanguageBottomSheet(
            selectedLanguage = uiState.selectedLanguage,
            onDismiss = {
                showLanguageSheet = false
            },
            onLanguageSelected = { language ->

                onLanguageSelected(language)

                showLanguageSheet = false
            },
        )
    }

    /*
     * THEME BOTTOM SHEET
     */
    if (showThemeSheet) {

        ThemeBottomSheet(
            selectedTheme = uiState.selectedTheme,
            onDismiss = {
                showThemeSheet = false
            },
            onThemeSelected = { theme ->

                onThemeSelected(theme)

                showThemeSheet = false
            },
        )
    }

    /*
     * CONTACT SUPPORT BOTTOM SHEET
     */
    if (showContactSupportSheet) {

        ContactSupportBottomSheet(
            onDismiss = {
                showContactSupportSheet = false
            },
        )
    }
}

/*
 * ============================================================
 * SETTINGS SECTION TITLE
 * ============================================================
 */

@Composable
private fun SettingsSectionTitle(
    title: String,
) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
    )
}

/*
 * ============================================================
 * SETTINGS CARD
 * ============================================================
 */

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = 0.dp,
    ) {

        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/*
 * ============================================================
 * SETTINGS ROW
 * ============================================================
 */

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = onClick,
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            SettingsIcon(
                icon = icon,
            )

            Spacer(
                modifier = Modifier.width(14.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {

                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (!subtitle.isNullOrBlank()) {

                    Spacer(
                        modifier = Modifier.height(3.dp),
                    )

                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                }
            }

            Spacer(
                modifier = Modifier.width(8.dp),
            )

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme
                    .onSurfaceVariant
                    .copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/*
 * ============================================================
 * SETTINGS ICON
 * ============================================================
 */

@Composable
private fun SettingsIcon(
    icon: ImageVector,
) {
    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center,
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(13.dp),
            color = SettingsBlue.copy(
                alpha = 0.08f,
            ),
        ) {}

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SettingsBlue,
            modifier = Modifier.size(23.dp),
        )
    }
}

/*
 * ============================================================
 * LANGUAGE BOTTOM SHEET
 * ============================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageBottomSheet(
    selectedLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 24.dp,
                ),
        ) {

            Text(
                text = "Language",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(
                modifier = Modifier.height(4.dp),
            )

            Text(
                text = "Save your language preference. The current interface is available in English.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )

            Spacer(
                modifier = Modifier.height(20.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        max = 520.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(
                    5.dp,
                ),
            ) {

                items(
                    items = SupportedLanguages.all,
                    key = { language ->
                        language.code
                    },
                ) { language ->

                    LanguageRow(
                        language = language,
                        selected =
                            language.code == selectedLanguage.code,
                        onClick = {
                            onLanguageSelected(language)
                        },
                    )
                }
            }
        }
    }
}

/*
 * ============================================================
 * LANGUAGE ROW
 * ============================================================
 */

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(
                alpha = 0.08f,
            )
        } else {
            Color.Transparent
        },
        onClick = onClick,
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Text(
                text = language.displayName,
                modifier = Modifier.weight(1f),
                color = if (selected) {
                    SettingsBlue
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = 14.sp,
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Medium
                },
            )

            if (selected) {

                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = SettingsBlue,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

/*
 * ============================================================
 * THEME BOTTOM SHEET
 * ============================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeBottomSheet(
    selectedTheme: AppThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
) {

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 34.dp,
                ),
        ) {

            Text(
                text = "Choose theme",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(
                modifier = Modifier.height(4.dp),
            )

            Text(
                text = "Select how File Recovery looks on your device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )

            Spacer(
                modifier = Modifier.height(22.dp),
            )

            ThemeOptionRow(
                title = "Default",
                subtitle = "Use device setting",
                icon = Icons.Rounded.PhoneAndroid,
                selected = selectedTheme == AppThemeMode.SYSTEM,
                onClick = {
                    onThemeSelected(
                        AppThemeMode.SYSTEM,
                    )
                },
            )

            Spacer(
                modifier = Modifier.height(8.dp),
            )

            ThemeOptionRow(
                title = "Light",
                subtitle = "Always use light theme",
                icon = Icons.Rounded.LightMode,
                selected = selectedTheme == AppThemeMode.LIGHT,
                onClick = {
                    onThemeSelected(
                        AppThemeMode.LIGHT,
                    )
                },
            )

            Spacer(
                modifier = Modifier.height(8.dp),
            )

            ThemeOptionRow(
                title = "Dark",
                subtitle = "Always use dark theme",
                icon = Icons.Rounded.DarkMode,
                selected = selectedTheme == AppThemeMode.DARK,
                onClick = {
                    onThemeSelected(
                        AppThemeMode.DARK,
                    )
                },
            )
        }
    }
}

/*
 * ============================================================
 * THEME OPTION ROW
 * ============================================================
 */

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {

    val selectedBackground =
        MaterialTheme.colorScheme.primary.copy(
            alpha = 0.08f,
        )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) {
            selectedBackground
        } else {
            Color.Transparent
        },
        border = if (selected) {
            BorderStroke(
                width = 1.3.dp,
                color = SettingsBlue,
            )
        } else {
            null
        },
        onClick = onClick,
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    SettingsBlue
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )

            Spacer(
                modifier = Modifier.width(16.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
            ) {

                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(
                    modifier = Modifier.height(4.dp),
                )

                Text(
                    text = subtitle,
                    color = if (selected) {
                        SettingsBlue.copy(
                            alpha = 0.75f,
                        )
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 13.sp,
                )
            }

            if (selected) {

                Box(
                    modifier = Modifier.size(25.dp),
                    contentAlignment = Alignment.Center,
                ) {

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = SettingsBlue,
                    ) {}

                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/*
 * ============================================================
 * CONTACT SUPPORT BOTTOM SHEET
 * ============================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSupportBottomSheet(
    onDismiss: () -> Unit,
) {

    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    /*
     * Replace these with your final support details if needed.
     */
    val supportEmail = "info@nexappra.com"

    /*
     * WhatsApp URL requires international number
     * without +, spaces or dashes.
     */
    val whatsappNumber = "923288562830"

    val whatsappDisplayNumber = "+" + whatsappNumber

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 36.dp,
                ),
        ) {

            Text(
                text = "Contact Support",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(
                modifier = Modifier.height(5.dp),
            )

            Text(
                text = "How would you like to get in touch?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(
                modifier = Modifier.height(22.dp),
            )

            /*
             * EMAIL
             */
            ContactSupportOption(
                icon = Icons.Rounded.Email,
                title = "Email Us",
                subtitle = supportEmail,
                onClick = {

                    val emailIntent = Intent(
                        Intent.ACTION_SENDTO,
                    ).apply {

                        data = Uri.parse(
                            "mailto:$supportEmail",
                        )

                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            "File Recovery Support",
                        )
                    }

                    runCatching {

                        context.startActivity(
                            emailIntent,
                        )
                    }
                },
            )

            Spacer(
                modifier = Modifier.height(8.dp),
            )

            /*
             * WHATSAPP
             */
            ContactSupportOption(
                icon = Icons.Rounded.ChatBubbleOutline,
                title = "WhatsApp",
                subtitle = whatsappDisplayNumber,
                onClick = {

                    val message =
                        "Hello, I need support with File Recovery."

                    val whatsappUri = Uri.parse(
                        "https://wa.me/$whatsappNumber?text=${
                            Uri.encode(message)
                        }",
                    )

                    val whatsappIntent = Intent(
                        Intent.ACTION_VIEW,
                        whatsappUri,
                    )

                    runCatching {

                        context.startActivity(
                            whatsappIntent,
                        )
                    }
                },
            )
        }
    }
}

/*
 * ============================================================
 * CONTACT SUPPORT OPTION
 * ============================================================
 */

@Composable
private fun ContactSupportOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        onClick = onClick,
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center,
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = MaterialTheme.colorScheme
                        .onSurfaceVariant
                        .copy(alpha = 0.75f),
                )
            }

            Spacer(
                modifier = Modifier.width(12.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {

                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(
                    modifier = Modifier.height(4.dp),
                )

                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/*
 * ============================================================
 * THEME SUBTITLE
 * ============================================================
 */

private fun themeSubtitle(
    themeMode: AppThemeMode,
): String {

    return when (themeMode) {

        AppThemeMode.SYSTEM -> {
            "Default"
        }

        AppThemeMode.LIGHT -> {
            "Light"
        }

        AppThemeMode.DARK -> {
            "Dark"
        }
    }
}