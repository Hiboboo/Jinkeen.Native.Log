@file:JvmName("LogUtils")

package com.jinkeen.lifeplus.log.util

import android.os.StatFs
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "LogUtils"

private val sDateFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)

/**
 * 获取当前日期（`yyyy-MM-dd`）的13位时间戳表示形式
 *
 * @return 返回系统当前的日期时间戳，若出现异常返回`0`
 */
fun getCurrentDateTimemillis(): Long = sDateFormat.parse(sDateFormat.format(Date()))?.time ?: 0L

/**
 * 检查`SDCard`中目标文件的现有总容量是否还允许被写入目标容量的数据
 *
 * @param path 要被检查的目标文件路径
 * @param capacity 要被写入的目标数据的容量
 * @return `true`表示允许，否则返回`false`
 */
fun isCanWriteSDCard(path: String, capacity: Long): Boolean = try {
    val stat = StatFs(path)
    val total = stat.blockSizeLong * stat.availableBlocksLong
    total > capacity
} catch (e: IllegalArgumentException) {
    Log.e(TAG, "检查SD卡可写入容量出现异常", e)
    false
}