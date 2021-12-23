package com.jinkeen.lifeplus.log.util

open class SingletonFactory<out T, in A>(creator: (A) -> T) {

    private var creator: ((A) -> T)? = creator

    @Volatile
    private var instance: T? = null

    fun get(arg: A): T = instance ?: synchronized(this) {
        instance ?: creator!!(arg).apply {
            instance = this
        }
    }
}