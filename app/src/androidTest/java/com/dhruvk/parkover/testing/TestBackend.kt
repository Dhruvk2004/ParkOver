package com.dhruvk.parkover.testing

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Direct access to the local Firebase emulator for tests: wipe data, sign in throwaway users,
 * and seed documents as an admin (bypassing rules, e.g. "another driver's" booking).
 */
object TestBackend {
    const val HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    @Volatile var usingEmulator = false

    const val DRIVER_A = "driver-a@parkover.test"
    const val DRIVER_B = "driver-b@parkover.test"
    const val PASSWORD = "emulator-only-pw" // emulator accounts only; never a real login

    private val projectId get() = FirebaseApp.getInstance().options.projectId!!
    private val docsBase get() = "http://$HOST:$FIRESTORE_PORT/v1/projects/$projectId/databases/(default)/documents"

    /** Deletes every document and every emulator account. */
    fun reset() {
        check(usingEmulator) { "Refusing to wipe: not using the emulator" }
        http("DELETE", "http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$projectId/databases/(default)/documents")
        http("DELETE", "http://$HOST:$AUTH_PORT/emulator/v1/projects/$projectId/accounts")
        FirebaseAuth.getInstance().signOut()
    }

    /** Signs in (creating the account the first time). Returns the uid. */
    fun signIn(email: String = DRIVER_A): String {
        check(usingEmulator) { "Test users exist only on the emulator" }
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.email == email) return auth.currentUser!!.uid
        auth.signOut()
        val result = runCatching { Tasks.await(auth.signInWithEmailAndPassword(email, PASSWORD), 20, TimeUnit.SECONDS) }
            .getOrElse { Tasks.await(auth.createUserWithEmailAndPassword(email, PASSWORD), 20, TimeUnit.SECONDS) }
        return result.user!!.uid
    }

    /** Writes a document as an admin, ignoring security rules. */
    fun seed(path: String, fields: Map<String, Any?>) {
        val name = "projects/$projectId/databases/(default)/documents/$path"
        val write = JSONObject().put("update", JSONObject().put("name", name).put("fields", encodeMap(fields)))
        http("POST", "$docsBase:commit", JSONObject().put("writes", JSONArray().put(write)).toString())
    }

    /** Reads a collection as an admin. Returns each document's decoded fields plus "_id". */
    fun list(collection: String): List<Map<String, Any?>> {
        val json = JSONObject(http("GET", "$docsBase/$collection?pageSize=300").ifBlank { "{}" })
        val docs = json.optJSONArray("documents") ?: return emptyList()
        return List(docs.length()) { i ->
            val d = docs.getJSONObject(i)
            decodeMap(d.optJSONObject("fields") ?: JSONObject()) + ("_id" to d.getString("name").substringAfterLast('/'))
        }
    }

    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    // ---- REST helpers ----

    private fun http(method: String, url: String, body: String? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer owner") // emulator admin: bypasses rules
            connectTimeout = 5_000
            readTimeout = 10_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "$method $url → HTTP $code: ${text.take(300)}" }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun encodeMap(map: Map<String, Any?>) = JSONObject().apply { map.forEach { (k, v) -> put(k, encode(v)) } }

    private fun encode(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long -> JSONObject().put("integerValue", v.toString())
        is Number -> JSONObject().put("doubleValue", v.toDouble())
        is Timestamp -> JSONObject().put("timestampValue", iso.format(v.toDate()))
        is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", encodeMap(v.entries.associate { it.key.toString() to it.value })))
        is List<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map { encode(it) })))
        else -> error("Can't encode ${v::class}")
    }

    private fun decodeMap(fields: JSONObject): Map<String, Any?> = fields.keys().asSequence().associateWith { decode(fields.getJSONObject(it)) }

    private fun decode(v: JSONObject): Any? = when {
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("doubleValue") -> v.getDouble("doubleValue")
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("timestampValue") -> v.getString("timestampValue")
        v.has("mapValue") -> decodeMap(v.getJSONObject("mapValue").optJSONObject("fields") ?: JSONObject())
        v.has("arrayValue") -> v.getJSONObject("arrayValue").optJSONArray("values")?.let { a -> List(a.length()) { decode(a.getJSONObject(it)) } } ?: emptyList<Any?>()
        else -> null
    }
}
