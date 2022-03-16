package com.programmersbox.timetexter

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.accompanist.permissions.PermissionsRequired
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.programmersbox.timetexter.ui.theme.currentColorScheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

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

@OptIn(
    ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun MainView(dao: ItemDao, workManager: WorkManager, navController: NavController) {
    val items by dao.getAll().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior() }

    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SmallTopAppBar(
                title = { Text("Time Texter") },
                actions = {
                    val time by currentTime()
                    Text(
                        context.getSystemDateTimeFormat().format(time),
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    Text("${items.size} texts")

                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                onClick = { navController.navigate(Screen.AddItem.route) }
            ) { Icon(Icons.Default.Add, contentDescription = null) }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { p ->

        val storagePermissions = rememberMultiplePermissionsState(listOf(Manifest.permission.SEND_SMS))

        LaunchedEffect(Unit) { storagePermissions.launchMultiplePermissionRequest() }

        PermissionsRequired(
            multiplePermissionsState = storagePermissions,
            permissionsNotGrantedContent = {
                NeedsPermissions { storagePermissions.launchMultiplePermissionRequest() }
            },
            permissionsNotAvailableContent = {
                NeedsPermissions {
                    context.startActivity(
                        Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            },
            content = {
                AnimatedLazyColumn(
                    contentPadding = p,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    items = items.fastMap { item ->
                        AnimatedLazyListItem(key = item.id, value = item) {
                            TimeTextItem(
                                item = item,
                                delete = {
                                    scope.launch { dao.removeItem(item) }
                                    workManager.cancelUniqueWork(item.id)
                                },
                                checked = {
                                    item.isActive = it
                                    scope.launch { println(dao.updateItem(item)) }
                                    if (it) {
                                        queueItem(context, item)
                                    } else {
                                        workManager.cancelUniqueWork(item.id)
                                    }
                                }
                            ) {
                                //TODO: Add in a "Are you sure you want to send a text" dialog
                                workManager.enqueue(
                                    OneTimeWorkRequestBuilder<TextWorker>()
                                        .setInputData(workDataOf("id" to item.id))
                                        .build()
                                )
                            }
                        }
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TimeTextItem(
    item: TextInfo,
    delete: () -> Unit = {},
    checked: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
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

        ElevatedCard(modifier = Modifier.clickable { onClick() }) {
            CustomListItem(
                text = { Text(text = "ID: ${item.id}") },
                secondaryText = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val context = LocalContext.current

                        val is24Hour = remember { DateFormat.is24HourFormat(context) }

                        val days = stringArrayResource(id = R.array.days)

                        when (item.timeInfo.type) {
                            TimeType.WEEKLY -> Text("On ${item.timeInfo.weekDay?.let { days[it] }}")
                            TimeType.MONTHLY -> Text("On ${LocalDate.parse(item.timeInfo.date).dayOfMonth}")
                            TimeType.YEARLY -> {
                                val day = LocalDate.parse(item.timeInfo.date)
                                    .let { "${it.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.dayOfMonth}" }
                                Text("On $day")
                            }
                        }
                        Text("At: ${item.timeInfo.time}${if (is24Hour) "" else item.timeInfo.amPm}")
                        Text("To ${item.numbers.size} number(s)")
                        androidx.compose.material3.Divider()
                        Text(item.text)
                    }
                },
                overlineText = { Text(item.type.name) },
                trailing = {
                    //TODO: Add an edit icon
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
//@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme(darkColorScheme()) {
        Scaffold {
            LazyColumn(contentPadding = it) {
                /*item {
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
                }*/
            }
        }
    }
}