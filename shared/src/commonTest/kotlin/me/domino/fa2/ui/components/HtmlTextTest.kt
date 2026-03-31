package me.domino.fa2.ui.components

import androidx.compose.ui.text.LinkAnnotation
import be.digitalia.compose.htmlconverter.htmlToAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.domino.fa2.fake.TestFixtures

class HtmlTextTest {
  @Test
  fun preprocessesIconUsernameAnchorWithVisibleLabelAsUserPlaceholder() {
    val html =
        """
        <a href="/user/andromeda-nsfw" class="iconusername">
          <img src="//a.furaffinity.net/1742570939/andromeda-nsfw.gif" title="andromeda-nsfw" alt="andromeda-nsfw">
          &nbsp;<span class="c-usernameBlockSimple username-underlined"><span class="c-usernameBlockSimple__displayName" title=" andromeda-nsfw ">RainlightDevours</span></span>
        </a>
        """
            .trimIndent()

    val result = preprocessHtmlForFaInlinePlaceholders(html)

    assertEquals(1, result.placeholders.size)
    assertEquals(HtmlInlinePlaceholderKind.User, result.placeholders.single().kind)
    assertEquals("andromeda-nsfw", result.placeholders.single().username)
    assertEquals("RainlightDevours", result.placeholders.single().displayText)
    assertTrue(result.html.contains("/user/andromeda-nsfw"))
    assertTrue(result.html.contains(result.placeholders.single().token))
    assertFalse(result.html.contains("RainlightDevours"))
  }

  @Test
  fun preprocessesIconUsernameAnchorWithoutVisibleLabelAsAvatarPlaceholder() {
    val html =
        """
        <a href="/user/patreon" class="iconusername">
          <img src="//a.furaffinity.net/1502284049/patreon.gif" title="patreon" alt="patreon">
        </a>
        """
            .trimIndent()

    val result = preprocessHtmlForFaInlinePlaceholders(html)

    assertEquals(1, result.placeholders.size)
    assertEquals(HtmlInlinePlaceholderKind.Avatar, result.placeholders.single().kind)
    assertEquals("patreon", result.placeholders.single().username)
    assertEquals("patreon", result.placeholders.single().alternateText)
  }

  @Test
  fun ignoresNonIconUsernameMarkup() {
    val html = """<a href="/user/test"><img src="//a.furaffinity.net/test.gif">Test</a>"""

    val result = preprocessHtmlForFaInlinePlaceholders(html)

    assertTrue(result.placeholders.isEmpty())
    assertEquals(html, result.html)
  }

  @Test
  fun replacesTokensAndPreservesLinkAnnotations() {
    val html =
        """
        <p>
          <a href="/user/andromeda-nsfw" class="iconusername">
            <img src="//a.furaffinity.net/1742570939/andromeda-nsfw.gif" title="andromeda-nsfw" alt="andromeda-nsfw">
            &nbsp;RainlightDevours
          </a>
        </p>
        """
            .trimIndent()
    val preprocessed = preprocessHtmlForFaInlinePlaceholders(html)
    val annotated = htmlToAnnotatedString(html = preprocessed.html, compactMode = true)

    val replaced = replaceHtmlInlineTokens(annotated, preprocessed.placeholders)

    assertEquals(1, replaced.placeholders.size)
    assertFalse(replaced.annotated.text.contains(preprocessed.placeholders.single().token))
    assertTrue(replaced.annotated.text.contains("RainlightDevours"))
    assertTrue(replaced.annotated.getStringAnnotations(0, replaced.annotated.length).isNotEmpty())
    val linkAnnotations = replaced.annotated.getLinkAnnotations(0, replaced.annotated.length)
    assertEquals(1, linkAnnotations.size)
    val link = linkAnnotations.single().item
    assertTrue(link is LinkAnnotation.Url)
    assertEquals("/user/andromeda-nsfw", link.url)
  }

  @Test
  fun parsesUserAndAvatarPlaceholdersFromFixture() {
    val html = TestFixtures.read("www.furaffinity.net:journals:tiaamaito:.html")

    val result = preprocessHtmlForFaInlinePlaceholders(html)

    assertTrue(
        result.placeholders.any {
          it.kind == HtmlInlinePlaceholderKind.Avatar && it.username == "trusted-artists"
        }
    )
    assertTrue(
        result.placeholders.any {
          it.kind == HtmlInlinePlaceholderKind.User && it.username == "nighttwilightwolf"
        }
    )
  }
}
