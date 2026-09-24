package com.quizcounter.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import okhttp3.*
import org.json.JSONObject

private const val DEFAULT_RELAY = "wss://YOUR-RELAY-HOST/ws"
private const val RELAY_TOKEN = "CHANGE_ME"

class MainActivity : ComponentActivity() {
    private val vm = UserVm()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UserScreen(vm) }
    }
}

class UserVm {
    var relay by mutableStateOf(DEFAULT_RELAY)
    var room by mutableStateOf("DEMO01")
    var name by mutableStateOf("")
    var connected by mutableStateOf(false)
    var counter by mutableLongStateOf(0L)
    var running by mutableStateOf(false)
    var currentQuestion by mutableStateOf("Waiting for a question…")
    var answer by mutableStateOf("")
    var submitted by mutableStateOf(false)
    private var ws: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        ws = client.newWebSocket(Request.Builder().url(relay).build(), object: WebSocketListener() {
            override fun onOpen(w: WebSocket, response: Response) {
                connected = true
                w.send(JSONObject(mapOf("type" to "join", "token" to RELAY_TOKEN, "room" to room, "role" to "user")).toString())
                w.send(JSONObject(mapOf("type" to "user_joined", "user" to name)).toString())
            }
            override fun onClosed(w: WebSocket, code: Int, reason: String) { connected=false }
            override fun onFailure(w: WebSocket, t: Throwable, response: Response?) { connected=false }
            override fun onMessage(w: WebSocket, text: String) {
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return
                when(o.optString("type")) {
                    "counter_state" -> { running=o.optBoolean("running"); counter=o.optLong("value") }
                    "counter_tick" -> { counter=o.optLong("value"); running=true }
                    "question" -> { currentQuestion=o.optString("text"); answer=""; submitted=false }
                }
            }
        })
    }

    fun submit() {
        if(answer.isBlank()) return
        ws?.send(JSONObject(mapOf("type" to "answer", "user" to name, "answer" to answer)).toString())
        submitted=true
    }
}

@Composable
fun UserScreen(vm: UserVm) {
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF22C55E))) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("QuizCounter", style=MaterialTheme.typography.headlineMedium)
                Text(if(vm.connected) "● Connected" else "○ Not connected")
                if (!vm.connected) {
                    OutlinedTextField(vm.name,{vm.name=it},label={Text("Your name")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(vm.room,{vm.room=it.uppercase()},label={Text("Room code")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(vm.relay,{vm.relay=it},label={Text("Relay URL")},modifier=Modifier.fillMaxWidth())
                    Button({if(vm.name.isNotBlank()) vm.connect()},modifier=Modifier.fillMaxWidth()){Text("JOIN")}
                } else {
                    Text(if(vm.running) "LIVE" else "PAUSED", color=if(vm.running) Color(0xFF22C55E) else Color.Gray)
                    Text(vm.counter.toString(), style=MaterialTheme.typography.displayLarge)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(vm.currentQuestion, style=MaterialTheme.typography.titleLarge)
                            OutlinedTextField(vm.answer,{vm.answer=it},label={Text("Your answer")},modifier=Modifier.fillMaxWidth(),enabled=!vm.submitted)
                            Button({vm.submit()},enabled=!vm.submitted,modifier=Modifier.fillMaxWidth()){Text(if(vm.submitted) "ANSWER SENT" else "SUBMIT ANSWER")}
                        }
                    }
                }
            }
        }
    }
}