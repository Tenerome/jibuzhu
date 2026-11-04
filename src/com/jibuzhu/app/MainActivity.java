package com.jibuzhu.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jibuzhu.app.data.Plan;
import com.jibuzhu.app.data.Stage;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;
import com.jibuzhu.app.trigger.AlarmScheduler;
import com.jibuzhu.app.trigger.GeoService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private int tab = 0;               // 0 待办 / 1 计划
    private LinearLayout listBox;
    private TextView tabTodo, tabPlan;
    private final Set<Long> expanded = new HashSet<>();
    private FrameLayout shell;
    private View drawerMask;
    private View drawerPanel;
    private boolean drawerOpen;
    private ScrollView scrollView;
    /** 待刷新距离的列表行：{TextView, Todo} */
    private final List<Object[]> distRows = new ArrayList<>();
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable distTick = new Runnable() {
        @Override
        public void run() {
            for (Object[] r : distRows) {
                ((TextView) r[0]).setText(condition((Todo) r[1]));
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        if (Theme.dark(this)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Theme.applyBars(this);
        super.onCreate(b);
        buildUi();
        askPerms();
        syncGeo();
        AlarmScheduler.rescheduleAll(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        applyFocus();
        syncGeo();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(distTick);
    }

    private void askPerms() {
        ArrayList<String> ps = new ArrayList<>();
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ps.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            ps.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ps.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (android.os.Build.VERSION.SDK_INT >= 29
                && checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ps.add("android.permission.ACTIVITY_RECOGNITION");
        }
        if (!ps.isEmpty()) requestPermissions(ps.toArray(new String[0]), 1);
        else askBackgroundLocation();
        askSpecialPerms();
    }


    /**
     * 悬浮窗（后台弹窗）和全屏通知（锁屏上直接弹提醒页）这两项属于特殊权限，
     * 运行时申请拿不到，只能带用户去系统页面打开。每项只打扰一次。
     */
    private void askSpecialPerms() {
        android.content.SharedPreferences sp = getSharedPreferences("perm", MODE_PRIVATE);

        if (!android.provider.Settings.canDrawOverlays(this) && !sp.getBoolean("asked_overlay", false)) {
            sp.edit().putBoolean("asked_overlay", true).apply();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 34 && !sp.getBoolean("asked_fullscreen", false)) {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                sp.edit().putBoolean("asked_fullscreen", true).apply();
                try {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            android.net.Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Android 10 起后台/锁屏时取位置要单独授予"始终允许"，
     * 只给"仅使用期间"的话锁屏后位置就停了，定位提醒自然不会响。这里只问一次。
     */
    private void askBackgroundLocation() {
        if (android.os.Build.VERSION.SDK_INT < 29) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences sp = getSharedPreferences("perm", MODE_PRIVATE);
        if (sp.getBoolean("bg_asked", false)) return;
        sp.edit().putBoolean("bg_asked", true).apply();
        try {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2);
        } catch (Exception ignored) {
        }
    }

    /**
     * 只有「当前存在处于时间段内的定位待办」才需要定位服务。
     * 没有就把常驻通知连同服务一起停掉，通知栏保持干净。
     */
    private void syncGeo() {
        try {
            boolean need = false;
            for (Todo t : Store.get(this).todos()) {
                if (!t.enabled) continue;
                // 定位型和打卡型都靠定位服务触发
                if (!t.isLocation() && !t.isCheckin()) continue;
                // 这里不能再要求「必须在时间段内、今天还没响过」。
                // 时间段是稍后才到的，而 syncGeo 只在打开 App 时跑一次 ——
                // 加上那些条件就等于时段外不启动服务，到点了没人去打开定位
                // （症状：锁屏时状态栏一直没有定位标志，解锁瞬间才出现）。
                need = true;
                break;
            }
            android.util.Log.d("JBZ-GEO", "syncGeo need=" + need);
            if (need) startForegroundService(new Intent(this, GeoService.class));
            else stopService(new Intent(this, GeoService.class));

        } catch (Exception e) {
            android.util.Log.e("JBZ-GEO", "syncGeo fail: " + e);
        }
        warmLoc();
    }

    /** 打开 App 就顺手把位置定一下：列表距离立刻能显示，去搜地点也更快 */
    private void warmLoc() {
        try {
            if (GeoService.lastLoc != null) return;   // 服务已经给了位置
            final android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return;
            android.location.Location best = null;
            for (String p : new String[]{android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (!lm.isProviderEnabled(p)) continue;
                    android.location.Location l = lm.getLastKnownLocation(p);
                    if (l == null) continue;
                    if (best == null || l.getTime() > best.getTime()) best = l;
                } catch (Exception ignored) {
                }
            }
            if (best != null) GeoService.lastLoc = best;
            for (String p : new String[]{android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.GPS_PROVIDER}) {
                try {
                    if (!lm.isProviderEnabled(p)) continue;
                    lm.requestSingleUpdate(p, new android.location.LocationListener() {
                        @Override
                        public void onLocationChanged(android.location.Location loc) {
                            if (loc != null) GeoService.lastLoc = loc;
                        }

                        @Override
                        public void onStatusChanged(String s, int i, android.os.Bundle bundle) {
                        }

                        @Override
                        public void onProviderEnabled(String s) {
                        }

                        @Override
                        public void onProviderDisabled(String s) {
                        }
                    }, getMainLooper());
                    break;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Theme.bg(this));

        LinearLayout top = Ui.row(this);
        top.setPadding(Theme.dp(this, 20), Theme.dp(this, 18), Theme.dp(this, 20), Theme.dp(this, 6));
        TextView title = Ui.text(this, "记不住就加个待办呀~", 17, Theme.text(this));
        top.addView(title);
        View titleGap = new View(this);
        top.addView(titleGap, new LinearLayout.LayoutParams(0, 1, 1));
        TextView themeBtn = Ui.text(this, "回收站", 13, Theme.sub(this));
        themeBtn.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), 0, Theme.dp(this, 8));
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TrashActivity.class));
            }
        });
        top.addView(themeBtn);
        TextView setBtn = Ui.text(this, "设置", 13, Theme.sub(this));
        setBtn.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), 0, Theme.dp(this, 8));
        setBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        top.addView(setBtn);
        root.addView(top);

        LinearLayout tabs = Ui.row(this);
        tabs.setPadding(Theme.dp(this, 20), 0, Theme.dp(this, 20), Theme.dp(this, 10));
        tabTodo = tabView("待办", 0);
        tabPlan = tabView("计划", 1);
        tabs.addView(tabTodo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(tabPlan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(tabs);


        ScrollView sv = new ScrollView(this);
        scrollView = sv;
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        listBox = Ui.col(this);
        listBox.setPadding(Theme.dp(this, 16), Theme.dp(this, 4), Theme.dp(this, 16), Theme.dp(this, 90));
        sv.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = Ui.primary(this, "＋ 新建");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 52));
        ap.leftMargin = Theme.dp(this, 20);
        ap.rightMargin = Theme.dp(this, 20);
        ap.bottomMargin = Theme.dp(this, 18);
        add.setLayoutParams(ap);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        tab == 0 ? TodoEditActivity.class : PlanEditActivity.class));
            }
        });
        root.addView(add);

        // 外面套一层 FrameLayout，右侧挂设置抽屉
        shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerMask = new View(this);
        drawerMask.setBackgroundColor(0x66000000);
        drawerMask.setVisibility(View.GONE);
        drawerMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSettings();
            }
        });
        shell.addView(drawerMask, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerPanel = SettingsPanel.build(this, new Runnable() {
            @Override
            public void run() {
                closeSettings();
            }
        });
        int dw = (int) (getResources().getDisplayMetrics().widthPixels * SettingsPanel.WIDTH_RATIO);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dw,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);
        drawerPanel.setLayoutParams(dlp);
        drawerPanel.setVisibility(View.GONE);
        shell.addView(drawerPanel);
        setContentView(shell);
    }

    private TextView tabView(String s, final int which) {
        TextView tv = Ui.text(this, s, 16, Theme.sub(this));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Theme.dp(this, 12));
        tv.setBackground(g);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = which;
                refresh();
            }
        });
        return tv;
    }

    private void openSettings() {
        if (drawerOpen || drawerPanel == null) return;
        drawerOpen = true;
        int w = drawerPanel.getWidth() > 0 ? drawerPanel.getWidth()
                : (int) (getResources().getDisplayMetrics().widthPixels * SettingsPanel.WIDTH_RATIO);
        drawerPanel.setTranslationX(w);
        drawerPanel.setVisibility(View.VISIBLE);
        drawerMask.setVisibility(View.VISIBLE);
        drawerMask.setAlpha(0f);
        drawerPanel.animate().translationX(0f).setDuration(220).start();
        drawerMask.animate().alpha(1f).setDuration(220).start();
    }

    private void closeSettings() {
        if (!drawerOpen || drawerPanel == null) return;
        drawerOpen = false;
        drawerPanel.animate().translationX(drawerPanel.getWidth()).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (drawerPanel != null) drawerPanel.setVisibility(View.GONE);
                        if (drawerMask != null) drawerMask.setVisibility(View.GONE);
                    }
                }).start();
        drawerMask.animate().alpha(0f).setDuration(200).start();
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            closeSettings();
            return;
        }
        super.onBackPressed();
    }

    /** 从桌面组件点进来：切到对应页签，滚到那条并闪一下 */
    private void applyFocus() {
        Intent in = getIntent();
        if (in == null || listBox == null) return;
        long tid = in.getLongExtra("focus_todo", 0);
        long pid = in.getLongExtra("focus_plan", 0);
        int ftab = in.getIntExtra("focus_tab", -1);
        if (tid <= 0 && pid <= 0) return;
        // 先消费掉，后面 refresh 再进来时不会再触发
        in.removeExtra("focus_todo");
        in.removeExtra("focus_plan");
        in.removeExtra("focus_tab");

        int want = ftab >= 0 ? ftab : (pid > 0 ? 1 : 0);
        if (want != tab) {
            tab = want;
            refresh();
        }
        if (pid > 0 && !expanded.contains(pid)) {
            expanded.add(pid);
            refresh();
        }

        final View target = listBox.findViewWithTag(tid > 0 ? ("t" + tid) : ("p" + pid));
        if (target == null) return;
        target.post(new Runnable() {
            @Override
            public void run() {
                if (scrollView != null) {
                    scrollView.smoothScrollTo(0, Math.max(0, target.getTop() - Theme.dp(MainActivity.this, 40)));
                }
                target.setAlpha(0.35f);
                target.animate().alpha(1f).setDuration(400).start();
            }
        });
    }

    private void refresh() {
        if (listBox == null) return;
        // 数据变了让桌面组件跟着变
        TodoWidget.refreshAll(this);
        listBox.removeAllViews();
        distRows.clear();
        paintTab(tabTodo, tab == 0);
        paintTab(tabPlan, tab == 1);
        if (tab == 0) showTodos();
        else showPlans();
        startDistTick();
    }

    /** 只有存在定位类待办时才定期刷新距离，列表里没有就不跑 */
    private void startDistTick() {
        ui.removeCallbacks(distTick);
        if (distRows.isEmpty() || tab != 0) return;
        ui.postDelayed(distTick, 1000);
    }

    private void paintTab(TextView tv, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Theme.dp(this, 12));
        g.setColor(on ? Theme.card(this) : Color.TRANSPARENT);
        tv.setBackground(g);
        tv.setTextColor(on ? Theme.text(this) : Theme.sub(this));
    }

    // ==================== 待办 ====================

    private void showTodos() {
        List<Todo> list = Store.get(this).todos();
        if (list.isEmpty()) {
            listBox.addView(empty("还没有待办\n点下面「＋ 新建」，加一个到点会叫你的事"));
            return;
        }
        for (final Todo t : list) {
            LinearLayout card = Ui.card(this);
            card.setBackground(Theme.cardBg(this, 16));
            card.setPadding(Theme.dp(this, 10), Theme.dp(this, 12), Theme.dp(this, 14), Theme.dp(this, 12));

            LinearLayout row = Ui.row(this);
            TextView check = Ui.text(this, t.done ? "✓" : "", 17, Theme.bg(this));
            check.setGravity(Gravity.CENTER);
            GradientDrawable cg = new GradientDrawable();
            cg.setShape(GradientDrawable.OVAL);
            cg.setColor(t.done ? Theme.text(this) : Color.TRANSPARENT);
            cg.setStroke(Theme.dp(this, 2), t.done ? Theme.text(this) : Theme.sub(this));
            check.setBackground(cg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    Theme.dp(this, 30), Theme.dp(this, 30));
            clp.rightMargin = Theme.dp(this, 12);
            check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    complete(t);
                }
            });
            row.addView(check, clp);

            LinearLayout body = Ui.col(this);
            body.addView(Ui.text(this, t.title, 17, t.done ? Theme.sub(this) : Theme.text(this)));
            TextView cond = Ui.text(this, condition(t), 12, Theme.sub(this));
            cond.setPadding(0, Theme.dp(this, 4), 0, 0);
            body.addView(cond);
            // 定位类才需要实时刷距离
            if (t.isLocation() || t.isCheckin()) distRows.add(new Object[]{cond, t});
            row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(row);

            final long id = t.id;
            View del = deleteButton();
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlarmScheduler.cancel(MainActivity.this, id);
                    Store.get(MainActivity.this).trashAdd("待办", t.title, condition(t));
                    Store.get(MainActivity.this).deleteTodo(id);
                    refresh();
                }
            });
            SwipeRow sw = new SwipeRow(this, card, del);
            sw.setOnRowClick(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(MainActivity.this, TodoEditActivity.class);
                    i.putExtra("id", id);
                    startActivity(i);
                }
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.bottomMargin = Theme.dp(this, 10);
            sw.setLayoutParams(sp);
            sw.setTag("t" + t.id);
            listBox.addView(sw);
        }
    }

    private String condition(Todo t) {
        StringBuilder s = new StringBuilder();
        boolean loc = t.isLocation() || t.isCheckin();
        if (loc) {
            s.append("到 ").append(t.place.isEmpty() ? "选定地点" : t.place);
            s.append(t.isCheckin() ? " 自动打卡" : " 提醒");
        } else {
            s.append(t.winStart).append("  提醒");
        }
        s.append(t.isRepeat() ? "  ·  每天" : "  ·  单次");
        if (!t.ring.isEmpty()) s.append("  ·  自定义铃声");
        if (loc) {
            String d = distanceText(t);
            if (!d.isEmpty()) s.append("  ").append(d);
        }
        return s.toString();
    }

    /** 距离文案：1000 米以上换成公里 */
    private String distanceText(Todo t) {
        android.location.Location l = GeoService.lastLoc;
        if (l == null) return "";
        double d = Util.distance(l.getLatitude(), l.getLongitude(), t.lat, t.lng);
        if (d >= 1000) return String.format(Locale.US, "距离 %.1f km", d / 1000.0);
        return "距离 " + ((int) d) + " m";
    }

    private void complete(Todo t) {
        if (t.done) {
            t.done = false;
            t.doneAt = 0;
            Store.get(this).saveTodo(t);
        } else if (t.isRepeat()) {
            t.done = true;
            t.doneAt = System.currentTimeMillis();
            Store.get(this).saveTodo(t);
        } else {
            AlarmScheduler.cancel(this, t.id);
            Store.get(this).trashAdd("待办", t.title, condition(t) + "  ·  已完成");
            Store.get(this).deleteTodo(t.id);
        }
        refresh();
    }

    private View deleteButton() {
        TextView del = Ui.text(this, "删除", 15, Color.WHITE);
        del.setGravity(Gravity.CENTER);
        del.setBackground(Theme.solidBg(Theme.danger(this), 12, this));
        del.setLayoutParams(new ViewGroup.LayoutParams(
                Theme.dp(this, 88), ViewGroup.LayoutParams.MATCH_PARENT));
        return del;
    }

    // ==================== 计划 ====================

    private void showPlans() {
        List<Plan> plans = Store.get(this).plans();
        if (plans.isEmpty()) {
            listBox.addView(empty("还没有计划\n比如「1月1号前换租」，再拆成几个阶段"));
            return;
        }
        for (final Plan p : plans) {
            final boolean open = expanded.contains(p.id);
            LinearLayout card = Ui.card(this);
            card.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 8), Theme.dp(this, 12));

            LinearLayout head = Ui.row(this);
            LinearLayout info = Ui.col(this);
            info.addView(Ui.text(this, p.title, 18, Theme.text(this)));
            String goal = p.goal;
            if (!p.deadline.isEmpty()) goal += (goal.isEmpty() ? "" : "  ·  ") + "截止 " + p.deadline;
            if (!goal.isEmpty()) {
                TextView gl = Ui.text(this, goal, 12, Theme.sub(this));
                gl.setPadding(0, Theme.dp(this, 4), 0, 0);
                info.addView(gl);
            }
            head.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView plus = Ui.text(this, "＋", 24, Theme.text(this));
            plus.setGravity(Gravity.CENTER);
            plus.setPadding(Theme.dp(this, 10), 0, Theme.dp(this, 6), 0);
            plus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openStageDialog(p, null);
                }
            });
            head.addView(plus);

            TextView arrow = Ui.text(this, open ? "︿" : "﹀", 15, Theme.sub(this));
            arrow.setGravity(Gravity.CENTER);
            arrow.setPadding(Theme.dp(this, 6), 0, Theme.dp(this, 6), 0);
            arrow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (expanded.contains(p.id)) expanded.remove(p.id);
                    else expanded.add(p.id);
                    refresh();
                }
            });
            head.addView(arrow);
            card.addView(head);

            if (open) {
                List<Stage> stages = Store.get(this).stages(p.id);
                if (stages.isEmpty()) {
                    TextView hint = Ui.text(this, "还没有阶段，点右上角 ＋ 加第一个", 12, Theme.sub(this));
                    hint.setPadding(Theme.dp(this, 4), Theme.dp(this, 14), 0, Theme.dp(this, 8));
                    card.addView(hint);
                } else {
                    LinearLayout flow = Ui.col(this);
                    flow.setPadding(0, Theme.dp(this, 10), 0, Theme.dp(this, 2));
                    for (int i = 0; i < stages.size(); i++) {
                        final Stage s = stages.get(i);
                        final boolean first = i == 0, last = i == stages.size() - 1;
                        LinearLayout lineRow = Ui.row(this);
                        DotView dot = new DotView(this, first, last, s.done, false);
                        lineRow.addView(dot, new LinearLayout.LayoutParams(
                                Theme.dp(this, 26), ViewGroup.LayoutParams.MATCH_PARENT));

                        LinearLayout sc = Ui.col(this);
                        sc.setBackground(Theme.strokeBg(Theme.card(this), Theme.line(this), 12, this));
                        sc.setPadding(Theme.dp(this, 12), Theme.dp(this, 10),
                                Theme.dp(this, 8), Theme.dp(this, 10));
                        TextView st = Ui.text(this, s.title, 16, s.done ? Theme.sub(this) : Theme.text(this));
                        if (s.done) {
                            st.setPaintFlags(st.getPaintFlags()
                                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                        }
                        sc.addView(st);
                        String range = dateRange(s);
                        if (!range.isEmpty()) {
                            TextView sd = Ui.text(this, range, 12, Theme.sub(this));
                            sd.setPadding(0, Theme.dp(this, 4), 0, 0);
                            sc.addView(sd);
                        }

                        LinearLayout rowWrap = Ui.row(this);

                        TextView scheck = Ui.text(this, s.done ? "✓" : "", 15, Theme.bg(this));
                        scheck.setGravity(Gravity.CENTER);
                        GradientDrawable scg = new GradientDrawable();
                        scg.setShape(GradientDrawable.OVAL);
                        scg.setColor(s.done ? Theme.text(this) : Color.TRANSPARENT);
                        scg.setStroke(Theme.dp(this, 2), s.done ? Theme.text(this) : Theme.sub(this));
                        scheck.setBackground(scg);
                        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                                Theme.dp(this, 26), Theme.dp(this, 26));
                        sclp.rightMargin = Theme.dp(this, 10);
                        scheck.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                s.done = !s.done;
                                Store.get(MainActivity.this).saveStage(s);
                                refresh();
                            }
                        });
                        rowWrap.addView(scheck, sclp);

                        rowWrap.addView(sc, new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                        TextView sPlus = Ui.text(this, "＋", 20, Theme.sub(this));
                        sPlus.setGravity(Gravity.CENTER);
                        sPlus.setPadding(Theme.dp(this, 8), 0, Theme.dp(this, 2), 0);
                        sPlus.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                openStageDialog(p, null);
                            }
                        });
                        rowWrap.addView(sPlus);

                        final long sid = s.id;
                        View sdel = deleteButton();
                        sdel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Store.get(MainActivity.this).deleteStage(sid);
                                refresh();
                            }
                        });
                        SwipeRow sw = new SwipeRow(this, rowWrap, sdel);
                        sw.setOnRowClick(new Runnable() {
                            @Override
                            public void run() {
                                openStageDialog(p, s);
                            }
                        });
                        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        swp.bottomMargin = Theme.dp(this, 6);
                        sw.setLayoutParams(swp);
                        lineRow.addView(sw, new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                        flow.addView(lineRow);
                    }
                    card.addView(flow);
                }
            }
            View pdel = deleteButton();
            pdel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("删除「" + p.title + "」和它的所有阶段？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    String gp = p.goal == null || p.goal.isEmpty() ? "" : p.goal;
                                    if (!p.deadline.isEmpty()) {
                                        gp = (gp.isEmpty() ? "" : gp + " · ") + p.deadline + " 前";
                                    }
                                    Store.get(MainActivity.this).trashAdd("计划", p.title, gp);
                                    Store.get(MainActivity.this).deletePlan(p.id);
                                    refresh();
                                }
                            }).show();
                }
            });
            SwipeRow psw = new SwipeRow(this, card, pdel);
            psw.setOnRowClick(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(MainActivity.this, PlanEditActivity.class);
                    i.putExtra("id", p.id);
                    startActivity(i);
                }
            });
            psw.setTag("p" + p.id);
            psw.setSwipeEnabled(!open);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Theme.dp(this, 10);
            listBox.addView(psw, lp);
        }
    }

    private String dateRange(Stage s) {
        boolean hasA = s.sDate != null && !s.sDate.isEmpty();
        boolean hasB = s.eDate != null && !s.eDate.isEmpty();
        if (hasA && hasB) return s.sDate + "  ~  " + s.eDate;
        if (hasA) return s.sDate;
        if (hasB) return s.eDate;
        return "";
    }

    // ---------- 阶段对话框 ----------

    private interface DatePick {
        void onPick(String d);
    }

    private void openStageDialog(final Plan p, final Stage exist) {
        LinearLayout box = Ui.col(this);
        box.setPadding(Theme.dp(this, 20), Theme.dp(this, 8), Theme.dp(this, 20), Theme.dp(this, 18));

        box.addView(Ui.label(this, "阶段名"));
        final EditText name = Ui.input(this, "比如 预约科目一");
        if (exist != null) name.setText(exist.title);
        box.addView(name);

        final String[] dates = new String[2];
        dates[0] = exist != null ? exist.sDate : "";
        dates[1] = exist != null ? exist.eDate : "";
        if (exist == null && dates[0].isEmpty()) {
            List<Stage> st = Store.get(this).stages(p.id);
            if (!st.isEmpty()) dates[0] = st.get(st.size() - 1).eDate;
        }

        box.addView(Ui.label(this, "开始日期"));
        final TextView startBtn = Ui.button(this, dates[0].isEmpty() ? "选择开始日期" : dates[0]);
        box.addView(startBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 44)));

        box.addView(Ui.label(this, "结束日期"));
        final TextView endBtn = Ui.button(this, dates[1].isEmpty() ? "选择结束日期" : dates[1]);
        box.addView(endBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 44)));

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate(dates[0], new DatePick() {
                    @Override
                    public void onPick(String d) {
                        // 再选同一个日期就是取消
                        if (d.equals(dates[0])) {
                            dates[0] = "";
                            startBtn.setText("选择开始日期");
                        } else {
                            dates[0] = d;
                            startBtn.setText(d);
                        }
                    }
                });
            }
        });
        endBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate(dates[1], new DatePick() {
                    @Override
                    public void onPick(String d) {
                        if (d.equals(dates[1])) {
                            dates[1] = "";
                            endBtn.setText("选择结束日期");
                        } else {
                            dates[1] = d;
                            endBtn.setText(d);
                        }
                    }
                });
            }
        });

        final AlertDialog[] dlgRef = new AlertDialog[1];

        LinearLayout btns = Ui.row(this);
        btns.setPadding(0, Theme.dp(this, 16), 0, 0);
        Button cancel = Ui.button(this, "取消");
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dlgRef[0] != null) dlgRef[0].dismiss();
            }
        });
        Button ok = Ui.primary(this, "保存");
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = name.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(MainActivity.this, "阶段名不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                Stage s = exist != null ? exist : new Stage();
                s.planId = p.id;
                s.title = title;
                s.sDate = dates[0];
                s.eDate = dates[1];
                if (!s.sDate.isEmpty() && !s.eDate.isEmpty()
                        && s.sDate.compareTo(s.eDate) > 0) {
                    Toast.makeText(MainActivity.this, "开始日期不能晚于结束日期",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Store.get(MainActivity.this).saveStage(s);
                expanded.add(p.id);
                if (dlgRef[0] != null) dlgRef[0].dismiss();
                refresh();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        blp.rightMargin = Theme.dp(this, 10);
        btns.addView(cancel, blp);
        btns.addView(ok, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(btns);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(exist == null ? "添加阶段" : "编辑阶段");
        builder.setView(box);
        dlgRef[0] = builder.create();
        dlgRef[0].show();
    }

    private void pickDate(String cur, final DatePick cb) {
        Calendar c = Calendar.getInstance();
        try {
            if (cur != null && cur.length() >= 10) {
                String[] a = cur.split("-");
                c.set(Integer.parseInt(a[0]), Integer.parseInt(a[1]) - 1, Integer.parseInt(a[2]));
            }
        } catch (Exception ignored) {
        }
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int y, int m, int day) {
                cb.onPick(String.format(Locale.CHINA, "%04d-%02d-%02d", y, m + 1, day));
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ---------- 小部件 ----------

    private TextView empty(String s) {
        TextView tv = Ui.text(this, s, 15, Theme.sub(this));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, Theme.dp(this, 60), 0, 0);
        return tv;
    }

    /** 流程图节点：竖线 + 圆点，首节点上半段 / 尾节点下半段不画线 */
    static class DotView extends View {
        private final boolean first, last, done, dot;
        private final android.graphics.Paint p =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        DotView(android.content.Context c, boolean first, boolean last, boolean done, boolean dot) {
            super(c);
            this.first = first;
            this.last = last;
            this.done = done;
            this.dot = dot;
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dp(getContext(), 2));
            p.setColor(Theme.line(getContext()));
            if (!first) cv.drawLine(cx, 0, cx, cy, p);
            if (!last) cv.drawLine(cx, cy, cx, getHeight(), p);
            if (!dot) return;
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(done ? Theme.text(getContext()) : Theme.card(getContext()));
            cv.drawCircle(cx, cy, Theme.dp(getContext(), 6), p);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dp(getContext(), 1.5f));
            p.setColor(done ? Theme.text(getContext()) : Theme.sub(getContext()));
            cv.drawCircle(cx, cy, Theme.dp(getContext(), 6), p);
        }
    }
}
