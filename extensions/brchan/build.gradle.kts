plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Brchan"
	packageName = "com.mishiranu.dashchan.chan.brchan"
	versionName = "1.7"
	apiVersion = 1
	icon = "ic_custom_brchan_white"
	hosts("brchan.org", "www.brchan.org")
}
