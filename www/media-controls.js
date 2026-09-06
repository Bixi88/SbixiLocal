// Wrapper per il plugin nativo MediaControls: notifica media reale (copertina, titolo,
// tasti prec/play/succ) e servizio in primo piano per non fermare la riproduzione a
// schermo spento. Uso:
//
//   SbixiMediaControls.update({ title, artist, artworkBase64, isPlaying, positionMs, durationMs });
//   SbixiMediaControls.clear();
//   SbixiMediaControls.on('play', () => ...);   // tasto/notifica/cuffie
//   SbixiMediaControls.on('pause', () => ...);
//   SbixiMediaControls.on('next', () => ...);
//   SbixiMediaControls.on('prev', () => ...);
//   SbixiMediaControls.on('seek', ({ positionMs }) => ...);

const SbixiMediaControls = (() => {
  function plugin() {
    return window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.MediaControls;
  }

  async function update(data) {
    const p = plugin();
    if (!p) return; // fuori dall'app nativa (es. in un browser normale durante lo sviluppo)
    try { await p.updateNowPlaying(data); } catch (err) { /* non bloccante */ }
  }

  async function clear() {
    const p = plugin();
    if (!p) return;
    try { await p.clearNowPlaying(); } catch (err) {}
  }

  function on(eventName, callback) {
    const p = plugin();
    if (!p) return;
    p.addListener(eventName, callback);
  }

  return { update, clear, on };
})();
