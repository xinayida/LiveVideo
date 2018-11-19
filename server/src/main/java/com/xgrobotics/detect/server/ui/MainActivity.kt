package com.xgrobotics.detect.server.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.xgrobotics.detect.lib.DetectConst
import com.xgrobotics.detect.server.R
import com.xgrobotics.detect.server.Receiver
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main_h264.*

class MainActivity : AppCompatActivity() {

    private var receiver: Receiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (0 == DetectConst.TRANS_MODE) {
            setContentView(R.layout.activity_main_h264)
        } else if (1 == DetectConst.TRANS_MODE) {
            setContentView(R.layout.activity_main)
        }
        startServer()
    }

    private fun startServer() {
        if (receiver == null) {
            if (0 == DetectConst.TRANS_MODE) {
                receiver = Receiver(surface_h264)
            } else {
                receiver = Receiver(surface)
            }
            receiver!!.start()
        } else {
            receiver!!.stop()
            receiver = null
        }
    }

    override fun onPause() {
        super.onPause()
        receiver?.stop()
    }
}
