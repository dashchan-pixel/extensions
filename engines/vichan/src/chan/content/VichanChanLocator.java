package chan.content;

import android.net.Uri;

import java.util.List;
import java.util.regex.Pattern;

public class VichanChanLocator extends ChanLocator {

    protected static String DEFAULT_SEGMENT_PRESET = "";

    protected static Pattern BOARD_PATH = Pattern.compile("/\\w+(?:/(?:(?:catalog|index|\\d+)\\.html)?)?");
    protected static Pattern THREAD_PATH = Pattern.compile("/\\w+/{1,2}thread/(\\d+).*\\.html");
    protected static Pattern ATTACHMENT_PATH = Pattern.compile("/\\w+/src/\\d+\\.\\w+");

    @Override
    public Uri createThreadUri(String boardName, String threadNumber) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, "res", threadNumber + ".json");
    }

    public Uri createThreadsUri(String boardName, int page, boolean isCatalog) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, (isCatalog ? "catalog"
                : Integer.toString(page)) + ".json");
    }

    public Uri createBoardsUri() {
        return buildPath(DEFAULT_SEGMENT_PRESET, "boards.json");
    }

    public Uri createFileUri(String boardName, String tim, String ext) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, "src", tim + ext);
    }

    public Uri createThumbnailUri(String boardName, String thumbnail) {
        return buildPath(DEFAULT_SEGMENT_PRESET, boardName, "thumb", thumbnail);
    }

    public Uri createAntispamUri(String boardName, String threadNumber) {
        return threadNumber != null ? super.createThreadUri(boardName, threadNumber)
                : super.createBoardUri(boardName, 0);
    }

    @Override
    public String getThreadNumber(Uri uri) {
        return uri != null ? getGroupValue(uri.getPath(), THREAD_PATH, 1) : null;
    }

    @Override
    public String getPostNumber(Uri uri) {
        String fragment = uri.getFragment();
        if (fragment != null && fragment.startsWith("q")) {
            return fragment.substring(1);
        }
        return fragment;
    }

    @Override
    public boolean isBoardUri(Uri uri) {
        return isChanHostOrRelative(uri) && isPathMatches(uri, BOARD_PATH);
    }

    @Override
    public boolean isThreadUri(Uri uri) {
        return isChanHostOrRelative(uri) && isPathMatches(uri, THREAD_PATH);
    }

    @Override
    public boolean isAttachmentUri(Uri uri) {
        return isChanHostOrRelative(uri) && isPathMatches(uri, ATTACHMENT_PATH);
    }

    @Override
    public String getBoardName(Uri uri) {
        if (uri != null) {
            List<String> segments = uri.getPathSegments();
            if (segments.size() > 0) {
                for (String segment : segments) {
                    if (!segment.equals(DEFAULT_SEGMENT_PRESET)) {
                        return segment;
                    }
                }
            }
        }
        return null;
    }



}
