import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.bixi.sbixifylocal',
  appName: 'SbixiLocal',
  webDir: 'www',
  // Sfondo nero della WebView finché la pagina non ha disegnato: niente flash bianco.
  backgroundColor: '#000000'
};

export default config;
