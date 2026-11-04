package com.jibuzhu.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * 滚轮时间选择：时 / 分两列，默认停在当前时间。
 *
 * 系统 NumberPicker 在部分机型上把内部画笔藏了起来（连 mSelectorWheelPaint 字段都没有），
 * 没法调上下两行的字号和透明度，所以这里直接自绘一个滚轮：中间一行深色大字，
 * 上下两行随距离变小、变淡，两条分割线只画 1 像素。
 */
public class TimeSpinner extends android.widget.LinearLayout {

    private final Wheel hour;
    private final Wheel minute;

    public TimeSpinner(Context c, String hhmm) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int h;
        int m;
        if (hhmm != null && hhmm.contains(":")) {
            h = 0;
            m = 0;
            try {
                String[] a = hhmm.split(":");
                h = Integer.parseInt(a[0].trim());
                m = Integer.parseInt(a[1].trim());
            } catch (Exception ignored) {
            }
        } else {
            Calendar now = Calendar.getInstance();
            h = now.get(Calendar.HOUR_OF_DAY);
            m = now.get(Calendar.MINUTE);
        }
        if (h < 0 || h > 23) h = 0;
        if (m < 0 || m > 59) m = 0;

        int basePx = Theme.sp(c, 20);
        int itemPx = Theme.dp(c, 40);
        int deep = Theme.text(c);
        int line = Theme.line(c);

        hour = new Wheel(c);
        hour.setStyle(basePx, itemPx, deep, line);
        hour.setRange(0, 23);
        hour.setValue(h);

        minute = new Wheel(c);
        minute.setStyle(basePx, itemPx, deep, line);
        minute.setRange(0, 59);
        minute.setValue(m);

        addView(hour, new LinearLayout.LayoutParams(0, Theme.dp(c, 120), 1));
        TextView colon = new TextView(c);
        colon.setText(":");
        colon.setTextColor(Theme.text(c));
        colon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        addView(colon);
        addView(minute, new LinearLayout.LayoutParams(0, Theme.dp(c, 120), 1));

        setBackground(Theme.strokeBg(Theme.card(c), Theme.line(c), 12, c));
    }

    /** 自绘滚轮：中间深、上下淡，松手后吸附到整格。 */
    static class Wheel extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int min = 0;
        private int max = 59;
        private int value = 0;
        private float offset = 0f;
        private float itemPx = 120f;
        private float basePx = 54f;
        private int deep = 0xff434343;
        private float lastY = 0f;
        private float downY = 0f;
        private ValueAnimator anim;
        private android.view.VelocityTracker tracker;

        Wheel(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
            linePaint.setStyle(Paint.Style.FILL);
        }

        void setStyle(int basePx, int itemPx, int deep, int line) {
            this.basePx = basePx;
            this.itemPx = itemPx;
            this.deep = deep;
            linePaint.setColor(line);
            invalidate();
        }

        void setRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        void setValue(int v) {
            cancelAnim();
            value = Math.max(min, Math.min(max, v));
            offset = 0f;
            invalidate();
        }

        int getValue() {
            return value;
        }

        private void cancelAnim() {
            if (anim != null) {
                anim.cancel();
                anim = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;


            for (int i = -2; i <= 2; i++) {
                int v = value + i;
                if (v < min || v > max) continue;
                float dy = i * itemPx + offset;
                float cyy = cy + dy;
                float t = Math.min(1f, Math.abs(dy) / itemPx);
                int alpha = (int) (255 * (1f - 0.70f * t));
                paint.setTextSize(basePx * (1f - 0.40f * t));
                paint.setTypeface(i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                paint.setColor((deep & 0x00ffffff) | (alpha << 24));
                String s = String.format(Locale.CHINA, "%02d", v);
                canvas.drawText(s, cx, cyy + paint.getTextSize() * 0.35f, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (tracker == null) tracker = android.view.VelocityTracker.obtain();
            tracker.addMovement(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelAnim();
                    // 滚轮在 ScrollView 里，不抢事件的话竖划会被外层滚动吞掉，导致滚不动
                    ViewParent vp = getParent();
                    if (vp != null) vp.requestDisallowInterceptTouchEvent(true);
                    lastY = e.getY();
                    downY = lastY;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getY() - lastY;
                    lastY = e.getY();
                    offset += dy;
                    // 每滚满一格就把数值推进一格，可以一直拖下去
                    while (offset >= itemPx) {
                        int nv = clamp(value - 1);
                        if (nv == value) {
                            offset = itemPx - 0.01f;
                            break;
                        }
                        value = nv;
                        offset -= itemPx;
                    }
                    while (offset <= -itemPx) {
                        int nv = clamp(value + 1);
                        if (nv == value) {
                            offset = -itemPx + 0.01f;
                            break;
                        }
                        value = nv;
                        offset += itemPx;
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    ViewParent p = getParent();
                    if (p != null) p.requestDisallowInterceptTouchEvent(false);
                    tracker.computeCurrentVelocity(1000);
                    float vy = tracker.getYVelocity();
                    tracker.recycle();
                    tracker = null;
                    boolean fling = Math.abs(vy) > 700f;
                    if (fling) {
                        int extra = Math.round(vy / 2600f);
                        if (extra != 0) value = clamp(value - extra);
                    } else if (Math.abs(e.getY() - downY) < itemPx * 0.3f
                            && Math.abs(offset) < itemPx * 0.3f) {
                        int idx = Math.round((downY - getHeight() / 2f) / itemPx);
                        if (idx != 0) value = clamp(value + idx);
                    }
                    slideBack();
                    return true;
                }
                default:
                    return super.onTouchEvent(e);
            }
        }

        private int clamp(int v) {
            return Math.max(min, Math.min(max, v));
        }

        /** 松手后把偏移量平滑归零，看起来像吸附到整格 */
        private void slideBack() {
            if (offset == 0f) {
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(offset, 0f);
            anim.setDuration(160);
            anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    offset = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }
    }

    public void setTime(String hhmm) {
        if (hhmm == null || !hhmm.contains(":")) return;
        try {
            String[] a = hhmm.split(":");
            int h = Integer.parseInt(a[0].trim());
            int m = Integer.parseInt(a[1].trim());
            if (h >= 0 && h <= 23) hour.setValue(h);
            if (m >= 0 && m <= 59) minute.setValue(m);
        } catch (Exception ignored) {
        }
    }

    /** "HH:mm" */
    public String value() {
        return String.format(Locale.CHINA, "%02d:%02d", hour.getValue(), minute.getValue());
    }
}
