package com.mishiranu.dashchan.chan.e444

import chan.content.ChanConfiguration
import chan.util.StringUtils

class E444ChanConfiguration : ChanConfiguration() {
    init {
        request(OPTION_ALLOW_CAPTCHA_PASS)
        addCaptchaType(CAPTCHA_TYPE_SLIDER)
    }

    override fun obtainBoardConfiguration(boardName: String?): Board =
        Board().apply {
            allowPosting = get(boardName, KEY_POSTING_ENABLED, true)
            allowCatalog = true
            allowDeleting = true
            allowReporting = true
        }

    override fun obtainCustomCaptchaConfiguration(captchaType: String): Captcha? =
        if (captchaType == CAPTCHA_TYPE_SLIDER) {
            Captcha().apply {
                title = "Slider"
                input = Captcha.Input.NUMERIC
                validity = Captcha.Validity.IN_BOARD_SEPARATELY
            }
        } else {
            null
        }

    override fun obtainPostingConfiguration(
        boardName: String?,
        newThread: Boolean,
    ): Posting =
        Posting().apply {
            val tripcodesEnabled = get(boardName, KEY_TRIPCODES_ENABLED, true)
            allowName = tripcodesEnabled
            allowTripcode = tripcodesEnabled
            allowEmail = true
            allowSubject = get(boardName, KEY_SUBJECTS_ENABLED, true)
            optionSage = get(boardName, KEY_SAGE_ENABLED, true)
            optionOriginalPoster = true
            maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, DEFAULT_MAX_COMMENT_LENGTH)
            maxCommentLengthEncoding = "UTF-8"
            attachmentCount = if (get(boardName, KEY_FILES_ENABLED, true)) MAX_ATTACHMENTS else 0
            attachmentMimeTypes.add("image/jpeg")
            attachmentMimeTypes.add("image/gif")
            attachmentMimeTypes.add("image/png")
            attachmentMimeTypes.add("image/webp")
            attachmentMimeTypes.add("video/webm")
            attachmentMimeTypes.add("video/mp4")
            attachmentMimeTypes.add("audio/mp3")
            attachmentMimeTypes.add("application/ogg")
            attachmentMimeTypes.add("application/zip")
            attachmentMimeTypes.add("application/pdf")
        }

    override fun obtainDeletingConfiguration(boardName: String?): Deleting = Deleting()

    override fun obtainReportingConfiguration(boardName: String?): Reporting =
        Reporting().apply {
            comment = true
            multiplePosts = true
        }

    /**
     * Persists what the app will ask for later through `obtain*Configuration`.
     *
     * The previous build serialised the whole board object to JSON under one preference key and
     * deserialised it on every `obtain*` call, which crashed outright for any board the app knew
     * about before a board list had ever been fetched. Individual scalar preferences have
     * defaults, so an unknown board degrades to sensible values instead.
     */
    internal fun updateFromBoardInfo(info: E444BoardInfo) {
        val boardName = info.id ?: return
        set(boardName, KEY_POSTING_ENABLED, info.enablePosting)
        set(boardName, KEY_TRIPCODES_ENABLED, info.enableTrips)
        set(boardName, KEY_SUBJECTS_ENABLED, info.enableSubject)
        set(boardName, KEY_SAGE_ENABLED, info.enableSage)
        set(boardName, KEY_FILES_ENABLED, info.hasFileTypes)
        if (info.maxComment > 0) {
            set(boardName, KEY_MAX_COMMENT_LENGTH, info.maxComment)
        }
        set(
            boardName,
            KEY_REACTION_ICONS,
            if (info.enableReactions) info.reactions.joinToString(ICON_SEPARATOR) else "",
        )
        storeBoardTitle(boardName, info.title)
        storeBoardDescription(boardName, StringUtils.clearHtml(info.description).trim())
        storeDefaultName(boardName, info.defaultName)
        storeBumpLimit(boardName, info.bumpLimit)
        storePagesCount(boardName, info.maxPages)
    }

    /**
     * Icon names the board allows as reactions, for the picker the post decorator adds to the post
     * context menu. Stored per board because only a board list response carries them, and the
     * decorator runs long after one was read.
     */
    internal fun getReactionIcons(boardName: String?): List<String> {
        val stored = get(boardName, KEY_REACTION_ICONS, "").orEmpty()
        if (StringUtils.isEmpty(stored)) {
            return emptyList()
        }
        return stored.split(ICON_SEPARATOR).filter { it.isNotEmpty() }
    }

    /**
     * Which poll answer this user picked for a post, or `-1` if none.
     *
     * The board's poll response reports only totals, so the user's own choice has to be remembered
     * locally to keep the answer highlighted. Kept here rather than in the post payload because it
     * belongs to this install, not to the post.
     */
    internal fun getVotedPollAnswer(stateKey: String): Int = get(null, "$KEY_PREFIX_POLL_VOTE$stateKey", -1)

    internal fun setVotedPollAnswer(
        stateKey: String,
        index: Int,
    ) {
        set(null, "$KEY_PREFIX_POLL_VOTE$stateKey", index)
    }

    /** Reactions this user has added to a post, for the same reason as [getVotedPollAnswer]. */
    internal fun getUsedReactions(stateKey: String): Set<String> {
        val stored = get(null, "$KEY_PREFIX_REACTIONS_USED$stateKey", "").orEmpty()
        if (StringUtils.isEmpty(stored)) {
            return emptySet()
        }
        return stored.split(ICON_SEPARATOR).filter { it.isNotEmpty() }.toSet()
    }

    internal fun toggleUsedReaction(
        stateKey: String,
        icon: String,
    ) {
        val used = getUsedReactions(stateKey).toMutableSet()
        if (!used.remove(icon)) {
            used.add(icon)
        }
        set(null, "$KEY_PREFIX_REACTIONS_USED$stateKey", used.joinToString(ICON_SEPARATOR))
    }

    companion object {
        const val CAPTCHA_TYPE_SLIDER = "ech"

        const val COOKIE_PASSCODE_AUTH = "passcode_auth"
        const val COOKIE_USERCODE_AUTH = "usercode_auth"

        private const val KEY_POSTING_ENABLED = "posting_enabled"
        private const val KEY_TRIPCODES_ENABLED = "tripcodes_enabled"
        private const val KEY_SUBJECTS_ENABLED = "subjects_enabled"
        private const val KEY_SAGE_ENABLED = "sage_enabled"
        private const val KEY_FILES_ENABLED = "files_enabled"
        private const val KEY_MAX_COMMENT_LENGTH = "max_comment_length"
        private const val KEY_REACTION_ICONS = "reaction_icons"

        private const val ICON_SEPARATOR = " "

        private const val KEY_PREFIX_POLL_VOTE = "poll_vote_"
        private const val KEY_PREFIX_REACTIONS_USED = "reactions_used_"

        private const val DEFAULT_MAX_COMMENT_LENGTH = 15000
        private const val MAX_ATTACHMENTS = 4
    }
}
