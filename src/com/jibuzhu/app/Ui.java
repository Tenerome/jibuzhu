package com.jibuzhu.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 纯代码 UI 的公共小工具，颜色全部走 Theme。 */
public class Ui {

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 19, Theme.text(c));
        t.setPadding(0, Theme.dp(c, 2), 0, Theme.dp(c, 2));
        return t;
    }

    public static TextView sub(Context c, String s) {
        return text(c, s, 13, Theme.sub(c));
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 13, Theme.sub(c));
        t.setPadding(0, Theme.dp(c, 14), 0, Theme.dp(c, 6));
        return t;
    }

    /** 卡片容器：圆角 + 卡片色 + 内边距 */
    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(Theme.cardBg(c, 16));
        l.setPadding(Theme.dp(c, 16), Theme.dp(c, 14), Theme.dp(c, 16), Theme.dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static Button button(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Theme.text(c));
        b.setBackground(Theme.solidBg(Theme.btnBg(c), 12, c));
        b.setMinHeight(Theme.dp(c, 40));
        b.setPadding(Theme.dp(c, 10), 0, Theme.dp(c, 10), 0);
        // 阴影统一：全部按钮一个高度，按下时不跳变
        b.setStateListAnimator(null);
        b.setElevation(Theme.dp(c, 4));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            b.setOutlineSpotShadowColor(0x33000000);
            b.setOutlineAmbientShadowColor(0x1F000000);
        }
        b.setTranslationZ(0);
        return b;
    }

    /** 实心主按钮：新建 / 保存 / 搜索 / 完成 都用这个底色 */
    public static Button primary(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(Theme.onPrimary(c));
        b.setBackground(Theme.solidBg(Theme.btnBg(c), 12, c));
        return b;
    }

    public static Button danger(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(Color.WHITE);
        b.setBackground(Theme.solidBg(Theme.danger(c), 12, c));
        return b;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(Theme.text(c));
        e.setHintTextColor(Theme.sub(c));
        e.setBackground(Theme.strokeBg(Theme.card(c), Theme.line(c), 10, c));
        e.setPadding(Theme.dp(c, 12), Theme.dp(c, 8), Theme.dp(c, 12), Theme.dp(c, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(c, 4);
        e.setLayoutParams(lp);
        return e;
    }

    /** 分段切换控件 */
    public static LinearLayout segmented(final Context c, String[] names, int selected, final OnPick onPick) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setBackground(Theme.strokeBg(Theme.bg(c), Theme.line(c), 12, c));
        box.setPadding(Theme.dp(c, 3), Theme.dp(c, 3), Theme.dp(c, 3), Theme.dp(c, 3));
        final TextView[] tvs = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView tv = text(c, names[i], 15, i == selected ? Theme.onPrimary(c) : Theme.text(c));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, Theme.dp(c, 9), 0, Theme.dp(c, 9));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Theme.dp(c, 9));
            g.setColor(i == selected ? Theme.card(c) : Color.TRANSPARENT);
            tv.setBackground(g);
            tvs[i] = tv;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < tvs.length; j++) {
                        boolean on = j == idx;
                        tvs[j].setTextColor(on ? Theme.onPrimary(c) : Theme.text(c));
                        GradientDrawable bg = new GradientDrawable();
                        bg.setCornerRadius(Theme.dp(c, 9));
                        bg.setColor(on ? Theme.card(c) : Color.TRANSPARENT);
                        tvs[j].setBackground(bg);
                    }
                    onPick.onPick(idx);
                }
            });
            box.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.dp(c, 4);
        box.setLayoutParams(lp);
        return box;
    }

    public interface OnPick {
        void onPick(int index);
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Theme.dp(c, dp)));
        return v;
    }

    public static int dp(Context c, float v) {
        return Theme.dp(c, v);
    }
}
