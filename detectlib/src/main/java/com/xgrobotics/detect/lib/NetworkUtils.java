package com.xgrobotics.detect.lib;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

/**
 * Created by Stefan on 2018/10/18.
 */
public class NetworkUtils {
    /**
     * 没有网络连接
     */
    public static final int NETWORK_TYPE_NONE = 0;
    /**
     * wifi
     */
    public static final int NETWORK_TYPE_WIFI = 1;
    /**
     * 2G网络连接
     */
    public static final int NETWORK_TYPE_2G = 2;
    /**
     * 3G网络连接
     */
    public static final int NETWORK_TYPE_3G = 3;
    /**
     * 4G网络连接
     */
    public static final int NETWORK_TYPE_4G = 4;
    /**
     * 未能判断网络类型，做移动网络处理
     */
    public static final int NETWORK_TYPE_MOBILE = 5;

    /**
     * 获取网络类型
     *
     * @return 返回值，参考NETWORK_TYPE_NONE;NETWORK_TYPE_WIFI;NETWORK_TYPE_2G;
     * NETWORK_TYPE_3G;
     * NETWORK_TYPE_4G;NETWORK_TYPE_MOBILE
     */
    public static int getNetworkType(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isAvailable()) {
            if (ConnectivityManager.TYPE_WIFI == networkInfo.getType()) {
                return NETWORK_TYPE_WIFI;
            } else {
                TelephonyManager telephonyManager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                int networkType = telephonyManager.getNetworkType();
                switch (networkType) {
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return NETWORK_TYPE_2G;
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        return NETWORK_TYPE_3G;
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        return NETWORK_TYPE_4G;
                    default:
                        return NETWORK_TYPE_MOBILE;
                }
            }
        } else {
            return NETWORK_TYPE_NONE;
        }
    }
}
