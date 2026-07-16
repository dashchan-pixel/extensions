package com.mishiranu.dashchan.chan.fourchan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanLocator
import chan.content.ChanMarkup
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.content.model.Board
import chan.content.model.BoardCategory
import chan.content.model.Post
import chan.content.model.Posts
import chan.content.model.ThreadSummary
import chan.http.CookieBuilder
import chan.http.FirewallResolver
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.HttpValidator
import chan.http.MultipartEntity
import chan.http.SimpleEntity
import chan.http.UrlEncodedEntity
import chan.text.JsonSerial
import chan.text.ParseException
import chan.util.CommonUtils
import chan.util.StringUtils
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import java.util.Objects
import java.util.regex.Pattern
import org.json.JSONException
import org.json.JSONObject

class FourchanChanPerformer : ChanPerformer() {

	init {
		registerFirewallResolver(FourchanSpurResolver())
	}

	private val lastRulesUpdate = HashMap<String, Long>()

	private val unsafeRedirectHandler = HttpRequestUnsafeRedirectHandler()
	private val strictUnsafeRedirectHandler = HttpRequestUnsafeRedirectHandler(HttpRequest.RedirectHandler.STRICT)

	@Volatile
	private var lastCaptchaPassData: String? = null

	@Volatile
	private var lastCaptchaPassCookie: String? = null

