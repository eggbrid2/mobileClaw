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
  window.Claw={
    config:{
      get:function(k){try{return A.getConfig(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setConfig(k,String(v))}catch(e){}}
    },
    memory:{
      get:function(k){try{return A.getMemory(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setMemory(k,String(v))}catch(e){}}
    },
    files:{
      read:function(n){try{return A.readFile(n)||''}catch(e){return ''}},
      write:function(n,d){try{A.writeFile(n,String(d))}catch(e){}},
      list:function(){try{return JSON.parse(A.listFiles()||'[]')}catch(e){return []}},
      del:function(n){try{return !!A.deleteFile(n)}catch(e){return false}}
    },
    fetch:function(url,opts){
      opts=opts||{};
      var m=opts.method||'GET',h=JSON.stringify(opts.headers||{}),b=opts.body||'';
      try{return JSON.parse(A.httpFetch(url,m,h,b))}
      catch(e){return {error:e.message,status:0,ok:false,body:''}}
    },
    sql:function(q,p){
      try{return JSON.parse(A.sqlite(q,JSON.stringify(p||[])))}
      catch(e){return {error:e.message,rows:[],rowCount:0}}
    },
    python:function(d){
      var s=typeof d==='string'?d:JSON.stringify(d);
      try{return JSON.parse(A.callPython(s))}
      catch(e){return {error:e.message}}
    },
    setPython:function(code){try{A.setPythonBackend(code)}catch(e){}},
    getPython:function(){try{return A.getPythonBackend()||''}catch(e){return ''}},
    toast:function(m){try{A.showToast(String(m))}catch(e){}},
    vibrate:function(ms){try{A.vibrate(ms||100)}catch(e){}},
    device:function(){try{return JSON.parse(A.getDeviceInfo()||'{}')}catch(e){return {}}},
    clipboard:{
      get:function(){try{return A.clipboardGet()||''}catch(e){return ''}},
      set:function(t){try{A.clipboardSet(String(t))}catch(e){}}
    },
    ask:function(m){try{A.askAgent(String(m))}catch(e){}},
    close:function(){try{A.close()}catch(e){}}
  };
})();
""".trimIndent()

    fun injectBridge(html: String): String {
        val script = """
<script>
"use strict";
(function(){
  var A=window.Android;
  window.Claw={
    config:{
      get:function(k){try{return A.getConfig(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setConfig(k,String(v))}catch(e){}}
    },
    memory:{
      get:function(k){try{return A.getMemory(k)||''}catch(e){return ''}},
      set:function(k,v){try{A.setMemory(k,String(v))}catch(e){}}
    },
    files:{
      read:function(n){try{return A.readFile(n)||''}catch(e){return ''}},
      write:function(n,d){try{A.writeFile(n,String(d))}catch(e){}},
      list:function(){try{return JSON.parse(A.listFiles()||'[]')}catch(e){return []}},
      del:function(n){try{return !!A.deleteFile(n)}catch(e){return false}}
    },
    fetch:function(url,opts){
      opts=opts||{};
      var m=opts.method||'GET',h=JSON.stringify(opts.headers||{}),b=opts.body||'';
      try{return JSON.parse(A.httpFetch(url,m,h,b))}
      catch(e){return {error:e.message,status:0,ok:false,body:''}}
    },
    sql:function(q,p){
      try{return JSON.parse(A.sqlite(q,JSON.stringify(p||[])))}
      catch(e){return {error:e.message,rows:[],rowCount:0}}
    },
    python:function(d){
      var s=typeof d==='string'?d:JSON.stringify(d);
      try{return JSON.parse(A.callPython(s))}
      catch(e){return {error:e.message}}
    },
    setPython:function(code){try{A.setPythonBackend(code)}catch(e){}},
    getPython:function(){try{return A.getPythonBackend()||''}catch(e){return ''}},
    toast:function(m){try{A.showToast(String(m))}catch(e){}},
    vibrate:function(ms){try{A.vibrate(ms||100)}catch(e){}},
    device:function(){try{return JSON.parse(A.getDeviceInfo()||'{}')}catch(e){return {}}},
    clipboard:{
      get:function(){try{return A.clipboardGet()||''}catch(e){return ''}},
      set:function(t){try{A.clipboardSet(String(t))}catch(e){}}
    },
    ask:function(m){try{A.askAgent(String(m))}catch(e){}},
    close:function(){try{A.close()}catch(e){}}
  };
})();
</script>
""".trimIndent()
        val headEnd = Regex("</head>", RegexOption.IGNORE_CASE).find(html)
        if (headEnd != null) return html.substring(0, headEnd.range.first) + script + html.substring(headEnd.range.first)
        val bodyStart = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (bodyStart != null) return html.substring(0, bodyStart.range.last + 1) + script + html.substring(bodyStart.range.last + 1)
        val htmlTag = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (htmlTag != null) return html.substring(0, htmlTag.range.last + 1) + script + html.substring(htmlTag.range.last + 1)
        return script + html
    }
}
