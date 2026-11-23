package me.domino.fa2.ui.pages.search.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fa2.shared.generated.resources.Res
import fa2.shared.generated.resources.clear_search_query
import fa2.shared.generated.resources.close
import fa2.shared.generated.resources.fa_search_syntax_and
import fa2.shared.generated.resources.fa_search_syntax_example
import fa2.shared.generated.resources.fa_search_syntax_fields
import fa2.shared.generated.resources.fa_search_syntax_grouping
import fa2.shared.generated.resources.fa_search_syntax_not
import fa2.shared.generated.resources.fa_search_syntax_or
import fa2.shared.generated.resources.fa_search_syntax_phrase
import fa2.shared.generated.resources.fa_search_syntax_proximity
import fa2.shared.generated.resources.fa_search_syntax_quorum
import fa2.shared.generated.resources.query
import fa2.shared.generated.resources.search_query_example
import fa2.shared.generated.resources.search_syntax_help
import me.domino.fa2.ui.components.uiTestState
import me.domino.fa2.ui.icons.FaMaterialSymbols
import org.jetbrains.compose.resources.stringResource

/** Keep manually entered syntax intact, separating an appended token from the previous term. */
internal fun appendSearchToken(query: String, token: String): String =
    query + (if (query.isNotEmpty() && !query.last().isWhitespace()) " " else "") + token

@Composable
internal fun SearchQueryInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
) {
  val focusRequester = remember { FocusRequester() }
  var input by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
  var showHelp by rememberSaveable { mutableStateOf(false) }
  var focusAfterHelp by remember { mutableStateOf(false) }
  LaunchedEffect(value) {
    if (input.text != value) input = TextFieldValue(value, TextRange(value.length))
  }
  LaunchedEffect(showHelp, focusAfterHelp) {
    if (!showHelp && focusAfterHelp) {
      // Let the real dialog window dispose and return focus to the application's scene.
      withFrameNanos {}
      focusRequester.requestFocus()
      focusAfterHelp = false
    }
  }
  OutlinedTextField(
      value = input,
      onValueChange = {
        input = it
        onValueChange(it.text)
      },
      label = { Text(stringResource(Res.string.query)) },
      placeholder = { Text(stringResource(Res.string.search_query_example)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("search-input").focusRequester(focusRequester),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { if (canSubmit) onSubmit() }),
      leadingIcon = {
        IconButton(
            onClick = { showHelp = true },
            modifier = Modifier.testTag("search-syntax-help"),
        ) {
          Icon(FaMaterialSymbols.Outlined.Info, stringResource(Res.string.search_syntax_help))
        }
      },
      trailingIcon = {
        if (value.isNotBlank()) {
          IconButton(
              onClick = {
                input = TextFieldValue("")
                onValueChange("")
              },
              modifier = Modifier.size(32.dp),
              colors =
                  IconButtonDefaults.iconButtonColors(
                      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
          ) {
            Icon(
                FaMaterialSymbols.Filled.Close,
                stringResource(Res.string.clear_search_query),
                Modifier.size(18.dp),
            )
          }
        }
      },
  )
  if (showHelp) {
    SearchSyntaxDialog(
        onDismiss = { showHelp = false },
        onTokenSelected = { token, caretOffset ->
          val next = appendSearchToken(input.text, token)
          input = TextFieldValue(next, TextRange(next.length - token.length + caretOffset))
          onValueChange(next)
          focusAfterHelp = true
          showHelp = false
        },
    )
  }
}

@Composable
private fun SearchSyntaxDialog(onDismiss: () -> Unit, onTokenSelected: (String, Int) -> Unit) {
  val scroll = rememberScrollState()
  AlertDialog(
      modifier = Modifier.testTag("search-syntax-dialog"),
      onDismissRequest = onDismiss,
      title = { Text(stringResource(Res.string.search_syntax_help)) },
      text = {
        Column(
            modifier =
                Modifier.testTag("search-syntax-content").verticalScroll(scroll).semantics {
                  uiTestState =
                      "offset=${scroll.value};max=${scroll.maxValue};scrolling=${scroll.isScrollInProgress}"
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SyntaxRow(stringResource(Res.string.fa_search_syntax_and)) {
            SyntaxExample("", "fender & rednef", onTokenSelected, "and", "& ")
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_or)) {
            SyntaxExample("", "fender | rednef", onTokenSelected, "or", "| ")
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_not)) {
            SyntaxExample("", "rednef -fender", onTokenSelected, "not", "-")
            SyntaxExample("", "rednef !fender", onTokenSelected, "not-bang", "!")
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_grouping)) {
            SyntaxExample("", "fox & (box | socks)", onTokenSelected, "grouping", "()", 1)
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_fields)) {
            for (field in listOf("title", "message", "lower", "keywords")) {
              SyntaxExample("@", field, onTokenSelected, field, "@" + field + " ")
            }
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_phrase)) {
            SyntaxExample("", "\"quick brown fox\"", onTokenSelected, "phrase", "\"\"", 1)
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_proximity)) {
            SyntaxExample("", "\"quick brown fox\"~3", onTokenSelected, "proximity", "\"\"~3", 1)
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_quorum)) {
            SyntaxExample(
                "",
                "\"red_fox gray_fox arctic_fox fennec swift_fox kit_fox\"/2",
                onTokenSelected,
                "quorum",
                "\"\"/2",
                1,
            )
          }
          SyntaxRow(stringResource(Res.string.fa_search_syntax_example)) {
            Text("(fender | rednef) -truck @keywords plushie", fontFamily = FontFamily.Monospace)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
      },
  )
}

@Composable
private fun SyntaxRow(description: String, examples: @Composable () -> Unit) {
  Column {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      examples()
    }
    Text(description, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun SyntaxExample(
    prefix: String,
    example: String,
    onTokenSelected: (String, Int) -> Unit,
    tag: String? = null,
    insertion: String = prefix,
    caretOffset: Int = insertion.length,
) {
  Text(
      prefix + example,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.primary,
      textDecoration = TextDecoration.Underline,
      modifier =
          (if (tag == null) Modifier else Modifier.testTag("search-syntax-$tag")).clickable {
            onTokenSelected(insertion, caretOffset)
          },
  )
}
