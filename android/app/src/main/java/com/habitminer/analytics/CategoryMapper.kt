package com.habitminer.analytics

/**
 * Maps an app to a friendly [AppCategory].
 *
 * Android's own ApplicationInfo.category is missing for most apps, which is why the
 * History screen used to tag YouTube clients as OTHER. The order of checks matters:
 * the system's GAME flag first, then exact packages, then name/package keywords, and
 * finally the category stored at collection time.
 */
object CategoryMapper {
    private val exactPackages: Map<String, AppCategory> =
        mapOf(
            "com.whatsapp" to AppCategory.MESSAGING,
            "com.whatsapp.w4b" to AppCategory.MESSAGING,
            "org.telegram.messenger" to AppCategory.MESSAGING,
            "org.thoughtcrime.securesms" to AppCategory.MESSAGING,
            "com.facebook.orca" to AppCategory.MESSAGING,
            "jp.naver.line.android" to AppCategory.MESSAGING,
            "com.viber.voip" to AppCategory.MESSAGING,
            "com.skype.raider" to AppCategory.MESSAGING,
            "com.discord" to AppCategory.MESSAGING,
            "com.google.android.apps.messaging" to AppCategory.MESSAGING,
            "com.google.android.dialer" to AppCategory.MESSAGING,
            "com.android.contacts" to AppCategory.MESSAGING,
            "com.google.android.apps.tachyon" to AppCategory.MESSAGING,
            "com.instagram.android" to AppCategory.SOCIAL,
            "com.snapchat.android" to AppCategory.SOCIAL,
            "com.facebook.katana" to AppCategory.SOCIAL,
            "com.twitter.android" to AppCategory.SOCIAL,
            "com.reddit.frontpage" to AppCategory.SOCIAL,
            "com.linkedin.android" to AppCategory.SOCIAL,
            "com.instagram.barcelona" to AppCategory.SOCIAL,
            "com.pinterest" to AppCategory.SOCIAL,
            "com.zhiliaoapp.musically" to AppCategory.VIDEO,
            "com.ss.android.ugc.trill" to AppCategory.VIDEO,
            "com.google.android.youtube" to AppCategory.VIDEO,
            "com.netflix.mediaclient" to AppCategory.VIDEO,
            "com.amazon.avod.thirdpartyclient" to AppCategory.VIDEO,
            "in.startv.hotstar" to AppCategory.VIDEO,
            "com.jio.media.jiocinema" to AppCategory.VIDEO,
            "tv.twitch.android.app" to AppCategory.VIDEO,
            "com.spotify.music" to AppCategory.MUSIC,
            "com.google.android.apps.youtube.music" to AppCategory.MUSIC,
            "com.jio.media.jiobeats" to AppCategory.MUSIC,
            "com.bsbportal.music" to AppCategory.MUSIC,
            "com.gaana" to AppCategory.MUSIC,
            "com.google.android.googlequicksearchbox" to AppCategory.BROWSING,
            "com.android.chrome" to AppCategory.BROWSING,
            "com.brave.browser" to AppCategory.BROWSING,
            "org.mozilla.firefox" to AppCategory.BROWSING,
            "com.google.android.gm" to AppCategory.PRODUCTIVITY,
            "com.microsoft.office.outlook" to AppCategory.PRODUCTIVITY,
            "com.google.android.apps.meetings" to AppCategory.PRODUCTIVITY,
            "us.zoom.videomeetings" to AppCategory.PRODUCTIVITY,
            "com.google.android.apps.maps" to AppCategory.NAVIGATION,
            "com.ubercab" to AppCategory.NAVIGATION,
            "com.olacabs.customer" to AppCategory.NAVIGATION,
            "com.amazon.mShop.android.shopping" to AppCategory.SHOPPING,
            "com.flipkart.android" to AppCategory.SHOPPING,
            "in.swiggy.android" to AppCategory.SHOPPING,
            "com.application.zomato" to AppCategory.SHOPPING,
            "com.google.android.apps.nbu.paisa.user" to AppCategory.SHOPPING,
            "com.phonepe.app" to AppCategory.SHOPPING,
            "net.one97.paytm" to AppCategory.SHOPPING,
            "com.android.settings" to AppCategory.TOOLS,
            "com.iqoo.secure" to AppCategory.TOOLS,
        )

