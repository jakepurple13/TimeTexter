package com.programmersbox.timetexter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.programmersbox.timetexter.ui.theme.TimeTexterTheme
import com.programmersbox.timetexter.ui.theme.currentColorScheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val dao by lazy { ItemDatabase.getInstance(this).itemDao() }

    private val workManager by lazy { WorkManager.getInstance(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(currentColorScheme) {
                val systemController = rememberSystemUiController()
                val statusBarColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                SideEffect { systemController.setStatusBarColor(color = statusBarColor) }

                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = Screen.Main.route) {
                    composable(Screen.Main.route) { MainView(dao = dao, workManager = workManager, navController = navController) }
                    composable(Screen.AddItem.route) { AddNewItem(dao = dao, workManager = workManager, navController = navController) }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Main : Screen("mainScreen")
    object AddItem : Screen("addItem")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(dao: ItemDao, workManager: WorkManager, navController: NavController) {
    val items by dao.getAll().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SmallTopAppBar(
                title = { Text("Time Texter") },
                actions = { Text("${items.size} texts") },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                onClick = {
                    /*TODO*/
                    navController.navigate(Screen.AddItem.route)
                    /*scope.launch {
                        dao.newItem(
                            TextInfo(
                                "asdf${items.size}",
                                "asdf",
                                "asdf",
                                123L,
                                false
                            )
                        )
                    }*/
                }
            ) { Icon(Icons.Default.Add, contentDescription = null) }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { p ->
        AnimatedLazyColumn(
            contentPadding = p,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
            items = items.fastMap { item ->
                AnimatedLazyListItem(key = item.id, value = item) {
                    TimeTextItem(
                        item = item,
                        delete = { scope.launch { dao.removeItem(item) } },
                        checked = {
                            item.isActive = it
                            scope.launch { println(dao.updateItem(item)) }
                            if (it) {

                                /*workManager.enqueueUniquePeriodicWork(
                                    item.id,
                                    ExistingPeriodicWorkPolicy.KEEP,
                                    PeriodicWorkRequestBuilder()
                                )*/

                            } else {
                                workManager.cancelUniqueWork(item.id)
                            }
                        }
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TimeTextItem(
    item: TextInfo,
    delete: () -> Unit = {},
    checked: (Boolean) -> Unit = {}
) {

    var showPopup by remember { mutableStateOf(false) }

    if (showPopup) {

        val onDismiss = { showPopup = false }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete ${item.id}") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onDismiss()
                        delete()
                    }
                ) { androidx.compose.material3.Text("Yes") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("No") } }
        )

    }

    val dismissState = rememberDismissState(
        confirmStateChange = {
            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                showPopup = true
            }
            false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    DismissValue.DismissedToEnd -> Color.Red
                    DismissValue.DismissedToStart -> Color.Red
                }
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.scale(scale)
                )
            }
        }
    ) {

        var isActive by remember { mutableStateOf(item.isActive) }

        ElevatedCard {
            ListItem(
                text = { Text(text = item.id) },
                secondaryText = { Text(item.text) },
                overlineText = { Text(item.type.name) },
                trailing = {
                    Switch(
                        checked = isActive,
                        onCheckedChange = {
                            checked(it)
                            isActive = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface,
                            uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            disabledCheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                .copy(alpha = ContentAlpha.disabled)
                                .compositeOver(androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface),
                            disabledCheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                .copy(alpha = ContentAlpha.disabled)
                                .compositeOver(androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface),
                            disabledUncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface
                                .copy(alpha = ContentAlpha.disabled)
                                .compositeOver(androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface),
                            disabledUncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                .copy(alpha = ContentAlpha.disabled)
                                .compositeOver(androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface)
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme(darkColorScheme()) {
        Scaffold {
            LazyColumn(contentPadding = it) {
                item {
                    TimeTextItem(
                        TextInfo(
                            "asdf",
                            "asdf",
                            TimeType.DAILY,
                            123L,
                            false,
                            listOf("123-456-789")
                        )
                    )
                }

                item {
                    TimeTextItem(
                        TextInfo(
                            "asdf",
                            "asdf",
                            TimeType.DAILY,
                            123L,
                            true,
                            listOf("123-456-789")
                        )
                    )
                }
            }
        }
    }
}