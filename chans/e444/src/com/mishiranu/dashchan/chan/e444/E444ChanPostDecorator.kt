package com.mishiranu.dashchan.chan.e444

import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPostDecorator
import chan.content.InvalidResponseException
import chan.http.HttpException
import chan.http.HttpRequest
import chan.text.JsonSerial
import chan.text.ParseException
import com.mishiranu.dashchan.chan.e444.decorator.MenuView
import com.mishiranu.dashchan.chan.e444.decorator.PollView
import com.mishiranu.dashchan.chan.e444.decorator.ReactionsView
import java.io.IOException

/**
 * Draws the three things `ech` puts on a post that the common post model has no room for: its menu,
 * its poll and its reactions. All of them arrive as the post's opaque payload, written by
 * [E444ModelMapper].
 *
 * This replaces an `enhance/` layer of 18 files that drove the client's UI by reflection -- into
 * `View.mListenerInfo`, `WindowManagerGlobal.mViews`, `DialogMenu$Adapter`, `ConfigurationSet
 * .chanName`, `PostNumber.major` and `ConcurrentUtils.PARALLEL_EXECUTOR` among others -- and polled
 * the whole view tree from an `OnPreDrawListener` on every frame. None of that survives here: the
 * client says which post a view is for, and hands back a usable `HttpHolder` for the vote and
 * reaction requests.
 */
