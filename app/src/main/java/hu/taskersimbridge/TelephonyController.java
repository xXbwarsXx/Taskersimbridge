package hu.taskersimbridge;

import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

final class TelephonyController {
    private static final String SUB_IFACE = "com.android.internal.telephony.ISub";
    private static final String PHONE_IFACE = "com.android.internal.telephony.ITelephony";
    private static final String SHELL_PACKAGE = "com.android.shell";
    private static boolean hiddenApiReady;

    static final class Sim {
        final int subId, slot;
        final String name;
        Sim(int subId, int slot, String name) { this.subId = subId; this.slot = slot; this.name = name; }
    }

    static String setSlot(Context context, int slot) throws Exception {
        if (slot != 0 && slot != 1) throw new IllegalArgumentException("A SIM slot csak 0 vagy 1 lehet.");
        if (!Shizuku.pingBinder()) throw new IllegalStateException("A Shizuku nem fut. Indítsd el a Shizuku alkalmazásban.");
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("A Tasker SIM Bridge nem kapott Shizuku-engedélyt. Nyisd meg az appot és add meg.");
        Object sub = getService("isub", SUB_IFACE);
        List<Sim> sims = getSims(sub, context);
        Sim target = null;
        for (Sim sim : sims) if (sim.slot == slot) target = sim;
        if (target == null) throw new IllegalStateException("A(z) SIM " + (slot + 1) + " nem aktív vagy nem található.");
        int current = (Integer) invoke(sub, SUB_IFACE, "getDefaultDataSubId", new Class<?>[0]);
        if (current == target.subId) return "Már a SIM " + (slot + 1) + " használja a mobilinternetet (subId=" + target.subId + ").";
        invoke(sub, SUB_IFACE, "setDefaultDataSubId", new Class<?>[]{int.class}, target.subId);
        enableData(target.subId, context);
        int confirmed = (Integer) invoke(sub, SUB_IFACE, "getDefaultDataSubId", new Class<?>[0]);
        if (confirmed != target.subId) throw new IllegalStateException("A rendszer nem erősítette meg a SIM-váltást. Várt subId=" + target.subId + ", kapott=" + confirmed);
        return "Sikeres váltás: SIM " + (slot + 1) + " (subId=" + target.subId + ", " + target.name + ").";
    }

    static String status(Context context) throws Exception {
        Object sub = getService("isub", SUB_IFACE);
        int current = (Integer) invoke(sub, SUB_IFACE, "getDefaultDataSubId", new Class<?>[0]);
        for (Sim sim : getSims(sub, context)) if (sim.subId == current)
            return "Jelenlegi mobiladat-SIM: SIM " + (sim.slot + 1) + " – " + sim.name + " (subId=" + current + ").";
        return "A jelenlegi adat-SIM subId-ja: " + current + ". Az aktív SIM-ek között nem található.";
    }

    private static List<Sim> getSims(Object service, Context context) throws Exception {
        Object value;
        try {
            value = invoke(service, SUB_IFACE, "getActiveSubscriptionInfoList",
                    new Class<?>[]{String.class, String.class, boolean.class}, callingPackage(context), null, true);
        } catch (NoSuchMethodException e) {
            try {
                value = invoke(service, SUB_IFACE, "getActiveSubscriptionInfoList",
                        new Class<?>[]{String.class, String.class}, callingPackage(context), null);
            } catch (NoSuchMethodException e2) {
                value = invoke(service, SUB_IFACE, "getActiveSubscriptionInfoList",
                        new Class<?>[]{String.class}, callingPackage(context));
            }
        }
        ArrayList<Sim> result = new ArrayList<>();
        if (value instanceof List<?>) for (Object item : (List<?>) value) {
            if (item instanceof SubscriptionInfo) {
                SubscriptionInfo info = (SubscriptionInfo) item;
                int slot = info.getSimSlotIndex();
                if (slot >= 0) {
                    CharSequence label = info.getDisplayName();
                    if (label == null || label.length() == 0) label = info.getCarrierName();
                    result.add(new Sim(info.getSubscriptionId(), slot, label == null ? "SIM" : label.toString()));
                }
            }
        }
        result.sort(Comparator.comparingInt(s -> s.slot));
        return result;
    }

    private static void enableData(int subId, Context context) throws Exception {
        Object phone = getService("phone", PHONE_IFACE);
        try {
            invoke(phone, PHONE_IFACE, "setDataEnabledForReason",
                    new Class<?>[]{int.class, int.class, boolean.class, String.class},
                    subId, TelephonyManager.DATA_ENABLED_REASON_USER, true, callingPackage(context));
        } catch (NoSuchMethodException e) {
            try {
                invoke(phone, PHONE_IFACE, "setDataEnabledForReason",
                        new Class<?>[]{int.class, int.class, boolean.class},
                        subId, TelephonyManager.DATA_ENABLED_REASON_USER, true);
            } catch (NoSuchMethodException e2) {
                try {
                    invoke(phone, PHONE_IFACE, "setUserDataEnabled", new Class<?>[]{int.class, boolean.class}, subId, true);
                } catch (NoSuchMethodException e3) {
                    invoke(phone, PHONE_IFACE, "setDataEnabled", new Class<?>[]{int.class, boolean.class}, subId, true);
                }
            }
        }
    }

    private static String callingPackage(Context context) {
        return Shizuku.getUid() == 2000 ? SHELL_PACKAGE : context.getPackageName();
    }

    private static Object getService(String serviceName, String iface) throws Exception {
        if (Build.VERSION.SDK_INT >= 28 && !hiddenApiReady) {
            if (!HiddenApiBypass.addHiddenApiExemptions("")) throw new IllegalStateException("A rejtett Android API-k nem érhetők el.");
            hiddenApiReady = true;
        }
        IBinder binder = SystemServiceHelper.getSystemService(serviceName);
        if (binder == null) throw new IllegalStateException("A rendszer szolgáltatása nem érhető el: " + serviceName);
        Class<?> stub = Class.forName(iface + "$Stub");
        return stub.getMethod("asInterface", IBinder.class).invoke(null, new ShizukuBinderWrapper(binder));
    }

    private static Object invoke(Object service, String iface, String method, Class<?>[] types, Object... args) throws Exception {
        Method m = Class.forName(iface).getMethod(method, types);
        try { return m.invoke(service, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }
}
