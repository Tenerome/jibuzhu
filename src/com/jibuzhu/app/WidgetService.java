package com.jibuzhu.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.jibuzhu.app.data.Plan;
import com.jibuzhu.app.data.Stage;
import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;

import java.util.ArrayList;
import java.util.List;

/** 桌面组件的列表数据：待办按时间排，计划只显示一行（折叠）。 */
public class WidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent);
    }

    private static class Factory implements RemoteViewsFactory {

        private final Context c;
        private final List<Item> items = new ArrayList<Item>();
        private final int tab;

        Factory(Context c, Intent intent) {
            this.c = c;
            this.tab = intent == null ? 0 : intent.getIntExtra(TodoWidget.EXTRA_TAB, 0);
        }

        static class Item {
            String title;
            String sub;
            long todoId;
            long planId;
            boolean done;
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDataSetChanged() {
            items.clear();
            Store st = Store.get(c);
            if (tab == 0) {
                for (Todo t : st.todos()) {
                    Item it = new Item();
                    it.title = t.title;
                    it.sub = condition(t);
                    it.todoId = t.id;
                    it.done = t.done;
                    items.add(it);
                }
            } else {
                for (Plan p : st.plans()) {
                    Item it = new Item();
                    it.title = p.title;
                    List<Stage> stages = st.stages(p.id);
                    int doneN = 0;
                    for (Stage s : stages) if (s.done) doneN++;
                    String goal = p.goal == null ? "" : p.goal.trim();
                    it.sub = (goal.isEmpty() ? "" : goal + " · ")
                            + doneN + "/" + stages.size() + " 阶段";
                    it.planId = p.id;
                    items.add(it);
                }
            }
        }

        private static String condition(Todo t) {
            if (t.isCheckin()) {
                return "连上/断开 " + t.wifi + " 打卡";
            }
            if (t.isLocation()) {
                return "到 " + t.place + " 提醒";
            }
            return t.winStart + " 提醒";
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;
            Item it = items.get(position);
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_item);
            rv.setTextViewText(R.id.wi_title, it.title);
            rv.setTextViewText(R.id.wi_sub, it.sub);

            // 已完成的划掉 + 变灰
            if (it.done) {
                rv.setTextColor(R.id.wi_title, 0x66FFFFFF);
                rv.setTextViewText(R.id.wi_mark, "✓");
                rv.setViewVisibility(R.id.wi_mark, android.view.View.VISIBLE);
            } else {
                rv.setTextColor(R.id.wi_title, 0xFFFFFFFF);
                rv.setViewVisibility(R.id.wi_mark, android.view.View.GONE);
            }

            // 点击跳到 App 对应位置
            Intent fill = new Intent();
            fill.putExtra("focus_tab", it.planId > 0 ? 1 : 0);
            fill.putExtra("focus_todo", it.todoId);
            fill.putExtra("focus_plan", it.planId);
            rv.setOnClickFillInIntent(R.id.wi_root, fill);
            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
