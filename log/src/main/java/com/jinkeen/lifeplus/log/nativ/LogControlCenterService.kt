package com.jinkeen.lifeplus.log.nativ

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.util.ArrayMap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.jinkeen.lifeplus.log.listener.OnLogProtocolStatusListener
import com.jinkeen.lifeplus.log.parser.LogParserProtocol
import com.jinkeen.lifeplus.log.util.escapeTimemillis
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * 本地日志操作控制器中心
 * --
 * 负责具体的日志写入，本地日志文件控制操作
 */
class LogControlCenterService : Service() {

    companion object {

        private const val TAG = "LogControlCenterService"
    }

    inner class ControlCenterBinder : Binder() {

        fun getService(): LogControlCenterService = this@LogControlCenterService
    }

    private val iBinder = ControlCenterBinder()
    private lateinit var config: LogConfig

    private lateinit var worker: FileWorker

    override fun onBind(intent: Intent): IBinder {
        config = intent.getParcelableExtra(LogConfig.EXTRA_CONFIG)!!
        worker = FileWorker.Instance.get(config)
        thread(start = true, name = "log_native_write") { this.execute() }
        return iBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // 强制停止
        this.quit(true)
        return false
    }

    private val logCacheQueue = ConcurrentLinkedQueue<LogAction>()

    // 是否要继续执行工作，只取决于队列中是否还有日志事件
    private var isContinueWorking = false
    private val workLock = Object()

    /**
     * TODO
     *
     * @param log
     * @param type
     */
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

    /**
     * TODO
     *
     */
    fun flush() {
        Log.d(TAG, "接收到一条强制写入事件")
        logCacheQueue.add(LogAction(Action.FLUSH))
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
        Log.d(TAG, "停止本地的日志写入")
    }

    private var listener: OnLogProtocolStatusListener? = null

    fun setOnLogProtocolStatusListener(listener: OnLogProtocolStatusListener?) {
        this.listener = listener
    }

    private val sTaskIDs = AtomicLong(1000)
    private val sTaskArray = ArrayMap<Long, Job>()
    private val gson = Gson()

    /**
     * 立即上传指定的日志信息到服务端，将按照具体的时间范围进行精细化的筛选。
     *
     * @param types 指定要上传的日志类型。当筛选时间间隔超过24小时，将忽略该参数的作用
     * @param beginTime 开始的时间戳，若超过本地已记录的最早日志时间，将自动按本地记录的最早时间来算。
     * @param endTime 结束的时间戳，若超过本地记录的最晚日志时间，将自动按照本地记录的最晚日志时间来算。
     */
    fun up(types: IntArray, beginTime: Long, endTime: Long): Long {
        val rKeys = hashSetOf<Long>()
        sTaskArray.entries.forEach { if (!it.value.isActive || it.value.isCompleted) rKeys.add(it.key) }
        sTaskArray.removeAll(rKeys)
        val id = sTaskIDs.getAndIncrement()
        sTaskArray[id] = CoroutineScope(Dispatchers.IO).launch {
            /*
             * 日志上传应该有两种方法
             * 1，少量的日志内容，直接传输字符串，以节省流量开支
             * 2，大量的日志内容，应上传对应的文件
             *
             * 当开始到结束时间的间隔在24小时以内，首选选择字符串上传，否则首选选择文件上传。
             */
            val logs = worker.filterFiles(escapeTimemillis(beginTime), escapeTimemillis(endTime))
            if (endTime - beginTime < LogConfig.DAY) {
                // 24小时以内，最多只有两个本地日志文件
                // {"c":"Log content-21660","f":101,"l":1640336274432,"n":"log","i":188,"m":false}
                val upLog = try {
                    buildString {
                        buildString { logs.forEach { if (isActive) append(LogParserProtocol(it).process()) } }.split("\n").forEach {
                            val log = gson.fromJson(it, L::class.java)
                            if (log.time in beginTime..endTime) {
                                if (types.isEmpty()) append(it) else if (types.contains(log.type)) append(it)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "筛选日志时出现异常。", e)
                    ""
                }
                Log.d(TAG, "要上传的日志全部数据：\n${upLog}")
                if (!isActive || upLog.isEmpty()) return@launch

                // 执行上传动作.....

            } else {
                // 整24小时或大于24小时，直接上传日志文件
            }
        }
        return id
    }

    /**
     * 停止正在进行中的上传任务，当 `taskId<0` 时，停止全部任务
     *
     * @param taskId 任务ID
     * @see up
     */
    fun stop(taskId: Long) {
        if (!sTaskArray.containsKey(taskId)) return
        if (sTaskArray[taskId]?.isActive == true) {
            sTaskArray[taskId]?.cancel("被强行停止。")
            sTaskArray.remove(taskId)
        }
    }

    private inner class L(
        @SerializedName("c")
        var content: String,
        @SerializedName("f")
        val type: Int,
        @SerializedName("l")
        val time: Long,
        @SerializedName("n")
        val threadName: String,
        @SerializedName("i")
        val threadId: Long,
        @SerializedName("m")
        val isMainThread: Boolean
    ) {
        fun toJson(): String {
            this.content = content.dropLast(1)
            return gson.toJson(this)
        }
    }
}