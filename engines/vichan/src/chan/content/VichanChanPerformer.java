package chan.content;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class VichanChanPerformer extends ChanPerformer {

    @Override
    public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {

        VichanChanConfiguration configuration = VichanChanConfiguration.get(this);
        VichanChanLocator locator = ChanLocator.get(this);
        Uri uri = locator.createBoardsUri();

        JSONArray jsonArray = null;
        try {
            String value = new HttpRequest(uri, data)
                    .setGetMethod()
                    .perform().readString();
            jsonArray = new JSONArray(value);
        } catch (HttpException e) {
            throw new HttpException(e.getResponseCode(), e.getMessage());
        } catch (JSONException e) {
            throw new InvalidResponseException();
        }

        HashMap<String, ArrayList<Board>> boardsMap = new HashMap<>();

        try {
            for (int i = 0; i < jsonArray.length(); i++) {

                JSONObject jsonObject = jsonArray.getJSONObject(i);

                String category = configuration.getDefaultBoardCategory();
                String boardName = null;
                String title = null;
                String description = null;

                Iterator<String> keys = jsonObject.keys();

                while (keys.hasNext()) {
                    switch (keys.next()) {
                        case "board": {
                            boardName = jsonObject.getString("board");;
                            break;
                        }
                        case "title": {
                            title = jsonObject.getString("title");;
                            break;
                        }
                        case "subtitle": {
                            description = jsonObject.getString("subtitle");;
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                }

                if (!StringUtils.isEmpty(category) && !StringUtils.isEmpty(boardName) &&
                        !StringUtils.isEmpty(title)) {
                    ArrayList<Board> boards = boardsMap.get(category);
                    if (boards == null) {
                        boards = new ArrayList<>();
                        boardsMap.put(category, boards);
                    }
                    boards.add(new Board(boardName, title, description));
                }

            }
        } catch (JSONException e) {
            throw new InvalidResponseException();
        }

        ArrayList<BoardCategory> boardCategories = new ArrayList<>();
        for (HashMap.Entry<String, ArrayList<Board>> entry : boardsMap.entrySet()) {
            ArrayList<Board> boards = entry.getValue();
            boardCategories.add(new BoardCategory(configuration.getDefaultBoardCategory(), boards));
            break;
        }

        return new ReadBoardsResult(boardCategories);

    }

    @Override
    public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
        VichanChanLocator locator = ChanLocator.get(this);
        Uri uri = locator.createThreadsUri(data.boardName, data.pageNumber, data.isCatalog());
        HttpResponse response = new HttpRequest(uri, data).setValidator(data.validator).perform();
        HttpValidator validator = response.getValidator();
        ArrayList<Posts> threads = new ArrayList<>();
        try (InputStream input = response.open();
             JsonSerial.Reader reader = JsonSerial.reader(input)) {
            if (data.isCatalog()) {
                reader.startArray();
                while (!reader.endStruct()) {
                    reader.startObject();
                    while (!reader.endStruct()) {
                        switch (reader.nextName()) {
                            case "threads": {
                                reader.startArray();
                                while (!reader.endStruct()) {
                                    threads.add(VichanModelMapper.createThread(reader,
                                            locator, data.boardName, true));
                                }
                                break;
                            }
                            default: {
                                reader.skip();
                                break;
                            }
                        }
                    }
                }
            } else {
                reader.startObject();
                while (!reader.endStruct()) {
                    switch (reader.nextName()) {
                        case "threads": {
                            reader.startArray();
                            while (!reader.endStruct()) {
                                threads.add(VichanModelMapper.createThread(reader,
                                        locator, data.boardName, false));
                            }
                            break;
                        }
                        default: {
                            reader.skip();
                            break;
                        }
                    }
                }
            }
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
        return new ReadThreadsResult(threads).setValidator(validator);
    }

    @Override
    public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
        VichanChanLocator locator = ChanLocator.get(this);
        Uri uri = locator.createThreadUri(data.boardName, data.threadNumber);
        ArrayList<Post> posts = new ArrayList<>();
        HttpResponse response = new HttpRequest(uri, data).setValidator(data.validator).perform();
        try (InputStream input = response.open();
             JsonSerial.Reader reader = JsonSerial.reader(input)) {
            reader.startObject();
            while (!reader.endStruct()) {
                switch (reader.nextName()) {
                    case "posts": {
                        VichanModelMapper.Extra extra = new VichanModelMapper.Extra();
                        reader.startArray();
                        while (!reader.endStruct()) {
                            posts.add(VichanModelMapper.createPost(reader,
                                    locator, data.boardName, extra));
                            if (extra != null) {
                                extra = null;
                            }
                        }
                        break;
                    }
                    default: {
                        reader.skip();
                        break;
                    }
                }
            }
            return new ReadPostsResult(new Posts(posts)).setFullThread(true);
        } catch (ParseException e) {
            throw new InvalidResponseException(e);
        } catch (IOException e) {
            throw response.fail(e);
        }
    }

    protected void parseAntispamFields(String text, MultipartEntity entity) throws ParseException {
    }

    @Override
    public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
        MultipartEntity entity = new MultipartEntity();
        entity.add("board", data.boardName);
        entity.add("thread", data.threadNumber);
        entity.add("name", data.name);
        entity.add("email", data.optionSage ? "sage" : data.email);
        entity.add("subject", data.subject);
        entity.add("body", StringUtils.emptyIfNull(data.comment));
        entity.add("password", data.password);
        boolean spoiler = false;
        if (data.attachments != null) {
            for (int i = 0; i < data.attachments.length; i++) {
                SendPostData.Attachment attachment = data.attachments[i];
                attachment.addToEntity(entity, "file" + (i > 0 ? i + 1 : ""));
                if (attachment.optionSpoiler) {
                    spoiler = true;
                }
            }
        }
        if (spoiler) {
            entity.add("spoiler", "on");
        }
        entity.add("json_response", "1");

        VichanChanLocator locator = ChanLocator.get(this);
        Uri contentUri = locator.createAntispamUri(data.boardName, data.threadNumber);
        String responseText = new HttpRequest(contentUri, data).perform().readString();
        try {
            parseAntispamFields(responseText, entity);
        } catch (ParseException e) {
            throw new InvalidResponseException();
        }

        Uri uri = locator.buildPath("vichan","post.php");
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
                    .addHeader("Referer", locator.buildPath().toString())
                    .setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (jsonObject == null) {
            throw new InvalidResponseException();
        }

        String redirect = jsonObject.optString("redirect");
        if (!StringUtils.isEmpty(redirect)) {
            uri = locator.buildPath(redirect);
            String threadNumber = locator.getThreadNumber(uri);
            String postNumber = locator.getPostNumber(uri);
            return new SendPostResult(threadNumber, postNumber);
        }
        String errorMessage = jsonObject.optString("error");
        if (errorMessage != null) {
            int errorType = 0;
            if (errorMessage.contains("The body was") || errorMessage.contains("must be at least")) {
                errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
            } else if (errorMessage.contains("You must upload an image")) {
                errorType = ApiException.SEND_ERROR_EMPTY_FILE;
            } else if (errorMessage.contains("mistyped the verification")) {
                errorType = ApiException.SEND_ERROR_CAPTCHA;
            } else if (errorMessage.contains("was too long")) {
                errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
            } else if (errorMessage.contains("The file was too big") || errorMessage.contains("is longer than")) {
                errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
            } else if (errorMessage.contains("Thread locked")) {
                errorType = ApiException.SEND_ERROR_CLOSED;
            } else if (errorMessage.contains("Invalid board")) {
                errorType = ApiException.SEND_ERROR_NO_BOARD;
            } else if (errorMessage.contains("Thread specified does not exist")) {
                errorType = ApiException.SEND_ERROR_NO_THREAD;
            } else if (errorMessage.contains("Unsupported image format")) {
                errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED;
            } else if (errorMessage.contains("Maximum file size")) {
                errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
            } else if (errorMessage.contains("Your IP address")) {
                errorType = ApiException.SEND_ERROR_BANNED;
            } else if (errorMessage.contains("That file")) {
                errorType = ApiException.SEND_ERROR_FILE_EXISTS;
            } else if (errorMessage.contains("Flood detected")) {
                errorType = ApiException.SEND_ERROR_TOO_FAST;
            }
            if (errorType != 0) {
                throw new ApiException(errorType);
            }
            throw new ApiException(errorMessage);
        }
        throw new InvalidResponseException();
    }

    @Override
    public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
            InvalidResponseException {
        VichanChanLocator locator = ChanLocator.get(this);
        UrlEncodedEntity entity = new UrlEncodedEntity("delete", "1", "board", data.boardName,
                "password", data.password, "json_response", "1");
        for (String postNumber : data.postNumbers) {
            entity.add("delete_" + postNumber, "1");
        }
        if (data.optionFilesOnly) {
            entity.add("file", "on");
        }
        Uri uri = locator.buildPath("vichan", "post.php");
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
                    .setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (jsonObject == null) {
            throw new InvalidResponseException();
        }
        if (jsonObject.optBoolean("success")) {
            return null;
        }
        String errorMessage = jsonObject.optString("error");
        if (errorMessage != null) {
            int errorType = 0;
            if (errorMessage.contains("Wrong password")) {
                errorType = ApiException.DELETE_ERROR_PASSWORD;
            } else if (errorMessage.contains("before deleting that")) {
                errorType = ApiException.DELETE_ERROR_TOO_NEW;
            }
            if (errorType != 0) {
                throw new ApiException(errorType);
            }
            CommonUtils.writeLog("Delete message", errorMessage);
            throw new ApiException(errorMessage);
        }
        throw new InvalidResponseException();
    }

    @Override
    public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
            InvalidResponseException {
        VichanChanLocator locator = ChanLocator.get(this);
        UrlEncodedEntity entity = new UrlEncodedEntity("report", "1", "board", data.boardName,
                "reason", StringUtils.emptyIfNull(data.comment), "json_response", "1");
        for (String postNumber : data.postNumbers) {
            entity.add("delete_" + postNumber, "1");
        }
        Uri uri = locator.buildPath("vichan", "post.php");
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(new HttpRequest(uri, data).setPostMethod(entity)
                    .setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (jsonObject == null) {
            throw new InvalidResponseException();
        }
        if (jsonObject.optBoolean("success")) {
            return null;
        }
        String errorMessage = jsonObject.optString("error");
        if (errorMessage != null) {
            CommonUtils.writeLog("Report message", errorMessage);
            throw new ApiException(errorMessage);
        }
        throw new InvalidResponseException();
    }

}
