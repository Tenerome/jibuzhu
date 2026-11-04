package com.jibuzhu.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 右侧设置面板：权限设置 / 桌面组件 / 开源声明。
 *
 * 三大项都是折叠的，点标题展开。权限项每次展开都重新读一遍状态，
 * 所以从系统设置里回来再点一次就是最新的。
 */
public final class SettingsPanel {

    private SettingsPanel() {
    }

    /** 侧边栏宽度占屏幕的比例 */
    static final float WIDTH_RATIO = 0.90f;
    private static final String[] BG_LABELS = {"全透明", "很淡", "淡", "半透明", "深"};

    public static View build(final Activity a, final Runnable onClose) {
        LinearLayout panel = Ui.col(a);
        panel.setBackgroundColor(Theme.bg(a));
        panel.setPadding(Theme.dp(a, 18), Theme.dp(a, 22), Theme.dp(a, 18), Theme.dp(a, 18));

        // 标题行
        LinearLayout head = Ui.row(a);
        TextView t = Ui.text(a, "设置", 20, Theme.text(a));
        head.addView(t);
        View gap = new View(a);
        head.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));
        TextView close = Ui.text(a, "关闭", 14, Theme.sub(a));
        close.setPadding(Theme.dp(a, 10), Theme.dp(a, 6), 0, Theme.dp(a, 6));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onClose != null) onClose.run();
            }
        });
        head.addView(close);
        panel.addView(head);

        ScrollView sv = new ScrollView(a);
        LinearLayout box = Ui.col(a);
        box.setPadding(0, Theme.dp(a, 10), 0, Theme.dp(a, 10));
        sv.addView(box, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // 1. 权限设置
        final LinearLayout permBody = Ui.col(a);
        permBody.setVisibility(View.GONE);
        box.addView(section(a, "权限设置", permBody, new Runnable() {
            @Override
            public void run() {
                fillPerms(a, permBody);
            }
        }));
        fillPerms(a, permBody);

        // 2. 桌面组件
        final LinearLayout widgetBody = Ui.col(a);
        widgetBody.setVisibility(View.GONE);
        box.addView(section(a, "桌面组件", widgetBody));
        fillWidget(a, widgetBody);

        // 3. 开源声明
        final LinearLayout ossBody = Ui.col(a);
        ossBody.setVisibility(View.GONE);
        box.addView(section(a, "开源声明", ossBody));
        fillOss(a, ossBody);

        return panel;
    }

    /** 一个可展开的区块：标题行 + 内容 */
    private static View section(final Activity a, String title, final View body) {
        return section(a, title, body, null);
    }

    private static View section(final Activity a, String title, final View body,
                                final Runnable onOpen) {
        LinearLayout wrap = Ui.col(a);
        LinearLayout row = Ui.row(a);
        row.setPadding(Theme.dp(a, 16), Theme.dp(a, 14), Theme.dp(a, 16), Theme.dp(a, 14));
        row.setBackground(Theme.solidBg(Theme.card(a), 12, a));
        final TextView name = Ui.text(a, title, 15, Theme.text(a));
        row.addView(name);
        View gap = new View(a);
        row.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));
        final TextView arrow = Ui.text(a, "▾", 14, Theme.sub(a));
        row.addView(arrow);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean open = body.getVisibility() == View.VISIBLE;
                body.setVisibility(open ? View.GONE : View.VISIBLE);
                arrow.setText(open ? "▾" : "▴");
                // 展开时重新检测一次（用户可能刚从系统设置里回来）
                if (!open && onOpen != null) onOpen.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(a, 10);
        wrap.addView(row, lp);
        wrap.addView(body);
        return wrap;
    }

    // ---------- 权限 ----------

    private static void fillPerms(final Activity a, LinearLayout body) {
        body.removeAllViews();

        permRow(a, body, "定位权限",
                check(a, android.Manifest.permission.ACCESS_FINE_LOCATION),
                new Runnable() {
                    @Override
                    public void run() {
                        a.requestPermissions(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION}, 11);
                    }
                });

        if (Build.VERSION.SDK_INT >= 29) {
            permRow(a, body, "后台定位",
                    check(a, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    new Runnable() {
                        @Override
                        public void run() {
                            // 后台定位不能直接弹框，只能去应用详情里手动选「始终允许」
                            openAppDetails(a);
                        }
                    });
        }

        if (Build.VERSION.SDK_INT >= 33) {
            permRow(a, body, "通知权限", check(a, "android.permission.POST_NOTIFICATIONS"),
                    new Runnable() {
                        @Override
                        public void run() {
                            a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 12);
                        }
                    });
        }

        if (Build.VERSION.SDK_INT >= 31) {
            boolean ok;
            try {
                AlarmManager am = (AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
                ok = am != null && am.canScheduleExactAlarms();
            } catch (Exception e) {
                ok = false;
            }
            permRow(a, body, "精确闹钟", ok, new Runnable() {
                @Override
                public void run() {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + a.getPackageName())));
                    } catch (Exception e) {
                        openAppDetails(a);
                    }
                }
            });
        }

        if (Build.VERSION.SDK_INT >= 34) {
            boolean ok;
            try {
                NotificationManager nm = (NotificationManager) a.getSystemService(Context.NOTIFICATION_SERVICE);
                ok = nm != null && nm.canUseFullScreenIntent();
            } catch (Exception e) {
                ok = false;
            }
            permRow(a, body, "全屏通知", ok, new Runnable() {
                @Override
                public void run() {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + a.getPackageName())));
                    } catch (Exception e) {
                        openAppDetails(a);
                    }
                }
            });
        }

        permRow(a, body, "显示在其他应用上层",
                Settings.canDrawOverlays(a), new Runnable() {
                    @Override
                    public void run() {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            openAppDetails(a);
                        }
                    }
                });

        if (Build.VERSION.SDK_INT >= 29) {
            permRow(a, body, "设备运动状态", check(a, "android.permission.ACTIVITY_RECOGNITION"),
                    new Runnable() {
                        @Override
                        public void run() {
                            a.requestPermissions(new String[]{"android.permission.ACTIVITY_RECOGNITION"}, 13);
                        }
                    });
        }

    }

    private static boolean check(Context c, String perm) {
        return c.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static void openAppDetails(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + c.getPackageName())));
        } catch (Exception ignored) {
        }
    }

    private static void permRow(Activity a, LinearLayout body, String name, boolean ok,
                                final Runnable action) {
        permRow(a, body, name, ok, action, ok ? "已开启" : "未开启");
    }

    private static void permRow(Activity a, LinearLayout body, String name, boolean ok,
                                final Runnable action, String label) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Theme.dp(a, 14), Theme.dp(a, 12), Theme.dp(a, 14), Theme.dp(a, 12));

        LinearLayout texts = Ui.col(a);
        TextView n = Ui.text(a, name, 13, Theme.text(a));
        texts.addView(n);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        int color = ok ? 0xFF4CAF50 : Theme.danger(a);
        TextView st = Ui.text(a, label, 13, color);
        st.setSingleLine(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Theme.dp(a, 10);
        row.addView(st, slp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action != null) action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Theme.dp(a, 6);
        lp.leftMargin = Theme.dp(a, 16);
        lp.rightMargin = Theme.dp(a, 6);
        row.setBackground(Theme.solidBg(Theme.bg(a), 10, a));
        body.addView(row, lp);
    }

    // ---------- 桌面组件 ----------

    private static void fillWidget(final Activity a, LinearLayout body) {
        body.removeAllViews();
        final SharedPreferences sp = a.getSharedPreferences("widget", Context.MODE_PRIVATE);

        LinearLayout row = Ui.row(a);
        row.setPadding(Theme.dp(a, 14), Theme.dp(a, 12), Theme.dp(a, 14), Theme.dp(a, 12));
        LinearLayout texts = Ui.col(a);
        texts.addView(Ui.text(a, "显示桌面组件内容", 14, Theme.text(a)));
        texts.addView(Ui.text(a, "关掉后桌面组件只显示一行提示", 12, Theme.sub(a)));
        row.addView(texts);
        View gap = new View(a);
        row.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));

        final Switch sw = new Switch(a);
        sw.setChecked(sp.getBoolean("enabled", true));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean("enabled", isChecked).apply();
                TodoWidget.refreshAll(a);
            }
        });
        row.addView(sw);
        row.setBackground(Theme.solidBg(Theme.card(a), 12, a));
        body.addView(row);

        LinearLayout bgBox = Ui.col(a);
        bgBox.setPadding(Theme.dp(a, 14), Theme.dp(a, 12), Theme.dp(a, 14), Theme.dp(a, 6));
        LinearLayout bgTop = Ui.row(a);
        TextView bgLabel = Ui.text(a, "背景透明度", 14, Theme.text(a));
        bgTop.addView(bgLabel);
        View bgGap = new View(a);
        bgTop.addView(bgGap, new LinearLayout.LayoutParams(0, 1, 1));
        final TextView bgVal = Ui.text(a, "", 12, Theme.sub(a));
        bgTop.addView(bgVal);
        bgBox.addView(bgTop);

        final SeekBar bar = new SeekBar(a);
        bar.setMax(4);
        int lv = TodoWidget.bgLevel(a);
        bar.setProgress(lv);
        bgVal.setText(BG_LABELS[lv]);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                bgVal.setText(BG_LABELS[Math.max(0, Math.min(4, progress))]);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                TodoWidget.setBgLevel(a, sb.getProgress());
                TodoWidget.refreshAll(a);
            }
        });
        bgBox.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(a, 34)));
        bgBox.setBackground(Theme.solidBg(Theme.card(a), 12, a));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Theme.dp(a, 10);
        body.addView(bgBox, blp);


        TextView tip = Ui.text(a, "长按桌面空白处 → 小组件 → 找到「记不住」添加", 12, Theme.sub(a));
        tip.setPadding(Theme.dp(a, 14), Theme.dp(a, 10), Theme.dp(a, 14), Theme.dp(a, 6));
        body.addView(tip);
    }

    // ---------- 开源声明 ----------

    private static void fillOss(Activity a, LinearLayout body) {
        body.removeAllViews();
        TextView t = Ui.text(a,
                "这里以后放项目地址和第三方库的许可声明。\n\n"
                        + "· 腾讯位置服务（地图搜索 / 逆地址解析 / 静态图）\n"
                        + "· AndroidX 未使用\n",
                13, Theme.sub(a));
        t.setLineSpacing(Theme.dp(a, 3), 1f);
        t.setPadding(Theme.dp(a, 14), Theme.dp(a, 8), Theme.dp(a, 14), Theme.dp(a, 8));
        body.addView(t);
    }

    static void toast(Activity a, String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }
}
