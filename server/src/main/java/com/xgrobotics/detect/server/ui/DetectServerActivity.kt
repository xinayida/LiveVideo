package com.xgrobotics.detect.server.ui

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.xgrobotics.detect.lib.DetectConst
import com.xgrobotics.detect.lib.DetectListener
import com.xgrobotics.detect.lib.DetectPack
import com.xgrobotics.detect.server.R
import com.xgrobotics.detect.server.detect.DetectServer
import kotlinx.android.synthetic.main.activity_detect.*

/**
 * Created by Stefan on 2018/10/18.
 */
class DetectServerActivity : AppCompatActivity(), DetectListener {
    override fun onDetect(pack: DetectPack) {

        if (pack.type == DetectConst.PACKET_TYPE_FIND_DEVICE_REQ) {
            runOnUiThread {
                runOnUiThread {
                    stop()
                    val intent = Intent(this@DetectServerActivity, MainActivity::class.java)
                    startActivity(intent)
                }
//                detectTxt.text = "${pack.name} is Connected, IP:${pack.ip} PORT:${pack.port} seq:${pack.seq}"
//                connectBtn.visibility = View.VISIBLE
            }
        }
//        else if (pack.type == DetectConst.PACKET_TYPE_PLAY_REQ) {
//            val localIP = DetectUtils.getLocalIP(this)
//            Log.d("Stefan", "local IP: $localIP remote: ${pack.name}")
//            if (pack.name != null && pack.name == localIP) {
//                runOnUiThread {
//                    stop()
//                    val intent = Intent(this@DetectServerActivity, MainActivity::class.java)
//                    startActivity(intent)
//                }
//            }
//        }
    }

    private fun stop() {
        server.stop()
//        connectBtn.visibility = View.GONE
        detectTxt.text = ""
        startDetectBtn.text = "Start"
    }

    lateinit var server: DetectServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect)

        server = DetectServer(this, serverName.editableText.toString())
        startDetectBtn.setOnClickListener {
            if (server.isRunning) {
                stop()
            } else {
                server.start()
                detectTxt.text = "搜索中..."
                startDetectBtn.text = "Stop"
            }
        }

        serverName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                server.setServerName(s?.toString())
            }
        })

//        connectBtn.setOnClickListener {
//            stop()
//            val intent = Intent(this@DetectServerActivity, MainActivity::class.java)
//            startActivity(intent)
//        }
    }
}
