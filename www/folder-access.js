// Wrapper leggero attorno al plugin nativo FolderAccess.
// Usalo così, dal codice del player:
//
//   const folder = await SbixiFolderAccess.pickFolder();
//   // folder = { uri, name, files: [{ uri, name, path, size }, ...] }
//   const track = await SbixiFolderAccess.readAudioAsBlobUrl(file.uri);
//   audioEl.src = track.blobUrl;
//   // ricordati di revocare l'URL quando cambi traccia:
//   URL.revokeObjectURL(precedenteBlobUrl);

const SbixiFolderAccess = (() => {
  function plugin() {
    const p = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.FolderAccess;
    if (!p) throw new Error('Plugin FolderAccess non disponibile (siamo dentro l\'app nativa?)');
    return p;
  }

  async function pickFolder() {
    return await plugin().pickFolder();
  }

  async function listFolder(uri) {
    return await plugin().listFolder({ uri });
  }

  async function listPersistedFolders() {
    const res = await plugin().listPersistedFolders();
    return res.folders;
  }

  async function releaseFolder(uri) {
    return await plugin().releaseFolder({ uri });
  }

  function base64ToBlob(base64, mimeType) {
    const byteChars = atob(base64);
    const byteNumbers = new Array(byteChars.length);
    for (let i = 0; i < byteChars.length; i++) {
      byteNumbers[i] = byteChars.charCodeAt(i);
    }
    const byteArray = new Uint8Array(byteNumbers);
    return new Blob([byteArray], { type: mimeType });
  }

  // Legge un file audio dal suo content-uri e restituisce un blob URL
  // riproducibile direttamente in <audio src="...">, con seek funzionante.
  async function readAudioAsBlobUrl(uri) {
    const res = await plugin().readFile({ uri });
    const blob = base64ToBlob(res.data, res.mimeType);
    return { blobUrl: URL.createObjectURL(blob), mimeType: res.mimeType };
  }

  return { pickFolder, listFolder, listPersistedFolders, releaseFolder, readAudioAsBlobUrl };
})();
