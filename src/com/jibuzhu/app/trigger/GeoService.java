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
    /** 最近一次定位到最近目标待办的距离（米） */
    private double lastBest = Double.MAX_VALUE;
    private Runnable tick;

    /** 最近一次定位结果，列表里显示距离要用，和判定共用同一次刷新 */
    public static volatile Location lastLoc;

    /** 刚唤起过提醒的待办，避免短时间内重复弹出（不落库，重启即清空） */
    private final java.util.Map<Long, Long> triedAt = new java.util.HashMap<Long, Long>();

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
            // 不能直接 stopSelf —— 待办的时间段还没到的时候服务就该守着了。
            // 否则「设好待办 → 锁屏 → 到点」这条链上没人把定位打开，
            // 表现就是锁屏后状态栏一直没有定位标志，解锁那一瞬间才出现。
            stopUpdates();
            if (!hasAnyLocationTodo()) stopSelf();
            return;
        }
        long want = intervalFor(active);
        // 间隔稍微变一点就重设，会让 GPS 反复重新搜星，反而更慢。
        // 只有明显变化（超出 ±25%）才重设。
        if (interval < 0 || want < interval * 0.75 || want > interval * 1.25) apply(want);
    }

    /** 还有没有任何「启用的」定位/打卡待办（不管在不在时间段内） */
    private boolean hasAnyLocationTodo() {
        try {
            for (Todo t : Store.get(this).todos()) {
                if (t.enabled && (t.isLocation() || t.isCheckin())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 当前真正需要关注的定位待办（启用 / 今天该响 / 在时间段内 / 今天还没响过） */
    private List<Todo> activeLocations() {
        List<Todo> out = new ArrayList<Todo>();
        Calendar now = Calendar.getInstance();
        String today = Util.today();
        List<Todo> all = Store.get(this).todos();
        for (Todo t : all) {
            String why = null;
            if (!t.enabled) why = "disabled";
            else if (!t.isLocation() && !t.isCheckin()) why = "not-loc";
            else if (!Util.dayMatches(t.rep, now)) why = "day";
            else if (!Util.inWindow(t.winStart, t.winEnd, now)) why = "window "
                    + t.winStart + "-" + t.winEnd;
            else if (today.equals(t.lastFired)) why = "fired";
            if (why != null) {
                android.util.Log.d("JBZ-GEO", "skip id=" + t.id + " " + why);
                continue;
            }
            out.add(t);
        }
        android.util.Log.d("JBZ-GEO", "active=" + out.size() + " of " + all.size());
        return out;
    }

    /** 距离越近，要得越勤；最近的那个距离顺手记下来（决定用 GPS 还是网络定位） */
    private long intervalFor(List<Todo> list) {
        if (last == null) {
            lastBest = Double.MAX_VALUE;
            return 60_000L;
        }
        double min = Double.MAX_VALUE;
        for (Todo t : list) {
            double d = Util.distance(last.getLatitude(), last.getLongitude(), t.lat, t.lng);
            if (d < min) min = d;
        }
        lastBest = min;
        if (min > 10_000) return 30_000L;   // 10 公里外：网络定位，30 秒一次
        if (min > 5_000) return 15_000L;    // 5-10 公里：网络定位，15 秒一次
        if (min > 3_000) return 10_000L;    // 3-5 公里：GPS，10 秒一次
        return 3_000L;                      // 3 公里内：GPS，3 秒一次
    }

    private void apply(long iv) {
        try {
            if (lm == null) return;
            if (interval > 0) lm.removeUpdates(this);
            interval = -1;
            boolean any = false;
            // 近处用 GPS 精确定位（网络定位实测精度 500 米上下，拿它判 300 米围栏
            // 永远命中不了，而且偏差大时算出的距离能差出几公里）。
            if (useGps() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, iv, 30f, this, ui.getLooper());
                any = true;
            }
            // 网络定位始终挂一份兜底：室内 GPS 没信号时至少还有位置更新。
            // 注意不能写死 5 分钟 —— 10 公里外本来就没开 GPS，全靠网络定位，
            // 那样就变成 5 分钟才动一次了。
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        useGps() ? Math.max(iv, 60_000L) : iv, 200f, this, ui.getLooper());
                any = true;
            }
            if (any) interval = iv;
            android.util.Log.d("JBZ-GEO", "apply iv=" + iv + " gps=" + useGps()
                    + " best=" + ((int) lastBest));
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * 要不要开 GPS。
     * 判断时把上一次定位的误差扣掉 —— 网络定位说「还有 6 公里」，但它自己就标着
     * 500 米甚至更大的误差，很可能人已经在楼下了。
     */
    private boolean useGps() {
        if (last == null) return true;   // 头一次先把位置定准
        float acc = last.hasAccuracy() ? last.getAccuracy() : 500f;
        // 10 公里内就开 GPS：贴近时才开的话，GPS 冷启动要十几秒，
        // 这段时间判定是空白的，表现出来就是"到了地方要愣一会儿才提醒"。
        return (lastBest - acc) <= 10_000;
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
        lastLoc = loc;
        List<Todo> list = activeLocations();
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 100f;
        for (Todo t : list) {
            double d = Util.distance(loc.getLatitude(), loc.getLongitude(), t.lat, t.lng);
            // 定位本身有误差（网络定位常在数百米），判定半径按误差放宽一点，
            // 否则精度差的时候围栏永远命中不了。
            double tol = Math.max(50, t.radius) + Math.min(acc, 150f);
            android.util.Log.d("JBZ-GEO", "todo=" + t.id + " d=" + ((int) d) + " acc=" + ((int) acc)
                    + " tol=" + ((int) tol) + " checkin=" + t.isCheckin());
            if (d <= tol) {
                // 不在这里写 lastFired：唤起提醒可能失败（权限/后台限制），
                // 写了当天就彻底不提醒了。只记一个内存里的"刚试过"，2 分钟内不重复唤起。
                long nowMs = System.currentTimeMillis();
                Long tried = triedAt.get(t.id);
                if (tried != null && nowMs - tried < 120_000L) {
                    android.util.Log.d("JBZ-GEO", "hit but tried recently id=" + t.id);
                    continue;
                }
                triedAt.put(t.id, nowMs);
                android.util.Log.d("JBZ-GEO", "HIT id=" + t.id + " d=" + ((int) d)
                        + " checkin=" + t.isCheckin() + " locked=" + locked());
                if (t.isCheckin()) {
                    // 打卡：锁屏就走闹钟式提醒（点「去打卡」后跳 WeLink）；
                    // 没锁屏直接跳过去，不响铃。
                    if (locked()) {
                        AlarmScheduler.fireSoon(this, t, "geo");
                    } else {
                        Checkin.open(this);
                    }
                } else {
                    AlarmScheduler.fireSoon(this, t, "geo");
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
