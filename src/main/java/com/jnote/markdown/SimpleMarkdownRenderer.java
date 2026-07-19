package com.jnote.markdown;

import com.jnote.io.NoteIO;
import com.jnote.model.NoteFormat;
import com.jnote.model.OpenDocument;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SimpleMarkdownRenderer {
    private static final String[] HEADING_COLORS = {"teal", "coral", "indigo", "gold"};
    private static final Pattern H1 = Pattern.compile("^#(?!#)\\s+(.*)$");
    private static final Pattern H2 = Pattern.compile("^##\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^-\\s+(.*)$");
    private static final Pattern FENCE = Pattern.compile("^`{3,}\\s*([A-Za-z0-9_+-]*)\\s*$");
    private static final Pattern IMAGE = Pattern.compile("^!\\[([^]]*)]\\(([^)]+)\\)\\s*$");
    private static final Pattern URL = Pattern.compile("(https?://[^\\s<]+)");

    public String render(OpenDocument document) {
        String body = document.format() == NoteFormat.MARKDOWN
                ? renderMarkdown(document)
                : renderPlain(document);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <style>
                """ + MarkdownEditorAssets.css() + """
                  </style>
                </head>
                <body>
                """ + body + """
                  <script>
                """ + MarkdownEditorScript.script() + """
                  </script>
                </body>
                </html>
                """;
    }

    public String renderEmpty() {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><style>
                """ + MarkdownEditorAssets.css() + """
                </style></head>
                <body>
                  <main class="page markdown-editor empty-page" contenteditable="false">
                    <h1 class="heading-one in-section section-teal" contenteditable="false">打开或新建一个笔记</h1>
                    <p class="body-copy in-section section-teal" contenteditable="false">使用顶部按钮新建文件，或在右侧添加顶级目录后打开文件。</p>
                  </main>
                </body></html>
                """;
    }

    private String renderMarkdown(OpenDocument document) {
        RenderContext context = new RenderContext(document);
        String[] lines = document.content()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n", -1);
        List<String> bullets = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String syntaxLine = line.stripLeading();
            Matcher fence = FENCE.matcher(syntaxLine);
            if (fence.matches()) {
                flushBullets(context, bullets);
                List<String> code = new ArrayList<>();
                i++;
                while (i < lines.length && !FENCE.matcher(lines[i].stripLeading()).matches()) {
                    code.add(lines[i]);
                    i++;
                }
                context.appendCode(fence.group(1), String.join("\n", code));
                continue;
            }

            Matcher h2 = H2.matcher(syntaxLine);
            Matcher h1 = H1.matcher(syntaxLine);
            Matcher bullet = BULLET.matcher(syntaxLine);
            Matcher image = IMAGE.matcher(syntaxLine.trim());
            if (h2.matches()) {
                flushBullets(context, bullets);
                context.appendHeading(2, h2.group(1).trim());
            } else if (h1.matches()) {
                flushBullets(context, bullets);
                context.appendHeading(1, h1.group(1).trim());
            } else if (bullet.matches()) {
                bullets.add(bullet.group(1));
            } else if (image.matches()) {
                flushBullets(context, bullets);
                context.appendImage(image.group(1), image.group(2));
            } else {
                flushBullets(context, bullets);
                context.appendParagraph(line);
            }
        }
        flushBullets(context, bullets);

        return "<main class=\"page markdown-editor\" contenteditable=\"true\" spellcheck=\"false\" data-base-uri=\""
                + HtmlEscaper.escape(markdownBaseUri(document))
                + "\" data-image-directory=\""
                + HtmlEscaper.escape(document.imageDirectoryName())
                + "\">"
                + context.html
                + "</main>";
    }

    private static String markdownBaseUri(OpenDocument document) {
        if (document.path() == null) {
            return "";
        }
        Path parent = document.path().toAbsolutePath().normalize().getParent();
        return parent == null ? "" : parent.toUri().toString();
    }

    private String renderPlain(OpenDocument document) {
        String label = document.format() == NoteFormat.WORD ? "Word 文档" : "文本文件";
        return "<main class=\"page plain-page\">"
                + "<div class=\"file-banner\" contenteditable=\"false\"><strong>"
                + HtmlEscaper.escape(document.displayName())
                + "</strong><span>"
                + label
                + " · "
                + document.format().label()
                + "</span></div>"
                + "<textarea class=\"plain-editor\" spellcheck=\"false\">"
                + HtmlEscaper.escape(document.content())
                + "</textarea></main>";
    }

    private static void flushBullets(RenderContext context, List<String> bullets) {
        if (bullets.isEmpty()) {
            return;
        }
        context.appendBullets(bullets);
        bullets.clear();
    }

    private static String paragraphWithLinks(String text) {
        String escaped = HtmlEscaper.escape(text);
        Matcher matcher = URL.matcher(escaped);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(1);
            String link = "<a href=\"" + url + "\">" + url + "</a>";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(link));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private final class RenderContext {
        private final OpenDocument document;
        private final StringBuilder html = new StringBuilder();
        private int h1Count;
        private String currentSectionColor;

        private RenderContext(OpenDocument document) {
            this.document = document;
        }

        void appendHeading(int level, String title) {
            String marker = level == 1 ? "# " : "## ";
            String tag = level == 1 ? "h1" : "h2";
            String cssClass;
            if (level == 1) {
                currentSectionColor = HEADING_COLORS[h1Count % HEADING_COLORS.length];
                h1Count++;
                cssClass = "heading-one in-section section-" + currentSectionColor;
            } else {
                cssClass = "heading-two" + sectionClasses();
            }
            html.append('<').append(tag)
                    .append(" class=\"").append(cssClass).append(" md-block\"")
                    .append(" data-marker=\"").append(HtmlEscaper.escape(marker)).append("\"")
                    .append('>')
                    .append(HtmlEscaper.escape(title))
                    .append("</").append(tag).append('>');
        }

        void appendParagraph(String text) {
            html.append("<p class=\"body-copy md-block")
                    .append(sectionClasses())
                    .append("\">");
            if (text == null || text.isEmpty()) {
                html.append("<br>");
            } else {
                html.append(paragraphWithLinks(text));
            }
            html.append("</p>");
        }

        void appendBullets(List<String> bullets) {
            html.append("<ul class=\"todo-list md-block")
                    .append(sectionClasses())
                    .append("\">");
            for (String bullet : bullets) {
                html.append("<li data-marker=\"- \">")
                        .append(paragraphWithLinks(bullet))
                        .append("</li>");
            }
            html.append("</ul>");
        }

        void appendCode(String sourceLanguage, String code) {
            String language = sourceLanguage == null ? "" : sourceLanguage.trim();
            String displayLanguage = language.isBlank() ? "text" : language;
            String marker = "```" + language;
            html.append("<div class=\"code-block md-block")
                    .append(sectionClasses())
                    .append("\" data-lang=\"").append(HtmlEscaper.escape(displayLanguage))
                    .append("\" data-marker=\"").append(HtmlEscaper.escape(marker))
                    .append("\" contenteditable=\"false\">")
                    .append("<div class=\"code-head\" contenteditable=\"false\"><span>")
                    .append(HtmlEscaper.escape(displayLanguage))
                    .append(" · syntax preview</span><span class=\"traffic\"><i></i><i></i><i></i></span></div>")
                    .append("<pre class=\"code-content\" contenteditable=\"true\">")
                    .append(CodeHighlighter.highlight(code, displayLanguage))
                    .append("</pre></div>");
        }

        void appendImage(String alt, String source) {
            String uri = resolveImageUri(source);
            String name = source;
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            if (slash >= 0 && slash < name.length() - 1) {
                name = name.substring(slash + 1);
            }
            String label = alt == null || alt.isBlank() ? name : alt;
            String markdown = "![" + label + "](" + source + ")";
            html.append("<figure class=\"inline-image md-block")
                    .append(sectionClasses())
                    .append("\" data-markdown=\"").append(HtmlEscaper.escape(markdown))
                    .append("\" contenteditable=\"false\"><img src=\"").append(HtmlEscaper.escape(uri))
                    .append("\" data-source=\"").append(HtmlEscaper.escape(source))
                    .append("\" alt=\"").append(HtmlEscaper.escape(label))
                    .append("\" title=\"双击放大查看 · 存放于 ")
                    .append(HtmlEscaper.escape(document.imageDirectoryName()))
                    .append("\"></figure>");
        }

        private String sectionClasses() {
            return currentSectionColor == null ? "" : " in-section section-" + currentSectionColor;
        }

        private String resolveImageUri(String source) {
            if (source == null || source.isBlank()) {
                return "";
            }
            if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:")) {
                return source;
            }
            try {
                Path imagePath;
                if (source.startsWith("file:")) {
                    imagePath = Path.of(java.net.URI.create(source));
                } else {
                    Path base = document.path() == null ? Path.of(".") : document.path().getParent();
                    imagePath = base.resolve(source).normalize();
                }
                if (java.nio.file.Files.isRegularFile(imagePath) && NoteIO.isImage(imagePath)) {
                    return NoteIO.imageDataUri(imagePath);
                }
                return imagePath.toUri().toString();
            } catch (java.io.IOException | RuntimeException ignored) {
                return source;
            }
        }
    }
}
