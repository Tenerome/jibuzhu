package com.jibuzhu.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * 桌面组件：待办 / 计划 两个页签，列表可上下滑，点条目跳到 App 对应位置。
 *
 * 页签切换靠顶部两个 TextView 的 PendingIntent 发广播回来改状态，
 * RemoteViews 处理不了手势滑动，所以这里用点击切换。
 */
public class TodoWidget extends AppWidgetProvider {

    public static final String ACTION_TAB = "com.jibuzhu.app.WIDGET_TAB";
    public static final String ACTION_UPDATE = "com.jibuzhu.app.WIDGET_UPDATE";
    public static final String EXTRA_TAB = "tab";

    /** 每个组件实例当前在哪个页签（0 待办 / 1 计划） */
    private static int tabOf(Context c, int id) {
        return c.getSharedPreferences("widget", Context.MODE_PRIVATE).getInt("tab_" + id, 0);
    }

    private static void setTab(Context c, int id, int tab) {
        c.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
                .putInt("tab_" + id, tab).apply();
    }

    /** 背景透明度档位 0..4（0 全透明 → 4 最深） */
    public static int bgLevel(Context c) {
        return c.getSharedPreferences("widget", Context.MODE_PRIVATE).getInt("bg_level", 2);
    }

    public static void setBgLevel(Context c, int level) {
        c.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
                .putInt("bg_level", Math.max(0, Math.min(4, level))).apply();
    }

    public static boolean enabled(Context c) {
        return c.getSharedPreferences("widget", Context.MODE_PRIVATE)
                .getBoolean("enabled", true);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            render(c, m, id);
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TAB.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int tab = intent.getIntExtra(EXTRA_TAB, 0);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setTab(c, id, tab);
                render(c, AppWidgetManager.getInstance(c), id);
            }
            return;
        }
        if (ACTION_UPDATE.equals(action)) {
            refreshAll(c);
            return;
        }
        super.onReceive(c, intent);
    }

    /** 数据变了（增删改待办/计划）时叫一声，让桌面组件重画 */
    public static void refreshAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            ComponentName cn = new ComponentName(c, TodoWidget.class);
            int[] ids = m.getAppWidgetIds(cn);
            for (int id : ids) {
                render(c, m, id);
            }
        } catch (Exception ignored) {
        }
    }

    private static void render(Context c, AppWidgetManager m, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_todo);
        int tab = tabOf(c, id);

        // 背景透明度档位
        int bg = bgLevel(c);
        int bgRes = bg == 0 ? R.drawable.widget_bg_0
                : bg == 1 ? R.drawable.widget_bg_1
                : bg == 2 ? R.drawable.widget_bg_2
                : bg == 3 ? R.drawable.widget_bg_3
                : R.drawable.widget_bg_4;
        rv.setInt(R.id.w_root, "setBackgroundResource", bgRes);

        // 顶部页签高亮
        rv.setTextViewText(R.id.w_tab_todo, "待办");
        rv.setTextViewText(R.id.w_tab_plan, "计划");
        rv.setTextColor(R.id.w_tab_todo, tab == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
        rv.setTextColor(R.id.w_tab_plan, tab == 1 ? 0xFFFFFFFF : 0x80FFFFFF);

        rv.setOnClickPendingIntent(R.id.w_tab_todo, tabIntent(c, id, 0));
        rv.setOnClickPendingIntent(R.id.w_tab_plan, tabIntent(c, id, 1));
        rv.setTextViewText(R.id.w_empty, tab == 0 ? "还没有待办，去 App 里加一条" : "还没有计划，去 App 里加一个");

        if (!enabled(c)) {
            rv.setViewVisibility(R.id.w_list, android.view.View.GONE);
            rv.setViewVisibility(R.id.w_off, android.view.View.VISIBLE);
            m.updateAppWidget(id, rv);
            return;
        }
        rv.setViewVisibility(R.id.w_list, android.view.View.VISIBLE);
        rv.setViewVisibility(R.id.w_off, android.view.View.GONE);

        // 点空白处也能打开 App
        rv.setOnClickPendingIntent(R.id.w_empty, openIntent(c, id));

        // ListView 的数据由 WidgetService 提供；换 tab 时换个 uri 让它重建
        Intent svc = new Intent(c, WidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        svc.putExtra(EXTRA_TAB, tab);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME) + "&tab=" + tab));
        rv.setRemoteAdapter(R.id.w_list, svc);
        rv.setEmptyView(R.id.w_list, R.id.w_empty);
        rv.setPendingIntentTemplate(R.id.w_list, openIntent(c, id, true));

        m.updateAppWidget(id, rv);
        m.notifyAppWidgetViewDataChanged(id, R.id.w_list);
    }

    private static PendingIntent openIntent(Context c, int id) {
        return openIntent(c, id, false);
    }

    private static PendingIntent openIntent(Context c, int id, boolean mutable) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        f |= mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, id + 5000, i, f);
    }

    private static PendingIntent tabIntent(Context c, int id, int tab) {
        Intent i = new Intent(c, TodoWidget.class);
        i.setAction(ACTION_TAB);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        i.putExtra(EXTRA_TAB, tab);
        i.setData(Uri.parse("jibuzhu://widget/tab/" + id + "/" + tab));
        return PendingIntent.getBroadcast(c, id * 10 + tab, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
