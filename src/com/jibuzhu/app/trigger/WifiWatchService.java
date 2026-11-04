package com.jibuzhu.app.trigger;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.jibuzhu.app.Checkin;
import com.jibuzhu.app.MainActivity;
import com.jibuzhu.app.Util;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;

import java.util.Calendar;
import java.util.List;

/**
 * 打卡监听服务。
 *
 * 连上（或断开）指定 WiFi 时：
 * - 屏幕解锁：直接打开 WeLink 打卡页；
 * - 锁屏：像闹钟一样弹提醒页并响铃，点「去打卡」解锁后再跳转。
 *
 * 「断开」要延迟 3 秒确认，避免网络重建时误判成下班打卡。
 */
public class WifiWatchService extends Service {

    private static final String CHANNEL = GeoService.CHANNEL;
    private static final int NID = 42;
    /** 同一个 WiFi 两次触发之间的最小间隔 */
    private static final long DEBOUNCE_MS = 30_000L;
    /** 断开后等这么久再判定，期间重连就当没断过 */
    private static final long OFF_DELAY_MS = 3_000L;

    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Handler ui;
    private Runnable pendingOff;
    private long lastFire;

    @Override
    public void onCreate() {
        super.onCreate();
        ui = new Handler(Looper.getMainLooper());
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();
        watch();
        // 启动时不触发，只记下当前连的是哪个网，避免刚进 App 就弹打卡
        String now = Checkin.currentSsid(this);
        if (!now.isEmpty()) save(now);
        android.util.Log.d("JBZ-WIFI", "startup ssid=" + now + " (no trigger)");
        return START_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "定位提醒", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);

        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("记不住")
                .setContentText("超低功耗待机~")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NID, n);
        }
    }

    private void watch() {
        if (callback != null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                String s = ssidOf(network);
                android.util.Log.d("JBZ-WIFI", "onAvailable ssid=" + s);
                if (s.isEmpty()) return;
                if (s.equals(saved())) {
                    // 注册时系统会对「已经连着的网」补发一次 onAvailable，这不是新连接
                    android.util.Log.d("JBZ-WIFI", "same as saved, skip");
                    return;
                }
                save(s);
                if (pendingOff != null) {
                    ui.removeCallbacks(pendingOff);
                    pendingOff = null;
                }
                check(s, true);
            }

            @Override
            public void onLost(Network network) {
                final String gone = saved();
                android.util.Log.d("JBZ-WIFI", "onLost last=" + gone);
                if (gone.isEmpty()) return;
                if (pendingOff != null) ui.removeCallbacks(pendingOff);
                pendingOff = new Runnable() {
                    @Override
                    public void run() {
                        pendingOff = null;
                        // 3 秒内没重连才算真的断开
                        if (!gone.equals(saved())) return;
                        clear();
                        check(gone, false);
                    }
                };
                ui.postDelayed(pendingOff, OFF_DELAY_MS);
            }
        };
        try {
            NetworkRequest req = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            cm.registerNetworkCallback(req, callback);
        } catch (Exception e) {
            android.util.Log.d("JBZ-WIFI", "register failed " + e);
        }
    }

    private String ssidOf(Network n) {
        try {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null) {
                TransportInfo ti = nc.getTransportInfo();
                if (ti instanceof WifiInfo) {
                    String s = Checkin.clean(((WifiInfo) ti).getSSID());
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Exception ignored) {
        }
        return Checkin.currentSsid(this);
    }

    /** 连上 / 断开某个 WiFi 时，看有没有待办要处理 */
    private void check(String ssid, boolean connected) {
        if (ssid == null || ssid.isEmpty()) return;
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastFire < DEBOUNCE_MS) return;

        Calendar now = Calendar.getInstance();
        List<Todo> list = Store.get(this).todos();
        for (Todo t : list) {
            if (!t.enabled || !t.isCheckin()) continue;
            if (t.wifi == null || !t.wifi.equalsIgnoreCase(ssid)) continue;
            if (!Util.dayMatches(t.rep, now)) continue;
            if (!Util.inWindow(t.winStart, t.winEnd, now)) continue;
            if ("once".equals(t.rep) && Util.today().equals(t.lastFired)) continue;

            lastFire = nowMs;
            t.lastFired = Util.today();
            Store.get(this).saveTodo(t);
            android.util.Log.d("JBZ-WIFI", "hit todo=" + t.title + " connected=" + connected);

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km != null && km.isKeyguardLocked();
            if (locked) {
                // 锁屏：走和闹钟一样的提醒页 + 铃声，点「去打卡」解锁后再跳
                AlarmReceiver.showFullScreen(this, t, "checkin");
            } else {
                Checkin.open(this);
            }
            return;
        }
    }

    private SharedPreferences sp() {
        return getSharedPreferences("wifi", MODE_PRIVATE);
    }

    private String saved() {
        return sp().getString("last_ssid", "");
    }

    private void save(String ssid) {
        sp().edit().putString("last_ssid", ssid).apply();
    }

    private void clear() {
        sp().edit().remove("last_ssid").apply();
    }

    @Override
    public void onDestroy() {
        try {
            if (callback != null) cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {
        }
        callback = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
