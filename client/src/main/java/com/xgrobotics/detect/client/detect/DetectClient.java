package com.xgrobotics.detect.client.detect;

import android.widget.Toast;

import com.xgrobotics.detect.lib.DetectConst;
import com.xgrobotics.detect.lib.DetectListener;
import com.xgrobotics.detect.lib.DetectPack;
import com.xgrobotics.detect.lib.DetectUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Timer;
import java.util.TimerTask;

import static com.xgrobotics.detect.lib.DetectConst.INT_LEN;
import static com.xgrobotics.detect.lib.DetectConst.MAX_DATA_LEN;


/**
 * Created by Stefan on 2018/10/18.
 */
public class DetectClient {

    private boolean isRunning;
    private MulticastSocket socket;
    private InetAddress inetAddress;
    private Timer timer;
    private DatagramPacket sendPack;
    private int seq = 0;
    private Thread receiveThread;
    private String name = "NO BODY";
    private DetectListener detectListener;

    public DetectClient(DetectListener listener, String clientName) {
        detectListener = listener;
        name = clientName;
    }


    public boolean isRunning() {
        return isRunning;
    }

    public void start() throws IOException {
        socket = new MulticastSocket(DetectConst.C_PORT);
        inetAddress = InetAddress.getByName(DetectConst.MULTICAST_IP);
        socket.joinGroup(inetAddress);
        socket.setLoopbackMode(false);
        socket.setTimeToLive(10);
        sendPack = new DatagramPacket(new byte[MAX_DATA_LEN], MAX_DATA_LEN, inetAddress, DetectConst.S_PORT);

        isRunning = true;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (isRunning) {
                        sendBroadCast();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 5000);
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    receive();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        receiveThread.start();
    }

    public void stop() {
        isRunning = false;
        timer.cancel();
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        if (socket != null) {
            try {
                socket.leaveGroup(inetAddress);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                socket.close();
                socket = null;
            }
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    private void sendBroadCast() throws IOException {
        sendPack.setData(DetectUtils.buildPacket(seq++, DetectConst.PACKET_TYPE_FIND_DEVICE_REQ, name));
        socket.send(sendPack);
    }

//    public void sendPlay(final String ip) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                sendPack.setData(DetectUtils.buildPacket(seq++, DetectConst.PACKET_TYPE_PLAY_REQ, ip));
//                try {
//                    socket.send(sendPack);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                stop();
//            }
//        }).start();
//    }

    private void receive() throws IOException {
        if (socket == null || socket.isClosed()) {
            return;
        }
        byte[] recData = new byte[MAX_DATA_LEN];
        DatagramPacket packet = new DatagramPacket(recData, MAX_DATA_LEN);
        while (isRunning && !receiveThread.isInterrupted()) {
            packet.setData(recData);
            socket.receive(packet);
            if (packet.getLength() > 0) {
                DetectPack dp = null;
                try {
                    dp = DetectUtils.parsePacket(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dp != null && detectListener != null) {
                    detectListener.onDetect(dp);
                }
            }
        }

    }
}