    // Keyword → category, checked in order against "package + app name" (lower-case).
    private val keywords: List<Pair<String, AppCategory>> =
        listOf(
            "game" to AppCategory.GAMES,
            "evony" to AppCategory.GAMES,
            "pubg" to AppCategory.GAMES,
            "bgmi" to AppCategory.GAMES,
            "freefire" to AppCategory.GAMES,
            "clash" to AppCategory.GAMES,
            "minecraft" to AppCategory.GAMES,
            "chess" to AppCategory.GAMES,
            "ludo" to AppCategory.GAMES,
            "whatsapp" to AppCategory.MESSAGING,
            "telegram" to AppCategory.MESSAGING,
            "messenger" to AppCategory.MESSAGING,
            "messages" to AppCategory.MESSAGING,
            "messaging" to AppCategory.MESSAGING,
            "dialer" to AppCategory.MESSAGING,
            "contacts" to AppCategory.MESSAGING,
            "signal" to AppCategory.MESSAGING,
            "instagram" to AppCategory.SOCIAL,
            "snapchat" to AppCategory.SOCIAL,
            "facebook" to AppCategory.SOCIAL,
            "twitter" to AppCategory.SOCIAL,
            "reddit" to AppCategory.SOCIAL,
            "linkedin" to AppCategory.SOCIAL,
            "threads" to AppCategory.SOCIAL,
            "youtube" to AppCategory.VIDEO,
            "tube" to AppCategory.VIDEO,
            "netflix" to AppCategory.VIDEO,
            "primevideo" to AppCategory.VIDEO,
            "hotstar" to AppCategory.VIDEO,
            "cinema" to AppCategory.VIDEO,
            "video" to AppCategory.VIDEO,
            "player" to AppCategory.VIDEO,
            "twitch" to AppCategory.VIDEO,
            "tiktok" to AppCategory.VIDEO,
            "spotify" to AppCategory.MUSIC,
            "music" to AppCategory.MUSIC,
            "podcast" to AppCategory.MUSIC,
            "saavn" to AppCategory.MUSIC,
            "audible" to AppCategory.MUSIC,
            "browser" to AppCategory.BROWSING,
            "chrome" to AppCategory.BROWSING,
            "firefox" to AppCategory.BROWSING,
            "brave" to AppCategory.BROWSING,
            "opera" to AppCategory.BROWSING,
            "duckduckgo" to AppCategory.BROWSING,
            "gmail" to AppCategory.PRODUCTIVITY,
            "outlook" to AppCategory.PRODUCTIVITY,
            "mail" to AppCategory.PRODUCTIVITY,
            "docs" to AppCategory.PRODUCTIVITY,
            "sheets" to AppCategory.PRODUCTIVITY,
            "slides" to AppCategory.PRODUCTIVITY,
            "office" to AppCategory.PRODUCTIVITY,
            "notion" to AppCategory.PRODUCTIVITY,
            "keep" to AppCategory.PRODUCTIVITY,
            "calendar" to AppCategory.PRODUCTIVITY,
            "drive" to AppCategory.PRODUCTIVITY,
            "slack" to AppCategory.PRODUCTIVITY,
            "teams" to AppCategory.PRODUCTIVITY,
            "meet" to AppCategory.PRODUCTIVITY,
            "zoom" to AppCategory.PRODUCTIVITY,
            "classroom" to AppCategory.PRODUCTIVITY,
            "chatgpt" to AppCategory.PRODUCTIVITY,
            "claude" to AppCategory.PRODUCTIVITY,
            "gemini" to AppCategory.PRODUCTIVITY,
            "copilot" to AppCategory.PRODUCTIVITY,
            "github" to AppCategory.PRODUCTIVITY,
            "notes" to AppCategory.PRODUCTIVITY,
            "duolingo" to AppCategory.PRODUCTIVITY,
            "coursera" to AppCategory.PRODUCTIVITY,
            "kindle" to AppCategory.PRODUCTIVITY,
            "maps" to AppCategory.NAVIGATION,
            "uber" to AppCategory.NAVIGATION,
            "rapido" to AppCategory.NAVIGATION,
            "amazon" to AppCategory.SHOPPING,
            "flipkart" to AppCategory.SHOPPING,
            "myntra" to AppCategory.SHOPPING,
            "swiggy" to AppCategory.SHOPPING,
            "zomato" to AppCategory.SHOPPING,
            "blinkit" to AppCategory.SHOPPING,
            "zepto" to AppCategory.SHOPPING,
            "paytm" to AppCategory.SHOPPING,
            "phonepe" to AppCategory.SHOPPING,
            "fitness" to AppCategory.HEALTH,
            "strava" to AppCategory.HEALTH,
            "health" to AppCategory.HEALTH,
            "settings" to AppCategory.TOOLS,
            "camera" to AppCategory.TOOLS,
            "gallery" to AppCategory.TOOLS,
            "photos" to AppCategory.TOOLS,
            "files" to AppCategory.TOOLS,
            "filemanager" to AppCategory.TOOLS,
            "clock" to AppCategory.TOOLS,
            "calculator" to AppCategory.TOOLS,
            "phone management" to AppCategory.TOOLS,
            "security" to AppCategory.TOOLS,
            "cleaner" to AppCategory.TOOLS,
            "installer" to AppCategory.TOOLS,
            "permissioncontroller" to AppCategory.TOOLS,
        )

