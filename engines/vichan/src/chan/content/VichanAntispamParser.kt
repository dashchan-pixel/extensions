package chan.content

import android.util.Pair
import chan.http.RequestEntity
import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

open class VichanAntispamParser private constructor(
    source: String?,
    entity: RequestEntity,
    vararg ignoreFields: String,
) {
    private val ignoreFields = HashSet<String>()
    private val fields = ArrayList<Pair<String?, String?>>()

    private var formParsing: Boolean = false
    private var fieldName: String? = null

    init {
        Collections.addAll(this.ignoreFields, *ignoreFields)
        PARSER.parse(source, this)
        for (field in fields) {
            entity.add(field.first, field.second)
        }
    }

    companion object {
        @JvmStatic
        @Throws(ParseException::class)
        fun parseAndApply(
            source: String?,
            entity: RequestEntity,
            vararg ignoreFields: String,
        ) {
            VichanAntispamParser(source, entity, *ignoreFields)
        }

        private val PARSER: TemplateParser<VichanAntispamParser> =
            TemplateParser
                .builder<VichanAntispamParser>()
                .equals("form", "name", "post")
                .open { instance, holder, tagName, attributes ->
                    holder.formParsing = true
                    false
                }.name("input")
                .open { instance, holder, tagName, attributes ->
                    if (holder.formParsing) {
                        val name = attributes.get("name")
                        if (!holder.ignoreFields.contains(name)) {
                            val value = StringUtils.unescapeHtml(attributes.get("value"))
                            holder.fields.add(Pair(name, value))
                        }
                    }
                    false
                }.name("textarea")
                .open { instance, holder, tagName, attributes ->
                    if (holder.formParsing) {
                        val name = attributes.get("name")
                        if (!holder.ignoreFields.contains(name)) {
                            holder.fieldName = name
                            return@open true
                        }
                    }
                    false
                }.content { instance, holder, text ->
                    val value = StringUtils.unescapeHtml(text)
                    holder.fields.add(Pair(holder.fieldName, value))
                }.name("form")
                .close { instance, holder, tagName ->
                    if (holder.formParsing) {
                        instance.finish()
                    }
                }.prepare()
    }
}
