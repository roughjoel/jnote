package com.jnote.markdown;

final class MarkdownEditorAssets {
    private MarkdownEditorAssets() {
    }

    static String css() {
        return """
                :root {
                  --ink: #202124;
                  --muted: #6b6f76;
                  --teal: #148f7a;
                  --coral: #d75a4a;
                  --indigo: #4058b9;
                  --gold: #b58218;
                  --teal-soft: #dff3ee;
                  --coral-soft: #f7e3de;
                  --indigo-soft: #e4e9fb;
                  --gold-soft: #f6ecd2;
                  --mono: "JetBrains Mono", Consolas, "Cascadia Mono", monospace;
                  --ui: "Microsoft YaHei UI", "Segoe UI", Arial, sans-serif;
                }
                * { box-sizing: border-box; }
                html, body {
                  margin: 0;
                  min-height: 100%;
                  background:
                    linear-gradient(90deg, rgba(32, 33, 36, 0.035) 1px, transparent 1px) 0 0 / 42px 42px,
                    #fffdfa;
                  color: var(--ink);
                  font-family: var(--ui);
                }
                body { padding: 24px 32px 48px; }
                .page {
                  max-width: 920px;
                  min-height: 520px;
                  margin: 0 auto;
                  padding: 28px;
                  border: 1px solid rgba(28, 28, 28, 0.08);
                  border-radius: 8px;
                  outline: none;
                  background: rgba(255, 255, 255, 0.78);
                  box-shadow: 0 16px 36px rgba(32, 33, 36, 0.08);
                  caret-color: var(--ink);
                }
                .empty-page { padding-top: 72px; }
                .file-banner {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 16px;
                  margin: 0 0 16px;
                  color: #42464c;
                  font-size: 13px;
                }
                .file-banner strong { color: var(--ink); font-size: 18px; }
                .file-banner span { color: var(--muted); font-size: 12px; font-weight: 800; }
                .heading-one, .heading-two, .body-copy, .todo-list li, .code-content, .plain-editor {
                  outline: none;
                  caret-color: var(--ink);
                }
                .source-exposed { color: #555b66; font-family: var(--mono); }
                .section-teal { --section-bg: var(--teal-soft); --section-accent: var(--teal); }
                .section-coral { --section-bg: var(--coral-soft); --section-accent: var(--coral); }
                .section-indigo { --section-bg: var(--indigo-soft); --section-accent: var(--indigo); }
                .section-gold { --section-bg: var(--gold-soft); --section-accent: var(--gold); }
                .in-section {
                  margin-left: -10px;
                  margin-right: -10px;
                  padding-left: 14px;
                  padding-right: 14px;
                  background: var(--section-bg);
                }
                .heading-one {
                  position: relative;
                  margin: 22px -10px 0;
                  min-height: 1.25em;
                  padding: 12px 14px 12px 44px;
                  border-radius: 7px 7px 0 0;
                  color: #202124;
                  font-size: 24px;
                  font-weight: 900;
                  line-height: 1.25;
                }
                .heading-one:first-child { margin-top: 0; }
                .heading-two {
                  position: relative;
                  margin: 0;
                  min-height: 1.2em;
                  padding: 12px 14px 8px 44px;
                  border-radius: 0;
                  background: var(--section-bg, rgba(223, 243, 238, 0.66));
                  color: #30343a;
                  font-size: 18px;
                  font-weight: 900;
                  line-height: 1.3;
                }
                .heading-fold-toggle {
                  position: absolute;
                  left: 13px;
                  top: 50%;
                  transform: translateY(-50%);
                  width: 22px;
                  height: 22px;
                  padding: 0;
                  border: 0;
                  border-radius: 5px;
                  outline: none;
                  background: transparent;
                  color: var(--section-accent, #596270);
                  cursor: pointer;
                }
                .heading-fold-toggle::before {
                  content: "";
                  position: absolute;
                  left: 7px;
                  top: 6px;
                  width: 7px;
                  height: 7px;
                  border-right: 2px solid currentColor;
                  border-bottom: 2px solid currentColor;
                  transform: rotate(45deg);
                  transition: transform 120ms ease, top 120ms ease;
                }
                .heading-folded > .heading-fold-toggle::before {
                  top: 7px;
                  transform: rotate(-45deg);
                }
                .heading-fold-toggle:hover,
                .heading-fold-toggle:focus-visible {
                  background: rgba(32, 33, 36, 0.08);
                }
                .heading-fold-hidden { display: none !important; }
                .body-copy {
                  min-height: 1.8em;
                  margin: 0 0 4px;
                  color: #42464c;
                  font-size: 14px;
                  line-height: 1.8;
                  white-space: pre-wrap;
                }
                .body-copy.in-section { margin-bottom: 0; padding: 4px 14px; }
                .body-copy a, .todo-list a {
                  color: var(--indigo);
                  font-weight: 800;
                  text-decoration: none;
                  border-bottom: 1px solid rgba(64, 88, 185, 0.35);
                }
                .todo-list {
                  display: grid;
                  gap: 7px;
                  margin: 8px 0 12px;
                  padding: 0;
                  list-style: none;
                }
                .todo-list.in-section { margin: 0; padding: 8px 14px; }
                .todo-list li {
                  position: relative;
                  display: block;
                  min-height: 1.6em;
                  padding-left: 27px;
                  color: #3e4248;
                  font-size: 14px;
                  line-height: 1.6;
                }
                .todo-list li::before {
                  content: "";
                  position: absolute;
                  left: 5px;
                  top: 9px;
                  width: 7px;
                  height: 7px;
                  border-radius: 50%;
                  background: var(--coral);
                  box-shadow: 0 0 0 4px rgba(215, 90, 74, 0.12);
                }
                .code-block {
                  overflow: hidden;
                  margin: 12px 0;
                  border: 1px solid #d7dbe2;
                  border-radius: 8px;
                  background: #fbfcfe;
                  box-shadow: 0 8px 20px rgba(32, 33, 36, 0.08);
                }
                .code-block.in-section {
                  overflow: visible;
                  margin: 0;
                  padding: 10px 14px 12px;
                  border: 0;
                  border-radius: 0;
                  background: var(--section-bg);
                  box-shadow: none;
                }
                .code-block.in-section .code-head { border-radius: 7px 7px 0 0; }
                .code-block.in-section pre { border-radius: 0 0 7px 7px; }
                .code-head {
                  height: 34px;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding: 0 12px;
                  border-bottom: 1px solid #d7dbe2;
                  background: #eef1f5;
                  color: #596270;
                  font-family: var(--mono);
                  font-size: 12px;
                }
                .traffic { display: flex; gap: 6px; }
                .traffic i { width: 9px; height: 9px; border-radius: 50%; background: #d86b61; }
                .traffic i:nth-child(2) { background: #d5a739; }
                .traffic i:nth-child(3) { background: #56a879; }
                pre {
                  margin: 0;
                  padding: 16px 18px 18px;
                  overflow: auto;
                  color: #2e343d;
                  background: #fbfcfe;
                  font-family: var(--mono);
                  font-size: 13px;
                  line-height: 1.65;
                  tab-size: 2;
                  white-space: pre-wrap;
                }
                .kw { color: #6f42c1; font-weight: 700; }
                .str { color: #167d55; }
                .num { color: #b74b18; }
                .comment { color: #7b8492; font-style: italic; }
                .inline-image { display: block; margin: 10px 0 6px; }
                .inline-image.in-section { margin: 0; padding: 10px 14px 12px; }
                .inline-image img {
                  display: block;
                  max-width: 100%;
                  max-height: 520px;
                  object-fit: contain;
                  border-radius: 7px;
                  background: #fff;
                  box-shadow: 0 6px 18px rgba(32, 33, 36, 0.09);
                }
                .inline-image.image-load-error {
                  min-height: 110px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  border: 1px dashed #d75a4a;
                  border-radius: 8px;
                  background: #fff8f6;
                }
                .image-error-message {
                  color: #a34236;
                  font-size: 12px;
                  font-weight: 700;
                }
                .plain-editor {
                  display: block;
                  width: 100%;
                  min-height: 460px;
                  margin: 0;
                  padding: 0;
                  border: 0;
                  outline: none;
                  overflow-y: hidden;
                  resize: none;
                  background: transparent;
                  color: #24272b;
                  font-family: var(--ui);
                  font-size: 14px;
                  line-height: 1.75;
                  white-space: pre-wrap;
                }
                .markdown-editor > .in-section {
                  margin-left: -10px;
                  margin-right: -10px;
                }
                [contenteditable="true"]:focus { outline: none; }
                """;
    }
}
