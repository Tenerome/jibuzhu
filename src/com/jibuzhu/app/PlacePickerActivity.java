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

        status = Ui.text(this, "输入关键词搜索，或直接用「当前位置」", 13, Theme.sub(this));
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<TencentMap.Place> list = TencentMap.search(q, hereLat, hereLng);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showResults(list);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
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
        if (hereLat == 0 && hereLng == 0) {
            Toast.makeText(this, "还拿不到当前位置，先等一下或到室外再试", Toast.LENGTH_LONG).show();
            return;
        }
        status.setText("正在识别位置…");
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
