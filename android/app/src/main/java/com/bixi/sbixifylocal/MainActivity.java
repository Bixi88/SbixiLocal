package com.bixi.sbixifylocal;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import android.content.pm.PackageManager;

public class MainActivity extends BridgeActivity {
    // Tetto massimo di attesa della splash: se per qualsiasi motivo la pagina non segnala
    // di essere pronta (AppReady.ready()), la splash sparisce comunque dopo questo tempo.
    private static final long SPLASH_MAX_MS = 2500;
    private static final long SPLASH_FADE_MS = 180;

    private volatile boolean webReady = false;

    // Chiamato da AppReadyPlugin (thread del plugin, non quello principale): basta il flag
    // volatile, la condizione della splash viene riletta dal thread UI a ogni frame.
    public void markWebReady() {
        webReady = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // La splash va installata PRIMA di super.onCreate. Resta a schermo (copre WebView,
        // caricamento di Tailwind/font e primo disegno della pagina) finché la pagina non
        // dice di essere pronta; solo a freddo, cioè quando il processo non era in RAM.
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        splash.setKeepOnScreenCondition(() -> !webReady);
        splash.setOnExitAnimationListener(provider ->
                provider.getView().animate()
                        .alpha(0f)
                        .setDuration(SPLASH_FADE_MS)
                        .withEndAction(provider::remove)
                        .start());
        new Handler(Looper.getMainLooper()).postDelayed(() -> webReady = true, SPLASH_MAX_MS);

        registerPlugin(FolderAccessPlugin.class);
        registerPlugin(MediaControlsPlugin.class);
        registerPlugin(AppReadyPlugin.class);
        super.onCreate(savedInstanceState);
        applyFullscreen();
        requestHighRefreshRate();

        // Da Android 13 (API 33) le notifiche richiedono un permesso esplicito
        // dell'utente: senza chiederlo qui, la notifica di riproduzione (con
        // copertina, titolo e controlli) non comparirebbe mai, in silenzio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
            }
        }
    }

    // Chiede al sistema la modalità display con il refresh rate più alto a parità di
    // risoluzione (es. 120 Hz sugli Snapdragon 8 Elite con pannello LTPO). Di default una
    // WebView può restare a 60 Hz anche su schermi veloci: scroll e animazioni risultano
    // meno fluidi. È solo una preferenza: il sistema può comunque abbassare la frequenza
    // (risparmio batteria, contenuto fermo). Per toglierla basta rimuovere questa chiamata.
    private void requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? getDisplay()
                    : getWindowManager().getDefaultDisplay();
            if (display == null) return;
            Display.Mode current = display.getMode();
            Display.Mode best = current;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() > best.getRefreshRate()) {
                    best = mode;
                }
            }
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = best.getModeId();
            getWindow().setAttributes(params);
        } catch (Exception ignored) {
            // Funzione di comodo: se il dispositivo non la supporta si resta sul default.
        }
    }

    // Schermo intero vero: nasconde la barra di stato (l'orologio/notifiche in alto),
    // non solo evita che i contenuti ci finiscano sotto. Uno swipe dal bordo la fa
    // ricomparire un istante (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) invece di doverla
    // richiamare da un menu: è il comportamento "immersive" standard di Android.
    private void applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    // Senza questo, la barra di stato ricompariva e restava visibile ogni volta che
    // l'app tornava in primo piano (es. dopo aver aperto il selettore cartelle o
    // essere tornati dalla home di Android): va riapplicata a ogni ripresa del focus.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen();
    }
}
