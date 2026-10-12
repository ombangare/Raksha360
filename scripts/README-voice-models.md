# Raksha360 offline Voice SOS model setup

The original ZIP does not include trained models. The old fallback was an energy detector, not speech recognition, and could treat unrelated loud audio as a phrase. This project removes that fallback.

From the project root in Windows PowerShell, run:

```powershell
.\scripts\prepare-voice-model.ps1
```

The script downloads these Apache-2.0 Vosk model archives over HTTPS from the official Vosk model host and checks for required model files before copying them into Android assets:

- `vosk-model-small-en-us-0.15` for offline English phrase recognition (about 40 MB compressed per the model list).
- `vosk-model-spk-0.4` for local speaker embeddings (about 13 MB per the model list).

Then rebuild and install the Android application. On first Guard Mode start, the app copies both directories into its private files directory and loads them locally. Raw microphone frames are not sent to the Vosk service or a remote speech API.

**Important limits:** the English model is not multilingual. Speaker similarity thresholds need tests with positive and impostor samples from the actual device/environment before real-life reliance. Android foreground-service and microphone permissions still apply. Do not treat a successful build as proof of reliable emergency detection; test the installed APK on the target phone.
