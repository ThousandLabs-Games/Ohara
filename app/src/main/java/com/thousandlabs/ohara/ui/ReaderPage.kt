package com.thousandlabs.ohara.ui

import androidx.compose.ui.graphics.Color
import com.thousandlabs.ohara.data.Prefs
import org.json.JSONObject

/** La page HTML du lecteur : mise en forme, surlignages, position exacte, passages masqués. */
object ReaderPage {
    data class Theme(val name: String, val bg: String, val fg: String, val dim: String, val hl: String) {
        val bgColor get() = Color(android.graphics.Color.parseColor(bg))
        val fgColor get() = Color(android.graphics.Color.parseColor(fg))
        val dark get() = name == "Sombre" || name == "Noir"
    }

    val THEMES = listOf(
        Theme("Clair", "#FFFFFF", "#222222", "#8A8A85", "rgba(255,206,84,0.55)"),
        Theme("Sépia", "#F4ECD8", "#4A3B28", "#9A8A70", "rgba(230,170,60,0.45)"),
        Theme("Sombre", "#1E1F1E", "#D8D6D0", "#7C7C78", "rgba(190,150,40,0.45)"),
        Theme("Noir", "#000000", "#BDBDBD", "#5E5E5E", "rgba(160,125,30,0.5)")
    )

    val FONTS = listOf(
        "Serif" to "'Noto Serif', 'Droid Serif', Georgia, serif",
        "Sans" to "Roboto, 'Noto Sans', sans-serif",
        "Condensée" to "'sans-serif-condensed', 'Roboto Condensed', sans-serif",
        "Machine à écrire" to "'Courier New', 'Droid Sans Mono', monospace"
    )

    fun theme(): Theme = THEMES.getOrElse(Prefs.readerTheme) { THEMES[0] }

    /** Variables de style actuelles (appliquées sans recharger le texte). */
    fun style(): JSONObject {
        val t = theme()
        return JSONObject()
            .put("--bg", t.bg).put("--fg", t.fg).put("--dim", t.dim).put("--hl", t.hl)
            .put("--ff", FONTS.getOrElse(Prefs.readerFont) { FONTS[0] }.second)
            .put("--fs", "${Prefs.readerSize}px")
            .put("--lh", Prefs.readerLine.toString())
            .put("--mg", "${Prefs.readerMargin}px")
            .put("--al", if (Prefs.readerJustify) "justify" else "left")
    }

