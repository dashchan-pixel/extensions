package chan.content;

public class VichanChanConfiguration extends ChanConfiguration {

    protected static String DEFAULT_BOARD_CATEGORY = "Boards";

    public VichanChanConfiguration() {
        setDefaultName("Anonymous");
    }

    @Override
    public Board obtainBoardConfiguration(String boardName) {
        Board board = new Board();
        board.allowCatalog = true;
        board.allowPosting = true;
        board.allowDeleting = true;
        board.allowReporting = true;
        return board;
    }

    @Override
    public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
        Posting posting = new Posting();
        posting.allowName = true;
        posting.allowTripcode = true;
        posting.allowEmail = true;
        posting.allowSubject = true;
        posting.optionSage = true;
        posting.attachmentCount = 4;
        posting.attachmentMimeTypes.add("image/*");
        posting.attachmentMimeTypes.add("video/webm");
        posting.attachmentMimeTypes.add("video/mp4");
        posting.attachmentSpoiler = true;
        return posting;
    }

    @Override
    public Deleting obtainDeletingConfiguration(String boardName) {
        Deleting deleting = new Deleting();
        deleting.password = true;
        deleting.multiplePosts = true;
        deleting.optionFilesOnly = true;
        return deleting;
    }

    @Override
    public Reporting obtainReportingConfiguration(String boardName) {
        Reporting reporting = new Reporting();
        reporting.comment = true;
        reporting.multiplePosts = true;
        return reporting;
    }

    public String getDefaultBoardCategory() {
        return DEFAULT_BOARD_CATEGORY;
    }

}