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
import java.util.concurrent.ConcurrentHashMap

class LocalChanPerformer : ChanPerformer() {
    /**
     * Which directory each archive was last seen in, as a path prefix ending in `/` — empty for one
     * sitting in the archive root.
     *
     * The thread number cannot carry the directory itself: the client limits it to 30 characters and
     * requires it to be usable as a file name, so the name of the archive stays bare and where it
     * lives is remembered here. Written and read from the background threads the reads run on.
     */
    private val directoryPrefixes = ConcurrentHashMap<String, String>()

    @Throws(HttpException::class)
    override fun onReadThreads(data: ReadThreadsData): ReadThreadsResult? {
        val configuration = ChanConfiguration.get<LocalChanConfiguration>(this)
        val thread = Thread.currentThread()
        val found = ArrayList<Archive>()
        if (!collectArchives(configuration.localDownloadDirectory, "", 0, found)) {
            return null
        }
        // Newest first across the whole tree, so subdirectories interleave with the root instead of
        // clumping at the end of the list. One entry per name after that: two files sharing one are
        // the same thread saved twice, and the client has no way to tell them apart.
        found.sortByDescending { it.file.lastModified }
        val archives = found.distinctBy { it.name }
        // Remember every directory while the whole tree is in hand. Opening a thread from this list
        // then costs no lookup, and it opens the same copy the list showed.
        for (archive in archives) {
            directoryPrefixes[archive.name] = archive.prefix
        }
        val threads = ArrayList<Posts>()
        val from = (THREADS_PER_PAGE * data.pageNumber).coerceAtMost(archives.size)
        val to = (from + THREADS_PER_PAGE).coerceAtMost(archives.size)
        for (index in from until to) {
            val archive = archives[index]
            try {
                archive.file.openInputStream().use { input ->
                    threads.add(LocalPostsParser(this, archive.name).convertThread(input))
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
        val directory = configuration.localDownloadDirectory
        val threadNumber = data.threadNumber
        val prefix = resolveDirectoryPrefix(directory, threadNumber)
        val file = directory.getChild("$prefix$threadNumber$HTML_EXTENSION")
        try {
            file.openInputStream().use { input ->
                return ReadPostsResult(LocalPostsParser(this, threadNumber).convertPosts(input))
            }
        } catch (e: ParseException) {
            throw InvalidResponseException(e)
        } catch (e: IOException) {
            forgetDirectoryPrefix(threadNumber)
            throw HttpException.createNotFoundException()
        }
    }

    @Throws(HttpException::class, InvalidResponseException::class)
    override fun onReadContent(data: ReadContentData): ReadContentResult {
        val configuration = ChanConfiguration.get<LocalChanConfiguration>(this)
        val path = data.uri.path
        return if (data.uri.authority == "localhost" && path != null) {
            val directory = configuration.localDownloadDirectory
            // The archive HTML names its media relative to the archive root, as
            // `<archive name>/src/...`, so the leading segment says which archive to look up and the
            // rest of the path hangs off wherever that archive turned out to live.
            val archiveName = path.trim('/').substringBefore('/')
            val prefix = resolveDirectoryPrefix(directory, archiveName)
            val file = directory.getChild(prefix + path)
            try {
                ReadContentResult(HttpResponse(file.openInputStream()))
            } catch (e: IOException) {
                forgetDirectoryPrefix(archiveName)
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
        val prefix = resolveDirectoryPrefix(localDownloadDirectory, threadNumber)
        val file = localDownloadDirectory.getChild("$prefix$threadNumber$HTML_EXTENSION")
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
                forgetDirectoryPrefix(threadNumber)
                throw ApiException(ApiException.DELETE_ERROR_NOT_FOUND)
            }
        if (data.postNumbers[0] != posts[0].postNumber) {
            throw ApiException(ApiException.DELETE_ERROR_NO_ACCESS)
        }
        removeDirectory(localDownloadDirectory.getChild("$prefix$threadNumber"))
        if (!Thread.currentThread().isInterrupted) {
            file.delete()
            forgetDirectoryPrefix(threadNumber)
        }
        return null
    }

    /**
     * Collects every `<name>.html` under [directory] depth first, so that a thread the user filed
     * away in a subdirectory is listed alongside the ones left in the archive root.
     *
     * A directory named exactly like a sibling `.html` file is that archive's own `src`/`thumb`
     * store, and is never entered: it holds no archives and can hold hundreds of files. For an
     * archive nobody has reorganised that skips every subdirectory there is, leaving the walk the
     * single directory listing it has always been.
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
                val archiveName = toArchiveName(child.name)
                if (archiveName != null) {
                    archiveNames.add(archiveName)
                    archives.add(Archive(child, archiveName, prefix))
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

    /**
     * The prefix of the directory holding the archive named [archiveName], from the last scan that
     * saw it. A name no scan has seen — one opened out of history, or handed over by a file manager
     * — is searched for, root first, so an archive nobody has reorganised resolves off the same
     * single directory listing as before.
     *
     * The result is remembered either way, including the empty prefix a search that found nothing
     * falls back to; every caller drops it again if the file then fails to open, which is what lets
     * an archive the user has since moved resolve afresh instead of staying broken.
     */
    private fun resolveDirectoryPrefix(
        directory: DataFile,
        archiveName: String,
    ): String {
        val known = directoryPrefixes[archiveName]
        if (known != null) {
            return known
        }
        val prefix = findArchive(directory, "", 0, archiveName).orEmpty()
        directoryPrefixes[archiveName] = prefix
        return prefix
    }

    private fun forgetDirectoryPrefix(archiveName: String) {
        directoryPrefixes.remove(archiveName)
    }

    /** Returns the prefix of the directory under [directory] holding `<archiveName>.html`, or null. */
    private fun findArchive(
        directory: DataFile,
        prefix: String,
        depth: Int,
        archiveName: String,
    ): String? {
        val children = directory.children ?: return null
        val subdirectories = ArrayList<DataFile>()
        val archiveNames = HashSet<String>()
        for (child in children) {
            if (child.isDirectory) {
                subdirectories.add(child)
            } else {
                toArchiveName(child.name)?.let { archiveNames.add(it) }
            }
        }
        if (archiveName in archiveNames) {
            return prefix
        }
        if (depth >= MAX_SCAN_DEPTH) {
            return null
        }
        val thread = Thread.currentThread()
        for (subdirectory in subdirectories) {
            if (thread.isInterrupted) {
                return null
            }
            val name = subdirectory.name
            if (name !in archiveNames) {
                findArchive(subdirectory, "$prefix$name/", depth + 1, archiveName)?.let { return it }
            }
        }
        return null
    }

    /** The archive name a file name stands for, or null if the file is not an archive at all. */
    private fun toArchiveName(fileName: String): String? =
        if (fileName.endsWith(HTML_EXTENSION)) {
            fileName.substring(0, fileName.length - HTML_EXTENSION.length)
        } else {
            null
        }

    private class Archive(
        val file: DataFile,
        val name: String,
        val prefix: String,
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

        // The archive is whatever the user has made of it, so the walk is bounded rather than
        // trusting the tree to be shallow.
        private const val MAX_SCAN_DEPTH = 6
    }
}
