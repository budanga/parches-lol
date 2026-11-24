package com.budanga.parcheslol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.budanga.parcheslol.work.UpdateWorker
import com.budanga.parcheslol.widget.PatchWidget
import androidx.glance.appwidget.updateAll
import com.budanga.parcheslol.domain.routeForNotification
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.budanga.parcheslol.anim.DecipherText
import com.budanga.parcheslol.anim.DitherCover
import com.budanga.parcheslol.anim.DitherReveal
import com.budanga.parcheslol.anim.DitherShimmer
import com.budanga.parcheslol.anim.FRAME_MS
import com.budanga.parcheslol.anim.rememberReduceMotion
import com.budanga.parcheslol.ui.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.budanga.parcheslol.data.DATA_BASE_URL
import com.budanga.parcheslol.data.Prefs
import com.budanga.parcheslol.data.Repository
import com.budanga.parcheslol.ui.ArchiveTheme
import com.budanga.parcheslol.ui.isDarkTheme
import com.budanga.parcheslol.ui.ChampionScreen
import com.budanga.parcheslol.ui.ChampionsScreen
import com.budanga.parcheslol.ui.HomeScreen
import com.budanga.parcheslol.ui.Ink
import com.budanga.parcheslol.ui.NewsScreen
import com.budanga.parcheslol.ui.PatchScreen
import com.budanga.parcheslol.ui.RowDivider
import com.budanga.parcheslol.ui.SettingsScreen
import com.budanga.parcheslol.ui.AnimationsScreen
import com.budanga.parcheslol.ui.FavoritesScreen
import java.io.File

class MainActivity : ComponentActivity() {
    /** Route requested by a notification tap, consumed by the navigation once. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = routeForNotification(intent.getStringExtra(EXTRA_PATCH))
    }

    companion object { const val EXTRA_PATCH = "patch" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingRoute.value = routeForNotification(intent.getStringExtra(EXTRA_PATCH))
        // Paper background in every system theme: always dark status/navigation icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val repo = Repository(DATA_BASE_URL, File(filesDir, "data"))
        val prefs = Prefs(this)
        UpdateWorker.createChannels(this)
        UpdateWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val mode by prefs.theme.collectAsState(initial = "system")
            val dark = isDarkTheme(mode, androidx.compose.foundation.isSystemInDarkTheme())
            // Status bar icons follow the theme: dark on paper, light on the dark archive.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // The widget follows the same theme setting.
            LaunchedEffect(mode) { runCatching { PatchWidget().updateAll(this@MainActivity) } }
            ArchiveTheme(dark) { AppRoot(repo, prefs, pendingRoute) }
        }
    }
}

private val TABS = listOf("home", "champions", "news")

/** The title assembles from glyphs once per app process (animation 1). */
private object Session { var titlePlayed = false }

@Composable
fun AppRoot(repo: Repository, prefs: Prefs, pendingRoute: androidx.compose.runtime.MutableState<String?> = mutableStateOf(null)) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduce = rememberReduceMotion()
    val cover = remember { Animatable(0f) }
    var transitioning by remember { mutableStateOf(false) }
    val transitionOn by prefs.animTransition.collectAsState(initial = true)
    LaunchedEffect(pendingRoute.value) {
        pendingRoute.value?.let { nav.navigate(it); pendingRoute.value = null }
    }

