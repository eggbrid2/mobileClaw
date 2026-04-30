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
 * Installs a Python package at runtime via pip into {filesDir}/pip_packages.
 * Pure-Python packages always work. Packages with C extensions require
 * an Android arm64 wheel to be available on PyPI.
 *
 * After installation, the package is immediately importable in all Python skills.
 * The install persists across app restarts (stored in filesDir).
 */
class PipInstallSkill : Skill {

    override val meta = SkillMeta(
        id = "pip_install",
        name = "Pip Install",
        nameZh = "pip 安装",
        description = "Installs a Python package at runtime via pip. " +
            "Pure-Python packages always work (e.g. requests, beautifulsoup4, pandas pure-Python ops). " +
            "After install the package is immediately importable in any Python skill. " +
            "Use pip_install(package=...) in Python skill scripts for inline installs.",
        descriptionZh = "通过 pip 在运行时安装 Python 包到 filesDir/pip_packages。纯 Python 包均可安装；" +
            "需要 C 扩展的包需要 PyPI 上有 Android arm64 wheel。安装后立即可在所有 Python 技能中 import。",
        parameters = listOf(
            SkillParam("package", "string", "Package name with optional version spec, e.g. 'pandas' or 'numpy==1.26.0'"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 0,
        isBuiltin = true,
        tags = listOf("Python", "系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val packageSpec = params["package"] as? String
            ?: return SkillResult(false, "package parameter is required")
        if (packageSpec.isBlank()) return SkillResult(false, "package name must not be blank")

        return withContext(Dispatchers.IO) {
            val timedOut = withTimeoutOrNull(300_000L) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(ClawApplication.instance))
                }
                val ctx = ClawApplication.instance
                RuntimePipInstaller.install(ctx, packageSpec)
            }
            when {
                timedOut == null -> SkillResult(false, "pip install timed out after 5 minutes")
                timedOut.isSuccess -> SkillResult(true, timedOut.getOrThrow())
                else -> SkillResult(false, "pip install failed: ${timedOut.exceptionOrNull()?.message}")
            }
        }
    }
}
