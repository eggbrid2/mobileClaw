package com.mobileclaw.skill.executor

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PythonSkillExecutor(
    override val meta: SkillMeta,
    private val script: String,
) : Skill {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(10_000L) {
                runCatching {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(com.mobileclaw.ClawApplication.instance))
                    }
                    val py = Python.getInstance()
                    val builtins = py.getBuiltins()

                    // Inject params as a Python dict named 'params'
                    val paramsDict = py.getModule("builtins").callAttr("dict")
                    params.forEach { (k, v) -> paramsDict.callAttr("__setitem__", k, v.toString()) }

                    val globals = py.getModule("builtins").callAttr("dict")
                    globals.callAttr("__setitem__", "params", paramsDict)

                    // Capture stdout
                    val io = py.getModule("io")
                    val stdout = io.callAttr("StringIO")
                    val sys = py.getModule("sys")
                    sys.put("stdout", stdout)

                    py.getBuiltins().callAttr("exec", script, globals)

                    stdout.callAttr("getvalue").toString()
                }.fold(
                    onSuccess = { SkillResult(success = true, output = it.take(4096)) },
                    onFailure = { SkillResult(success = false, output = "Python error: ${it.message}") }
                )
            }
            result ?: SkillResult(success = false, output = "Python execution timed out (10s)")
        }
    }
}
