package com.jensen.draculanightbynight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.timeline.TimelineScreen
import com.jensen.draculanightbynight.timeline.TimelineViewModel
import com.jensen.draculanightbynight.ui.DraculaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as DraculaApp).container
        setContent { DraculaTheme { AppNavigation(app) } }
    }
}

@Composable
private fun AppNavigation(app: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "timeline") {
        composable("timeline") {
            TimelineScreen(viewModel { TimelineViewModel(app) }, onOpen = { nav.navigate("reader/$it") })
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ReaderScreen(
                viewModel(key = id) { ReaderViewModel(app, id) },
                onBack = { nav.popBackStack() },
                onOpen = { next -> nav.navigate("reader/$next") { popUpTo("timeline") } },
            )
        }
    }
}
