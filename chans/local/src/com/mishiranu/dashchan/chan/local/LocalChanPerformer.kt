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
        val archives = ArrayList<Archive>()
        if (!collectArchives(configuration.localDownloadDirectory, "", 0, archives)) {
            return null
        }
        // One sort across the whole tree, so subdirectories interleave with the root by age instead
        // of clumping at the end of the list.
        archives.sortByDescending { it.file.lastModified }
        val threads = ArrayList<Posts>()
        val from = (THREADS_PER_PAGE * data.pageNumber).coerceAtMost(archives.size)
        val to = (from + THREADS_PER_PAGE).coerceAtMost(archives.size)
        for (index in from until to) {
            val archive = archives[index]
            try {
                archive.file.openInputStream().use { input ->
                    threads.add(LocalPostsParser(this, archive.threadNumber).convertThread(input))
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
        val threadNumber = data.threadNumber ?: throw ApiException(ApiException.DELETE_ERROR_NOT_FOUND)
        val localDownloadDirectory = configuration.localDownloadDirectory
        val file = localDownloadDirectory.getChild("$threadNumber.html")
        val posts =
            try {
                file.openInputStream().use { input ->
                    val thread = LocalPostsParser(this, threadNumber).convertThread(input)
                    thread.posts.takeIf { it.isNotEmpty() }
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
        removeDirectory(localDownloadDirectory.getChild(threadNumber))
        if (!Thread.currentThread().isInterrupted) {
            file.delete()
        }
        return null
    }

    /**
     * Collects every `<name>.html` under [directory] depth first, naming each one by its path
     * relative to the archive root — `12345` at the root, `old/12345` a directory down. That
     * relative path is the thread number the rest of the extension works with, so a thread the user
     * filed away in a subdirectory stays reachable and keeps its own media next to it.
     *
     * A directory named exactly like a sibling `.html` file is that archive's own `src`/`thumb`
     * store, and is skipped: it holds no archives and can hold hundreds of files.
     *
     * Returns false if the thread was interrupted, in which case [archives] is incomplete.
     */
    private fun collectArchives(
        directory: DataFile,
        prefix: String,
        depth: Int,
        archives: MutableList<Archive>,
    ): Boolean {
        // getChildren() returns null when the directory is missing or is not a directory.
        val children = directory.children ?: return true
        val subdirectories = ArrayList<DataFile>()
        val archiveNames = HashSet<String>()
        for (child in children) {
            if (child.isDirectory) {
                subdirectories.add(child)
            } else {
                val name = child.name
                if (name.endsWith(HTML_EXTENSION)) {
                    val archiveName = name.substring(0, name.length - HTML_EXTENSION.length)
                    archiveNames.add(archiveName)
                    archives.add(Archive(child, prefix + archiveName))
                }
            }
        }
        if (depth >= MAX_SCAN_DEPTH) {
            return true
        }
        val thread = Thread.currentThread()
        for (subdirectory in subdirectories) {
            if (thread.isInterrupted) {
                return false
            }
            val name = subdirectory.name
            if (name !in archiveNames && !collectArchives(subdirectory, "$prefix$name/", depth + 1, archives)) {
                return false
            }
        }
        return true
    }

    private class Archive(
        val file: DataFile,
        val threadNumber: String,
    )

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
        private const val HTML_EXTENSION = ".html"

        // The archive is whatever the user made of it, so the walk is bounded rather than trusting
        // the tree to be shallow.
        private const val MAX_SCAN_DEPTH = 6
    }
}
