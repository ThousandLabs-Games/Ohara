package com.thousandlabs.ohara.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.translate.Names
import com.thousandlabs.ohara.translate.Translate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Traduction des pages du navigateur, comme dans les navigateurs habituels : un bouton, et toutes les pages
 * s'affichent en français jusqu'à ce qu'on le désactive. La traduction se fait sur le téléphone
 * (le modèle est téléchargé une seule fois) : rien n'est envoyé à un service extérieur.
 */
object PageTranslator {
    /** Message affiché pendant le travail (téléchargement du modèle, traduction de la page). */
    var status by mutableStateOf<String?>(null)

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4000
    }
    @Volatile private var generation = 0

    /** Nouvelle page : le travail en cours pour l'ancienne est abandonné. */
    fun newPage() { generation++ }

    /*
     * La page est lue par phrases entières, pas par morceaux de texte : un mot en italique ou en gras au milieu
     * d'une phrase coupait la phrase en trois bouts traduits chacun de son côté (mots mal placés, sens perdu).
     * Chaque ligne d'un paragraphe (coupée par un retour à la ligne) est traduite d'un bloc ; la traduction s'écrit
     * à la place du texte d'origine et la mise en forme interne à la phrase est simplifiée.
     * Ce qui est à l'écran passe en premier.
     */
    const val JS = """(function(){
if(window.__ohTr){window.__ohTr.scan();return;}
var units=[];
var INL={A:1,ABBR:1,B:1,BDI:1,BDO:1,CITE:1,DFN:1,EM:1,FONT:1,I:1,KBD:1,MARK:1,Q:1,S:1,SAMP:1,SMALL:1,SPAN:1,STRIKE:1,STRONG:1,SUB:1,SUP:1,TIME:1,U:1,VAR:1,LABEL:1,INS:1,DEL:1,NOBR:1,WBR:1};
var SKIP='script,style,noscript,textarea,code,pre,select,option,button,input';
function blockOf(n){var p=n.parentElement;while(p&&INL[p.tagName]&&p.parentElement)p=p.parentElement;return p;}
function ok(n){if(n.__oh||!n.nodeValue||!n.nodeValue.trim())return false;var p=n.parentElement;return !!p&&!p.closest(SKIP);}
function unitsOf(b){var res=[],cur=[];var w=document.createTreeWalker(b,5,null);var n;
while((n=w.nextNode())){if(n.nodeType===1){if(n.tagName==='BR'||!INL[n.tagName]){if(cur.length)res.push(cur);cur=[];}continue;}
if(blockOf(n)!==b)continue;if(ok(n))cur.push(n);}
if(cur.length)res.push(cur);return res;}
function scan(){if(!document.body)return;var w=document.createTreeWalker(document.body,4,null);var n,blocks=[];
while((n=w.nextNode())){if(!ok(n))continue;var b=blockOf(n);if(b&&!b.__ohS){b.__ohS=1;blocks.push(b);}}
var fresh=[];blocks.forEach(function(b){b.__ohS=0;unitsOf(b).forEach(function(ns){ns.forEach(function(x){x.__oh=1;});
var t=ns.map(function(x){return x.nodeValue;}).join('').replace(/\s+/g,' ').trim();
if(t.length<2||!/[A-Za-z]{2}/.test(t))return;fresh.push({n:ns,b:b,t:t});});});
if(!fresh.length)return;var h=window.innerHeight||800;
fresh.forEach(function(u){var r=u.b.getBoundingClientRect();u.k=(r.bottom>0&&r.top<h)?r.top-100000:(r.top<0?h-r.top:r.top);});
fresh.sort(function(x,y){return x.k-y.k;});
var batch=[];fresh.slice(0,400).forEach(function(u){units.push(u);batch.push({i:units.length-1,t:u.t});});
fresh.slice(400).forEach(function(u){u.n.forEach(function(x){x.__oh=0;});});
OharaTr.texts(JSON.stringify(batch));}
window.__ohTr={scan:scan,
set:function(i,t){var u=units[i];if(!u)return;var ns=u.n;var tg=null;
for(var k=0;k<ns.length;k++){if(ns[k].parentElement===u.b){tg=ns[k];break;}}if(!tg)tg=ns[0];
var a=ns[0].__orig!=null?ns[0].__orig:ns[0].nodeValue,z=ns[ns.length-1].__orig!=null?ns[ns.length-1].__orig:ns[ns.length-1].nodeValue;
var lead=a.match(/^\s*/)[0],trail=z.match(/\s*$/)[0];
ns.forEach(function(x){if(x.__orig==null)x.__orig=x.nodeValue;x.nodeValue=(x===tg)?lead+t+trail:'';});},
restore:function(){units.forEach(function(u){u.n.forEach(function(x){if(x.__orig!=null){x.nodeValue=x.__orig;x.__orig=null;}x.__oh=0;});});units=[];clearInterval(window.__ohTrT);window.__ohTr=null;}};
scan();window.__ohTrT=setInterval(scan,2500);
})();"""

    fun attach(w: WebView) {
        w.addJavascriptInterface(object {
            private val main = Handler(Looper.getMainLooper())
            @JavascriptInterface fun texts(json: String) { main.post { work(json, w) } }
        }, "OharaTr")
    }

    fun inject(w: WebView) { if (Translate.enabled) w.evaluateJavascript(JS, null) }

    /** Retraduit la page ouverte (après un changement dans les noms à ne pas traduire). */
    fun retranslate(w: WebView) {
        Translate.namesChanged()
        synchronized(cache) { cache.clear() }
        if (!Translate.enabled) return
        restore(w)
        inject(w)
    }

    fun restore(w: WebView) {
        generation++
        status = null
        w.evaluateJavascript("if(window.__ohTr)window.__ohTr.restore()", null)
    }

    private fun show(web: WebView, i: Int, fr: String) = web.post {
        web.evaluateJavascript("if(window.__ohTr)window.__ohTr.set($i,${JSONObject.quote(fr)})", null)
    }

    private fun work(json: String, web: WebView) {
        val gen = generation
        val items = runCatching { JSONArray(json) }.getOrNull() ?: return
        AppScope.launch {
            mutex.withLock {
                if (gen != generation || !Translate.enabled) return@withLock
                try {
                    Translate.prepare { status = it }
                    status = "Traduction de la page…"
                    val sig = Translate.signature()
                    val all = List(items.length()) { items.getJSONObject(it) }
                    // déjà traduits : affichés tout de suite
                    val todo = all.filter { o -> synchronized(cache) { cache[sig + "\u0000" + o.optString("t")] }?.let { fr -> show(web, o.optInt("i"), fr); false } ?: true }
                    // le haut de la page d'abord (petit paquet), puis le reste par paquets envoyés en même temps
                    val groups = ArrayList<List<JSONObject>>()
                    var cur = ArrayList<JSONObject>(); var size = 0
                    for (o in todo) {
                        val limit = if (groups.isEmpty()) 600 else 3500
                        if (cur.isNotEmpty() && size + o.optString("t").length > limit) { groups += cur; cur = ArrayList(); size = 0 }
                        cur += o; size += o.optString("t").length
                    }
                    if (cur.isNotEmpty()) groups += cur
                    suspend fun doGroup(g: List<JSONObject>) {
                        if (gen != generation || !Translate.enabled) return
                        val src = g.map { it.optString("t") }
                        Translate.texts(src).forEachIndexed { k, fr -> synchronized(cache) { cache[sig + "\u0000" + src[k]] = fr }; show(web, g[k].optInt("i"), fr) }
                    }
                    groups.firstOrNull()?.let { doGroup(it) }
                    if (Translate.online) kotlinx.coroutines.coroutineScope {
                        groups.drop(1).map { g -> launch { doGroup(g) } }
                    } else groups.drop(1).forEach { doGroup(it) }
                } catch (e: Exception) {
                    Toast.show("Traduction impossible : ${e.message ?: "pas de connexion pour télécharger le modèle"}.")
                } finally {
                    status = null
                }
            }
        }
    }
}
