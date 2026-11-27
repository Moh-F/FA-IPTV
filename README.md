# FA IPTV

مشغّل IPTV احترافي للجوال والتابلت و Android TV و Google TV و Fire TV.
Kotlin + Jetpack Compose + Media3 ExoPlayer. يعمل باللمس وبالريموت.

> التطبيق لا يحتوي على أي قنوات أو محتوى. المستخدم يضيف اشتراكه الخاص (Xtream Codes أو M3U).

## الميزات
- **تسجيل الدخول:** Xtream Codes (مع تاريخ الانتهاء وعدد الاتصالات) وقوائم M3U/M3U8 من رابط أو ملف. روابط `get.php` تتحول تلقائياً إلى حساب Xtream.
- **البث المباشر:** فئات، مفضلة، آخر القنوات، تقليب القنوات بالأسهم أو CH+/CH-، دليل البرامج EPG (من Xtream أو XMLTV).
- **إعادة البث Catch-up:** Xtream timeshift وأنواع M3U المعروفة (default, append, shift, flussonic, xc).
- **أفلام ومسلسلات:** صور، تفاصيل، مواسم وحلقات، متابعة من حيث توقفت، الحلقة التالية تلقائياً.
- **المشغّل:** اختيار الصوت والترجمة والجودة، مقاس الصورة، السرعة، صورة داخل صورة PiP، فتح بمشغّل خارجي (VLC، MX…)، إعادة اتصال تلقائية.
- **التسجيل:** تسجيل القناة الآن أو جدولة برنامج من الدليل (ملفات .ts).
- **عدة حسابات + قفل أبوين PIN:** قفل أي فئة بضغطة مطوّلة، وفئات الكبار تُقفل تلقائياً.
- **بحث** في القنوات والأفلام والمسلسلات يفهم العربية (همزات، تاء مربوطة…).
- **الإدخال من الجوال:** صفحة على الشبكة المنزلية لإضافة الحساب من الجوال بدل الكتابة بالريموت، مع سجل الأخطاء المباشر.
- **المعايير الثابتة:** شاشة بداية بأيقونة وشريط تقدم ونسبة مئوية، رقم الإصدار على وشاح مائل في الأيقونة، تحديث داخلي عند الفتح، توقيع ثابت (التحديث فوق القديم بدون فقد بيانات)، عربي/إنجليزي/ألماني، فاتح/داكن.

## التحكم بالريموت
| الزر | في القوائم | في المشغّل |
|---|---|---|
| OK | فتح/تشغيل | إظهار الأزرار |
| ضغطة مطوّلة أو Menu | قائمة القناة / قفل فئة / مفضلة | إظهار الأزرار |
| أعلى/أسفل | تنقّل | القناة السابقة/التالية (مباشر) |
| يمين/يسار | تنقّل | ±10 ثوانٍ (أفلام وإعادة البث) |
| رجوع | خطوة للخلف | إخفاء الأزرار ثم خروج |

## خطوات النشر (مرة واحدة)

### 1) مفتاح التوقيع (على جهازك فقط)
```powershell
cd $HOME\Downloads
git clone https://github.com/Moh-F/FA-IPTV.git
cd FA-IPTV
powershell -ExecutionPolicy Bypass -File scripts\create_keystore.ps1 -App fa-iptv
```
النتيجة: مجلد `C:\Users\<اسمك>\app-keystores\fa-iptv` فيه `fa-iptv.p12` و`fa-iptv.p12.enc` (نسخة احتياطية مشفّرة، احفظها في مكانين) و`fa-iptv.p12.b64`.

### 2) الأسرار الأربعة في GitHub
```powershell
$d = "$HOME\app-keystores\fa-iptv"
Get-Content "$d\fa-iptv.p12.b64" -Raw | gh secret set KEYSTORE_B64 -R Moh-F/FA-IPTV
gh secret set KEYSTORE_PASSWORD -R Moh-F/FA-IPTV
gh secret set KEY_PASSWORD -R Moh-F/FA-IPTV
gh secret set KEY_ALIAS -R Moh-F/FA-IPTV -b "fa-iptv-release"
gh secret list -R Moh-F/FA-IPTV
```
`KEYSTORE_PASSWORD` و`KEY_PASSWORD` نفس كلمة المرور التي اخترتها في الخطوة 1. النتيجة: `gh secret list` يعرض الأسرار الأربعة.
(بدون gh: GitHub ← المستودع ← Settings ← Secrets and variables ← Actions ← New repository secret.)

### 3) إصدار جديد
```powershell
git pull
git tag v1.0.0
git push origin v1.0.0
```
بعد 5–8 دقائق: GitHub ← Releases ← مسودة `v1.0.0` ← **Publish release**.
لكل إصدار لاحق: غيّر `VERSION_NAME` في `gradle.properties` واكتب ملاحظات `release_notes/*.md` ثم tag جديد.

### 4) التثبيت على Fire TV
```powershell
adb connect <IP-الجهاز>:5555
Invoke-WebRequest https://github.com/Moh-F/FA-IPTV/releases/latest/download/FA-IPTV-v1.0.0.apk -OutFile FA-IPTV.apk
adb install -r FA-IPTV.apk
```
النتيجة: `Success`. التحديثات اللاحقة تأتي من داخل التطبيق.

## البناء
CI يبني ويختبر كل push على `main` (ملف APK للتجربة في Actions ← Artifacts ← reports).
الإصدارات الموقّعة تُبنى فقط من tag يبدأ بـ `v`.

## الخصوصية
انظر [PRIVACY.md](PRIVACY.md).
