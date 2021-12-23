package com.jinkeen.lifeplus.log.task

import com.jinkeen.lifeplus.log.nativ.LogAction

internal class TaskDispatcher private constructor() {

    companion object {

        private val instance: TaskDispatcher by lazy { TaskDispatcher() }

        operator fun invoke(): TaskDispatcher = instance
    }

    fun execute(action: LogAction) {}
}