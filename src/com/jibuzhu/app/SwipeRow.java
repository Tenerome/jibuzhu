package com.jibuzhu.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * 一行可左滑的容器：向左滑时内容与右侧「删除」按钮同速一起左移，
 * 按钮平时停在屏幕右侧外面（被裁掉），滑出来多少就露出多少；向右滑同理退回。
 */
public class SwipeRow extends FrameLayout {

    private final int actionWidth;
    private float downX, downY;
    private float startTx = 0;
    private boolean dragging = false;
    private boolean open = false;
    private boolean swipeEnabled = true;
    private final int slop;
    private View content;
    private View action;
    private Runnable onClick;

    public SwipeRow(Context c, View content, View action) {
        super(c);
        this.content = content;
        this.action = action;
        actionWidth = Theme.dp(c, 88);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(true);

        addView(content, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(action, new FrameLayout.LayoutParams(
                actionWidth, LayoutParams.MATCH_PARENT));
    }

    /** 删除按钮放在屏幕右侧之外，靠 translationX 移进来 */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        content.layout(0, 0, w, h);
        action.layout(w, 0, w + actionWidth, h);
    }

    /** false 时这一行不响应左滑（展开的计划卡用这个） */
    public void setSwipeEnabled(boolean on) {
        this.swipeEnabled = on;
        if (!on) {
            content.animate().translationX(0).setDuration(120).start();
            action.animate().translationX(0).setDuration(120).start();
            open = false;
        }
    }

    public void setOnRowClick(Runnable r) {
        this.onClick = r;
        content.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (open) {
                    close();
                } else if (SwipeRow.this.onClick != null) {
                    SwipeRow.this.onClick.run();
                }
            }
        });
    }

    public void close() {
        open = false;
        content.animate().translationX(0).setDuration(160).start();
        action.animate().translationX(0).setDuration(160).start();
    }

    public void open() {
        open = true;
        content.animate().translationX(-actionWidth).setDuration(160).start();
        action.animate().translationX(-actionWidth).setDuration(160).start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!swipeEnabled) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                startTx = content.getTranslationX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    dragging = true;
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!swipeEnabled) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                startTx = content.getTranslationX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - downX;
                if (!dragging) {
                    if (Math.abs(dx) > slop) dragging = true;
                    else return true;
                }
                float tx = startTx + dx;
                if (tx > 0) tx = 0;
                if (tx < -actionWidth) tx = -actionWidth;
                content.setTranslationX(tx);
                action.setTranslationX(tx);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float tx = content.getTranslationX();
                if (tx < -actionWidth / 2f) open();
                else close();
                dragging = false;
                break;
            }
        }
        return true;
    }
}
