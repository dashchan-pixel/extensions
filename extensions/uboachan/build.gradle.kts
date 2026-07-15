plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Uboachan"
	packageName = "com.mishiranu.dashchan.chan.uboachan"
	versionName = "1.0"
	apiVersion = 1
	icon = "ic_custom_uboachan_white"
	hosts("uboachan.net", "www.uboachan.net")
}
