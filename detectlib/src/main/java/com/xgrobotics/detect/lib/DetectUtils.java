package com.xgrobotics.detect.lib;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import static com.xgrobotics.detect.lib.DetectConst.INT_LEN;

/**
 * Created by Stefan on 2018/10/18.
 */
public class DetectUtils {

    public static int bytesToInt(byte[] src, int offset) {
        if (src == null || src.length < offset || offset + 4 > src.length) {
            return -1;
        }
        return src[offset] & 0xFF |
                (src[offset + 1] & 0xFF) << 8 |
                (src[offset + 2] & 0xFF) << 16 |
                (src[offset + 3] & 0xFF) << 24;
    }

    public static byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) (value & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[3] = (byte) ((value >> 24) & 0xFF);
        return src;
    }

    public static byte[] buildPacket(int seq, byte packType, String name) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeByte(DetectConst.PACKET_PREFIX);
            dos.writeByte(packType);
            dos.writeInt(seq);
            dos.writeUTF(name);//搜索请求时为名称，播放请求时为链接服务端的IP
        } catch (IOException e) {
            e.printStackTrace();
        }

        return baos.toByteArray();
    }

    public static boolean isPacketLive(DetectPack pack) {
        return (System.currentTimeMillis() - pack.initTS) < DetectConst.PACKET_LIVE_TIME;
    }

    public static DetectPack parsePacket(DatagramPacket packet) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength()));
        if (dis.readByte() == DetectConst.PACKET_PREFIX) {
            DetectPack detectPack = new DetectPack();
            detectPack.type = dis.readByte();
            detectPack.seq = dis.readInt();
            detectPack.name = dis.readUTF();//搜索请求时为名称，播放请求时为链接服务端的IP
            detectPack.port = packet.getPort();
            detectPack.ip = packet.getAddress().getHostAddress();
            return detectPack;
        }
        return null;
    }

    public static String getLocalIP(Context context) {

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
