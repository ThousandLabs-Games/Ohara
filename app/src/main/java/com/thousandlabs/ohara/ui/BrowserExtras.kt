package com.thousandlabs.ohara.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.translate.Translate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * FanFiction.net et WebNovel sont ouverts dans leur version ordinateur (la seule qu'Ohara sait lire).
 * Cette mise en page les ramène à la largeur du téléphone, pour les lire comme la version mobile.
 */
object PageFit {
    var enabled: Boolean get() = Prefs.getBool("mobile_fit", true); set(v) = Prefs.putBool("mobile_fit", v)

    private const val CSS = "html,body{min-width:0!important;max-width:100%!important;overflow-x:hidden!important;}" +
        "body *{max-width:100%!important;box-sizing:border-box;}" +
        "#content_wrapper,#content_wrapper_inner,.maxwidth,#top,#top>div,.zmenu,#zmenu,.lc-wrapper,.lc{width:auto!important;min-width:0!important;margin-left:0!important;margin-right:0!important;float:none!important;}" +
        "#content_wrapper_inner{padding:6px 12px!important;}" +
        "#storytext,#storytextp{font-size:17px!important;line-height:1.55!important;width:auto!important;padding:0!important;}" +
        "#profile_top{font-size:15px!important;line-height:1.45!important;}" +
        "#profile_top img.cimage{float:left!important;max-width:30%!important;height:auto!important;margin:0 10px 6px 0!important;}" +
        "img{height:auto;}table{display:block;overflow-x:auto;}" +
        "select{max-width:60%!important;}"

    val js: String = "(function(){try{var m=document.querySelector('meta[name=viewport]');" +
        "if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}" +
        "m.content='width=device-width, initial-scale=1';" +
        "if(!document.getElementById('ohara-fit')){var s=document.createElement('style');s.id='ohara-fit';s.textContent=" + JSONObject.quote(CSS) + ";" +
        "(document.head||document.documentElement).appendChild(s);}}catch(e){}})();"
}

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
    private var web: WebView? = null

    /** Nouvelle page : le travail en cours pour l'ancienne est abandonné. */
    fun newPage() { generation++ }

    const val JS = """(function(){
if(window.__ohTr){window.__ohTr.scan();return;}
var nodes=[];
function leafs(){var out=[];var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
if(n.__oh||!n.nodeValue||n.nodeValue.trim().length<2)return 2;if(!/[A-Za-z]{2}/.test(n.nodeValue))return 2;
var p=n.parentElement;if(!p||p.closest('script,style,noscript,textarea,code,pre,select,option'))return 2;return 1;}});
var n;while((n=w.nextNode()))out.push(n);return out;}
function scan(){if(!document.body)return;var list=leafs();if(!list.length)return;var batch=[];
list.slice(0,400).forEach(function(n){n.__oh=1;nodes.push(n);batch.push({i:nodes.length-1,t:n.nodeValue.trim()});});
OharaTr.texts(JSON.stringify(batch));}
window.__ohTr={scan:scan,
set:function(i,t){var n=nodes[i];if(!n)return;var v=n.nodeValue;var a=v.match(/^\s*/)[0],b=v.match(/\s*$/)[0];if(n.__orig==null)n.__orig=v;n.nodeValue=a+t+b;},
restore:function(){nodes.forEach(function(n){if(n.__orig!=null){n.nodeValue=n.__orig;n.__orig=null;}n.__oh=0;});nodes=[];clearInterval(window.__ohTrT);window.__ohTr=null;}};
scan();window.__ohTrT=setInterval(scan,2500);
})();"""

    fun attach(w: WebView) {
        web = w
        w.addJavascriptInterface(object {
            private val main = Handler(Looper.getMainLooper())
            @JavascriptInterface fun texts(json: String) { main.post { work(json) } }
        }, "OharaTr")
    }

    fun inject(w: WebView) { if (Translate.enabled) w.evaluateJavascript(JS, null) }

    fun restore(w: WebView) {
        generation++
        status = null
        w.evaluateJavascript("if(window.__ohTr)window.__ohTr.restore()", null)
    }

    private fun work(json: String) {
        val gen = generation
        val items = runCatching { JSONArray(json) }.getOrNull() ?: return
        AppScope.launch {
            mutex.withLock {
                if (gen != generation || !Translate.enabled) return@withLock
                try {
                    Translate.ensureModel { status = it }
                    status = "Traduction de la page…"
                    for (k in 0 until items.length()) {
                        if (gen != generation || !Translate.enabled) break
                        val o = items.getJSONObject(k)
                        val src = o.optString("t")
                        val fr = cache[src] ?: Translate.text(src).also { cache[src] = it }
                        web?.evaluateJavascript("if(window.__ohTr)window.__ohTr.set(${o.optInt("i")},${JSONObject.quote(fr)})", null)
                    }
                } catch (e: Exception) {
                    Toast.show("Traduction impossible : ${e.message ?: "pas de connexion pour télécharger le modèle"}.")
                } finally {
                    status = null
                }
            }
        }
    }
}
