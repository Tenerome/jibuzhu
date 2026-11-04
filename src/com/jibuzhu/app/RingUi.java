package com.jibuzhu.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;
import com.jibuzhu.app.trigger.AlarmReceiver;
import com.jibuzhu.app.trigger.AlarmScheduler;
import com.jibuzhu.app.trigger.RingService;

/**
 * 提醒界面的内容与动作。
 * 同一套界面既给 RingActivity（亮屏/解锁时）用，也给 RingOverlay（锁屏/熄屏时）用，
 * 保证两条路径长得一模一样、行为也一模一样。
 */
public final class RingUi {

    private RingUi() {
    }

    /** 构建提醒界面；点按钮后执行 after（Activity 里是 finish，悬浮窗里是移除窗口）。 */
    public static View build(final Context ctx, final Todo t, final String mode, final Runnable after) {
        final long id = t == null ? -1 : t.id;

        LinearLayout root = Ui.col(ctx);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Theme.bg(ctx));
        int pad = Theme.dp(ctx, 26);
        root.setPadding(pad, pad, pad, pad);

        TextView tag = Ui.text(ctx, "记不住 · 提醒", 14, Theme.sub(ctx));
        tag.setGravity(Gravity.CENTER);
        root.addView(tag);

        TextView title = Ui.text(ctx, t == null ? "提醒" : t.title, 32, Theme.text(ctx));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Theme.dp(ctx, 26), 0, Theme.dp(ctx, 10));
        root.addView(title);

        TextView sub = Ui.text(ctx, subText(t, mode), 16, Theme.accent(ctx));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        // 兜底时间到了：只提醒，不跳 WeLink
        final boolean checkin = t != null && t.isCheckin() && !"fallback".equals(mode);
        Button check = Ui.primary(ctx, checkin ? "✓  去打卡" : "✓  完成了，别再响");
        check.setTextSize(19);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 66));
        cp.topMargin = Theme.dp(ctx, 46);
        check.setLayoutParams(cp);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkin) {
                    stopRing(ctx, id);
                    if (after != null) after.run();
                    final Context app = ctx.getApplicationContext();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    boolean ok = Checkin.open(app);
                                    android.util.Log.d("JBZ-WIFI", "ring checkin open=" + ok);
                                }
                            }, 600);
                    return;
                }
                stopRing(ctx, id);
                finishTodo(ctx, id);
                if (after != null) after.run();
            }
        });
        root.addView(check);

        Button snooze = Ui.button(ctx, "稍后 5 分钟");
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snoozeFive(ctx, id);
                stopRing(ctx, id);
                if (after != null) after.run();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 50));
        sp.topMargin = Theme.dp(ctx, 10);
        snooze.setLayoutParams(sp);
        if (!checkin) root.addView(snooze);

        return root;
    }

    public static String subText(Todo t, String mode) {
        if (t == null) return "该做这件事了";
        if (t.isCheckin()) {
            if ("fallback".equals(mode)) return "快去打卡哦";
            return "已到 " + (t.place == null || t.place.isEmpty() ? "打卡地点" : t.place);
        }
        if (t.isLocation()) {
            return "fallback".equals(mode) ? "兜底时间到了，还没到 " + t.place : "已到 " + t.place;
        }
        return "fallback".equals(mode) ? "兜底时间到了" : "时间到了 · " + t.winStart;
    }

    public static void stopRing(Context ctx, long todoId) {
        try {
            ctx.stopService(new Intent(ctx, RingService.class));
        } catch (Exception ignored) {
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) (todoId % 100000) + 1000);
    }

    /** check 掉本次提醒：单次待办完成后删除，重复待办变灰置底到次日 0 点 */
    public static void finishTodo(Context ctx, long todoId) {
        Todo t = Store.get(ctx).todo(todoId);
        if (t == null) return;
        if (t.isRepeat()) {
            t.done = true;
            t.doneAt = System.currentTimeMillis();
            Store.get(ctx).saveTodo(t);
        } else {
            AlarmScheduler.cancel(ctx, t.id);
            Store.get(ctx).deleteTodo(t.id);
        }
    }

    public static void snoozeFive(Context ctx, long todoId) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(ctx, AlarmReceiver.class)
                    .putExtra("todo_id", todoId)
                    .putExtra("mode", "main");
            PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) (todoId % 100000) + 7, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 5 * 60 * 1000L, pi);
        } catch (Exception ignored) {
        }
    }
}
