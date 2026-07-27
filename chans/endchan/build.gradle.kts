plugins {
    id("chan-extension")
}

chan {
    apiVersion = 1
    hosts("endchan.net", "endchan.org")
}

dependencies {
    implementation(project(":engines:lynxchan"))
}
