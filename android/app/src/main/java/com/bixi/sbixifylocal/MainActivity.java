package com.bixi.sbixifylocal;

import android.os.Bundle;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FolderAccessPlugin.class);
        super.onCreate(savedInstanceState);
        // Android disegna di default i contenuti "sotto" la barra di stato (edge-to-edge):
        // questo dice al sistema di riservare lo spazio invece di lasciarci disegnare sotto,
        // così l'header dell'app non finisce più coperto dall'orologio/icone di sistema.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    }
}
