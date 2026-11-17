package com.thousandlabs.ohara

import android.app.Application
import com.thousandlabs.ohara.data.Library
import com.thousandlabs.ohara.data.Prefs
import com.thousandlabs.ohara.discover.Discover
import com.thousandlabs.ohara.discover.FandomIndex
import com.thousandlabs.ohara.discover.HiddenLog
import com.thousandlabs.ohara.download.Downloads
import com.thousandlabs.ohara.download.Notify
import com.thousandlabs.ohara.download.UpdateChecker
import com.thousandlabs.ohara.net.Http
import com.thousandlabs.ohara.net.WebFetcher
import com.thousandlabs.ohara.privacy.AdBlocker

class OharaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        HiddenLog.init(Library.dataDir())
        com.thousandlabs.ohara.data.History.init(Library.dataDir())
        FandomIndex.init(Library.dataDir())
        Discover.init(Library.dataDir())
        Http.init(this)
        WebFetcher.init(this)
        com.thousandlabs.ohara.net.ApiBrowser.init(this)
        Downloads.init(this)
        Notify.init(this)
        AdBlocker.init(this)
        runCatching { UpdateChecker.schedule(this) }
        runCatching { IconSwitcher.schedule(this) }
    }
}
