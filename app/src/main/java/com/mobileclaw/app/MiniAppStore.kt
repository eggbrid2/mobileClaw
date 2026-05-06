package com.mobileclaw.app

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class MiniApp(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "📱",
    val htmlPath: String,
    val hasPython: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Persists mini-app metadata and HTML content under filesDir/apps/. */
class MiniAppStore(private val context: Context) {

    private val gson = Gson()
    private val appsDir: File get() = context.filesDir.resolve("apps").also { it.mkdirs() }

    fun all(): List<MiniApp> = appsDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { file ->
            runCatching { gson.fromJson(file.readText(), MiniApp::class.java) }.getOrNull()
        }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun get(id: String): MiniApp? =
        runCatching { gson.fromJson(File(appsDir, "$id.json").readText(), MiniApp::class.java) }.getOrNull()

    fun save(app: MiniApp, htmlContent: String) {
        val htmlFile = File(appsDir, "${app.id}.html")
        htmlFile.writeText(htmlContent)
        val meta = app.copy(htmlPath = htmlFile.absolutePath, updatedAt = System.currentTimeMillis())
        File(appsDir, "${app.id}.json").writeText(gson.toJson(meta))
    }

    fun htmlFile(id: String): File = File(appsDir, "$id.html")

    fun readHtml(id: String): String? =
        runCatching { htmlFile(id).readText() }.getOrNull()

    fun updateHtml(id: String, htmlContent: String): Boolean {
        val meta = get(id) ?: return false
        File(appsDir, "$id.html").writeText(htmlContent)
        File(appsDir, "$id.json").writeText(gson.toJson(meta.copy(updatedAt = System.currentTimeMillis())))
        return true
    }

    fun savePython(id: String, code: String) {
        val dir = appDataDir(id)
        File(dir, "backend.py").writeText(code)
        // Update hasPython flag in metadata
        val meta = get(id) ?: return
        if (!meta.hasPython) {
            File(appsDir, "$id.json").writeText(gson.toJson(meta.copy(hasPython = true, updatedAt = System.currentTimeMillis())))
        }
    }

    fun readPython(id: String): String? =
        runCatching { File(appDataDir(id), "backend.py").readText() }.getOrNull()

    fun updateIcon(id: String, iconPath: String): Boolean {
        val meta = get(id) ?: return false
        File(appsDir, "$id.json").writeText(gson.toJson(meta.copy(icon = iconPath, updatedAt = System.currentTimeMillis())))
        return true
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
  window.Claw={
    // ── Async I/O (always use await) ──────────────────────────────────────
    fetch:_async(function(url,opts,id){
      var o=opts||{};
      A.httpFetchAsync(url,o.method||'GET',JSON.stringify(o.headers||{}),o.body||'',id);
    }),
    sql:_async(function(q,p,id){A.sqliteAsync(q,JSON.stringify(p||[]),id);}),
    python:_async(function(d,id){A.callPythonAsync(typeof d==='string'?d:JSON.stringify(d),id);}),
    shell:_async(function(cmd,id){A.shellExecAsync(cmd,id);}),
    // ── Config / Memory (sync — fast, no I/O wait) ────────────────────────
    config:{
      get:function(k){try{return A.getConfig(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setConfig(k,String(v))}catch(e){}}
    },
    memory:{
      get:function(k){try{return A.getMemory(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setMemory(k,String(v))}catch(e){}}
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
    clipboard:{
      get:function(){try{return A.clipboardGet()||''}catch(e){return ''}},
      set:function(t){try{A.clipboardSet(String(t))}catch(e){}}
    },
    ask:function(m){try{A.askAgent(String(m))}catch(e){}},
    close:function(){try{A.close()}catch(e){}},
    setTitle:function(t){try{A.setTitle(String(t))}catch(e){}}
  };
  // Override native fetch so AI-generated code works without CORS issues (file:// origin)
  window.fetch=Claw.fetch;
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
        // Stable layout foundation: viewport meta + overflow guard injected once
        val layoutMeta = buildString {
            if (!html.contains("name=\"viewport\"", ignoreCase = true)) {
                append("""<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">""")
                append("\n")
            }
            // Force body to fill WebView exactly; use overflow-y:auto so content can scroll inside
            append("""<style>html{height:100%;overflow:hidden;}body{min-height:100%;height:auto;overflow-y:auto;overflow-x:hidden;-webkit-text-size-adjust:100%;box-sizing:border-box;}*{box-sizing:inherit;}</style>""")
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
}
