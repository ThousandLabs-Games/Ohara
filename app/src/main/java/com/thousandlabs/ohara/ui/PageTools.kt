package com.thousandlabs.ohara.ui

import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.data.Site
import com.thousandlabs.ohara.sources.LinkInfo
import com.thousandlabs.ohara.sources.Sources
import org.json.JSONObject

/**
 * Outils ajoutés aux pages de chapitres :
 * - tout en bas, glisser vers le bas et maintenir une seconde : chapitre suivant ; tout en haut, glisser vers le haut : chapitre précédent ;
 * - mode lecture : seulement le texte du chapitre, avec votre police, votre taille et votre thème.
 */
object PageTools {
    data class Theme(val name: String, val bg: String, val fg: String, val dim: String)

    val THEMES = listOf(
        Theme("Clair", "#FFFFFF", "#222222", "#8A8A85"),
        Theme("Sépia", "#F4ECD8", "#4A3B28", "#9A8A70"),
        Theme("Sombre", "#1E1F1E", "#D8D6D0", "#7C7C78"),
        Theme("Noir", "#000000", "#BDBDBD", "#5E5E5E")
    )
    val FONTS = listOf(
        "Simple" to "Roboto, 'Noto Sans', sans-serif",
        "Livre" to "'Noto Serif', 'Droid Serif', Georgia, serif",
        "Étroite" to "'sans-serif-condensed', 'Roboto Condensed', sans-serif",
        "Machine" to "'Courier New', 'Droid Sans Mono', monospace"
    )

    /** Toujours ouvrir les chapitres en mode lecture. */
    var autoReader: Boolean get() = Prefs.getBool("auto_reader", false); set(v) = Prefs.putBool("auto_reader", v)
    /** Glisser au bout de la page pour changer de chapitre. */
    var pullNav: Boolean get() = Prefs.getBool("pull_nav", true); set(v) = Prefs.putBool("pull_nav", v)

    /** Adresses des chapitres voisins quand le site les donne par l'adresse (FanFiction.net). */
    fun neighbours(url: String, info: LinkInfo): Pair<String?, String?> {
        if (info.site == Site.LOCAL && info.chapterIndex != null) {
            val i = info.chapterIndex
            val total = Library.get(info.ficId)?.totalChapters ?: 0
            fun u(k: Int) = "https://${com.thousandlabs.ohara.sources.LocalSource.HOST}/${info.ficId}/$k"
            return (if (i > 0) u(i - 1) else null) to (if (i + 1 < total) u(i + 1) else null)
        }
        if (info.site != Site.FFN || info.chapterIndex == null) return null to null
        val n = info.chapterIndex + 1
        val total = Library.get(info.ficId)?.totalChapters ?: 0
        val base = url.substringBefore("/s/") + "/s/" + info.storyId + "/"
        val prev = if (n > 1) base + (n - 1) + "/" else null
        val next = if (total == 0 || n < total) base + (n + 1) + "/" else null
        return prev to next
    }

    private const val FIND_LINK = """function __ohFind(dir){
var pre=dir>0?window.__ohNextUrl:window.__ohPrevUrl;
var rel=document.querySelector(dir>0?'link[rel=next],a[rel=next]':'link[rel=prev],a[rel=prev]');if(rel&&rel.href)return rel.href;
var re=dir>0?/^\s*(next( chapter| threadmark| page)?|chapitre suivant|suivant\s*[»>›]*|next\s*[»>›]+|[»>›]+)\s*$/i:/^\s*(prev(ious)?( chapter| threadmark| page)?|chapitre précédent|[«<‹]*\s*précédent|[«<‹]+\s*prev(ious)?|[«<‹]+)\s*$/i;
var as=document.querySelectorAll('a[href]');for(var i=0;i<as.length;i++){var a=as[i];var t=(a.innerText||a.textContent||a.getAttribute('aria-label')||a.title||'').trim();
if(t.length<=28&&re.test(t)&&a.href&&a.href.indexOf('javascript')!==0)return a.href;}
var c=document.querySelector(dir>0?'.threadmark-control--next[href],a[class*=next][href]':'.threadmark-control--previous[href],a[class*=prev][href]');if(c&&c.href)return c.href;
return pre||null;}"""

