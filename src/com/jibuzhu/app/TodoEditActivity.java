package com.jibuzhu.app;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jibuzhu.app.data.Store;
import com.jibuzhu.app.data.Todo;
import com.jibuzhu.app.trigger.AlarmScheduler;

public class TodoEditActivity extends Activity {

    private static final int REQ_PLACE = 21;
    private static final int REQ_RING = 22;

    private long id;
    private Todo t;

    private EditText titleEt;
    private TimeSpinner timeSpin;        // 时间提醒的时刻 / 定位提醒的窗口起
    private TimeSpinner winStartSpin, winEndSpin;
    private LinearLayout chkBox;         // 打卡：兜底时间
    private LinearLayout winBox;
    private TextView winLabel;
    private TextView placeBtn, ringName;
    private LinearLayout bodyBox, timeBox, locBox, repeatBox, ringBox;
    private TimeSpinner fallbackSpin;
    private LinearLayout fallbackRow;
    private int kindIdx;                 // 0 时间 / 1 定位 / 2 打卡
    private int repIdx;                  // 0 单次 / 1 每天
    private int fbIdx;                   // 0 不设兜底 / 1 设兜底（仅打卡用）
    private String ring = "";
    private MediaPlayer preview;
    private Button tryBtn;

    @Override
    protected void onCreate(Bundle b) {
        if (Theme.dark(this)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Theme.applyBars(this);
        super.onCreate(b);
        id = getIntent().getLongExtra("id", 0);
        t = id > 0 ? Store.get(this).todo(id) : new Todo();
        if (t == null) t = new Todo();
        kindIdx = t.isCheckin() ? 2 : (t.isLocation() ? 1 : 0);
        repIdx = "daily".equals(t.rep) ? 1 : 0;
        fbIdx = (t.fallback == null || t.fallback.isEmpty()) ? 0 : 1;
        ring = t.ring == null ? "" : t.ring;
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
        top.addView(Ui.text(this, id > 0 ? "编辑待办" : "新建待办", 20, Theme.text(this)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        LinearLayout body = Ui.col(this);
        body.setPadding(Theme.dp(this, 16), Theme.dp(this, 6), Theme.dp(this, 16), Theme.dp(this, 30));
        body.setClipChildren(false);
        body.setClipToPadding(false);
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        body.addView(Ui.label(this, "要做什么"));
        titleEt = Ui.input(this, "比如 去南门拿快递");
        titleEt.setText(t.title);
        body.addView(titleEt);

        body.addView(Ui.label(this, "提醒方式"));
        body.addView(Ui.segmented(this, new String[]{"时间提醒", "到地点提醒", "打卡"}, kindIdx,
                new Ui.OnPick() {
                    @Override
                    public void onPick(int index) {
                        kindIdx = index;
                        rebuild();
                    }
                }));

        bodyBox = Ui.col(this);
        body.addView(bodyBox);

        // ---- 时间提醒 ----
        timeBox = Ui.col(this);
        timeBox.addView(Ui.label(this, "什么时间提醒你"));
        String cur = (id > 0 && "time".equals(t.kind)) ? t.winStart : "";
        timeSpin = new TimeSpinner(this, cur);
        timeBox.addView(timeSpin);
        bodyBox.addView(timeBox);

        // ---- 定位提醒 ----
        locBox = Ui.col(this);
        locBox.addView(Ui.label(this, "地点"));
        placeBtn = Ui.button(this, placeText());
        placeBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        placeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(TodoEditActivity.this, PlacePickerActivity.class),
                        REQ_PLACE);
            }
        });
        locBox.addView(placeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 46)));

        winBox = Ui.col(this);
        winLabel = Ui.label(this, "只在哪个时间段内提醒你");
        winBox.addView(winLabel);
        LinearLayout winRow = Ui.row(this);
        winStartSpin = new TimeSpinner(this,
                !t.winStart.isEmpty() ? t.winStart : "00:00");
        winEndSpin = new TimeSpinner(this,
                !t.winEnd.isEmpty() ? t.winEnd : "23:59");
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        wlp.rightMargin = Theme.dp(this, 8);
        winRow.addView(winStartSpin, wlp);
        TextView dash = Ui.text(this, "—", 16, Theme.sub(this));
        dash.setGravity(Gravity.CENTER);
        winRow.addView(dash);
        LinearLayout.LayoutParams wlp2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        wlp2.leftMargin = Theme.dp(this, 8);
        winRow.addView(winEndSpin, wlp2);
        winBox.addView(winRow);
        bodyBox.addView(locBox);
        bodyBox.addView(winBox);

        // ---- 打卡：兜底时间（到点还没到地点才响，不跳 WeLink）----
        chkBox = Ui.col(this);
        chkBox.addView(Ui.label(this, "兜底时间（到点还没到地点，就响铃提醒）"));
        chkBox.addView(Ui.segmented(this, new String[]{"不设兜底", "设兜底"}, fbIdx,
                new Ui.OnPick() {
                    @Override
                    public void onPick(int index) {
                        fbIdx = index;
                        refreshFallback();
                    }
                }));
        fallbackRow = Ui.row(this);
        String fbCur = (t.fallback == null || t.fallback.isEmpty())
                ? (!t.winStart.isEmpty() && !"00:00".equals(t.winStart) ? t.winStart : "")
                : t.fallback;
        fallbackSpin = new TimeSpinner(this, fbCur);
        fallbackRow.addView(fallbackSpin);
        chkBox.addView(fallbackRow);
        bodyBox.addView(chkBox);



        // ---- 重复（打卡固定每天，不显示这一块） ----
        repeatBox = Ui.col(this);
        repeatBox.addView(Ui.label(this, "重复"));
        repeatBox.addView(Ui.segmented(this, new String[]{"单次", "每天"}, repIdx,
                new Ui.OnPick() {
                    @Override
                    public void onPick(int index) {
                        repIdx = index;
                    }
                }));
        body.addView(repeatBox);

        // ---- 铃声 ----
        ringBox = Ui.col(this);
        ringBox.addView(Ui.label(this, "提醒铃声"));
        ringName = Ui.text(this, ringText(), 13, Theme.sub(this));
        ringBox.addView(ringName);
        LinearLayout ringOps = Ui.row(this);
        Button pick = Ui.button(this, "选择本地音乐");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/*");
                startActivityForResult(i, REQ_RING);
            }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Theme.dp(this, 46), 1);
        rl.rightMargin = Theme.dp(this, 8);
        ringOps.addView(pick, rl);
        tryBtn = Ui.button(this, "试听");
        tryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (preview != null && preview.isPlaying()) {
                    stopPreview();
                } else {
                    preview();
                }
            }
        });
        LinearLayout.LayoutParams rl2 = new LinearLayout.LayoutParams(0, Theme.dp(this, 46), 1);
        ringOps.addView(tryBtn, rl2);
        ringBox.addView(ringOps);
        Button reset = Ui.button(this, "恢复系统闹钟铃声");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ring = "";
                ringName.setText(ringText());
                stopPreview();
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 46));
        rlp.topMargin = Theme.dp(this, 14);
        ringBox.addView(reset, rlp);
        body.addView(ringBox);

        Button done = Ui.primary(this, "保存");
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 50));
        dp.topMargin = Theme.dp(this, 22);
        done.setLayoutParams(dp);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        body.addView(done);

        rebuild();
    }

    private void rebuild() {
        timeBox.setVisibility(kindIdx == 0 ? View.VISIBLE : View.GONE);
        locBox.setVisibility(kindIdx >= 1 ? View.VISIBLE : View.GONE);
        if (winBox != null) winBox.setVisibility(kindIdx >= 1 ? View.VISIBLE : View.GONE);
        if (chkBox != null) chkBox.setVisibility(kindIdx == 2 ? View.VISIBLE : View.GONE);
        // 打卡固定每天，不显示重复；其它类型正常显示
        if (repeatBox != null) repeatBox.setVisibility(kindIdx == 2 ? View.GONE : View.VISIBLE);
        refreshFallback();
    }

    /** 兜底时间滚轮：只有打卡 + 选了「设兜底」才显示 */
    private void refreshFallback() {
        if (fallbackRow != null) {
            fallbackRow.setVisibility(kindIdx == 2 && fbIdx == 1 ? View.VISIBLE : View.GONE);
        }
    }

    private String placeText() {
        if (t.place == null || t.place.isEmpty()) return "搜索并选择地点 ›";
        return t.place;
    }

    private String ringText() {
        if (ring.isEmpty()) return "当前：系统闹钟铃声";
        return "当前：" + ringName(ring);
    }

    private String ringName(String uri) {
        try {
            if (uri.startsWith("content://")) {
                android.database.Cursor c = getContentResolver().query(
                        android.net.Uri.parse(uri), new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                        null, null, null);
                if (c != null) {
                    String dn = c.moveToFirst() ? c.getString(0) : null;
                    c.close();
                    if (dn != null && !dn.trim().isEmpty()) return shorten(dn.trim());
                }
            }
            String n = android.net.Uri.decode(uri);
            int slash = n.lastIndexOf('/');
            if (slash >= 0) n = n.substring(slash + 1);
            int q = n.indexOf('?');
            if (q > 0) n = n.substring(0, q);
            return shorten(n);
        } catch (Exception e) {
            return "本地铃声";
        }
    }

    private String shorten(String n) {
        if (n.length() > 46) return "..." + n.substring(n.length() - 44);
        return n;
    }

    // ---------- 试听 ----------

    private void preview() {
        stopPreview();
        try {
            preview = new MediaPlayer();
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            preview.setAudioAttributes(aa);
            if (ring.isEmpty()) {
                Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                preview.setDataSource(this, u);
            } else {
                preview.setDataSource(this, Uri.parse(ring));
            }
            preview.setLooping(false);
            preview.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPreview();
                }
            });
            preview.prepare();
            preview.start();
            if (tryBtn != null) tryBtn.setText("停止");
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopPreview();
                }
            }, 8000);
        } catch (Exception e) {
            Toast.makeText(this, "试听失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        try {
            if (preview != null) {
                if (preview.isPlaying()) preview.stop();
                preview.release();
            }
        } catch (Exception ignored) {
        }
        preview = null;
        if (tryBtn != null) tryBtn.setText("试听");
        preview = null;
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        super.onDestroy();
    }

    // ---------- 选择结果 ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_PLACE) {
            String pn = data.getStringExtra(PlacePickerActivity.R_TITLE);
            t.place = pn == null ? "" : pn;
            t.lat = data.getDoubleExtra(PlacePickerActivity.R_LAT, 0);
            t.lng = data.getDoubleExtra(PlacePickerActivity.R_LNG, 0);
            placeBtn.setText(placeText());
        } else if (req == REQ_RING) {
            Uri u = data.getData();
            if (u == null) return;
            try {
                getContentResolver().takePersistableUriPermission(u,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            ring = u.toString();
            ringName.setText(ringText());
        }
    }

    // ---------- 保存 ----------

    private void save() {
        String title = titleEt.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "先写一下要做什么", Toast.LENGTH_SHORT).show();
            return;
        }
        t.title = title;
        t.kind = kindIdx == 0 ? "time" : (kindIdx == 1 ? "location" : "checkin");
        t.rep = (kindIdx == 2 || repIdx == 1) ? "daily" : "once";
        if (kindIdx == 2) {
            if (t.place == null || t.place.isEmpty()) {
                Toast.makeText(this, "请先选择一个打卡地点", Toast.LENGTH_SHORT).show();
                return;
            }
            t.wifi = "";
            t.winStart = winStartSpin.value();
            t.winEnd = winEndSpin.value();
            if (toMin(t.winStart) > toMin(t.winEnd)) {
                Toast.makeText(this, "开始时间不能晚于结束时间", Toast.LENGTH_SHORT).show();
                return;
            }
            t.fallback = fbIdx == 1 ? fallbackSpin.value() : "";
            if (t.radius <= 0) t.radius = 300;
        } else if (kindIdx == 0) {
            t.winStart = timeSpin.value();
            t.winEnd = t.winStart;
        } else {
            if (t.place == null || t.place.isEmpty()) {
                Toast.makeText(this, "请先选择一个地点", Toast.LENGTH_SHORT).show();
                return;
            }
            t.winStart = winStartSpin.value();
            t.winEnd = winEndSpin.value();
            if (toMin(t.winStart) > toMin(t.winEnd)) {
                Toast.makeText(this, "开始时间不能晚于结束时间", Toast.LENGTH_SHORT).show();
                return;
            }
            if (t.radius <= 0) t.radius = 300;
        }
        t.ring = ring;
        // 改过内容就当成一条新的：清掉"今天已经响过"的标记，
        // 否则改完地点当天不会再提醒（这是最容易让人以为"没生效"的地方）
        t.lastFired = "";
        long nid = Store.get(this).saveTodo(t);
        t.id = nid;
        AlarmScheduler.cancel(this, nid);
        AlarmScheduler.schedule(this, t);
        // 保存完就把定位服务拉起来，不依赖回到主界面时那一次判断
        // （用户可能存完直接锁屏，主界面的 onResume 根本没机会跑）
        if (t.enabled && (t.isLocation() || t.isCheckin())) {
            try {
                startForegroundService(new Intent(this, com.jibuzhu.app.trigger.GeoService.class));
            } catch (Exception e) {
                android.util.Log.e("JBZ-GEO", "save start geo fail: " + e);
            }
        }
        if (t.isCheckin()) {
            String tail = t.fallback.isEmpty() ? "" : ("，兜底 " + t.fallback);
            Toast.makeText(this, "到「" + t.place + "」自动打卡" + tail, Toast.LENGTH_SHORT).show();
        } else if (t.isLocation()) {
            Toast.makeText(this, "到达「" + t.place + "」时提醒", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "好，到 " + t.winStart + " 叫你", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /** "HH:mm" 转分钟数，用来比较时间段的先后 */
    private static int toMin(String hm) {
        if (hm == null || !hm.contains(":")) return 0;
        try {
            String[] a = hm.split(":");
            return Integer.parseInt(a[0].trim()) * 60 + Integer.parseInt(a[1].trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
