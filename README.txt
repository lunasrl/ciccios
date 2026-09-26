CICCIO'S SUNMI ANDROID 1.0.0
Compatibilità minima: Android 7.1 / API 25.
Polling ordini: 10 secondi.
Stampa: libreria SUNMI printerlibrary 1.0.18.
Flusso:
- pending/on-hold: allarme + ACCETTA
- ACCETTA: POST /accept, stato processing, stampa automatica
- processing: CONSEGNATO
- CONSEGNATO: POST /complete, stato completed
- RISTAMPA: stampa senza cambiare stato

Per compilare:
1. Aprire la cartella in Android Studio.
2. Lasciare sincronizzare Gradle e scaricare le dipendenze.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).
4. Installare l'APK sul SUNMI V2.
5. Inserire URL API e token mostrati da WordPress > Impostazioni > Ciccio's SUNMI.

Nota: il progetto usa HTTPS. Il sito deve avere un certificato SSL valido.
