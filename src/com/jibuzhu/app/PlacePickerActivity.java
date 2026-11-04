package com.jibuzhu.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 地点搜索：腾讯位置服务关键词搜索，选中后把名称和经纬度带回编辑页。 */
public class PlacePickerActivity extends Activity {

    public static final String R_TITLE = "place_title";
    public static final String R_ADDRESS = "place_address";
    public static final String R_LAT = "place_lat";
    public static final String R_LNG = "place_lng";

    private EditText kw;
    private android.widget.ImageView mapView;
    private Button confirm;
    private TencentMap.Place selected;
    private final java.util.List<LinearLayout> cards = new java.util.ArrayList<LinearLayout>();
    private LinearLayout resultBox;
    private TextView status;
    private double hereLat, hereLng;

    @Override
    protected void onCreate(Bundle b) {
        Theme.setMode(this, Theme.mode(this));
        if (Theme.dark(this)) setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        else setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Theme.applyBars(this);
        super.onCreate(b);
        buildUi();
        tagHere();
        warmLoc();
    }

    /**
     * 进页面就先把位置预热出来。
     * 搜索接口没坐标时只能按「全国」搜，慢得明显；有坐标就是附近 50 公里，快很多。
     * 这里先用 last known 顶上（同步、极快），再在后台补一次新定位。
     */
    private void warmLoc() {
        try {
            final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return;
            Location known = bestLastKnown(lm);
            if (known != null) {
                hereLat = known.getLatitude();
                hereLng = known.getLongitude();
                hereAcc = accOf(known);
            }
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return;
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                @Override
                public void onLocationChanged(Location loc) {
                    if (loc != null) {
                        hereLat = loc.getLatitude();
                        hereLng = loc.getLongitude();
                        hereAcc = accOf(loc);
                    }
                }

                @Override
                public void onStatusChanged(String p, int s, Bundle bundle) {
                }

                @Override
                public void onProviderEnabled(String p) {
                }

                @Override
                public void onProviderDisabled(String p) {
                }
            }, getMainLooper());
        } catch (Exception ignored) {
        }
    }

    private void tagHere() {
        try {
            final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            // 先用最后已知位置顶上，避免按钮点了没反应
            Location best = null;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                if (!lm.isProviderEnabled(p)) continue;
                Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime()) best = l;
            }
            if (best != null) {
                hereLat = best.getLatitude();
                hereLng = best.getLongitude();
            }
            // 选地点要准，这里主动要一次 GPS
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, getMainExecutor(),
                        new java.util.function.Consumer<Location>() {
                            @Override
                            public void accept(Location loc) {
                                if (loc != null) {
                                    hereLat = loc.getLatitude();
                                    hereLng = loc.getLongitude();
                                }
                            }
                        });
            } else {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        if (loc != null) {
                            hereLat = loc.getLatitude();
                            hereLng = loc.getLongitude();
                        }
                    }

                    @Override
                    public void onStatusChanged(String p, int s, Bundle b) {
                    }

                    @Override
                    public void onProviderEnabled(String p) {
                    }

                    @Override
                    public void onProviderDisabled(String p) {
                    }
                }, android.os.Looper.getMainLooper());
            }
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Theme.bg(this));

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Theme.dp(this, 16), Theme.dp(this, 14), Theme.dp(this, 16), Theme.dp(this, 6));
        TextView title = Ui.text(this, "选择地点", 20, Theme.text(this));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.text(this, "返回", 15, Theme.sub(this));
        close.setPadding(Theme.dp(this, 10), Theme.dp(this, 6), 0, Theme.dp(this, 6));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(close);
        root.addView(bar);

        LinearLayout searchRow = Ui.row(this);
        searchRow.setPadding(Theme.dp(this, 16), 0, Theme.dp(this, 16), 0);
        kw = Ui.input(this, "比如 华为南京研究所");
        searchRow.addView(kw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button go = Ui.primary(this, "搜索");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.leftMargin = Theme.dp(this, 8);
        searchRow.addView(go, gp);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch();
            }
        });
        root.addView(searchRow);

        status = Ui.text(this, "先搜关键词选点最准；「当前位置」靠手机定位，室内会偏几百米", 13, Theme.sub(this));
        status.setPadding(Theme.dp(this, 16), Theme.dp(this, 10), Theme.dp(this, 16), Theme.dp(this, 4));
        root.addView(status);

        mapView = new android.widget.ImageView(this);
        mapView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        mapView.setBackground(Theme.cardBg(this, 16));
        mapView.setClipToOutline(true);
        mapView.setVisibility(View.GONE);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 170));
        mp.leftMargin = Theme.dp(this, 16);
        mp.rightMargin = Theme.dp(this, 16);
        mp.topMargin = Theme.dp(this, 6);
        root.addView(mapView, mp);

        Button here = Ui.button(this, "用当前位置作为地点");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = Theme.dp(this, 16);
        hp.rightMargin = Theme.dp(this, 16);
        here.setLayoutParams(hp);
        here.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useHere();
            }
        });
        root.addView(here);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        resultBox = Ui.col(this);
        resultBox.setPadding(Theme.dp(this, 16), Theme.dp(this, 8), Theme.dp(this, 16), Theme.dp(this, 40));
        sv.addView(resultBox);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        confirm = Ui.primary(this, "就用这个地点");
        confirm.setVisibility(View.GONE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Theme.dp(this, 16);
        cp.rightMargin = Theme.dp(this, 16);
        cp.topMargin = Theme.dp(this, 8);
        cp.bottomMargin = Theme.dp(this, 16);
        root.addView(confirm, cp);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selected != null) pick(selected);
            }
        });

        setContentView(root);
    }

    private void doSearch() {
        final String q = kw.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "先输入关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("搜索中…");
        resultBox.removeAllViews();
        // 2 秒还没回来就提示一句信号弱，免得用户以为卡死了
        final Runnable slow = new Runnable() {
            @Override
            public void run() {
                status.setText("当前GPS信号较弱~");
            }
        };
        status.postDelayed(slow, 2000);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<TencentMap.Place> list = TencentMap.search(q, hereLat, hereLng);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.removeCallbacks(slow);
                            showResults(list);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.removeCallbacks(slow);
                            status.setText("搜索失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showResults(List<TencentMap.Place> list) {
        resultBox.removeAllViews();
        cards.clear();
        if (list.isEmpty()) {
            status.setText("没搜到，换个关键词试试");
            return;
        }
        status.setText("找到 " + list.size() + " 个地点，点一个看位置");
        for (final TencentMap.Place p : list) {
            final LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, p.title, 17, Theme.text(this)));
            if (!p.address.isEmpty()) {
                TextView sub = Ui.text(this, p.address, 13, Theme.sub(this));
                sub.setPadding(0, Theme.dp(this, 3), 0, 0);
                card.addView(sub);
            }
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(p, card);
                }
            });
            cards.add(card);
            resultBox.addView(card);
        }
    }

    /** 选中一个地点：高亮卡片、画出地图、露出底部确认 */
    private void select(TencentMap.Place p, LinearLayout card) {
        selected = p;
        for (LinearLayout c : cards) c.setBackground(Theme.cardBg(this, 16));
        if (card != null) {
            card.setBackground(Theme.strokeBg(Theme.card(this), Theme.accent(this), 16, this));
        }
        confirm.setText("就用「" + p.title + "」");
        confirm.setVisibility(View.VISIBLE);
        showMap(p);
    }

    private void showMap(final TencentMap.Place p) {
        mapView.setVisibility(View.VISIBLE);
        mapView.setImageBitmap(null);
        status.setText("正在加载地图…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final android.graphics.Bitmap bm = TencentMap.staticMap(p.lat, p.lng, 640, 340);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mapView.setImageBitmap(bm);
                            status.setText(p.title);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("地图加载不出来：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void useHere() {
        // 进页面时 warmLoc() 已经在取位置了，拿到了就直接用，别再让用户等一轮
        if (hereLat != 0 && hereLng != 0) {
            doUseHere();
            return;
        }
        status.setText("正在定位…");
        pickWithFreshLocation(new Runnable() {
            @Override
            public void run() {
                doUseHere();
            }
        });
    }

    /** 当前取到的坐标精度（米），选点时给用户看 */
    private float hereAcc = 999f;

    private static float accOf(Location l) {
        if (l == null) return 999f;
        return l.hasAccuracy() ? l.getAccuracy() : 999f;
    }

    /** 拿 last known 里最新的一个当兜底 */
    private static Location bestLastKnown(LocationManager lm) {
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                if (!lm.isProviderEnabled(p)) continue;
                Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime()) best = l;
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    /**
     * 要一次尽量准的定位。
     * GPS 和网络同时挂，谁报上来的精度好用谁；一旦精度到 30 米内立刻采用，
     * 否则最多等 12 秒，取这段时间里最优的那个点。
     */
    private void pickWithFreshLocation(final Runnable then) {
        final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        // 快路径：手上已经有一份不太旧、精度还行的位置就直接用。
        // 系统 GPS 冷启动要搜星，等它十几秒对「选个地点」这件事完全不划算。
        Location quick = bestLastKnown(lm);
        if (quick != null && accOf(quick) <= 200f
                && System.currentTimeMillis() - quick.getTime() < 10 * 60 * 1000L) {
            hereLat = quick.getLatitude();
            hereLng = quick.getLongitude();
            hereAcc = accOf(quick);
            runOnUiThread(then);
            return;
        }
        final boolean[] done = new boolean[]{false};
        final Location[] best = new Location[]{null};

        final Runnable[] finish = new Runnable[1];
        final android.location.LocationListener lis = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(Location loc) {
                if (loc == null || done[0]) return;
                if (best[0] == null || accOf(loc) < accOf(best[0])) best[0] = loc;
                status.setText("定位中… ±" + ((int) accOf(best[0])) + " m");
                if (accOf(best[0]) <= 120f) finish[0].run();
            }

            @Override
            public void onStatusChanged(String p, int s, Bundle b) {
            }

            @Override
            public void onProviderEnabled(String p) {
            }

            @Override
            public void onProviderDisabled(String p) {
            }
        };

        finish[0] = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                try {
                    lm.removeUpdates(lis);
                } catch (Exception ignored) {
                }
                Location b = best[0];
                if (b == null) b = bestLastKnown(lm);
                if (b != null) {
                    hereLat = b.getLatitude();
                    hereLng = b.getLongitude();
                    hereAcc = accOf(b);
                }
                runOnUiThread(then);
            }
        };

        try {
            if (lm != null) {
                for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 1000L, 0f, lis, getMainLooper());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // 3.5 秒还没等到足够准的点，就用这期间最好的（或 last known）
        new android.os.Handler(getMainLooper()).postDelayed(finish[0], 3500);
    }

    private void doUseHere() {
        if (hereLat == 0 && hereLng == 0) {
            Toast.makeText(this, "还拿不到当前位置，先等一下或到室外再试", Toast.LENGTH_LONG).show();
            return;
        }
        if (hereAcc > 100f) {
            Toast.makeText(this, "定位精度一般（±" + ((int) hereAcc) + " 米），到室外开阔处再取会更准",
                    Toast.LENGTH_LONG).show();
        }
        status.setText("正在识别位置…（±" + ((int) hereAcc) + " 米）");
        final double lat = hereLat, lng = hereLng;
        new Thread(new Runnable() {
            @Override
            public void run() {
                TencentMap.Place p;
                try {
                    p = TencentMap.reverse(lat, lng);
                } catch (Exception e) {
                    p = new TencentMap.Place();
                    p.title = "当前位置";
                    p.lat = lat;
                    p.lng = lng;
                }
                final TencentMap.Place fp = p;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        select(fp, null);
                    }
                });
            }
        }).start();
    }

    private void pick(TencentMap.Place p) {
        Intent i = new Intent();
        i.putExtra(R_TITLE, p.title);
        i.putExtra(R_ADDRESS, p.address);
        i.putExtra(R_LAT, p.lat);
        i.putExtra(R_LNG, p.lng);
        setResult(RESULT_OK, i);
        finish();
    }
}
