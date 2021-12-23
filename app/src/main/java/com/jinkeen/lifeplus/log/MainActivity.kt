package com.jinkeen.lifeplus.log

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.jinkeen.lifeplus.log.nativ.LogConfig
import java.io.File
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
            timer = fixedRateTimer("log", false, 0, 30 * 1000L) { execute() }
        }
    }

    private var timer: Timer? = null

    private var num = 1

    private fun execute() {
        JKLog.w(999, "Log content - $num")
        num++
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        JKLog.f()
    }
}