package com.mobileclaw.skill.executor

import android.content.Context
import com.chaquo.python.Python
import java.io.File

/**
 * Runtime package installer for Chaquopy's embedded Python.
 *
 * Does NOT use pip (not bundled in Chaquopy APK). Instead queries the
 * PyPI JSON API (with Chinese mirror fallback), downloads the pure-Python
 * wheel via requests, and extracts to filesDir/pip_packages on sys.path.
 *
 * Pure-Python packages (py3-none-any wheels) always work.
 * Native packages (C extensions) must be pre-installed in build.gradle.
 * Pre-installed: requests, beautifulsoup4, numpy, pillow, pandas, lxml.
 *
 * Mirror order: Tsinghua → PyPI (automatic fallback for CN users).
 */
object RuntimePipInstaller {

    fun pipPackagesDir(context: Context): File =
        File(context.filesDir, "pip_packages").also { it.mkdirs() }

    fun injectSysPath(python: Python, targetPath: String) {
        val sys = python.getModule("sys")
        val path = sys.get("path") ?: return
        if (path.asList().none { it.toString() == targetPath }) {
            path.callAttr("insert", 0, targetPath)
        }
    }

    /**
     * Downloads and installs [packageSpec] by fetching its pure-Python wheel
     * from PyPI using the pre-installed requests library.
     * Version pin supported: "package==1.2.3".
     */
    fun install(context: Context, packageSpec: String): Result<String> = runCatching {
        val targetPath = pipPackagesDir(context).absolutePath
        val python = Python.getInstance()
        injectSysPath(python, targetPath)

        val builtins = python.getModule("builtins")
        val ns = builtins.callAttr("dict")

        // Pass package spec and target path via namespace to avoid string injection
        ns.callAttr("__setitem__", "_pkg_spec", packageSpec)
        ns.callAttr("__setitem__", "_target", targetPath)

        builtins.callAttr("exec", INSTALL_SCRIPT, ns)

        val result = ns.callAttr("get", "_install_result").toString()
        if (result.startsWith("ERROR:")) error(result.removePrefix("ERROR:").trim())
        result
    }

    /** Python snippet injected at the top of every skill script. */
    fun buildPreamble(targetPath: String): String = """
import sys as _sys
if "$targetPath" not in _sys.path:
    _sys.path.insert(0, "$targetPath")

def pip_install(package):
    import requests as _req, zipfile as _zf, io as _io, os as _os, json as _json
    _target = "$targetPath"
    _os.makedirs(_target, exist_ok=True)
    _MIRRORS = [
        "https://pypi.tuna.tsinghua.edu.cn/pypi/{}/json",
        "https://pypi.org/pypi/{}/json",
    ]
    # Parse name and optional version
    _name = package.split("==")[0].split(">=")[0].split("<=")[0].strip()
    _ver_spec = package[len(_name):].strip() or None
    # Query PyPI (try mirrors in order)
    _data = None
    for _mirror in _MIRRORS:
        try:
            _r = _req.get(_mirror.format(_name), timeout=10)
            if _r.ok:
                _data = _r.json()
                break
        except Exception:
            continue
    if not _data:
        return f"Package not found: {_name} (checked {len(_MIRRORS)} mirrors)"
    _version = _data["info"]["version"] if not _ver_spec else _ver_spec.lstrip("=><")
    _urls = _data.get("releases", {}).get(_version, _data.get("urls", []))
    # Find pure-Python wheel
    _wheel_url = None
    for _u in _urls:
        _fn = _u.get("filename","")
        if _fn.endswith(".whl") and ("py3-none-any" in _fn or "py2.py3-none-any" in _fn):
            _wheel_url = _u["url"]
            break
    if not _wheel_url:
        return f"No pure-Python wheel for {_name}=={_version}. Native packages (lxml/numpy/pandas/pillow) are pre-installed, no need to install."
    # Download from tsinghua if available, else original URL
    _dl_url = _wheel_url.replace("https://files.pythonhosted.org/", "https://pypi.tuna.tsinghua.edu.cn/")
    try:
        _wr = _req.get(_dl_url, timeout=60)
        _wr.raise_for_status()
    except Exception:
        _wr = _req.get(_wheel_url, timeout=60)
        _wr.raise_for_status()
    with _zf.ZipFile(_io.BytesIO(_wr.content)) as _z:
        for _member in _z.namelist():
            if ".dist-info/" not in _member:
                _z.extract(_member, _target)
    if _target not in _sys.path:
        _sys.path.insert(0, _target)
    return f"Installed {_name}=={_version}"

""".trimIndent()

    // Reuse same logic for the Kotlin-side install() call
    private val INSTALL_SCRIPT = """
import requests as _req, zipfile as _zf, io as _io, os as _os, json as _json, sys as _sys

_pkg_spec = _pkg_spec  # injected from namespace
_target   = _target    # injected from namespace
_os.makedirs(_target, exist_ok=True)

_MIRRORS = [
    "https://pypi.tuna.tsinghua.edu.cn/pypi/{}/json",
    "https://pypi.org/pypi/{}/json",
]
_name = _pkg_spec.split("==")[0].split(">=")[0].split("<=")[0].strip()
_ver_spec = _pkg_spec[len(_name):].strip() or None

try:
    _data = None
    for _mirror in _MIRRORS:
        try:
            _r = _req.get(_mirror.format(_name), timeout=10)
            if _r.ok:
                _data = _r.json()
                break
        except Exception:
            continue
    if not _data:
        _install_result = f"ERROR: Package not found: {_name} (checked all mirrors)"
        raise StopIteration
    _version = _data["info"]["version"] if not _ver_spec else _ver_spec.lstrip("=><")
    _urls = _data.get("releases", {}).get(_version, _data.get("urls", []))
    _wheel_url = None
    for _u in _urls:
        _fn = _u.get("filename", "")
        if _fn.endswith(".whl") and ("py3-none-any" in _fn or "py2.py3-none-any" in _fn):
            _wheel_url = _u["url"]
            break
    if not _wheel_url:
        _install_result = f"ERROR: No pure-Python wheel for {_name}=={_version}. Native packages (lxml/numpy/pandas/pillow) are pre-installed."
        raise StopIteration
    _dl_url = _wheel_url.replace("https://files.pythonhosted.org/", "https://pypi.tuna.tsinghua.edu.cn/")
    try:
        _wr = _req.get(_dl_url, timeout=60)
        _wr.raise_for_status()
    except Exception:
        _wr = _req.get(_wheel_url, timeout=60)
        _wr.raise_for_status()
    with _zf.ZipFile(_io.BytesIO(_wr.content)) as _z:
        for _member in _z.namelist():
            if ".dist-info/" not in _member:
                _z.extract(_member, _target)
    if _target not in _sys.path:
        _sys.path.insert(0, _target)
    _install_result = f"Installed {_name}=={_version}"
except StopIteration:
    pass
except Exception as _e:
    _install_result = f"ERROR: {_e}"
""".trimIndent()
}
