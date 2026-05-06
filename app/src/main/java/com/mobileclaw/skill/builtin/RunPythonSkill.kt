package com.mobileclaw.skill.builtin

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.skill.executor.RuntimePipInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executes arbitrary Python 3 code via Chaquopy.
 * Pre-installed packages: requests, beautifulsoup4, numpy, pillow.
 * Runtime packages can be installed with pip_install() helper inside the script,
 * or via the pip_install skill before calling run_python.
 */
class RunPythonSkill : Skill {

    override val meta = SkillMeta(
        id = "run_python",
        name = "Run Python",
        nameZh = "执行 Python",
        description = "Executes Python 3 code via Chaquopy (embedded Python 3.11). " +
            "Pre-installed: requests, beautifulsoup4, numpy, pillow. " +
            "Use pip_install skill first for other packages. " +
            "Script output (print/return) is returned as the result. Timeout 60s.",
        descriptionZh = "通过 Chaquopy 执行 Python 3.11 代码。" +
            "已预装：requests、beautifulsoup4、numpy、pillow。" +
            "其他包请先用 pip_install skill 安装，脚本的 print 输出作为结果返回，超时 60 秒。",
        parameters = listOf(
            SkillParam("code", "string", "Python code to execute. Use print() to produce output."),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        isBuiltin = true,
        tags = listOf("Python", "代码"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val code = params["code"] as? String
            ?: return SkillResult(false, "code parameter is required")
        if (code.isBlank()) return SkillResult(false, "code must not be blank")

        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(60_000L) {
                runCatching {
                    val app = ClawApplication.instance
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(app))
                    }
                    val py = Python.getInstance()
                    val pipTarget = RuntimePipInstaller.pipPackagesDir(app).absolutePath
                    RuntimePipInstaller.injectSysPath(py, pipTarget)

                    val io = py.getModule("io")
                    val sys = py.getModule("sys")
                    val buf = io.callAttr("StringIO")
                    val origOut = sys.get("stdout")
                    val origErr = sys.get("stderr")
                    sys.put("stdout", buf)
                    sys.put("stderr", buf)

                    val globals = py.getModule("builtins").callAttr("dict")
                    val fullScript = RuntimePipInstaller.buildPreamble(pipTarget) + "\n" + code
                    try {
                        py.getBuiltins().callAttr("exec", fullScript, globals)
                    } finally {
                        sys.put("stdout", origOut)
                        sys.put("stderr", origErr)
                    }

                    buf.callAttr("getvalue").toString().trim().take(4096).ifBlank { "(no output)" }
                }.fold(
                    onSuccess = { SkillResult(true, it) },
                    onFailure = { SkillResult(false, "Python error: ${it.message?.take(1000)}") },
                )
            }
            result ?: SkillResult(false, "Python execution timed out (60s)")
        }
    }
}
