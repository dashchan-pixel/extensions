plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Fourplebs"
	packageName = "com.mishiranu.dashchan.chan.fourplebs"
	versionName = "1.4"
	apiVersion = 1
	icon = "ic_custom_fourplebs_white"
	hosts("4plebs.org", "www.4plebs.org", "archive.4plebs.org")
}
