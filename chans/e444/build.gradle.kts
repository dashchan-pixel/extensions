plugins {
    id("chan-extension")
}

chan {
    apiVersion = 1
    // ech.u is a Web3 (Unstoppable Domains) name with no DNS record, so it stays first only
    // because hosts[0] becomes the extension's displayed title. The reachable mirrors follow
    // and must match addConvertableChanHost in E444ChanLocator.
    hosts("ech.u", "ech.bz", "ech.ist")
    // Adds E444ChanPostDecorator, which draws post polls and reactions.
    postDecorator = true
}
