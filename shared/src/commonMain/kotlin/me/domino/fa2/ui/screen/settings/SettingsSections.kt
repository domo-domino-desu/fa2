package me.domino.fa2.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.domino.fa2.data.settings.AppSettings
import me.domino.fa2.data.settings.BlockedSubmissionPagerMode
import me.domino.fa2.data.settings.BlockedSubmissionWaterfallMode
import me.domino.fa2.data.settings.TranslationProvider
import me.domino.fa2.ui.component.SettingsGroup
import me.domino.fa2.ui.component.settings.SettingsDropdownField
import me.domino.fa2.ui.component.settings.SettingsSwitchRow

@Composable
internal fun AppearanceSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
    SettingsGroup(
        title = "外观",
        framed = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsDropdownField(
                label = "主题模式",
                selected = draft.themeMode,
                options = AppSettings.supportedThemeModes,
                optionLabel = ::themeModeLabel,
                onSelect = { selected -> onDraftChange(draft.copy(themeMode = selected)) },
            )

            OutlinedTextField(
                value = draft.waterfallMinCardWidthInput,
                onValueChange = { next ->
                    onDraftChange(
                        draft.copy(
                            waterfallMinCardWidthInput = next.filter(Char::isDigit),
                        ),
                    )
                },
                label = { Text("瀑布流单列最小宽度 (dp)") },
                supportingText = {
                    Text(
                        "范围 ${AppSettings.minWaterfallMinCardWidthDp}-${AppSettings.maxWaterfallMinCardWidthDp}，在此基础上自动布局列数",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun TranslationSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
    showApiKey: Boolean,
    onToggleShowApiKey: () -> Unit,
) {
    SettingsGroup(
        title = "翻译",
        framed = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsDropdownField(
                label = "Provider",
                selected = draft.translationProvider,
                options = AppSettings.supportedTranslationProviders,
                optionLabel = ::translationProviderLabel,
                onSelect = { selected ->
                    onDraftChange(draft.copy(translationProvider = selected))
                },
            )

            OutlinedTextField(
                value = draft.chunkWordLimitInput,
                onValueChange = { next ->
                    onDraftChange(draft.copy(chunkWordLimitInput = next.filter(Char::isDigit)))
                },
                label = { Text("Chunk Word Limit") },
                supportingText = {
                    Text(
                        "范围 ${AppSettings.minTranslationChunkWordLimit}-${AppSettings.maxTranslationChunkWordLimit}",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.maxConcurrencyInput,
                onValueChange = { next ->
                    onDraftChange(draft.copy(maxConcurrencyInput = next.filter(Char::isDigit)))
                },
                label = { Text("Max Concurrency") },
                supportingText = {
                    Text(
                        "范围 ${AppSettings.minTranslationMaxConcurrency}-${AppSettings.maxTranslationMaxConcurrency}",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.translationProvider == TranslationProvider.OPENAI_COMPATIBLE) {
                OutlinedTextField(
                    value = draft.openAiBaseUrl,
                    onValueChange = { next -> onDraftChange(draft.copy(openAiBaseUrl = next)) },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = draft.openAiApiKey,
                    onValueChange = { next -> onDraftChange(draft.copy(openAiApiKey = next)) },
                    label = { Text("API Key") },
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = onToggleShowApiKey) {
                            Text(if (showApiKey) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = draft.openAiModel,
                    onValueChange = { next -> onDraftChange(draft.copy(openAiModel = next)) },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = draft.openAiPromptTemplate,
                    onValueChange = { next ->
                        onDraftChange(draft.copy(openAiPromptTemplate = next))
                    },
                    label = { Text("Prompt Template") },
                    supportingText = {
                        Text("支持 [INPUT] [TARGET_LANG] [SEPARATOR]")
                    },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun BlockedContentSettingsSection(
    draft: SettingsDraft,
    onDraftChange: (SettingsDraft) -> Unit,
) {
    SettingsGroup(
        title = "屏蔽内容",
        framed = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSwitchRow(
                label = "瀑布流中模糊显示被屏蔽的投稿",
                checked = draft.blockedSubmissionWaterfallMode ==
                        BlockedSubmissionWaterfallMode.BLUR_THEN_OPEN,
                onCheckedChange = { enabled ->
                    onDraftChange(
                        draft.copy(
                            blockedSubmissionWaterfallMode = if (enabled) {
                                BlockedSubmissionWaterfallMode.BLUR_THEN_OPEN
                            } else {
                                BlockedSubmissionWaterfallMode.SHOW
                            },
                        ),
                    )
                },
            )
            SettingsSwitchRow(
                label = "详情页中模糊显示被屏蔽的投稿",
                checked = draft.blockedSubmissionPagerMode ==
                        BlockedSubmissionPagerMode.BLUR_THEN_OPEN,
                onCheckedChange = { enabled ->
                    onDraftChange(
                        draft.copy(
                            blockedSubmissionPagerMode = if (enabled) {
                                BlockedSubmissionPagerMode.BLUR_THEN_OPEN
                            } else {
                                BlockedSubmissionPagerMode.SHOW
                            },
                        ),
                    )
                },
            )
        }
    }
}
