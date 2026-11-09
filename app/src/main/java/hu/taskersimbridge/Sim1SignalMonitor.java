package hu.taskersimbridge;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellSignalStrength;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Read-only SIM 1 diagnostic. This is an in-app monitor, not yet a background service. */
final class Sim1SignalMonitor {
    private final Context context;
    private final Consumer<String> output;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TelephonyManager telephonyManager;
    private Sim1Callback callback;
    private int signalLevel = -1;
    private int signalDbm = Integer.MIN_VALUE;
    private String displayNetwork = "Adatok lekérése…";
    private String registeredNetwork = "ismeretlen";
    private boolean running;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running || telephonyManager == null) return;
            try {
                SignalStrength ss = telephonyManager.getSignalStrength();
                updateSignal(ss);
                int dataType = telephonyManager.getDataNetworkType();
                if (dataType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    registeredNetwork = basicNetworkLabel(dataType);
                }
                publish();
            } catch (SecurityException e) {
                output.accept("Nincs Telefonállapot engedély: " + e.getMessage());
            } catch (Exception e) {
                output.accept("Lekérdezési hiba: " + e.getClass().getSimpleName());
            }
            handler.postDelayed(this, 5000);
        }
    };

    /**
     * Returns Android's 0..4 signal-quality level for physical SIM slot 1.
     * Returns -1 if SIM 1 or its signal reading is unavailable.
     */
    static int getSim1SignalLevel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return -1;
        try {
            SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
            List<SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
            int subId = -1;
            if (infos != null) {
                for (SubscriptionInfo info : infos) {
                    if (info.getSimSlotIndex() == 0) {
                        subId = info.getSubscriptionId();
                        break;
                    }
                }
            }
            if (subId < 0) return -1;
            TelephonyManager tm = context.getSystemService(TelephonyManager.class)
                    .createForSubscriptionId(subId);
            SignalStrength ss = tm.getSignalStrength();
            return ss == null ? -1 : ss.getLevel();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            return -1;
        }
    }

    Sim1SignalMonitor(Context context, Consumer<String> output) {
        this.context = context.getApplicationContext();
        this.output = output;
    }

    void start() {
        stop();
        if (Build.VERSION.SDK_INT < 31) {
            output.accept("A diagnosztika Android 12 vagy újabb rendszert igényel.");
            return;
        }
        try {
            SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
            List<SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
            int subId = -1;
            if (infos != null) {
                for (SubscriptionInfo info : infos) {
                    if (info.getSimSlotIndex() == 0) { subId = info.getSubscriptionId(); break; }
                }
            }
            if (subId < 0) { output.accept("SIM 1 nem található vagy nincs aktiválva."); return; }

            telephonyManager = context.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
            callback = new Sim1Callback();
            telephonyManager.registerTelephonyCallback(context.getMainExecutor(), callback);
            running = true;
            handler.post(poll);
            output.accept("SIM 1 figyelése elindult…");
        } catch (SecurityException e) {
            output.accept("Nincs Telefonállapot engedély: " + e.getMessage());
        } catch (Exception e) {
            output.accept("Diagnosztikai hiba: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private final class Sim1Callback extends TelephonyCallback implements
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.DisplayInfoListener {
        @Override public void onSignalStrengthsChanged(SignalStrength ss) { updateSignal(ss); publish(); }
        @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
            displayNetwork = displayNetworkLabel(info); publish();
        }
    }

    private void updateSignal(SignalStrength ss) {
        if (ss == null) { signalLevel = -1; signalDbm = Integer.MIN_VALUE; return; }
        signalLevel = ss.getLevel(); // Android's own 0..4 quality bucket, not Samsung's icon bars.
        int best = Integer.MIN_VALUE;
        for (CellSignalStrength cell : ss.getCellSignalStrengths()) {
            int dbm = cell.getDbm();
            if (dbm != Integer.MIN_VALUE && (best == Integer.MIN_VALUE || dbm > best)) best = dbm;
        }
        signalDbm = best;
    }

    private void publish() {
        String level = signalLevel < 0 ? "—" : signalLevel + "/4 (Android jelszint)";
        String dbm = signalDbm == Integer.MIN_VALUE ? "nem érhető el" : signalDbm + " dBm";
        output.accept("SIM 1 diagnosztika\nTérerő: " + level + "\nJelerősség: " + dbm
                + "\nHálózati regisztráció: " + registeredNetwork
                + "\nHálózati kijelzés: " + displayNetwork
                + "\nFrissítés: 5 másodpercenként");
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (telephonyManager != null && callback != null) {
            try { telephonyManager.unregisterTelephonyCallback(callback); } catch (Exception ignored) {}
        }
        callback = null;
        telephonyManager = null;
    }

    private static String displayNetworkLabel(TelephonyDisplayInfo info) {
        int type = info.getNetworkType();
        int override = info.getOverrideNetworkType();
        if (type == TelephonyManager.NETWORK_TYPE_NR
                || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) return "5G";
        if (type == TelephonyManager.NETWORK_TYPE_LTE
                && override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA) return "4G+";
        return basicNetworkLabel(type);
    }

    private static String basicNetworkLabel(int type) {
        if (type == TelephonyManager.NETWORK_TYPE_NR) return "5G (NR)";
        if (type == TelephonyManager.NETWORK_TYPE_LTE) return "4G (LTE)";
        if (type == TelephonyManager.NETWORK_TYPE_UMTS || type == TelephonyManager.NETWORK_TYPE_HSPA
                || type == TelephonyManager.NETWORK_TYPE_HSPAP) return "3G";
        if (type == TelephonyManager.NETWORK_TYPE_GSM || type == TelephonyManager.NETWORK_TYPE_EDGE
                || type == TelephonyManager.NETWORK_TYPE_GPRS) return "2G";
        return "Egyéb / ismeretlen (" + type + ")";
    }
}