    fun pullScript(prev: String?, next: String?): String = """(function(){
window.__ohNextUrl=${JSONObject.quote(next ?: "")}||null;window.__ohPrevUrl=${JSONObject.quote(prev ?: "")}||null;
if(window.__ohPull)return;window.__ohPull=1;
$FIND_LINK
window.__ohFind=__ohFind;
var ind=null;
function show(txt,bottom,f){if(!ind){ind=document.createElement('div');ind.style.cssText='position:fixed;left:50%;transform:translateX(-50%);z-index:2147483647;background:rgba(28,28,28,.9);color:#fff;font:14px sans-serif;padding:10px 18px;border-radius:22px;pointer-events:none;transition:opacity .15s;min-width:220px;text-align:center';document.documentElement.appendChild(ind);}
ind.style.opacity='1';ind.style.bottom=bottom?'28px':'';ind.style.top=bottom?'':'28px';
ind.innerHTML=txt+'<div style="height:3px;background:rgba(255,255,255,.25);border-radius:2px;margin-top:7px"><div style="height:3px;width:'+Math.round(f*100)+'%;background:#4cc9a0;border-radius:2px"></div></div>';}
function hide(){if(ind)ind.style.opacity='0';}
var y0=null,dir=0,t0=0,target=null,timer=null,gone=false,edgeB=false,edgeT=false,h0=0;
window.addEventListener('pageshow',function(){gone=false;hide();});
function H(){return document.documentElement.scrollHeight;}
function atBottom(){return window.innerHeight+window.scrollY>=H()-4;}
function atTop(){return window.scrollY<=1;}
function stop(){clearInterval(timer);timer=null;}
function cancel(){dir=0;stop();target=null;if(!gone)hide();}
function tick(){if(!dir||!target)return;
/* la page s'est allongée (site qui charge la suite) ou a défilé : ce n'est plus un geste au bout du chapitre */
if(Math.abs(H()-h0)>40||(dir>0&&!atBottom())||(dir<0&&!atTop())){cancel();return;}
var f=Math.min(1,(Date.now()-t0)/1000);show('Chapitre '+(dir>0?'suivant':'précédent')+'…',dir>0,f);
if(f>=1&&!gone){gone=true;stop();show('Chargement du chapitre '+(dir>0?'suivant':'précédent'),dir>0,1);location.href=target;}}
/* le geste ne compte que s'il commence alors que la page est déjà tout en bas (ou tout en haut) */
document.addEventListener('touchstart',function(e){if(e.touches.length!==1){y0=null;return;}y0=e.touches[0].clientY;edgeB=atBottom();edgeT=atTop()&&!edgeB;h0=H();cancel();},{passive:true});
document.addEventListener('touchmove',function(e){if(y0==null||gone)return;if(e.touches.length!==1){y0=null;cancel();return;}var dy=e.touches[0].clientY-y0;
var d=(edgeB&&dy<-90&&atBottom())?1:(edgeT&&dy>90&&atTop())?-1:0;
if(d!==dir){dir=d;t0=Date.now();stop();target=d?__ohFind(d):null;
if(d&&target){timer=setInterval(tick,50);tick();}else if(d){show(d>0?'Pas de chapitre suivant':'Pas de chapitre précédent',d>0,0);}else hide();}
},{passive:true});
document.addEventListener('touchend',function(){y0=null;cancel();},{passive:true});
document.addEventListener('touchcancel',function(){y0=null;cancel();},{passive:true});
})();"""

