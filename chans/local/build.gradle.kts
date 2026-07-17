plugins {
    id("chan-extension")
}

chan {
    apiVersion = 1
    hosts("localhost")
    customUriHandler = true
    customFilter = "<data android:scheme=\"file\" />\n<data android:mimeType=\"text/html\" />\n"
}
