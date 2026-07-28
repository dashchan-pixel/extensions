package chan.content

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import chan.http.HttpException
import chan.http.HttpHolder
import chan.http.HttpRequest
import chan.library.api.BuildConfig

/**
 * Decorates displayed posts.
 *
 * This component is optional: declare it with `postDecorator = true` in the extension's `chan { }`
 * block and name the class `<NameUpper>ChanPostDecorator`, exactly as the four required components
 * are named. An extension without one behaves as before.
 *
 * A decorator can:
 *
 * - attach its own views to a post ([onCreatePostView], [onBindPostView], [onUnbindPostView]);
 * - contribute post context menu entries ([onCreatePostMenu]);
 * - intercept clicks on links inside a comment ([onPostLinkClick]);
 * - run background work with a usable [HttpHolder] ([onPerformAction]).
 *
 * The data a decorator renders comes from [chan.content.model.Post.setExtra]: whatever the extension
 * stores while parsing is handed back here. The client keeps that payload with the post, including in
 * the post cache, so it needs no re-fetching and no side channel.
 *
 * Every method except [onPerformAction] is called on the main thread.
 */
open class ChanPostDecorator {
    /**
     * Create the view this decorator attaches to a post.
     *
     * Called once per recycled post holder, so the result must not depend on any particular post:
     * bind the data in [onBindPostView].
     *
     * Extension resources are available through [ChanConfiguration.resources], but
     * [CreatePostViewData.context] is the client's, so its [android.view.LayoutInflater] cannot
     * resolve extension layout ids. Build the hierarchy programmatically.
     *
     * @param data Arguments holder.
     * @return View to attach, or `null` to decorate nothing.
     */
    open fun onCreatePostView(data: CreatePostViewData): View? =
        BuildConfig.Private.expr(data)

    /**
     * Bind a post to the view returned by [onCreatePostView].
     *
     * Called every time the post is displayed, including after the holder was recycled from an
     * unrelated post, so it must fully overwrite the previous state rather than assume anything
     * about it.
     *
     * @param data Arguments holder.
     * @return `true` to show the view, `false` to hide it for this post.
     */
    open fun onBindPostView(data: BindPostViewData): Boolean =
        BuildConfig.Private.expr(data)

    /**
     * Called when a bound view stops showing its post, so pending animations and image requests can
     * be dropped. The view itself stays alive for reuse.
     *
     * @param data Arguments holder.
     */
    open fun onUnbindPostView(data: UnbindPostViewData) {
        BuildConfig.Private.expr<Any>(data)
    }

    /**
     * Contribute entries to a post's context menu.
     *
     * May be called more than once for the same menu, because the client rebuilds it after a
     * configuration change, so it must be side-effect free.
     *
     * @param data Arguments holder.
     */
    open fun onCreatePostMenu(data: CreatePostMenuData) {
        BuildConfig.Private.expr<Any>(data)
    }

    /**
     * Called when a link inside a post comment is clicked, before the client handles it.
     *
     * @param data Arguments holder.
     * @return `true` if you handled the click, `false` to let the client proceed.
     */
    open fun onPostLinkClick(data: PostLinkClickData): Boolean =
        BuildConfig.Private.expr(data)

    /**
     * Perform work requested by [PostContext.performAction].
     *
     * Called on a background thread with a usable [HttpHolder], so a decorator never has to reach
     * outside the API to make a request.
     *
     * @param data Arguments holder.
     * @return Outcome, or `null` if there is nothing to report.
     * @throws HttpException if a request failed.
     * @throws InvalidResponseException if the response was invalid.
     */
    @Throws(HttpException::class, InvalidResponseException::class)
    open fun onPerformAction(data: PerformActionData): ActionResult? {
        BuildConfig.Private.error<HttpException>()
        BuildConfig.Private.error<InvalidResponseException>()
        return BuildConfig.Private.expr(data)
    }

    /**
     * Host colors resolved from the current theme, so your views can match the client without
     * guessing at its resources.
     */
    class PostTheme private constructor() {
        @JvmField
        val accentColor: Int = BuildConfig.Private.expr()

