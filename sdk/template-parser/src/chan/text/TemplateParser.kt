package chan.text

import android.util.Pair
import chan.util.CommonUtils
import java.io.IOException
import java.io.Reader

/**
 * HTML text parser. This parser is a convenient wrapper over the [GroupParser]. Read about wrapped
 * parser before using this one.
 *
 * You can define parsing rules using the following methods:
 *
 * - [SimpleRuleBuilder.name]
 * - [ComplexRuleBuilder.equals]
 * - [ComplexRuleBuilder.starts]
 * - [ComplexRuleBuilder.contains]
 * - [ComplexRuleBuilder.ends]
 *
 * And define reaction rules:
 *
 * - [OpenBuilder.open]
 * - [ContentBuilder.content]
 * - [SimpleBuilder.close]
 * - [InitialBuilder.text]
 *
 * After defining parsing rules you should call [InitialBuilder.prepare] method.
 * Then you can use your parsing calling [parse] method.
 */
class TemplateParser<H> {
    private val openMatchers = HashMap<String, ArrayList<AttributeMatcher<H>>>()
    private val closeMatchers = HashMap<String, ArrayList<AttributeMatcher<H>>>()
    private val textCallbacks = ArrayList<TextCallback<H>>()
    private val newTextCallbacks = ArrayList<NewTextCallback<H>>()
    private var ready = false

    private val buildingMatchers = ArrayList<Pair<String, AttributeMatcher<H>>>()
    private var openCallback: OpenCallback<H>? = null
    private var contentCallback: ContentCallback<H>? = null
    private var closeCallback: CloseCallback<H>? = null

    internal class AttributeMatcher<H>(
        val attribute: String?,
        val value: String?,
        val method: Method?
    ) {
        enum class Method { EQUALS, STARTS, CONTAINS, ENDS }

        var openCallback: OpenCallback<H>? = null
        var contentCallback: ContentCallback<H>? = null
        var closeCallback: CloseCallback<H>? = null

        fun match(attributes: Attributes): Boolean {
            if (method == null) {
                return true
            }
            val value = attributes[attribute!!]
            return when (method) {
                Method.EQUALS -> CommonUtils.equals(value, this.value)
                Method.STARTS -> value != null && value.startsWith(this.value!!)
                Method.CONTAINS -> value != null && value.contains(this.value!!)
                Method.ENDS -> value != null && value.endsWith(this.value!!)
            }
        }
    }

    private fun copyCallbacks() {
        if (openCallback != null || contentCallback != null || closeCallback != null) {
            if ((openCallback != null || contentCallback != null) && closeCallback != null) {
                error(
                    "OpenCallback and ContentCallback can not be defined " +
                            "with CloseCallback at once"
                )
            }
            for (pair in buildingMatchers) {
                if (closeCallback != null && pair.second.attribute != null) {
                    error("Attributed tag definition is not supported for closing tags")
                }
                val map = if (closeCallback != null) closeMatchers else openMatchers
                var matchers = map[pair.first]
                if (matchers == null) {
                    matchers = ArrayList()
                    map[pair.first] = matchers
                }
                pair.second.openCallback = openCallback
                pair.second.contentCallback = contentCallback
                pair.second.closeCallback = closeCallback
                matchers.add(pair.second)
            }
            buildingMatchers.clear()
            openCallback = null
            contentCallback = null
            closeCallback = null
        }
    }

    private fun normalize() {
        for (matchers in openMatchers.values) {
            var i = 0
            var j = matchers.size
            while (i < j) {
                val matcher = matchers[i]
                if (matcher.attribute == null) {
                    // Move to end
                    matchers.removeAt(i)
                    matchers.add(matcher)
                    j--
                } else {
                    i++
                }
            }
        }
    }

    private fun checkReady() {
        check(!ready) { "You can not call this method after prepare() call" }
    }

    fun name(tagName: String): TemplateParser<H> {
        tag(tagName, null, null, null)
        return this
    }

    fun equals(tagName: String, attribute: String, value: String): TemplateParser<H> {
        tag(tagName, attribute, value, AttributeMatcher.Method.EQUALS)
        return this
    }

