package com.jinkeen.lifeplus.log.nativ

import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.jinkeen.lifeplus.log.listener.OnLogProtocolStatusListener
import com.jinkeen.lifeplus.log.util.SingletonFactory
import com.jinkeen.lifeplus.log.util.getCurrentDateTimemillis
import com.jinkeen.lifeplus.log.util.isCanWriteSDCard
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 本地日志操作控制器中心
 * --
 * 负责具体的日志写入，本地日志文件控制操作
 */
class LogControlCenter private constructor(private val config: LogConfig) {

    object Instance : SingletonFactory<LogControlCenter, LogConfig>(::LogControlCenter)

    companion object {

        private const val TAG = "LogControlCenter"

        /** 一分钟的毫秒数 */
        private const val MINUTE = 60 * 1000L
    }

    private val executors = Executors.newSingleThreadExecutor()

    init {
        executors.execute { this.execute() }
    }

    private val logCacheQueue = ConcurrentLinkedQueue<LogAction>()

    // 是否要继续执行工作，只取决于队列中是否还有日志事件
    private var isContinueWorking = false
    private val workLock = Object()

    fun write(log: String, type: Int) {
        Log.d(TAG, "接收到一条新的日志内容：log=${log}, type=${type}")
        if (TextUtils.isEmpty(log)) return
        logCacheQueue.add(LogAction(Action.WRITE).apply {
            writeAction = WriteAction(log).apply {
                localTime = System.currentTimeMillis()
                flag = type
                isMainThread = (Looper.getMainLooper() == Looper.myLooper())
                threadId = Thread.currentThread().id
                threadName = Thread.currentThread().name
            }
        })
        Log.d(TAG, "已将新日志添加到队列")
        synchronized(workLock) { isContinueWorking = true }
    }

    fun flush() {
        Log.d(TAG, "接收到一条强制写入事件")
        logCacheQueue.add(LogAction(Action.FLUSH))
        Log.d(TAG, "已将强制写入事件添加到队列")
        synchronized(workLock) { isContinueWorking = true }
    }

    private val isQuit = AtomicBoolean(false)
    private val protocol = LogProtocol()

    private fun execute() {
        while (!isQuit.get()) {
            synchronized(workLock) {
                if (!isContinueWorking) return@synchronized
                try {
                    if (!protocol.isInitialized()) {
                        Log.d(TAG, "对LogProtocol进行初始化")
                        protocol.setOnLogProtocolStatusListener(listener)
                        if (config.isValid()) protocol.init(
                            config.cachePath,
                            config.logDirPath,
                            config.mMaxFile.toInt(),
                            String(config.mEncryptKey16),
                            String(config.mEncryptIv16)
                        )
                        protocol.debug(config.isDebug)
                    }
                    logCacheQueue.poll()?.let { action ->
                        if (!protocol.isInitialized()) return@synchronized
                        Log.d(TAG, "准备进行事件：${action.action}")
                        when (action.action) {
                            Action.WRITE -> write(action.writeAction)
                            Action.FLUSH -> protocol.flush()
                            Action.SEND -> {}
                        }
                    } ?: run { isContinueWorking = false }
                } catch (e: Exception) {
                    Log.e(TAG, "工作线程出现异常", e)
                }
            }
        }
    }

    private var beforeDay = 0L

    /**
     * 之前记录的时间距离当前系统时间，是否在24小时之内
     *
     * @return `true`表示在24小时之内，否则为`false`
     */
    private fun isDay(): Boolean {
        val currentTime = System.currentTimeMillis()
        return beforeDay < currentTime && beforeDay + LogConfig.DAY > currentTime
    }

    private val lastTime = AtomicLong(0)
    private val isCanWrite = AtomicBoolean(true)

    private fun write(w: WriteAction) {
        // 如果记录已超过一天
        if (!isDay()) {
            val currentDayTimemillis = getCurrentDateTimemillis()
            this.deleteExpiredFile(currentDayTimemillis - config.mDay)
            beforeDay = currentDayTimemillis
            protocol.open(beforeDay.toString())
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime.get() > MINUTE)
            isCanWrite.set(isCanWriteSDCard(config.logDirPath, config.mMinSDCard))
        lastTime.set(System.currentTimeMillis())
        if (!isCanWrite.get()) return // 如果不再允许写入

        protocol.write(w.flag, w.log, w.localTime, w.threadName, w.threadId, w.isMainThread)
    }

    /**
     * 删除过期的文件
     *
     * @param delTime 过期的最早时间
     */
    private fun deleteExpiredFile(delTime: Long) {
        File(config.logDirPath).apply {
            if (isDirectory) listFiles()?.forEach {
                if (it.name.matches(Regex("\\d{13}"))) {
                    if (it.name.toLong() <= delTime) it.delete()
                }
            }
        }
    }

    fun quit() {
        isQuit.set(true)
        if (!executors.isShutdown) executors.shutdown()
    }

    private var listener: OnLogProtocolStatusListener? = null

    private fun setOnLogProtocolStatusListener(listener: OnLogProtocolStatusListener) {
        this.listener = listener
    }
}