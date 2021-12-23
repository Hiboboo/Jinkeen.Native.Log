package com.jinkeen.lifeplus.log.nativ

import android.text.TextUtils

enum class Action {
    WRITE, SEND, FLUSH
}

class LogAction(val action: Action) {

    lateinit var writeAction: WriteAction
}

class WriteAction(val log: String) {
    var isMainThread: Boolean = false
    var threadId: Long = 0L
    var threadName: String = ""
    var localTime: Long = 0L
    var flag: Int = 0

    fun isValid(): Boolean = !TextUtils.isEmpty(log)
}