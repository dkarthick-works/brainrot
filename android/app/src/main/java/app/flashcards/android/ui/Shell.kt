package app.flashcards.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.flashcards.android.AppContainer
import app.flashcards.android.ui.components.BText
import app.flashcards.android.ui.components.BrainrotIcon
import app.flashcards.android.ui.components.BrainrotIcons
import app.flashcards.android.ui.components.plainClick
import app.flashcards.android.ui.screens.AgentAccessPlaceholder
import app.flashcards.android.ui.screens.PlayConnectionCheck
import app.flashcards.android.ui.screens.ScreenPlaceholder
import app.flashcards.android.ui.screens.SettingsPlaceholder
import app.flashcards.android.ui.theme.Brainrot
import app.flashcards.android.ui.theme.SpaceGrotesk
import app.flashcards.android.ui.theme.cardShadow
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable data object Play : Route
    @Serializable data object Freestyle : Route
    @Serializable data object Topics : Route
    @Serializable data class Cards(val topicId: String? = null) : Route
    @Serializable data object Settings : Route
    @Serializable data object AgentAccess : Route
}

private data class MenuItem(val label: String, val route: Route, val icon: ImageVector, val isCurrent: (NavDestination?) -> Boolean)

private val Menu = listOf(
    MenuItem("Freestyle", Route.Freestyle, BrainrotIcons.Freestyle) { it?.hasRoute<Route.Freestyle>() == true },
    MenuItem("Topics", Route.Topics, BrainrotIcons.Topics) { it?.hasRoute<Route.Topics>() == true },
    MenuItem("Cards", Route.Cards(), BrainrotIcons.Cards) { it?.hasRoute<Route.Cards>() == true },
    MenuItem("Settings", Route.Settings, BrainrotIcons.Settings) {
        it?.hasRoute<Route.Settings>() == true || it?.hasRoute<Route.AgentAccess>() == true
    },
)

private val DrawerEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

@Composable
fun Shell(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val colors = Brainrot.colors

    val isPlay = destination?.hasRoute<Route.Play>() ?: true
    val isSubScreen = destination?.hasRoute<Route.AgentAccess>() == true && !menuOpen

    // Every drawer screen sits directly above Play, so system back returns there
    fun go(route: Route) {
        menuOpen = false
        if (route == Route.Play) {
            nav.popBackStack(Route.Play, inclusive = false)
        } else if (Menu.none { it.route == route && it.isCurrent(destination) }) {
            nav.navigate(route) {
                popUpTo(Route.Play)
                launchSingleTop = true
            }
        }
    }

    BackHandler(enabled = menuOpen) { menuOpen = false }

    val progress by animateFloatAsState(if (menuOpen) 1f else 0f, tween(300, easing = DrawerEasing), label = "drawer")
    val scrim by animateFloatAsState(if (menuOpen) 1f else 0f, tween(250), label = "scrim")

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.bg)) {
        val drawerWidth = min(maxWidth * 0.78f, 300.dp)
        val drawerPx = with(LocalDensity.current) { drawerWidth.toPx() }

        NavHost(nav, startDestination = Route.Play, Modifier.fillMaxSize()) {
            composable<Route.Play> { PlayConnectionCheck(container.api) }
            composable<Route.Freestyle> { ScreenPlaceholder("Freestyle", "Arrives in phase 3") }
            composable<Route.Topics> { ScreenPlaceholder("Topics", "Arrives in phase 4") }
            composable<Route.Cards> { backStackEntry ->
                backStackEntry.toRoute<Route.Cards>()
                ScreenPlaceholder("Cards", "Arrives in phase 5")
            }
            composable<Route.Settings> {
                SettingsPlaceholder(container.themeStore) { nav.navigate(Route.AgentAccess) { launchSingleTop = true } }
            }
            composable<Route.AgentAccess> { AgentAccessPlaceholder() }
        }

        if (scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(Color.Black.copy(alpha = 0.3f))
                    .plainClick { menuOpen = false },
            )
        }

        if (progress > 0f) {
            Column(
                Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = -drawerPx * (1f - progress) }
                    .background(colors.card)
                    .border(1.dp, colors.line)
                    .plainClick(onClick = {})
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                    .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    BText(
                        "brainrot.",
                        Modifier.padding(start = 12.dp, top = 16.dp).height(36.dp).plainClick { go(Route.Play) },
                        size = 30.sp,
                        weight = FontWeight.Bold,
                        family = SpaceGrotesk,
                    )
                }
                Menu.forEach { item ->
                    val current = item.isCurrent(destination)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (current) colors.bg else Color.Transparent)
                            .semantics { selected = current }
                            .plainClick { go(item.route) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BrainrotIcon(item.icon, colors.muted)
                        BText(item.label, weight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }

        val label = when {
            isSubScreen -> "Back to Settings"
            menuOpen -> "Close menu"
            else -> "Open menu"
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))) {
            MenuPill(
                showBack = !isPlay && !menuOpen,
                progress = progress,
                label = label,
                modifier = Modifier.offset(x = 16.dp + (drawerWidth - 84.dp) * progress, y = 16.dp),
            ) {
                if (isSubScreen) nav.popBackStack() else menuOpen = !menuOpen
            }
        }
    }
}

@Composable
private fun MenuPill(showBack: Boolean, progress: Float, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = Brainrot.colors
    val shape = RoundedCornerShape(999.dp)
    val density = LocalDensity.current
    val shift = with(density) { 3.5.dp.toPx() } * progress
    Box(
        modifier
            .size(52.dp, 36.dp)
            .cardShadow(colors, 18.dp)
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.line, shape)
            .semantics { contentDescription = label }
            .plainClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showBack) {
            BrainrotIcon(BrainrotIcons.Back, colors.ink)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(1f, -1f).forEach { direction ->
                    Box(
                        Modifier
                            .size(18.dp, 2.dp)
                            .graphicsLayer {
                                translationY = shift * direction
                                rotationZ = 45f * progress * direction
                            }
                            .background(colors.ink, CircleShape),
                    )
                }
            }
        }
    }
}
