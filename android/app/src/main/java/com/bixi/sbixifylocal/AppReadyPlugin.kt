package com.bixi.sbixifylocal

import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

// Ponte minimale JS -> nativo: la pagina chiama AppReady.ready() quando ha disegnato il
// primo frame "vero" (contenuti, font), e MainActivity solo allora toglie la splash screen.
@CapacitorPlugin(name = "AppReady")
class AppReadyPlugin : Plugin() {
    @PluginMethod
    fun ready(call: PluginCall) {
        (activity as? MainActivity)?.markWebReady()
        call.resolve()
    }
}
