package com.freellm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.freellm.chat.BuildConfig
import com.freellm.chat.R

@Composable
fun AboutScreen(onBack: () -> Unit, onTour: () -> Unit, onPrivacy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.about), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            BrandRow(
                markSize = 76.dp,
                modifier = Modifier.appear(0),
                devName = stringResource(R.string.dev_name),
                devRole = stringResource(R.string.dev_role),
                aiName = stringResource(R.string.claude_name),
                aiRole = stringResource(R.string.claude_role)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.app_name),
                Modifier.appear(1),
                style = MaterialTheme.typography.headlineSmall,
                color = cs.onBackground
            )
            Text(
                "v" + BuildConfig.VERSION_NAME,
                Modifier.appear(1),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )
            Text(
                stringResource(R.string.about_desc),
                Modifier.appear(2).padding(horizontal = Spacing.sm),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Box(Modifier.fillMaxWidth()) { SectionLabel(stringResource(R.string.about_made_by)) }
            SettingsGroup(Modifier.appear(3)) {
                SettingsRow(Icons.Outlined.Code, stringResource(R.string.about_dev_title), stringResource(R.string.about_dev_body))
                SettingsRow(Icons.Outlined.AutoAwesome, stringResource(R.string.about_ai_title), stringResource(R.string.about_ai_body))
            }
            Box(Modifier.fillMaxWidth()) { SectionLabel(stringResource(R.string.about_features)) }
            SettingsGroup(Modifier.appear(4)) {
                SettingsRow(Icons.Outlined.SwapHoriz, stringResource(R.string.about_f1), stringResource(R.string.about_f1_d))
                SettingsRow(Icons.Outlined.VpnKey, stringResource(R.string.about_f2), stringResource(R.string.about_f2_d))
                SettingsRow(Icons.Outlined.Psychology, stringResource(R.string.about_f3), stringResource(R.string.about_f3_d))
                SettingsRow(Icons.Outlined.Cloud, stringResource(R.string.about_f4), stringResource(R.string.about_f4_d))
            }
            Spacer(Modifier.height(4.dp))
            SettingsGroup(Modifier.appear(5)) {
                SettingsRow(Icons.Outlined.PlayArrow, stringResource(R.string.about_tour), stringResource(R.string.about_tour_d), onClick = onTour)
                SettingsRow(Icons.Outlined.Lock, stringResource(R.string.privacy_title), stringResource(R.string.privacy_row_d), onClick = onPrivacy)
                SettingsRow(Icons.Outlined.Info, stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sections = listOf(
        R.string.pp1_t to R.string.pp1_b,
        R.string.pp2_t to R.string.pp2_b,
        R.string.pp3_t to R.string.pp3_b,
        R.string.pp4_t to R.string.pp4_b,
        R.string.pp5_t to R.string.pp5_b,
        R.string.pp6_t to R.string.pp6_b,
        R.string.pp7_t to R.string.pp7_b,
        R.string.pp8_t to R.string.pp8_b
    )
    Scaffold(containerColor = cs.background, topBar = { AppTopBar(stringResource(R.string.privacy_title), onBack) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            PrivacyArt(Modifier.fillMaxWidth().height(190.dp).appear(0))
            Text(
                stringResource(R.string.privacy_intro),
                Modifier.appear(1).padding(horizontal = Spacing.sm),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant
            )
            sections.forEachIndexed { i, (t, b) ->
                Column(
                    Modifier
                        .appear(i + 2)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Corners.card))
                        .background(cs.surfaceContainerHigh)
                        .padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(stringResource(t), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(b), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.privacy_updated),
                Modifier.fillMaxWidth().padding(top = Spacing.xxs, bottom = 28.dp),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