class E444ChanPostDecorator : ChanPostDecorator() {
    override fun onCreatePostView(data: ChanPostDecorator.CreatePostViewData): View? {
        val container = LinearLayout(data.context)
        container.orientation = LinearLayout.VERTICAL
        val menuView = MenuView(data.context)
        val pollView = PollView(data.context)
        val reactionsView = ReactionsView(data.context)
        // The menu comes first: on the board's own pages it is appended to the comment, while the
        // poll and the reactions are the post's own furniture below it.
        container.addView(
            menuView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            pollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            reactionsView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.tag = Holder(menuView, pollView, reactionsView)
        return container
    }

    override fun onBindPostView(data: ChanPostDecorator.BindPostViewData): Boolean {
        val holder = data.view.tag as? Holder ?: return false
        val extra = E444PostExtra.decode(data.extra)
        if (extra.isEmpty) {
            return false
        }
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        val stateKey = stateKey(data.boardName, data.postNumber)
        if (extra.menu.isNotEmpty()) {
            holder.menuView.visibility = View.VISIBLE
            holder.menuView.bind(extra.menu, data.theme) { url ->
                data.postContext.navigate(menuLinkUri(url))
            }
        } else {
            holder.menuView.visibility = View.GONE
        }
        if (extra.pollAnswers.isNotEmpty()) {
            holder.pollView.visibility = View.VISIBLE
            holder.pollView.bind(
                extra,
                data.theme,
                stateKey,
                configuration.getVotedPollAnswer(stateKey),
            ) { index -> vote(data, index) }
        } else {
            holder.pollView.unbind()
            holder.pollView.visibility = View.GONE
        }
        if (extra.reactions.isNotEmpty()) {
            holder.reactionsView.visibility = View.VISIBLE
            holder.reactionsView.bind(
                extra.reactions,
                data.theme,
                configuration.getUsedReactions(stateKey),
                ::iconUri,
                data.postContext,
            ) { icon -> react(data.postContext, stateKey, icon) }
        } else {
            holder.reactionsView.visibility = View.GONE
        }
        return true
    }

    override fun onUnbindPostView(data: ChanPostDecorator.UnbindPostViewData) {
        (data.view.tag as? Holder)?.pollView?.unbind()
    }

    /**
     * Adds the reaction picker under the post context menu, which is where a reaction the post does
     * not carry yet can be chosen. Contributes a view rather than entries: a row of icons is the
     * whole point, and a list of icon names would not be recognisable.
     */
    override fun onCreatePostMenu(data: ChanPostDecorator.CreatePostMenuData) {
        val icons = ChanConfiguration.get<E444ChanConfiguration>(this).getReactionIcons(data.boardName)
        if (icons.isEmpty()) {
            return
        }
        val stateKey = stateKey(data.boardName, data.postNumber)
        data.menu.setFooterView(
            ReactionsView.createPicker(data.context, icons, ::iconUri, data.postContext) { icon ->
                data.menu.dismiss()
                react(data.postContext, stateKey, icon)
            },
        )
    }

    /**
     * Runs a vote or a reaction, then re-reads the post so the counts the user sees are the board's
     * and not a local guess.
     */
    override fun onPerformAction(data: ChanPostDecorator.PerformActionData): ChanPostDecorator.ActionResult? {
        when (data.action) {
            ACTION_VOTE -> sendPollVote(data, data.actionExtra ?: return null)
            ACTION_REACT -> sendReaction(data, data.actionExtra ?: return null)
            else -> return null
        }
        return ChanPostDecorator.ActionResult().setExtra(readPostExtra(data))
    }

    private fun vote(
        data: ChanPostDecorator.BindPostViewData,
        index: Int,
    ) {
        val configuration = ChanConfiguration.get<E444ChanConfiguration>(this)
        // The board's response says nothing about which answer this user picked, so the choice is
        // remembered here and re-applied on every bind.
        configuration.setVotedPollAnswer(stateKey(data.boardName, data.postNumber), index)
        data.postContext.performAction(ACTION_VOTE, index.toString())
    }

    private fun react(
        postContext: ChanPostDecorator.PostContext,
        stateKey: String,
        icon: String,
    ) {
        ChanConfiguration.get<E444ChanConfiguration>(this).toggleUsedReaction(stateKey, icon)
        postContext.performAction(ACTION_REACT, icon)
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun sendPollVote(
        data: ChanPostDecorator.PerformActionData,
        voteIndex: String,
    ) {
        val response =
            E444RequestPerformer
                .request(data, "api", "polls", "vote")
                .param("board", data.boardName.orEmpty())
                .param("num", data.postNumber)
                .param("vote", voteIndex)
                .configure(HttpRequest::setGetMethod)
                .performJsonObject()
        if (response.optInt("result") != 1) {
            throw HttpException(0, errorMessage(response) ?: "Vote was rejected")
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    private fun sendReaction(
        data: ChanPostDecorator.PerformActionData,
        icon: String,
    ) {
        val response =
            E444RequestPerformer
                .request(data, "api", "react")
                .param("board", data.boardName.orEmpty())
                .param("num", data.postNumber)
                .param("icon", icon)
                .configure(HttpRequest::setGetMethod)
                .performJsonObject()
        val error = response.optJSONObject("error")
        if (error != null && error.optInt("code") != 0) {
            throw HttpException(0, errorMessage(response) ?: "Reaction was rejected")
        }
    }

    /**
     * Re-reads the single post and returns its freshly built payload, so the poll shares and reaction
     * counts come from the board rather than being incremented locally.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    private fun readPostExtra(data: ChanPostDecorator.PerformActionData): String? {
        val locator = ChanLocator.get<E444ChanLocator>(this)
        return E444RequestPerformer
            .request(data, "api", "mobile", "v2", "post", data.boardName.orEmpty(), data.postNumber)
            .param("_", System.currentTimeMillis().toString())
            .configure(HttpRequest::setGetMethod)
            .performJson { reader -> readPostExtra(reader, locator) }
    }

    @Throws(IOException::class, ParseException::class)
    private fun readPostExtra(
        reader: JsonSerial.Reader,
        locator: E444ChanLocator,
    ): String? {
        var extra: String? = null
        reader.startObject()
        while (!reader.endStruct()) {
            when (reader.nextName()) {
                "post" -> {
                    val posts = ArrayList<chan.content.model.Post>()
                    E444ModelMapper.readPostInto(reader, locator, posts)
                    extra = posts.firstOrNull()?.getExtra()
                }

                else -> reader.skip()
            }
        }
        return extra
    }

    private fun iconUri(icon: String): Uri = ChanLocator.get<E444ChanLocator>(this).buildPath("static", "img", "reactions", icon)

    /**
     * Where a menu button leads.
     *
     * The board stores the address exactly as the poster wrote it, so a menu holds anything from
     * `/b/res/1234.html` through `/pol/` to a link off the board entirely. A relative one is
     * resolved against the chan's host, the way the client resolves a relative address in a comment
     * link, which is what turns the common case into a thread the app can open itself.
     */
    private fun menuLinkUri(url: String): Uri {
        val uri = Uri.parse(url)
        if (!uri.isRelative) {
            return uri
        }
        val path = uri.encodedPath.orEmpty()
        return ChanLocator
            .get<E444ChanLocator>(this)
            .buildPath()
            .buildUpon()
            .encodedPath(if (path.startsWith("/")) path else "/$path")
            .encodedQuery(uri.encodedQuery)
            .encodedFragment(uri.encodedFragment)
            .build()
    }

    /** Groups the three views a decorated post owns, so bind does not have to search for them. */
    private class Holder(
        val menuView: MenuView,
        val pollView: PollView,
        val reactionsView: ReactionsView,
    )

    companion object {
        private const val ACTION_VOTE = "vote"
        private const val ACTION_REACT = "react"

        private fun stateKey(
            boardName: String?,
            postNumber: String,
        ): String = "${boardName.orEmpty()}:$postNumber"

        private fun errorMessage(response: org.json.JSONObject): String? = response.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }

        /**
         * Reports a failure that only costs the user a decoration. Deliberately not an exception:
         * the client would show "extension error" for a post that is otherwise perfectly readable.
         */
        fun logDecorationFailure(
            message: String,
            t: Throwable,
        ) {
            android.util.Log.w("E444Decorator", message, t)
        }
    }
}