	@Throws(HttpException::class)
	private fun updateBoardRules(
			preset: HttpRequest.Preset,
			boardName: String,
			threads: List<Posts>
	) {
		val update = synchronized(lastRulesUpdate) { lastRulesUpdate[boardName] }
		if (update != null && update + 24 * 60 * 60 * 1000 > SystemClock.elapsedRealtime()) {
			return
		}
		var postNumber: String? = null
		for (posts in threads) {
			val post = posts.posts[0]
			if (!post.isClosed && !post.isArchived && !post.isSticky) {
				postNumber = post.postNumber
				break
			}
		}
		var response: HttpResponse? = null
		if (postNumber != null) {
			val locator = ChanLocator.get(this) as FourchanChanLocator
			val uri = locator.createSysUri("imgboard.php").buildUpon()
					.appendQueryParameter("board", boardName)
					.appendQueryParameter("mode", "report")
					.appendQueryParameter("no", postNumber)
					.build()
			response = HttpRequest(uri, preset)
					.setSuccessOnly(false)
					.setRedirectHandler(unsafeRedirectHandler)
					.perform()
		}
		var reportReasons: List<ReportReason> = emptyList()
		if (response != null) {
			try {
				response.open().use { input ->
					reportReasons = FourchanRulesParser().parse(input)
				}
			} catch (e: ParseException) {
				// Ignore
			} catch (e: IOException) {
				throw response.fail(e)
			}
		}
		if (reportReasons.isNotEmpty()) {
			synchronized(lastRulesUpdate) {
				lastRulesUpdate[boardName] = SystemClock.elapsedRealtime()
			}
			val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
			configuration.updateReportingConfiguration(boardName, reportReasons)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val uri = locator.createApiUri(data.boardName, if (data.isCatalog) "catalog.json" else "${data.pageNumber + 1}.json")
		val response = HttpRequest(uri, data)
				.setValidator(data.validator)
				.setRedirectHandler(unsafeRedirectHandler)
				.perform()
		val validator = response.validator
		val threads = ArrayList<Posts>()
		val handleMathTags = configuration.isMathTagsHandlingEnabled()
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					if (data.isCatalog) {
						reader.startArray()
						while (!reader.endStruct()) {
							reader.startObject()
							while (!reader.endStruct()) {
								when (reader.nextName()) {
									"threads" -> {
										reader.startArray()
										while (!reader.endStruct()) {
											threads.add(FourchanModelMapper.createThread(reader, locator, data.boardName, handleMathTags, true))
										}
									}
									else -> reader.skip()
								}
							}
						}
					} else {
						reader.startObject()
						while (!reader.endStruct()) {
							when (reader.nextName()) {
								"threads" -> {
									reader.startArray()
									while (!reader.endStruct()) {
										threads.add(FourchanModelMapper.createThread(reader, locator, data.boardName, handleMathTags, false))
									}
								}
								else -> reader.skip()
							}
						}
					}
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
		if (data.pageNumber == 0) {
			updateBoardRules(data, data.boardName, threads)
		}
		return ReadThreadsResult(threads).setValidator(validator)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val handleMathTags = configuration.isMathTagsHandlingEnabled()
		val tail = ThreadsWithTailCache.INSTANCE.contains(data.threadNumber) &&
				data.partialThreadLoading && data.lastPostNumber != null
		val posts = ArrayList<Post>()
		var uniquePosters = 0
		if (tail) {
			val uri = locator.createApiUri(data.boardName, "thread", "${data.threadNumber}-tail.json")
			val response = HttpRequest(uri, data)
					.setValidator(data.validator)
					.setSuccessOnly(false)
					.setRedirectHandler(unsafeRedirectHandler)
					.perform()
			if (response.responseCode == HttpURLConnection.HTTP_OK) {
				var loadFullThread = false
				try {
					response.open().use { input ->
						JsonSerial.reader(input).use { reader ->
							reader.startObject()
							while (!reader.endStruct()) {
								when (reader.nextName()) {
									"posts" -> {
										reader.startArray()
										var sincePostNumber: String? = null
										reader.startObject()
										while (!reader.endStruct()) {
											when (reader.nextName()) {
												"unique_ips" -> uniquePosters = reader.nextInt()
												"tail_id" -> sincePostNumber = reader.nextString()
												else -> reader.skip()
											}
										}
										val lastPostNum = data.lastPostNumber
										if (sincePostNumber != null && lastPostNum != null &&
												lastPostNum.toInt() >= sincePostNumber.toInt()
										) {
											while (!reader.endStruct()) {
												posts.add(FourchanModelMapper.createPost(reader, locator, data.boardName, handleMathTags, null))
											}
											return ReadPostsResult(Posts(posts).setUniquePosters(uniquePosters))
										} else {
											loadFullThread = true
											break
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
				// If loadFullThread is true or if we broke out of the parsing loop, we continue to load full thread
			}
		}
		val uri = locator.createApiUri(data.boardName, "thread", "${data.threadNumber}.json")
		val response = HttpRequest(uri, data)
				.setValidator(data.validator)
				.setRedirectHandler(unsafeRedirectHandler)
				.perform()
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					reader.startObject()
					while (!reader.endStruct()) {
						when (reader.nextName()) {
							"posts" -> {
								var extra: FourchanModelMapper.Extra? = FourchanModelMapper.Extra()
								reader.startArray()
								while (!reader.endStruct()) {
									posts.add(FourchanModelMapper.createPost(reader, locator, data.boardName, handleMathTags, extra))
									if (extra != null) {
										uniquePosters = extra.uniquePosters
										extra = null
									}
								}
							}
							else -> reader.skip()
						}
					}
				}
			}
			return ReadPostsResult(Posts(posts).setUniquePosters(uniquePosters)).setFullThread(true)
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadSearchPosts(data: ReadSearchPostsData): ReadSearchPostsResult {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val handleMathTags = configuration.isMathTagsHandlingEnabled()
		val uri = locator.createSearchApiUri(
				"b", data.boardName,
				"q", data.searchQuery,
				"o", (10 * data.pageNumber).toString()
		)
		val response = HttpRequest(uri, data)
				.setRedirectHandler(unsafeRedirectHandler)
				.perform()
		val locale = Locale.US
		val lowerSearchQuery = data.searchQuery.lowercase(locale)
		try {
			response.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					val posts = ArrayList<Post>()
					reader.startObject()
					while (!reader.endStruct()) {
						when (reader.nextName()) {
							"threads" -> {
								reader.startArray()
								while (!reader.endStruct()) {
									reader.startObject()
									while (!reader.endStruct()) {
										when (reader.nextName()) {
											"posts" -> {
												reader.startArray()
												while (!reader.endStruct()) {
													val post = FourchanModelMapper.createPost(reader, locator, data.boardName, handleMathTags, null)
													var matches = post.parentPostNumber != null
													if (!matches) {
														matches = StringUtils.clearHtml(post.subject).lowercase(locale).contains(lowerSearchQuery)
													}
													if (!matches) {
														matches = StringUtils.clearHtml(post.comment).lowercase(locale).contains(lowerSearchQuery)
													}
													if (matches) {
														posts.add(post)
													}
												}
											}
											else -> reader.skip()
										}
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
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadBoards(data: ReadBoardsData): ReadBoardsResult {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val uri = locator.buildPath()
		val response = HttpRequest(uri, data)
				.setRedirectHandler(unsafeRedirectHandler)
				.perform()
		val categoryMap: Map<String, List<String>> = try {
			response.open().use { input ->
				FourchanBoardsParser(this).parse(input)
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
		val uncategorized = "Uncategorized"
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val boardsMap = LinkedHashMap<String, ArrayList<Board>>()
		for (title in categoryMap.keys) {
			boardsMap[title] = ArrayList()
		}
		boardsMap[uncategorized] = ArrayList()
		val boardToCategory = HashMap<String, String>()
		for (entry in categoryMap.entries) {
			for (boardName in entry.value) {
				boardToCategory[boardName] = entry.key
			}
		}
		val apiUri = locator.createApiUri("boards.json")
		val apiResponse = HttpRequest(apiUri, data)
				.setRedirectHandler(unsafeRedirectHandler)
				.perform()
		try {
			apiResponse.open().use { input ->
				JsonSerial.reader(input).use { reader ->
					reader.startObject()
					while (!reader.endStruct()) {
						when (reader.nextName()) {
							"boards" -> {
								reader.startArray()
								while (!reader.endStruct()) {
									val board = configuration.updateBoard(reader)
									if (board != null) {
										val category = boardToCategory[board.boardName]
										var boards = boardsMap[category]
										if (boards == null) {
											boards = boardsMap[uncategorized]
										}
										Objects.requireNonNull(boards)!!.add(board)
									}
								}
							}
							else -> reader.skip()
						}
					}
					val boardCategories = ArrayList<BoardCategory>()
					for (entry in boardsMap.entries) {
						val boards = entry.value
						if (boards.isNotEmpty()) {
							boards.sort()
							boardCategories.add(BoardCategory(entry.key, boards))
						}
					}
					return ReadBoardsResult(boardCategories)
				}
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw apiResponse.fail(e)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadThreadSummaries(data: ReadThreadSummariesData): ReadThreadSummariesResult {
		return if (data.type == ReadThreadSummariesData.TYPE_ARCHIVED_THREADS) {
			val locator = ChanLocator.get(this) as FourchanChanLocator
			val uri = locator.createBoardUri(data.boardName, 0).buildUpon().appendPath("archive").build()
			val responseText = HttpRequest(uri, data)
					.setRedirectHandler(unsafeRedirectHandler)
					.perform()
					.readString()
			val threadSummaries = ArrayList<ThreadSummary>()
			val matcher = PATTERN_ARCHIVED_THREAD.matcher(responseText)
			while (matcher.find()) {
				threadSummaries.add(ThreadSummary(
						data.boardName, matcher.group(1),
						StringUtils.clearHtml(matcher.group(2))
				))
			}
			ReadThreadSummariesResult(threadSummaries)
		} else {
			super.onReadThreadSummaries(data)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadContent(data: ReadContentData): ReadContentResult {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val mathData = locator.extractMathData(data.uri)
		if (mathData != null) {
			val uri = locator.buildPathWithHost("quicklatex.com", "latex3.f")
			val entity = SimpleEntity()
			entity.setData("formula=" + mathData.replace("%", "%25").replace("&", "%26") + "&fsize=60px&" +
					"fcolor=000000&mode=0&out=1&remhost=quicklatex.com&preamble=\\usepackage{amsmath}\n" +
					"\\usepackage{amsfonts}\n\\usepackage{amssymb}")
			entity.setContentType("application/x-www-form-urlencoded")
			val responseText = HttpRequest(uri, data)
					.setPostMethod(entity)
					.setRedirectHandler(unsafeRedirectHandler)
					.perform()
					.readString()
			val splitted = responseText.split("\r?\n| ".toRegex()).toTypedArray()
			if (splitted.size >= 2 && "0" == splitted[0]) {
				val parsedUri = Uri.parse(splitted[1])
				return ReadContentResult(
						HttpRequest(parsedUri, data)
								.setRedirectHandler(unsafeRedirectHandler)
								.perform()
				)
			}
			throw HttpException.createNotFoundException()
		}
		return super.onReadContent(data)
	}

	private fun buildCookies(captchaPassCookie: String?): CookieBuilder? {
		if (captchaPassCookie != null) {
			val builder = CookieBuilder()
			builder.append("pass_enabled", "1")
			builder.append("pass_id", captchaPassCookie)
			return builder
		}
		return null
	}

	private fun getCaptchaPassData(token: String, pin: String): String {
		return "$token|$pin"
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onCheckAuthorization(data: CheckAuthorizationData): CheckAuthorizationResult {
		val token = data.authorizationData[0]
		val pin = data.authorizationData[1]
		return CheckAuthorizationResult(readCaptchaPass(data, token, pin) != null)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	private fun readCaptchaPass(preset: HttpRequest.Preset, token: String, pin: String): String? {
		lastCaptchaPassData = null
		lastCaptchaPassCookie = null

		val locator = ChanLocator.get(this) as FourchanChanLocator
		val uri = locator.createSysUri("auth")
		val entity = UrlEncodedEntity("act", "do_login", "id", token, "pin", pin, "long_login", "yes")
		val response = HttpRequest(uri, preset)
				.setPostMethod(entity)
				.setRedirectHandler(strictUnsafeRedirectHandler)
				.perform()
		val responseText = response.readString()
		val matcher = PATTERN_AUTH_MESSAGE.matcher(responseText)
		if (matcher.find()) {
			var message = StringUtils.clearHtml(matcher.group(1))
			if (message.startsWith("Error: ")) {
				message = message.substring(7)
			}
			if (message.contains("Your device is now authorized")) {
				var captchaPassCookie: String? = null
				val cookies = response.headerFields["Set-Cookie"]
				if (cookies != null) {
					for (cookie in cookies) {
						if (cookie.startsWith("pass_id=") && !cookie.startsWith("pass_id=0;")) {
							val index = cookie.indexOf(';')
							captchaPassCookie = cookie.substring(8, if (index >= 0) index else cookie.length)
							break
						}
					}
				}
				if (captchaPassCookie == null) {
					throw InvalidResponseException()
				}
				lastCaptchaPassData = getCaptchaPassData(token, pin)
				lastCaptchaPassCookie = captchaPassCookie
				return captchaPassCookie
			}
			if (message.contains("Incorrect Token or PIN") || message.contains("Your Token must be exactly") ||
					message.contains("You have left one or more fields blank")
			) {
				return null
			}
			message = removeErrorFromMessage(message)
			throw HttpException(0, message)
		} else {
			throw InvalidResponseException()
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
		val banned = REQUIREMENT_BANNED == data.requirement
		if (!banned) {
			val token = data.captchaPass?.get(0)
			val pin = data.captchaPass?.get(1)
			var captchaPassCookie: String? = null
			if (token != null && pin != null) {
				if (getCaptchaPassData(token, pin) == lastCaptchaPassData) {
					captchaPassCookie = lastCaptchaPassCookie
				} else {
					captchaPassCookie = readCaptchaPass(data, token, pin)
				}
			}
			if (captchaPassCookie != null) {
				val captchaData = CaptchaData()
				captchaData.put(CAPTCHA_DATA_KEY_PASS_COOKIE, captchaPassCookie)
				return ReadCaptchaResult(CaptchaState.PASS, captchaData)
						.setValidity(ChanConfiguration.Captcha.Validity.LONG_LIFETIME)
			}
		}
		val captchaType = if (banned) ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 else data.captchaType
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val result: ReadCaptchaResult
		if (FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA == captchaType) {
			if (data.mayShowLoadButton) {
				return ReadCaptchaResult(CaptchaState.NEED_LOAD, null)
			}
			val threadNumber = if (data.requirement == null) data.threadNumber else "1"
			val builder = locator.createSysUri("captcha").buildUpon()
					.appendQueryParameter("board", data.boardName)
			if (threadNumber != null) {
				builder.appendQueryParameter("thread_id", threadNumber)
			}
			val captchaTicket = getCaptchaTicket()
			if (captchaTicket != null) {
				builder.appendQueryParameter("ticket", captchaTicket)
			}
			val uri = builder.build()
			var challenge: String
			var image: Bitmap?
			var background: Bitmap?
			val fourchanPassCookie = getFourchanPassCookie(configuration, data.boardName)
			val boardsHost = if (configuration.isSafeForWork(data.boardName)) "boards.4channel.org" else "boards.4chan.org"
			var responseStr: String? = null
			while (true) {
				try {
					val cachedJson = configuration.getCookie("fourchan_captcha_json")
					if (!cachedJson.isNullOrEmpty()) {
						configuration.storeCookie("fourchan_captcha_json", null, null)
						responseStr = cachedJson
					} else {
						responseStr = HttpRequest(uri, data)
								.addCookie(COOKIE_FOURCHAN_PASS, fourchanPassCookie)
								.addHeader("Referer", "https://$boardsHost/")
								.addHeader("Origin", "https://$boardsHost")
								.setRedirectHandler(unsafeRedirectHandler)
								.perform()
								.readString()
					}
					var jsonString = responseStr
					if (jsonString != null && jsonString.startsWith("<!DOCTYPE")) {
						val matcher = Pattern.compile("window\\.parent\\.postMessage\\((.*?)\\s*,\\s*['\"]\\*['\"]\\);").matcher(jsonString)
						if (matcher.find()) {
							jsonString = matcher.group(1)
						}
					}
					var jsonObject = JSONObject(jsonString)
					if (jsonObject.has("twister")) {
						jsonObject = jsonObject.getJSONObject("twister")
					}
					val newCaptchaTicket = jsonObject.optString("ticket")
					if (!newCaptchaTicket.isNullOrEmpty()) {
						saveCaptchaTicket(newCaptchaTicket)
					}
					val error = jsonObject.optString("error")
					val captchaOnCooldown = error == "You have to wait a while before doing this again"
					val captchaTicketOnCooldown = !captchaOnCooldown && jsonObject.has("pcd")
					if (captchaOnCooldown || captchaTicketOnCooldown) {
						val cooldownFieldName = if (captchaOnCooldown) "cd" else "pcd"
						val cooldownSeconds = jsonObject.optInt(cooldownFieldName, -1)
						if (cooldownSeconds == -1) throw HttpException(0, null)
						val reasonableCooldownWaitSeconds = 10
						if (cooldownSeconds <= reasonableCooldownWaitSeconds) {
							try {
								Thread.sleep((cooldownSeconds + 1) * 1000L)
							} catch (e: InterruptedException) {
								throw HttpException(0, null)
							}
						} else {
							throw HttpException(0, configuration.resources.getQuantityString(
									R.plurals.capthca_cooldown_message__format,
									cooldownSeconds, cooldownSeconds
							))
						}
					} else if (!error.isNullOrEmpty()) {
						val cleanError = error.replace(Regex("<[^>]*>"), "")
						throw HttpException(0, cleanError)
					} else {
						challenge = jsonObject.getString("challenge")
						val imgString = jsonObject.optString("img")
						val bgString = jsonObject.optString("bg")
						val imageBytes = if (imgString.isNullOrEmpty()) ByteArray(0) else Base64.decode(imgString, 0)
						val backgroundBytes = if (bgString.isNullOrEmpty()) ByteArray(0) else Base64.decode(bgString, 0)
						image = if (imageBytes.isEmpty()) null else BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
						background = if (backgroundBytes.isEmpty()) null else BitmapFactory.decodeByteArray(backgroundBytes, 0, backgroundBytes.size)
						break
					}
				} catch (e: Exception) {
					android.util.Log.e("FOURCHAN_DEBUG", "Response string: $responseStr")
					if (e is HttpException) {
						android.util.Log.e("FOURCHAN_DEBUG", "HttpException code: ${e.getResponseCode()}")
					}
					android.util.Log.e("FOURCHAN_DEBUG", "Error loading captcha", e)
					if (e is HttpException) {
						throw e
					}
					throw InvalidResponseException(e)
				}
			}
			if (image == null) {
				throw InvalidResponseException(Exception("Image is null"))
			}
			var centerOffset = 0
			if (background != null) {
				val offset = FourchanCaptchaUtils.findCenterOffset(image)
				if (offset != null) {
					centerOffset = offset
				} else {
					background.recycle()
					background = null
				}
			}
			if (background != null) {
				if (image.height != background.height) {
					throw InvalidResponseException(Exception("Image heights are not equal"))
				} else if (background.width < image.width) {
					throw InvalidResponseException(Exception("Invalid image sizes"))
				}
			}
			var resultOffset = 0
			if (background != null) {
				val description = configuration.resources.getString(R.string.select_the_most_readable_captcha)
				val offset = FourchanCaptchaUtils.binarySearchOffset(image, background, 9, centerOffset,
						object : FourchanCaptchaUtils.BinarySearchCallback<HttpException> {
							override fun getIndex(images: Array<Bitmap>): Int? {
								return requireUserImageSingleChoice(-1, images, description, null)
							}
						})
				if (offset != null) {
					resultOffset = offset
				} else {
					return ReadCaptchaResult(CaptchaState.NEED_LOAD, null)
				}
			}
			val resultImage = FourchanCaptchaUtils.create(image, background, resultOffset)
			val captchaData = CaptchaData()
			captchaData.put(CAPTCHA_DATA_KEY_TYPE, captchaType)
			captchaData.put(CaptchaData.CHALLENGE, challenge)
			result = ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(resultImage)
		} else if (ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 == captchaType) {
			val captchaData = CaptchaData()
			captchaData.put(CAPTCHA_DATA_KEY_TYPE, captchaType)
			captchaData.put(CaptchaData.API_KEY, RECAPTCHA_API_KEY)
			if (banned) {
				captchaData.put(CaptchaData.REFERER, locator.buildPath("banned").toString())
			} else {
				captchaData.put(CaptchaData.REFERER, locator.createBoardsRootUri(data.boardName).toString())
			}
			result = ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData)
					.setValidity(ChanConfiguration.Captcha.Validity.IN_BOARD_SEPARATELY)
		} else {
			throw IllegalStateException()
		}
		if (!CommonUtils.equals(data.captchaType, captchaType)) {
			result.setCaptchaType(captchaType)
		}
		return result
	}

	private fun saveCaptchaTicket(captchaTicket: String) {
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		configuration.set(null, CAPTCHA_TICKET_KEY, captchaTicket)
	}

	private fun getCaptchaTicket(): String? {
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		return configuration.get(null, CAPTCHA_TICKET_KEY, null)
	}

	@Throws(HttpException::class)
	private fun readBanExtra(preset: HttpRequest.Preset, boardName: String): ApiException.BanExtra? {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val uri = locator.buildPath("banned")
		var responseText = HttpRequest(uri, preset)
				.setRedirectHandler(unsafeRedirectHandler)
				.perform()
				.readString()
		while (responseText.contains(RECAPTCHA_API_KEY)) {
			val captchaData = requireUserCaptcha(REQUIREMENT_BANNED, boardName, null, false) ?: return null
			val entity = MultipartEntity()
			entity.add("g-recaptcha-response", captchaData.get(CaptchaData.INPUT))
			responseText = HttpRequest(uri, preset)
					.setPostMethod(entity)
					.setRedirectHandler(unsafeRedirectHandler)
					.perform()
					.readString()
		}
		val fields = HashMap<String, String>()
		for (name in Arrays.asList("reason", "startDate", "endDate")) {
			for (tag in Arrays.asList("b", "span")) {
				val open = "<$tag class=\"$name\">"
				var start = responseText.indexOf(open)
				if (start < 0) {
					continue
				}
				start += open.length
				val end = responseText.indexOf("</$tag>", start)
				if (end < start) {
					continue
				}
				var actualEnd = end
				if (responseText[actualEnd - 1] == '.') {
					actualEnd--
				}
				fields[name] = responseText.substring(start, actualEnd)
				break
			}
		}
		return ApiException.BanExtra()
				.setMessage(fields["reason"])
				.setStartDate(parseBanDate(fields["startDate"]))
				.setExpireDate(parseBanDate(fields["endDate"]))
	}

	private fun handleFourchanPass(response: HttpResponse, boardName: String) {
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val fourchanPassCookie = response.getCookieValue(COOKIE_FOURCHAN_PASS)
		if (fourchanPassCookie != null) {
			val fourchanPassCookieKey = getFourchanPassCookieStoreKey(configuration, boardName)
			val displayName = if (configuration.isSafeForWork(boardName)) "4channel pass" else "4chan pass"
			configuration.storeCookie(fourchanPassCookieKey, fourchanPassCookie, displayName)
		}
	}

	private fun getFourchanPassCookie(configuration: FourchanChanConfiguration, boardName: String): String? {
		val fourchanPassCookieKey = getFourchanPassCookieStoreKey(configuration, boardName)
		return configuration.getCookie(fourchanPassCookieKey)
	}

	private fun getFourchanPassCookieStoreKey(configuration: FourchanChanConfiguration, boardName: String): String {
		val prefix = if (configuration.isSafeForWork(boardName)) "4channel" else "4chan"
		return prefix + "_" + COOKIE_FOURCHAN_PASS
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	override fun onSendPost(data: SendPostData): SendPostResult {
		val entity = MultipartEntity()
		entity.add("mode", "regist")
		entity.add("resto", data.threadNumber)
		entity.add("sub", data.subject)
		entity.add("com", data.comment)
		entity.add("name", data.name)
		if (data.optionSage) {
			entity.add("email", "sage")
		} else if (data.email != null && !FORBIDDEN_OPTIONS.contains(data.email.lowercase(Locale.US))) {
			entity.add("email", data.email)
		}
		entity.add("pwd", data.password)
		entity.add("flag", data.userIcon)
		if (data.attachments != null) {
			val attachment = data.attachments[0]
			attachment.addToEntity(entity, "upfile")
			if (attachment.optionSpoiler) {
				entity.add("spoiler", "on")
			}
		}
		var captchaPassCookie: String? = null
		if (data.captchaData != null) {
			if (FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA == data.captchaType) {
				entity.add("t-challenge", data.captchaData.get(CaptchaData.CHALLENGE))
				entity.add("t-response", data.captchaData.get(CaptchaData.INPUT))
			} else if (ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 == data.captchaType) {
				entity.add("g-recaptcha-response", data.captchaData.get(CaptchaData.INPUT))
			}
			captchaPassCookie = data.captchaData.get(CAPTCHA_DATA_KEY_PASS_COOKIE)
		}

		val locator = ChanLocator.get(this) as FourchanChanLocator
		val uri = locator.createSysUri("post")
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val fourchanPassCookie = getFourchanPassCookie(configuration, data.boardName)
		val request = HttpRequest(uri, data)
				.addCookie(buildCookies(captchaPassCookie))
				.addCookie(COOKIE_FOURCHAN_PASS, fourchanPassCookie)
				.addHeader("Sec-Fetch-Dest", "document")
				.addHeader("Sec-Fetch-Mode", "navigate")
				.addHeader("Sec-Fetch-Site", "same-site")
				.addHeader("Sec-Fetch-User", "?1")

		val response = request.setPostMethod(entity)
				.setRedirectHandler(strictUnsafeRedirectHandler)
				.perform()
		handleFourchanPass(response, data.boardName)
		val responseText = response.readString()

		var matcher = PATTERN_POST_SUCCESS.matcher(responseText)
		if (matcher.find()) {
			var threadNumber = matcher.group(1)
			var postNumber: String? = matcher.group(2)
			if ("0" == threadNumber) {
				threadNumber = postNumber
				postNumber = null
			}
			return SendPostResult(threadNumber, postNumber)
		}
		matcher = PATTERN_POST_ERROR.matcher(responseText)
		if (matcher.find()) {
			val errorMessage = matcher.group(1)
			if (errorMessage != null) {
				var errorType = 0
				var extra: Any? = null
				if (errorMessage.contains("CAPTCHA")) {
					errorType = ApiException.SEND_ERROR_CAPTCHA
				} else if (errorMessage.contains("No text entered")) {
					errorType = ApiException.SEND_ERROR_EMPTY_COMMENT
				} else if (errorMessage.contains("No file selected")) {
					errorType = ApiException.SEND_ERROR_EMPTY_FILE
				} else if (errorMessage.contains("File too large")) {
					errorType = ApiException.SEND_ERROR_FILE_TOO_BIG
				} else if (errorMessage.contains("Field too long")) {
					errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG
				} else if (errorMessage.contains("You cannot reply to this thread anymore")) {
					errorType = ApiException.SEND_ERROR_CLOSED
				} else if (errorMessage.contains("This board doesn't exist")) {
					errorType = ApiException.SEND_ERROR_NO_BOARD
				} else if (errorMessage.contains("Specified thread does not exist")) {
					errorType = ApiException.SEND_ERROR_NO_THREAD
				} else if (errorMessage.contains("You must wait")) {
					errorType = ApiException.SEND_ERROR_TOO_FAST
				} else if (errorMessage.contains("Corrupted file or unsupported file type")) {
					errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED
				} else if (errorMessage.contains("Duplicate file exists")) {
					errorType = ApiException.SEND_ERROR_FILE_EXISTS
				} else if (errorMessage.contains("has been blocked due to abuse") || errorMessage.contains("banned")) {
					errorType = ApiException.SEND_ERROR_BANNED
					extra = readBanExtra(data, data.boardName)
				} else if (errorMessage.contains("image replies has been reached")) {
					errorType = ApiException.SEND_ERROR_FILES_LIMIT
				}
				if (errorType != 0) {
					throw ApiException(errorType, extra)
				}
			}
			CommonUtils.writeLog("4chan send message", errorMessage)
			throw ApiException(errorMessage)
		}
		throw InvalidResponseException()
	}

	@Throws(HttpException::class, ApiException::class)
	override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult? {
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val uri = locator.createSysUri("imgboard.php")
		val entity = UrlEncodedEntity("mode", "usrdel", "pwd", data.password)
		for (postNumber in data.postNumbers) {
			entity.add(postNumber, "delete")
		}
		if (data.optionFilesOnly) {
			entity.add("onlyimgdel", "on")
		}
		val responseText = HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(strictUnsafeRedirectHandler)
				.perform()
				.readString()
		val matcher = PATTERN_POST_ERROR.matcher(responseText)
		if (matcher.find()) {
			var errorMessage = matcher.group(1)
			if (errorMessage != null) {
				var errorType = 0
				if (errorMessage.contains("Password incorrect")) {
					errorType = ApiException.DELETE_ERROR_PASSWORD
				} else if (errorMessage.contains("You must wait longer before deleting this post")) {
					errorType = ApiException.DELETE_ERROR_TOO_NEW
				} else if (errorMessage.contains("You cannot delete a post this old")) {
					errorType = ApiException.DELETE_ERROR_TOO_OLD
				} else if (errorMessage.contains("Can't find the post")) {
					errorType = ApiException.DELETE_ERROR_NOT_FOUND
				} else if (errorMessage.contains("You cannot delete posts this often")) {
					errorType = ApiException.DELETE_ERROR_TOO_OFTEN
				}
				if (errorType == ApiException.SEND_ERROR_CAPTCHA) {
					lastCaptchaPassData = null
					lastCaptchaPassCookie = null
				}
				if (errorType != 0) {
					throw ApiException(errorType)
				}
			}
			errorMessage = removeErrorFromMessage(errorMessage)
			CommonUtils.writeLog("4chan delete message", errorMessage)
			throw ApiException(errorMessage)
		}
		return null
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	override fun onSendReportPosts(data: SendReportPostsData): SendReportPostsResult? {
		val reportReason = ReportReason.fromKey(data.type)!!
		val configuration = ChanConfiguration.get(this) as FourchanChanConfiguration
		val locator = ChanLocator.get(this) as FourchanChanLocator
		val uri = locator.createSysUri("imgboard.php").buildUpon()
				.appendQueryParameter("board", data.boardName)
				.appendQueryParameter("mode", "report")
				.appendQueryParameter("no", data.postNumbers[0])
				.build()
		var retry = false
		var message: String
		while (true) {
			val captchaData = requireUserCaptcha(REQUIREMENT_REPORT, data.boardName, data.threadNumber, retry)
			retry = true
			if (captchaData == null) {
				throw ApiException(ApiException.REPORT_ERROR_NO_ACCESS)
			}
			val entity = UrlEncodedEntity("cat", reportReason.category, "cat_id", reportReason.value,
					"board", data.boardName)
			val captchaType = captchaData.get(CAPTCHA_DATA_KEY_TYPE)
			if (FourchanChanConfiguration.CAPTCHA_TYPE_4CHAN_CAPTCHA == captchaType) {
				entity.add("t-challenge", captchaData.get(CaptchaData.CHALLENGE))
				entity.add("t-response", captchaData.get(CaptchaData.INPUT))
			} else if (ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2 == captchaType) {
				entity.add("g-recaptcha-response", captchaData.get(CaptchaData.INPUT))
			}
			val fourchanPassCookie = getFourchanPassCookie(configuration, data.boardName)
			val response = HttpRequest(uri, data).setPostMethod(entity)
					.addCookie(COOKIE_FOURCHAN_PASS, fourchanPassCookie)
					.setRedirectHandler(strictUnsafeRedirectHandler)
					.perform()
			handleFourchanPass(response, data.boardName)
			val responseText = response.readString()
			val matcher = PATTERN_REPORT_MESSAGE.matcher(responseText)
			if (matcher.find()) {
				message = StringUtils.emptyIfNull(matcher.group(1))
				if (!message.contains("CAPTCHA")) {
					break
				}
			} else {
				throw InvalidResponseException()
			}
		}
		message = StringUtils.clearHtml(message).trim()
		var errorType = 0
		if (message.contains("Report submitted") || message.contains("You have already reported this post")) {
			return null
		} else if (message.contains("You cannot report a sticky")) {
			errorType = ApiException.REPORT_ERROR_NO_ACCESS
		}
		if (errorType != 0) {
			throw ApiException(errorType)
		}
		message = removeErrorFromMessage(message)
		CommonUtils.writeLog("4chan report message", message)
		throw ApiException(message)
	}


	private inner class FourchanSpurResolver : FirewallResolver() {
		override fun checkResponse(
			session: FirewallResolver.Session,
			response: HttpResponse,
		): FirewallResolver.CheckResponseResult? {
			val responseText = response.readString().orEmpty()
			if (responseText.contains("https://mcl.spur.us")) {
				val matcher = Pattern.compile("https://mcl\\.spur\\.us/d/mcl\\.js\\?tk=[a-zA-Z0-9._-]+").matcher(responseText)
				if (matcher.find()) {
					val scriptUrl = matcher.group(0)
					val key = session.getKey(FirewallResolver.Identifier.Flag.USER_AGENT) ?: return null
					return CheckResponseResult(key, Exclusive(scriptUrl))
				}
			}
			return null
		}

		override fun collectCookies(
			session: FirewallResolver.Session,
			cookieBuilder: CookieBuilder,
		) {
		}

		private inner class Exclusive(private val scriptUrl: String) : FirewallResolver.Exclusive {
			override fun resolve(
				session: FirewallResolver.Session,
				key: FirewallResolver.Exclusive.Key,
			): Boolean {
				val html = """
					<!DOCTYPE html>
					<html>
					<head>
						<meta charset="utf-8">
						<title></title>
						<script async src="$scriptUrl" id="_mcl"></script>
					</head>
					<body style="text-align:center;line-height:100vh;overflow:hidden;font-size:40px">
						<p>Loading security check...</p>
						<script>
							(function() {
								function proceed(bundle) {
									if (!bundle) bundle = 0;
									document.title = "SOLVED:" + bundle;
								}
								function configureMcl() {
									if (window.MCL) {
										MCL.configure({ onBundle: proceed });
									} else {
										setTimeout(configureMcl, 500);
									}
								}
								var el = document.getElementById('_mcl');
								el.addEventListener('load', configureMcl);
								el.addEventListener('error', function() { proceed(0); });
							})();
						</script>
					</body>
					</html>
				""".trimIndent()
				val dataUri = Uri.parse("data:text/html;charset=utf-8," + Uri.encode(html))
				val client = object : FirewallResolver.WebViewClient<String>("FourchanSpur") {
					override fun onPageFinished(
						uri: Uri,
						cookies: Map<String, String>,
						title: String?,
					): Boolean {
						if (title != null && title.startsWith("SOLVED:")) {
							setResult(title.substring("SOLVED:".length))
							return true
						}
						return false
					}
				}
				client.customUri = dataUri
				val mclToken = session.resolveWebView<String>(client)
				if (!mclToken.isNullOrEmpty() && mclToken != "0") {
					val postUri = session.getUri()!!
					val board = postUri.getQueryParameter("board") ?: "pol"
					val config = session.getChanConfiguration<FourchanChanConfiguration>()
					val boardsHost = if (config != null && config.isSafeForWork(board)) "boards.4channel.org" else "boards.4chan.org"
					val fourchanPassCookie = if (config != null) getFourchanPassCookie(config, board) else null
					val entity = MultipartEntity()
					entity.add("mcl", mclToken)
					try {
						val request = HttpRequest(postUri, session)
								.setPostMethod(entity)
								.addHeader("Referer", "https://$boardsHost/")
								.addHeader("Origin", "https://$boardsHost")
						if (!fourchanPassCookie.isNullOrEmpty()) {
							request.addCookie(COOKIE_FOURCHAN_PASS, fourchanPassCookie)
						}
						val responseJson = request.perform().readString()
						if (!responseJson.isNullOrEmpty()) {
							session.getChanConfiguration<ChanConfiguration>()?.storeCookie("fourchan_captcha_json", responseJson, null)
						}
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
				return false
			}
		}
	}

	companion object {
		private const val RECAPTCHA_API_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc"

		private const val CAPTCHA_DATA_KEY_TYPE = "captchaType"
		private const val CAPTCHA_DATA_KEY_PASS_COOKIE = "captchaPassCookie"

		private const val COOKIE_FOURCHAN_PASS = "4chan_pass"

		private const val REQUIREMENT_BANNED = "banned"
		private const val REQUIREMENT_REPORT = "report"

		private const val CAPTCHA_TICKET_KEY = "captcha_ticket"

		private val DATE_FORMAT_BAN = SimpleDateFormat("MMMM d yyyy", Locale.US)

		private fun parseBanDate(value: String?): Long {
			if (value == null) {
				return -1
			}
			val cleanedValue = value.replace("(st|nd|rd|th),".toRegex(), "")
			return try {
				val date = DATE_FORMAT_BAN.parse(cleanedValue)
				date?.time ?: 0L
			} catch (e: java.text.ParseException) {
				0L
			}
		}

		private fun removeErrorFromMessage(message: String?): String {
			if (message != null && message.startsWith("Error: ")) {
				return message.substring(7)
			}
			return message ?: ""
		}

		private val PATTERN_AUTH_MESSAGE = Pattern.compile("<h2.*?>(.*?)<(?:br|/h2)>")
		private val PATTERN_POST_ERROR = Pattern.compile("<span id=\"errmsg\".*?>(.*?)</span>")
		private val PATTERN_POST_SUCCESS = Pattern.compile("<!-- thread:(\\d+),no:(\\d+) -->")
		private val PATTERN_REPORT_MESSAGE = Pattern.compile("<font.*?>(.*?)<(?:br|/font)>")
		private val PATTERN_ARCHIVED_THREAD = Pattern.compile("<tr><td>(\\d+)</td>.*?" +
				"<td class=\"teaser-col\">(.*?)</td>")

		private val FORBIDDEN_OPTIONS = HashSet(Arrays.asList("nonoko", "nonokosage"))
	}
}


