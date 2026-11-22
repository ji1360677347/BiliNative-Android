package local.bili23.android.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import local.bili23.android.core.Video
import local.bili23.android.ui.dynamic.DynamicScreen
import local.bili23.android.ui.home.HomeScreen
import local.bili23.android.ui.player.PlayerScreen
import local.bili23.android.ui.profile.FavoritesScreen
import local.bili23.android.ui.profile.FollowingScreen
import local.bili23.android.ui.profile.HistoryScreen
import local.bili23.android.ui.profile.ProfileScreen
import local.bili23.android.ui.profile.SettingsScreen
import local.bili23.android.ui.profile.UploaderScreen
import local.bili23.android.ui.search.SearchScreen
import local.bili23.android.ui.theme.AppColors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "首页", Icons.Filled.Home),
    Tab("search", "搜索", Icons.Filled.Search),
    Tab("dynamic", "动态", Icons.Filled.Notifications),
    Tab("profile", "我的", Icons.Filled.Person),
)

fun playerRoute(video: Video): String = "player/${video.routeId}?cid=${video.cid}"

fun playerRoute(id: String, cid: Int = 0): String = "player/$id?cid=$cid"

@Composable
fun RootNav(pendingVideoId: String?, onPendingConsumed: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == null || tabs.any { it.route == route }

    LaunchedEffect(pendingVideoId) {
        val id = pendingVideoId ?: return@LaunchedEffect
        nav.navigate(playerRoute(id, 0))
        onPendingConsumed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                val colors = AppColors.current
                NavigationBar(
                    containerColor = colors.card,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                if (route != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.pink,
                                selectedTextColor = colors.pink,
                                indicatorColor = colors.pinkSoft,
                                unselectedIconColor = colors.text3,
                                unselectedTextColor = colors.text3,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = if (showBar) Modifier.padding(padding) else Modifier,
        ) {
            composable("home") {
                HomeScreen(
                    onOpen = { nav.navigate(playerRoute(it)) },
                    onSearch = { nav.navigate("search") },
                )
            }
            composable("search") {
                SearchScreen(onOpen = { nav.navigate(playerRoute(it)) })
            }
            composable("dynamic") {
                DynamicScreen(
                    onOpen = { nav.navigate(playerRoute(it)) },
                    onLogin = { nav.navigate("profile") },
                )
            }
            composable("profile") {
                ProfileScreen(
                    onOpen = { nav.navigate(playerRoute(it)) },
                    onNavigate = { nav.navigate(it) },
                )
            }
            composable(
                "player/{id}?cid={cid}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("cid") {
                        type = NavType.StringType
                        defaultValue = "0"
                    },
                ),
            ) { entry ->
                PlayerScreen(
                    videoId = entry.arguments?.getString("id").orEmpty(),
                    initialCid = entry.arguments?.getString("cid")?.toLongOrNull() ?: 0L,
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(playerRoute(it)) },
                    onOpenSpace = { mid, name ->
                        nav.navigate("space/$mid?name=${Uri.encode(name)}")
                    },
                )
            }
            composable("history") {
                HistoryScreen(onOpen = { nav.navigate(playerRoute(it)) }, onBack = { nav.popBackStack() })
            }
            composable("favorites") {
                FavoritesScreen(onOpen = { nav.navigate(playerRoute(it)) }, onBack = { nav.popBackStack() })
            }
            composable("following") {
                FollowingScreen(
                    onOpenAccount = { account ->
                        nav.navigate("space/${account.mid}?name=${Uri.encode(account.name)}")
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "space/{mid}?name={name}",
                arguments = listOf(
                    navArgument("mid") { type = NavType.IntType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                UploaderScreen(
                    mid = entry.arguments?.getInt("mid") ?: 0,
                    name = entry.arguments?.getString("name").orEmpty(),
                    onOpen = { nav.navigate(playerRoute(it)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
