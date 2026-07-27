plugins {
    id("chan-extension")
}

chan {
    apiVersion = 1
    hosts("kohlchan.net", "www.kohlchan.net")
}

dependencies {
    implementation(project(":engines:lynxchan"))
}