    fun readerConfig(info: LinkInfo?): JSONObject {
        val t = THEMES.getOrElse(Prefs.readerTheme) { THEMES[0] }
        return JSONObject()
            .put("sel", info?.let { ReadTracker.mainSelector(it) } ?: "")
            .put("bg", t.bg).put("fg", t.fg).put("dim", t.dim)
            .put("font", FONTS.getOrElse(Prefs.readerFont.coerceIn(0, FONTS.lastIndex)) { FONTS[0] }.second)
            .put("size", Prefs.readerSize).put("line", Prefs.readerLine.toDouble()).put("margin", Prefs.readerMargin)
            .put("justify", Prefs.readerJustify)
    }

    /** Met la page en mode lecture (ou applique les nouveaux réglages si elle l'est déjà). */
    fun readerScript(cfg: JSONObject): String = """(function(c){
function css(c){return 'html,body{background:'+c.bg+'!important;color:'+c.fg+'!important;margin:0;padding:0}'+
'body{font-family:'+c.font+';font-size:'+c.size+'px;line-height:'+c.line+';padding:18px '+c.margin+'px 90px!important;text-align:'+(c.justify?'justify':'left')+';word-wrap:break-word}'+
'h1.ohr-t{font-size:1.3em;line-height:1.3;margin:.4em 0 .2em;font-weight:600}.ohr-s{color:'+c.dim+';font-size:.8em;margin-bottom:1.6em}'+
'#storytext p,#storytext div{margin:0 0 .9em}#storytext img{max-width:100%;height:auto}#storytext a{color:inherit}'+
'#storytext blockquote{margin:1em 1.2em;padding-left:.8em;border-left:3px solid '+c.dim+'}#storytext hr{border:none;border-top:1px solid '+c.dim+';margin:1.6em 25%}'+
'nav.ohr-n{display:flex;justify-content:space-between;margin:2.2em 0;font:15px sans-serif}nav.ohr-n a{color:'+c.fg+';opacity:.85;text-decoration:none;padding:10px 6px;border:1px solid '+c.dim+';border-radius:18px;min-width:110px;text-align:center}';}
if(window.__ohReader){var st=document.getElementById('ohr-css');if(st)st.textContent=css(c);return;}
if(window.__ohTr)window.__ohTr.restore();
var best=null,len=0;if(c.sel)document.querySelectorAll(c.sel).forEach(function(e){var l=(e.innerText||'').length;if(l>len){len=l;best=e;}});
if(!best||len<300){var cands=document.querySelectorAll('article,main,[class*=content],[id*=content],[class*=chapter],[id*=chapter],.entry,.post');
cands.forEach(function(e){var l=(e.innerText||'').length;if(l>len){len=l;best=e;}});}
if(!best||len<300){OharaReader.failed();return;}
window.__ohReader=1;
var h=document.querySelector('h1,.p-title-value,.cha-tit h3,b.xcontrast_txt');var title=(h?h.innerText:document.title).trim();
var next=window.__ohFind?window.__ohFind(1):null,prev=window.__ohFind?window.__ohFind(-1):null;
var host=location.hostname.replace(/^(www|m)\./,'');
var html=best.innerHTML;
var nav='<nav class="ohr-n">'+(prev?'<a href="'+prev+'">« Précédent</a>':'<span></span>')+(next?'<a href="'+next+'">Suivant »</a>':'<span></span>')+'</nav>';
document.head.innerHTML='<meta name="viewport" content="width=device-width, initial-scale=1"><title>'+document.title.replace(/</g,'&lt;')+'</title><style id="ohr-css"></style>';
document.body.className='';document.body.removeAttribute('style');
document.body.innerHTML='<h1 class="ohr-t"></h1><div class="ohr-s">'+host+' · mode lecture</div>'+nav+'<div id="storytext" class="storytext cha-words chapter-content bbWrapper"></div>'+nav;
document.querySelector('h1.ohr-t').textContent=title;document.getElementById('storytext').innerHTML=html;
document.getElementById('ohr-css').textContent=css(c);
window.scrollTo(0,0);
})(${cfg});"""

    fun isChapter(info: LinkInfo?, url: String) =
        info != null && (info.chapterIndex != null || info.chapterKey != null || (info.site == Site.SB && url.contains("/reader")))

    fun infoOf(url: String) = Sources.resolve(url)?.second
}
