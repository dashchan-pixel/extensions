package com.trixiether.dashchan.chan.warosu;

import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class WarosuPostsParser {

    private final WarosuChanLocator locator;

    private boolean searchModeOn = false;

    private String resTo;
    private Posts thread;
    private Post post;
    private FileAttachment attachment;

    private ArrayList<Posts> threads;
    private final ArrayList<Post> posts = new ArrayList<>();

    private static final Pattern PATTERN_ID = Pattern.compile("p([\\d]+)");
    private static final Pattern PATTERN_FILE_SRC = Pattern.compile("/data/[\\w\\d]+/img/[\\d]+/[\\d]+/[\\S]+.[\\w\\d]+$");
    private static final Pattern PATTERN_FILE = Pattern.compile("File: ([\\d.]+) ([\\w]+), ([\\d]+)x([\\d]+), ([\\S]+.[\\d\\w]+)");
    // 1 - size (float)
    // 2 - unit
    // 3 - width
    // 4 - height
    // 5 - filename
    private static final Integer PATTERN_FILE_SIZE = 1;
    private static final Integer PATTERN_FILE_UNIT = 2;
    private static final Integer PATTERN_FILE_WIDTH = 3;
    private static final Integer PATTERN_FILE_HEIGHT = 4;
    private static final Integer PATTERN_FILE_FILENAME = 5;

    private static final Integer FROM_KILOBYTE_TO_BYTES_FACTOR = 1000;
    private static final Integer FROM_MEGABYTE_TO_BYTES_FACTOR = FROM_KILOBYTE_TO_BYTES_FACTOR * FROM_KILOBYTE_TO_BYTES_FACTOR;

    public WarosuPostsParser(Object linked) {
        locator = WarosuChanLocator.get(linked);
    }

    private void closeThread() {
        if (thread != null) {
            thread.setPosts(posts);
            thread.addPostsCount(posts.size());
            int postsWithFilesCount = 0;
            for (Post post : posts) {
                postsWithFilesCount += post.getAttachmentsCount();
            }
            thread.addPostsWithFilesCount(postsWithFilesCount);
            threads.add(thread);
            posts.clear();
        }
    }

    public ArrayList<Posts> convertThreads(InputStream input) throws IOException, ParseException {
        threads = new ArrayList<>();
        searchModeOn = false;
        PARSER.parse(new InputStreamReader(input), this);
        closeThread();
        return threads;
    }

    public Posts convertPosts(InputStream input, Uri threadUri) throws IOException, ParseException {
        searchModeOn = false;
        PARSER.parse(new InputStreamReader(input), this);
        return posts.size() > 0 ? new Posts(posts).setArchivedThreadUri(threadUri) : null;
    }

    public ArrayList<Post> convertSearch(InputStream input) throws IOException, ParseException {
        searchModeOn = true;
        PARSER.parse(new InputStreamReader(input), this);
        return posts;
    }

    private static final TemplateParser<WarosuPostsParser> PARSER = TemplateParser
            .<WarosuPostsParser>builder()
            .name("div")
            .open(((instance, holder, tagName, attributes) -> {
                String threadDivId = attributes.get("id");
                if (threadDivId != null) {
                    Matcher matcher = PATTERN_ID.matcher(threadDivId);
                    if (matcher.find()) {
                        String threadId = matcher.group(1);
                        Post post = new Post();
                        post.setPostNumber(threadId);
                        holder.resTo = threadId;
                        holder.post = post;
                        holder.thread = new Posts();
                    }
                }
                return false;
            }))
            .equals("td", "class", "reply")
            .open(((instance, holder, tagName, attributes) -> {
                String postIdTr = attributes.get("id");
                if (postIdTr != null) {
                    Matcher matcher = PATTERN_ID.matcher(postIdTr);
                    if (matcher.find()) {
                        String postId = matcher.group(1);
                        Post post = new Post();
                        post.setPostNumber(postId);
                        if (!holder.searchModeOn)
                            post.setParentPostNumber(holder.resTo);
                        else
                            post.setParentPostNumber(postId);
                        holder.post = post;
                    }
                }
                return false;
            }))
            .equals("span", "class", "filetitle")
            .open(((instance, holder, tagName, attributes) -> holder.post != null))
            .content(((instance, holder, text) -> {
                holder.post.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
            }))
            .equals("span", "class", "postername")
            .open(((instance, holder, tagName, attributes) -> holder.post != null))
            .content(((instance, holder, text) -> {
                holder.post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));
            }))
            .equals("span", "class", "posttime")
            .open(((instance, holder, tagName, attributes) -> {
                if (holder.post != null) {
                    String timestring = attributes.get("title");
                    try {
                        assert timestring != null;
                        long unixtime = Long.parseLong(timestring) / 1000;
                        holder.post.setTimestamp(unixtime);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                return false;
            }))
            .name("span")
            .content(((instance, holder, text) -> {
                if (holder.post != null) {
                    Matcher matcher = PATTERN_FILE.matcher(StringUtils.clearHtml(text).trim());
                    int bytes = 0;
                    try {
                        if (matcher.find()) {
                            if (holder.attachment == null) {
                                holder.attachment = new FileAttachment();
                            }
                            switch (Objects.requireNonNull(matcher.group(PATTERN_FILE_UNIT))) {
                                case "KB": {
                                    bytes = (int) (Float.parseFloat(Objects.requireNonNull(matcher.group(PATTERN_FILE_SIZE))) * FROM_KILOBYTE_TO_BYTES_FACTOR);
                                    break;
                                }
                                case "MB": {
                                    bytes = (int) (Float.parseFloat(Objects.requireNonNull(matcher.group(PATTERN_FILE_SIZE))) * FROM_MEGABYTE_TO_BYTES_FACTOR);
                                    break;
                                }
                            }
                            holder.attachment.setSize(bytes);
                            holder.attachment.setWidth(Integer.parseInt(Objects.requireNonNull(matcher.group(PATTERN_FILE_WIDTH))));
                            holder.attachment.setHeight(Integer.parseInt(Objects.requireNonNull(matcher.group(PATTERN_FILE_HEIGHT))));
                            holder.attachment.setOriginalName(matcher.group(PATTERN_FILE_FILENAME));
                        }
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }))
            .name("a")
            .open(((instance, holder, tagName, attributes) -> {
                if (holder.post != null) {
                    Uri uri = Uri.parse(attributes.get("href"));
                    Matcher matcher = PATTERN_FILE_SRC.matcher(uri.toString());
                    if (matcher.matches()) {
                        holder.attachment.setFileUri(holder.locator, uri);
                    }
                }
                return false;
            }))
            .equals("img","class", "thumb")
            .open((instance, holder, tagName, attributes) -> {
                if (holder.post != null) {
                    Uri uri = Uri.parse(attributes.get("src"));
                    holder.attachment.setThumbnailUri(holder.locator, uri);
                }
                return false;
            })
            .name("blockquote")
            .content(((instance, holder, text) -> {
                if (holder.post != null) {
                    if (text != null) {
                        text = text.trim();
                    }
                    holder.post.setComment(text);
                    if (holder.attachment != null) {
                        holder.post.setAttachments(holder.attachment);
                    }
                    holder.posts.add(holder.post);
                    holder.attachment = null;
                    holder.post = null;
                }
            }))
            .equals("br", "class", "newthr")
            .open(((instance, holder, tagName, attributes) -> {
                if (holder.threads != null) {
                    holder.closeThread();
                }
                return false;
            }))
            .prepare();
}
