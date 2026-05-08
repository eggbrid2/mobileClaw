package com.mobileclaw

import androidx.annotation.StringRes

fun str(@StringRes id: Int): String = ClawApplication.instance.localizedContext.getString(id)
fun str(@StringRes id: Int, vararg args: Any): String = ClawApplication.instance.localizedContext.getString(id, *args)
