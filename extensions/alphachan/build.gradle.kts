plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Alphachan"
	packageName = "com.mishiranu.dashchan.chan.alphachan"
	versionName = "1.0"
	apiVersion = 1
	icon = "ic_custom_alphachan_white"
	hosts("alphachan.org", "www.alphachan.org")
}
