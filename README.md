# Plus POS — App natif Kotlin (SUNMI + Bluetooth ekstèn)

App Android natif (Kotlin) pou ajan yo konekte, (pita) vann tikè, epi
enprime resi — **mache sou nenpòt aparèy Android**: si se yon SUNMI, li
itilize enprimant entegre a; sinon, li itilize yon enprimant tèmik
Bluetooth eksitèn (estanda ESC/POS).

## Kijan sistèm enprimant an fonksyone

`PrinterManager.kt` detekte otomatikman ki kalite aparèy l ap kouri sou li:

- **SUNMI** (detekte pa `Build.MANUFACTURER`/`Build.BRAND`) → itilize
  `SunmiPrinterHelper.kt` (sèvis entegre SUNMI a, pa AIDL)
- **Nenpòt lòt aparèy** (lòt mak POS, oswa yon senp telefòn Android) →
  itilize `BluetoothPrinterHelper.kt` (pwotokòl ESC/POS estanda pa
  Bluetooth, mache ak prèske tout enprimant tèmik Bluetooth ki egziste)

Rès app la (`MainActivity`, e pita ekran vann tikè a) rele **sèlman**
`PrinterManager` — li pa bezwen konnen ki kalite enprimant ki reyèlman
itilize anba a.

Fichye AIDL SUNMI yo (`IWoyouService.aidl`, `ICallback.aidl`) **deja
ekri** nan pwojè a (baze sou estrikti piblik ki estab depi plizyè ane).
Ou PA bezwen telechaje anyen apa pou SUNMI. Si Android Studio siyale yon
erè "method not found" pandan konpilasyon (sa ta vle di SUNMI chanje yon
bagay nan dènye vèsyon SDK yo), konpare fichye a ak dènye vèsyon ofisyèl
la sou developer.sunmi.com — men sa pa dwe rive nòmalman.

## Sa ki deja fèt

- Ekran koneksyon (Device ID pèsistan, idantifyan, modpas)
- Koneksyon ak `/auth/login` (menm backend ak sit web/Expo a)
- Marye Device ID otomatikman ak ajan an via `/tenant/register-device`
- Sistèm enprimant ipbrid (SUNMI otomatik + Bluetooth ekstèn kòm fallback)
- Bouton "Chwazi Enprimant Bluetooth" (parèt sèlman si se pa yon SUNMI)
  pou chwazi youn nan enprimant ki deja **pè** (paired) nan Bluetooth
  telefòn/POS la
- Bouton "Tès Enprimant" pou konfime enprimant la mache anvan w kontinye
- `minSdk 21` (Android 5.0+) ak sipò toude achitekti (32-bit ak 64-bit)
  nan YON SÈL APK

## Sa ki rete pou bati

- Ekran vann tikè (chwazi tiraj, jwèt, nimewo, kantite)
- Resi tikè konplè (`printTicketReceipt` deja egziste nan tou de klas
  enprimant yo, fòma final ka ajiste)
- Istorik tikè vandi / anile
- Balans ajan an
- SQLite/Room pou sipò offline (vann san entènèt, senkwonize apre)
- Si ou bezwen scanner barcode: yon `SunmiScannerHelper.kt`/
  `BluetoothScannerHelper.kt` similè

## ÈTAP 1 — Pou aparèy ki PA SUNMI: pè enprimant Bluetooth la anvan

Sou telefòn/POS la: **Paramèt → Bluetooth → Pè yon nouvo aparèy** →
chwazi enprimant tèmik la (souvan li rele yon bagay tankou "Printer",
"BT-Printer", "POS-58", elatriye — gade dokiman enprimant ou an pou kòd
PIN koneksyon si l mande l, souvan `0000` oswa `1234`).

## ÈTAP 2 — Louvri pwojè a nan Android Studio

