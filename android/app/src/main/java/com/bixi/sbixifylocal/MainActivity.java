package com.bixi.sbixifylocal;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import android.content.pm.PackageManager;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FolderAccessPlugin.class);
        registerPlugin(MediaControlsPlugin.class);
        super.onCreate(savedInstanceState);
        applyFullscreen();

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
