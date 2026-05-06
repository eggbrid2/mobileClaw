package com.mobileclaw.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.config.UserConfig
import com.mobileclaw.memory.SemanticMemory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Native bridge exposed to mini-app HTML as `window.Android`. */
class AppJsBridge(
    private val context: Context,
    private val appId: String,
    private val store: MiniAppStore,
    private val userConfig: UserConfig,
    private val semanticMemory: SemanticMemory,
    private val onAskAgent: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onSetTitle: (String) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private var pythonNamespace: PyObject? = null
    private var sqliteDb: SQLiteDatabase? = null

    // ── Async callback infrastructure ──────────────────────────────────────────
    @Volatile private var _webView: WeakReference<WebView>? = null
    private val bgExecutor = Executors.newCachedThreadPool()

    /** Bind a WebView so async methods can fire JS callbacks on it. */
    fun bindWebView(wv: WebView) { _webView = WeakReference(wv) }

    private fun fireCallback(callbackId: String, json: String) {
        val enc = URLEncoder.encode(json, "UTF-8")
        mainHandler.post {
            _webView?.get()?.evaluateJavascript("window._clawCb('$callbackId','$enc')", null)
        }
    }

    @JavascriptInterface
    fun httpFetchAsync(url: String, method: String, headersJson: String, body: String, callbackId: String) {
        bgExecutor.submit { fireCallback(callbackId, httpFetch(url, method, headersJson, body)) }
    }

    @JavascriptInterface
    fun sqliteAsync(sql: String, paramsJson: String, callbackId: String) {
        bgExecutor.submit { fireCallback(callbackId, sqlite(sql, paramsJson)) }
    }

    @JavascriptInterface
    fun callPythonAsync(inputJson: String, callbackId: String) {
        bgExecutor.submit { fireCallback(callbackId, callPython(inputJson)) }
    }

    @JavascriptInterface
    fun pipInstallAsync(packageName: String, callbackId: String) {
        bgExecutor.submit { fireCallback(callbackId, pipInstall(packageName)) }
    }

    @JavascriptInterface
    fun pythonEnvInfo(): String {
        return runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            val py = Python.getInstance()
            val builtins = py.getModule("builtins")
            val code = """
import sys, json, importlib
packages = {}
for pkg in ['requests','bs4','numpy','pillow','PIL','pandas','pip']:
    try:
        m = importlib.import_module(pkg)
        packages[pkg] = getattr(m, '__version__', 'ok')
    except ImportError:
        packages[pkg] = None
_env_info = json.dumps({
    'python': sys.version,
    'packages': packages,
    'path': sys.path[:3],
})
""".trimIndent()
            val ns = builtins.callAttr("dict")
            builtins.callAttr("exec", code, ns)
            ns.callAttr("get", "_env_info").toString()
        }.getOrElse { e ->
            gson.toJson(mapOf("error" to (e.message ?: "Python not available")))
        }
    }

    private fun pipInstall(packageName: String): String {
        return runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            val app = context.applicationContext as com.mobileclaw.ClawApplication
            val result = com.mobileclaw.skill.executor.RuntimePipInstaller.install(app, packageName)
            if (result.isSuccess) {
                gson.toJson(mapOf("ok" to true, "output" to result.getOrThrow()))
            } else {
                gson.toJson(mapOf("ok" to false, "output" to (result.exceptionOrNull()?.message ?: "install failed")))
            }
        }.getOrElse { e ->
            gson.toJson(mapOf("ok" to false, "output" to (e.message ?: "install error")))
        }
    }

    @JavascriptInterface
    fun shellExec(cmd: String): String = runCatching {
        val process = ProcessBuilder("sh", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val done = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val out = process.inputStream.bufferedReader().readText().take(8192)
        val code = if (done) process.exitValue() else { process.destroyForcibly(); -1 }
        gson.toJson(mapOf("stdout" to out, "exitCode" to code, "ok" to (code == 0)))
    }.getOrElse { e -> gson.toJson(mapOf("error" to (e.message ?: "shell error"), "exitCode" to -1, "ok" to false)) }

    @JavascriptInterface
    fun shellExecAsync(cmd: String, callbackId: String) {
        bgExecutor.submit { fireCallback(callbackId, shellExec(cmd)) }
    }

    // ── Config / Memory ────────────────────────────────────────────────────────

    @JavascriptInterface
    fun getConfig(key: String): String = runBlocking {
        runCatching { userConfig.get(key) ?: "" }.getOrDefault("")
    }

    @JavascriptInterface
    fun setConfig(key: String, value: String) = runBlocking<Unit> {
        runCatching { userConfig.set(key, value) }
    }

    @JavascriptInterface
    fun getMemory(key: String): String = runBlocking {
        runCatching { semanticMemory.get(key) ?: "" }.getOrDefault("")
    }

    @JavascriptInterface
    fun setMemory(key: String, value: String) = runBlocking<Unit> {
        runCatching { semanticMemory.set(key, value) }
    }

    // ── File I/O ───────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun readFile(name: String): String =
        runCatching { appDataFile(name).readText() }.getOrDefault("")

    @JavascriptInterface
    fun writeFile(name: String, data: String) {
        runCatching {
            val f = appDataFile(name)
            f.parentFile?.mkdirs()
            f.writeText(data)
        }
    }

    @JavascriptInterface
    fun listFiles(): String = runCatching {
        val dir = store.appDataDir(appId)
        val files = dir.listFiles()
            ?.filter { it.name != "app.db" && it.name != "backend.py" }
            ?.map { mapOf("name" to it.name, "size" to it.length(), "isDir" to it.isDirectory) }
            ?: emptyList()
        gson.toJson(files)
    }.getOrDefault("[]")

    @JavascriptInterface
    fun deleteFile(name: String): Boolean = runCatching {
        appDataFile(name).delete()
    }.getOrDefault(false)

    // ── Network ────────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun httpFetch(url: String, method: String, headersJson: String, body: String): String {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val headers = runCatching {
                gson.fromJson(headersJson, Map::class.java) as? Map<String, String>
            }.getOrNull() ?: emptyMap()

            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val requestBody = if (method.uppercase() in setOf("POST", "PUT", "PATCH") && body.isNotEmpty()) {
                val ct = (headers["Content-Type"] ?: "application/json").toMediaType()
                body.toRequestBody(ct)
            } else null

            reqBuilder.method(method.uppercase(), requestBody)
            val response = http.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = mutableMapOf<String, String>()
            response.headers.forEach { (k, v) -> responseHeaders[k] = v }
            gson.toJson(mapOf(
                "status" to response.code,
                "ok" to response.isSuccessful,
                "body" to responseBody,
                "headers" to responseHeaders,
            ))
        }.getOrElse { e ->
            gson.toJson(mapOf("error" to (e.message?.take(400) ?: "Network error"), "status" to 0, "ok" to false))
        }
    }

    // ── SQLite ─────────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun sqlite(sql: String, paramsJson: String): String {
        return runCatching {
            val db = getOrOpenDb()
            val params = runCatching {
                gson.fromJson(paramsJson, Array<Any>::class.java)?.map { it.toString() }?.toTypedArray()
            }.getOrNull() ?: emptyArray()

            val upper = sql.trim().uppercase()
            if (upper.startsWith("SELECT") || upper.startsWith("PRAGMA") || upper.startsWith("WITH")) {
                val cursor = db.rawQuery(sql, params)
                val rows = mutableListOf<Map<String, Any?>>()
                val cols = cursor.columnNames
                while (cursor.moveToNext()) {
                    val row = mutableMapOf<String, Any?>()
                    cols.forEachIndexed { i, col ->
                        row[col] = when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            Cursor.FIELD_TYPE_NULL -> null
                            else -> cursor.getString(i)
                        }
                    }
                    rows.add(row)
                }
                cursor.close()
                gson.toJson(mapOf("rows" to rows, "rowCount" to rows.size))
            } else {
                db.execSQL(sql, params)
                gson.toJson(mapOf("rows" to emptyList<Any>(), "rowCount" to 0, "ok" to true))
            }
        }.getOrElse { e ->
            gson.toJson(mapOf("error" to (e.message?.take(400) ?: "SQL error")))
        }
    }

    private fun getOrOpenDb(): SQLiteDatabase {
        val existing = sqliteDb
        if (existing != null && existing.isOpen) return existing
        val dbFile = File(store.appDataDir(appId), "app.db")
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null).also { sqliteDb = it }
    }

    // ── System APIs ────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun vibrate(ms: Long) {
        mainHandler.post {
            runCatching {
                val duration = ms.coerceIn(10, 1000)
                val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
                }
            }
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            runCatching { Toast.makeText(context, message.take(200), Toast.LENGTH_SHORT).show() }
        }
    }

    @JavascriptInterface
    fun getDeviceInfo(): String = runCatching {
        val dm = context.resources.displayMetrics
        gson.toJson(mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "sdk" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
            "screenWidth" to dm.widthPixels,
            "screenHeight" to dm.heightPixels,
            "density" to dm.density,
            "language" to context.resources.configuration.locales[0].language,
        ))
    }.getOrDefault("{}")

    @JavascriptInterface
    fun clipboardGet(): String = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }.getOrDefault("")

    @JavascriptInterface
    fun clipboardSet(text: String) {
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("", text))
            }
        }
    }

    // ── Python Backend ─────────────────────────────────────────────────────────

    @JavascriptInterface
    fun callPython(inputJson: String): String {
        return runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            val py = Python.getInstance()
            val ns = ensurePythonNamespace(py)
                ?: return gson.toJson(mapOf("error" to "No Python backend set. Call setPythonBackend(code) first."))
            val handleFn = ns.callAttr("get", "handle")
            if (handleFn == null || handleFn.toString() == "None") {
                return gson.toJson(mapOf("error" to "Python backend must define a handle(input_json) function."))
            }
            handleFn.call(inputJson).toString()
        }.getOrElse { e ->
            gson.toJson(mapOf("error" to (e.message?.take(400) ?: "Python execution error")))
        }
    }

    @JavascriptInterface
    fun setPythonBackend(code: String) {
        pythonNamespace = null
        store.savePython(appId, code)
    }

    @JavascriptInterface
    fun getPythonBackend(): String = store.readPython(appId) ?: ""

    private fun ensurePythonNamespace(py: Python): PyObject? {
        val cached = pythonNamespace
        if (cached != null) return cached
        val code = store.readPython(appId) ?: return null
        return runCatching {
            val builtins = py.getModule("builtins")
            val ns = builtins.callAttr("dict")
            builtins.callAttr("exec", code, ns)
            pythonNamespace = ns
            ns
        }.getOrNull()
    }

    // ── Agent / Close ──────────────────────────────────────────────────────────

    @JavascriptInterface
    fun askAgent(message: String) {
        if (message.isNotBlank()) mainHandler.post { onAskAgent(message.trim()) }
    }

    @JavascriptInterface
    fun close() = mainHandler.post { onClose() }

    @JavascriptInterface
    fun setTitle(title: String) = mainHandler.post { onSetTitle(title) }

    @JavascriptInterface
    fun launchApp(packageName: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return gson.toJson(mapOf("error" to "App not found: $packageName"))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            mainHandler.post { context.startActivity(intent) }
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to e.message))
        }
    }

    @JavascriptInterface
    fun openUrl(url: String): String {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            mainHandler.post { context.startActivity(intent) }
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to e.message))
        }
    }

    @JavascriptInterface
    fun shareText(text: String, title: String = ""): String {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                if (title.isNotBlank()) putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            mainHandler.post { context.startActivity(android.content.Intent.createChooser(intent, "分享").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }) }
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            gson.toJson(mapOf("error" to e.message))
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun appDataFile(name: String): File {
        val safe = name.replace("..", "").replace("/", "_").ifBlank { "data.txt" }
        return File(store.appDataDir(appId), safe)
    }
}
