package com.myraa.app
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.*
import java.net.*
import java.util.Locale
import java.util.concurrent.*
import kotlin.concurrent.thread
class MainActivity : Activity() {
companion object {
const val DEFAULT_MODEL = "claude-sonnet-5-5"
}
private val cBg = Color.parseColor("#0E0B1A")
private val cSf = Color.parseColor("#1A1530")
private val cTx = Color.parseColor("#EDE9FF")
private val cMu = Color.parseColor("#9D93C9")
private val cAc = Color.parseColor("#FF5FA2")
private val cDark = Color.parseColor("#1A0B14")
private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private lateinit var prefs: SharedPreferences
private lateinit var container: LinearLayout
private lateinit var scroll: ScrollView
private lateinit var input: EditText
private lateinit var micBtn: TextView
private lateinit var sendBtn: TextView
private val history = JSONArray()
private var busy = false
private var listening = false
private var recognizer: SpeechRecognizer? = null
private var tts: TextToSpeech? = null
private var ttsReady = false
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
prefs = getSharedPreferences("myraa", MODE_PRIVATE)
window.statusBarColor = cBg
window.navigationBarColor = cBg
setContentView(buildUi())
tts = TextToSpeech(this) { status -> ttsReady = (status == TextToSpeech.SUCCESS) }
greet()
}
override fun onDestroy() {
recognizer?.destroy()
tts?.shutdown()
super.onDestroy()
}
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
super.onRequestPermissionsResult(requestCode, permissions, grantResults)
if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
toggleMic()
}
}
private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
private fun toast(msg: String) {
Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
private fun buildUi(): View {
val root = LinearLayout(this)
root.orientation = LinearLayout.VERTICAL
root.setBackgroundColor(cBg)
root.fitsSystemWindows = true
val bar = LinearLayout(this)
bar.orientation = LinearLayout.HORIZONTAL
bar.gravity = Gravity.CENTER_VERTICAL
bar.setPadding(dp(16), dp(4), dp(4), dp(4))
val title = TextView(this)
title.text = "Myraa"
title.textSize = 20f
title.setTextColor(cTx)
title.typeface = Typeface.DEFAULT_BOLD
bar.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
val menu = TextView(this)
menu.text = "\u22EE"
menu.textSize = 26f
menu.gravity = Gravity.CENTER
menu.setTextColor(cTx)
menu.setOnClickListener { showMenu() }
bar.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
scroll = ScrollView(this)
container = LinearLayout(this)
container.orientation = LinearLayout.VERTICAL
container.setPadding(dp(12), dp(8), dp(12), dp(8))
scroll.addView(container, ViewGroup.LayoutParams(MATCH, WRAP))
root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
val row = LinearLayout(this)
row.orientation = LinearLayout.HORIZONTAL
row.gravity = Gravity.BOTTOM
row.setPadding(dp(12), dp(6), dp(12), dp(10))
input = EditText(this)
input.hint = "Message Myraa"
input.setHintTextColor(cMu)
input.setTextColor(cTx)
input.textSize = 16f
input.maxLines = 4
input.setHorizontallyScrolling(false)
input.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
input.imeOptions = EditorInfo.IME_ACTION_SEND
input.setPadding(dp(16), dp(10), dp(16), dp(10))
val inBg = GradientDrawable()
inBg.cornerRadius = dp(22).toFloat()
inBg.setColor(cSf)
input.background = inBg
input.setOnEditorActionListener { _, actionId, _ ->
if (actionId == EditorInfo.IME_ACTION_SEND) {
submit()
true
} else {
false
}
}
row.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
micBtn = roundBtn("\uD83C\uDFA4", cSf) { toggleMic() }
val mlp = LinearLayout.LayoutParams(dp(46), dp(46))
mlp.leftMargin = dp(8)
row.addView(micBtn, mlp)
sendBtn = roundBtn("\u27A4", cAc) { submit() }
sendBtn.setTextColor(cDark)
val slp = LinearLayout.LayoutParams(dp(46), dp(46))
slp.leftMargin = dp(8)
row.addView(sendBtn, slp)
root.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
return root
}
private fun roundBtn(label: String, color: Int, onClick: () -> Unit): TextView {
val t = TextView(this)
t.text = label
t.textSize = 20f
t.gravity = Gravity.CENTER
t.setTextColor(cTx)
val bg = GradientDrawable()
bg.shape = GradientDrawable.OVAL
bg.setColor(color)
t.background = bg
t.setOnClickListener { onClick() }
return t
}
private fun addBubble(text: String, user: Boolean): TextView {
val t = TextView(this)
t.text = text
t.textSize = 16f
t.setTextColor(if (user) cDark else cTx)
t.setPadding(dp(14), dp(10), dp(14), dp(10))
val bg = GradientDrawable()
bg.cornerRadius = dp(18).toFloat()
bg.setColor(if (user) cAc else cSf)
t.background = bg
t.maxWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt()
val lp = LinearLayout.LayoutParams(WRAP, WRAP)
lp.gravity = if (user) Gravity.END else Gravity.START
lp.topMargin = dp(5)
lp.bottomMargin = dp(5)
container.addView(t, lp)
toBottom()
return t
}
private fun toBottom() {
scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
}
private fun greet() {
addBubble("Hi, I'm Myraa. Add your API key from the \u22EE menu, then type or tap the mic.", false)
}
private fun setBusy(b: Boolean) {
busy = b
sendBtn.alpha = if (b) 0.4f else 1f
}
private fun submit() {
val v = input.text.toString().trim()
if (v.isEmpty()) return
input.setText("")
send(v)
}
private fun showMenu() {
val speak = prefs.getBoolean("speak", false)
val lang = prefs.getString("lang", "hi-IN")
val items = arrayOf(
"API key",
"Memory",
"New chat",
"Voice language: " + (if (lang == "hi-IN") "Hindi" else "English"),
"Read replies aloud: " + (if (speak) "on" else "off")
)
AlertDialog.Builder(this).setItems(items) { _, which ->
when (which) {
0 -> showKeyDialog()
1 -> showMemory()
2 -> newChat()
3 -> prefs.edit().putString("lang", if (lang == "hi-IN") "en-IN" else "hi-IN").apply()
4 -> {
prefs.edit().putBoolean("speak", !speak).apply()
if (speak) tts?.stop()
}
else -> {}
}
}.show()
}
private fun showKeyDialog() {
val et = EditText(this)
et.hint = "sk-ant-..."
et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
et.setText(prefs.getString("key", ""))
AlertDialog.Builder(this)
.setTitle("Anthropic API key")
.setView(et)
.setPositiveButton("Save") { _, _ ->
prefs.edit().putString("key", et.text.toString().trim()).apply()
toast("Saved")
}
.setNegativeButton("Cancel", null)
.show()
}
private fun showMemory() {
val m = getMem()
val text = if (m.length() == 0) "Nothing saved yet." else
(0 until m.length()).joinToString("\n") { "\u2022 " + m.getString(it) }
AlertDialog.Builder(this)
.setTitle("Memory")
.setMessage(text)
.setPositiveButton("Close", null)
.setNegativeButton("Clear all") { _, _ -> prefs.edit().putString("mem", "[]").apply() }
.show()
}
private fun newChat() {
if (busy) {
toast("Wait for the reply to finish")
return
}
while (history.length() > 0) history.remove(0)
container.removeAllViews()
greet()
}
private fun getMem(): JSONArray {
return try {
JSONArray(prefs.getString("mem", "[]"))
} catch (e: Exception) {
JSONArray()
}
}
private fun addMem(fact: String) {
val arr = getMem()
if (arr.length() >= 60) arr.remove(0)
arr.put(fact)
prefs.edit().putString("mem", arr.toString()).apply()
}
private fun say(text: String) {
if (!ttsReady) return
tts?.setLanguage(Locale.forLanguageTag(prefs.getString("lang", "hi-IN") ?: "hi-IN"))
tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "myraa")
}
private fun setListening(on: Boolean) {
listening = on
val bg = GradientDrawable()
bg.shape = GradientDrawable.OVAL
bg.setColor(if (on) Color.parseColor("#FF7A7A") else cSf)
micBtn.background = bg
}
private fun toggleMic() {
if (listening) {
recognizer?.stopListening()
return
}
if (busy) return
if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
return
}
if (!SpeechRecognizer.isRecognitionAvailable(this)) {
toast("Speech recognition is not available on this phone")
return
}
tts?.stop()
if (recognizer == null) {
val r = SpeechRecognizer.createSpeechRecognizer(this)
r.setRecognitionListener(object : RecognitionListener {
override fun onReadyForSpeech(params: Bundle?) { setListening(true) }
override fun onBeginningOfSpeech() {}
override fun onRmsChanged(rmsdB: Float) {}
override fun onBufferReceived(buffer: ByteArray?) {}
override fun onEndOfSpeech() {}
override fun onEvent(eventType: Int, params: Bundle?) {}
override fun onError(error: Int) {
setListening(false)
toast(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
"Did not hear anything" else "Mic error " + error)
}
override fun onPartialResults(partialResults: Bundle?) {
val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
if (!p.isNullOrBlank()) input.setText(p)
}
override fun onResults(results: Bundle?) {
setListening(false)
val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
if (!t.isNullOrBlank()) {
input.setText("")
send(t)
}
}
})
recognizer = r
}
val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.getString("lang", "hi-IN"))
i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
recognizer?.startListening(i)
}
private fun send(text: String) {
if (busy) return
val key = prefs.getString("key", "") ?: ""
if (key.isBlank()) {
toast("Add your API key first")
showKeyDialog()
return
}
addBubble(text, true)
val startLen = history.length()
history.put(JSONObject().put("role", "user").put("content", text))
val pending = addBubble("\u2026", false)
setBusy(true)
thread {
try {
var finalText = ""
var rounds = 0
while (rounds < 5) {
rounds++
val resp = callApi(key)
val content = resp.getJSONArray("content")
val clean = JSONArray()
val results = JSONArray()
val sb = StringBuilder()
for (i in 0 until content.length()) {
val blk = content.getJSONObject(i)
val type = blk.getString("type")
if (type == "text") {
val tx = blk.getString("text")
if (tx.isNotBlank()) {
clean.put(JSONObject().put("type", "text").put("text", tx))
sb.append(tx)
}
} else if (type == "tool_use") {
clean.put(
JSONObject().put("type", "tool_use").put("id", blk.getString("id"))
.put("name", blk.getString("name")).put("input", blk.getJSONObject("input"))
)
val out = onUi { runTool(blk.getString("name"), blk.getJSONObject("input")) }
results.put(
JSONObject().put("type", "tool_result").put("tool_use_id", blk.getString("id"))
.put("content", out)
)
}
}
if (clean.length() > 0) history.put(JSONObject().put("role", "assistant").put("content", clean))
if (sb.isNotEmpty()) finalText = sb.toString().trim()
if (results.length() == 0) break
history.put(JSONObject().put("role", "user").put("content", results))
val shown = finalText
runOnUiThread { if (shown.isNotEmpty()) pending.text = shown }
}
val reply = if (finalText.isEmpty()) "Done." else finalText
runOnUiThread {
pending.text = reply
setBusy(false)
toBottom()
if (prefs.getBoolean("speak", false)) say(reply)
}
} catch (e: Exception) {
while (history.length() > startLen) history.remove(history.length() - 1)
val msg = "Could not get a reply: " + (e.message ?: "unknown error")
runOnUiThread {
pending.text = msg
setBusy(false)
}
}
}
}
private fun onUi(block: () -> String): String {
var out = ""
val latch = CountDownLatch(1)
runOnUiThread {
out = try {
block()
} catch (e: Exception) {
"Error: " + e.message
}
latch.countDown()
}
latch.await(10, TimeUnit.SECONDS)
return out
}
private fun systemPrompt(): String {
val m = getMem()
val mem = if (m.length() == 0) "nothing yet" else (0 until m.length()).joinToString("; ") { m.getString(it) }
return "You are Myraa, a warm, natural AI companion living on the user's Android phone. " +
"Reply in the language and script the user writes in (Hinglish stays Hinglish). " +
"Keep replies short and conversational, with no markdown, because they are read on a phone and may be spoken aloud. " +
"Use tools only when the user asks for a phone action, and describe honestly what happened based on the tool result. " +
"Phone numbers for WhatsApp need the country code. " +
"When the user tells you something lasting about themselves, call remember. " +
"What you know about the user: " + mem + "."
}
private fun trimHistory() {
while (history.length() > 30) history.remove(0)
while (history.length() > 0) {
val m = history.getJSONObject(0)
if (m.getString("role") == "user" && m.get("content") is String) break
history.remove(0)
}
}
private fun callApi(key: String): JSONObject {
trimHistory()
val body = JSONObject()
.put("model", prefs.getString("model", DEFAULT_MODEL))
.put("max_tokens", 1024)
.put("system", systemPrompt())
.put("tools", tools())
.put("messages", history)
val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
conn.requestMethod = "POST"
conn.connectTimeout = 20000
conn.readTimeout = 90000
conn.doOutput = true
conn.setRequestProperty("content-type", "application/json")
conn.setRequestProperty("x-api-key", key)
conn.setRequestProperty("anthropic-version", "2023-06-01")
conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
val code = conn.responseCode
val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
val text = stream.bufferedReader().use { it.readText() }
if (code !in 200..299) {
val msg = try {
JSONObject(text).getJSONObject("error").getString("message")
} catch (e: Exception) {
"HTTP " + code
}
throw Exception(msg)
}
return JSONObject(text)
}
private fun sProp(d: String) = JSONObject().put("type", "string").put("description", d)
private fun iProp(d: String) = JSONObject().put("type", "integer").put("description", d)
private fun bProp(d: String) = JSONObject().put("type", "boolean").put("description", d)
private fun tool(name: String, desc: String, props: JSONObject, req: List<String>): JSONObject {
val schema = JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(req))
return JSONObject().put("name", name).put("description", desc).put("input_schema", schema)
}
private fun tools(): JSONArray {
val t = JSONArray()
t.put(tool("open_app", "Open an app.",
JSONObject().put("name", sProp("App name")), listOf("name")))
t.put(tool("find_contact", "Find a contact number.",
JSONObject().put("name", sProp("Name")), listOf("name")))
t.put(tool("call", "Open dialer.",
JSONObject().put("number", sProp("Number")), listOf("number")))
t.put(tool("send_sms", "Open SMS draft.",
JSONObject().put("number", sProp("Number")).put("text", sProp("Text")), listOf("number", "text")))
t.put(tool("whatsapp", "Open WhatsApp draft.",
JSONObject().put("number", sProp("Number with country code")).put("text", sProp("Text")), listOf("number")))
t.put(tool("youtube_search", "Search YouTube.",
JSONObject().put("query", sProp("Text")), listOf("query")))
t.put(tool("web_search", "Search Google.",
JSONObject().put("query", sProp("Text")), listOf("query")))
t.put(tool("open_url", "Open a web link.",
JSONObject().put("url", sProp("URL")), listOf("url")))
t.put(tool("maps", "Search in Maps.",
JSONObject().put("query", sProp("Place")), listOf("query")))
t.put(tool("set_alarm", "Set an alarm (24h).",
JSONObject().put("hour", iProp("Hour 0-23")).put("minute", iProp("Minute 0-59")).put("label", sProp("Label")),
listOf("hour", "minute")))
t.put(tool("set_timer", "Start a timer.",
JSONObject().put("seconds", iProp("Seconds")).put("label", sProp("Label")), listOf("seconds")))
t.put(tool("flashlight", "Flashlight on or off.",
JSONObject().put("on", bProp("On")), listOf("on")))
t.put(tool("set_volume", "Set media volume.",
JSONObject().put("percent", iProp("Percent")), listOf("percent")))
t.put(tool("battery", "Battery level.", JSONObject(), listOf()))
t.put(tool("open_settings", "Open a settings screen.",
JSONObject().put("page", JSONObject().put("type", "string").put("description", "Page")
.put("enum", JSONArray(listOf("wifi", "bluetooth", "display", "sound", "location", "airplane", "main")))),
listOf("page")))
t.put(tool("remember", "Remember a lasting fact about the user.",
JSONObject().put("fact", sProp("Fact")), listOf("fact")))
return t
}
private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
private fun go(i: Intent, ok: String): String {
return try {
startActivity(i)
ok
} catch (e: Exception) {
"No app on this phone could do that"
}
}
private fun runTool(name: String, a: JSONObject): String {
return try {
when (name) {
"open_app" -> openApp(a.optString("name"))
"find_contact" -> findContact(a.optString("name"))
"call" -> go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(a.optString("number")))), "Dialer opened")
"send_sms" -> go(
Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(a.optString("number"))))
.putExtra("sms_body", a.optString("text")),
"SMS draft opened"
)
"whatsapp" -> {
val digits = a.optString("number").filter { it.isDigit() }
go(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits + "?text=" + enc(a.optString("text")))),
"WhatsApp draft opened")
}
"youtube_search" -> go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(a.optString("query")))), "YouTube search opened")
"web_search" -> go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + enc(a.optString("query")))), "Search opened")
"open_url" -> {
val u = a.optString("url")
if (u.startsWith("http://") || u.startsWith("https://")) go(Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Link opened")
else "Only http and https links are allowed"
}
"maps" -> go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(a.optString("query")))), "Maps opened")
"set_alarm" -> {
val h = a.optInt("hour", 7)
val m = a.optInt("minute", 0)
go(Intent(AlarmClock.ACTION_SET_ALARM)
.putExtra(AlarmClock.EXTRA_HOUR, h)
.putExtra(AlarmClock.EXTRA_MINUTES, m)
.putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Myraa"))
.putExtra(AlarmClock.EXTRA_SKIP_UI, true),
"Alarm set for " + h + ":" + (if (m < 10) "0" else "") + m)
}
"set_timer" -> {
val s = a.optInt("seconds", 60)
go(Intent(AlarmClock.ACTION_SET_TIMER)
.putExtra(AlarmClock.EXTRA_LENGTH, s)
.putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Myraa"))
.putExtra(AlarmClock.EXTRA_SKIP_UI, true),
"Timer started for " + s + " seconds")
}
"flashlight" -> torch(a.optBoolean("on", true))
"set_volume" -> setVolume(a.optInt("percent", 50))
"battery" -> battery()
"open_settings" -> {
val act = when (a.optString("page")) {
"wifi" -> Settings.ACTION_WIFI_SETTINGS
"bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
"display" -> Settings.ACTION_DISPLAY_SETTINGS
"sound" -> Settings.ACTION_SOUND_SETTINGS
"location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
"airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
else -> Settings.ACTION_SETTINGS
}
go(Intent(act), "Settings opened")
}
"remember" -> {
addMem(a.optString("fact"))
"Saved"
}
else -> "Unknown tool"
}
} catch (e: Exception) {
"Error: " + e.message
}
}
private fun openApp(name: String): String {
val q = name.lowercase(Locale.ROOT).trim()
val pm = packageManager
val main = Intent(Inte
