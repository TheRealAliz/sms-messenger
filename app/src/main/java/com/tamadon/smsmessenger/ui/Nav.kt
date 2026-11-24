package com.tamadon.smsmessenger.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

fun chatRoute(address: String, body: String? = null, msgId: Long? = null): String {
    val base = "chat/${Uri.encode(address)}"
    val params = buildList {
        body?.let { add("body=${Uri.encode(it)}") }
        msgId?.let { add("msg=$it") }
    }
    return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
}

enum class ListMode { HOME, ARCHIVE, SPAM, BLOCKED }

@Composable
fun AppNav(activity: FragmentActivity, target: MutableState<Target?>) {
    val nav = rememberNavController()

    // Opened from a notification, an sms: link or "share via SMS"
    LaunchedEffect(target.value) {
        target.value?.let {
            nav.navigate(chatRoute(it.address, it.body)) { launchSingleTop = true }
            target.value = null
        }
    }

    // The Surface is the colour seen between two destinations during a transition. Without it the
    // platform window background (grey in light mode, wrong when the in-app theme is forced) leaks
    // through and produces the flash that used to happen when opening / closing a chat.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = nav,
            startDestination = "home",
            // Plain pages (settings, chats, search ...) cross-fade quickly; the old stock 700 ms
            // fade made switching feel sluggish.
            enterTransition = { fadeIn(tween(170)) },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(170)) },
            popExitTransition = { fadeOut(tween(140)) }
        ) {
            composable("home") { ConversationsScreen(nav, activity, ListMode.HOME) }
            composable("archive") { ConversationsScreen(nav, activity, ListMode.ARCHIVE) }
            composable("spam") { ConversationsScreen(nav, activity, ListMode.SPAM) }
            composable("blocked") { ConversationsScreen(nav, activity, ListMode.BLOCKED) }
            composable(
                route = "chat/{addr}?body={body}&msg={msg}",
                arguments = listOf(
                    navArgument("addr") { type = NavType.StringType },
                    navArgument("body") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("msg") { type = NavType.LongType; defaultValue = -1L }
                ),
                enterTransition = {
                    slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(tween(180))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(170)) },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = tween(280), targetOffsetX = { it }) + fadeOut(tween(170))
                }
            ) { entry ->
                ChatScreen(
                    nav = nav,
                    activity = activity,
                    addr = entry.arguments?.getString("addr").orEmpty(),
                    prefill = entry.arguments?.getString("body"),
                    highlightId = entry.arguments?.getLong("msg") ?: -1L
                )
            }
            composable("new") { NewChatScreen(nav) }
            composable("search") { SearchScreen(nav) }
            composable("starred") { StarredScreen(nav) }
            composable("trash") { TrashScreen(nav) }
            composable("quick") { QuickRepliesScreen(nav) }
            composable("settings") { SettingsScreen(nav, activity) }
        }
    }
}
