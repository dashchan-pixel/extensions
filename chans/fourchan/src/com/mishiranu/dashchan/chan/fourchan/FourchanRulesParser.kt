package com.mishiranu.dashchan.chan.fourchan

import chan.text.ParseException
import chan.text.TemplateParser
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class FourchanRulesParser {
	private val reportReasons = ArrayList<ReportReason>()
	private val categoryReportReasons = ArrayList<ReportReason>()

	private var category: String? = null
	private var categoryTitle: String? = null
	private var value: String? = null

	@Throws(IOException::class, ParseException::class)
	fun parse(input: InputStream): List<ReportReason> {
		PARSER.parse(InputStreamReader(input), this)
		return reportReasons
	}

	private fun closeCategory() {
		val category = this.category
		if (category != null) {
			if (categoryReportReasons.isEmpty()) {
				val categoryTitle = this.categoryTitle
				if (categoryTitle != null) {
					reportReasons.add(ReportReason(category, "", categoryTitle))
				}
			} else {
				reportReasons.addAll(categoryReportReasons)
				categoryReportReasons.clear()
			}
			this.category = null
			this.categoryTitle = null
		}
	}

	companion object {
		private val PARSER = TemplateParser
				.builder<FourchanRulesParser>()
				.equals("input", "name", "cat")
				.open { _, holder, _, attributes ->
					holder.closeCategory()
					holder.category = attributes["value"]
					false
				}
				.name("label")
				.open { _, holder, _, _ -> holder.category != null }
				.content { _, holder, text -> holder.categoryTitle = StringUtils.clearHtml(text) }
				.name("option")
				.open { _, holder, _, attributes ->
					if (holder.category != null) {
						val value = attributes["value"]
						if (value != null) {
							holder.value = value
							return@open true
						}
					}
					false
				}
				.content { _, holder, text ->
					val title = StringUtils.clearHtml(text)
					if (!StringUtils.isEmpty(title)) {
						val category = holder.category
						val value = holder.value
						if (category != null && value != null) {
							holder.categoryReportReasons.add(ReportReason(category, value, title))
						}
					}
				}
				.name("form")
				.close { _, holder, _ -> holder.closeCategory() }
				.prepare()
	}
}
