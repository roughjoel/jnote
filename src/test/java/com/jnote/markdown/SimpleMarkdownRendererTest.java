package com.jnote.markdown;

import com.jnote.model.NoteFormat;
import com.jnote.model.OpenDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleMarkdownRendererTest {
    private final SimpleMarkdownRenderer renderer = new SimpleMarkdownRenderer();

    @Test
    void rendersIndentedClosingFenceWithoutConsumingFollowingMarkdown() {
        String html = render("""
                ```java
                public class Demo {}
                   ```
                # 后续标题
                """);

        assertTrue(html.contains("data-lang=\"java\""));
        assertTrue(html.contains("class=\"kw\">public</span>"));
        assertTrue(html.contains(">后续标题</h1>"));
        assertFalse(html.contains("# 后续标题</pre>"));
    }

    @Test
    void rendersEverySupportedMarkdownBlock() {
        String html = render("""
                # 一级标题
                ## 二级标题
                - 列表项
                https://example.com
                ![示例](image_note/example.png)
                ```sql
                select 1
                ```
                """);

        assertTrue(html.contains("class=\"heading-one"));
        assertTrue(html.contains("class=\"heading-two"));
        assertTrue(html.contains("class=\"todo-list"));
        assertTrue(html.contains("<a href=\"https://example.com\">"));
        assertTrue(html.contains("class=\"inline-image"));
        assertTrue(html.contains("data-lang=\"sql\""));
    }

    @Test
    void interceptsEveryLinkClickAndDelegatesItOutsideTheWebView() {
        String html = render("https://example.com");

        assertTrue(html.contains("const link = event.target.closest && event.target.closest('a');"));
        assertTrue(html.contains("if (!link) return;"));
        assertTrue(html.contains("event.preventDefault();"));
        assertTrue(html.contains("window.javaBridge.openLink(link.href)"));
        assertFalse(html.contains("if (link && event.ctrlKey)"));
    }

    @Test
    void rendersLinksContainingRegexReplacementCharacters() {
        String html = render("下载：https://example.com/files/$5?name=test");

        assertTrue(html.contains("<a href=\"https://example.com/files/$5?name=test\">"));
    }

    @Test
    void keepsMarkdownMarkersInDomAndIncludesNewEditorModel() {
        String html = render("");

        assertTrue(html.contains("data-base-uri=\"file:///D:/Jnote/notes/\""));
        assertTrue(html.contains("data-image-directory=\"image_realtime-test\""));
        assertTrue(html.contains("function handleParagraphDeletion"));
        assertTrue(html.contains("function handleDeletion"));
        assertTrue(html.contains("function handleEnter"));
        assertTrue(html.contains("function exposeHeading"));
        assertTrue(html.contains("function exposeListItem"));
        assertTrue(html.contains("function exposeCode"));
        assertTrue(html.contains("function normalizedKey"));
        assertTrue(html.contains("deleteContentBackward"));
    }

    @Test
    void keepsATerminalCodeLineVisibleAfterOneEnterPress() {
        String html = render("```java\nint value = 1;\n```\n");

        assertTrue(html.contains("function ensureCodeCaretLine(element, value)"));
        assertTrue(html.contains("text.endsWith('\\n')"));
        assertTrue(html.contains("data-code-caret-line"));
        assertTrue(html.contains("range.setStart(unit, terminalBreakIndex)"));
    }

    @Test
    void preservesEveryBlankLineAsAnEditableBlock() {
        String html = render("第一行\n\n第二行\n");

        assertEquals(4, occurrences(html, "<p class=\"body-copy md-block"));
        assertTrue(html.contains("<p class=\"body-copy md-block\"><br></p>"));
    }

    @Test
    void renderedBlocksRetainTheirSourceMarkers() {
        String html = render("# 标题\n- 列表\n```java\nclass Demo {}\n```\n");

        assertTrue(html.contains("data-marker=\"# \""));
        assertTrue(html.contains("data-marker=\"- \""));
        assertTrue(html.contains("data-marker=\"```java\""));
        assertTrue(html.contains("class=\"page markdown-editor\" contenteditable=\"true\""));
    }

    @Test
    void reloadsEmptyHeadingAndListMarkersAsStyledBlocks() {
        String html = render("# \n- ");

        assertTrue(html.contains("class=\"heading-one"));
        assertTrue(html.contains("class=\"todo-list"));
        assertTrue(html.contains("data-marker=\"# \""));
        assertTrue(html.contains("data-marker=\"- \""));
    }

    @Test
    void rendersPlainTextInATextareaAndSerializesItsValue() {
        String content = "第一行\n\n第二行\n";
        String html = renderPlain(content);

        assertTrue(html.contains("<textarea class=\"plain-editor\" spellcheck=\"false\">"
                + content
                + "</textarea>"));
        assertFalse(html.contains("<pre class=\"plain-editor\""));
        assertTrue(html.contains("plainEditor.value"));
    }

    @Test
    void appliesTheSameSectionBackgroundEdgesToHeadingAndBodyBlocks() {
        String html = render("# 一级标题\n## 二级标题\n正文");
        String css = MarkdownEditorAssets.css();
        int sharedSectionRule = css.indexOf(".markdown-editor > .in-section {");

        assertTrue(html.contains("class=\"heading-one in-section"));
        assertTrue(html.contains("class=\"heading-two in-section"));
        assertTrue(html.contains("class=\"body-copy md-block in-section"));
        assertTrue(sharedSectionRule > css.indexOf(".heading-two {"));
        assertTrue(sharedSectionRule > css.indexOf(".body-copy {"));
        assertTrue(css.substring(sharedSectionRule).startsWith("""
                .markdown-editor > .in-section {
                  margin-left: -10px;
                  margin-right: -10px;
                }
                """));
    }

    @Test
    void usesALightCodeBlockTheme() {
        String css = MarkdownEditorAssets.css();

        assertTrue(css.contains("background: #fbfcfe;"));
        assertTrue(css.contains("color: #2e343d;"));
        assertFalse(css.contains("background: #1f2430;"));
        assertFalse(css.contains("background: #252b38;"));
    }

    @Test
    void doesNotTreatCommentMarkersInsideStringsAsComments() {
        String html = render("```java\nString url = \"https://example.com\"; // 说明\n```");

        assertTrue(html.contains("<span class=\"str\">&quot;https://example.com&quot;</span>"));
        assertTrue(html.contains("<span class=\"comment\">// 说明</span>"));
    }

    @Test
    void forwardsClipboardImageDataToTheJavaBridge() {
        String html = render("");

        assertTrue(html.contains("function pasteImageFromEvent(event)"));
        assertTrue(html.contains("window.javaBridge.pasteImageData"));
        assertTrue(html.contains("reader.readAsDataURL(file)"));
    }

    @Test
    void smoothsMouseWheelScrollingWithAnimationFrames() {
        String html = render("# 标题\n" + "正文\n".repeat(100));

        assertTrue(html.contains("function installSmoothWheelScrolling()"));
        assertTrue(html.contains("window.requestAnimationFrame(animate)"));
        assertTrue(html.contains("{ passive: false, capture: true }"));
        assertTrue(html.contains("event.preventDefault()"));
    }

    @Test
    void embedsLocalImagesAsDataUrisForReliableWebViewDisplay(@TempDir Path directory) throws IOException {
        Path imageDirectory = directory.resolve("image_note");
        Files.createDirectories(imageDirectory);
        Files.write(imageDirectory.resolve("sample.png"), new byte[]{1, 2, 3, 4});
        OpenDocument document = new OpenDocument(
                directory.resolve("note.md"),
                NoteFormat.MARKDOWN,
                "![示例](image_note/sample.png)");

        String html = renderer.render(document);

        assertTrue(html.contains("src=\"data:image/png;base64,AQIDBA==\""));
        assertTrue(html.contains("window.javaBridge.imageDataUri"));
    }

    private String render(String markdown) {
        OpenDocument document = new OpenDocument(
                Path.of("D:/Jnote/notes/realtime-test.md"),
                NoteFormat.MARKDOWN,
                markdown);
        return renderer.render(document);
    }

    private String renderPlain(String content) {
        OpenDocument document = new OpenDocument(
                Path.of("D:/Jnote/notes/realtime-test.txt"),
                NoteFormat.TEXT,
                content);
        return renderer.render(document);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
