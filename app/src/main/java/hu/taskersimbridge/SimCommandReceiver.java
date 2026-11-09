package hu.taskersimbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SimCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_SIM1 = "hu.taskersimbridge.SET_SIM_1";
    public static final String ACTION_SIM2 = "hu.taskersimbridge.SET_SIM_2";
    public static final String ACTION_STATUS = "hu.taskersimbridge.GET_STATUS";
    public static final String ACTION_SIM1_SIGNAL = "hu.taskersimbridge.GET_SIM1_SIGNAL";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final String action = intent == null ? "" : intent.getAction();
        WORK.execute(() -> {
            String result;
            try {
                if (ACTION_SIM1.equals(action)) result = TelephonyController.setSlot(context, 0);
                else if (ACTION_SIM2.equals(action)) result = TelephonyController.setSlot(context, 1);
                else if (ACTION_STATUS.equals(action)) result = TelephonyController.status(context);
                else if (ACTION_SIM1_SIGNAL.equals(action)) {
                    int level = Sim1SignalMonitor.getSim1SignalLevel(context);
                    result = Integer.toString(level);
                }
                else result = "Ismeretlen parancs.";
                Log.i("TaskerSIMBridge", result);
            } catch (Throwable e) {
                result = "HIBA: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e("TaskerSIMBridge", result, e);
            }
            Bundle extras = new Bundle();
            extras.putString("result", result);
            if (ACTION_SIM1_SIGNAL.equals(action)) {
                Intent response = new Intent("hu.taskersimbridge.SIM1_SIGNAL_RESULT");
                response.putExtras(extras);
                context.sendBroadcast(response);
            } else {
                Intent response = new Intent("hu.taskersimbridge.RESULT");
                response.putExtras(extras);
                context.sendBroadcast(response);
            }
            pending.finish();
        });
    }
}