    fun starts(tagName: String, attribute: String, value: String): TemplateParser<H> {
        tag(tagName, attribute, value, AttributeMatcher.Method.STARTS)
        return this
    }

    fun contains(tagName: String, attribute: String, value: String): TemplateParser<H> {
        tag(tagName, attribute, value, AttributeMatcher.Method.CONTAINS)
        return this
    }

    fun ends(tagName: String, attribute: String, value: String): TemplateParser<H> {
        tag(tagName, attribute, value, AttributeMatcher.Method.ENDS)
        return this
    }

    private fun tag(tagName: String, attribute: String?, value: String?, method: AttributeMatcher.Method?) {
        checkReady()
        copyCallbacks()
        @Suppress("NAME_SHADOWING")
        val value = if (attribute == null) null else value
        buildingMatchers.add(Pair(tagName, AttributeMatcher(attribute, value, method)))
    }

    fun open(openCallback: OpenCallback<H>): TemplateParser<H> {
        checkReady()
        checkHasMatchers()
        this.openCallback = openCallback
        return this
    }

    fun content(contentCallback: ContentCallback<H>): TemplateParser<H> {
        checkReady()
        checkHasMatchers()
        this.contentCallback = contentCallback
        return this
    }

    fun close(closeCallback: CloseCallback<H>): TemplateParser<H> {
        checkReady()
        checkHasMatchers()
        this.closeCallback = closeCallback
        return this
    }

    private fun checkHasMatchers() {
        check(buildingMatchers.isNotEmpty()) {
            "You must define at least one parsing rule before adding this callback"
        }
    }

    fun text(textCallback: TextCallback<H>): TemplateParser<H> {
        checkReady()
        copyCallbacks()
        check(buildingMatchers.isEmpty()) { "This callback can not be used with any parsing rules" }
        textCallbacks.add(textCallback)
        return this
    }

    fun text(textCallback: NewTextCallback<H>): TemplateParser<H> {
        checkReady()
        copyCallbacks()
        check(buildingMatchers.isEmpty()) { "This callback can not be used with any parsing rules" }
        newTextCallbacks.add(textCallback)
        return this
    }

    fun prepare(): TemplateParser<H> {
        checkReady()
        copyCallbacks()
        normalize()
        ready = true
        return this
    }

    /**
     * Starts a new parsing process.
     *
     * @param source String to parse.
     * @param holder Intermediate data holder during parsing process.
     * @throws ParseException when parsing process was interrupted.
     */
    @Throws(ParseException::class)
    fun parse(source: String?, holder: H) {
        if (source == null) return
        check(ready) { "prepare() was not called" }
        try {
            GroupParser.parse(source, Implementation(this, holder))
        } catch (e: FinishException) {
            // finish() was called
        }
    }

    /**
     * Starts a new parsing process.
     *
     * @param reader Input to parse.
     * @param holder Intermediate data holder during parsing process.
     * @throws IOException when reading process was interrupted due to I/O problem.
     * @throws ParseException when parsing process was interrupted.
     */
    @Throws(IOException::class, ParseException::class)
    fun parse(reader: Reader, holder: H) {
        check(ready) { "prepare() was not called" }
        try {
            GroupParser.parse(reader, Implementation(this, holder))
        } catch (e: FinishException) {
            // finish() was called
        }
    }

    /**
     * Attributes holder and parser.
     */
    class Attributes {
        private var groupAttributes: GroupParser.Attributes? = null
        private val lastValues = HashMap<String, String>()

        /**
         * Parses the attribute and returns its value if attribute exists.
         *
         * @param attribute Attribute name.
         * @return Attribute value.
         */
        @Suppress("ReplaceCallWithBinaryOperator")
        operator fun get(attribute: String): String? {
            var value = lastValues[attribute]
            if (value == null) {
                value = groupAttributes!!.get(attribute)
                lastValues[attribute] = value ?: NULL
            }
            return if (value.equals(NULL)) null else value
        }

        internal fun set(attributes: GroupParser.Attributes) {
            this.groupAttributes = attributes
            lastValues.clear()
        }

