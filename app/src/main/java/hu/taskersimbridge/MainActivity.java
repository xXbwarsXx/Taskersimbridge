package hu.taskersimbridge;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView signalStatus;
    private Sim1SignalMonitor signalMonitor;
    private static final int PHONE_STATE_REQUEST = 2002;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> refresh();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        signalStatus = findViewById(R.id.signal_status);
        signalMonitor = new Sim1SignalMonitor(this, text -> runOnUiThread(() -> signalStatus.setText(text)));
        Button grant = findViewById(R.id.grant);
        grant.setOnClickListener(v -> requestShizuku());
        findViewById(R.id.sim1).setOnClickListener(v -> runSet(0));
        findViewById(R.id.sim2).setOnClickListener(v -> runSet(1));
        Shizuku.addRequestPermissionResultListener(permissionListener);
        refresh();
        ensurePhoneStatePermission();
    }

    private void ensurePhoneStatePermission() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_PHONE_STATE},
                    PHONE_STATE_REQUEST);
        } else {
            signalMonitor.start();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PHONE_STATE_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                signalMonitor.start();
            } else {
                signalStatus.setText("A SIM 1 térerő és hálózattípus olvasásához engedélyezd a Telefon állapotának hozzáférését.");
            }
        }
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) { status.setText("A Shizuku nem fut. Indítsd el, majd térj vissza ide."); return; }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { refresh(); return; }
            Shizuku.requestPermission(1001);
        } catch (Exception e) { status.setText("Shizuku hiba: " + e.getMessage()); }
    }

    private void refresh() {
        try {
            if (!Shizuku.pingBinder()) { status.setText("Shizuku nem fut."); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { status.setText("Shizuku fut, de az engedély még nincs megadva."); return; }
            new Thread(() -> {
                try {
                    String s = TelephonyController.status(this);
                    runOnUiThread(() -> status.setText(s));
                } catch (Exception e) { runOnUiThread(() -> status.setText("Lekérdezési hiba: " + e.getMessage())); }
            }).start();
        } catch (Exception e) { status.setText("Hiba: " + e.getMessage()); }
    }

    private void runSet(int slot) {
        new Thread(() -> {
            try {
                String result = TelephonyController.setSlot(this, slot);
                runOnUiThread(() -> { status.setText(result); Toast.makeText(this, result, Toast.LENGTH_LONG).show(); });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> { status.setText(message); Toast.makeText(this, message, Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    @Override protected void onDestroy() {
        if (signalMonitor != null) signalMonitor.stop();
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }
}
