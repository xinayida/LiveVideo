package com.xgrobotics.detect.server.detect;

import com.xgrobotics.detect.lib.DetectConst;
import com.xgrobotics.detect.lib.DetectListener;
import com.xgrobotics.detect.lib.DetectPack;
import com.xgrobotics.detect.lib.DetectUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import static com.xgrobotics.detect.lib.DetectConst.INT_LEN;
import static com.xgrobotics.detect.lib.DetectConst.MAX_DATA_LEN;

/**
 * Created by Stefan on 2018/10/18.
 */
public class DetectServer implements Runnable {

    private MulticastSocket multicastSocket;
    private InetAddress inetAddress;
    private boolean isRunning;
    private Thread thread;
    private DetectListener detectListener;
    private String name;

    public DetectServer(DetectListener listener, String serverName) {
        detectListener = listener;
        name = serverName;
    }

    public void start() {
        try {
            multicastSocket = new MulticastSocket(DetectConst.S_PORT);
            inetAddress = InetAddress.getByName(DetectConst.MULTICAST_IP);
            multicastSocket.joinGroup(inetAddress);
//            multicastSocket.setLoopbackMode(false);
            multicastSocket.setTimeToLive(10);
            isRunning = true;
            thread = new Thread(this);
            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
            stop();
        }
    }

    public void setServerName(String serverName) {
        name = serverName;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stop() {
        isRunning = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (multicastSocket != null) {
            try {
                multicastSocket.leaveGroup(inetAddress);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                multicastSocket.close();
                multicastSocket = null;
            }
        }
    }

    @Override
    public void run() {
        byte[] buf = new byte[MAX_DATA_LEN];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        if (multicastSocket == null || multicastSocket.isClosed()) {
            return;
        }
        try {
            while (isRunning && !Thread.interrupted()) {
                multicastSocket.receive(packet);
                DetectPack reqPacket = null;
                try {
                    reqPacket = DetectUtils.parsePacket(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (reqPacket != null && detectListener != null) {
                    detectListener.onDetect(reqPacket);
                    if (reqPacket.type == DetectConst.PACKET_TYPE_FIND_DEVICE_REQ) {//搜索请求
                        byte[] resp = DetectUtils.buildPacket(reqPacket.seq, DetectConst.PACKET_TYPE_FIND_DEVICE_RSP, name);
                        multicastSocket.send(new DatagramPacket(resp, resp.length, packet.getAddress(), packet.getPort()));
                    }
//                    else if (reqPacket.type == DetectConst.PACKET_TYPE_PLAY_REQ) {//播放请求
//                        byte[] resp = DetectUtils.buildPacket(reqPacket.seq, DetectConst.PACKET_TYPE_PLAY_RSP, name);
//                        multicastSocket.send(new DatagramPacket(resp, resp.length, packet.getAddress(), packet.getPort()));
//                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
