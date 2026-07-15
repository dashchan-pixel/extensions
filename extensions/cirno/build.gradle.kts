plugins {
	id("chan-extension")
}

chan {
	versionName = "1.22-experimental-1.0"
	apiVersion = 1
	hosts("iichan.hk", "n.iichan.hk", "on.iichan.hk", "iichan.moe", "closed.iichan.moe")
}

dependencies {
	implementation(project(":engines:wakaba"))
}
