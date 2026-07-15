package com.trixiether.dashchan.chan.warosu;

import android.net.Uri;

import chan.content.FoolFuukaChanLocator;

public class WarosuChanLocator extends FoolFuukaChanLocator {

    private static int SEARCH_STEP = 24;

    public WarosuChanLocator() {
        addChanHost("warosu.org");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }

    @Override
    public Uri createBoardUri(String boardName, int pageNumber) {
        return pageNumber > 0 ? buildPath(boardName, "?task=page&page=" + pageNumber) : buildPath(boardName, "");
    }

    @Override
    public Uri createThreadUri(String boardName, String threadNumber) {
        return buildPath(boardName, "thread", threadNumber);
    }

    public Uri createSearchUri(String boardName, String text, int pageNumber) {
        String offset = "&offset=" + (pageNumber * SEARCH_STEP);
        return buildPath(boardName, "?task=search&ghost=false&search_text=" + text + offset);
    }

}
