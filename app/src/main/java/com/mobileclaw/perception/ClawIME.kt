package com.mobileclaw.perception

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo

/** Lightweight IME for reliable text injection bypassing clipboard. */
class ClawIME : InputMethodService() {

    companion object {
        var instance: ClawIME? = null
            private set

        fun inputText(text: String) {
            instance?.currentInputConnection?.commitText(text, 1)
        }
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