        @JvmField
        val postTextColor: Int = BuildConfig.Private.expr()

        @JvmField
        val metaTextColor: Int = BuildConfig.Private.expr()

        @JvmField
        val cardBackgroundColor: Int = BuildConfig.Private.expr()

        @JvmField
        val windowBackgroundColor: Int = BuildConfig.Private.expr()
    }

    /**
     * Operations you can ask the client to perform for the post being decorated. Instances are valid
     * only for the duration of the call that supplied them; do not store one and use it later.
     */
    interface PostContext {
        /**
         * Rebind the post, to display state you changed on your own. Rebinding is not free: drive
         * animations from your own views instead of calling this repeatedly.
         */
        fun invalidatePost()

        /**
         * Schedule [onPerformAction] on a background thread.
         *
         * @param action Your action name.
         * @param actionExtra Your payload, or `null`.
         */
        fun performAction(
            action: String,
            actionExtra: String?,
        )

        /**
         * Open `uri` the way the client opens a link the user clicked.
         *
         * @param uri Uri to open.
         */
        fun navigate(uri: Uri)

        /**
         * Load an image into `view` using the client's cache. The request is bound to the view, so
         * recycling it cancels the load instead of showing the wrong image.
         *
         * @param uri Image uri.
         * @param view Destination view.
         */
        fun loadImage(
            uri: Uri,
            view: ImageView,
        )

        /**
         * Show `message` to the user as the client shows its own notices.
         *
         * @param message Message to show.
         */
        fun showMessage(message: String?)
    }

    /**
     * Post context menu under construction. Entries appear below the client's own, in the order they
     * are added.
     */
    interface PostMenu {
        /**
         * Add a menu entry.
         *
         * @param title Entry title.
         * @param runnable Action to run when it is chosen.
         */
        fun addItem(
            title: String,
            runnable: Runnable,
        )

        /**
         * Add a menu entry with a check box.
         *
         * @param title Entry title.
         * @param checked Whether the box is checked.
         * @param runnable Action to run when it is chosen.
         */
        fun addCheckItem(
            title: String,
            checked: Boolean,
            runnable: Runnable,
        )

        /**
         * Place `view` below every menu entry. Use it for controls a list of entries cannot express;
         * call [dismiss] from it to close the menu.
         *
         * @param view View to place.
         */
        fun setFooterView(view: View)

        /**
         * Close the menu.
         */
        fun dismiss()
    }

    /**
     * Arguments holder for [onCreatePostView].
     */
    open class CreatePostViewData private constructor() {
        /**
         * Client context, themed for the post list.
         */
        @JvmField
        val context: Context = BuildConfig.Private.expr()

        /**
         * The group the returned view will be attached to. Pass it to
         * [android.view.LayoutInflater.inflate] with `attachToRoot = false` to get correct layout
         * parameters; do not add anything to it directly.
         */
        @JvmField
        val parent: ViewGroup = BuildConfig.Private.expr()

        @JvmField
        val theme: PostTheme = BuildConfig.Private.expr()
    }

    /**
     * Arguments holder for [onBindPostView].
     */
    open class BindPostViewData private constructor() {
        /**
         * The view returned by [onCreatePostView].
         */
        @JvmField
        val view: View = BuildConfig.Private.expr()

        @JvmField
        val boardName: String? = BuildConfig.Private.expr()

        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        @JvmField
        val postNumber: String = BuildConfig.Private.expr()

        @JvmField
        val originalPostNumber: String? = BuildConfig.Private.expr()

        /**
         * Payload stored by [chan.content.model.Post.setExtra], or `null`.
         */
        @JvmField
        val extra: String? = BuildConfig.Private.expr()

        @JvmField
        val theme: PostTheme = BuildConfig.Private.expr()

        @JvmField
        val postContext: PostContext = BuildConfig.Private.expr()
    }

    /**
     * Arguments holder for [onUnbindPostView].
     */
    open class UnbindPostViewData private constructor() {
        @JvmField
        val view: View = BuildConfig.Private.expr()