    /** Animation 4: terracotta pixels cover, navigate, then uncover (250 ms each way, 12 fps steps). */
    fun openPatchWithCover(id: String) {
        if (transitioning) return // a second tap during the transition does nothing
        if (reduce || !transitionOn) { nav.navigate("patch/$id"); return }
        transitioning = true
        scope.launch {
            for (f in 1..3) { delay(FRAME_MS); cover.snapTo(f / 3f) }
            nav.navigate("patch/$id")
            for (f in 2 downTo 0) { delay(FRAME_MS); cover.snapTo(f / 3f) }
            transitioning = false
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                nav, startDestination = "home",
                enterTransition = { fadeIn(tween(180, easing = EaseOutCubic)) },
                exitTransition = { fadeOut(tween(180, easing = EaseInCubic)) },
                popEnterTransition = { fadeIn(tween(180, easing = EaseOutCubic)) },
                popExitTransition = { fadeOut(tween(180, easing = EaseInCubic)) },
            ) {
                composable("home") {
                    val playTitle = remember { !Session.titlePlayed.also { Session.titlePlayed = true } }
                    // Animation 5: decided before any row is drawn, so a new row never flashes visible first.
                    var seenAtStart by remember { mutableStateOf<Set<String>?>(null) }
                    var titleOn by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) { titleOn = prefs.animTitle.first(); seenAtStart = prefs.revealedPatches.first() }
                    val seen = seenAtStart ?: return@composable
                    HomeScreen(
                        repo, prefs,
                        onOpenPatch = ::openPatchWithCover,
                        onSettings = { nav.navigate("settings") },
                        title = { DecipherText(stringResource(R.string.archive_title), Type.serif(20), play = playTitle && titleOn) },
                        loading = { DitherShimmer() },
                        // First install (nothing seen yet): everything counts as seen, nothing animates.
                        wrapRow = { p, content -> DitherReveal(play = seen.isNotEmpty() && p !in seen) { content() } },
                        onIndex = { patches ->
                            scope.launch {
                                prefs.addRevealed(patches)
                                runCatching { PatchWidget().updateAll(context) }
                            }
                        },
                    )
                }
                composable("patch/{id}") { back ->
                    val id = back.arguments?.getString("id")!!
                    // Animation 2: numbers decipher only the first time this patch is opened.
                    // Saved with the screen: coming back from a champion draws the patch at once, scroll included.
                    var firstOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(id) {
                        // Animation 2 only plays the first time, and only if its switch is on.
                        if (firstOpen == null) firstOpen = id !in prefs.openedPatches.first() && prefs.animNumbers.first()
                    }
                    // Marked as opened only once it actually loads, so an offline attempt keeps the animation.
                    firstOpen?.let {
                        PatchScreen(id, repo, prefs, onBack = { nav.popBackStack() }, animateNumbers = it,
                            onLoaded = { if (it) scope.launch { prefs.addOpened(id) } },
                            onOpenEntity = { e -> nav.navigate(if (e.kind == "item") "item/${e.id}" else "champion/${e.id}") })
                    }
                }
                composable("champions") {
                    ChampionsScreen(repo, prefs, onOpen = { nav.navigate("champion/$it") }, onOpenItem = { nav.navigate("item/$it") })
                }
                composable("item/{id}") { back ->
                    ChampionScreen(back.arguments?.getString("id")!!, repo, onBack = { nav.popBackStack() }, kind = "item")
                }
                composable("champion/{id}") { back ->
                    ChampionScreen(back.arguments?.getString("id")!!, repo, onBack = { nav.popBackStack() })
                }
                composable("news") { NewsScreen(repo) }
                composable("settings") {
                    SettingsScreen(repo, prefs, onBack = { nav.popBackStack() }, onAnimations = { nav.navigate("animations") },
                        onFavorites = { kind -> nav.navigate("favorites/$kind") })
                }
                composable("favorites/{kind}") { back ->
                    FavoritesScreen(back.arguments?.getString("kind")!!, repo, prefs, onBack = { nav.popBackStack() })
                }
                composable("animations") { AnimationsScreen(prefs, onBack = { nav.popBackStack() }) }
            }
        }
        if (route in TABS) BottomBar(nav, route)
    }
    DitherCover(cover.value, Modifier.fillMaxSize())
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    RowDivider()
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val tabs: List<Triple<String, ImageVector, Int>> = listOf(
            Triple("home", Icons.Outlined.Description, R.string.tab_patches),
            Triple("champions", Icons.Outlined.Groups, R.string.tab_champions),
            Triple("news", Icons.Outlined.Newspaper, R.string.tab_news),
        )
        tabs.forEach { (dest, icon, label) ->
            Box(Modifier.weight(1f)) {
                IconButton(
                    onClick = {
                        if (route != dest) nav.navigate(dest) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                ) { Icon(icon, stringResource(label), tint = if (route == dest) Ink.Accent else Ink.Faint) }
            }
        }
    }
}
