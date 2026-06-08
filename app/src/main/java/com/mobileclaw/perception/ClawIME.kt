package com.mobileclaw.perception

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.view.inputmethod.EditorInfo

/** Lightweight IME for reliable text injection bypassing clipboard. */
class ClawIME : InputMethodService() {

    companion object {
        var instance: ClawIME? = null
            private set

        fun isReady(): Boolean = instance?.currentInputConnection != null

        fun inputText(text: String): Boolean =
            instance?.currentInputConnection?.commitText(text, 1) == true

        fun statusSummary(): String {
            val service = instance
            val context = service ?: return "MobileClaw 输入法未启用或未被系统绑定。请在系统设置 > 语言和输入法中启用 MobileClaw 输入法，并切换为当前输入法。"
            return statusSummary(context)
        }

        fun statusSummary(context: Context): String {
            val service = instance
            val enabledInputMethods = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty()
            val defaultInputMethod = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
            return when {
                !enabledInputMethods.contains(context.packageName) -> "MobileClaw 输入法未启用。请在系统输入法设置中启用 MobileClaw 输入法。"
                !defaultInputMethod.contains(context.packageName) -> "MobileClaw 输入法已启用，但不是当前输入法。请从输入法切换面板选择 MobileClaw 输入法。"
                service == null -> "MobileClaw 输入法已被系统选中，但服务尚未连接。请点一下输入框或重新切换到 MobileClaw 输入法。"
                currentConnectionUnavailable() -> "MobileClaw 输入法是当前输入法，但当前输入框没有可用 InputConnection。请先点击输入框让它获得焦点。"
                else -> "MobileClaw 输入法可用。"
            }
        }

        private fun currentConnectionUnavailable(): Boolean =
            instance?.currentInputConnection == null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onCreateInputView() = null

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {}
}
