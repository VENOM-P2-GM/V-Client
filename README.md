# V Client — VENOM-P2-GM

عميل ألعاب كامل بـ **بيئة معزولة** (زي Atlas Client): كل بروفايل يعيش في مجلده الخاص
بملفاته ومكتباته وجلساته وسجلاته، وبيتشغل كجلسة مستقلة — تقدر تشغل أكتر من بروفايل في نفس الوقت.

> ⚠️ **ملاحظة**: بيئة البناء دي (سبينج) مقدرتش تنزّل Android SDK (سيرفرات Google محظورة من هنا)،
> فملف APK ما اتبنىش جوه. المشروع كامل وجاهز: افتحه في **Android Studio** وهيتبني بضغطة واحدة.

---

## 📦 المحتوى

| المجلد | الوصف |
|---|---|
| `android/` | **العميل الكامل للأندرويد** (Kotlin + Jetpack Compose) — المشروع الرئيسي |
| `web/` | نسخة ويب شغالة من نفس المعمارية — تجربتها مباشرة في المتصفح (preview) |

---

## 🤖 أندرويد (المشروع الرئيسي)

### التشغيل
1. افتح مجلد `android/` في **Android Studio** (Hedgehog أو أحدث).
2. استنى الـ Sync (هينزّل كل الـ dependencies من Google/Maven).
3. **Run** — أو من الطرفية: `cd android && ./gradlew assembleDebug`
4. الـ APK هيكون في: `android/app/build/outputs/apk/debug/app-debug.apk`

> لو مش عندك Gradle wrapper jar: Android Studio هيولّده تلقائياً من `gradle/wrapper/gradle-wrapper.properties`.

### المميزات
- **بيئة معزولة لكل بروفايل** — `files/profiles/<id>/` كاملة:
  ```
  profiles/<id>/
  ├── env.json           هوية البروفايل
  ├── game/              versions · libraries · assets
  ├── saves/             الحفظ (لكل بروفايل)
  ├── mods/              مودات البروفايل بس
  ├── resourcepacks/  shaderpacks/  config/
  ├── auth/session.json  الجلسة المعزولة (توكن + UUID)
  ├── logs/              سجلات التشغيل
  └── reports/           أمر التشغيل الكامل
  ```
- **محرك V الداخلي (V-Engine)** — محرك تجريبي مدمج بيشغل بيئة معزولة كاملة
  (تيكات، مobs، حفظ دوري في `saves/world.dat`) — **بدون إنترنت**، عشان تقدر تجرّب العزل فوراً.
- **ماينكرافت جاڤا (عبر Termux)** — الخط الكامل:
  `manifest ← version JSON ← libraries ← client jar ← assets ← natives ← session ← أمر JVM كامل`.
  على الأندرويد اللعبة بتشتغل جوه **Termux**: V Client بينزّل كل حاجة جوه البيئة المعزولة،
  بيبني أمر التشغيل (Xmx/classpath/natives/arguments) وبيبعته لـ Termux بـ `com.termux.RUN_COMMAND`.
  - ثبّت Termux من **F-Droid** + `pkg install openjdk-17`.
  - لو Termux مش موجود: العميل بيحفظ أمر التشغيل كامل في `reports/last-launch.txt` ويعرض رسالة واضحة.
- **حسابات Offline** — UUID بـ name-hash (معيار MultiMC).
- **واجهة عربية/إنجليزية** RTL، ثيمين (Venom بنفسجي / Toxic أخضر)، شاشة كونسول حي.

### البنية
```
android/app/src/main/java/com/venom/vclient/
├── MainActivity.kt
├── core/
│   ├── Paths.kt          مسارات البيئة المعزولة
│   ├── Profiles.kt       إدارة البروفايلات
│   ├── Accounts.kt       الحسابات + name-hash
│   ├── SettingsStore.kt  الإعدادات
│   ├── Net.kt / Downloader.kt   تنزيل (resume + sha1 + retries)
│   ├── Mojang.kt         manifest / version JSON / libraries / rules
│   ├── JavaLaunch.kt     بناء أمر JVM (modern + legacy formats)
│   ├── ZipExtractor.kt   استخراج natives
│   ├── TermuxBridge.kt   تسليم أمر التشغيل لـ Termux
│   ├── Pipeline.kt       آلة حالات التشغيل (jobs + stages)
│   └── engine/VEngine.kt المحرك الداخلي
└── ui/                   Compose UI (6 شاشات + ثيم + strings ar/en)
```

---

## 🌐 نسخة الويب (preview شغالة)

نفس المعمارية بالـ Node.js — هتشتغل على أي جهاز:

```bash
cd web
npm install
npm run build
npm start          # http://localhost:3001
```

- **Home**: تشغيل مع progress حية + كونسول WebSocket.
- **Profiles**: إنشاء/حذف/تفاصيل البيئة المعزولة.
- **Mods**: رفع .jar داخل بيئة البروفايل + تفعيل/تعطيل.
- **Accounts**: Offline + Microsoft (device code flow — حط client id في الإعدادات).
- **Minecraft Java على الويب/ديسكتوب**: نفس الخط الكامل + spawn JVM مباشر (يبحت عن Java على جهازك).

---

## ⚖️ قانوني
- V Client مشروع شخصي (VENOM-P2-GM) للتعلم والاستخدام الشخصي.
- ملفات اللعبة بتنزل من خوادم Mojang الرسمية (نفس أي لانشر غير رسمي).
- Minecraft علامة مسجلة لموجان. ما في أي علاقة رسمية بين V Client وموجان.
