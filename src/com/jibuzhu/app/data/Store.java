package com.jibuzhu.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 全部数据落在 SQLite，单例。 */
public class Store extends SQLiteOpenHelper {

    private static final String DB_NAME = "jibuzhu.db";
    private static final int VER = 4;
    private static Store INST;

    public static synchronized Store get(Context c) {
        if (INST == null) INST = new Store(c.getApplicationContext());
        return INST;
    }

    private Store(Context c) {
        super(c, DB_NAME, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE todo(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT,kind TEXT,rep TEXT,winStart TEXT,winEnd TEXT,fallback TEXT," +
                "lat REAL,lng REAL,radius INTEGER,place TEXT,enabled INTEGER,lastFired TEXT," +
                "done INTEGER DEFAULT 0,doneAt INTEGER DEFAULT 0,ring TEXT DEFAULT ''," +
                "wifi TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE plan(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,goal TEXT,deadline TEXT,enabled INTEGER)");
        db.execSQL("CREATE TABLE stage(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,planId INTEGER,title TEXT,sDate TEXT,eDate TEXT,done INTEGER)");
        db.execSQL("CREATE TABLE trash(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,title TEXT,sub TEXT,at INTEGER)");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldV, int newV) {
        // 装过新版本又退回旧版本时，数据库版本号会更高。
        // 表结构是向下兼容的（多出来的列旧代码不读），所以直接保留数据，什么都不做。
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            try {
                db.execSQL("ALTER TABLE todo ADD COLUMN done INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE todo ADD COLUMN doneAt INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE todo ADD COLUMN ring TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 3) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS trash(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,title TEXT,sub TEXT,at INTEGER)");
            } catch (Exception ignored) {
            }
        }
        if (oldV < 4) {
            try {
                db.execSQL("ALTER TABLE todo ADD COLUMN wifi TEXT DEFAULT ''");
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- todo ----------

    public List<Todo> todos() {
        List<Todo> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("todo", null, null, null, null, null, "id ASC");
        while (c.moveToNext()) out.add(readTodo(c));
        c.close();
        // 过了 24 点：昨天完成的重复待办复位，回到时间顺序
        long midnight = midnight();
        for (Todo t : out) {
            if (t.done && t.doneAt < midnight) {
                t.done = false;
                t.doneAt = 0;
                saveTodo(t);
            }
        }
        // 未完成按时间先后排；已完成的重复待办排最后
        java.util.Collections.sort(out, new java.util.Comparator<Todo>() {
            @Override
            public int compare(Todo a, Todo b) {
                if (a.done != b.done) return a.done ? 1 : -1;
                int ka = UtilTime(a.winStart), kb = UtilTime(b.winStart);
                if (ka != kb) return ka < kb ? -1 : 1;
                return a.id < b.id ? -1 : (a.id == b.id ? 0 : 1);
            }
        });
        return out;
    }

    private static int UtilTime(String hhmm) {
        if (hhmm == null || !hhmm.contains(":")) return 0;
        try {
            String[] a = hhmm.split(":");
            return Integer.parseInt(a[0].trim()) * 60 + Integer.parseInt(a[1].trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** 今天 0 点毫秒 */
    public static long midnight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public Todo todo(long id) {
        Cursor c = getReadableDatabase().query("todo", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Todo t = c.moveToFirst() ? readTodo(c) : null;
        c.close();
        return t;
    }

    public long saveTodo(Todo t) {
        ContentValues v = new ContentValues();
        v.put("title", t.title);
        v.put("kind", t.kind);
        v.put("rep", t.rep);
        v.put("winStart", t.winStart);
        v.put("winEnd", t.winEnd);
        v.put("fallback", t.fallback);
        v.put("lat", t.lat);
        v.put("lng", t.lng);
        v.put("radius", t.radius);
        v.put("place", t.place);
        v.put("enabled", t.enabled ? 1 : 0);
        v.put("lastFired", t.lastFired);
        v.put("done", t.done ? 1 : 0);
        v.put("doneAt", t.doneAt);
        v.put("ring", t.ring == null ? "" : t.ring);
        v.put("wifi", t.wifi == null ? "" : t.wifi);
        if (t.id > 0) {
            getWritableDatabase().update("todo", v, "id=?", new String[]{String.valueOf(t.id)});
            return t.id;
        }
        long id = getWritableDatabase().insert("todo", null, v);
        t.id = id;
        return id;
    }

    public void deleteTodo(long id) {
        getWritableDatabase().delete("todo", "id=?", new String[]{String.valueOf(id)});
    }

    private Todo readTodo(Cursor c) {
        Todo t = new Todo();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.title = str(c, "title");
        t.kind = str(c, "kind");
        t.rep = str(c, "rep");
        t.winStart = str(c, "winStart");
        t.winEnd = str(c, "winEnd");
        t.fallback = str(c, "fallback");
        t.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
        t.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
        t.radius = c.getInt(c.getColumnIndexOrThrow("radius"));
        t.place = str(c, "place");
        t.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) != 0;
        t.lastFired = str(c, "lastFired");
        t.done = c.getInt(c.getColumnIndexOrThrow("done")) != 0;
        t.doneAt = c.getLong(c.getColumnIndexOrThrow("doneAt"));
        t.ring = str(c, "ring");
        t.wifi = str(c, "wifi");
        return t;
    }

    // ---------- plan ----------

    public List<Plan> plans() {
        List<Plan> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("plan", null, null, null, null, null, "id DESC");
        while (c.moveToNext()) out.add(readPlan(c));
        c.close();
        return out;
    }

    public Plan plan(long id) {
        Cursor c = getReadableDatabase().query("plan", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Plan p = c.moveToFirst() ? readPlan(c) : null;
        c.close();
        return p;
    }

    public long savePlan(Plan p) {
        ContentValues v = new ContentValues();
        v.put("title", p.title);
        v.put("goal", p.goal);
        v.put("deadline", p.deadline);
        v.put("enabled", p.enabled ? 1 : 0);
        if (p.id > 0) {
            getWritableDatabase().update("plan", v, "id=?", new String[]{String.valueOf(p.id)});
            return p.id;
        }
        long id = getWritableDatabase().insert("plan", null, v);
        p.id = id;
        return id;
    }

    public void deletePlan(long id) {
        getWritableDatabase().delete("stage", "planId=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("plan", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- 回收站（只留记录，不支持恢复） ----------

    /** 用户主动删除时记一条，kind 传「待办」或「计划」 */
    public void trashAdd(String kind, String title, String sub) {
        try {
            ContentValues v = new ContentValues();
            v.put("kind", kind);
            v.put("title", title == null ? "" : title);
            v.put("sub", sub == null ? "" : sub);
            v.put("at", System.currentTimeMillis());
            getWritableDatabase().insert("trash", null, v);
        } catch (Exception ignored) {
        }
    }

    /** 清空回收站 */
    public void trashClear() {
        try {
            getWritableDatabase().delete("trash", null, null);
        } catch (Exception ignored) {
        }
    }

    public List<Trash> trashList() {        List<Trash> out = new ArrayList<>();
        try {
            Cursor c = getReadableDatabase().query("trash", null, null, null, null, null, "at DESC, id DESC");
            while (c.moveToNext()) {
                Trash t = new Trash();
                t.id = c.getLong(c.getColumnIndexOrThrow("id"));
                t.kind = str(c, "kind");
                t.title = str(c, "title");
                t.sub = str(c, "sub");
                t.at = c.getLong(c.getColumnIndexOrThrow("at"));
                out.add(t);
            }
            c.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    public static class Trash {
        public long id;
        public String kind = "";
        public String title = "";
        public String sub = "";
        public long at;
    }

    private Plan readPlan(Cursor c) {
        Plan p = new Plan();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.title = str(c, "title");
        p.goal = str(c, "goal");
        p.deadline = str(c, "deadline");
        p.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) != 0;
        return p;
    }

    // ---------- stage ----------

    public List<Stage> stages(long planId) {
        List<Stage> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("stage", null, "planId=?",
                new String[]{String.valueOf(planId)}, null, null, "sDate ASC, id ASC");
        while (c.moveToNext()) out.add(readStage(c));
        c.close();
        return out;
    }

    public long saveStage(Stage s) {
        ContentValues v = new ContentValues();
        v.put("planId", s.planId);
        v.put("title", s.title);
        v.put("sDate", s.sDate);
        v.put("eDate", s.eDate);
        v.put("done", s.done ? 1 : 0);
        if (s.id > 0) {
            getWritableDatabase().update("stage", v, "id=?", new String[]{String.valueOf(s.id)});
            return s.id;
        }
        long id = getWritableDatabase().insert("stage", null, v);
        s.id = id;
        return id;
    }

    public void deleteStage(long id) {
        getWritableDatabase().delete("stage", "id=?", new String[]{String.valueOf(id)});
    }

    private Stage readStage(Cursor c) {
        Stage s = new Stage();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.planId = c.getLong(c.getColumnIndexOrThrow("planId"));
        s.title = str(c, "title");
        s.sDate = str(c, "sDate");
        s.eDate = str(c, "eDate");
        s.done = c.getInt(c.getColumnIndexOrThrow("done")) != 0;
        return s;
    }

    private static String str(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return v == null ? "" : v;
    }
}
