plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Allchan"
	packageName = "com.mishiranu.dashchan.chan.allchan"
	versionName = "1.8"
	apiVersion = 1
	icon = "ic_custom_allchan_white"
	hosts("allchan.su", "www.allchan.su")
}
