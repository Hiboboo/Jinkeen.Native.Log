package com.jinkeen.lifeplus.log.nativ

import com.jinkeen.lifeplus.log.util.SingletonFactory
import com.jinkeen.lifeplus.log.util.getCurrentDateTimemillis
import com.jinkeen.lifeplus.log.util.isCanWriteSDCard
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class FileWorker private constructor(private val config: LogConfig) {

    object Instance : SingletonFactory<FileWorker, LogConfig>(::FileWorker)

    companion object {

        /** 一分钟的毫秒数 */
        private const val MINUTE = 60 * 1000L
    }

    private var sLastRecordTime = 0L

    private val lastTime = AtomicLong(0)
    private val isCanWrite = AtomicBoolean(true)

    internal fun write(protocol: LogProtocol, w: WriteAction) {
        // 默认自动在每天的0点整创建一个新的日志存储文件
        if (sLastRecordTime == 0L || System.currentTimeMillis() - LogConfig.DAY >= sLastRecordTime) {
            sLastRecordTime = getCurrentDateTimemillis()
            this.deleteExpiredFile(sLastRecordTime - config.saveDays)
            protocol.open(sLastRecordTime.toString())
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

    internal fun flush(protocol: LogProtocol) {
        protocol.flush()
    }
}