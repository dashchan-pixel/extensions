package chan.build

open class ChanExtension {
	var name: String? = null
	var nameUpper: String? = null
	var packageName: String? = null
	var versionName: String? = null
	var apiVersion: Int = 0
	var icon: String? = null
	var updateUri: String? = null
	var hosts: Array<String> = emptyArray()
	var customUriHandler: Boolean = false
	var customFilter: String? = null

	fun hosts(vararg hosts: String) {
		this.hosts = arrayOf(*hosts)
	}

	fun versionName(versionName: String) {
		this.versionName = versionName
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
}
