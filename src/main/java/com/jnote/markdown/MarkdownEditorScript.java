package com.jnote.markdown;

final class MarkdownEditorScript {
    private MarkdownEditorScript() {
    }

    static String script() {
        return """
                (function () {
                  const editor = document.querySelector('.markdown-editor');
                  const plainEditor = document.querySelector('.plain-editor');
                  const unitSelector = '.body-copy, .heading-one, .heading-two, .todo-list li, .code-block pre';
                  let normalizePending = false;
                  let forceLinkRefresh = false;
                  let suppressBeforeInput = false;

                  window.jnoteUserChanged = false;
                  function markDirty() {
                    window.jnoteUserChanged = true;
                    if (window.javaBridge && window.javaBridge.markDirty) {
                      window.javaBridge.markDirty();
                    }
                  }
                  window.jnoteHasUserChanges = function () { return !!window.jnoteUserChanged; };
                  window.jnoteResetUserChanges = function () { window.jnoteUserChanged = false; };

                  function normalizeText(value) {
                    return (value || '').replace(/\\u00a0/g, ' ').replace(/　/g, ' ').replace(/\\r/g, '');
                  }

                  function plain(element) {
                    if (!element) return '';
                    if (!element.textContent && element.querySelector && element.querySelector('br')) return '';
                    const value = element.textContent == null ? element.innerText : element.textContent;
                    return normalizeText(value);
                  }

                  function terminalCodeCaretLine(element) {
                    if (!element || !element.matches || !element.matches('.code-block pre')) return null;
                    const last = element.lastElementChild;
                    return last && last.matches('br[data-code-caret-line]') ? last : null;
                  }

                  function ensureCodeCaretLine(element, value) {
                    if (!element || !element.matches || !element.matches('.code-block pre')) return;
                    const text = value || '';
                    const existing = terminalCodeCaretLine(element);
                    if (text.endsWith('\\n')) {
                      if (!existing) {
                        const placeholder = document.createElement('br');
                        placeholder.setAttribute('data-code-caret-line', 'true');
                        element.appendChild(placeholder);
                      }
                    } else if (existing) {
                      existing.remove();
                    }
                  }

                  function setPlain(element, value) {
                    const text = value || '';
                    element.textContent = text;
                    if (!text) element.innerHTML = '<br>';
                    else ensureCodeCaretLine(element, text);
                  }

                  function resizePlainEditor() {
                    if (!plainEditor) return;
                    plainEditor.style.height = 'auto';
                    plainEditor.style.height = Math.max(460, plainEditor.scrollHeight) + 'px';
                  }

                  function installSmoothWheelScrolling() {
                    const scrollingElement = document.scrollingElement || document.documentElement;
                    if (!scrollingElement) return;
                    let target = scrollingElement.scrollTop;
                    let animationFrame = 0;
                    let lastFrameTime = 0;

                    function clampScroll(value) {
                      const maximum = Math.max(0, scrollingElement.scrollHeight - scrollingElement.clientHeight);
                      return Math.max(0, Math.min(maximum, value));
                    }

                    function animate(timestamp) {
                      const current = scrollingElement.scrollTop;
                      const distance = target - current;
                      if (Math.abs(distance) < 0.4) {
                        scrollingElement.scrollTop = target;
                        animationFrame = 0;
                        lastFrameTime = 0;
                        return;
                      }
                      const elapsed = lastFrameTime ? Math.min(48, timestamp - lastFrameTime) : 16;
                      lastFrameTime = timestamp;
                      const progress = 1 - Math.exp(-elapsed / 70);
                      scrollingElement.scrollTop = current + distance * progress;
                      animationFrame = window.requestAnimationFrame(animate);
                    }

                    function cancelAnimation() {
                      if (animationFrame) window.cancelAnimationFrame(animationFrame);
                      animationFrame = 0;
                      lastFrameTime = 0;
                      target = scrollingElement.scrollTop;
                    }

                    document.addEventListener('wheel', function (event) {
                      if (event.ctrlKey || Math.abs(event.deltaX) > Math.abs(event.deltaY)) return;
                      const maximum = Math.max(0, scrollingElement.scrollHeight - scrollingElement.clientHeight);
                      if (maximum <= 0 || Math.abs(event.deltaY) < 0.01) return;
                      let delta = event.deltaY;
                      if (event.deltaMode === 1) delta *= 18;
                      else if (event.deltaMode === 2) delta *= scrollingElement.clientHeight * 0.9;
                      delta = Math.max(-220, Math.min(220, delta * 1.16));
                      const current = scrollingElement.scrollTop;
                      if (!animationFrame) target = current;
                      const maximumLead = Math.max(360, scrollingElement.clientHeight * 1.35);
                      target = clampScroll(Math.max(current - maximumLead, Math.min(current + maximumLead, target + delta)));
                      if (Math.abs(target - current) < 0.4) return;
                      event.preventDefault();
                      if (!animationFrame) animationFrame = window.requestAnimationFrame(animate);
                    }, { passive: false, capture: true });
                    document.addEventListener('mousedown', cancelAnimation, true);
                    document.addEventListener('keydown', function (event) {
                      if (['PageUp', 'PageDown', 'Home', 'End', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
                        cancelAnimation();
                      }
                    }, true);
                  }

                  function selection() { return window.getSelection(); }
                  function selectionCollapsed() {
                    const current = selection();
                    return !!current && current.rangeCount > 0 && current.isCollapsed;
                  }

                  function currentUnit() {
                    const current = selection();
                    if (!current || !current.anchorNode || !editor) return null;
                    let node = current.anchorNode;
                    if (node === editor) {
                      const children = editor.childNodes;
                      const offset = current.anchorOffset;
                      node = children[Math.min(offset, children.length - 1)]
                        || children[Math.max(0, offset - 1)]
                        || editor;
                    }
                    let element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
                    if (!element) return null;
                    if (element.matches && element.matches(unitSelector)) return element;
                    const direct = element.closest ? element.closest(unitSelector) : null;
                    if (direct) return direct;
                    const block = element.closest ? element.closest('.md-block') : null;
                    if (!block) return null;
                    if (block.classList.contains('code-block')) return block.querySelector('pre');
                    if (block.classList.contains('todo-list')) {
                      const items = block.querySelectorAll('li');
                      return items.length ? items[Math.min(current.anchorOffset, items.length - 1)] : null;
                    }
                    return block.matches(unitSelector) ? block : null;
                  }

                  function blockOf(unit) {
                    if (!unit) return null;
                    if (unit.matches('.todo-list li')) return unit.closest('.todo-list');
                    if (unit.matches('.code-block pre')) return unit.closest('.code-block');
                    return unit.closest('.md-block');
                  }

                  function focusHost(unit) {
                    if (unit && unit.matches('.code-block pre')) unit.focus();
                    else if (editor) editor.focus();
                  }

                  function caretOffset(unit) {
                    const current = selection();
                    if (!current || !current.anchorNode || !unit.contains(current.anchorNode)) return null;
                    const range = document.createRange();
                    range.selectNodeContents(unit);
                    range.setEnd(current.anchorNode, current.anchorOffset);
                    return range.toString().length;
                  }

                  function setCaretOffset(unit, requestedOffset) {
                    if (!unit) return;
                    focusHost(unit);
                    const total = plain(unit).length;
                    let remaining = Math.max(0, Math.min(total, requestedOffset));
                    const terminalBreak = terminalCodeCaretLine(unit);
                    if (remaining === total && terminalBreak) {
                      const range = document.createRange();
                      const terminalBreakIndex = Array.prototype.indexOf.call(unit.childNodes, terminalBreak);
                      range.setStart(unit, terminalBreakIndex);
                      range.collapse(true);
                      const current = selection();
                      current.removeAllRanges();
                      current.addRange(range);
                      return;
                    }
                    const walker = document.createTreeWalker(unit, NodeFilter.SHOW_TEXT);
                    let node = walker.nextNode();
                    while (node) {
                      if (remaining <= node.textContent.length) {
                        const range = document.createRange();
                        range.setStart(node, remaining);
                        range.collapse(true);
                        const current = selection();
                        current.removeAllRanges();
                        current.addRange(range);
                        return;
                      }
                      remaining -= node.textContent.length;
                      node = walker.nextNode();
                    }
                    const range = document.createRange();
                    range.selectNodeContents(unit);
                    range.collapse(false);
                    const current = selection();
                    current.removeAllRanges();
                    current.addRange(range);
                  }

                  function setCaretStart(unit) { setCaretOffset(unit, 0); }
                  function setCaretEnd(unit) { setCaretOffset(unit, plain(unit).length); }
                  function atStart(unit) {
                    const offset = caretOffset(unit);
                    return offset === 0 || (offset === null && !plain(unit));
                  }
                  function atEnd(unit) {
                    const offset = caretOffset(unit);
                    return (offset !== null && offset === plain(unit).length) || (offset === null && !plain(unit));
                  }
                  function textAroundCaret(unit) {
                    const text = plain(unit);
                    const offset = caretOffset(unit);
                    if (offset === null) return { before: text, after: '' };
                    return { before: text.substring(0, offset), after: text.substring(offset) };
                  }

                  function createParagraph(text, sourceExposed) {
                    const paragraph = document.createElement('p');
                    paragraph.className = 'body-copy md-block' + (sourceExposed ? ' source-exposed' : '');
                    if (sourceExposed) paragraph.setAttribute('data-source-exposed', 'true');
                    setPlain(paragraph, text || '');
                    return paragraph;
                  }

                  function createHeading(level, title) {
                    const heading = document.createElement(level === 1 ? 'h1' : 'h2');
                    heading.className = (level === 1 ? 'heading-one' : 'heading-two') + ' md-block';
                    heading.setAttribute('data-marker', level === 1 ? '# ' : '## ');
                    setPlain(heading, title || '');
                    return heading;
                  }

                  function createList() {
                    const list = document.createElement('ul');
                    list.className = 'todo-list md-block';
                    return list;
                  }

                  function createListItem(text) {
                    const item = document.createElement('li');
                    item.setAttribute('data-marker', '- ');
                    setPlain(item, text || '');
                    refreshLinks(item, false);
                    return item;
                  }

                  function appendCodeToken(parent, className, text) {
                    if (!text) return;
                    if (!className) {
                      parent.appendChild(document.createTextNode(text));
                      return;
                    }
                    const token = document.createElement('span');
                    token.className = className;
                    token.textContent = text;
                    parent.appendChild(token);
                  }

                  function codeKeywords(language) {
                    const java = 'abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record sealed permits non-sealed';
                    const python = 'and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield';
                    const sql = 'select from where join left right inner outer on and or not insert into update delete create alter drop table view index group by order having limit offset as distinct union all case when then else end values set desc asc';
                    return new Set((language === 'python' || language === 'py' ? python : (language === 'sql' ? sql : java)).split(' '));
                  }

                  function highlightCode(pre) {
                    if (!pre) return;
                    const code = plain(pre);
                    const block = blockOf(pre);
                    const language = ((block && block.getAttribute('data-lang')) || 'text').toLowerCase();
                    const keywords = codeKeywords(language);
                    const commentPrefix = language === 'python' || language === 'py' ? '#' : (language === 'sql' ? '--' : '//');
                    pre.innerHTML = '';
                    code.split('\\n').forEach(function (line, lineIndex, lines) {
                      const commentIndex = line.indexOf(commentPrefix);
                      const source = commentIndex >= 0 ? line.substring(0, commentIndex) : line;
                      const comment = commentIndex >= 0 ? line.substring(commentIndex) : '';
                      let index = 0;
                      while (index < source.length) {
                        const character = source.charAt(index);
                        if (character === '"' || character === "'") {
                          let end = index + 1;
                          let escaping = false;
                          while (end < source.length) {
                            const current = source.charAt(end);
                            if (escaping) escaping = false;
                            else if (current === '\\\\') escaping = true;
                            else if (current === character) { end++; break; }
                            end++;
                          }
                          appendCodeToken(pre, 'str', source.substring(index, end));
                          index = end;
                        } else if (/\\d/.test(character)) {
                          let end = index + 1;
                          while (end < source.length && /[\\d.]/.test(source.charAt(end))) end++;
                          appendCodeToken(pre, 'num', source.substring(index, end));
                          index = end;
                        } else if (/[A-Za-z_$]/.test(character)) {
                          let end = index + 1;
                          while (end < source.length && /[A-Za-z0-9_$-]/.test(source.charAt(end))) end++;
                          const word = source.substring(index, end);
                          const lookup = language === 'sql' ? word.toLowerCase() : word;
                          appendCodeToken(pre, keywords.has(lookup) ? 'kw' : '', word);
                          index = end;
                        } else {
                          appendCodeToken(pre, '', character);
                          index++;
                        }
                      }
                      appendCodeToken(pre, comment ? 'comment' : '', comment);
                      if (lineIndex < lines.length - 1) pre.appendChild(document.createTextNode('\\n'));
                    });
                    ensureCodeCaretLine(pre, code);
                  }

                  function flattenCode(pre) {
                    if (!pre || !pre.querySelector('span')) return;
                    const offset = caretOffset(pre);
                    const text = plain(pre);
                    pre.textContent = text;
                    ensureCodeCaretLine(pre, text);
                    if (offset !== null) setCaretOffset(pre, offset);
                  }

                  function createCode(language, code) {
                    const sourceLanguage = (language || '').trim();
                    const displayLanguage = sourceLanguage || 'text';
                    const block = document.createElement('div');
                    block.className = 'code-block md-block';
                    block.setAttribute('contenteditable', 'false');
                    block.setAttribute('data-marker', '```' + sourceLanguage);
                    block.setAttribute('data-lang', displayLanguage);
                    const head = document.createElement('div');
                    head.className = 'code-head';
                    head.setAttribute('contenteditable', 'false');
                    const label = document.createElement('span');
                    label.textContent = displayLanguage + ' · syntax preview';
                    const traffic = document.createElement('span');
                    traffic.className = 'traffic';
                    traffic.appendChild(document.createElement('i'));
                    traffic.appendChild(document.createElement('i'));
                    traffic.appendChild(document.createElement('i'));
                    head.appendChild(label);
                    head.appendChild(traffic);
                    const pre = document.createElement('pre');
                    pre.className = 'code-content';
                    pre.setAttribute('contenteditable', 'true');
                    pre.textContent = code || '';
                    block.appendChild(head);
                    block.appendChild(pre);
                    highlightCode(pre);
                    return block;
                  }

                  function imageUri(source) {
                    const base = editor ? editor.getAttribute('data-base-uri') || '' : '';
                    try { return base ? new URL(source, base).href : source; }
                    catch (ignored) { return source; }
                  }

                  function attachImageStatus(image) {
                    if (!image || image.dataset.statusAttached) return;
                    image.dataset.statusAttached = 'true';
                    const figure = image.closest('.inline-image');
                    image.addEventListener('load', function () {
                      if (!figure) return;
                      figure.classList.remove('image-load-error');
                      const message = figure.querySelector('.image-error-message');
                      if (message) message.remove();
                    });
                    image.addEventListener('error', function () {
                      const source = image.getAttribute('data-source') || '';
                      if (!image.dataset.dataRetried && window.javaBridge && window.javaBridge.imageDataUri) {
                        image.dataset.dataRetried = 'true';
                        const dataUri = String(window.javaBridge.imageDataUri(source) || '');
                        if (dataUri) {
                          image.src = dataUri;
                          return;
                        }
                      }
                      if (!figure) return;
                      figure.classList.add('image-load-error');
                      if (!figure.querySelector('.image-error-message')) {
                        const message = document.createElement('span');
                        message.className = 'image-error-message';
                        message.textContent = '图片加载失败 · ' + source;
                        figure.appendChild(message);
                      }
                    });
                    if (image.src && image.complete && !image.naturalWidth) {
                      window.setTimeout(function () { image.dispatchEvent(new Event('error')); }, 0);
                    }
                  }

                  function createImage(uri, source, alt) {
                    const figure = document.createElement('figure');
                    figure.className = 'inline-image md-block';
                    figure.setAttribute('contenteditable', 'false');
                    const name = source.split(/[\\\\/]/).pop();
                    const label = alt && alt.trim() ? alt : name;
                    const markdown = '![' + label + '](' + source + ')';
                    figure.setAttribute('data-markdown', markdown);
                    const image = document.createElement('img');
                    image.src = uri || imageUri(source);
                    image.setAttribute('data-source', source);
                    image.alt = label;
                    image.title = '双击放大查看 · 存放于 ' + (editor.getAttribute('data-image-directory') || '');
                    figure.appendChild(image);
                    attachImageStatus(image);
                    return figure;
                  }

                  function removeSectionClasses(block) {
                    block.classList.remove('in-section', 'section-teal', 'section-coral', 'section-indigo', 'section-gold');
                  }
                  function refreshSections() {
                    if (!editor) return;
                    const colors = ['section-teal', 'section-coral', 'section-indigo', 'section-gold'];
                    let active = null;
                    let headingIndex = 0;
                    Array.from(editor.children).forEach(function (block) {
                      removeSectionClasses(block);
                      if (block.classList.contains('heading-one')) {
                        active = colors[headingIndex % colors.length];
                        headingIndex++;
                      }
                      if (active && block.classList.contains('md-block')) block.classList.add('in-section', active);
                    });
                  }

                  function replaceBlock(block, replacements, focusUnit, offset) {
                    if (!block || !block.parentNode) return false;
                    replacements.forEach(function (replacement) { block.parentNode.insertBefore(replacement, block); });
                    block.remove();
                    refreshSections();
                    if (focusUnit) setCaretOffset(focusUnit, offset == null ? plain(focusUnit).length : offset);
                    return true;
                  }

                  function headingMatch(text) {
                    const h2 = text.match(/^##\\s+([\\s\\S]*)$/);
                    if (h2) return { level: 2, title: h2[1] };
                    const h1 = text.match(/^#\\s+([\\s\\S]*)$/);
                    return h1 ? { level: 1, title: h1[1] } : null;
                  }
                  function openingFenceMatch(text) {
                    return normalizeText(text).trim().match(/^`{3,}\\s*([A-Za-z0-9_+-]*)\\s*$/);
                  }
                  function completeFenceMatch(text) {
                    const normalized = normalizeText(text).trim();
                    const lines = normalized.split('\\n');
                    if (lines.length > 1) {
                      const opening = openingFenceMatch(lines[0]);
                      if (opening && /^`{3,}\\s*$/.test(lines[lines.length - 1].trim())) {
                        return { language: opening[1], code: lines.slice(1, -1).join('\\n') };
                      }
                    }
                    const oneLine = normalized.match(/^```\\s*([A-Za-z0-9_+-]*)\\s*```$/);
                    return oneLine ? { language: oneLine[1], code: '' } : null;
                  }
                  function imageMatch(text) {
                    return normalizeText(text).trim().match(/^!\\[([^\\]]*)]\\(([^)]+)\\)\\s*$/);
                  }

                  function refreshLinks(unit, preserveCaret) {
                    if (!unit || unit.matches('.code-block pre')) return false;
                    const text = plain(unit);
                    const matcher = /https?:\\/\\/[^\\s<]+/g;
                    const anchors = Array.from(unit.querySelectorAll('a'));
                    if (!matcher.test(text)) {
                      if (!anchors.length) return false;
                      const offset = preserveCaret ? caretOffset(unit) : null;
                      setPlain(unit, text);
                      if (offset !== null) setCaretOffset(unit, offset);
                      return true;
                    }
                    matcher.lastIndex = 0;
                    const urls = [];
                    let found;
                    while ((found = matcher.exec(text)) !== null) urls.push(found[0]);
                    if (anchors.length === urls.length && anchors.every(function (anchor, index) {
                      return anchor.textContent === urls[index] && anchor.getAttribute('href') === urls[index];
                    })) return false;
                    const offset = preserveCaret ? caretOffset(unit) : null;
                    matcher.lastIndex = 0;
                    unit.innerHTML = '';
                    let index = 0;
                    let match;
                    while ((match = matcher.exec(text)) !== null) {
                      unit.appendChild(document.createTextNode(text.substring(index, match.index)));
                      const link = document.createElement('a');
                      link.href = match[0];
                      link.textContent = match[0];
                      unit.appendChild(link);
                      index = match.index + match[0].length;
                    }
                    unit.appendChild(document.createTextNode(text.substring(index)));
                    if (offset !== null) setCaretOffset(unit, offset);
                    return true;
                  }

                  function normalizeParagraph(paragraph, forceLinks) {
                    if (!paragraph || !paragraph.classList.contains('body-copy') || paragraph.hasAttribute('data-source-exposed')) return false;
                    const text = plain(paragraph);
                    const fence = completeFenceMatch(text);
                    if (fence) {
                      const code = createCode(fence.language, fence.code);
                      const next = createParagraph('', false);
                      return replaceBlock(paragraph, [code, next], next, 0);
                    }
                    const heading = headingMatch(text);
                    if (heading) {
                      const block = createHeading(heading.level, heading.title);
                      return replaceBlock(paragraph, [block], block, plain(block).length);
                    }
                    const listMatch = text.match(/^-\\s+([\\s\\S]*)$/);
                    if (listMatch) {
                      const list = createList();
                      const item = createListItem(listMatch[1].replace(/\\n+$/g, ''));
                      list.appendChild(item);
                      return replaceBlock(paragraph, [list], item, plain(item).length);
                    }
                    const image = imageMatch(text);
                    if (image) {
                      const figure = createImage('', image[2], image[1]);
                      const next = createParagraph('', false);
                      return replaceBlock(paragraph, [figure, next], next, 0);
                    }
                    if (forceLinks || /\\s$/.test(text) || paragraph.querySelector('a')) {
                      return refreshLinks(paragraph, paragraph === currentUnit());
                    }
                    return false;
                  }

                  function scheduleNormalize(forceLinks) {
                    forceLinkRefresh = forceLinkRefresh || !!forceLinks;
                    if (normalizePending) return;
                    normalizePending = true;
                    window.setTimeout(function () {
                      normalizePending = false;
                      const force = forceLinkRefresh;
                      forceLinkRefresh = false;
                      normalizeParagraph(currentUnit(), force);
                    }, 0);
                  }

                  function firstUnit(block) {
                    if (!block) return null;
                    if (block.matches(unitSelector)) return block;
                    return block.querySelector(unitSelector);
                  }
                  function lastUnit(block) {
                    if (!block) return null;
                    if (block.matches(unitSelector)) return block;
                    const units = block.querySelectorAll(unitSelector);
                    return units.length ? units[units.length - 1] : null;
                  }

                  function splitListItem(item, paragraph) {
                    const list = item.closest('.todo-list');
                    if (!list) return false;
                    const hasPrevious = !!item.previousElementSibling;
                    const trailing = createList();
                    let sibling = item.nextElementSibling;
                    while (sibling) {
                      const next = sibling.nextElementSibling;
                      trailing.appendChild(sibling);
                      sibling = next;
                    }
                    item.remove();
                    if (hasPrevious) list.insertAdjacentElement('afterend', paragraph);
                    else {
                      list.insertAdjacentElement('beforebegin', paragraph);
                      list.remove();
                    }
                    if (trailing.querySelector('li')) paragraph.insertAdjacentElement('afterend', trailing);
                    refreshSections();
                    return true;
                  }

                  function exposeHeading(heading, key) {
                    const marker = heading.getAttribute('data-marker') || '';
                    const shownMarker = key === 'Backspace' ? marker.replace(/\\s+$/g, '') : marker;
                    const source = shownMarker + plain(heading);
                    const paragraph = createParagraph(source, true);
                    return replaceBlock(heading, [paragraph], paragraph, key === 'Backspace' ? shownMarker.length : source.length);
                  }
                  function exposeListItem(item, key) {
                    const marker = item.getAttribute('data-marker') || '- ';
                    const shownMarker = key === 'Backspace' ? marker.replace(/\\s+$/g, '') : marker;
                    const source = shownMarker + plain(item);
                    const paragraph = createParagraph(source, true);
                    if (!splitListItem(item, paragraph)) return false;
                    setCaretOffset(paragraph, key === 'Backspace' ? shownMarker.length : source.length);
                    return true;
                  }
                  function exposeCode(pre, key) {
                    const block = blockOf(pre);
                    const opening = block.getAttribute('data-marker') || '```';
                    const code = plain(pre).replace(/\\n+$/g, '');
                    const source = opening + (code ? '\\n' + code + '\\n```' : '');
                    const paragraph = createParagraph(source, true);
                    return replaceBlock(block, [paragraph], paragraph, key === 'Backspace' ? opening.length : source.length);
                  }
                  function exposeImage(figure, focusAtEnd) {
                    const source = figure.getAttribute('data-markdown') || '';
                    const paragraph = createParagraph(source, true);
                    return replaceBlock(figure, [paragraph], paragraph, focusAtEnd ? source.length : 0);
                  }

                  function handleParagraphDeletion(paragraph, key) {
                    if (!selectionCollapsed()) return false;
                    if (!(key === 'Backspace' ? atStart(paragraph) : atEnd(paragraph))) return false;
                    const text = plain(paragraph);
                    const sibling = key === 'Backspace' ? paragraph.previousElementSibling : paragraph.nextElementSibling;
                    if (sibling && sibling.classList.contains('inline-image')) {
                      if (!text) paragraph.remove();
                      return exposeImage(sibling, key === 'Backspace');
                    }
                    if (!text) {
                      if (!sibling) return false;
                      paragraph.remove();
                      const target = key === 'Backspace' ? lastUnit(sibling) : firstUnit(sibling);
                      if (target) {
                        if (key === 'Backspace') setCaretEnd(target); else setCaretStart(target);
                      }
                      refreshSections();
                      return true;
                    }
                    if (sibling && sibling.classList.contains('body-copy')) {
                      if (key === 'Backspace') {
                        const offset = plain(sibling).length;
                        setPlain(sibling, plain(sibling) + text);
                        paragraph.remove();
                        setCaretOffset(sibling, offset);
                        refreshLinks(sibling, true);
                      } else {
                        const offset = text.length;
                        setPlain(paragraph, text + plain(sibling));
                        sibling.remove();
                        setCaretOffset(paragraph, offset);
                        refreshLinks(paragraph, true);
                      }
                      return true;
                    }
                    if (sibling) {
                      const target = key === 'Backspace' ? lastUnit(sibling) : firstUnit(sibling);
                      if (target) {
                        if (key === 'Backspace') setCaretEnd(target); else setCaretStart(target);
                        return true;
                      }
                    }
                    return false;
                  }

                  function handleDeletion(unit, key) {
                    if (!unit || !selectionCollapsed()) return false;
                    if (unit.classList.contains('body-copy')) return handleParagraphDeletion(unit, key);
                    if (!(key === 'Backspace' ? atStart(unit) : atEnd(unit))) return false;
                    if (unit.classList.contains('heading-one') || unit.classList.contains('heading-two')) {
                      return exposeHeading(unit, key);
                    }
                    if (unit.matches('.todo-list li')) return exposeListItem(unit, key);
                    if (unit.matches('.code-block pre')) return exposeCode(unit, key);
                    return false;
                  }

                  function finishCodeIfClosed(pre) {
                    const text = plain(pre).replace(/\\n+$/g, '');
                    const lines = text.split('\\n');
                    if (!lines.length || !/^`{3,}\\s*$/.test(lines[lines.length - 1].trim())) return false;
                    lines.pop();
                    setPlain(pre, lines.join('\\n'));
                    highlightCode(pre);
                    const block = blockOf(pre);
                    let next = block.nextElementSibling;
                    if (!next || !next.classList.contains('body-copy')) {
                      next = createParagraph('', false);
                      block.insertAdjacentElement('afterend', next);
                    }
                    refreshSections();
                    setCaretStart(next);
                    return true;
                  }

                  function handleEnter(unit) {
                    if (!unit) return false;
                    if (unit.classList.contains('body-copy')) {
                      if (!unit.hasAttribute('data-source-exposed')) {
                        const opening = openingFenceMatch(plain(unit));
                        if (opening) {
                          const code = createCode(opening[1], '');
                          const next = createParagraph('', false);
                          replaceBlock(unit, [code, next], code.querySelector('pre'), 0);
                          return true;
                        }
                      }
                      const parts = textAroundCaret(unit);
                      setPlain(unit, parts.before);
                      unit.removeAttribute('data-source-exposed');
                      unit.classList.remove('source-exposed');
                      const next = createParagraph(parts.after, false);
                      unit.insertAdjacentElement('afterend', next);
                      refreshSections();
                      setCaretStart(next);
                      return true;
                    }
                    if (unit.classList.contains('heading-one') || unit.classList.contains('heading-two')) {
                      const next = createParagraph('', false);
                      unit.insertAdjacentElement('afterend', next);
                      refreshSections();
                      setCaretStart(next);
                      return true;
                    }
                    if (unit.matches('.todo-list li')) {
                      if (plain(unit).trim()) {
                        const next = createListItem('');
                        unit.insertAdjacentElement('afterend', next);
                        setCaretStart(next);
                      } else {
                        const paragraph = createParagraph('', false);
                        splitListItem(unit, paragraph);
                        setCaretStart(paragraph);
                      }
                      refreshSections();
                      return true;
                    }
                    if (unit.matches('.code-block pre')) {
                      if (finishCodeIfClosed(unit)) return true;
                      flattenCode(unit);
                      const parts = textAroundCaret(unit);
                      setPlain(unit, parts.before + '\\n' + parts.after);
                      setCaretOffset(unit, parts.before.length + 1);
                      return true;
                    }
                    return false;
                  }

                  function normalizedKey(event) {
                    const key = event.key || '';
                    const code = event.keyCode || event.which || 0;
                    if (key === 'Backspace' || code === 8) return 'Backspace';
                    if (key === 'Delete' || code === 46) return 'Delete';
                    if (key === 'Enter' || key === 'Return' || code === 13) return 'Enter';
                    if (key === ' ' || key === 'Space' || key === 'Spacebar' || code === 32) return 'Space';
                    return key;
                  }

                  function handleShortcutSpace(unit) {
                    if (!unit || !unit.classList.contains('body-copy') || unit.hasAttribute('data-source-exposed')) {
                      return false;
                    }
                    const parts = textAroundCaret(unit);
                    if (parts.after || !/^(#|##|-)$/.test(parts.before)) return false;
                    setPlain(unit, parts.before + ' ');
                    setCaretEnd(unit);
                    normalizeParagraph(unit, false);
                    return true;
                  }

                  function pasteImageFromEvent(event) {
                    const clipboard = event.clipboardData;
                    if (!clipboard || !clipboard.items || !window.javaBridge
                        || !window.javaBridge.pasteImageData) return false;
                    for (let index = 0; index < clipboard.items.length; index++) {
                      const item = clipboard.items[index];
                      const mimeType = item.type || '';
                      if (item.kind !== 'file' || !mimeType.toLowerCase().startsWith('image/')) continue;
                      const file = item.getAsFile();
                      if (!file) continue;
                      event.preventDefault();
                      const reader = new FileReader();
                      reader.onload = function () {
                        window.javaBridge.pasteImageData(String(reader.result || ''), mimeType);
                      };
                      reader.readAsDataURL(file);
                      return true;
                    }
                    return false;
                  }

                  if (plainEditor) {
                    plainEditor.addEventListener('input', function () {
                      resizePlainEditor();
                      markDirty();
                    }, true);
                    window.addEventListener('resize', resizePlainEditor);
                  }

                  if (editor) {
                    document.addEventListener('beforeinput', function (event) {
                      if (suppressBeforeInput) {
                        event.preventDefault();
                        return;
                      }
                      const unit = currentUnit();
                      if (!unit) return;
                      if (unit.matches('.code-block pre')) flattenCode(unit);
                      const inputType = event.inputType || '';
                      if (inputType === 'insertText'
                          && (event.data === ' ' || event.data === '\\u00a0' || event.data === '　')
                          && handleShortcutSpace(unit)) {
                        event.preventDefault();
                        markDirty();
                        return;
                      }
                      if (inputType === 'deleteContentBackward' || inputType === 'deleteContentForward') {
                        const key = inputType === 'deleteContentBackward' ? 'Backspace' : 'Delete';
                        if (handleDeletion(unit, key)) {
                          event.preventDefault();
                          markDirty();
                        }
                        return;
                      }
                      if (inputType === 'insertParagraph' || inputType === 'insertLineBreak') {
                        if (handleEnter(unit)) {
                          event.preventDefault();
                          markDirty();
                        }
                      }
                    }, true);

                    document.addEventListener('input', function () {
                      const unit = currentUnit();
                      if (!unit) { markDirty(); return; }
                      if (unit.classList.contains('body-copy') && unit.hasAttribute('data-source-exposed')) {
                        unit.removeAttribute('data-source-exposed');
                        unit.classList.remove('source-exposed');
                        markDirty();
                        return;
                      }
                      if (unit.matches('.code-block pre')) {
                        const offset = caretOffset(unit);
                        ensureCodeCaretLine(unit, plain(unit));
                        if (offset !== null) setCaretOffset(unit, offset);
                        finishCodeIfClosed(unit);
                      }
                      else if (unit.classList.contains('body-copy')) scheduleNormalize(false);
                      markDirty();
                    }, true);

                    document.addEventListener('compositionend', function () { scheduleNormalize(true); }, true);
                    document.addEventListener('paste', function (event) {
                      if (!pasteImageFromEvent(event)) scheduleNormalize(true);
                    }, true);
                    document.addEventListener('focusout', function (event) {
                      const target = event.target && event.target.closest ? event.target.closest(unitSelector) : null;
                      if (!target) return;
                      if (target.matches('.code-block pre')) highlightCode(target);
                      else refreshLinks(target, false);
                    }, true);

                    document.addEventListener('keydown', function (event) {
                      if (event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return;
                      const unit = currentUnit();
                      if (!unit) return;
                      const key = normalizedKey(event);
                      if (key === 'Space' && handleShortcutSpace(unit)) {
                        event.preventDefault();
                        suppressBeforeInput = true;
                        window.setTimeout(function () { suppressBeforeInput = false; }, 0);
                        markDirty();
                        return;
                      }
                      if (key === 'Backspace' || key === 'Delete') {
                        if (handleDeletion(unit, key)) {
                          event.preventDefault();
                          suppressBeforeInput = true;
                          window.setTimeout(function () { suppressBeforeInput = false; }, 0);
                          markDirty();
                        }
                        return;
                      }
                      if (key === 'Enter') {
                        if (handleEnter(unit)) {
                          event.preventDefault();
                          suppressBeforeInput = true;
                          window.setTimeout(function () { suppressBeforeInput = false; }, 0);
                          markDirty();
                        }
                      }
                    }, true);

                    document.addEventListener('click', function (event) {
                      const link = event.target.closest && event.target.closest('a');
                      if (!link) return;
                      event.preventDefault();
                      if (window.javaBridge) window.javaBridge.openLink(link.href);
                    }, true);
                    document.addEventListener('dblclick', function (event) {
                      const image = event.target.closest && event.target.closest('img');
                      if (image && window.javaBridge) {
                        window.javaBridge.openImage(image.getAttribute('data-source') || image.src);
                        event.preventDefault();
                      }
                    });
                  }

                  window.jnoteInsertImage = function (uri, source) {
                    if (!editor) return;
                    const figure = createImage(uri, source, source.split(/[\\\\/]/).pop());
                    const next = createParagraph('', false);
                    const unit = currentUnit();
                    const block = blockOf(unit);
                    if (block && block.classList.contains('body-copy') && !plain(block)) {
                      replaceBlock(block, [figure, next], next, 0);
                    } else if (block) {
                      block.insertAdjacentElement('afterend', next);
                      block.insertAdjacentElement('afterend', figure);
                      refreshSections();
                      setCaretStart(next);
                    } else {
                      editor.appendChild(figure);
                      editor.appendChild(next);
                      refreshSections();
                      setCaretStart(next);
                    }
                    markDirty();
                  };

                  window.jnoteFind = function (query) {
                    if (!query) return false;
                    return window.find(query, false, false, true, false, true, false);
                  };

                  window.jnoteFocusEditor = function () {
                    if (editor) {
                      const first = editor.querySelector(unitSelector);
                      if (first && document.activeElement === document.body) setCaretStart(first);
                    } else if (plainEditor && document.activeElement === document.body) {
                      plainEditor.focus();
                    }
                  };

                  window.jnoteSerialize = function () {
                    if (plainEditor) {
                      return (plainEditor.value || '').replace(/\\r\\n/g, '\\n').replace(/\\r/g, '\\n');
                    }
                    if (!editor) return '';
                    const lines = [];
                    Array.from(editor.children).forEach(function (block) {
                      if (block.classList.contains('body-copy')) {
                        lines.push(plain(block));
                      } else if (block.classList.contains('heading-one') || block.classList.contains('heading-two')) {
                        lines.push((block.getAttribute('data-marker') || (block.classList.contains('heading-one') ? '# ' : '## ')) + plain(block));
                      } else if (block.classList.contains('todo-list')) {
                        block.querySelectorAll('li').forEach(function (item) {
                          lines.push((item.getAttribute('data-marker') || '- ') + plain(item));
                        });
                      } else if (block.classList.contains('code-block')) {
                        lines.push(block.getAttribute('data-marker') || '```');
                        lines.push(plain(block.querySelector('pre')));
                        lines.push('```');
                      } else if (block.classList.contains('inline-image')) {
                        lines.push(block.getAttribute('data-markdown') || '');
                      }
                    });
                    return lines.join('\\n');
                  };

                  installSmoothWheelScrolling();
                  window.setTimeout(refreshSections, 0);
                  window.setTimeout(resizePlainEditor, 0);
                  document.querySelectorAll('.inline-image img').forEach(attachImageStatus);
                  window.setTimeout(window.jnoteFocusEditor, 0);
                })();
                """;
    }
}
