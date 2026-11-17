package com.thousandlabs.ohara.net

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Place les navigateurs invisibles d'Ohara derrière l'écran de l'appli, en taille réelle.
 * Un navigateur qui n'est attaché à aucune fenêtre ne s'affiche jamais : certaines protections anti-robot
 * (Cloudflare) le détectent et refusent de le laisser passer, alors qu'elles laissent passer le même navigateur affiché.
 * Ici il est vraiment « affiché », mais sous le contenu de l'appli : vous ne le voyez pas.
 */
object HiddenHost {
    /** Fenêtre de l'appli ouverte (renseignée par l'activité principale), sinon null. */
    var container: (() -> ViewGroup?)? = null

    fun attach(web: WebView) {
        val parent = runCatching { container?.invoke() }.getOrNull() ?: return
        if (web.parent != null) return
        runCatching {
            web.alpha = 0f
            web.isFocusable = false
            web.isClickable = false
            web.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            parent.addView(web, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    fun detach(web: WebView) {
        runCatching { (web.parent as? ViewGroup)?.removeView(web) }
    }
}
