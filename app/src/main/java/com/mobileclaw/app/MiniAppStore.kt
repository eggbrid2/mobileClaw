package com.mobileclaw.app

import android.content.Context
import com.google.gson.Gson
import com.mobileclaw.artifact.ArtifactHistoryEntry
import com.mobileclaw.artifact.ArtifactSpec
import com.mobileclaw.storage.AtomicTextFile
import java.io.File

data class MiniApp(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "apps",
    val htmlPath: String,
    val hasPython: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val spec: ArtifactSpec = ArtifactSpec(),
    val history: List<ArtifactHistoryEntry> = emptyList(),
)

/** Persists mini-app metadata and HTML content under filesDir/apps/. */
class MiniAppStore(private val context: Context) {

    private val gson = Gson()
    private val ioLock = Any()
    private val appsDir: File get() = context.filesDir.resolve("apps").also { it.mkdirs() }

    fun all(): List<MiniApp> = synchronized(ioLock) {
        appsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), MiniApp::class.java) }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun get(id: String): MiniApp? = synchronized(ioLock) {
        runCatching { gson.fromJson(AtomicTextFile.readOrNull(File(appsDir, "$id.json")), MiniApp::class.java) }.getOrNull()
    }

    fun save(app: MiniApp, htmlContent: String) {
        synchronized(ioLock) {
            val htmlFile = File(appsDir, "${app.id}.html")
            AtomicTextFile.write(htmlFile, htmlContent)
            val meta = app.copy(htmlPath = htmlFile.absolutePath, updatedAt = System.currentTimeMillis())
            AtomicTextFile.write(File(appsDir, "${app.id}.json"), gson.toJson(meta))
        }
    }

    fun htmlFile(id: String): File = File(appsDir, "$id.html")

    fun readHtml(id: String): String? = synchronized(ioLock) {
        runCatching { AtomicTextFile.readOrNull(htmlFile(id)) }.getOrNull()
    }

    fun updateHtml(id: String, htmlContent: String): Boolean = synchronized(ioLock) {
        val meta = get(id) ?: return false
        AtomicTextFile.write(File(appsDir, "$id.html"), htmlContent)
        AtomicTextFile.write(File(appsDir, "$id.json"), gson.toJson(meta.copy(updatedAt = System.currentTimeMillis())))
        true
    }

    fun savePython(id: String, code: String) {
        synchronized(ioLock) {
            val dir = appDataDir(id)
            AtomicTextFile.write(File(dir, "backend.py"), code)
            val meta = get(id) ?: return
            if (!meta.hasPython) {
                AtomicTextFile.write(File(appsDir, "$id.json"), gson.toJson(meta.copy(hasPython = true, updatedAt = System.currentTimeMillis())))
            }
        }
    }

    fun readPython(id: String): String? = synchronized(ioLock) {
        runCatching { AtomicTextFile.readOrNull(File(appDataDir(id), "backend.py")) }.getOrNull()
    }

    fun appLogFile(id: String): File = File(appDataDir(id), "app.log")

    fun appendLog(id: String, level: String, tag: String, message: String) {
        synchronized(ioLock) {
            val line = buildString {
                append(System.currentTimeMillis())
                append(" [")
                append(level.uppercase())
                append("] ")
                append(tag.ifBlank { "app" }.take(40))
                append(": ")
                append(message.take(2000).replace('\n', ' '))
            }
            val file = appLogFile(id)
            file.parentFile?.mkdirs()
            val current = AtomicTextFile.readOrNull(file).orEmpty()
            AtomicTextFile.write(file, current + line + "\n")
            trimLogFile(file, maxLines = 400)
        }
    }

    fun readLogs(id: String, limit: Int = 120): List<String> = synchronized(ioLock) {
        runCatching {
            AtomicTextFile.readOrNull(appLogFile(id))
                ?.lines()
                ?.filter { it.isNotEmpty() }
                ?.takeLast(limit)
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    fun clearLogs(id: String): Boolean =
        runCatching { appLogFile(id).delete() || !appLogFile(id).exists() }.getOrDefault(false)

    fun updateIcon(id: String, iconPath: String): Boolean = synchronized(ioLock) {
        val meta = get(id) ?: return false
        AtomicTextFile.write(File(appsDir, "$id.json"), gson.toJson(meta.copy(icon = iconPath, updatedAt = System.currentTimeMillis())))
        true
    }

    fun delete(id: String) {
        File(appsDir, "$id.json").delete()
        File(appsDir, "$id.html").delete()
        // Clean up data directory
        appDataDir(id).deleteRecursively()
    }

    fun appDataDir(id: String): File = File(appsDir, "${id}_data").also { it.mkdirs() }

    /**
     * Injects the Claw JS bridge helper script into HTML content.
     * Tries to insert before </head>, then before <body>, then after <html>, else prepends.
     */
    /** Returns just the IIFE JS string (no <script> tags) for use in evaluateJavascript. */
    fun clawBridgeSetupJs(): String = """
"use strict";
(function(){
  var A=window.Android;
  // ── Async callback registry ────────────────────────────────────────────
  window._clawPending={};
  window._clawCb=function(id,enc){
    var cb=window._clawPending[id];
    if(!cb)return;
    try{var r=JSON.parse(decodeURIComponent(enc));if(r&&r.error)cb.rej(r);else cb.res(r);}
    catch(e){cb.rej({error:e.message});}
    delete window._clawPending[id];
  };
  function _async(fn){
    return function(){
      var args=[].slice.call(arguments);
      return new Promise(function(res,rej){
        var id='c'+Math.random().toString(36).substr(2,9);
        window._clawPending[id]={res:res,rej:rej};
        args.push(id);
        try{fn.apply(null,args);}catch(e){delete window._clawPending[id];rej({error:e.message});}
      });
    };
  }
  // ── Global error handlers — catch silent failures ──────────────────────
  window.addEventListener('unhandledrejection',function(e){
    var msg='Unhandled Promise rejection: '+(e.reason&&(e.reason.message||e.reason)||'unknown');
    try{A.showToast(msg.substring(0,100));}catch(_){}
    try{A.appendLog('error','promise',msg);}catch(_){}
    console.error(msg);
  });
  window.addEventListener('error',function(e){
    var msg='JS error: '+(e.message||'unknown')+' ('+e.filename+':'+e.lineno+')';
    try{A.showToast(msg.substring(0,100));}catch(_){}
    try{A.appendLog('error','window',msg);}catch(_){}
    console.error(msg);
  });
  // ── Redirect native fetch() → Claw.fetch() so AI mistakes fail loudly ──
  window.fetch=function(url,opts){
    try{A.showToast('Use Claw.fetch() not fetch(). Redirecting…');}catch(_){}
    return _async(function(u,o,id){var op=o||{};A.httpFetchAsync(u,op.method||'GET',JSON.stringify(op.headers||{}),op.body||'',id);})(url,opts).then(function(r){
      return {ok:r.ok,status:r.status,text:function(){return Promise.resolve(r.body);},json:function(){return Promise.resolve(JSON.parse(r.body));}};
    });
  };
  window.XMLHttpRequest=function(){try{A.showToast('Use Claw.fetch() instead of XHR');}catch(_){} throw new Error('XMLHttpRequest blocked — use Claw.fetch()');};
  window.Claw={
    // ── Async I/O (always use await) ──────────────────────────────────────
    fetch:_async(function(url,opts,id){
      var o=opts||{};
      A.httpFetchAsync(url,o.method||'GET',JSON.stringify(o.headers||{}),o.body||'',id);
    }),
    sql:_async(function(q,p,id){A.sqliteAsync(q,JSON.stringify(p||[]),id);}),
    python:_async(function(d,id){A.callPythonAsync(typeof d==='string'?d:JSON.stringify(d),id);}),
    shell:_async(function(cmd,id){A.shellExecAsync(cmd,id);}),
    pip:_async(function(pkg,id){A.pipInstallAsync(pkg,id);}),
    pythonEnv:function(){try{return JSON.parse(A.pythonEnvInfo()||'{}')}catch(e){return {error:e.message}}},
    // ── Config / Memory (sync — fast, no I/O wait) ────────────────────────
    config:{
      get:function(k){try{return A.getConfig(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setConfig(k,String(v))}catch(e){}}
    },
    memory:{
      get:function(k){try{return A.getMemory(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setMemory(k,String(v))}catch(e){}},
      context:function(m){try{return A.memoryContext(String(m||''))||''}catch(e){return ''}}
    },
    // ── Files (sync — local disk) ─────────────────────────────────────────
    files:{
      read:function(n){try{return A.readFile(n)||''}catch(e){return ''}},
      write:function(n,d){try{A.writeFile(n,String(d))}catch(e){}},
      list:function(){try{return JSON.parse(A.listFiles()||'[]')}catch(e){return []}},
      del:function(n){try{return !!A.deleteFile(n)}catch(e){return false}}
    },
    // ── Python backend helpers ─────────────────────────────────────────────
    setPython:function(code){try{A.setPythonBackend(code)}catch(e){}},
    getPython:function(){try{return A.getPythonBackend()||''}catch(e){return ''}},
    // ── UI helpers (sync) ─────────────────────────────────────────────────
    toast:function(m){try{A.showToast(String(m))}catch(e){}},
    vibrate:function(ms){try{A.vibrate(ms||100)}catch(e){}},
    device:function(){try{return JSON.parse(A.getDeviceInfo()||'{}')}catch(e){return {}}},
    log:{
      info:function(tag,msg){try{A.appendLog('info',String(tag||'app'),String(msg||''))}catch(e){}},
      warn:function(tag,msg){try{A.appendLog('warn',String(tag||'app'),String(msg||''))}catch(e){}},
      error:function(tag,msg){try{A.appendLog('error',String(tag||'app'),String(msg||''))}catch(e){}},
      debug:function(tag,msg){try{A.appendLog('debug',String(tag||'app'),String(msg||''))}catch(e){}},
      read:function(limit){try{return JSON.parse(A.readLogs(limit||80)||'[]')}catch(e){return []}},
      clear:function(){try{return !!A.clearLogs()}catch(e){return false}}
    },
    clipboard:{
      get:function(){try{return A.clipboardGet()||''}catch(e){return ''}},
      set:function(t){try{A.clipboardSet(String(t))}catch(e){}}
    },
    ask:function(m){try{A.askAgent(String(m))}catch(e){}},
    close:function(){try{A.close()}catch(e){}},
    setTitle:function(t){try{A.setTitle(String(t))}catch(e){}},
    // ── Native Android integration ────────────────────────────────────────────
    launchApp:function(pkg){try{return JSON.parse(A.launchApp(String(pkg)));}catch(e){return{error:e.message};}},
    openUrl:function(url){try{return JSON.parse(A.openUrl(String(url)));}catch(e){return{error:e.message};}},
    shareText:function(text,title){try{return JSON.parse(A.shareText(String(text),String(title||'')));}catch(e){return{error:e.message};}}
  };
  // Inject device-accurate viewport height as CSS custom property --vh
  // Use height:calc(var(--vh)*100) instead of 100vh in your CSS for reliable full-screen layout
  (function(){
    function setVH(){
      var h=window.innerHeight||document.documentElement.clientHeight;
      document.documentElement.style.setProperty('--vh',(h*0.01)+'px');
      document.documentElement.style.setProperty('--app-height',h+'px');
    }
    setVH();
    window.addEventListener('resize',setVH);
  })();
})();
""".trimIndent()

    fun injectBridge(html: String): String {
        val layoutMeta = buildString {
            if (!html.contains("name=\"viewport\"", ignoreCase = true)) {
                append("""<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">""")
                append("\n")
            }
            // Let WebView handle native scroll — do NOT set overflow:hidden on html, that causes
            // dual-scroll conflicts between CSS overflow and WebView's native scroll mechanism.
            // Inner scrollable containers should use overflow-y:auto + -webkit-overflow-scrolling:touch.
            append("""<style>html,body{margin:0;padding:0;min-height:100%;overflow-x:hidden;}body{-webkit-text-size-adjust:100%;box-sizing:border-box;}*{box-sizing:inherit;}-webkit-scrollbar{display:none;}</style>""")
            append("\n")
        }

        val script = "$layoutMeta<script>\n${clawBridgeSetupJs()}\n</script>"
        val headEnd = Regex("</head>", RegexOption.IGNORE_CASE).find(html)
        if (headEnd != null) return html.substring(0, headEnd.range.first) + script + html.substring(headEnd.range.first)
        val bodyStart = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (bodyStart != null) return html.substring(0, bodyStart.range.last + 1) + script + html.substring(bodyStart.range.last + 1)
        val htmlTag = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (htmlTag != null) return html.substring(0, htmlTag.range.last + 1) + script + html.substring(htmlTag.range.last + 1)
        return script + html
    }

    private fun trimLogFile(file: File, maxLines: Int) {
        runCatching {
            val lines = file.readLines()
            if (lines.size > maxLines) {
                file.writeText(lines.takeLast(maxLines).joinToString("\n") + "\n")
            }
        }
    }
}
