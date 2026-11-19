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

        // 保活心跳：每 15 分钟把定位服务拉一次，防止被系统/厂商后台清理杀掉后
        // 一直躺着不动（表现为锁屏时状态栏没有定位标志、到地点不提醒）。
        if ("keepalive".equals(mode)) {
            boolean need = false;
            for (Todo t : Store.get(ctx).todos()) {
                if (t.enabled && (t.isLocation() || t.isCheckin())) {
                    need = true;
                    break;
                }
            }
            if (need) {
                try {
                    ctx.startForegroundService(new Intent(ctx, GeoService.class));
                } catch (Exception e) {
                    android.util.Log.e("JBZ-GEO", "keepalive start fail: " + e);
                }
                AlarmScheduler.keepAlive(ctx);
            }
            return;
        }

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
        // 打卡今天已经打过（兜底催过）就彻底安静，别再响兜底闹钟。
        // 但 "geo" 是 GeoService 刚命中后临时排的立即闹钟，它一定是打点的那一刻发出的，
        // 必须放行，否则命中后 lastFired/done 已写、闹钟到达时被这里吞掉，锁屏就再也不弹。
        if (t.isCheckin() && !"geo".equals(mode) && (Util.today().equals(t.lastFired) || t.done)) {
            AlarmScheduler.schedule(ctx, t);
            return;
        }

        // 打卡不管是位置命中还是兜底时间催的，都算今天打过了：写 lastFired，
        // 同时自动 check（变灰置底），今天不再监听，明天 0 点自动恢复。
        t.lastFired = Util.today();
        if (t.isCheckin()) {
            t.done = true;
            t.doneAt = System.currentTimeMillis();
        }
        Store.get(ctx).saveTodo(t);

        showFullScreen(ctx, t, mode);
        AlarmScheduler.schedule(ctx, t);
    }

    static void showFullScreen(Context ctx, Todo t, String mode) {
        // 1) 先用前台服务起铃声，不依赖界面能否被拉起
        try {
            Intent si = new Intent(ctx, RingService.class);
            si.putExtra(AlarmScheduler.EXTRA_ID, t.id);
            si.putExtra("mode", mode);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(si);
            else ctx.startService(si);
        } catch (Exception ignored) {
        }

        Intent ri = new Intent(ctx, RingActivity.class)
                .putExtra("todo_id", t.id)
                .putExtra("mode", mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // 这里**故意不直接 startActivity**：Android 10+ 后台启动 Activity 被拒后，系统会把它
        // 塞进「pending launch」队列，等用户下次跟本 app 交互时补发——表现就是"点了去打卡/
        // 已完成、解锁后回到记不住，提醒页又弹一次"。界面统一交给 RingService：有悬浮窗权限
        // 就弹悬浮窗（能压在锁屏上），没有权限才在服务里重试拉起 Activity。

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
