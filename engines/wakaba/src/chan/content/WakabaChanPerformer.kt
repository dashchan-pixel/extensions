package chan.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Pair
import chan.content.model.Post
import chan.content.model.Posts
import chan.http.HttpException
import chan.http.HttpRequest
import chan.http.HttpResponse
import chan.http.MultipartEntity
import chan.http.RequestEntity
import chan.http.UrlEncodedEntity
import chan.text.ParseException
import chan.util.CommonUtils
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class WakabaChanPerformer : ChanPerformer() {
	@Throws(IOException::class, ParseException::class, InvalidResponseException::class, RedirectException::class)
	protected abstract fun parseThreads(boardName: String?, input: InputStream): List<Posts>?

	@Throws(HttpException::class, InvalidResponseException::class, RedirectException::class)
	open override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult {
		val locator = ChanLocator.get(this) as WakabaChanLocator
		val uri = locator.createBoardUri(data.boardName, data.pageNumber)
		val response = HttpRequest(uri, data).setValidator(data.validator).perform()
		try {
			response.open().use { input ->
				return ReadThreadsResult(parseThreads(data.boardName, input))
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(IOException::class, ParseException::class)
	protected abstract fun parsePosts(boardName: String?, input: InputStream): List<Post>?

	@Throws(HttpException::class, InvalidResponseException::class)
	protected open fun readPosts(data: ReadPostsData, uri: Uri,
			redirectHandler: HttpRequest.RedirectHandler?): List<Post> {
		val request = HttpRequest(uri, data).setValidator(data.validator)
		if (redirectHandler != null) {
			request.setRedirectHandler(redirectHandler)
		}
		val response = request.perform()
		try {
			response.open().use { input ->
				val posts = parsePosts(data.boardName, input)
				if (posts == null || posts.isEmpty()) {
					throw InvalidResponseException()
				}
				return posts
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw response.fail(e)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	open override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
		val locator = ChanLocator.get(this) as WakabaChanLocator
		val uri = locator.createThreadUri(data.boardName, data.threadNumber)
		return ReadPostsResult(readPosts(data, uri, null))
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	protected open fun readCaptchaResult(data: ReadCaptchaData, uri: Uri): ReadCaptchaResult {
		val image = HttpRequest(uri, data).perform().readBitmap()
		if (image != null) {
			val newImage = Bitmap.createBitmap(image.width, 32, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(newImage)
			canvas.drawColor(0xffffffff.toInt())
			val paint = Paint()
			paint.colorFilter = CAPTCHA_FILTER
			canvas.drawBitmap(image, 0f, (newImage.height - image.height) / 2f, paint)
			image.recycle()
			return ReadCaptchaResult(CaptchaState.CAPTCHA, CaptchaData()).setImage(newImage)
		}
		throw InvalidResponseException()
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	protected open fun readCaptchaScript(data: ReadCaptchaData, script: String?): ReadCaptchaResult {
		val locator = ChanLocator.get(this) as WakabaChanLocator
		val uri = locator.createScriptUri(data.boardName, script).buildUpon().appendQueryParameter("key",
				if (data.threadNumber == null) "mainpage" else "res" + data.threadNumber).build()
		return readCaptchaResult(data, uri)
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	open override fun onReadCaptcha(data: ReadCaptchaData): ReadCaptchaResult {
		return readCaptchaScript(data, "captcha.pl")
	}

	fun interface SendPostEntityMapper {
		fun convert(field: String): String
	}

	@Throws(HttpException::class)
	protected open fun executeWakaba(boardName: String?, entity: RequestEntity,
			preset: HttpRequest.Preset?): Pair<HttpResponse, Uri> {
		val locator = ChanLocator.get(this) as WakabaChanLocator
		val uri = locator.createScriptUri(boardName, "wakaba.pl")
		val response = HttpRequest(uri, preset).setPostMethod(entity)
				.setRedirectHandler(POST_REDIRECT_HANDLER).perform()
		if (response.responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
			return Pair(null, response.redirectedUri)
		}
		return Pair(response, null)
	}

	protected enum class ErrorSource {
		POST, DELETE
	}

	@Throws(ApiException::class)
	protected open fun handleError(errorSource: ErrorSource, responseText: String) {
		val matcher = PATTERN_POST_ERROR.matcher(responseText)
		if (matcher.find()) {
			val errorMessage = matcher.group(1)
			if (errorMessage != null) {
				var errorType = 0
				var flags = 0
				when (errorSource) {
					ErrorSource.POST -> {
						if (errorMessage.contains("Wrong verification code entered") ||
								errorMessage.contains("No verification code on record") ||
								errorMessage.contains("Введён неверный код подтверждения") ||
								errorMessage.contains("Код подтверждения не найден в базе")) {
							errorType = ApiException.SEND_ERROR_CAPTCHA
						} else if (errorMessage.contains("No comment entered") ||
								errorMessage.contains("Пустое поле сообщения")) {
							errorType = ApiException.SEND_ERROR_EMPTY_COMMENT
							flags = flags or ApiException.FLAG_KEEP_CAPTCHA
						} else if (errorMessage.contains("No file selected") ||
								errorMessage.contains("Сообщения без изображений запрещены")) {
							errorType = ApiException.SEND_ERROR_EMPTY_FILE
							flags = flags or ApiException.FLAG_KEEP_CAPTCHA
						} else if (errorMessage.contains("This image is too large") ||
								errorMessage.contains("Изображение слишком большое")) {
							errorType = ApiException.SEND_ERROR_FILE_TOO_BIG
							flags = flags or ApiException.FLAG_KEEP_CAPTCHA
						} else if (errorMessage.contains("Too many characters") ||
								errorMessage.contains("превышает заданный предел")) {
							errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG
							flags = flags or ApiException.FLAG_KEEP_CAPTCHA
						} else if (errorMessage.contains("Этот файл уже был запощен")) {
							errorType = ApiException.SEND_ERROR_FILE_EXISTS
						} else if (errorMessage.contains("Thread does not exist") ||
								errorMessage.contains("Тред не существует")) {
							errorType = ApiException.SEND_ERROR_NO_THREAD
						} else if (errorMessage.contains("String refused") ||
								errorMessage.contains("Flood detected, ") ||
								errorMessage.contains("Строка отклонена")) {
							errorType = ApiException.SEND_ERROR_SPAM_LIST
							flags = flags or ApiException.FLAG_KEEP_CAPTCHA
						} else if (errorMessage.contains("Host is banned")) {
							errorType = ApiException.SEND_ERROR_BANNED
						} else if (errorMessage.contains("Flood detected") ||
								errorMessage.contains("Флуд")) {
							errorType = ApiException.SEND_ERROR_TOO_FAST
						}
					}
					ErrorSource.DELETE -> {
						if (errorMessage.contains("Incorrect password for deletion") ||
								errorMessage.contains("Введён неверный пароль для удаления")) {
							errorType = ApiException.DELETE_ERROR_PASSWORD
						}
					}
				}
				if (errorType != 0) {
					throw ApiException(errorType, flags)
				}
			}
			CommonUtils.writeLog("Wakaba send message", errorMessage)
			throw ApiException(errorMessage)
		}
		if (responseText.contains("<h1>Anti-spam filters triggered.</h1>")) {
			throw ApiException(ApiException.SEND_ERROR_SPAM_LIST, ApiException.FLAG_KEEP_CAPTCHA)
		}
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	open override fun onSendPost(data: SendPostData): SendPostResult? {
		val entity = createSendPostEntity(data, null)
		val response = executeWakaba(data.boardName, entity, data)
		val httpResponse = response.first
		if (httpResponse == null) {
			return null
		}
		handleError(ErrorSource.POST, httpResponse.readString())
		throw InvalidResponseException()
	}

	@Throws(HttpException::class, ApiException::class, InvalidResponseException::class)
	open override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult? {
		val entity = UrlEncodedEntity("task", "delete", "password", data.password)
		for (postNumber in data.postNumbers) {
			entity.add("delete", postNumber)
		}
		if (data.optionFilesOnly) {
			entity.add("fileonly", "on")
		}
		val result = executeWakaba(data.boardName, entity, data)
		val httpResponse = result.first
		if (httpResponse == null) {
			return null
		}
		handleError(ErrorSource.DELETE, httpResponse.readString())
		throw InvalidResponseException()
	}

	companion object {
		private val CAPTCHA_FILTER = ColorMatrixColorFilter(floatArrayOf(
				0f, 0f, 1f, 0f, 0f,
				0f, 0f, 1f, 0f, 0f,
				0f, 0f, 1f, 0f, 0f,
				0f, 0f, 0f, 1f, 0f
		))

		private val PATTERN_POST_ERROR = Pattern.compile("<h1 style=\"text-align: center\">(.*?)<br />")
		private val DEFAULT_MAPPER = SendPostEntityMapper { field -> field }

		private val POST_REDIRECT_HANDLER = HttpRequest.RedirectHandler { response ->
			if (response.responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
				HttpRequest.RedirectHandler.Action.CANCEL
			} else {
				HttpRequest.RedirectHandler.STRICT.onRedirect(response)
			}
		}

		@JvmStatic
		fun createSendPostEntity(data: SendPostData, mapper: SendPostEntityMapper?): RequestEntity {
			val actualMapper = mapper ?: DEFAULT_MAPPER
			val entity = MultipartEntity()
			entity.add(actualMapper.convert("task"), "post")
			entity.add(actualMapper.convert("parent"), data.threadNumber)
			entity.add(actualMapper.convert("field1"), data.name)
			entity.add(actualMapper.convert("field2"), if (data.optionSage) "sage" else data.email)
			entity.add(actualMapper.convert("field3"), data.subject)
			entity.add(actualMapper.convert("field4"), data.comment)
			entity.add(actualMapper.convert("password"), data.password)
			val attachments = data.attachments
			if (attachments != null && attachments.isNotEmpty()) {
				val attachment = attachments[0]
				attachment.addToEntity(entity, actualMapper.convert("file"))
				if (attachment.optionSpoiler) {
					entity.add(actualMapper.convert("spoiler"), "on")
				}
			} else {
				entity.add(actualMapper.convert("nofile"), "1")
			}
			val captchaData = data.captchaData
			if (captchaData != null) {
				entity.add(actualMapper.convert("captcha"), captchaData[CaptchaData.INPUT])
			}
			return entity
		}
	}
}
