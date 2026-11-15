package com.example.adblocker

data class BlocklistSource(
    val id: String,
    val displayName: String,
    val url: String,
    val defaultEnabled: Boolean
)

data class SourceStatus(
    val id: String,
    val entryCount: Int = 0,
    val lastUpdatedMillis: Long = 0L
)

object BlocklistSources {
    val STEVEN_BLACK = BlocklistSource(
        id = "stevenblack",
        displayName = "StevenBlack",
        url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        defaultEnabled = true
    )

    val ADAWAY = BlocklistSource(
        id = "adaway",
        displayName = "AdAway",
        url = "https://adaway.org/hosts.txt",
        defaultEnabled = true
    )

    val PETER_LOWE = BlocklistSource(
        id = "peter_lowe",
        displayName = "Peter Lowe",
        url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
        defaultEnabled = true
    )

    val ONE_HOSTS_LITE = BlocklistSource(
        id = "onehosts_lite",
        displayName = "1Hosts Lite",
        url = "https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/hosts.txt",
        defaultEnabled = true
    )

    val HAGEZI_PRO = BlocklistSource(
        id = "hagezi_pro",
        displayName = "HaGeZi Pro",
        url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt",
        defaultEnabled = false
    )

    val ALL: List<BlocklistSource> = listOf(
        STEVEN_BLACK,
        ADAWAY,
        PETER_LOWE,
        ONE_HOSTS_LITE,
        HAGEZI_PRO
    )

    val DEFAULT_ENABLED_IDS: Set<String> = ALL
        .filter { it.defaultEnabled }
        .mapTo(LinkedHashSet()) { it.id }
}
