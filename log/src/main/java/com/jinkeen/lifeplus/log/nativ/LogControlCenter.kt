package com.jinkeen.lifeplus.log.nativ

import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.jinkeen.lifeplus.log.listener.OnLogProtocolStatusListener
import com.jinkeen.lifeplus.log.util.SingletonFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地日志操作控制器中心
 * --
 * 负责具体的日志写入，本地日志文件控制操作
 */
class LogControlCenter private constructor(private val config: LogConfig) {

    object Instance : SingletonFactory<LogControlCenter, LogConfig>(::LogControlCenter)

    companion object {

        private const val TAG = "LogControlCenter"
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
        synchronized(workLock) { isContinueWorking = true }
    }

    fun flush() {
        Log.d(TAG, "接收到一条强制写入事件")
        logCacheQueue.add(LogAction(Action.FLUSH))
        synchronized(workLock) { isContinueWorking = true }
    }

    private val isQuit = AtomicBoolean(false)
    private val protocol = LogProtocol()
    private val worker = FileWorker.Instance.get(config)

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
                            Action.WRITE -> worker.write(protocol, action.writeAction)
                            Action.FLUSH -> worker.flush(protocol)
                            Action.SEND -> {}
                        }
                    } ?: run { isContinueWorking = false }
                } catch (e: Exception) {
                    Log.e(TAG, "工作线程出现异常", e)
                }
            }
        }
    }

    /**
     * 结束本地的日志写入任务，将不再接收新的日志信息
     *
     * @param isFlush 是否在结束前将缓存队列中的日志强制写入到日志文件
     */
    fun quit(isFlush: Boolean) {
        if (isFlush) protocol.flush()
        isQuit.set(true)
        if (!executors.isShutdown) executors.shutdown()
    }

    private var listener: OnLogProtocolStatusListener? = null

    fun setOnLogProtocolStatusListener(listener: OnLogProtocolStatusListener?) {
        this.listener = listener
    }
}