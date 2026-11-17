package com.thousandlabs.ohara.ui

import com.thousandlabs.ohara.data.Fic
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.discover.Masking
import com.thousandlabs.ohara.sources.LinkInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Masquage dans les pages du navigateur.
 * Appui long sur une phrase › « Masquer » : la phrase (ou sa ligne) disparaît tout de suite, et aussi dans les chapitres suivants
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
    fun rulesFor(info: LinkInfo?): List<Masking.Rule> {
        val fic: Fic? = info?.let { Library.get(it.ficId) }
        return Masking.rules().filter { r ->
            when (r.scope) {
                Masking.Scope.ALL -> true
                Masking.Scope.FIC -> info != null && r.target == info.ficId
                Masking.Scope.AUTHOR -> fic != null && r.target == Masking.authorKey(fic)
            }
        }
    }

    fun config(info: LinkInfo?): JSONObject = JSONObject()
        .put("rules", JSONArray(rulesFor(info).map { it.phrase }))
        .put("promo", Masking.autoPromo && info != null)
        .put("an", Masking.authorNotes && info != null)
        .put("marker", Masking.showMarker)
        .put("re", PROMO)
        .put("sel", info?.let { ReadTracker.mainSelector(it) } ?: "")

    /**
     * Découpe commune au masquage et à la sélection : un bloc est coupé en lignes (aux <br> et aux éléments de bloc),
     * chaque ligne en phrases, avec leurs positions, pour ne cacher que ce qui correspond vraiment.
     */
    private const val SEG_JS = """
var BLK=/^(P|DIV|LI|BLOCKQUOTE|CENTER|TD|TR|TABLE|UL|OL|H[1-6]|HR|SECTION|ARTICLE)$/;
function segs(b){var out=[],cur={nodes:[],text:''};function push(){if(cur.text.trim())out.push(cur);cur={nodes:[],text:''};}
(function walk(n){for(var c=n.firstChild;c;c=c.nextSibling){if(c.nodeType===3){cur.nodes.push({n:c,o:cur.text.length});cur.text+=c.nodeValue;}
else if(c.nodeType===1){if(c.hasAttribute('data-oh-m')||c.hasAttribute('data-oh-shown'))continue;if(c.tagName==='BR'){push();}else if(BLK.test(c.tagName)){push();walk(c);push();}else if(c.tagName!=='SCRIPT'&&c.tagName!=='STYLE')walk(c);}}})(b);push();return out;}
function sentences(t){var r=[],re=/[^.!?…]+(?:[.!?…]+["'”’»)]*|$)/g,m;while((m=re.exec(t))){if(m[0].length===0){re.lastIndex++;continue;}if(m[0].trim())r.push({s:m.index,e:m.index+m[0].length,t:m[0]});}return r;}
function rangeOf(seg,s,e){var r=document.createRange(),st=null,en=null;for(var i=0;i<seg.nodes.length;i++){var x=seg.nodes[i],L=x.n.nodeValue.length;
if(st===null&&s<x.o+L){r.setStart(x.n,Math.max(0,s-x.o));st=1;}if(st!==null&&e<=x.o+L){r.setEnd(x.n,Math.max(0,e-x.o));en=1;break;}}
if(st===null)return null;if(en===null){var l=seg.nodes[seg.nodes.length-1];r.setEnd(l.n,l.n.nodeValue.length);}return r;}
"""

    fun script(cfg: JSONObject): String = """(function(cfg){
window.__ohMaskCfg=cfg;
$SEG_JS
function norm(s){return s.toLowerCase().replace(/[‘’“”]/g,"'").replace(/\d+/g,'#').replace(/[^\p{L}#']+/gu,' ').replace(/\s+/g,' ').trim();}
function words(s){return s.split(' ').filter(function(w){return w.length>=3;});}
/* Un texte ne correspond à une règle que s'il a à peu près sa taille : une phrase ne fait jamais masquer tout un chapitre. */
function fits(n,p){return n.length<=Math.max(p.length*2.5,p.length+160);}
function match(n,p){p=norm(p);if(!p||!fits(n,p))return false;if(n.indexOf(p)>=0)return true;var pw=words(p);if(pw.length<4)return false;
var set={};words(n).forEach(function(w){set[w]=1;});var c=0;pw.forEach(function(w){if(set[w])c++;});return c>=Math.max(3,Math.floor(pw.length*0.75));}
var AN=/^\s*(a\/n|an:|a\.n\.|author'?s'? notes?|note de l'auteur)\b/i;
function root(c){if(!c.sel)return document.body;var best=null,len=0;document.querySelectorAll(c.sel).forEach(function(e){var l=(e.innerText||'').length;if(l>len){len=l;best=e;}});return best||document.body;}
function blocks(r){return Array.prototype.filter.call(r.querySelectorAll('p,li,blockquote,center,h2,h3,h4,td,div'),function(e){return !e.closest('[data-oh-m]')&&!e.querySelector('p,li,blockquote,div,td,center')&&(e.textContent||'').trim().length>0;});}
function marker(e){if(!window.__ohMaskCfg.marker)return;var m=document.createElement('div');m.className='oh-mk';m.textContent='Passage masqué — touchez pour l\'afficher';
m.style.cssText='font:12px sans-serif;opacity:.55;border-left:2px dashed currentColor;padding:2px 8px;margin:6px 0;cursor:pointer';
m.onclick=function(){e.style.display='';e.setAttribute('data-oh-shown','1');m.remove();};e.parentNode.insertBefore(m,e);}
function hide(e){e.setAttribute('data-oh-m','1');marker(e);e.style.setProperty('display','none','important');}
function hideRange(r){if(!r)return;var sp=document.createElement('span');sp.setAttribute('data-oh-m','1');
try{sp.appendChild(r.extractContents());r.insertNode(sp);}catch(x){return;}sp.style.setProperty('display','none','important');marker(sp);
var nx=sp.nextSibling;if(nx&&nx.nodeType===1&&nx.tagName==='BR'){nx.setAttribute('data-oh-br','1');nx.style.display='none';}}
function bad(t,n,c,re){return c.rules.some(function(p){return match(n,p);})||(re&&t.length<900&&re.test(t));}
function scan(){var c=window.__ohMaskCfg;if(!document.body||c.off)return;var re=c.promo?new RegExp(c.re,'i'):null;var r=root(c);var list=blocks(r);
list.forEach(function(e,i){if(e.getAttribute('data-oh-m')||e.getAttribute('data-oh-shown'))return;var t=e.textContent||'';var n=norm(t);
if(t.length<=900&&(bad(t,n,c,re)||(c.an&&AN.test(t)&&(i<6||i>list.length-10)))){hide(e);return;}
/* bloc long (texte coupé par des sauts de ligne) : on cherche ligne par ligne, puis phrase par phrase */
var ss=segs(e);for(var k=ss.length-1;k>=0;k--){var sg=ss[k],st=sg.text,sn=norm(st);if(!sn)continue;
if(st.length<=900&&bad(st,sn,c,re)){hideRange(rangeOf(sg,0,st.length));continue;}
if(!c.rules.length)continue;var ph=sentences(st);for(var j=ph.length-1;j>=0;j--){var q=ph[j];if(c.rules.some(function(p){return match(norm(q.t),p);}))hideRange(rangeOf(sg,q.s,q.e));}}});}
window.__ohMaskScan=scan;scan();
if(!window.__ohMaskT){window.__ohMaskT=setInterval(function(){if(window.__ohMaskScan)window.__ohMaskScan();},2500);}
})(${cfg});"""

    /** Réaffiche tout ce qui est masqué sur la page ouverte, sans changer les règles. */
    const val SHOW_ALL_JS = """(function(){if(window.__ohMaskCfg)window.__ohMaskCfg.off=true;
document.querySelectorAll('[data-oh-m],[data-oh-br]').forEach(function(e){e.style.display='';e.setAttribute('data-oh-shown','1');e.removeAttribute('data-oh-m');});
document.querySelectorAll('.oh-mk').forEach(function(m){m.remove();});})();"""

    /**
     * Script de sélection : signale le texte choisi et la phrase (ou la ligne courte) qui le contient,
     * jamais tout le bloc : sur FanFiction.net, un chapitre entier peut être un seul bloc.
     */
    const val SELECTION_JS = """(function(){
if(window.__ohSel)return;window.__ohSel=1;var t=null;
$SEG_JS
function around(s){var n=s.anchorNode;if(!n)return '';var el=n.nodeType===1?n:n.parentElement;var b=el&&el.closest('p,li,blockquote,center,td,div');if(!b)return '';
var ss=segs(b);for(var k=0;k<ss.length;k++){var sg=ss[k];for(var i=0;i<sg.nodes.length;i++){if(sg.nodes[i].n===n){
var off=sg.nodes[i].o+s.anchorOffset,line=sg.text.trim();if(line.length<=320)return line;
var ph=sentences(sg.text);for(var j=0;j<ph.length;j++){if(off>=ph[j].s&&off<=ph[j].e)return ph[j].t.trim().slice(0,400);}return '';}}}
var bt=(b.textContent||'').trim();return bt.length<=320?bt:'';}
document.addEventListener('selectionchange',function(){clearTimeout(t);t=setTimeout(function(){
var s=window.getSelection();var txt=s?s.toString().trim():'';if(!txt){OharaSel.selection('','');return;}
var p='';try{p=around(s);}catch(x){}OharaSel.selection(txt,p||txt);},350);});
})();"""

    /** Masque tout de suite ce passage dans la page ouverte. */
    fun hideNowJs(paragraph: String): String =
        "(function(p){if(!window.__ohMaskCfg)return;window.__ohMaskCfg.off=false;window.__ohMaskCfg.rules.push(p);if(window.__ohMaskScan)window.__ohMaskScan();" +
            "var s=window.getSelection();if(s)s.removeAllRanges();})(${JSONObject.quote(paragraph)})"
}
