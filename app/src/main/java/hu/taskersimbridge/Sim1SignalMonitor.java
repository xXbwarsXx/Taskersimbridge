package hu.taskersimbridge;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Read-only diagnostic monitor for physical SIM slot 1.
 * It deliberately does not change the active data SIM.
 */
final class Sim1SignalMonitor {
    private final Context context;
    private final Consumer<String> output;
    private TelephonyManager telephonyManager;
    private TelephonyCallback callback;
    private int signalLevel = -1;
    private String network = "Lekérdezés folyamatban…";

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
                    if (info.getSimSlotIndex() == 0) {
                        subId = info.getSubscriptionId();
                        break;
                    }
                }
            }
            if (subId < 0) {
                output.accept("SIM 1 nem található vagy nincs aktiválva.");
                return;
            }

            telephonyManager = context.getSystemService(TelephonyManager.class)
                    .createForSubscriptionId(subId);

            callback = new Sim1Callback();
            telephonyManager.registerTelephonyCallback(context.getMainExecutor(), callback);
            publish();
        } catch (SecurityException e) {
            output.accept("Nincs telefonállapot-engedély: " + e.getMessage());
        } catch (Exception e) {
            output.accept("Diagnosztikai hiba: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private final class Sim1Callback extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener,
                       TelephonyCallback.DisplayInfoListener {
        @Override public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            signalLevel = signalStrength == null ? -1 : signalStrength.getLevel();
            publish();
        }

        @Override public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            network = networkLabel(displayInfo);
            publish();
        }
    }

    private void publish() {
        String levelText = signalLevel < 0 ? "ismeretlen" : signalLevel + "/4";
        output.accept("SIM 1 – térerő: " + levelText + "\nHálózat: " + network);
    }

    void stop() {
        if (Build.VERSION.SDK_INT >= 31 && telephonyManager != null && callback != null) {
            try { telephonyManager.unregisterTelephonyCallback(callback); }
            catch (Exception ignored) {}
        }
        callback = null;
        telephonyManager = null;
    }

    private static String networkLabel(TelephonyDisplayInfo info) {
        int type = info.getNetworkType();
        int override = info.getOverrideNetworkType();

        if (type == TelephonyManager.NETWORK_TYPE_NR
                || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                || override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED) {
            return "5G";
        }
        if (type == TelephonyManager.NETWORK_TYPE_LTE
                && override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA) {
            return "4G+";
        }
        if (type == TelephonyManager.NETWORK_TYPE_LTE) return "4G";
        if (type == TelephonyManager.NETWORK_TYPE_UMTS
                || type == TelephonyManager.NETWORK_TYPE_HSPA
                || type == TelephonyManager.NETWORK_TYPE_HSPAP) return "3G";
        if (type == TelephonyManager.NETWORK_TYPE_GSM
                || type == TelephonyManager.NETWORK_TYPE_EDGE
                || type == TelephonyManager.NETWORK_TYPE_GPRS) return "2G";
        return "Egyéb (networkType=" + type + ")";
    }
}
