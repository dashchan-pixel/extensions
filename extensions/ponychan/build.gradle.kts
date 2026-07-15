plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Ponychan"
	packageName = "com.mishiranu.dashchan.chan.ponychan"
	versionName = "1.7"
	apiVersion = 1
	icon = "ic_custom_ponychan_white"
	hosts("ponychan.net", "www.ponychan.net", "ml.ponychan.net", "vintage.ponychan.net")
}
