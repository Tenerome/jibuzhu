package com.jibuzhu.app.trigger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jibuzhu.app.RingActivity;
import com.jibuzhu.app.Util;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;

import java.util.Calendar;

/** 闹钟到点：发全屏通知把 RingActivity 顶到前台，并把下一天重排好。 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL = "ring_v2";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1);
        String mode = intent.getStringExtra(AlarmScheduler.EXTRA_MODE);
        if (mode == null) mode = "main";
        if (id <= 0) return;

        // 定位待办的时间段开始了：把定位服务唤起来，让它立刻开始定位。
        // 不能只靠服务自己的定时 —— 锁屏进 doze 之后 Handler 的定时会被冻住，
        // 时段到了没人重新评估，表现就是锁屏时状态栏一直没有定位标志。
        if ("geoStart".equals(mode)) {
            try {
                ctx.startForegroundService(new Intent(ctx, GeoService.class));
            } catch (Exception e) {
                android.util.Log.e("JBZ-GEO", "geoStart start fail: " + e);
            }
            Todo g = Store.get(ctx).todo(id);
            if (g != null) AlarmScheduler.schedule(ctx, g);   // 顺手排下一轮
            return;
        }

        Todo t = Store.get(ctx).todo(id);
        if (t == null || !t.enabled) return;

        Calendar now = Calendar.getInstance();
        if (!Util.dayMatches(t.rep, now)) {
            AlarmScheduler.schedule(ctx, t);
            return;
        }
        if ("once".equals(t.rep) && Util.today().equals(t.lastFired)) return;

        // 打卡不管是位置命中还是兜底时间催的，都算今天打过了：写 lastFired，
        // 今天不再监听，明天 0 点自动恢复。
        boolean mark = true;
        if (mark) {
            t.lastFired = Util.today();
            Store.get(ctx).saveTodo(t);
        }

        showFullScreen(ctx, t, mode);
        AlarmScheduler.schedule(ctx, t);
    }

    static void showFullScreen(Context ctx, Todo t, String mode) {
        // 1) 先用前台服务起铃声，不依赖界面能否被拉起
        try {
            Intent si = new Intent(ctx, RingService.class);
            si.putExtra(AlarmScheduler.EXTRA_ID, t.id);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(si);
            else ctx.startService(si);
        } catch (Exception ignored) {
        }

        Intent ri = new Intent(ctx, RingActivity.class)
                .putExtra("todo_id", t.id)
                .putExtra("mode", mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 有「显示在其他应用上层」权限时，后台直接 startActivity 是被系统允许的，
        // 而且比等系统走全屏通知（Android 14+ 默认拦截，要用户手动开）快得多。
        // 先试一把，不行还有 notifications 和 RingService 的重试兜底。
        try {
            ctx.startActivity(ri);
        } catch (Exception ignored) {
        }

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "待办提醒", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        PendingIntent pi = PendingIntent.getActivity(ctx, (int) (t.id % 100000), ri,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(t.title)
                .setContentText("点开查看")
                .setContentIntent(pi)
                // 关键：熄屏/AOD 时只有系统发起的全屏通知能把屏幕点亮并盖在锁屏上；
                // 应用自己 startActivity 只会"页面起来了但屏幕不亮"。
                .setFullScreenIntent(pi, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM);
        nm.notify((int) (t.id % 100000) + 1000, b.build());
    }
}
