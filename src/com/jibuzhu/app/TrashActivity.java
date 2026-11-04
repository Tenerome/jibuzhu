package com.jibuzhu.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jibuzhu.app.data.Store;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 回收站：删掉的待办和计划留个记录在这里，不支持恢复。 */
public class TrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Theme.applyBars(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Theme.bg(this));

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Theme.dp(this, 20), Theme.dp(this, 18), Theme.dp(this, 20), Theme.dp(this, 6));
        bar.addView(Ui.text(this, "回收站", 19, Theme.text(this)));
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));
        if (!Store.get(this).trashList().isEmpty()) {
            TextView clear = Ui.text(this, "清理", 14, Theme.danger(this));
            clear.setPadding(Theme.dp(this, 12), Theme.dp(this, 6), Theme.dp(this, 12), Theme.dp(this, 6));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new android.app.AlertDialog.Builder(TrashActivity.this)
                            .setMessage("清空回收站里的所有记录？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    Store.get(TrashActivity.this).trashClear();
                                    buildUi();
                                }
                            }).show();
                }
            });
            bar.addView(clear);
        }
        TextView back = Ui.text(this, "返回", 14, Theme.sub(this));
        back.setPadding(Theme.dp(this, 12), Theme.dp(this, 6), 0, Theme.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back);
        root.addView(bar);

        TextView hint = Ui.text(this, "删掉的待办和计划会留在这里做个记录，不支持恢复", 13, Theme.sub(this));
        hint.setPadding(Theme.dp(this, 20), 0, Theme.dp(this, 20), Theme.dp(this, 8));
        root.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout list = Ui.col(this);
        list.setPadding(Theme.dp(this, 16), Theme.dp(this, 4), Theme.dp(this, 16), Theme.dp(this, 30));

        List<Store.Trash> items = Store.get(this).trashList();
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, "回收站是空的", 14, Theme.sub(this));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Theme.dp(this, 60), 0, 0);
            list.addView(empty);
        } else {
            SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            for (Store.Trash t : items) {
                LinearLayout card = Ui.card(this);

                LinearLayout head = Ui.row(this);
                TextView kind = Ui.text(this, t.kind, 12, Theme.sub(this));
                kind.setBackground(Theme.strokeBg(Theme.card(this), Theme.line(this), 8, this));
                kind.setPadding(Theme.dp(this, 8), Theme.dp(this, 2), Theme.dp(this, 8), Theme.dp(this, 2));
                head.addView(kind);
                TextView when = Ui.text(this, f.format(new Date(t.at)) + " 删除", 12, Theme.sub(this));
                when.setPadding(Theme.dp(this, 10), 0, 0, 0);
                head.addView(when);
                card.addView(head);

                TextView name = Ui.text(this, t.title == null || t.title.isEmpty() ? "（没有名称）" : t.title, 17, Theme.text(this));
                name.setPadding(0, Theme.dp(this, 8), 0, 0);
                card.addView(name);

                if (t.sub != null && !t.sub.isEmpty()) {
                    TextView sub = Ui.text(this, t.sub, 13, Theme.sub(this));
                    sub.setPadding(0, Theme.dp(this, 4), 0, 0);
                    card.addView(sub);
                }
                list.addView(card);
            }
        }

        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }
}
