package com.mobileclaw.skill.executor

import android.content.Context
import com.chaquo.python.Python
import java.io.File

/**
 * Runtime pip installer using Chaquopy's embedded Python + pip.
 * Packages are installed to {filesDir}/pip_packages and automatically
 * added to sys.path for all subsequent Python skill executions.
 *
 * Pure-Python packages and packages with Android arm64 wheels work.
 * Packages requiring C compilation at install time do not.
 */
object RuntimePipInstaller {

    fun pipPackagesDir(context: Context): File =
        File(context.filesDir, "pip_packages").also { it.mkdirs() }

    /**
     * Injects pip_packages into sys.path so runtime-installed packages
     * are importable. Must be called before exec()-ing any Python script.
     */
    fun injectSysPath(python: Python, targetPath: String) {
        val sys = python.getModule("sys")
        val path = sys.get("path") ?: return
        val pathList = path.asList()
        if (pathList.none { it.toString() == targetPath }) {
            path.callAttr("insert", 0, targetPath)
        }
    }

    /**
     * Installs [packageSpec] (e.g. "requests", "pandas==2.0") via pip.
     * Returns the combined stdout+stderr output from pip.
     */
    fun install(context: Context, packageSpec: String): Result<String> = runCatching {
        val targetPath = pipPackagesDir(context).absolutePath
        val python = Python.getInstance()

        injectSysPath(python, targetPath)

        val io = python.getModule("io")
        val sys = python.getModule("sys")

        val buf = io.callAttr("StringIO")
        val origStdout = sys.get("stdout")
        val origStderr = sys.get("stderr")
        sys.put("stdout", buf)
        sys.put("stderr", buf)

        val exitCode = try {
            python.getModule("pip._internal.cli.main")
                .callAttr("main", arrayOf(
                    "install",
                    "--target", targetPath,
                    "--no-cache-dir",
                    "--disable-pip-version-check",
                    packageSpec,
                )).toInt()
        } finally {
            sys.put("stdout", origStdout)
            sys.put("stderr", origStderr)
        }

        val output = buf.callAttr("getvalue").toString().trim()
        if (exitCode != 0) error("pip exited with code $exitCode\n$output")
        output.ifBlank { "Successfully installed $packageSpec" }
    }

    /** Python snippet injected at the top of every skill script. */
    fun buildPreamble(targetPath: String): String = """
import sys as _sys
if "$targetPath" not in _sys.path:
    _sys.path.insert(0, "$targetPath")

def pip_install(package):
    import io as _io
    _buf = _io.StringIO()
    _orig_out, _orig_err = _sys.stdout, _sys.stderr
    _sys.stdout = _sys.stderr = _buf
    try:
        from pip._internal.cli.main import main as _pip_main
        _code = _pip_main(['install', '--target', '$targetPath', '--no-cache-dir', '--disable-pip-version-check', package])
    except Exception as _e:
        _code = 1
        _buf.write(str(_e))
    finally:
        _sys.stdout, _sys.stderr = _orig_out, _orig_err
    if "$targetPath" not in _sys.path:
        _sys.path.insert(0, "$targetPath")
    _msg = _buf.getvalue().strip()
    return ("Installed " + package) if _code == 0 else ("pip failed: " + _msg)

""".trimIndent()
}