        companion object {
            private const val NULL = "null"
        }
    }

    /**
     * Parsing process holder.
     */
    class Instance<H> internal constructor(private val implementation: Implementation<H>) {
        /**
         * Finishes the parsing process. Calling this method doesn't interrupt your working callback.
         */
        fun finish() {
            implementation.finish = true
        }
    }

    /**
     * Tag open callback.
     */
    fun interface OpenCallback<H> {
        /**
         * Tag open callback method. See [OpenBuilder.open].
         *
         * @param instance Parser instance holder.
         * @param holder Intermediate data holder.
         * @param tagName Tag name.
         * @param attributes Attributes holder.
         * @return True if parser should parser full tag content.
         * @throws ParseException to interrupt parsing process.
         */
        @Throws(ParseException::class)
        fun onOpen(instance: Instance<H>, holder: H, tagName: String, attributes: Attributes): Boolean
    }

    /**
     * Tag full content callback.
     */
    fun interface ContentCallback<H> {
        /**
         * Tag full content method. See [ContentBuilder.content].
         *
         * @param instance Parser instance holder.
         * @param holder Intermediate data holder.
         * @param text Full tag content.
         * @throws ParseException to interrupt parsing process.
         */
        @Throws(ParseException::class)
        fun onContent(instance: Instance<H>, holder: H, text: String)
    }

    /**
     * Tag close callback.
     */
    fun interface CloseCallback<H> {
        /**
         * Tag close callback method. See [SimpleBuilder.close].
         *
         * @param instance Parser instance holder.
         * @param holder Intermediate data holder.
         * @param tagName Tag name.
         * @throws ParseException to interrupt parsing process.
         */
        @Throws(ParseException::class)
        fun onClose(instance: Instance<H>, holder: H, tagName: String)
    }

    /**
     * Text between tags callback.
     */
    fun interface NewTextCallback<H> {
        @Throws(ParseException::class)
        fun onText(instance: Instance<H>, holder: H, source: CharSequence)
    }

    fun interface TextCallback<H> {
        /**
         * Text between tags callback method. See [InitialBuilder.text].
         *
         * @param instance Parser instance holder.
         * @param holder Intermediate data holder.
         * @param source Source string.
         * @throws ParseException to interrupt parsing process.
         */
        @Throws(ParseException::class)
        fun onText(instance: Instance<H>, holder: H, source: String, start: Int, end: Int)
    }

    private class FinishException : ParseException()

    internal class Implementation<H>(
        val parser: TemplateParser<H>,
        val holder: H
    ) : GroupParser.Callback {
        val attributes = Attributes()
        val instance = Instance(this)

        var workMatcher: AttributeMatcher<H>? = null
        var finish = false

        @Throws(FinishException::class)
        private fun checkFinish() {
            if (finish) {
                throw FinishException()
            }
        }

        @Throws(ParseException::class)
        override fun onStartElement(
            parser: GroupParser,
            tagName: String,
            attributes: GroupParser.Attributes
        ): Boolean {
            val matchers = this.parser.openMatchers[tagName]
            if (matchers != null) {
                this.attributes.set(attributes)
                for (matcher in matchers) {
                    if (matcher.match(this.attributes)) {
                        val readContent: Boolean
                        if (matcher.openCallback != null) {
                            readContent = matcher.openCallback!!.onOpen(
                                instance, holder, tagName, this.attributes
                            )
                            checkFinish()
                        } else {
                            readContent = true
                        }
                        if (readContent) {
                            workMatcher = matcher
                            return true
                        }
                    }
                }
            }
            return false
        }

        @Throws(ParseException::class)
        override fun onEndElement(parser: GroupParser, tagName: String) {
            val matchers = this.parser.closeMatchers[tagName]
            if (matchers != null) {
                for (matcher in matchers) {
                    matcher.closeCallback!!.onClose(instance, holder, tagName)
                    checkFinish()
                }
            }
        }

        @Throws(ParseException::class)
        override fun onGroupComplete(parser: GroupParser, text: String) {
            workMatcher?.contentCallback?.let {
                it.onContent(instance, holder, text)
                checkFinish()
            }
        }

        @Throws(ParseException::class)
        override fun onText(parser: GroupParser, text: CharSequence) {
            for (textCallback in this.parser.textCallbacks) {
                textCallback.onText(instance, holder, text.toString(), 0, text.length)
                checkFinish()
            }
            for (newTextCallback in this.parser.newTextCallbacks) {
                newTextCallback.onText(instance, holder, text)
                checkFinish()
            }
        }
    }

