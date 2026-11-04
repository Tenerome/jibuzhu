package com.jibuzhu.app.trigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 安装更新后：重排所有闹钟，并把定位服务拉起来。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            AlarmScheduler.rescheduleAll(ctx);
            ctx.startForegroundService(new Intent(ctx, GeoService.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
