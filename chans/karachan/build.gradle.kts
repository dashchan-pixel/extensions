plugins {
    id("chan-extension")
}

chan {
    apiVersion = 1
    hosts("karachan.org", "www.karachan.org")
}

dependencies {
    implementation("chan.library:template-parser:0")
}
