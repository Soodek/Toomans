package com.example.tomanrates

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val AI_PREFS = "toman_ai"
private fun aiPrefs(ctx: Context) = ctx.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)

data class ChatLine(val role: String, val text: String)

object AssistantRepository {
    fun endpoint(ctx: Context) = aiPrefs(ctx).getString("worker_url", "") ?: ""
    fun saveEndpoint(ctx: Context, url: String) = aiPrefs(ctx).edit().putString("worker_url", url.trim()).apply()

    fun ask(ctx: Context, prompt: String, snap: Snapshot): String {
        val endpoint = endpoint(ctx)
        require(endpoint.startsWith("https://")) { "ابتدا آدرس HTTPS مربوط به Cloudflare Worker را وارد کن." }
        val market = JSONObject()
        snap.rates.values.forEach { r -> market.put(r.slug, JSONObject().put("name", r.name).put("sell_toman", r.sell.toPlainString()).put("buy_toman", r.buy?.toPlainString())) }
        val body = JSONObject()
            .put("message", prompt)
            .put("market", market)
            .put("market_updated_at", snap.lastSuccessAt)
            .put("market_status", snap.status.name)
        var c: HttpURLConnection? = null
        try {
            c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("خطای سرور ($code): " + JSONObject(raw).optString("error", "درخواست ناموفق بود"))
            val json = JSONObject(raw)
            return json.optString("reply").ifBlank { throw IllegalStateException("پاسخ سرور خالی بود.") }
        } finally { c?.disconnect() }
    }
}

@Composable
fun AiAssistantTab(snap: Snapshot) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf(AssistantRepository.endpoint(ctx)) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatLine>() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("دستیار هوشمند بازار", fontSize = 23.sp)
        Text("پاسخ فارسی درباره خود برنامه و تحلیل آموزشی بازار ایران؛ نه توصیه قطعی سرمایه‌گذاری.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("آدرس Cloudflare Worker (HTTPS)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { AssistantRepository.saveEndpoint(ctx, endpoint); error = "آدرس ذخیره شد." }, enabled = endpoint.startsWith("https://")) { Text("ذخیره آدرس سرور") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { line ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (line.role == "user") "شما" else "دستیار", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        Text(line.text)
                    }
                }
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), label = { Text("سؤالت را بنویس…") }, enabled = !busy)
            Button(enabled = input.isNotBlank() && !busy, onClick = {
                val q = input.trim(); input = ""; messages.add(ChatLine("user", q)); busy = true; error = null
                scope.launch {
                    try {
                        val answer = withContext(Dispatchers.IO) { AssistantRepository.ask(ctx, q, snap) }
                        messages.add(ChatLine("assistant", answer))
                    } catch (e: Exception) { error = e.message ?: "ارتباط با دستیار ناموفق بود." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "…" else "ارسال") }
        }
    }
}
