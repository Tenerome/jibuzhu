package com.jibuzhu.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jibuzhu.app.data.Plan;
import com.jibuzhu.app.data.Store;

import java.util.Calendar;
import java.util.Locale;

/** 新建 / 编辑计划：名称 + 最终目标 + 截止日期。阶段在主页面展开后添加。 */
public class PlanEditActivity extends Activity {

    private long id;
    private Plan p;
    private EditText titleEt, goalEt;
    private TextView deadlineBtn;
    private String deadline = "";

    @Override
    protected void onCreate(Bundle b) {
        if (Theme.dark(this)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Theme.applyBars(this);
        super.onCreate(b);
        id = getIntent().getLongExtra("id", 0);
        p = id > 0 ? Store.get(this).plan(id) : new Plan();
        if (p == null) p = new Plan();
        deadline = p.deadline == null ? "" : p.deadline;
        build();
    }

    private void build() {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Theme.bg(this));

        LinearLayout top = Ui.row(this);
        top.setPadding(Theme.dp(this, 14), Theme.dp(this, 12), Theme.dp(this, 18), Theme.dp(this, 6));
        TextView back = Ui.text(this, "←", 22, Theme.text(this));
        back.setPadding(Theme.dp(this, 6), 0, Theme.dp(this, 16), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        top.addView(Ui.text(this, id > 0 ? "编辑计划" : "新建计划", 20, Theme.text(this)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout body = Ui.col(this);
        body.setPadding(Theme.dp(this, 16), Theme.dp(this, 6), Theme.dp(this, 16), Theme.dp(this, 30));
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        body.addView(Ui.label(this, "计划名"));
        titleEt = Ui.input(this, "比如 可信");
        titleEt.setText(p.title);
        body.addView(titleEt);

        body.addView(Ui.label(this, "最终目标"));
        goalEt = Ui.input(this, "比如 通过可信认证");
        goalEt.setText(p.goal);
        goalEt.setMinLines(2);
        body.addView(goalEt);

        body.addView(Ui.label(this, "截止日期"));
        deadlineBtn = Ui.button(this, deadline.isEmpty() ? "选择截止日期" : deadline);
        deadlineBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        deadlineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar c = Calendar.getInstance();
                try {
                    if (deadline.length() >= 10) {
                        String[] a = deadline.split("-");
                        c.set(Integer.parseInt(a[0]), Integer.parseInt(a[1]) - 1,
                                Integer.parseInt(a[2]));
                    }
                } catch (Exception ignored) {
                }
                new DatePickerDialog(PlanEditActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int y, int m, int d) {
                        String picked = String.format(Locale.CHINA, "%04d-%02d-%02d", y, m + 1, d);
                        // 再选同一个日期就是取消
                        if (picked.equals(deadline)) {
                            deadline = "";
                            deadlineBtn.setText("选择截止日期");
                        } else {
                            deadline = picked;
                            deadlineBtn.setText(picked);
                        }
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                        c.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        body.addView(deadlineBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 46)));

        TextView hint = Ui.text(this, "存好之后回到「计划」，展开它，点右侧 ＋ 一个个加阶段", 12, Theme.sub(this));
        hint.setPadding(0, Theme.dp(this, 14), 0, 0);
        body.addView(hint);

        Button done = Ui.primary(this, "保存");
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 50));
        dp.topMargin = Theme.dp(this, 24);
        done.setLayoutParams(dp);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        body.addView(done);
    }

    private void save() {
        String title = titleEt.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "给计划起个名", Toast.LENGTH_SHORT).show();
            return;
        }
        p.title = title;
        p.goal = goalEt.getText().toString().trim();
        p.deadline = deadline;
        Store.get(this).savePlan(p);
        finish();
    }
}
