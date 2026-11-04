package com.jibuzhu.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Util {
    public static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    public static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.CHINA);

    public static String today() {
        return DAY.format(new Date());
    }

    public static int toMinutes(String hhmm) {
        if (hhmm == null || !hhmm.contains(":")) return -1;
        String[] a = hhmm.split(":");
        try {
            return Integer.parseInt(a[0].trim()) * 60 + Integer.parseInt(a[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public static String normalizeHm(String hm) {
        int m = toMinutes(hm);
        if (m < 0) return "";
        return String.format(Locale.CHINA, "%02d:%02d", m / 60, m % 60);
    }

    /** 当前日期是否匹配周期 */
    public static boolean dayMatches(String rep, Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK); // 1=周日 ... 7=周六
        if ("daily".equals(rep)) return true;
        if ("work5".equals(rep)) return d >= 2 && d <= 6;   // 双休：周一到周五
        if ("work6".equals(rep)) return d >= 2 && d <= 7;   // 单休：周一到周六
        if ("weekend".equals(rep)) return d == 1 || d == 7; // 周末
        return true; // once 由 lastFired 去重
    }

    /** 当前时刻是否在 [start, end] 窗口内 */
    public static boolean inWindow(String start, String end, Calendar now) {
        int s = toMinutes(start);
        int e = toMinutes(end);
        if (s < 0 || e < 0) return true;
        int n = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (s <= e) return n >= s && n <= e;
        return n >= s || n <= e; // 跨零点
    }

    public static String repName(String rep) {
        if ("daily".equals(rep)) return "每天";
        if ("work5".equals(rep)) return "双休(周一至周五)";
        if ("work6".equals(rep)) return "单休(周一至周六)";
        if ("weekend".equals(rep)) return "周末";
        return "单次";
    }

    /** 两点间距离（米） */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
