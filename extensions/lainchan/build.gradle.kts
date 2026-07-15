plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Lainchan"
	packageName = "com.mishiranu.dashchan.chan.lainchan"
	versionName = "1.5"
	apiVersion = 1
	icon = "ic_custom_lainchan_white"
	hosts("lainchan.org", "www.lainchan.org")
}
