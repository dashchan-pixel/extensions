package com.mishiranu.dashchan.chan.local

import chan.content.ApiException
import chan.content.ChanConfiguration
import chan.content.ChanPerformer
import chan.content.InvalidResponseException
import chan.content.model.Posts
import chan.http.HttpException
import chan.http.HttpResponse
import chan.text.ParseException
import chan.util.DataFile
import java.io.IOException

class LocalChanPerformer : ChanPerformer() {
	@Throws(HttpException::class)
	override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult? {
		val configuration = ChanConfiguration.get<LocalChanConfiguration>(this)
		val thread = Thread.currentThread()
		val threads = ArrayList<Posts>()
		val from = THREADS_PER_PAGE * data.pageNumber
		val to = from + THREADS_PER_PAGE
		var current = 0
		val files = configuration.localDownloadDirectory.children
		if (files != null) {
			files.sortedByDescending { it.lastModified }.forEach { file ->
				val name = file.name
				if (!file.isDirectory && name.endsWith(".html")) {
					if (current in from until to) {
						val threadNumber = name.substring(0, name.length - 5)
						try {
							file.openInputStream().use { input ->
								threads.add(LocalPostsParser(this, threadNumber).convertThread(input))
							}
						} catch (e: IOException) {
							// Ignore
						} catch (e: ParseException) {
							// Ignore
						}
						if (thread.isInterrupted) {
							return null
						}
					}
					current++
				}
			}
		}
		return when {
			threads.isEmpty() && data.pageNumber == 0 -> null
			threads.isEmpty() -> throw HttpException.createNotFoundException()
			else -> ReadThreadsResult(threads)
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadPosts(data: ReadPostsData): ReadPostsResult {
		val configuration = ChanConfiguration.get<LocalChanConfiguration>(this)
		val file = configuration.localDownloadDirectory.getChild("${data.threadNumber}.html")
		try {
			file.openInputStream().use { input ->
				return ReadPostsResult(LocalPostsParser(this, data.threadNumber).convertPosts(input))
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw HttpException.createNotFoundException()
		}
	}

	@Throws(HttpException::class, InvalidResponseException::class)
	override fun onReadContent(data: ReadContentData): ReadContentResult {
		val configuration = ChanConfiguration.get<LocalChanConfiguration>(this)
		val path = data.uri.path
		return if (data.uri.authority == "localhost" && path != null) {
			val file = configuration.localDownloadDirectory.getChild(path)
			try {
				ReadContentResult(HttpResponse(file.openInputStream()))
			} catch (e: IOException) {
				throw HttpException.createNotFoundException()
			}
		} else {
			super.onReadContent(data)
		}
	}

	@Throws(ApiException::class, InvalidResponseException::class)
	override fun onSendDeletePosts(data: SendDeletePostsData): SendDeletePostsResult? {
		val configuration = ChanConfiguration.get<LocalChanConfiguration>(this)
		val localDownloadDirectory = configuration.localDownloadDirectory
		val file = localDownloadDirectory.getChild("${data.threadNumber}.html")
		val posts = try {
			file.openInputStream().use { input ->
				val thread = LocalPostsParser(this, data.threadNumber).convertThread(input)
						?: throw ApiException(ApiException.DELETE_ERROR_NOT_FOUND)
				thread.posts?.takeIf { it.isNotEmpty() }
						?: throw ApiException(ApiException.DELETE_ERROR_NOT_FOUND)
			}
		} catch (e: ParseException) {
			throw InvalidResponseException(e)
		} catch (e: IOException) {
			throw ApiException(ApiException.DELETE_ERROR_NOT_FOUND)
		}
		if (data.postNumbers[0] != posts[0].postNumber) {
			throw ApiException(ApiException.DELETE_ERROR_NO_ACCESS)
		}
		removeDirectory(localDownloadDirectory.getChild(data.threadNumber))
		if (!Thread.currentThread().isInterrupted) {
			file.delete()
		}
		return null
	}

	private fun removeDirectory(directory: DataFile) {
		val thread = Thread.currentThread()
		directory.children?.forEach { file ->
			if (thread.isInterrupted) {
				return
			}
			if (file.isDirectory) {
				removeDirectory(file)
			} else {
				file.delete()
			}
		}
		directory.delete()
	}

	companion object {
		private const val THREADS_PER_PAGE = 20
	}
}
