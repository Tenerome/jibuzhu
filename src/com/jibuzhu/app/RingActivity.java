package com.jibuzhu.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;
import com.jibuzhu.app.trigger.AlarmScheduler;
import com.jibuzhu.app.trigger.RingService;

/** 全屏提醒页：响铃 + 一个 check 按钮停止铃声并结束本次提醒。锁屏也能显示。 */
public class RingActivity extends Activity {

    /** 界面是否在前台，响铃服务用它判断要不要继续尝试拉起 */
    public static volatile boolean alive = false;

    private long todoId;

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        alive = false;
    }

    @Override
    protected void onCreate(Bundle b) {
        if (Theme.dark(this)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Theme.applyBars(this);
        super.onCreate(b);
        alive = true;
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        // 关键：不能带 FLAG_DISMISS_KEYGUARD —— 它会让系统直接解除锁屏（等于替用户解锁），
        // 页面一退出就直接落到桌面/上一个应用，用户看到的就是"锁屏没弹出来"。
        // 只保留"显示在锁屏之上"+"点亮屏幕"，锁屏本身保持在后面。
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        todoId = getIntent().getLongExtra("todo_id", -1);
        String mode = getIntent().getStringExtra("mode");
        Todo t = Store.get(this).todo(todoId);

        setContentView(RingUi.build(this, t, mode, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        startRing();
    }

    private void startRing() {
        try {
            Intent si = new Intent(this, RingService.class);
            si.putExtra(AlarmScheduler.EXTRA_ID, todoId);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(si);
            else startService(si);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        // 不在销毁时停铃：界面被系统回收时铃声继续，直到用户点按钮
        super.onDestroy();
    }
}
