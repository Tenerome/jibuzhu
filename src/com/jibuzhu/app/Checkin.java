package com.jibuzhu.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/** WeLink 打卡：读当前 WiFi 名称、打开打卡页。 */
public final class Checkin {

    private static final String PKG = "com.huawei.works";
    private static final String ENTRY = "huawei.w3.ui.welcome.W3SplashScreenActivity";

    private Checkin() {
    }

    /**
     * 打开 WeLink 打卡页。走的是桌面「打卡」快捷方式那套 Intent，
     * 页面进去会自己打卡一次，所以这里只负责把它叫起来。
     */
    /** 打开打卡页的 Intent（通知点击也复用它） */
    public static Intent intent() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(PKG, ENTRY));
        i.setAction("com.huawei.works.action.shortcut");
        i.addCategory("android.shortcut.conversation");
        i.setData(Uri.parse("ui://welink.attendance/"));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    public static boolean open(Context c) {
        try {
            c.startActivity(intent());
            android.util.Log.d("JBZ-WIFI", "open checkin: startActivity ok");
            return true;
        } catch (Exception e) {
            android.util.Log.d("JBZ-WIFI", "open checkin FAILED " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return false;
        }
    }

    /** 当前连着的 WiFi 名称；没连或读不到返回空串。SSID 两侧的双引号已去掉。 */
    public static String currentSsid(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) {
                android.util.Log.d("JBZ-WIFI", "connectionInfo=null");
                return "";
            }
            android.util.Log.d("JBZ-WIFI", "raw=[" + info.getSSID() + "] netId="
                    + info.getNetworkId() + " state=" + info.getSupplicantState());
            String s = clean(info.getSSID());
            if (s.isEmpty()) {
                // 定位权限有、开关也开，仍然读不到时，退一步用网络列表里的名字
                s = byCapabilities(c);
            }
            return s;
        } catch (Exception e) {
            android.util.Log.d("JBZ-WIFI", "ssid err " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "";
        }
    }

    /** getConnectionInfo 拿不到 SSID 时的退路：从正在连着的网络能力里取 */
    private static String byCapabilities(Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "";
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) return "";
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null || !nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return "";
            android.net.TransportInfo ti = nc.getTransportInfo();
            if (ti instanceof WifiInfo) {
                String v = clean(((WifiInfo) ti).getSSID());
                android.util.Log.d("JBZ-WIFI", "capabilities ssid=[" + ((WifiInfo) ti).getSSID() + "]");
                return v;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** 去掉系统给 SSID 加的双引号，以及 <unknown ssid> 这种占位值 */
    public static String clean(String ssid) {
        if (ssid == null) return "";
        String s = ssid.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.startsWith("<") && s.endsWith(">")) return "";
        return s;
    }

    /** 定位开关关着时读不到 SSID，这里给个提示用的判断 */
    public static boolean locationOn(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }
}