    /**
     * Parser builder.
     */
    interface SimpleRuleBuilder<H> {
        /**
         * Indicates the parser to react on [tagName] tags.
         *
         * @param tagName Tag name.
         * @return Parser builder.
         */
        fun name(tagName: String): SimpleBuilder<H>
    }

    /**
     * Parser builder.
     */
    interface ComplexSimpleRuleBuilder<H> {
        /**
         * Indicates the parser to react on [tagName] tags.
         *
         * @param tagName Tag name.
         * @return Parser builder.
         */
        fun name(tagName: String): ComplexBuilder<H>
    }

    /**
     * Parser builder.
     */
    interface ComplexRuleBuilder<H> {
        /**
         * Indicates the parser to react on [tagName] tags which has an [attribute]
         * equals [value].
         *
         * @param tagName Tag name.
         * @param attribute Attribute name.
         * @param value Attribute value.
         * @return Parser builder.
         */
        fun equals(tagName: String, attribute: String, value: String): ComplexBuilder<H>

        /**
         * Indicates the parser to react on [tagName] tags which has an [attribute]
         * starts with [value].
         *
         * @param tagName Tag name.
         * @param attribute Attribute name.
         * @param value Attribute value.
         * @return Parser builder.
         */
        fun starts(tagName: String, attribute: String, value: String): ComplexBuilder<H>

        /**
         * Indicates the parser to react on [tagName] tags which has an [attribute]
         * contains [value].
         *
         * @param tagName Tag name.
         * @param attribute Attribute name.
         * @param value Attribute value.
         * @return Parser builder.
         */
        fun contains(tagName: String, attribute: String, value: String): ComplexBuilder<H>

        /**
         * Indicates the parser to react on [tagName] tags which has an [attribute]
         * ends with [value].
         *
         * @param tagName Tag name.
         * @param attribute Attribute name.
         * @param value Attribute value.
         * @return Parser builder.
         */
        fun ends(tagName: String, attribute: String, value: String): ComplexBuilder<H>
    }

    /**
     * Parser builder.
     */
    interface OpenBuilder<H> {
        /**
         * Defines a reaction callback when tag opened. This callback determines whether parser should parse
         * the full tag content and call content callback or not depending on the return value. If you don't
         * specify this callback parser will parse full content anyway.
         *
         * @param openCallback Tag open callback.
         * @return Parser builder.
         * @see GroupParser.Callback.onStartElement
         */
        fun open(openCallback: OpenCallback<H>): ContentBuilder<H>

        /**
         * Defines a reaction callback when full tag content parsed. This callback may be not called if
         * open callback returned a `false` value.
         *
         * @param contentCallback Tag content callback.
         * @return Parser builder.
         * @see GroupParser.Callback.onGroupComplete
         */
        fun content(contentCallback: ContentCallback<H>): InitialBuilder<H>
    }

    /**
     * Parser builder.
     */
    interface InitialBuilder<H> : SimpleRuleBuilder<H>, ComplexRuleBuilder<H> {
        /**
         * Defines a reaction callback on text between tags. This callback doesn't depend on any rules.
         *
         * @param textCallback Text between tags callback.
         * @return Parser builder.
         * @see GroupParser.Callback.onText
         */
        fun text(textCallback: TextCallback<H>): InitialBuilder<H>
        fun text(textCallback: NewTextCallback<H>): InitialBuilder<H>

        /**
         * Creates a parser from builder.
         *
         * @return Parser instance.
         */
        fun prepare(): TemplateParser<H>
    }

