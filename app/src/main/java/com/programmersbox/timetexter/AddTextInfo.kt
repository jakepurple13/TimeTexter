package com.programmersbox.timetexter

import android.R.attr.data
import android.database.Cursor
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.window.DialogProperties
import androidx.core.database.getStringOrNull
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.composethemeadapter.MdcTheme
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

data class ContactInfo(
    val name: String,
    val numbers: List<String>
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
fun AddNewItem(dao: ItemDao, workManager: WorkManager, navController: NavController) {

    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    var idError by remember { mutableStateOf(false) }

    var id by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var textMessage by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TimeType.DAILY) }

    val numberList = remember { mutableStateListOf<Pair<String, String>>() }

    var potentialNumbers by remember { mutableStateOf<ContactInfo>(ContactInfo("", emptyList())) }
    var chooseNumber by remember { mutableStateOf(false) }

    val contactIntent = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->

        if (uri != null) {
            try {

                val contactData: Uri = uri
                val phone: Cursor? = context.contentResolver.query(contactData, null, null, null, null)
                if (phone!!.moveToFirst()) {
                    val cIndex = phone.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val contactName: String = phone.getString(cIndex)

                    // To get number - runtime permission is mandatory.
                    val iIndex = phone.getColumnIndex(ContactsContract.Contacts._ID)
                    val idIndex: String = phone.getString(iIndex)
                    val nIndex = phone.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    val numbers = mutableListOf<String>()
                    if (phone.getString(nIndex).toInt() > 0) {
                        val phones = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + idIndex,
                            null,
                            null
                        )
                        while (phones!!.moveToNext()) {
                            val pIndex = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val phoneNumber = phones.getString(pIndex)
                            numbers.add(phoneNumber)
                        }
                        phones.close()
                    }


                    potentialNumbers = ContactInfo(
                        contactName,
                        numbers
                    )
                    if (numbers.size > 1) {
                        chooseNumber = true
                    } else if (numbers.size == 1) {
                        numberList.add(contactName to numbers.firstOrNull().orEmpty())
                    }
                }
                phone.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    if (chooseNumber) {

        var possibleNumber by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { chooseNumber = false },
            title = { Text("Choose a Number for ${potentialNumbers.name}") },
            text = {
                LazyColumn {
                    items(potentialNumbers.numbers) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = rememberRipple(),
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { possibleNumber = it }
                                .border(0.dp, Color.Transparent, RoundedCornerShape(20.dp))
                        ) {
                            RadioButton(
                                selected = it == possibleNumber,
                                onClick = { possibleNumber = it },
                                modifier = Modifier.padding(8.dp),
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if(possibleNumber.isNotEmpty()) {
                            numberList.add(potentialNumbers.name to possibleNumber)
                            chooseNumber = false
                        }
                    }
                ) { Text("Add Number") }
            },
            dismissButton = { TextButton(onClick = { chooseNumber = false }) { Text("Cancel") } }
        )

    }

    val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SmallTopAppBar(
                title = { Text(text = "Add a new Item") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            BottomAppBar {
                OutlinedButton(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .weight(1f),
                    onClick = { navController.popBackStack() }
                ) { Text("Cancel") }

                OutlinedButton(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .weight(1f),
                    onClick = {
                        if (id.isNotEmpty()) {
                            scope.launch {
                                dao.newItem(
                                    TextInfo(
                                        id = id,
                                        text = textMessage,
                                        type = type,
                                        time = 0L,
                                        true,
                                        numberList.fastMap { it.second }.toList()
                                    )
                                )
                            }

                            /*workManager.enqueueUniquePeriodicWork(
                                item.id,
                                ExistingPeriodicWorkPolicy.KEEP,
                                PeriodicWorkRequestBuilder()
                            )*/

                            navController.popBackStack()
                        } else {
                            idError = true
                        }
                    }
                ) { Text("Save") }
            }
        }
    ) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                MdcTheme {
                    OutlinedTextField(
                        value = id,
                        onValueChange = {
                            id = it
                            idError = false
                        },
                        isError = idError,
                        label = { androidx.compose.material.Text("Set Unique ID") },
                        trailingIcon = {
                            androidx.compose.material.IconButton(
                                onClick = { id = "" }
                            ) { androidx.compose.material.Icon(Icons.Default.Cancel, null) }
                        },
                        modifier = Modifier
                            .padding(4.dp)
                            .weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { id = UUID.randomUUID().toString() }
                ) { Text("Random ID") }

            }

            Divider()

            MdcTheme {
                OutlinedTextField(
                    value = textMessage,
                    onValueChange = { textMessage = it },
                    label = { androidx.compose.material.Text("Create Text to Send") },
                    trailingIcon = {
                        androidx.compose.material.IconButton(
                            onClick = { textMessage = "" }
                        ) { androidx.compose.material.Icon(Icons.Default.Cancel, null) }
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth(),
                )
            }

            Divider()

            PreferenceSetting(
                settingTitle = { Text("Contacts to Text") },
                summaryValue = {
                    Text(numberList.joinToString(", ") { "${it.first} = ${it.second}" })
                },
                modifier = Modifier.clickable {
                    contactIntent.launch()
                }
            )

            var customNumber by remember { mutableStateOf("") }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                MdcTheme {
                    OutlinedTextField(
                        value = customNumber,
                        onValueChange = { customNumber = it },
                        isError = idError,
                        label = { androidx.compose.material.Text("Add a custom number") },
                        trailingIcon = {
                            androidx.compose.material.IconButton(
                                onClick = { customNumber = "" }
                            ) { androidx.compose.material.Icon(Icons.Default.Cancel, null) }
                        },
                        modifier = Modifier
                            .padding(4.dp)
                            .weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }

                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = { numberList.add("Custom" to customNumber) }
                ) { Icon(Icons.Default.Add, null) }

            }

            Divider()

            ListSetting(
                settingTitle = { Text("Interval Type") },
                dialogTitle = { Text("Choose an interval type") },
                confirmText = {
                    TextButton(onClick = { it.value = false }) {
                        Text("OK")
                    }
                },
                summaryValue = { Text(type.name) },
                value = type,
                options = TimeType.values().toList(),
                updateValue = { it, b ->
                    type = it
                    b.value = false
                }
            )

            var timeLong by remember { mutableStateOf(0L) }

            when (type) {
                TimeType.DAILY -> {

                    PreferenceSetting(
                        settingTitle = { Text("Set the Daily Time") },
                        summaryValue = {
                            Text("At $timeLong")
                        },
                        modifier = Modifier.clickable {
                            val c = Calendar.getInstance()
                            val timePicker = MaterialTimePicker.Builder()
                                .setTitleText(R.string.selectTime)
                                .setPositiveButtonText("OK")
                                .setTimeFormat(
                                    if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
                                )
                                .setHour(c[Calendar.HOUR_OF_DAY])
                                .setMinute(c[Calendar.MINUTE])
                                .build()

                            timePicker.addOnPositiveButtonClickListener { _ ->
                                c.add(Calendar.DAY_OF_YEAR, 1)
                                c[Calendar.HOUR_OF_DAY] = timePicker.hour
                                c[Calendar.MINUTE] = timePicker.minute

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.willNotifyAt,
                                            SimpleDateFormat.getDateTimeInstance().format(c.timeInMillis)
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            //timePicker.show(fragmentManager, "timePicker")
                        }
                    )
                }
                TimeType.WEEKLY -> {

                }
                TimeType.MONTHLY -> {

                }
                TimeType.YEARLY -> {

                }
            }

            Divider()

        }
    }
}