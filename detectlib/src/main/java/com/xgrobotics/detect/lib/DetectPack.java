package com.xgrobotics.detect.lib;

/**
 * Created by Stefan on 2018/10/18.
 */
public class DetectPack {
    public byte type;//消息类型
    public String name;//自定义设备名称
    public String ip;
    public int port;
    public int seq;//消息序列号

    long initTS;//初始时间戳

    public DetectPack() {
        initTS = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DetectPack) {
            DetectPack other = (DetectPack) obj;
            return ip.equals(other.ip) && port == other.port;
        }

        return false;
    }
}
