package com.jibuzhu.app.trigger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import com.jibuzhu.app.RingActivity;
import com.jibuzhu.app.RingOverlay;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;

/**
 * 到点时在前台服务里循环播放铃声，不依赖界面是否被拉起。
 * 声音走 STREAM_ALARM，所以静音/震动下也会响，音量跟系统闹钟一致。
 * 待办里选过本地 mp3 就播那个，否则播系统闹钟音。
 */
public class RingService extends Service {

    public static final String CH = "ringing_v1";
    private MediaPlayer mp;
    private android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private int kickTries;
    private long kickId = -1;
    private android.os.PowerManager.WakeLock wake;

    /**
     * 息屏显示（AOD）下屏幕处于 doze，通知只能显示成一个小图标。
     * 这里用老式的 ACQUIRE_CAUSES_WAKEUP 亮屏唤醒强行把屏幕点亮，
     * 点亮之后全屏通知/提醒页才有机会显示出来。
     */
    private void wakeScreen() {
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (wake == null) {
                wake = pm.newWakeLock(android.os.PowerManager.FULL_WAKE_LOCK
                        | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | android.os.PowerManager.ON_AFTER_RELEASE, "jibuzhu:ring");
                wake.setReferenceCounted(false);
            }
            if (!wake.isHeld()) wake.acquire(60 * 1000L);
            android.util.Log.i("JBZ", "wake screen ok");
        } catch (Throwable e) {
            android.util.Log.w("JBZ", "wake screen failed: " + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "闹钟响铃", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("提醒响铃中")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(9001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(9001, n);
        }

        long id = intent == null ? -1 : intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1);
        if (mp == null) startRing(id);
        wakeScreen();

        // 界面交给系统的全屏通知（fullScreenIntent）：只有系统拉起的全屏页才能点亮
        // 熄屏/AOD 的屏幕，应用自己 startActivity 只会"启动了但屏幕不亮"。
        // 这里只做兜底：等几秒还没看到界面，再自己试着拉起一次。
        if (id > 0 && id != kickId) {
            kickId = id;
            kickTries = 0;
            ui.removeCallbacks(kick);
            ui.postDelayed(kick, 800);
        }
        return START_NOT_STICKY;
    }

    /** 锁屏/后台下系统可能吞掉一次 startActivity，这里隔几秒重试，直到提醒页真的在前台 */
    private final Runnable kick = new Runnable() {
        @Override
        public void run() {
            if (RingActivity.alive || kickId <= 0 || kickTries >= 10) return;
            kickTries++;
            try {
                Intent ri = new Intent(RingService.this, RingActivity.class)
                        .putExtra("todo_id", kickId)
                        .putExtra("mode", "main")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(ri);
            } catch (Exception ignored) {
            }
            ui.postDelayed(this, 3000);
        }
    };

    private void startRing(long todoId) {
        try {
            Uri uri = custom(todoId);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(this, uri);
            mp.setLooping(true);
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            // 自定义铃声打不开（文件被删/权限失效）就退回系统闹钟音
            mp = null;
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setDataSource(this, uri);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
            } catch (Exception ignored) {
                mp = null;
            }
        }
    }

    private Uri custom(long todoId) {
        try {
            if (todoId <= 0) return null;
            Todo t = Store.get(this).todo(todoId);
            if (t == null || t.ring == null || t.ring.isEmpty()) return null;
            return Uri.parse(t.ring);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(kick);
        kickId = -1;
        com.jibuzhu.app.RingOverlay.hide(this);
        try {
            if (wake != null && wake.isHeld()) wake.release();
        } catch (Throwable ignored) {
        }
        wake = null;
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception ignored) {
            }
            mp.release();
            mp = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
