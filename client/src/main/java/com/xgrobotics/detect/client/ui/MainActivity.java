package com.xgrobotics.detect.client.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.View;

import com.xgrobotics.detect.client.R;
import com.xgrobotics.detect.client.VideoStream;
import com.xgrobotics.detect.lib.DetectConst;

import java.io.IOException;

/**
 * Created by Stefan on 2018/10/15.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_2);
        parseIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        parseIntent(intent);
    }

    private void parseIntent(Intent intent) {
        String ip = intent.getStringExtra("ip");
//        int port = intent.getIntExtra("port", 0);
        startStream(ip);
    }

    private void startStream(String ip) {
//        VideoStream vs = new VideoStream(0, "10.58.98.68", 8086);
        VideoStream vs = new VideoStream(0, ip, DetectConst.STREAM_PORT);
        vs.setSurfaceView((SurfaceView) findViewById(R.id.surfaceView));
//        try {
//            vs.encodeWithMediaCodec();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }
}
