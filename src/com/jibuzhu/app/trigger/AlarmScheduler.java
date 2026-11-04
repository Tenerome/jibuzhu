package com.jibuzhu.app.trigger;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.jibuzhu.app.Util;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;

import java.util.Calendar;
import java.util.List;

/** 把待办的时间点登记成系统精确闹钟（走 AlarmManager，不受静音影响由响铃界面负责）。 */
public class AlarmScheduler {

    public static final String EXTRA_ID = "todo_id";
    public static final String EXTRA_MODE = "mode";   // main | fallback

    /** 为一条待办重排它的闹钟 */
    public static void schedule(Context ctx, Todo t) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        cancel(ctx, t.id);
        if (!t.enabled) return;

        // 时间型待办：到 winStart 就提醒。定位型/打卡型靠位置触发，不排主闹钟
        if (!t.isLocation() && !t.isCheckin() && !t.winStart.isEmpty()) {
            setAt(ctx, am, t.id, "main", nextAt(t.winStart));
        }
        // 兜底：到点还没触发就提醒（三种类型都支持）
        if (!t.fallback.isEmpty()) {
            setAt(ctx, am, t.id, "fallback", nextAt(t.fallback));
        }
    }

    /** 距离下一次 hh:mm 还有多少毫秒，今天已过则顺延到明天 */
    static long nextAt(String hhmm) {
        Calendar c = Calendar.getInstance();
        int target = Util.toMinutes(hhmm);
        if (target < 0) target = 0;
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        c.set(Calendar.HOUR_OF_DAY, target / 60);
        c.set(Calendar.MINUTE, target % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (target <= now) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    static void setAt(Context ctx, AlarmManager am, long id, String mode, long at) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_MODE, mode);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, reqCode(id, mode), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            // 用 setAlarmClock：系统把它当真闹钟，触发时允许本应用从后台拉起界面
            Intent show = new Intent(ctx, com.jibuzhu.app.RingActivity.class)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_MODE, mode);
            PendingIntent showPi = PendingIntent.getActivity(ctx, reqCode(id, mode) + 1000, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pi);
        } catch (Exception e) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (Exception e2) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
    }

    public static void cancel(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (String mode : new String[]{"main", "fallback"}) {
            Intent i = new Intent(ctx, AlarmReceiver.class)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_MODE, mode);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, reqCode(id, mode), i,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
        }
    }

    static int reqCode(long id, String mode) {
        return (int) (id * 10 + ("fallback".equals(mode) ? 1 : 0));
    }

    public static void rescheduleAll(Context ctx) {
        List<Todo> list = Store.get(ctx).todos();
        for (Todo t : list) schedule(ctx, t);
    }
}
