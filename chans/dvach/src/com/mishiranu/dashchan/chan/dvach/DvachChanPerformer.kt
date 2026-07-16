package com.mishiranu.dashchan.chan.dvach

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.content.RedirectException
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.ThreadSummary
import chan.http.CookieBuilder
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.MultipartEntity
import chan.http.UrlEncodedEntity
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import java.io.IOException
import java.net.HttpURLConnection
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.regex.Pattern
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DvachChanPerformer : ChanPerformer() {
	init {
		try {
			registerFirewallResolver(DvachFirewallResolver())
		} catch (e: LinkageError) {
			chan.util.CommonUtils.writeLog("Dvach init", e)
		}
	}

	private val configuration: DvachChanConfiguration
		get() = ChanConfiguration.get(this)

	private val locator: DvachChanLocator
		get() = ChanLocator.get(this)

	private fun buildCookies(captchaPassCookie: String?): CookieBuilder = CookieBuilder()
			.append(COOKIE_USERCODE_AUTH, configuration.getCookie(COOKIE_USERCODE_AUTH))
			.append(COOKIE_PASSCODE_AUTH, captchaPassCookie)

	private fun buildCookiesWithCaptchaPass(): CookieBuilder =
			buildCookies(configuration.getCookie(COOKIE_PASSCODE_AUTH))

	private val mobileApiLock = Any()

	@Throws(HttpException::class)
	private fun readMobileApi(request: HttpRequest): HttpResponse {
		synchronized(mobileApiLock) {
			var lastException: HttpException? = null
			for (delay in MOBILE_API_DELAYS) {
				if (delay > 0) {
					request.setDelay(delay)
				}
				try {
					return request.perform()
				} catch (e: HttpException) {
					if (e.isHttpException && e.responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
						lastException = e
						// Retry in loop
					} else {
						throw e
					}
				}
			}
			throw lastException!!
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
		val uri = locator.buildPath(data.boardName, (if (data.isCatalog) "catalog"
				else if (data.pageNumber == 0) "index" else data.pageNumber.toString()) + ".json")
		val response = HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
				.setValidator(data.validator).perform()
		val boardConfiguration = DvachModelMapper.BoardConfiguration()
		val threads = ArrayList<Posts>()
		var boardSpeed = 0
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					reader.startObject()
					while (!reader.endStruct()) {
						val name = reader.nextName()
						if (!boardConfiguration.handle(reader, name)) {
							when (name) {
								"threads" -> {
									val sageEnabled = boardConfiguration.sageEnabled
											?: configuration.isSageEnabled(data.boardName)
									reader.startArray()
									while (!reader.endStruct()) {
										threads.add(DvachModelMapper.createThread(reader,
												locator, data.boardName, sageEnabled))
									}
								}
								"board_speed" -> boardSpeed = reader.nextInt()
								"board" -> {
									reader.startObject()
									while (!reader.endStruct()) {
										if (!boardConfiguration.handle(reader, reader.nextName())) {
											reader.skip()
										}
									}
								}
								else -> reader.skip()
							}
						}
					}
					configuration.updateFromThreadsPostsJson(data.boardName, boardConfiguration)
					return ReadThreadsResult(threads).setBoardSpeed(boardSpeed)
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class, RedirectException::class)
	override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
		val usePartialApi = data.partialThreadLoading && data.lastPostNumber != null
		var tryReadStatic = false
		try {
			return ReadPostsResult(onReadPosts(data, usePartialApi, false))
		} catch (e: HttpException) {
			val responseCode = e.responseCode
			if (responseCode in 500..599 && usePartialApi) {
				tryReadStatic = true
			} else if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
				throw e
			}
		}
		if (tryReadStatic) {
			try {
				return ReadPostsResult(onReadPosts(data, false, false))
			} catch (e: HttpException) {
				if (e.responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
					throw e
				}
			}
		}
		return ReadPostsResult(onReadPosts(data, false, true)).setFullThread(true)
	}

	@Throws(HttpException::class, InvalidResponseException::class, RedirectException::class)
	private fun onReadPosts(data: ReadPostsData, usePartialApi: Boolean, archive: Boolean): Posts {
		val locator = this.locator
		val configuration = this.configuration
		var handler = HttpRequest.RedirectHandler.BROWSER
		val archiveThreadUri = arrayOfNulls<Uri>(1)
		val uri: Uri = when {
			usePartialApi -> locator.createMobileApiV2Uri("after", data.boardName, data.threadNumber,
					if (data.lastPostNumber == null) data.threadNumber
					else (data.lastPostNumber.toInt() + 1).toString())
			archive -> {
				handler = HttpRequest.RedirectHandler { response ->
					archiveThreadUri[0] = response.redirectedUri
					HttpRequest.RedirectHandler.BROWSER.onRedirect(response)
				}
				locator.buildPath(data.boardName, "arch", "res", "${data.threadNumber}.json")
			}
			else -> locator.buildPath(data.boardName, "res", "${data.threadNumber}.json")
		}
		val request = HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
				.setValidator(data.validator).setRedirectHandler(handler)
		val response = if (usePartialApi) readMobileApi(request) else request.perform()
		var archiveDate: String? = null
		if (archive) {
			val archivePath = archiveThreadUri[0]?.path ?: throw HttpException.createNotFoundException()
			val index1 = archivePath.indexOf("arch/")
			val index2 = archivePath.indexOf("/res")
			if (index1 >= 0 && index2 - index1 > 5) {
				archiveDate = archivePath.substring(index1 + 5, index2)
			} else {
				throw HttpException.createNotFoundException()
			}
		}
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					try {
						return parsePostsResponse(reader, data, usePartialApi, archive, archiveDate)
					} catch (e: ParseException) {
						if (archive && response.readString().contains("Доска не существует")) {
							throw HttpException.createNotFoundException()
						} else {
							throw e
						}
					}
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(IOException::class, ParseException::class, HttpException::class,
			InvalidResponseException::class, RedirectException::class)
	private fun parsePostsResponse(reader: JsonSerial.Reader, data: ReadPostsData,
			usePartialApi: Boolean, archive: Boolean, archiveDate: String?): Posts {
		val locator = this.locator
		val configuration = this.configuration
		if (usePartialApi) {
			var posts: List<Post> = emptyList()
			var uniquePosters = 0
			var result = 0
			reader.startObject()
			while (!reader.endStruct()) {
				when (reader.nextName()) {
					"posts" -> {
						posts = DvachModelMapper.createPosts(reader, locator, data.boardName, null,
								configuration.isSageEnabled(data.boardName), null)
						if (posts.isNotEmpty()) {
							val post = posts[0]
							val parentPostNumber = post.parentPostNumber
							if (parentPostNumber != null && parentPostNumber != data.threadNumber) {
								throw RedirectException.toThread(data.boardName,
										parentPostNumber, post.postNumber)
							}
						}
					}
					"unique_posters" -> uniquePosters = reader.nextInt()
					"error" -> throw handleMobileApiV2Error(reader)
					"result" -> result = reader.nextInt()
					else -> reader.skip()
				}
			}
			if (result == 0) {
				throw InvalidResponseException()
			}
			return Posts(posts).setUniquePosters(uniquePosters)
		} else if (archive && archiveDate == "wakaba") {
			val posts = ArrayList<Post>()
			reader.startObject()
			while (!reader.endStruct()) {
				when (reader.nextName()) {
					"thread" -> {
						reader.startArray()
						while (!reader.endStruct()) {
							reader.startArray()
							// Array of arrays with a single post object (weird)
							posts.add(DvachModelMapper.createWakabaArchivePost(reader,
									this, data.boardName))
							while (!reader.endStruct()) {
								// Skip the rest items
								reader.skip()
							}
						}
					}
					else -> reader.skip()
				}
			}
			return Posts(posts)
		} else {
			val boardConfiguration = DvachModelMapper.BoardConfiguration()
			var posts: ArrayList<Post>? = null
			var uniquePosters = 0
			reader.startObject()
			while (!reader.endStruct()) {
				val name = reader.nextName()
				if (!boardConfiguration.handle(reader, name)) {
					when (name) {
						"threads" -> {
							val sageEnabled = boardConfiguration.sageEnabled
									?: configuration.isSageEnabled(data.boardName)
							reader.startArray()
							reader.startObject()
							while (!reader.endStruct()) {
								when (reader.nextName()) {
									"posts" -> posts = DvachModelMapper.createPosts(reader,
											locator, data.boardName, archiveDate, sageEnabled, null)
									else -> reader.skip()
								}
							}
							while (!reader.endStruct()) {
								reader.skip()
							}
						}
						"board" -> {
							reader.startObject()
							while (!reader.endStruct()) {
								if (!boardConfiguration.handle(reader, reader.nextName())) {
									reader.skip()
								}
							}
						}
						"unique_posters" -> uniquePosters = reader.nextInt()
						else -> reader.skip()
					}
				}
			}
			configuration.updateFromThreadsPostsJson(data.boardName, boardConfiguration)
			return Posts(posts).setUniquePosters(uniquePosters)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadSinglePost(data: ReadSinglePostData): ReadSinglePostResult {
		val uri = locator.createMobileApiV2Uri("post", data.boardName, data.postNumber)
		val response = readMobileApi(HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass()))
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					var post: Post? = null
					var result = 0
					reader.startObject()
					while (!reader.endStruct()) {
						when (reader.nextName()) {
							"post" -> post = DvachModelMapper.createPost(reader, this, data.boardName,
									null, configuration.isSageEnabled(data.boardName), null)
							"error" -> throw handleMobileApiV2Error(reader)
							"result" -> result = reader.nextInt()
							else -> reader.skip()
						}
					}
					if (result == 0 || post == null) {
						throw InvalidResponseException()
					}
					return ReadSinglePostResult(post)
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(IOException::class, ParseException::class)
	private fun handleMobileApiV2Error(reader: JsonSerial.Reader): HttpException {
		var code = 0
		var error = ""
		reader.startObject()
		while (!reader.endStruct()) {
			when (reader.nextName()) {
				"code" -> code = Math.abs(reader.nextInt())
				"error" -> error = reader.nextString()
				else -> reader.skip()
			}
		}
		return when (code) {
			// ErrorNotFound, ErrorNoBoard, ErrorNoParent, ErrorNoPost
			667, 2, 3, 31 -> HttpException.createNotFoundException()
			else -> HttpException(code, error)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadSearchPosts(data: ReadSearchPostsData): ReadSearchPostsResult {
		val locator = this.locator
		val configuration = this.configuration
		if (data.searchQuery.startsWith("#")) {
			val uri = locator.buildPath(data.boardName, "catalog.json")
			val response = HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass()).perform()
			try {
				response.open().use { input ->
					JsonSerial.reader(input).use { reader ->
						val tag = data.searchQuery.substring(1)
						val posts = ArrayList<Post>()
						reader.startObject()
						while (!reader.endStruct()) {
							when (reader.nextName()) {
								"threads" -> {
									reader.startArray()
									while (!reader.endStruct()) {
										val extra = DvachModelMapper.Extra()
										val post = DvachModelMapper.createPost(reader, this,
												data.boardName, null,
												configuration.isSageEnabled(data.boardName), extra)
										if (tag == extra.tags) {
											posts.add(post)
										}
									}
								}
								else -> reader.skip()
							}
						}
						return ReadSearchPostsResult(posts)
					}
				}
			} catch (e: ParseException) {
				throw InvalidResponseException(e)
			} catch (e: IOException) {
				throw response.fail(e)
			}
		} else {
			var uri = locator.buildPath("user/search")
			val entity = MultipartEntity("board", data.boardName, "text", data.searchQuery)
			var response = HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
					.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform()

			val postsNumbers = DvachModelMapper.createPostsFromHtml(response.readString())
			val posts = ArrayList<Post>()

			for (number in postsNumbers) {
				uri = locator.createMobileApiV2Uri("post", data.boardName, number)
				response = HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
						.setGetMethod().setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform()
				try {
					response.open().use { input ->
						JsonSerial.reader(input).use { reader ->
							reader.startObject()
							while (!reader.endStruct()) {
								when (reader.nextName()) {
									"post" -> posts.add(DvachModelMapper.createPost(reader, this,
											data.boardName, null,
											configuration.isSageEnabled(data.boardName), null))
									else -> reader.skip()
								}
							}
						}
					}
				} catch (e: ParseException) {
					throw InvalidResponseException()
				} catch (e: IOException) {
					throw response.fail(e)
				}
			}
			return ReadSearchPostsResult(posts)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
		val uri = locator.buildPath("/api/mobile/v2/boards")
		val jsonArray = try {
			JSONArray(HttpRequest(uri, data).setGetMethod()
					.addCookie(buildCookiesWithCaptchaPass()).perform().readString())
		} catch (e: JSONException) {
			throw InvalidResponseException()
		}

		val boardsMap = HashMap<String, ArrayList<Board>>()
		try {
			for (i in 0 until jsonArray.length()) {
				val jsonObject = jsonArray.getJSONObject(i)
				val category = jsonObject.optString("category")
				val boardName = jsonObject.optString("id")
				val title = jsonObject.optString("name")
				val description = jsonObject.optString("info")
				val defaultName = jsonObject.optString("default_name")
				val bumpLimit = jsonObject.optInt("bump_limit", -1)

				if (!StringUtils.isEmpty(category) && !StringUtils.isEmpty(boardName) &&
						!StringUtils.isEmpty(title)) {
					val boards = boardsMap.getOrPut(category) { ArrayList() }
					boards.add(Board(boardName, title, configuration.transformBoardDescription(description)))
					configuration.updateFromBoardsJson(boardName, defaultName,
							if (bumpLimit > 0) bumpLimit else null)
				}
			}
		} catch (e: JSONException) {
			throw InvalidResponseException()
		}

		val boardCategories = ArrayList<BoardCategory>()
		for (title in PREFERRED_BOARDS_ORDER) {
			val boards = boardsMap[title]
			if (boards != null) {
				boards.sort()
				boardCategories.add(BoardCategory(title, boards))
			}
		}
		return ReadBoardsResult(boardCategories)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadUserBoards(data: ReadUserBoardsData): ReadUserBoardsResult {
		val uri = locator.buildPath("/api/mobile/v2/boards")
		val jsonArray = try {
			JSONArray(HttpRequest(uri, data).setGetMethod()
					.addCookie(buildCookiesWithCaptchaPass()).perform().readString())
		} catch (e: JSONException) {
			throw InvalidResponseException()
		}

		val boards = ArrayList<Board>()
		try {
			for (i in 0 until jsonArray.length()) {
				val jsonObject = jsonArray.getJSONObject(i)
				if (jsonObject.optString("category") == "Пользовательские") {
					val boardName = jsonObject.optString("id")
					val title = jsonObject.optString("name")
					val description = jsonObject.optString("info")
					if (!StringUtils.isEmpty(boardName) && !StringUtils.isEmpty(title)) {
						boards.add(Board(boardName, title,
								configuration.transformBoardDescription(description)))
					}
				}
			}
		} catch (e: JSONException) {
			throw InvalidResponseException()
		}
		return ReadUserBoardsResult(boards)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadThreadSummaries(data: ReadThreadSummariesData): ReadThreadSummariesResult {
		if (data.type != ReadThreadSummariesData.TYPE_ARCHIVED_THREADS) {
			return super.onReadThreadSummaries(data)
		}
		val locator = this.locator
		var uri = locator.buildPath(data.boardName, "arch", "index.json")
		var response = HttpRequest(uri, data).perform()
		val pages = ArrayList<Int>()
		var threadSummaries: List<ThreadSummary> = emptyList()
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					reader.startObject()
					while (!reader.endStruct()) {
						when (reader.nextName()) {
							"pages" -> {
								reader.startArray()
								while (!reader.endStruct()) {
									pages.add(reader.nextInt())
								}
							}
							"threads" -> {
								if (data.pageNumber > 0 && pages.isNotEmpty()) {
									reader.skip()
								} else {
									threadSummaries = DvachModelMapper.createArchive(reader, data.boardName)
								}
							}
							else -> reader.skip()
						}
					}
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
		if (data.pageNumber > 0) {
			if (data.pageNumber + 1 > pages.size) {
				return ReadThreadSummariesResult()
			}
			val pageNumber = pages[pages.size - data.pageNumber - 1]
			uri = locator.buildPath(data.boardName, "arch", "$pageNumber.json")
			response = HttpRequest(uri, data).perform()
			threadSummaries = emptyList()
			try {
				response.open().use { input ->
					JsonSerial.reader(input).use { reader ->
						reader.startObject()
						while (!reader.endStruct()) {
							when (reader.nextName()) {
								"threads" -> threadSummaries =
										DvachModelMapper.createArchive(reader, data.boardName)
								else -> reader.skip()
							}
						}
					}
				}
			} catch (e: ParseException) {
				throw InvalidResponseException(e)
			} catch (e: IOException) {
				throw response.fail(e)
			}
		}
		return ReadThreadSummariesResult(threadSummaries)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadPostsCount(data: ReadPostsCountData): ReadPostsCountResult {
		val uri = locator.createMobileApiV2Uri("info", data.boardName, data.threadNumber)
		val response = readMobileApi(HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass()))
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					var count = 0
					var result = 0
					reader.startObject()
					while (!reader.endStruct()) {
						when (reader.nextName()) {
							"thread" -> {
								reader.startObject()
								while (!reader.endStruct()) {
									when (reader.nextName()) {
										"posts" -> count = reader.nextInt() + 1
										else -> reader.skip()
									}
								}
							}
							"error" -> throw handleMobileApiV2Error(reader)
							"result" -> result = reader.nextInt()
							else -> reader.skip()
						}
					}
					if (result == 0) {
						throw InvalidResponseException()
					}
					return ReadPostsCountResult(count)
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(HttpException::class)
	override fun onReadContent(data: ReadContentData): ReadContentResult =
			ReadContentResult(HttpRequest(data.uri, data.direct)
					.addCookie(buildCookiesWithCaptchaPass()).perform())

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onCheckAuthorization(data: CheckAuthorizationData): CheckAuthorizationResult =
			CheckAuthorizationResult(readCaptchaPass(data, data.authorizationData[0]) != null)

	private var lastCaptchaPassData: String? = null
	private var lastCaptchaPassCookie: String? = null

	@Throws(HttpException::class, InvalidResponseException::class)
	private fun readCaptchaPass(preset: HttpRequest.Preset, captchaPassData: String): String {
		lastCaptchaPassData = null
		lastCaptchaPassCookie = null
		val configuration = this.configuration
		configuration.revokeMaxFilesCount()
		configuration.storeCookie(COOKIE_PASSCODE_AUTH, null, null)
		val uri = locator.buildPath("/user/passlogin")
		val entity = UrlEncodedEntity("passcode", captchaPassData)

		val response = try {
			HttpRequest(uri, preset).addCookie(buildCookies(null))
					.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.NONE).perform()
		} catch (e: HttpException) {
			throw InvalidResponseException()
		}

		val captchaPassCookie = response?.getCookieValue(COOKIE_PASSCODE_AUTH)
		if (StringUtils.isEmpty(captchaPassCookie)) {
			throw InvalidResponseException()
		}
		lastCaptchaPassData = captchaPassData
		lastCaptchaPassCookie = captchaPassCookie
		configuration.setMaxFilesCount(PASSCODUBOYAR_MAX_FILES)
		configuration.storeCookie(COOKIE_PASSCODE_AUTH, captchaPassCookie, "Passcode Auth")
		return captchaPassCookie!!
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
		val uri = locator.buildPath("api", "captcha", "settings", data.boardName)
		val jsonObject = try {
			JSONObject(readMobileApi(HttpRequest(uri, data).addCookie(buildCookies(null))).readString())
		} catch (e: JSONException) {
			throw InvalidResponseException(e)
		}
		if (jsonObject.optInt("enabled", 1) == 0) {
			return ReadCaptchaResult(CaptchaState.SKIP, null)
		}
		return onReadCaptcha(data, data.captchaPass?.get(0), true)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	private fun onReadCaptcha(data: ReadCaptchaData, captchaPassData: String?,
			mayUseLastCaptchaPassCookie: Boolean): ReadCaptchaResult {
		val locator = this.locator
		val configuration = this.configuration
		var captchaPassCookie: String? = null
		var mayRelogin = false
		if (captchaPassData != null) {
			if (mayUseLastCaptchaPassCookie && captchaPassData == lastCaptchaPassData) {
				captchaPassCookie = lastCaptchaPassCookie
				mayRelogin = true
			} else {
				captchaPassCookie = readCaptchaPass(data, captchaPassData)
			}
		}

		val remoteCaptchaType = DvachChanConfiguration.CAPTCHA_TYPES[data.captchaType]
				?: throw RuntimeException()

		val uriBuilder = locator.buildPath("api", "captcha", remoteCaptchaType, "id").buildUpon()
		uriBuilder.appendQueryParameter("board", data.boardName)
		if (data.threadNumber != null) {
			uriBuilder.appendQueryParameter("thread", data.threadNumber)
		}
		val uri = uriBuilder.build()
		var jsonObject: JSONObject? = null
		var exception: HttpException? = null
		try {
			jsonObject = JSONObject(readMobileApi(HttpRequest(uri, data)
					.addCookie(buildCookies(captchaPassCookie))).readString())
		} catch (e: JSONException) {
			// Ignore exception
		} catch (e: HttpException) {
			if (!e.isHttpException) {
				throw e
			}
			exception = e
			if (e.responseCode == 429) {
				mayRelogin = false
			}
		}

		val apiResult = jsonObject?.let { CommonUtils.optJsonString(it, "result") }
		if (apiResult == "3") {
			configuration.setMaxFilesCountEnabled(false)
			return ReadCaptchaResult(CaptchaState.SKIP, null)
		} else if (apiResult == "2") {
			configuration.setMaxFilesCountEnabled(true)
			return makeCaptchaPassResult(captchaPassCookie)
		} else {
			if (mayRelogin) {
				return onReadCaptcha(data, captchaPassData, false)
			}
			configuration.setMaxFilesCountEnabled(false)
			val id = jsonObject?.let { CommonUtils.optJsonString(it, "id") }
			if (id != null) {
				val captchaData = CaptchaData()
				val result: ReadCaptchaResult
				when {
					DvachChanConfiguration.CAPTCHA_TYPE_2CH_CAPTCHA == data.captchaType -> {
						result = ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData)
						captchaData.put(CaptchaData.CHALLENGE, id)
						val imageUri = locator.buildPath("api", "captcha", remoteCaptchaType, "show")
								.buildUpon().appendQueryParameter("id", id).build()
						var captchaImage: Bitmap?
						var loadCaptchaImageAttempts = 3
						while (true) {
							try {
								captchaImage = HttpRequest(imageUri, data).perform().readBitmap()
								break
							} catch (e: HttpException) {
								loadCaptchaImageAttempts--
								if (loadCaptchaImageAttempts == 0 ||
										e.responseCode != HttpURLConnection.HTTP_INTERNAL_ERROR) {
									throw e
								}
								try {
									Thread.sleep(500)
								} catch (ex: InterruptedException) {
									throw e
								}
							}
						}
						if (captchaImage == null) {
							throw InvalidResponseException()
						}
						result.setImage(captchaImage)

						if (configuration.isFullKeyboardForCaptchaEnabled()) {
							result.setInput(ChanConfiguration.Captcha.Input.ALL)
						} else {
							result.setInput(when (jsonObject.optString("input")) {
								"numeric" -> ChanConfiguration.Captcha.Input.NUMERIC
								"english" -> ChanConfiguration.Captcha.Input.LATIN
								else -> ChanConfiguration.Captcha.Input.ALL
							})
						}
					}
					DvachChanConfiguration.CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA == data.captchaType -> {
						if (data.mayShowLoadButton) {
							return ReadCaptchaResult(CaptchaState.NEED_LOAD, null)
						}
						val retriever = DvachEmojiCaptchaProvider.DvachEmojiCaptchaAnswerRetriever {
								task, keyboardImages ->
							try {
								requireUserImageSingleChoice(-1, keyboardImages,
										configuration.resources.getString(R.string.emoji_captcha_input), task)
							} catch (e: HttpException) {
								-1
							}
						}
						return DvachEmojiCaptchaProvider(data, locator, id, retriever).loadEmojiCaptcha()
					}
					ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 == data.captchaType ||
							ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE == data.captchaType -> {
						result = ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData)
						captchaData.put(CaptchaData.API_KEY, id)
						captchaData.put(CaptchaData.REFERER, locator.buildPath().toString())
					}
					else -> throw RuntimeException()
				}
				return result
			} else {
				if (exception != null) {
					// If wakaba is swaying, but passcode is verified, let's try to use it
					if (captchaPassCookie != null) {
						configuration.setMaxFilesCountEnabled(true)
						return makeCaptchaPassResult(captchaPassCookie)
					}
					throw exception
				}
				throw InvalidResponseException()
			}
		}
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	override fun onSendPost(data: SendPostData): SendPostResult {
		val locator = this.locator
		var subject = data.subject
		var tag: String? = null
		if (data.threadNumber == null && data.subject != null) {
			val matcher = PATTERN_TAG.matcher(subject)
			if (matcher.matches()) {
				subject = matcher.group(1)
				tag = matcher.group(2)
			}
		}
		val entity = MultipartEntity()
		entity.add("task", "post")
		entity.add("board", data.boardName)
		entity.add("thread", data.threadNumber ?: "0")
		entity.add("subject", subject)
		entity.add("tags", tag)
		entity.add("comment", data.comment)
		entity.add("name", data.name)
		entity.add("email", if (data.optionSage) "sage" else data.email)
		if (data.optionOriginalPoster) {
			entity.add("op_mark", "1")
		}
		data.attachments?.forEach { it.addToEntity(entity, "file[]") }
		entity.add("icon", data.userIcon)

		var captchaPassCookie: String? = null
		if (data.captchaData != null) {
			captchaPassCookie = data.captchaData.get(CAPTCHA_PASS_COOKIE)
			val challenge = data.captchaData.get(CaptchaData.CHALLENGE)
			val input = StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT))

			if (DvachChanConfiguration.CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA != data.captchaType) {
				DvachChanConfiguration.CAPTCHA_TYPES[data.captchaType]?.let {
					entity.add("captcha_type", it)
				}
			}
			when {
				DvachChanConfiguration.CAPTCHA_TYPE_2CH_CAPTCHA == data.captchaType -> {
					entity.add("2chcaptcha_id", challenge)
					entity.add("2chcaptcha_value", input)
				}
				DvachChanConfiguration.CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA == data.captchaType -> {
					entity.add("captcha_type", DvachChanConfiguration.CAPTCHA_TYPE_2CH_EMOJI_CAPTCHA)
					entity.add("emoji_captcha_id", challenge)
				}
				ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 == data.captchaType ||
						ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2_INVISIBLE == data.captchaType -> {
					entity.add("g-recaptcha-response", input)
				}
			}
		}

		var originalPosterCookieName: String? = null
		var originalPosterCookie: String? = null
		if (data.threadNumber != null && data.optionOriginalPoster) {
			originalPosterCookieName = "op_${data.boardName}_${data.threadNumber}"
			originalPosterCookie = configuration.getCookie(originalPosterCookieName)
		}

		val uri = locator.buildPath("user/posting")
		val response = HttpRequest(uri, data).setPostMethod(entity)
				.addCookie(buildCookies(captchaPassCookie))
				.addCookie(originalPosterCookieName, originalPosterCookie)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform()
		val jsonObject = try {
			JSONObject(response.readString())
		} catch (e: JSONException) {
			throw InvalidResponseException(e)
		}
		val auth = response.getCookieValue(COOKIE_USERCODE_AUTH)
		if (!StringUtils.isEmpty(auth)) {
			configuration.storeCookie(COOKIE_USERCODE_AUTH, auth, "Usercode Auth")
		}
		val postNumber = CommonUtils.optJsonString(jsonObject, "num")
		if (!StringUtils.isEmpty(postNumber)) {
			return SendPostResult(data.threadNumber, postNumber)
		}
		val threadNumber = CommonUtils.optJsonString(jsonObject, "thread")
		if (!StringUtils.isEmpty(threadNumber)) {
			originalPosterCookieName = "op_${data.boardName}_$threadNumber"
			originalPosterCookie = response.getCookieValue(originalPosterCookieName)
			if (!StringUtils.isEmpty(originalPosterCookie)) {
				configuration.storeCookie(originalPosterCookieName, originalPosterCookie,
						"OP /${data.boardName}/$threadNumber")
			}
			return SendPostResult(threadNumber, null)
		}

		val error: Int
		val reason: String?
		try {
			val jsonError = JSONObject(CommonUtils.getJsonString(jsonObject, "error"))
			error = Math.abs(jsonError.optInt("code", Int.MAX_VALUE))
			reason = CommonUtils.optJsonString(jsonError, "message")
		} catch (e: JSONException) {
			throw InvalidResponseException()
		}

		var errorType = 0
		var extra: Any? = null
		when (error) {
			2 -> errorType = ApiException.SEND_ERROR_NO_BOARD
			3 -> errorType = ApiException.SEND_ERROR_NO_THREAD
			4 -> errorType = ApiException.SEND_ERROR_NO_ACCESS
			7 -> errorType = ApiException.SEND_ERROR_CLOSED
			8 -> errorType = ApiException.SEND_ERROR_TOO_FAST
			9 -> errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG
			10 -> errorType = ApiException.SEND_ERROR_FILE_EXISTS
			11 -> errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED
			12 -> errorType = ApiException.SEND_ERROR_FILE_TOO_BIG
			13 -> errorType = ApiException.SEND_ERROR_FILES_TOO_MANY
			16, 18 -> errorType = ApiException.SEND_ERROR_SPAM_LIST
			19 -> errorType = ApiException.SEND_ERROR_EMPTY_FILE
			20 -> errorType = ApiException.SEND_ERROR_EMPTY_COMMENT
			6, 14, 15 -> errorType = ApiException.SEND_ERROR_BANNED
			5, 21, 22 -> errorType = ApiException.SEND_ERROR_CAPTCHA
			else -> if (reasonIsBanMessage(reason)) {
				errorType = ApiException.SEND_ERROR_BANNED
			}
		}
		if (errorType == ApiException.SEND_ERROR_BANNED) {
			val banExtra = ApiException.BanExtra()
			val matcher = PATTERN_BAN.matcher(reason)
			if (matcher.find()) {
				banExtra.setId(StringUtils.emptyIfNull(matcher.group(1)))
				banExtra.setMessage(StringUtils.emptyIfNull(matcher.group(2)))
				val banExpireDate = StringUtils.emptyIfNull(matcher.group(3))
				if (!StringUtils.isEmpty(banExpireDate)) {
					try {
						banExtra.setExpireDate(DATE_FORMAT_BAN.get()!!.parse(banExpireDate)!!.time)
					} catch (e: java.text.ParseException) {
						// Ignore exception
					}
				}
			} else {
				banExtra.setMessage(reason)
			}
			extra = banExtra
		}
		if (errorType == ApiException.SEND_ERROR_CAPTCHA) {
			lastCaptchaPassData = null
			lastCaptchaPassCookie = null
		}
		if (extra != null) {
			throw ApiException(errorType, extra)
		}
		if (!StringUtils.isEmpty(reason)) {
			throw ApiException(reason)
		}
		throw InvalidResponseException()
	}

	private fun reasonIsBanMessage(reason: String?): Boolean {
		if (!StringUtils.isEmpty(reason)) {
			val lowerCaseReason = reason!!.lowercase()
			return lowerCaseReason.contains("постинг запрещён") || lowerCaseReason.contains("бан")
		}
		return false
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? {
		val uri = locator.buildPath("user/report")
		val entity = MultipartEntity()
		entity.add("board", data.boardName)
		entity.add("thread", data.threadNumber)
		if (data.postNumbers.isNotEmpty()) {
			entity.add("post", data.postNumbers[0])
		}
		entity.add("comment", data.comment)

		val jsonObject = try {
			JSONObject(HttpRequest(uri, data).addCookie(buildCookiesWithCaptchaPass())
					.setPostMethod(entity).setRedirectHandler(HttpRequest.RedirectHandler.STRICT)
					.perform().readString())
		} catch (e: JSONException) {
			throw InvalidResponseException(e)
		}
		try {
			val result = CommonUtils.getJsonString(jsonObject, "result")
			if (result == "1") {
				return null
			}
			val message = CommonUtils.getJsonString(jsonObject, "message")
			if (StringUtils.isEmpty(message)) {
				return null
			}
			var errorType = 0
			if (message.contains("Вы уже отправляли жалобу")) {
				errorType = ApiException.REPORT_ERROR_TOO_OFTEN
			} else if (message.contains("Вы ничего не написали в жалобе")) {
				errorType = ApiException.REPORT_ERROR_EMPTY_COMMENT
			}
			if (errorType != 0) {
				throw ApiException(errorType)
			}
			throw ApiException(message)
		} catch (e: JSONException) {
			throw InvalidResponseException(e)
		}
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	override fun onSendVotePost(data: SendVotePostData): SendVotePostResult? {
		val action = if (data.isLike) "like" else "dislike"
		val uri = locator.buildPath("api/$action?board=${data.boardName}&num=${data.postNumber}")

		val jsonObject = try {
			JSONObject(HttpRequest(uri, data).setGetMethod()
					.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString())
		} catch (e: HttpException) {
			throw InvalidResponseException(e)
		} catch (e: JSONException) {
			throw InvalidResponseException(e)
		}

		try {
			val result = CommonUtils.getJsonString(jsonObject, "result")
			if (result != "1") {
				val error = CommonUtils.getJsonString(jsonObject, "error")
				if (error.contains("Постинг запрещён.")) {
					throw ApiException(ApiException.VOTE_ERROR_POSTING_PROHIBITED)
				}
			}
		} catch (e: JSONException) {
			throw InvalidResponseException(e)
		}
		return null
	}

	companion object {
		private const val COOKIE_USERCODE_AUTH = "usercode_auth"
		private const val COOKIE_PASSCODE_AUTH = "passcode_auth"
		private const val PASSCODUBOYAR_MAX_FILES = 8

		private val PREFERRED_BOARDS_ORDER = arrayOf("Разное", "Тематика", "Творчество", "Политика",
				"Техника и софт", "Игры", "Японская культура", "Взрослым", "Пробное")

		private val MOBILE_API_DELAYS = intArrayOf(0, 250, 500, 1000)

		private const val CAPTCHA_PASS_COOKIE = "captchaPassCookie"

		private val PATTERN_TAG = Pattern.compile("(.*) /([^/]*)/")
		private val PATTERN_BAN = Pattern.compile("[^ ]*?: (\\d+)\\. .*: (.*(?=//![a-z]+\\.)|" +
				".*(?=[А-Я][а-я]{2} [А-Я][а-я]{2} \\d{2} (?:\\d{2}:?){3} \\d{4}$)|.*$)(?:.*?[а-я] )?([А-Я].*|)")

		private val DATE_FORMAT_BAN = object : ThreadLocal<SimpleDateFormat>() {
			@SuppressLint("SimpleDateFormat")
			override fun initialValue(): SimpleDateFormat {
				return SimpleDateFormat("MMM dd HH:mm:ss yyyy",
						DateFormatSymbols().apply {
							shortMonths = arrayOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг",
									"Сен", "Окт", "Ноя", "Дек")
						}).apply {
					timeZone = TimeZone.getTimeZone("GMT+3")
				}
			}
		}

		private fun makeCaptchaPassResult(captchaPassCookie: String?): ReadCaptchaResult {
			val captchaData = CaptchaData()
			captchaData.put(CAPTCHA_PASS_COOKIE, captchaPassCookie)
			return ReadCaptchaResult(CaptchaState.PASS, captchaData)
					.setValidity(ChanConfiguration.Captcha.Validity.LONG_LIFETIME)
		}
	}
}