    fun shell(): String = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
:root { --bg:#FBFAF7; --fg:#1E1E1C; --dim:#8A8A85; --hl:rgba(255,206,84,.55); --ff:serif; --fs:18px; --lh:1.6; --mg:20px; --al:justify; }
html, body { background: var(--bg); color: var(--fg); }
body { margin: 0; padding: 76px var(--mg) 160px; font-family: var(--ff); font-size: var(--fs); line-height: var(--lh);
  text-align: var(--al); word-wrap: break-word; overflow-wrap: break-word; -webkit-text-size-adjust: none; hyphens: auto; -webkit-hyphens: auto; }
h1.titre { font-size: 1.25em; line-height: 1.3; text-align: center; margin: 0.6em 0 1.4em; font-weight: 600; }
.texte p { margin: 0 0 0.9em; }
.texte blockquote { margin: 1em 1.2em; padding-left: 0.8em; border-left: 3px solid var(--dim); font-style: italic; }
.texte hr { border: none; border-top: 1px solid var(--dim); margin: 1.6em 25%; }
.texte table { border-collapse: collapse; max-width: 100%; font-size: 0.9em; } .texte td { border: 1px solid var(--dim); padding: 0.2em 0.4em; }
.texte img { max-width: 100%; height: auto; }
.texte a { color: inherit; }
mark.hl { background: var(--hl); color: inherit; border-radius: 2px; padding: 0 1px; }
mark.hl.note { border-bottom: 2px solid #D89A00; }
.ohara-masque.cache { display: none !important; }
.ohara-masque.repere { position: relative; max-height: 2.2em; overflow: hidden; opacity: 0.4; margin: 0 0 0.9em; border-left: 2px dashed var(--dim); padding-left: 0.6em; }
.ohara-masque.repere::before { content: "Passage masqué — touchez pour l'afficher"; display: block; font-family: sans-serif; font-size: 0.7em; color: var(--dim); text-align: left; }
.ohara-masque.repere > * { visibility: hidden; }
.ohara-masque.repere.ouvert { max-height: none; opacity: 0.75; } .ohara-masque.repere.ouvert > * { visibility: visible; }
.ohara-masque.repere.ouvert::before { content: "Passage masqué (touchez pour le replier)"; }
.fin { text-align: center; color: var(--dim); font-family: sans-serif; font-size: 0.75em; margin: 2.5em 0 3em; letter-spacing: 0.08em; }
section.chap + section.chap { border-top: 1px solid var(--dim); padding-top: 2em; }
::selection { background: var(--hl); }
</style></head>
<body><div id="contenu"></div>
<script>
(function(){
  var O = window.Ohara;
  var BLOCKS = '.texte p, .texte li, .texte blockquote, .texte h2, .texte h3, .texte h4, .texte center, .texte > div, .texte td';
  var st = { cont: false, loadingNext: false, lastCh: -1, done: {} };

  function b64(s) {
    var bin = atob(s), a = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) a[i] = bin.charCodeAt(i);
    return new TextDecoder('utf-8').decode(a);
  }

  function blocksOf(sec) { return Array.prototype.slice.call(sec.querySelectorAll(BLOCKS)); }

  /** Surligne un texte dans un conteneur (insensible aux espaces). */
  function mark(root, text, note) {
    if (!text) return false;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var nodes = [], full = '', n;
    while ((n = walker.nextNode())) { nodes.push({ n: n, s: full.length }); full += n.nodeValue; }
    var flat = '', map = [], prevSpace = false;
    for (var i = 0; i < full.length; i++) {
      var c = full[i], sp = /\s/.test(c);
      if (sp) { if (prevSpace) continue; c = ' '; }
      prevSpace = sp; map.push(i); flat += c;
    }
    var t = text.replace(/\s+/g, ' ').trim();
    var at = flat.indexOf(t);
    if (at < 0) return false;
    var s = map[at], e = map[at + t.length - 1] + 1;
    for (var k = nodes.length - 1; k >= 0; k--) {
      var nd = nodes[k], ns = nd.s, ne = nd.s + nd.n.nodeValue.length;
      if (ne <= s || ns >= e) continue;
      var a = Math.max(s, ns) - ns, b = Math.min(e, ne) - ns;
      var node = nd.n;
      if (b < node.nodeValue.length) node.splitText(b);
      var mid = a > 0 ? node.splitText(a) : node;
      if (!mid.nodeValue.trim() && mid.parentNode && mid.parentNode.nodeName === 'TR') continue;
      var m = document.createElement('mark');
      m.className = 'hl' + (note ? ' note' : '');
      if (note) m.title = note;
      mid.parentNode.insertBefore(m, mid);
      m.appendChild(mid);
    }
    return true;
  }

  function section(d) {
    var s = document.createElement('section');
    s.className = 'chap'; s.setAttribute('data-ch', d.ch);
    var h = document.createElement('h1'); h.className = 'titre'; h.textContent = d.title; s.appendChild(h);
    var t = document.createElement('div'); t.className = 'texte'; t.innerHTML = b64(d.html); s.appendChild(t);
    var f = document.createElement('div'); f.className = 'fin'; f.textContent = d.end; s.appendChild(f);
    (d.hl || []).forEach(function (h) { mark(t, h.t, h.n); });
    return s;
  }

  function current() {
    var y = window.scrollY + 64;
    var secs = document.querySelectorAll('section.chap');
    if (!secs.length) return null;
    var cur = secs[0];
    for (var i = 0; i < secs.length; i++) if (secs[i].offsetTop <= y) cur = secs[i];
    var bl = blocksOf(cur), pos = 0;
    for (var j = 0; j < bl.length; j++) {
      var r = bl[j].getBoundingClientRect(), top = r.top + window.scrollY;
      if (top + r.height > y) { pos = j + Math.max(0, Math.min(0.99, (y - top) / Math.max(1, r.height))); break; }
      pos = j + 0.99;
    }
    var bottom = (window.scrollY + window.innerHeight - cur.offsetTop) / Math.max(1, cur.offsetHeight);
    var top2 = (y - cur.offsetTop) / Math.max(1, cur.offsetHeight);
    return { ch: +cur.getAttribute('data-ch'), pos: pos, frac: Math.max(0, Math.min(1, top2)), end: Math.max(0, Math.min(1, bottom)) };
  }

  var tmr = null;
  function report() {
    var c = current(); if (!c) return;
    O.progress(c.ch, c.pos, c.frac, c.end);
    if (st.cont && !st.loadingNext && window.scrollY + window.innerHeight >= document.body.scrollHeight - 2600) {
      st.loadingNext = true; O.needNext(st.lastCh);
    }
  }
  window.addEventListener('scroll', function () { if (tmr) return; tmr = setTimeout(function () { tmr = null; report(); }, 250); }, { passive: true });

  function restore(pos) {
    var sec = document.querySelector('section.chap');
    if (!sec || !(pos > 0)) { window.scrollTo(0, 0); return; }
    var bl = blocksOf(sec), i = Math.floor(pos), b = bl[Math.min(i, bl.length - 1)];
    if (!b) return;
    var r = b.getBoundingClientRect();
    window.scrollTo(0, r.top + window.scrollY + (pos - i) * r.height - 64);
  }

  var selT = null;
  document.addEventListener('selectionchange', function () {
    clearTimeout(selT);
    selT = setTimeout(function () {
      var s = window.getSelection(), t = s ? s.toString().trim() : '';
      if (!t) { O.selection('', -1, ''); return; }
      var n = s.anchorNode, el = n && (n.nodeType === 1 ? n : n.parentElement);
      var sec = el && el.closest('section.chap'), blk = el && el.closest(BLOCKS);
      O.selection(t, sec ? +sec.getAttribute('data-ch') : -1, blk ? blk.textContent : t);
    }, 350);
  });

  document.addEventListener('click', function (e) {
    var sel = window.getSelection();
    if (sel && sel.toString().trim()) return;
    var m = e.target.closest && e.target.closest('.ohara-masque');
    if (m) { m.classList.toggle('ouvert'); return; }
    if (e.target.closest && e.target.closest('a')) { e.preventDefault(); }
    var y = e.clientY / window.innerHeight;
    if (y < 0.2) page(-1); else if (y > 0.8) page(1); else O.tap();
  }, true);

  function page(dir) { window.scrollBy({ top: dir * (window.innerHeight - 90), behavior: 'smooth' }); }

  // ---- Traduction en place, l'écran visible d'abord ----
  var tr = { on: false, nodes: [], busy: false };
  function trKey(e) { var t = e.getBoundingClientRect().top; return t < -40 ? 100000 - t : t; }
  function trCollect() {
    if (!tr.on || tr.busy) return;
    var els = Array.prototype.slice.call(document.querySelectorAll(BLOCKS + ', h1.titre')).filter(function (e) {
      return !e.__trq && e.textContent.trim().length > 1 && /[A-Za-z]{2}/.test(e.textContent) &&
        !e.querySelector('p,li,blockquote,td') && !(e.closest('.ohara-masque.cache'));
    });
    if (!els.length) { O.trDone(); return; }
    els.sort(function (a, b) { return trKey(a) - trKey(b); });
    var batch = els.slice(0, 40).map(function (e) { e.__trq = 1; tr.nodes.push(e); return { i: tr.nodes.length - 1, t: e.textContent.trim() }; });
    tr.busy = true;
    O.trTexts(JSON.stringify(batch), els.length);
  }
  window.addEventListener('scroll', function () { if (tr.on && !tr.busy) { clearTimeout(tr.t); tr.t = setTimeout(trCollect, 400); } }, { passive: true });

  window.ohara = {
    trOn: function () { tr.on = true; tr.busy = false; trCollect(); },
    trNext: function () { tr.busy = false; trCollect(); },
    trSet: function (i, t) { var e = tr.nodes[i]; if (!e) return; if (e.__orig == null) e.__orig = e.innerHTML; e.textContent = t; },
    trOff: function () {
      tr.on = false; tr.busy = false;
      tr.nodes.forEach(function (e) { if (e.__orig != null) { e.innerHTML = e.__orig; e.__orig = null; } e.__trq = 0; });
      tr.nodes = [];
    },
    style: function (vars) { for (var k in vars) document.documentElement.style.setProperty(k, vars[k]); },
    show: function (d, pos, cont) {
      st.cont = cont; st.loadingNext = false; st.lastCh = d.ch;
      tr.on = false; tr.busy = false; tr.nodes = [];
      var c = document.getElementById('contenu'); c.innerHTML = '';
      c.appendChild(section(d));
      requestAnimationFrame(function () { restore(pos); setTimeout(report, 300); });
    },
    append: function (d) {
      if (!d) { st.loadingNext = false; return; }
      if (document.querySelector('section.chap[data-ch="' + d.ch + '"]')) { st.loadingNext = false; return; }
      document.getElementById('contenu').appendChild(section(d));
      st.lastCh = d.ch; st.loadingNext = false;
      if (tr.on && !tr.busy) setTimeout(trCollect, 100);
    },
    endOfBook: function () { st.loadingNext = true; },
    highlight: function (ch, text, note) {
      var t = document.querySelector('section.chap[data-ch="' + ch + '"] .texte');
      if (t) mark(t, text, note);
      var s = window.getSelection(); if (s) s.removeAllRanges();
    },
    clearSelection: function () { var s = window.getSelection(); if (s) s.removeAllRanges(); },
    page: page,
    scrollToEnd: function () { window.scrollTo(0, document.body.scrollHeight); }
  };
  O.ready();
})();
</script></body></html>"""
}
