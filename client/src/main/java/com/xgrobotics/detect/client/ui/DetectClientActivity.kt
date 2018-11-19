package com.xgrobotics.detect.client.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import com.xgrobotics.detect.client.R
import com.xgrobotics.detect.client.detect.DetectClient
import com.xgrobotics.detect.lib.DetectConst
import com.xgrobotics.detect.lib.DetectListener
import com.xgrobotics.detect.lib.DetectPack
import com.xgrobotics.detect.lib.DetectUtils
import kotlinx.android.synthetic.main.activity_detect.*
import java.util.*

/**
 * Created by Stefan on 2018/10/22.
 */
class DetectClientActivity : AppCompatActivity(), DetectListener {
    override fun onDetect(pack: DetectPack) {
        runOnUiThread {
            if (pack.type == DetectConst.PACKET_TYPE_FIND_DEVICE_RSP) {
                adapter.add(pack)
            }
//            else if(pack.type == DetectConst.PACKET_TYPE_PLAY_RSP){
//            }
        }
    }

    private lateinit var client: DetectClient
    private lateinit var adapter: DetectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
            }
        }

        adapter = DetectAdapter(this)
        detect_list.adapter = adapter
        detect_list.setOnItemClickListener { parent, view, position, id ->
            val pack = adapter.getItem(position)
//            client.sendPlay(pack.ip)
            val intent = Intent(this@DetectClientActivity, MainActivity::class.java)
            intent.putExtra("ip", pack.ip)
            startActivity(intent)
            stop()
        }

        client = DetectClient(this, "NO.1")
        search_btn.setOnClickListener {
            if (client.isRunning) {
                stop()
            } else {
                search_btn.setText("结束搜索")
                try {
                    client.start()
                } catch (e: Exception) {
                    Toast.makeText(this, "启动失败${e.message}", Toast.LENGTH_SHORT).show()
                }

            }
        }
        clientName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                client.setName(s.toString())
            }

        })
    }

    private fun stop() {
        search_btn.text = "开始搜索"
        adapter.clear()
        client.stop()
    }

    class DetectAdapter : BaseAdapter {
        private val context: Context
        private val inflater: LayoutInflater

        constructor(context: Context) : super() {
            this.context = context
            inflater = LayoutInflater.from(context)
            this.mDeviceList = ArrayList()
        }

        private val mDeviceList: ArrayList<DetectPack>
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var holder: ViewHolder
            var v: View?
            if (convertView == null) {
                v = inflater.inflate(R.layout.item_device_list, parent, false)
                holder = ViewHolder()
                holder.nameTv = v.findViewById(R.id.textName)
                holder.ipTv = v.findViewById(R.id.textIp)
                v.tag = holder
            } else {
                holder = convertView.tag as ViewHolder
                v = convertView
            }
            val pack = mDeviceList[position]
            holder.nameTv?.text = pack.name
            holder.ipTv?.text = "${pack.ip}:${pack.port}"
            return v!!
        }

        override fun getItem(position: Int): DetectPack {
            return mDeviceList.get(position)
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return mDeviceList.size
        }

        fun clear() {
            mDeviceList.clear()
            notifyDataSetChanged()
        }

        fun add(pack: DetectPack) {
            val iter = mDeviceList.iterator()
            while (iter.hasNext()) {
                val pack = iter.next()
                if (!DetectUtils.isPacketLive(pack)) {
                    iter.remove()
                }
            }
            if (!mDeviceList.contains(pack)) {
                mDeviceList.add(pack)
                notifyDataSetChanged()
            }
        }
    }


    private class ViewHolder {
        var nameTv: TextView? = null
        var ipTv: TextView? = null
    }
}
