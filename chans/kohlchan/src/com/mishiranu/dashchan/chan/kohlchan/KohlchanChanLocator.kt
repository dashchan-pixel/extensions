package com.mishiranu.dashchan.chan.kohlchan

import android.net.Uri
import chan.content.LynxchanChanLocator

class KohlchanChanLocator : LynxchanChanLocator() {
    init {
        addChanHost("kohlchan.net")
        addConvertableChanHost("www.kohlchan.net")
        addConvertableChanHost("kohlchan.mett.ru")
        addConvertableChanHost("nocsp.kohlchan.net")
        addConvertableChanHost("kohl.chan")
        addConvertableChanHost("kohlchan7cwtdwfuicqhxgqx4k47bsvlt2wn5eduzovntrzvonv4cqyd.onion")
        addConvertableChanHost("fastkohlt5rxcxtl5no7k3efmahlt7mafry7be6yvxdovekhq2hdnwqd.onion")
        setHttpsMode(HttpsMode.HTTPS_ONLY)
    }

    /**
     * The board's own quote links point at `#<number>`, but its reply form links at `#q<number>`
     * to open the editor as well. Both denote the same post.
     */
    override fun getPostNumber(uri: Uri): String? = uri.fragment?.removePrefix("q")
}
