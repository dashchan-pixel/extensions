plugins {
    id("chan-extension")
}

chan {
    apiVersion = 1
    hosts("2ch.org", "2ch.su", "2ch.life", "2ch.hk")
}

dependencies {
    implementation("org.jsoup:jsoup:1.21.2")
}
