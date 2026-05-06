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
        description = "Installs a pure-Python package from PyPI at runtime (no pip needed — uses requests+zipfile). " +
            "Works for any package with a py3-none-any wheel (e.g. pydantic, httpx, toml, yaml, etc.). " +
            "Native packages (numpy, pandas, pillow, requests, bs4) are pre-installed — no need to install them. " +
            "After install the package is immediately importable in run_python.",
        descriptionZh = "从 PyPI 下载纯 Python 包（py3-none-any wheel）并安装到 filesDir/pip_packages，无需 pip。" +
            "numpy/pandas/pillow/requests/bs4 已预装，无需安装。安装后立即可在 run_python 中 import。",
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
