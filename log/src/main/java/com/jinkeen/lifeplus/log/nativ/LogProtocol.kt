package com.jinkeen.lifeplus.log.nativ

import android.util.Log
import com.dianping.logan.*
import com.jinkeen.lifeplus.log.listener.OnLogProtocolStatusListener
import java.util.*
import kotlin.collections.HashSet

class LogProtocol {

    companion object {

        private val instance: LogProtocol by lazy { LogProtocol() }

        operator fun invoke(): LogProtocol = instance

        private const val TAG = "LogProtocol"
    }

    private var isInitialized = false
    private var loganProtocol: CLoganProtocol? = null

    /**
     * 是否已初始化
     *
     * @return `true`表示已初始化，否则为`false`
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * 初始化文件目录和最大文件大小
     *
     * @param cachePath 指定缓存<code>MMAP</code>的目录文件
     * @param logFilePath 指定日志文件夹目录
     * @param maxSize 指定最大文件大小
     * @param key16 指定128位的文件加密key
     * @param iv16 128位的文件加密iv
     */
    fun init(cachePath: String, logFilePath: String, maxSize: Int, key16: String, iv16: String) {
        Log.d(TAG, "准备初始化，isInitialized=$isInitialized")
        if (isInitialized) return
        if (CLoganProtocol.isCloganSuccess()) {
            loganProtocol = CLoganProtocol.newInstance()
            isInitialized = try {
                Log.d(TAG, "执行clogan_init()函数")
                Log.d(TAG, "参数：cachePath=${cachePath}, logFilePath=${logFilePath}, maxSize=${maxSize}, key=${key16}, iv=${iv16}")
                val code = loganProtocol!!.clogan_init(cachePath, logFilePath, maxSize, key16, iv16)
                this.setLoganStatus(CLGOAN_INIT_STATUS, code)
                code == CLOGAN_INIT_SUCCESS_MMAP || code == CLOGAN_INIT_SUCCESS_MEMORY
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "clogan_init()函数执行出现异常", e)
                this.setLoganStatus(CLGOAN_INIT_STATUS, CLOGAN_INIT_FAIL_JNI)
                false
            }
        } else {
            isInitialized = false
            this.setLoganStatus(CLOGAN_LOAD_SO, CLOGAN_LOAD_SO_FAIL)
        }
    }

    /**
     * 打开一个文件的写入
     *
     * @param fileName 文件名称
     */
    fun open(fileName: String) {
        if (!isInitialized) return
        loganProtocol?.let { protocol ->
            try {
                Log.d(TAG, "执行clogan_open(${fileName})函数")
                val code = protocol.clogan_open(fileName)
                this.setLoganStatus(CLOGAN_OPEN_STATUS, code)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "clogan_open(${fileName})函数执行异常")
                this.setLoganStatus(CLOGAN_OPEN_STATUS, CLOGAN_OPEN_FAIL_JNI)
            }
        } ?: this.setLoganStatus(CLOGAN_OPEN_STATUS, CLOGAN_OPEN_FAIL_NOINIT)
    }

    /**
     * 写入数据 按照顺序和类型传值
     *
     * @param type 日志类型
     * @param log 日志内容
     * @param localTime 日志发生的本地时间（时间戳）
     * @param threadName 线程名称
     * @param threadId 线程id
     * @param isMainThread 是否为主线程
     */
    fun write(type: Int, log: String, localTime: Long, threadName: String, threadId: Long, isMainThread: Boolean) {
        if (!isInitialized) return
        loganProtocol?.let { protocol ->
            try {
                Log.d(TAG, "执行clogan_write()函数")
                Log.d(TAG, "参数：type=${type}, log=${log}, localTime=${localTime}, threadName=${threadName}, threadId=${threadId}, isMainThread=${isMainThread}")
                val code = protocol.clogan_write(type, log, localTime, threadName, threadId, if (isMainThread) 1 else 0)
                this.setLoganStatus(CLOGAN_WRITE_STATUS, code)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "clogan_write()函数执行异常", e)
                this.setLoganStatus(CLOGAN_WRITE_STATUS, CLOGAN_WRITE_FAIL_JNI)
            }
        } ?: this.setLoganStatus(CLOGAN_WRITE_STATUS, CLOGAN_OPEN_FAIL_NOINIT)
    }

    /**
     * 强制写入文件。建议在崩溃或者退出程序的时候调用
     */
    fun flush() {
        if (!isInitialized) return
        loganProtocol?.let { protocol ->
            try {
                Log.d(TAG, "执行clogan_flush()函数")
                protocol.clogan_flush()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "clogan_flush()函数执行异常", e)
            }
        }
    }

    /**
     * 是否为debug环境。debug环境将输出过程日志到控制台中
     *
     * @param isDebug 是否为debug环境
     */
    fun debug(isDebug: Boolean) {
        if (!isInitialized) return
        try {
            Log.d(TAG, "执行clogan_debug()函数")
            loganProtocol?.clogan_debug(isDebug)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "clogan_debug()函数执行异常", e)
        }
    }

    private var listener: OnLogProtocolStatusListener? = null

    fun setOnLogProtocolStatusListener(listener: OnLogProtocolStatusListener?) {
        this.listener = listener
    }

    private val writeCodes = Collections.synchronizedSet(HashSet<Int>())

    private fun setLoganStatus(cmd: String, code: Int) {
        Log.d(TAG, "设置日志状态，CMD=${cmd}, CODE=${code}")
        if (code >= 0) return
        if (CLOGAN_WRITE_STATUS.endsWith(cmd) && code != CLOGAN_WRITE_FAIL_JNI)
            if (writeCodes.contains(code)) return else writeCodes.add(code)
        listener?.onProtocolStatus(cmd, code)
    }
}