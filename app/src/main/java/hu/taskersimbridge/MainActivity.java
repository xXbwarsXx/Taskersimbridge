package hu.taskersimbridge;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView signalStatus;
    private Sim1SignalMonitor signalMonitor;
    private Button themeToggle;
    private Spinner themePalette;
    private SharedPreferences preferences;
    private boolean darkMode;
    private int paletteIndex;
    private static final int PHONE_STATE_REQUEST = 2002;
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> refresh();

    private static final class Palette {
        final int bg, card, text, secondary, accent, accentText;
        Palette(int bg, int card, int text, int secondary, int accent, int accentText) {
            this.bg = bg; this.card = card; this.text = text;
            this.secondary = secondary; this.accent = accent; this.accentText = accentText;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("appearance", MODE_PRIVATE);
        darkMode = preferences.getBoolean("dark", false);
        paletteIndex = preferences.getInt("palette", 0);

        status = findViewById(R.id.status);
        signalStatus = findViewById(R.id.signal_status);
        signalMonitor = new Sim1SignalMonitor(this,
                text -> runOnUiThread(() -> signalStatus.setText(text)));

        themeToggle = findViewById(R.id.theme_toggle);
        themePalette = findViewById(R.id.theme_palette);
        themePalette.setSelection(Math.max(0, Math.min(4, paletteIndex)));
        themePalette.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                  int position, long id) {
                paletteIndex = position;
                preferences.edit().putInt("palette", paletteIndex).apply();
                applyAppearance();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        themeToggle.setOnClickListener(v -> {
            darkMode = !darkMode;
            preferences.edit().putBoolean("dark", darkMode).apply();
            applyAppearance();
        });

        Button grant = findViewById(R.id.grant);
        grant.setOnClickListener(v -> requestShizuku());
        findViewById(R.id.sim1).setOnClickListener(v -> runSet(0));
        findViewById(R.id.sim2).setOnClickListener(v -> runSet(1));

        Shizuku.addRequestPermissionResultListener(permissionListener);
        applyAppearance();
        refresh();
        ensurePhoneStatePermission();
    }

    private int systemColor(String name, int fallback) {
        int id = getResources().getIdentifier(name, "color", "android");
        if (id == 0) return fallback;
        try { return getColor(id); } catch (Exception e) { return fallback; }
    }

    private Palette currentPalette() {
        int bg, card, text, secondary, accent;
        if (paletteIndex == 0) {
            accent = systemColor(darkMode ? "system_accent1_200" : "system_accent1_600",
                    darkMode ? Color.rgb(164, 195, 255) : Color.rgb(36, 91, 155));
            if (darkMode) {
                bg = Color.rgb(16, 22, 31); card = Color.rgb(28, 37, 50);
                text = Color.rgb(235, 240, 248); secondary = Color.rgb(174, 188, 207);
            } else {
                bg = Color.rgb(244, 247, 252); card = Color.WHITE;
                text = Color.rgb(25, 39, 58); secondary = Color.rgb(91, 108, 130);
            }
        } else {
            int[][] accents = {
                {Color.rgb(20, 112, 155), Color.rgb(125, 205, 235)},
                {Color.rgb(43, 120, 86), Color.rgb(139, 215, 174)},
                {Color.rgb(105, 76, 170), Color.rgb(190, 169, 245)},
                {Color.rgb(177, 91, 47), Color.rgb(255, 184, 135)}
            };
            int[] pair = accents[Math.max(0, Math.min(3, paletteIndex - 1))];
            accent = pair[darkMode ? 1 : 0];
            if (darkMode) {
                bg = Color.rgb(18, 23, 29); card = Color.rgb(31, 39, 48);
                text = Color.rgb(239, 242, 246); secondary = Color.rgb(180, 191, 202);
            } else {
                bg = Color.rgb(246, 248, 250); card = Color.WHITE;
                text = Color.rgb(28, 40, 52); secondary = Color.rgb(93, 108, 122);
            }
        }
        int accentText = darkMode ? Color.rgb(18, 27, 38) : Color.WHITE;
        return new Palette(bg, card, text, secondary, accent, accentText);
    }

    private void applyAppearance() {
        Palette c = currentPalette();
        findViewById(R.id.root_scroll).setBackgroundColor(c.bg);
        findViewById(R.id.content_root).setBackgroundColor(c.bg);
        int[] cardIds = {R.id.status, R.id.signal_status};
        for (int id : cardIds) {
            TextView v = findViewById(id);
            v.setBackgroundTintList(ColorStateList.valueOf(c.card));
            v.setTextColor(c.text);
        }
        int[] headingIds = {R.id.eyebrow, R.id.diagnostic_heading, R.id.sim_heading, R.id.footer};
        for (int id : headingIds) ((TextView)findViewById(id)).setTextColor(c.secondary);
        ((TextView)findViewById(R.id.title)).setTextColor(c.text);

        int[] buttonIds = {R.id.grant, R.id.sim1, R.id.sim2};
        for (int id : buttonIds) {
            Button b = findViewById(id);
            b.setBackgroundTintList(ColorStateList.valueOf(c.accent));
            b.setTextColor(c.accentText);
        }
        themeToggle.setText(darkMode ? "Sötét mód: BE" : "Sötét mód: KI");
        themeToggle.setBackgroundTintList(ColorStateList.valueOf(c.accent));
        themeToggle.setTextColor(c.accentText);
        themePalette.setBackgroundTintList(ColorStateList.valueOf(c.card));
        themePalette.setPopupBackgroundDrawable(new ColorDrawable(c.card));

        getWindow().setStatusBarColor(c.bg);
        getWindow().setNavigationBarColor(c.bg);
        int flags = darkMode ? 0 :
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().getDecorView().setSystemUiVisibility(flags);
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
                signalStatus.setText("A SIM 1 térerő lekérdezéséhez engedélyezd a Telefon állapotának hozzáférését.");
            }
        }
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                status.setText("A Shizuku nem fut. Indítsd el, majd térj vissza ide.");
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh(); return;
            }
            Shizuku.requestPermission(1001);
        } catch (Exception e) { status.setText("Shizuku hiba: " + e.getMessage()); }
    }

    private void refresh() {
        try {
            if (!Shizuku.pingBinder()) { status.setText("Shizuku nem fut."); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                status.setText("Shizuku fut, de az engedély még nincs megadva."); return;
            }
            new Thread(() -> {
                try {
                    String s = TelephonyController.status(this);
                    runOnUiThread(() -> status.setText(s));
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("Lekérdezési hiba: " + e.getMessage()));
                }
            }).start();
        } catch (Exception e) { status.setText("Hiba: " + e.getMessage()); }
    }

    private void runSet(int slot) {
        new Thread(() -> {
            try {
                String result = TelephonyController.setSlot(this, slot);
                runOnUiThread(() -> {
                    status.setText(result);
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String message = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> {
                    status.setText(message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override protected void onDestroy() {
        if (signalMonitor != null) signalMonitor.stop();
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }
}
