# Jarvis Assistant

Hindi/Hinglish voice AI assistant for Android: ek pyaari, dost jaisi awaaz jo aapka phone chalati hai. Offline wake word, phone control, aur ek floating anime character. Kotlin + Jetpack Compose me bana hai.

## Kya-kya kar sakti hai

- **Dost jaisi baat:** pyaar se bulati hai, haal poochhti hai, mazaak karti hai, aur aapki baatein yaad rakhti hai.
- **"Jarvis" bolke jagao:** offline wake word, aur phir baat-cheet mode me baar-baar bolna nahi padta.
- **Phone control:** app kholna, call, SMS, WhatsApp message, alarm, timer, torch, volume, notifications padhna, call utha/kaat dena, QR scan, camera.
- **Screen control (Accessibility):** screen padhkar tap, type, scroll aur back/home/recents.
- **Macros:** "macro record karo" bolke kaam karo, phir "macro save karo" se naam do. Baad me naam se chalega.
- **Floating character:** dusre apps ke upar tairti hai. Drag karo, tap karke bolo.
- **Triggers:** charger lagne/nikalne aur battery low par khud bolti hai.
- **Default assistant:** power button long-press se khulti hai.
- **Live wallpaper:** teal glow aur udti titliyan.
- **Boot par auto-start** (Android 14+ par ek notification dabana padta hai).

## Setup

1. Release/Actions se APK install karo.
2. App kholo, upar Settings me:
   - apna **naam** aur pyaar ka naam daalo
   - **Gemini API key** daalo (aistudio.google.com se free milti hai)
   - **Accessibility** aur **Notification access** ON karo
3. Mic, phone, contacts aur SMS ki permissions Allow karo.
4. **Floating: OFF** wala chip dabao aur "Display over other apps" ON karo.
5. Pehli baar wake word ka chhota model (~40 MB) download hoga. WiFi par karo.
6. Ab kisi bhi screen par bolo: **"Jarvis"**.

Android 13+ par sideload ki hui app me Accessibility grey ho to: Settings -> Apps -> Jarvis -> teen dot -> **Allow restricted settings**.

Xiaomi/POCO/Realme phones par Jarvis ko Autostart aur "No battery restrictions" do, nahi to phone background service maar deta hai.

## Build karna

GitHub par push karo. Actions me **Build APK** chalega aur Artifacts me APK milegi.
Local par: Android Studio me kholo ya `gradle :app:assembleDebug`.

## Privacy

- API key aur yaad rakhi hui baatein sirf aapke phone me save hoti hain.
- Aapka text aur kaam Google Gemini ko bheja jaata hai (aapki apni API key se).
- Password, OTP aur card number yaad nahi rakhti.

## Kisse bana hai

Kotlin, Jetpack Compose, OkHttp, [Vosk](https://alphacephei.com/vosk/) (offline wake word, Apache 2.0), Google Gemini API, ML Kit code scanner.
