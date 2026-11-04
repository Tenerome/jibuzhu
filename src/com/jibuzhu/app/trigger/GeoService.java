package com.jibuzhu.app.trigger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.jibuzhu.app.Checkin;
import com.jibuzhu.app.MainActivity;
import com.jibuzhu.app.Util;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 定位提醒服务。
 *
 * 省电策略：
 * 1. 只有「存在处于提醒时间段内的定位待办」时才请求位置，否则完全停掉；
 * 2. 位移小于 100 米不回调 —— 人没动就不定位；
 * 3. 用网络定位判定远近（300 米量级够用），按距离动态调整请求频率：
 *    >10km 5 分钟 / 5-10km 1 分钟 / 3-5km 30 秒 / <3km 10 秒。
 */
public class GeoService extends Service implements LocationListener {

    public static final String CHANNEL = "idle_v1";
    private static final int NID = 42;

    /** 位移小于这个距离就不回调，静止时完全不产生定位 */
    private static final float MIN_MOVE = 100f;
    /** 多久重新评估一次「有没有待办在时间段内」 */
    private static final long CHECK_EVERY = 60_000L;

    private LocationManager lm;
    private Handler ui;
    private Location last;
    /** 当前生效的请求间隔；-1 表示没在请求 */
    private long interval = -1;
    private Runnable tick;

    @Override
    public void onCreate() {
        super.onCreate();
        ui = new Handler(Looper.getMainLooper());
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();
        scheduleTick(0);
        return START_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "定位提醒", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
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

    private void scheduleTick(long delay) {
        if (tick == null) {
            tick = new Runnable() {
                @Override
                public void run() {
                    sync();
                    if (ui != null) ui.postDelayed(this, CHECK_EVERY);
                }
            };
        }
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, delay);
    }

    /** 决定现在要不要定位、以及多久定位一次 */
    private void sync() {
        List<Todo> active = activeLocations();
        if (active.isEmpty()) {
            stopUpdates();
            stopSelf();
            return;
        }
        long want = intervalFor(active);
        if (want != interval) apply(want);
    }

    /** 当前真正需要关注的定位待办（启用 / 今天该响 / 在时间段内 / 今天还没响过） */
    private List<Todo> activeLocations() {
        List<Todo> out = new ArrayList<Todo>();
        Calendar now = Calendar.getInstance();
        String today = Util.today();
        List<Todo> all = Store.get(this).todos();
        for (Todo t : all) {
            if (!t.enabled) continue;
            // 打卡型也是靠位置触发的，一起纳入
            if (!t.isLocation() && !t.isCheckin()) continue;
            if (!Util.dayMatches(t.rep, now)) continue;
            if (!Util.inWindow(t.winStart, t.winEnd, now)) continue;
            if (today.equals(t.lastFired)) continue;
            out.add(t);
        }
        return out;
    }

    /** 距离越近，要得越勤 */
    private long intervalFor(List<Todo> list) {
        if (last == null) return 60_000L;
        double min = Double.MAX_VALUE;
        for (Todo t : list) {
            double d = Util.distance(last.getLatitude(), last.getLongitude(), t.lat, t.lng);
            if (d < min) min = d;
        }
        if (min > 10_000) return 300_000L;
        if (min > 5_000) return 60_000L;
        if (min > 3_000) return 30_000L;
        return 10_000L;
    }

    private void apply(long iv) {
        try {
            if (lm == null) return;
            if (interval > 0) lm.removeUpdates(this);
            interval = -1;
            String provider = LocationManager.NETWORK_PROVIDER;
            if (!lm.isProviderEnabled(provider)) {
                provider = LocationManager.GPS_PROVIDER;
                if (!lm.isProviderEnabled(provider)) return;
            }
            lm.requestLocationUpdates(provider, iv, MIN_MOVE, this, ui.getLooper());
            interval = iv;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void stopUpdates() {
        try {
            if (lm != null && interval > 0) lm.removeUpdates(this);
        } catch (Exception ignored) {
        }
        interval = -1;
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (loc == null) return;
        last = loc;
        List<Todo> list = activeLocations();
        for (Todo t : list) {
            double d = Util.distance(loc.getLatitude(), loc.getLongitude(), t.lat, t.lng);
            if (d <= Math.max(50, t.radius)) {
                t.lastFired = Util.today();
                Store.get(this).saveTodo(t);
                if (t.isCheckin()) {
                    // 打卡：锁屏就走闹钟式提醒（点「去打卡」后跳 WeLink）；
                    // 没锁屏直接跳过去，不响铃。
                    if (locked()) {
                        AlarmReceiver.showFullScreen(this, t, "geo");
                    } else {
                        Checkin.open(this);
                    }
                } else {
                    AlarmReceiver.showFullScreen(this, t, "geo");
                }
            }
        }
        sync();
    }

    private boolean locked() {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager)
                    getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onProviderEnabled(String p) {
    }

    @Override
    public void onProviderDisabled(String p) {
    }

    @Override
    public void onStatusChanged(String p, int s, Bundle b) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopUpdates();
        if (ui != null && tick != null) ui.removeCallbacks(tick);
        super.onDestroy();
    }
}
