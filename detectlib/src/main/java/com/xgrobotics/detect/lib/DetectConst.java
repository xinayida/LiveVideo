package com.xgrobotics.detect.lib;

/**
 * Created by Stefan on 2018/10/18.
 */
public interface DetectConst {
    byte PACKET_PREFIX = '$';//包前缀

    byte PACKET_TYPE_FIND_DEVICE_REQ = 0x01; // 搜索请求
    byte PACKET_TYPE_FIND_DEVICE_RSP = 0x02; // 搜索响应
//    byte PACKET_TYPE_PLAY_REQ = 0x03;//播放请求
//    byte PACKET_TYPE_PLAY_RSP = 0x04;//播放响应

    String MULTICAST_IP = "239.0.0.251";//组播地址
    int C_PORT = 7838;//client 组播端口
    int S_PORT = 7839;//server 组播端口
    int STREAM_PORT = 8086;//stream传输端口

    int MAX_DATA_LEN = 1024;//组播包大小

    int INT_LEN = 4;

    int PACKET_LIVE_TIME = 30000;//消息有效时间
}
