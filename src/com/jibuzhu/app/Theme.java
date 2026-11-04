package com.jibuzhu.app;

import android.util.TypedValue;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** 主题：浅色 #EBEDEE / #FDFCFB / #434343，深色取反色。 */
public class Theme {

    public static final int LIGHT = 0;
    public static final int DARK = 1;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("theme", Context.MODE_PRIVATE);
    }

    public static int mode(Context c) {
        return LIGHT;
    }

    public static void setMode(Context c, int m) {
        p(c).edit().putInt("mode", LIGHT).apply();
    }

    /** 深色已去掉，只保留浅色 */
    public static int toggle(Context c) {
        return LIGHT;
    }

    public static boolean dark(Context c) {
        return false;
    }

    // 浅色：背景 #EBEDEE、组件 #FDFCFB、文字 #434343
    // 深色：背景 #434343、组件 #868F96、文字 #FFF
    public static int bg(Context c) {
        return dark(c) ? 0xFF434343 : 0xFFEBEDEE;
    }

    public static int card(Context c) {
        return dark(c) ? 0xFF868F96 : 0xFFFDFCFB;
    }

    public static int text(Context c) {
        return dark(c) ? 0xFFFFFFFF : 0xFF434343;
    }

    public static int sub(Context c) {
        return dark(c) ? 0xFFE6E9EC : 0xFF8B8B8B;
    }

    public static int line(Context c) {
        return dark(c) ? 0xFF5F686F : 0xFFD9DBDD;
    }

    public static int danger(Context c) {
        return dark(c) ? 0xFFE06A5F : 0xFFC4473A;
    }

    public static int accent(Context c) {
        return dark(c) ? 0xFFFFFFFF : 0xFF434343;
    }

    /** 主按钮（新建 / 保存 / 搜索 / 完成）的底色 */
    public static int primaryBg(Context c) {
        return dark(c) ? 0xFF7A828A : 0xFFBAC8E0;
    }

    /** 主按钮上的文字颜色 */
    public static int onPrimary(Context c) {
        return dark(c) ? 0xFFFFFFFF : 0xFF434343;
    }

    /** 所有按钮统一的底色 */
    public static int btnBg(Context c) {
        return card(c);
    }

    /** 状态栏 / 导航栏染成页面背景色，并改用深色图标 */
    public static void applyBars(android.app.Activity a) {
        android.view.Window w = a.getWindow();
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(bg(a));
        w.setNavigationBarColor(bg(a));
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            android.view.View d = w.getDecorView();
            int f = d.getSystemUiVisibility() | android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                f |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            d.setSystemUiVisibility(f);
        }
    }

    /** 圆角卡片背景 */
    public static GradientDrawable cardBg(Context c, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(card(c));
        d.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return d;
    }

    public static GradientDrawable solidBg(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return d;
    }

    public static GradientDrawable strokeBg(int fill, int stroke, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke((int) (1 * c.getResources().getDisplayMetrics().density), stroke);
        d.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return d;
    }

    public static int sp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics());
    }

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
