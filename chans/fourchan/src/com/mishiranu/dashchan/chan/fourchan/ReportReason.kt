package com.mishiranu.dashchan.chan.fourchan

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ReportReason(val category: String, val value: String, val title: String) {

	fun getKey(): String {
		val jsonObject = JSONObject()
		try {
			jsonObject.put("category", category)
			jsonObject.put("value", value)
		} catch (e: JSONException) {
			throw RuntimeException(e)
		}
		return jsonObject.toString()
	}

	companion object {
		@JvmStatic
		fun fromKey(key: String?): ReportReason? {
			if (key == null) {
				return null
			}
			return try {
				val jsonObject = JSONObject(key)
				val category = jsonObject.getString("category")
				val value = jsonObject.getString("value")
				ReportReason(category, value, "")
			} catch (e: JSONException) {
				null
			}
		}

		@JvmStatic
		fun parse(json: String): List<ReportReason> {
			return try {
				val jsonArray = JSONArray(json)
				val reportReasons = ArrayList<ReportReason>(jsonArray.length())
				for (i in 0 until jsonArray.length()) {
					val jsonObject = jsonArray.getJSONObject(i)
					val category = jsonObject.getString("category")
					val value = jsonObject.getString("value")
					val title = jsonObject.getString("title")
					reportReasons.add(ReportReason(category, value, title))
				}
				reportReasons
			} catch (e: JSONException) {
				emptyList()
			}
		}

		@JvmStatic
		fun serialize(reportReasons: List<ReportReason>?): String? {
			if (reportReasons.isNullOrEmpty()) {
				return null
			}
			val jsonArray = JSONArray()
			for (reportReason in reportReasons) {
				val jsonObject = JSONObject()
				try {
					jsonObject.put("category", reportReason.category)
					jsonObject.put("value", reportReason.value)
					jsonObject.put("title", reportReason.title)
				} catch (e: JSONException) {
					throw RuntimeException(e)
				}
				jsonArray.put(jsonObject)
			}
			return jsonArray.toString()
		}
	}
}
