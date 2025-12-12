package com.example.pixeldiet.ui.navigation

import android.app.Application
import com.example.pixeldiet.ui.main.AppSelectionScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pixeldiet.data.DatabaseProvider
import com.example.pixeldiet.friend.FriendRepository
import com.example.pixeldiet.friend.FriendViewModelFactory
import com.example.pixeldiet.friend.group.GroupDetailScreen
import com.example.pixeldiet.friend.group.GroupRepository
import com.example.pixeldiet.friend.group.GroupViewModel
import com.example.pixeldiet.friend.group.GroupViewModelFactory
import com.example.pixeldiet.ui.calendar.CalendarScreen
import com.example.pixeldiet.ui.friend.FriendScreen
import com.example.pixeldiet.ui.friend.FriendViewModel
import com.example.pixeldiet.ui.main.MainScreen
import com.example.pixeldiet.ui.settings.SettingsScreen
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(sharedViewModel: SharedViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val db = DatabaseProvider.getDatabase(context)

    // Repository 생성
    val friendRepository = FriendRepository(
        dao = db.friendDao(),
        firestore = FirebaseFirestore.getInstance(),
        auth = FirebaseAuth.getInstance()
    )
    val groupRepository = GroupRepository(
        dao = db.groupDao(),
        firestore = FirebaseFirestore.getInstance(),
        auth = FirebaseAuth.getInstance(),
        friendRepository = friendRepository,
        userProfileDao = db.userProfileDao()
    )

    // ViewModel 한 번만 생성
    val friendViewModel: FriendViewModel = viewModel(
        factory = FriendViewModelFactory(friendRepository)
    )
    val groupViewModel: GroupViewModel = viewModel(
        factory = GroupViewModelFactory(groupRepository, db.trackedAppDao(), LocalContext.current.applicationContext as Application)
    )

    val items = listOf(
        BottomNavItem.Main,
        BottomNavItem.Calendar,
        BottomNavItem.Friends,
        BottomNavItem.Settings,
    )
    val welcomeText by sharedViewModel.userName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = welcomeText, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Main.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Main.route) {
                MainScreen(
                    viewModel = sharedViewModel,
                    onAppSelectionClick = { navController.navigate("app_selection") }
                )
            }

            composable(BottomNavItem.Calendar.route) {
                CalendarScreen(viewModel = sharedViewModel)
            }

            composable(BottomNavItem.Friends.route) {
                // 이미 생성한 ViewModel 전달
                FriendScreen(
                    navController = navController,
                    viewModel = friendViewModel,
                    groupViewModel = groupViewModel
                )
            }

            composable(BottomNavItem.Settings.route) {
                SettingsScreen()
            }

            composable("app_selection") {
                AppSelectionScreen(
                    viewModel = sharedViewModel,
                    onDone = { navController.popBackStack() }
                )
            }

            composable("groupDetail") {
                // FriendScreen에서 사용하던 동일한 ViewModel 전달
                GroupDetailScreen(
                    viewModel = friendViewModel,
                    groupviewModel = groupViewModel
                )
            }
        }
    }
}
