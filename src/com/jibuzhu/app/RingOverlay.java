package com.jibuzhu.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.jibuzhu.app.data.Todo;

/**
 * 锁屏/熄屏时用的全屏悬浮窗。
 * 普通 Activity 在锁屏上只能"短暂露一下"，很多 ROM 的锁屏会几秒后重新盖回来；
 * SYSTEM_ALERT_WINDOW 的悬浮窗可以稳定压在锁屏之上，系统闹钟也是这么做的。
 */
public final class RingOverlay {

    private static View view;
    private static WindowManager wm;

    private RingOverlay() {
    }

    /** 显示提醒悬浮窗；没有悬浮窗权限或添加失败时返回 false，调用方可以回退到 Activity。 */
    public static boolean show(final Context ctx, Todo t, String mode) {
        hide(ctx);
        try {
            wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            view = RingUi.build(ctx, t, mode, new Runnable() {
                @Override
                public void run() {
                    hide(ctx);
                }
            });
            wm.addView(view, params());
            android.util.Log.i("JBZ", "overlay shown ok");
            return true;
        } catch (Exception e) {
            android.util.Log.w("JBZ", "overlay show failed: " + e, e);
            view = null;
            wm = null;
            return false;
        }
    }

    public static synchronized void hide(Context ctx) {
        try {
            if (wm != null && view != null) wm.removeView(view);
        } catch (Exception ignored) {
        }
        view = null;
        wm = null;
    }

    public static synchronized boolean showing() {
        return view != null;
    }

    private static WindowManager.LayoutParams params() {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.FILL;
        return lp;
    }
}
