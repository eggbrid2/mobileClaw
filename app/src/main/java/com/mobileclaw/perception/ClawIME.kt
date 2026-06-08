package com.mobileclaw.perception

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo

/** Lightweight IME for reliable text injection bypassing clipboard. */
class ClawIME : InputMethodService() {

    companion object {
        var instance: ClawIME? = null
            private set

        fun isReady(): Boolean = instance?.currentInputConnection != null

        fun inputText(text: String): Boolean =
            instance?.currentInputConnection?.commitText(text, 1) == true
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