1. Enstale [Android Studio](https://developer.android.com/studio) (gratis)
2. `File → Open` → chwazi dosye `PlusGroupPOS/` (rasin pwojè a, kote
   `settings.gradle` ye a)
3. Tann Gradle Sync fini (premye fwa a ka pran plizyè minit)
4. Ajoute yon ikòn app (opsyonèl pou tès): clic dwat sou `res/` →
   `New → Image Asset`

## ÈTAP 3 — Teste sou yon aparèy reyèl

1. Aktive "Opsyon devlopè" ak "Debogaj USB" sou aparèy la
   (Paramèt → Sou telefòn → tape "Nimewo bati" 7 fwa, epi Paramèt →
   Opsyon devlopè → Debogaj USB)
2. Konekte aparèy la ak kab USB
3. Nan Android Studio, klike bouton vèt "Run" (▶) — chwazi aparèy la
   nan lis la
4. App la ap enstale e louvri dirèkteman sou aparèy la. Si se pa yon
   SUNMI, klike "Chwazi Enprimant Bluetooth" epi chwazi enprimant ou
   pè a (ÈTAP 1)

## ÈTAP 4 — Konpile APK final pou distribye

1. Nan Android Studio: `Build → Generate Signed Bundle / APK`
2. Chwazi **APK** (pa "Android App Bundle")
3. Kreye yon **keystore** (yon sèl fwa — konsève fichye `.jks` la ak modpas
   li an sekirite, w ap bezwen menm keystore a pou CHAK mizajou app la
   apre sa)
4. Chwazi "release" build variant
5. Android Studio ap jenere `app-release.apk` nan
   `app/release/app-release.apk`

## Chanje URL backend la

Si backend la deplwaye sou yon lòt adrès pase
`https://plusgroup-lottery-api.onrender.com/api/v1/`, chanje l nan:

```
app/src/main/java/com/plusgroup/pos/network/ApiClient.kt
```
(varyab `BASE_URL`)

## Poukisa APK a te "pa enstale sou tout aparèy" anvan

Dyagnostik ki te fèt la kòrèk — koz ki pi komen yo:

1. **ABI manke** — si APK a te konpile pou yon sèl achitekti (pa egzanp
   sèlman 64-bit), li pa enstale sou aparèy 32-bit pi ansyen yo. Pwojè sa
   a konfigire pou mete TOULEDE (`armeabi-v7a` + `arm64-v8a`) nan menm
   APK a — wè `ndk { abiFilters }` nan `app/build.gradle`.
2. **`minSdk` twò wo** — si `minSdk` te mete a yon vèsyon Android twò
   resan, aparèy ki gen ansyen vèsyon Android pa ka enstale l ditou.
   Pwojè sa a mete `minSdk = 21` (Android 5.0) pou kouvri prèske tout
   aparèy.
3. **SDK/lib ki pa konpatib ak modèl POS espesifik la** — sa se pou sa
   sistèm ipbrid la (SUNMI otomatik + Bluetooth ekstèn kòm fallback)
   itil: si yon aparèy pa SUNMI, li otomatikman sèvi ak apwòch Bluetooth
   jeneral la olye l eseye itilize yon SDK ki pa konpatib.

## Estrikti pwojè a

```
PlusGroupPOS/
├── settings.gradle
├── build.gradle
├── gradle.properties
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── aidl/woyou/aidlservice/jiuiv5/
        │   ├── IWoyouService.aidl        ← deja ekri, pa bezwen touche
        │   └── ICallback.aidl            ← deja ekri, pa bezwen touche
        ├── java/com/plusgroup/pos/
        │   ├── LoginActivity.kt
        │   ├── MainActivity.kt
        │   ├── network/
        │   │   ├── ApiClient.kt
        │   │   ├── ApiService.kt
        │   │   └── models/Models.kt
        │   ├── printer/
        │   │   ├── PrinterManager.kt      ← chwazi SUNMI/Bluetooth otomatikman
        │   │   ├── SunmiPrinterHelper.kt
        │   │   └── BluetoothPrinterHelper.kt
        │   └── util/
        │       ├── DeviceIdHelper.kt
        │       └── SessionManager.kt
        └── res/
            ├── layout/ (activity_login.xml, activity_main.xml)
            ├── values/ (strings.xml, themes.xml)
            └── drawable/ (bg_input.xml)
```
