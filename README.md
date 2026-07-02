# Voice Control (V1)

Offline voice control for Android — bolo aur app open/close karo.
Supported abhi: **YouTube, Chrome, Instagram**.

## Kaise chalega (Itel S23 / kisi bhi Android 7+ phone pe)

1. Is project ko **Android Studio** mein `Open` karo (poora folder select karo).
2. Gradle sync hone do (pehli baar internet chahiye, dependencies download hongi).
3. USB debugging on karke Itel S23 ko connect karo, ya `.apk` build karke seedha install karo.
4. App open karo → **Start Listening** dabao → Microphone permission allow karo.
5. Bolo: `"open youtube"`, `"close chrome"`, `"open instagram"` etc.

## Architecture (honest breakdown)

- **Kotlin** — poora app isi mein hai. Android ka `SpeechRecognizer` (offline mode) use hota hai — koi custom C++/ASR engine nahi, kyunki wo already Android mein built-in hai.
- **Foreground Service** — background mein continuous sunta rehta hai, notification ke saath (Android ki requirement hai, chhupa nahi sakte).
- **Command matching** — simple keyword + Levenshtein distance (fuzzy match), taaki halka-sa galat sunne pe bhi command chal jaye.

## Jaanbojh ke limitations (V1)

- **"Close" command 100% guaranteed nahi hai.** Android security ki wajah se koi app doosre app ko force-stop nahi kar sakta bina root ya Accessibility Service ke. Abhi `killBackgroundProcesses()` use ho raha hai jo sirf background process ko hi kill kar sakta hai — agar target app already foreground mein hai to ye kaam nahi karega.
  - Agar tumhe real "close" chahiye (jaisa app-switcher se swipe karna), next step **Accessibility Service** implement karna hoga — bologe to wo bhi bana dunga.
- Offline recognition ki accuracy Google ke on-device model pe depend karti hai — sab Android phones mein available nahi hoti (zyada tar modern phones mein hai, Itel S23 pe check kar lena Settings → Google → Speech mein "Offline speech recognition" download hua hai ya nahi).
- Battery ke liye: continuous listening thoda drain karega — ye tradeoff hai jo har "always listening" app ka hota hai (Google Assistant bhi wahi karta hai).

## GitHub pe push karna

```bash
git init
git add .
git commit -m "Voice Control V1 - open/close YouTube, Chrome, Instagram"
git branch -M main
git remote add origin <tumhara-repo-url>
git push -u origin main
```

## Next steps (jab bologe)

- Accessibility Service se real app-close
- Aur apps add karna (map mein bas entry add karni hogi)
- C++/NEON optimization — sirf tab jab measure karke pata chale ki zaroorat hai
