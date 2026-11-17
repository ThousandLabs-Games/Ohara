package com.thousandlabs.ohara.ui

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.sources.LinkInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Masquage dans les pages du navigateur.
 * Appui long sur une phrase › « Masquer » : le paragraphe disparaît tout de suite, et aussi dans les chapitres suivants
 * (règle par texte, tolérante aux nombres et aux petites différences). Les promos et notes d'auteur sont masquées d'office.
 */
object PageMask {
    /** Expression des promos, identique à celle du masquage des chapitres gardés. */
    const val PROMO = "patreon|ko-?fi\\.com|\\bko-?fi\\b|buymeacoffee|buy me a coffee|subscribestar|discord\\.gg|join (my|our|the) discord|" +
        "advanced? chapters?|chapters? ahead|early access|chapters? in advance|support me (on|at)|\\btip jar\\b|paypal\\.me|" +
        "\\bpower stones?\\b|\\bgolden tickets?\\b|vote with your|p\\s*a\\s*t\\s*r\\s*e\\s*o\\s*n|pat-?reon|\\+ ?\\d+ chapters|" +
        "read (up to )?\\d+ chapters|add (it|this|the book|this book) to (your )?(library|collection)|don'?t forget to (review|vote|comment|like)|" +
        "please (review|vote|comment)|leave a (review|comment)|reviews? (are|is) appreciated|\\bcheck out my\\b|my other (stories|fics|works)"

    /** Règles qui s'appliquent à cette page (fic connue : règles de la fic et de son auteur ; sinon règles « partout »). */
    fun config(info: LinkInfo?): JSONObject {
        val fic: Fic? = info?.let { Library.get(it.ficId) }
        val rules = Masking.rules().filter { r ->
            when (r.scope) {
                Masking.Scope.ALL -> true
                Masking.Scope.FIC -> info != null && r.target == info.ficId
                Masking.Scope.AUTHOR -> fic != null && r.target == Masking.authorKey(fic)
            }
        }
        return JSONObject()
            .put("rules", JSONArray(rules.map { it.phrase }))
            .put("promo", Masking.autoPromo && info != null)
            .put("an", Masking.authorNotes && info != null)
            .put("marker", Masking.showMarker)
            .put("re", PROMO)
            .put("sel", info?.let { ReadTracker.mainSelector(it) } ?: "")
    }

    fun script(cfg: JSONObject): String = """(function(cfg){
window.__ohMaskCfg=cfg;
function norm(s){return s.toLowerCase().replace(/[‘’“”]/g,"'").replace(/\d+/g,'#').replace(/[^\p{L}#']+/gu,' ').replace(/\s+/g,' ').trim();}
function words(s){return s.split(' ').filter(function(w){return w.length>=3;});}
function match(n,p){p=norm(p);if(!p)return false;if(n.indexOf(p)>=0)return true;var pw=words(p);if(pw.length<4)return false;
var set={};words(n).forEach(function(w){set[w]=1;});var c=0;pw.forEach(function(w){if(set[w])c++;});return c>=Math.max(3,Math.floor(pw.length*0.75));}
var AN=/^\s*(a\/n|an:|a\.n\.|author'?s'? notes?|note de l'auteur)\b/i;
function root(c){if(!c.sel)return document.body;var best=null,len=0;document.querySelectorAll(c.sel).forEach(function(e){var l=(e.innerText||'').length;if(l>len){len=l;best=e;}});return best||document.body;}
function blocks(r){return Array.prototype.filter.call(r.querySelectorAll('p,li,blockquote,center,h2,h3,h4,td,div'),function(e){return !e.querySelector('p,li,blockquote,div,td,center')&&(e.textContent||'').trim().length>0;});}
function hide(e){e.setAttribute('data-oh-m','1');
if(window.__ohMaskCfg.marker){var m=document.createElement('div');m.textContent='Passage masqué — touchez pour l\'afficher';
m.style.cssText='font:12px sans-serif;opacity:.55;border-left:2px dashed currentColor;padding:2px 8px;margin:6px 0;cursor:pointer';
m.onclick=function(){e.style.display='';m.remove();};e.parentNode.insertBefore(m,e);}
e.style.setProperty('display','none','important');}
function scan(){var c=window.__ohMaskCfg;if(!document.body)return;var re=c.promo?new RegExp(c.re,'i'):null;var r=root(c);var list=blocks(r);
list.forEach(function(e,i){if(e.getAttribute('data-oh-m'))return;var t=e.textContent||'';if(t.length>3000)return;var n=norm(t);
if(c.rules.some(function(p){return match(n,p);})||(re&&t.length<900&&re.test(t))||(c.an&&AN.test(t)&&(i<6||i>list.length-10)))hide(e);});}
window.__ohMaskScan=scan;scan();
if(!window.__ohMaskT){window.__ohMaskT=setInterval(function(){if(window.__ohMaskScan)window.__ohMaskScan();},2500);}
})(${cfg});"""

    /** Script de sélection : signale le texte choisi et le paragraphe qui le contient. */
    const val SELECTION_JS = """(function(){
if(window.__ohSel)return;window.__ohSel=1;var t=null;
document.addEventListener('selectionchange',function(){clearTimeout(t);t=setTimeout(function(){
var s=window.getSelection();var txt=s?s.toString().trim():'';if(!txt){OharaSel.selection('','');return;}
var n=s.anchorNode;var el=n&&(n.nodeType===1?n:n.parentElement);var b=el&&el.closest('p,li,blockquote,center,td,div');
OharaSel.selection(txt,b?(b.textContent||'').trim().slice(0,1500):txt);},350);});
})();"""

    /** Masque tout de suite ce paragraphe dans la page ouverte. */
    fun hideNowJs(paragraph: String): String =
        "(function(p){if(!window.__ohMaskCfg)return;window.__ohMaskCfg.rules.push(p);if(window.__ohMaskScan)window.__ohMaskScan();" +
            "var s=window.getSelection();if(s)s.removeAllRanges();})(${JSONObject.quote(paragraph)})"
}
