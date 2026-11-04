package com.jibuzhu.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 腾讯位置服务 WebService：地点搜索 / 逆地址解析。 */
public class TencentMap {

    public static final String KEY = "65XBZ-ZMLLQ-OPZ5P-BF6XO-LRCXO-TMFBD";
    private static final String BASE = "https://apis.map.qq.com/ws";

    public static class Place {
        public String title = "";
        public String address = "";
        public double lat;
        public double lng;

        public String label() {
            return address.isEmpty() ? title : title + " · " + address;
        }
    }

    /** 关键词搜索，优先按当前位置附近排序；附近没结果再放宽到全国 */
    public static List<Place> search(String keyword, double lat, double lng) throws Exception {
        List<Place> out = searchNear(keyword, lat, lng);
        if (out.isEmpty() && (lat != 0 || lng != 0)) out = searchNear(keyword, 0, 0);
        return out;
    }

    /**
     * 这个 key 的 place/v1/search 强制要求 boundary，光给 location+radius 会报
     * 「参数错误，以下参数不合法：boundary」，所以附近搜索也要写成 boundary=nearby(...)。
     */
    private static List<Place> searchNear(String keyword, double lat, double lng) throws Exception {
        StringBuilder u = new StringBuilder(BASE + "/place/v1/search?");
        u.append("keyword=").append(URLEncoder.encode(keyword, "UTF-8"));
        u.append("&page_size=15&page_index=1&key=").append(KEY);
        if (lat != 0 || lng != 0) {
            u.append("&boundary=nearby(").append(lat).append(",").append(lng).append(",50000)");
        } else {
            u.append("&boundary=region(").append(URLEncoder.encode("中国", "UTF-8")).append(",0)");
        }
        JSONObject o = new JSONObject(get(u.toString()));
        List<Place> out = new ArrayList<>();
        if (o.optInt("status") != 0) {
            throw new Exception(o.optString("message", "腾讯地图返回 " + o.optInt("status")));
        }
        JSONArray arr = o.optJSONArray("data");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject d = arr.getJSONObject(i);
            JSONObject loc = d.optJSONObject("location");
            if (loc == null) continue;
            Place p = new Place();
            p.title = d.optString("title");
            p.address = d.optString("address");
            p.lat = loc.optDouble("lat");
            p.lng = loc.optDouble("lng");
            if (p.title.isEmpty() || (p.lat == 0 && p.lng == 0)) continue;
            out.add(p);
        }
        return out;
    }

    /** 坐标反查地名 */
    public static Place reverse(double lat, double lng) throws Exception {
        String u = BASE + "/geocoder/v1/?location=" + lat + "," + lng + "&key=" + KEY;
        JSONObject o = new JSONObject(get(u));
        if (o.optInt("status") != 0) throw new Exception(o.optString("message"));
        JSONObject r = o.optJSONObject("result");
        Place p = new Place();
        p.lat = lat;
        p.lng = lng;
        if (r != null) {
            p.address = r.optString("address");
            JSONObject ac = r.optJSONObject("address_component");
            p.title = ac != null ? ac.optString("street") : "";
            if (p.title.isEmpty()) p.title = p.address;
        }
        if (p.title.isEmpty()) p.title = "选定位置";
        return p;
    }

    /** 静态地图截图，带一个红点标记 */
    public static android.graphics.Bitmap staticMap(double lat, double lng, int w, int h) throws Exception {
        String u = BASE + "/staticmap/v2/?center=" + lat + "," + lng
                + "&zoom=16&size=" + w + "*" + h + "&scale=2&key=" + KEY
                + "&markers=size:large|color:red|" + lat + "," + lng;
        byte[] b = getBytes(u);
        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
        if (bm == null) throw new Exception("地图返回的不是图片");
        return bm;
    }

    private static String get(String url) throws Exception {
        return new String(getBytes(url), "UTF-8");
    }

    private static byte[] getBytes(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "jibuzhu-app");
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) throw new Exception("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } finally {
            conn.disconnect();
        }
    }
}
