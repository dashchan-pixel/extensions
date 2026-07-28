package chan.build

open class ChanExtension {
	var name: String? = null
	var nameUpper: String? = null
	var packageName: String? = null
	var apiVersion: Int = 0
	var icon: String? = null
	var updateUri: String? = null
	var hosts: Array<String> = emptyArray()
	var customUriHandler: Boolean = false
	var customFilter: String? = null

	// Opts into the optional fifth component, <NameUpper>ChanPostDecorator. Off by default: the
	// generated manifest entry, keep rule and USED_CLASSES reference must all stay absent for the
	// extensions that do not have the class, or they stop compiling.
	var postDecorator: Boolean = false

	fun hosts(vararg hosts: String) {
		this.hosts = arrayOf(*hosts)
	}

	fun apiVersion(apiVersion: Int) {
		this.apiVersion = apiVersion
	}

	fun customUriHandler(customUriHandler: Boolean) {
		this.customUriHandler = customUriHandler
	}

	fun customFilter(customFilter: String) {
		this.customFilter = customFilter
	}

	fun postDecorator(postDecorator: Boolean) {
		this.postDecorator = postDecorator
	}
}
