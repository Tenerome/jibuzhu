package com.jibuzhu.app.data;

public class Todo {
    public long id;
    public String title = "";
    public String kind = "time";        // time | location | checkin
    public String rep = "once";         // once | daily
    public String winStart = "00:00";   // 触发时间 / 时间窗口起
    public String winEnd = "23:59";     // 时间窗口止
    public String fallback = "";        // 兜底提醒时间 HH:mm，空=不兜底
    public double lat;
    public double lng;
    public int radius = 300;
    public String place = "";
    public boolean enabled = true;
    public String lastFired = "";       // yyyy-MM-dd
    public boolean done = false;        // 重复型待办"已完成"：变灰置底，次日 0 点复位
    public long doneAt = 0;
    public String ring = "";            // 本地铃声 uri，空=系统闹钟音
    public String wifi = "";
    /** 定位提醒命中后是否顺便打开 WeLink 打卡页 */

    public boolean isLocation() {
        return "location".equals(kind);
    }

    public boolean isCheckin() {
        return "checkin".equals(kind);
    }

    public boolean isRepeat() {
        return !"once".equals(rep);
    }
}