    /**
     * Parser builder.
     */
    interface SimpleBuilder<H> : SimpleRuleBuilder<H>, ComplexRuleBuilder<H>, OpenBuilder<H> {
        /**
         * Defines a reaction callback when tag closed. Parser can react only on [name] rule.
         *
         * @param closeCallback Tag close callback.
         * @return Parser builder.
         * @see GroupParser.Callback.onEndElement
         */
        fun close(closeCallback: CloseCallback<H>): InitialBuilder<H>
    }

    /**
     * Parser builder.
     */
    interface ComplexBuilder<H> : ComplexSimpleRuleBuilder<H>, ComplexRuleBuilder<H>, OpenBuilder<H>

    /**
     * Parser builder.
     */
    interface ContentBuilder<H> : InitialBuilder<H> {
        /**
         * Defines a reaction callback when full tag content parsed. This callback may be not called if
         * open callback returned a `false` value.
         *
         * @param contentCallback Tag content callback.
         * @return Parser builder.
         * @see GroupParser.Callback.onGroupComplete
         */
        fun content(contentCallback: ContentCallback<H>): InitialBuilder<H>
    }

    private val contentBuilderImpl: ContentBuilder<H> = object : ContentBuilder<H> {
        override fun name(tagName: String): SimpleBuilder<H> {
            this@TemplateParser.name(tagName)
            return simpleBuilderImpl
        }

        override fun equals(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.equals(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun starts(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.starts(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun contains(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.contains(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun ends(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.ends(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun text(textCallback: TextCallback<H>): InitialBuilder<H> {
            this@TemplateParser.text(textCallback)
            return contentBuilderImpl
        }

        override fun text(textCallback: NewTextCallback<H>): InitialBuilder<H> {
            this@TemplateParser.text(textCallback)
            return contentBuilderImpl
        }

        override fun content(contentCallback: ContentCallback<H>): InitialBuilder<H> {
            this@TemplateParser.content(contentCallback)
            return contentBuilderImpl
        }

        override fun prepare(): TemplateParser<H> {
            this@TemplateParser.prepare()
            return this@TemplateParser
        }

        fun open(openCallback: OpenCallback<H>): ContentBuilder<H> {
            this@TemplateParser.open(openCallback)
            return contentBuilderImpl
        }
    }

    private val simpleBuilderImpl: SimpleBuilder<H> = object : SimpleBuilder<H> {
        override fun name(tagName: String): SimpleBuilder<H> {
            this@TemplateParser.name(tagName)
            return simpleBuilderImpl
        }

        override fun equals(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.equals(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun starts(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.starts(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun contains(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.contains(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun ends(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.ends(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun open(openCallback: OpenCallback<H>): ContentBuilder<H> {
            this@TemplateParser.open(openCallback)
            return contentBuilderImpl
        }

        override fun content(contentCallback: ContentCallback<H>): InitialBuilder<H> {
            this@TemplateParser.content(contentCallback)
            return contentBuilderImpl
        }

        override fun close(closeCallback: CloseCallback<H>): InitialBuilder<H> {
            this@TemplateParser.close(closeCallback)
            return contentBuilderImpl
        }
    }

    private val complexBuilderImpl: ComplexBuilder<H> = object : ComplexBuilder<H> {
        override fun name(tagName: String): ComplexBuilder<H> {
            this@TemplateParser.name(tagName)
            return complexBuilderImpl
        }

        override fun equals(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.equals(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun starts(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.starts(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun contains(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.contains(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun ends(tagName: String, attribute: String, value: String): ComplexBuilder<H> {
            this@TemplateParser.ends(tagName, attribute, value)
            return complexBuilderImpl
        }

        override fun open(openCallback: OpenCallback<H>): ContentBuilder<H> {
            this@TemplateParser.open(openCallback)
            return contentBuilderImpl
        }

        override fun content(contentCallback: ContentCallback<H>): InitialBuilder<H> {
            this@TemplateParser.content(contentCallback)
            return contentBuilderImpl
        }
    }

    companion object {
        /**
         * Creates a new parser builder.
         *
         * @param H Holder object type.
         * @return Template parser builder.
         */
        @JvmStatic
        fun <H> builder(): InitialBuilder<H> {
            return TemplateParser<H>().contentBuilderImpl
        }
    }
}
