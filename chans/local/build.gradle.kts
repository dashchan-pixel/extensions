plugins {
    id("chan-extension")
}

chan {
    versionName = "26.7"
    apiVersion = 1
    hosts("localhost")
    customUriHandler = true
    customFilter = "<data android:scheme=\"file\" />\n<data android:mimeType=\"text/html\" />\n"
}
