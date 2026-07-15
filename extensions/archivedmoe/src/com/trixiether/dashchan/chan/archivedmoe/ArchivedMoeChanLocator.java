package com.trixiether.dashchan.chan.archivedmoe;

import chan.content.FoolFuukaChanLocator;

public class ArchivedMoeChanLocator extends FoolFuukaChanLocator {
    public ArchivedMoeChanLocator() {
        addChanHost("archived.moe");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }
}
