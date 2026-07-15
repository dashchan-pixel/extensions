plugins {
	id("chan-extension")
}

chan {
	nameUpper = "Ponyach"
	packageName = "com.mishiranu.dashchan.chan.ponyach"
	versionName = "1.3"
	apiVersion = 1
	icon = "ic_custom_ponyach_white"
	hosts("ponyach.ru", "ponychan.ru", "ponya.ch", "ponyach.cf", "ponyach.ga", "ponyach.ml")
}