        @JvmField
        val boardName: String? = BuildConfig.Private.expr()

        @JvmField
        val postNumber: String = BuildConfig.Private.expr()
    }

    /**
     * Arguments holder for [onCreatePostMenu].
     */
    open class CreatePostMenuData private constructor() {
        @JvmField
        val context: Context = BuildConfig.Private.expr()

        @JvmField
        val menu: PostMenu = BuildConfig.Private.expr()

        @JvmField
        val boardName: String? = BuildConfig.Private.expr()

        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        @JvmField
        val postNumber: String = BuildConfig.Private.expr()

        /**
         * Payload stored by [chan.content.model.Post.setExtra], or `null`.
         */
        @JvmField
        val extra: String? = BuildConfig.Private.expr()

        @JvmField
        val theme: PostTheme = BuildConfig.Private.expr()

        @JvmField
        val postContext: PostContext = BuildConfig.Private.expr()
    }

    /**
     * Arguments holder for [onPostLinkClick].
     */
    open class PostLinkClickData private constructor() {
        @JvmField
        val uri: Uri = BuildConfig.Private.expr()

        @JvmField
        val boardName: String? = BuildConfig.Private.expr()

        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        @JvmField
        val postNumber: String = BuildConfig.Private.expr()

        /**
         * Payload stored by [chan.content.model.Post.setExtra], or `null`.
         */
        @JvmField
        val extra: String? = BuildConfig.Private.expr()

        /**
         * Whether the link was long-clicked rather than clicked.
         */
        @JvmField
        val longClick: Boolean = BuildConfig.Private.expr()

        @JvmField
        val postContext: PostContext = BuildConfig.Private.expr()
    }

    /**
     * Arguments holder for [onPerformAction]. Notify that this class might be used as
     * [HttpRequest.Preset].
     */
    open class PerformActionData private constructor() : HttpRequest.Preset {
        @JvmField
        val holder: HttpHolder = BuildConfig.Private.expr()

        /**
         * The action name passed to [PostContext.performAction].
         */
        @JvmField
        val action: String = BuildConfig.Private.expr()

        /**
         * The payload passed to [PostContext.performAction], or `null`.
         */
        @JvmField
        val actionExtra: String? = BuildConfig.Private.expr()

        @JvmField
        val boardName: String? = BuildConfig.Private.expr()

        @JvmField
        val threadNumber: String? = BuildConfig.Private.expr()

        @JvmField
        val postNumber: String = BuildConfig.Private.expr()

        /**
         * Payload stored by [chan.content.model.Post.setExtra], or `null`.
         */
        @JvmField
        val extra: String? = BuildConfig.Private.expr()
    }

    /**
     * Result holder for [onPerformAction].
     */
    class ActionResult {
        /**
         * Replace the payload the decorator sees for this post, as
         * [chan.content.model.Post.setExtra] does while parsing, and rebind it.
         *
         * The replacement lives as long as the post stays loaded; it is not written to the post
         * cache, so a restart shows the parsed payload again. Use it to reflect what an action just
         * changed on the server, and keep durable decorator state in your own [ChanConfiguration]
         * storage.
         *
         * @param extra Payload to store, or `null` to store none.
         * @return This object.
         */
        fun setExtra(extra: String?): ActionResult =
            BuildConfig.Private.expr(extra)

        /**
         * Show a message to the user once the action completes.
         *
         * @param message Message to show.
         * @return This object.
         */
        fun setMessage(message: String?): ActionResult =
            BuildConfig.Private.expr(message)
    }

    companion object {
        /**
         * Return linked [ChanPostDecorator] instance.
         *
         * @param object Linked object: [ChanConfiguration], [ChanPerformer], [ChanLocator],
         * [ChanMarkup] or [ChanPostDecorator].
         * @return [ChanPostDecorator] instance, or `null` if the extension declares none.
         */
        @JvmStatic
        fun <T : ChanPostDecorator> get(`object`: Any?): T? =
            BuildConfig.Private.expr(`object`)
    }
}
