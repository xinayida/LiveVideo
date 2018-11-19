package com.xgrobotics.detect.client;

import android.media.MediaCodec;
import android.util.Log;

import com.xgrobotics.detect.lib.DetectConst;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;


/**
 * Created by Stefan on 2018/10/11.
 */
public class Sender {
    private String ip;
    private int port;
    private Socket mSocket;
    private DataOutputStream dos = null;

    public Sender(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void init() {
        try {
            mSocket = new Socket(ip, port);
            dos = new DataOutputStream(mSocket.getOutputStream());
        } catch (IOException e) {
            Log.d("Stefan", "socket connect error: " + ip + ":" + port + "  " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void release() {
        Log.d("Stefan", "sender release");
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] data, int offset, int len) throws IOException {
        if (dos != null)
            dos.write(data, offset, len);
    }

    public void writeInt(int data) throws IOException {
        if (dos != null)
            dos.writeInt(data);
    }

    public void writeLong(long data) throws IOException {
        if (dos != null)
            dos.writeLong(data);
    }
}