    // Short tokens that would match inside unrelated words, so they must match a whole word.
    private val wordKeywords: Map<String, AppCategory> =
        mapOf(
            "yt" to AppCategory.VIDEO,
            "mx" to AppCategory.VIDEO,
            "vlc" to AppCategory.VIDEO,
        )

    private val storedFallback: Map<String, AppCategory> =
        mapOf(
            "SOCIAL" to AppCategory.SOCIAL,
            "COMMUNICATION" to AppCategory.MESSAGING,
            "ENTERTAINMENT" to AppCategory.VIDEO,
            "GAMING" to AppCategory.GAMES,
            "PRODUCTIVITY" to AppCategory.PRODUCTIVITY,
            "EDUCATION" to AppCategory.PRODUCTIVITY,
            "NAVIGATION" to AppCategory.NAVIGATION,
            "SHOPPING" to AppCategory.SHOPPING,
            "HEALTH" to AppCategory.HEALTH,
        )

    fun categorize(
        packageName: String,
        appName: String,
        storedCategory: String? = null,
    ): AppCategory {
        if (storedCategory == "GAMING") return AppCategory.GAMES
        exactPackages[packageName]?.let { return it }

        val haystack = "$packageName $appName".lowercase()
        keywords.firstOrNull { (key, _) -> haystack.contains(key) }?.let { return it.second }

        val words = haystack.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        wordKeywords.entries.firstOrNull { it.key in words }?.let { return it.value }

        return storedCategory?.let { storedFallback[it] } ?: AppCategory.OTHER
    }

    /**
     * Apps that are system plumbing rather than behaviour (settings, permission dialogs,
     * phone managers). They still count towards screen time but are hidden from session
     * cards and pattern lists.
     */
    fun isSystemNoise(
        packageName: String,
        appName: String,
    ): Boolean {
        val name = appName.lowercase()
        return packageName in noisePackages ||
            name == "phone management" ||
            name == "i manager" ||
            packageName.contains("packageinstaller") ||
            packageName.contains("permissioncontroller")
    }

    private val noisePackages =
        setOf(
            "android",
            "com.android.settings",
            "com.android.systemui",
            "com.iqoo.secure",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.samsung.android.lool",
            "com.vivo.permissionmanager",
            "com.google.android.gms",
        )
}
