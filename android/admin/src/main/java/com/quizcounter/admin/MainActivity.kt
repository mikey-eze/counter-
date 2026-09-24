package com.quizcounter.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

private const val DEFAULT_RELAY = "wss://YOUR-RELAY-HOST/ws"
private const val RELAY_TOKEN = "CHANGE_ME"

class MainActivity : ComponentActivity() {
    private val vm = AdminVm()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdminScreen(vm) }
    }
}

class AdminVm {
    var relay by mutableStateOf(DEFAULT_RELAY)
    var room by mutableStateOf("DEMO01")
    var connected by mutableStateOf(false)
    var running by mutableStateOf(false)
    var counter by mutableLongStateOf(0L)
    var question by mutableStateOf("")
    var answers = mutableStateListOf<String>()
    var users = mutableStateListOf<String>()
    private var ws: WebSocket? = null
    private var ticker: Job? = null
    private val client = OkHttpClient()

    fun connect() {
        ws?.close(1000, "reconnect")
        ws = client.newWebSocket(Request.Builder().url(relay).build(), object: WebSocketListener() {
            override fun onOpen(w: WebSocket, response: Response) {
                connected = true
                w.send(JSONObject(mapOf("type" to "join", "token" to RELAY_TOKEN, "room" to room, "role" to "admin")).toString())
            }
            override fun onClosed(w: WebSocket, code: Int, reason: String) { connected = false }
            override fun onFailure(w: WebSocket, t: Throwable, response: Response?) { connected = false }
            override fun onMessage(w: WebSocket, text: String) {
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (o.optString("type")) {
                    "answer" -> answers.add("${o.optString("user")}: ${o.optString("answer")}")
                    "user_joined" -> if (!users.contains(o.optString("user"))) users.add(o.optString("user"))
                }
            }
        })
    }

    fun sendQuestion() {
        if (question.isBlank()) return
        ws?.send(JSONObject(mapOf("type" to "question", "text" to question)).toString())
    }

    fun startStop() {
        running = !running
        ws?.send(JSONObject(mapOf("type" to "counter_state", "running" to running, "value" to counter)).toString())
        ticker?.cancel()
        if (running) ticker = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && running) {
                delay(1000)
                counter++
                ws?.send(JSONObject(mapOf("type" to "counter_tick", "value" to counter)).toString())
            }
        }
    }
}

@Composable
fun AdminScreen(vm: AdminVm) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF22C55E))) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("QuizCounter • ADMIN", style = MaterialTheme.typography.headlineSmall)
                Text(if (vm.connected) "● Connected" else "○ Disconnected")
                OutlinedTextField(vm.relay, { vm.relay = it }, label={Text("Relay WebSocket URL")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(vm.room, { vm.room = it.uppercase() }, label={Text("Room code")}, modifier=Modifier.fillMaxWidth())
                Button({ vm.connect() }, modifier=Modifier.fillMaxWidth()) { Text("CONNECT") }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(vm.counter.toString(), style=MaterialTheme.typography.displayLarge)
                        Button({ vm.startStop() }, modifier=Modifier.fillMaxWidth()) {
                            Text(if(vm.running) "STOP COUNTER" else "START COUNTER")
                        }
                    }
                }
                OutlinedTextField(vm.question, { vm.question = it }, label={Text("Question to send")}, modifier=Modifier.fillMaxWidth())
                Button({ vm.sendQuestion() }, modifier=Modifier.fillMaxWidth()) { Text("SEND QUESTION") }
                Text("Users: ${vm.users.size}")
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) { items(vm.answers) { Text(it, Modifier.padding(6.dp)) } }
            }
        }
    }
}