package com.bixi.sbixifylocal;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;
import android.content.pm.PackageManager;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FolderAccessPlugin.class);
        registerPlugin(MediaControlsPlugin.class);
        super.onCreate(savedInstanceState);
        // Android disegna di default i contenuti "sotto" la barra di stato (edge-to-edge):
        // questo dice al sistema di riservare lo spazio invece di lasciarci disegnare sotto,
        // così l'header dell'app non finisce più coperto dall'orologio/icone di sistema.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

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
}
