package com.example.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Ma'lumotlar ----------

data class CallItem(
    val name: String,
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long
)

data class Contact(val name: String, val number: String)

fun digitsOf(s: String) = s.filter { it.isDigit() }

fun loadCalls(ctx: Context): List<CallItem> {
    val list = mutableListOf<CallItem>()
    try {
        val cursor = ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null, null, "${CallLog.Calls.DATE} DESC"
        ) ?: return list
        cursor.use {
            while (it.moveToNext() && list.size < 300) {
                list.add(
                    CallItem(
                        name = it.getString(1) ?: "",
                        number = it.getString(0) ?: "",
                        type = it.getInt(2),
                        date = it.getLong(3),
                        duration = it.getLong(4)
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun loadContacts(ctx: Context): List<Contact> {
    val list = mutableListOf<Contact>()
    try {
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        ) ?: return list
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                list.add(Contact(name, number))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.distinctBy { it.name + digitsOf(it.number) }
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) { DialerApp() }
            }
        }
    }
}

// ---------- Asosiy ilova ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerApp() {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var number by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var calls by remember { mutableStateOf(listOf<CallItem>()) }
    var contacts by remember { mutableStateOf(listOf<Contact>()) }
    var pendingCall by remember { mutableStateOf<String?>(null) }

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun reload() {
        if (granted(Manifest.permission.READ_CALL_LOG)) calls = loadCalls(ctx)
        if (granted(Manifest.permission.READ_CONTACTS)) contacts = loadContacts(ctx)
    }

    fun startCall(n: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(n)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        reload()
        val p = pendingCall
        if (p != null && granted(Manifest.permission.CALL_PHONE)) startCall(p)
        pendingCall = null
    }

    LaunchedEffect(Unit) {
        val need = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        ).filter { !granted(it) }
        if (need.isNotEmpty()) launcher.launch(need.toTypedArray()) else reload()
    }

    LaunchedEffect(tab) { reload() }

    fun call(n: String) {
        if (n.isBlank()) return
        if (granted(Manifest.permission.CALL_PHONE)) startCall(n)
        else {
            pendingCall = n
            launcher.launch(arrayOf(Manifest.permission.CALL_PHONE))
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Phone, null) }, label = { Text("Klaviatura") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Tarix") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("Kontaktlar") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> {
                    val typed = digitsOf(number)
                    val match = if (typed.length >= 3)
                        contacts.firstOrNull { digitsOf(it.number).contains(typed) }?.name
                    else null
                    KeypadScreen(
                        number = number,
                        match = match,
                        onAppend = { number += it },
                        onBackspace = { if (number.isNotEmpty()) number = number.dropLast(1) },
                        onClear = { number = "" },
                        onCall = { call(number) }
                    )
                }

                1 -> {
                    val shown = calls.filter {
                        query.isBlank() ||
                            it.name.contains(query, true) ||
                            digitsOf(it.number).contains(digitsOf(query).ifEmpty { "\u0000" }) ||
                            it.number.contains(query)
                    }
                    Column(Modifier.fillMaxSize()) {
                        SearchField(query) { query = it }
                        if (shown.isEmpty()) EmptyText("Qo'ng'iroqlar topilmadi")
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(shown) { c ->
                                CallRow(
                                    c,
                                    onTap = { number = c.number; tab = 0 },
                                    onDoubleTap = { call(c.number) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    val shown = contacts.filter {
                        query.isBlank() ||
                            it.name.contains(query, true) ||
                            it.number.contains(query) ||
                            digitsOf(it.number).contains(digitsOf(query).ifEmpty { "\u0000" })
                    }
                    Column(Modifier.fillMaxSize()) {
                        SearchField(query) { query = it }
                        if (shown.isEmpty()) EmptyText("Kontaktlar topilmadi")
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(shown) { c ->
                                ContactRow(
                                    c,
                                    onTap = { number = c.number; tab = 0 },
                                    onDoubleTap = { call(c.number) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Klaviatura ----------

@Composable
fun KeypadScreen(
    number: String,
    match: String?,
    onAppend: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (number.isEmpty()) "Raqam kiriting" else number,
                fontSize = if (number.length > 12) 26.sp else 36.sp,
                fontWeight = FontWeight.Medium,
                color = if (number.isEmpty()) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = match ?: " ",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Raqam yozish va qo'ng'iroq uchun tugmani ikki marta bosing",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }

        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { (d, sub) ->
                    DialKey(d, sub) { onAppend(d) }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(78.dp))
            CallButton(onCall)
            BackspaceKey(onBackspace, onClear)
        }
    }
}

@Composable
fun DialKey(label: String, sub: String, onDouble: () -> Unit) {
    val action by rememberUpdatedState(onDouble)
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { action() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotEmpty()) Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun CallButton(onCall: () -> Unit) {
    val action by rememberUpdatedState(onCall)
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color(0xFF2E9E47))
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { action() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Call, "Qo'ng'iroq", tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun BackspaceKey(onBackspace: () -> Unit, onClear: () -> Unit) {
    val back by rememberUpdatedState(onBackspace)
    val clear by rememberUpdatedState(onClear)
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { back() }, onLongPress = { clear() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text("⌫", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Ro'yxatlar ----------

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        placeholder = { Text("Ism yoki raqam bo'yicha qidirish") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Tozalash") }
            }
        },
        singleLine = true,
        shape = CircleShape
    )
}

@Composable
fun EmptyText(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
fun CallRow(c: CallItem, onTap: () -> Unit, onDoubleTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val dbl by rememberUpdatedState(onDoubleTap)
    val (label, color) = when (c.type) {
        CallLog.Calls.INCOMING_TYPE -> "Kiruvchi" to Color(0xFF1E88E5)
        CallLog.Calls.OUTGOING_TYPE -> "Chiquvchi" to Color(0xFF2E9E47)
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> "Qabul qilinmagan" to Color(0xFFE53935)
        else -> "Boshqa" to Color.Gray
    }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { tap() }, onDoubleTap = { dbl() })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (c.name.isNotBlank()) c.name else c.number.ifBlank { "Noma'lum" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (c.type == CallLog.Calls.MISSED_TYPE) color
                else MaterialTheme.colorScheme.onSurface
            )
            Text(label, fontSize = 13.sp, color = color)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt.format(Date(c.date)), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            if (c.duration > 0)
                Text("${c.duration / 60}:%02d".format(c.duration % 60), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
    Line()
}

@Composable
fun ContactRow(c: Contact, onTap: () -> Unit, onDoubleTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val dbl by rememberUpdatedState(onDoubleTap)
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { tap() }, onDoubleTap = { dbl() })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(c.name.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(c.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(c.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
    Line()
}

@Composable
fun Line() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
