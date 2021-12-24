package com.jinkeen.lifeplus.log

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.jinkeen.lifeplus.log.nativ.LogConfig
import java.io.File
import java.lang.NullPointerException
import java.util.*
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        JKLog.init(
            LogConfig(
                filesDir.absolutePath,
                "${getExternalFilesDir(null)?.absolutePath}${File.separator}logan",
                "0123456789012345".toByteArray(),
                "0123456789012345".toByteArray()
            )
        )

        findViewById<Button>(R.id.write).setOnClickListener {
            timer = fixedRateTimer("log", false, 0, 1000L) { execute() }
        }
    }

    private var timer: Timer? = null

    private var num = 1

    private fun execute() {
        JKLog.w(101, "Log content-$num")
        if (num % 5 == 0) JKLog.e(102, "强制空指针-$num", NullPointerException("强制空指针"))
        num++
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        JKLog.quit(true)
    }
}