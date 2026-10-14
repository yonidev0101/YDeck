# YDeck ב־Google Play: מדריך ההעלאה (מעודכן ל־0.8, 11 באוקטובר 2026)

שלב אחרי שלב ב־Play Console, עם הטקסט המוכן לכל שדה. הקבצים:

| מה | איפה |
|---|---|
| ה־AAB להעלאה | `dist/YDeck-0.8.aab` (חתום במפתח ההעלאה) |
| אייקון 512 | `store/play/icon-512.png` |
| תמונה ראשית (Feature graphic) 1024×500 | `store/play/feature-graphic-he.png` (ולדף האנגלי `feature-graphic-en.png`) |
| צילומי מסך לטלפון, 8 | `store/play/screenshots/YDeck-01.png` … `YDeck-08.png` (1080×1920) |
| מדיניות פרטיות | https://ydeck-app.pages.dev/privacy |
| אתר | https://ydeck-app.pages.dev |

---

## 1. יצירת האפליקציה

Play Console ← **Create app**:
- App name: **YDeck**
- Default language: **Hebrew – iw-IL**
- App or game: **App** · Free or paid: **Free**
- מסמנים את שתי ההצהרות (Developer Program Policies, US export laws) ← **Create app**.

## 2. חתימה: להשתמש במפתח שלנו (חשוב)

ברירת המחדל של Google היא לחתום את גרסת החנות במפתח של Google. אז ה־APK שבאתר (חתום במפתח שלנו) וגרסת החנות לא יותקנו זו על זו, ומי שהתקין מהאתר יצטרך להסיר ולהתקין מחדש. כדי שזה יהיה **אותו מפתח בכל מקום**, נותנים ל־Google את המפתח שלנו:

1. Test and release ← **App integrity** ← App signing ← **Change app signing key** (או "Use a different key" בהעלאה הראשונה).
2. בוחרים **Export and upload a key from Java keystore**. Google מציגה פקודה עם `pepk.jar` ומפתח הצפנה. מורידים את `pepk.jar` מהקישור שם, ומריצים (Google מציגה את הפקודה המדויקת; אלה הערכים שלנו):
   ```
   java -jar pepk.jar --keystore="C:\Users\user\.android-keys\ydeck-upload.jks" --alias=ydeck-upload --output=ydeck-signing.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```
   הסיסמה נמצאת ב־`C:\Users\user\.android-keys\ydeck-upload.properties`. אפשר גם לשלוח לי את הפקודה ש־Google מציגה, ואני אריץ אותה.
3. מעלים את `ydeck-signing.zip` ← Save.

אם מעדיפים את ברירת המחדל של Google: מדלגים על השלב, ואז מי שהתקין מהאתר יצטרך להסיר פעם אחת לפני ההתקנה מהחנות.

## 3. בדיקה סגורה (חובה בחשבון אישי: 12 בודקים × 14 יום)

1. **קבוצת גוגל** ב־groups.google.com: שם `ydeck-testers`, כל אחד יכול להצטרף.
2. Test and release ← Testing ← **Closed testing** ← Create track (או Alpha) ← **Testers**: מוסיפים את `ydeck-testers@googlegroups.com`.
3. **Create new release** ← מעלים את `dist/YDeck-0.8.aab`.
   - Release name: `0.8`
   - Release notes (עברית):
     ```
     <iw-IL>
     הגרסה הראשונה בחנות: ווידג'ט לכל פרויקט, וכל קישור נפתח בחשבון הנכון.
     </iw-IL>
     ```
4. Next ← Save ← **Send for review**. הבדיקה הראשונה לוקחת בדרך כלל כמה ימים.
5. אחרי האישור: **קישור ההצטרפות** (Join on the web) נשלח לבודקים יחד עם קישור הקבוצה. קודם מצטרפים לקבוצה, ואז מתקינים.
6. אחרי 14 יום עם 12 בודקים לפחות: Dashboard ← **Apply for production**.

## 4. דף החנות (Grow users ← Store presence ← Main store listing)

**App name (30):** `YDeck: כל פרויקט בווידג'ט`

**Short description (80):**
```
ווידג'ט לכל הפרויקטים שלך. כל קישור נפתח בחשבון הנכון.
```

**Full description:**
```
כל הפרויקט. במקום אחד.

יש לך כמה פרויקטים, ולכל אחד Firebase, Play Console, Vercel, GitHub ועוד, כל אחד בחשבון אחר. YDeck שמה את כולם בווידג'ט אחד על מסך הבית, וכל נגיעה פותחת את הקישור בחשבון של הפרויקט.

מה YDeck עושה
• ווידג'ט לכל פרויקט: הלוגו, הצבע והקישורים שלו, על זכוכית בגוון הפרויקט. מחליפים פרויקט בנגיעה בנקודות.
• החשבון הנכון: שירותי גוגל נפתחים בחשבון הגוגל של הפרויקט, ו־Vercel בערוץ Chrome שבו מחובר החשבון שלו.
• מדביקים קישור, והשירות מזוהה לבד: 161 שירותים עם הלוגו האמיתי.
• כל גודל: מ־4×2 ועד שורה אחת.
• ערכים להעתקה: package, repo, מזהים.
• הכול נערך בטלפון. בלי חשבון, בלי שרת, בלי פרסומות.

פרטיות
הפרויקטים נשמרים בטלפון בלבד. רק שמות אתרים נשלחים, כדי להביא את האייקון שלהם.
```

**Graphics:** App icon ← `icon-512.png` · Feature graphic ← `feature-graphic-he.png` · Phone screenshots ← 8 הקבצים מ־`store/play/screenshots/` לפי הסדר.

**Store settings:** App category: **Productivity** · Contact: `yonidev0101@gmail.com` · Website: `https://ydeck-app.pages.dev`

**דף באנגלית (Add translation ← English):**
- Name: `YDeck: Projects widget`
- Short: `One widget for all your projects. Every link opens in the right account.`
- Full:
```
Your whole project. One place.

You have several projects, and each has its Firebase, Play Console, Vercel, GitHub and more, each in a different account. YDeck puts them all in one home-screen widget, and every tap opens the link in that project's account.

• A widget per project: its logo, colour and links on glass tinted with the project colour. Tap the dots to switch projects.
• The right account: Google services open in the project's Google account; Vercel opens in the Chrome channel signed in to its account.
• Paste a link and the service is recognised: 161 services with their real logos.
• Any size, from 4×2 down to a single row.
• Values to copy: package, repo, IDs.
• Everything is edited on the phone. No account, no server, no ads.

Privacy: projects stay on your phone. Only website names are sent, to fetch their icons.
```
- Feature graphic ← `feature-graphic-en.png`. אפשר להשתמש באותם צילומי מסך.

## 5. תוכן האפליקציה (Policy and programs ← App content)

| שאלה | תשובה |
|---|---|
| Privacy policy | `https://ydeck-app.pages.dev/privacy` |
| App access | **All functionality is available without special access** |
| Ads | **No, my app does not contain ads** |
| Content rating | שאלון IARC: קטגוריה **All Other App Types**, מייל `yonidev0101@gmail.com`. לכל השאלות (אלימות, מין, שפה, סמים, הימורים, תקשורת בין משתמשים, שיתוף מיקום, רכישות) עונים **No** |
| Target audience | **18 and over** · לא פונה לילדים |
| News app | No |
| COVID-19 / Health / Financial features / Government | No / No / My app doesn't provide any financial features / No |
| Data safety | הטבלה למטה |

### Data safety
- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **No** (אין לנו מידע לשמור: הכול בטלפון ונמחק בהסרה)

סוג המידע היחיד: **App activity → Other user-generated content**
- Collected: **Yes** · Shared: **Yes** (Google ו־DuckDuckGo מקבלים את שם האתר כדי להחזיר אייקון)
- Processed ephemerally: **Yes**
- Required (users can't turn it off)
- Purpose: **App functionality**

כל שאר הסוגים (מייל, שם, מיקום, מזהי מכשיר, היסטוריית גלישה, קריסות): **לא**. כתובות המייל של הפרויקטים לא יוצאות מהטלפון. הגיבוי של אנדרואיד לא נחשב איסוף, כי הוא מוצפן ומנוהל על ידי המערכת.

## 6. אחרי הבדיקה

- Production ← Create new release ← אותו AAB (או גרסה חדשה) ← Countries: **Israel** (ואחר כך כל העולם, כשתהיה אנגלית באפליקציה).
- כל גרסה חדשה: אני מעלה את `versionCode`, בונה `dist/YDeck-<גרסה>.aab`, ואתה מעלה ב־Closed testing או Production.

---

## נספחים

### המפתח
- מפתח ההעלאה: `C:\Users\user\.android-keys\ydeck-upload.jks`, והסיסמאות ב־`ydeck-upload.properties` באותה תיקייה. **מחוץ לגיט. צריך לגבות את כל התיקייה** (כונן חיצוני או ענן מוצפן), יחד עם המפתח של YVoice.
- טביעת האצבע (SHA-256): `6D:CD:65:0C:7F:FA:85:FA:3E:40:75:34:28:2D:5C:B2:40:0A:A8:83:CA:6B:32:85:22:37:17:99:98:38:83:6D`
- `./gradlew assembleRelease bundleRelease` חותם אוטומטית במפתח ההעלאה כשהקובץ קיים.

### המעבר אצלך בטלפון (פעם אחת, מגרסה 0.5 ומטה)
1. ב־YDeck הישנה: גיבוי ← **ייצוא לקובץ**. 2. מסירים את YDeck. 3. מתקינים את `releases/YDeck-0.8-glasspreview.apk`. 4. במסך הפתיחה: **שחזור מקובץ**, ומוסיפים מחדש את הווידג'טים.

### מה השתנה לקראת החנות
- 0.6: מסך פתיחה ריק עם "נסה עם פרויקט לדוגמה" (Nova), מפתח העלאה אמיתי, גיבוי אוטומטי של אנדרואיד (רק `projects.json` ו־`logos/`), תצוגה מקדימה של הווידג'ט ברשימת הווידג'טים בעיצוב הזכוכית.
- 0.7: אנימציית הלוגו ("The deal") בפתיחת האפליקציה.
- 0.8: התצוגה המקדימה בעורך בעיצוב הזכוכית (הייתה עדיין בריבועים), ותיקון כיוון הטקסט בשורת הפרויקט כשהסוג באנגלית ("SaaS · 7 קישורים").

### נכסים
- `store/play/source/feature.html` + `shoot.cjs`: התמונה הראשית והאייקון. הגרסה הקודמת (ריבועים) ב־`store/play/old-squares/`.
- `store/play/source/screenshot.html` + `shoot-screens.cjs`: צילומי המסך, ממסכים אמיתיים של האפליקציה (`source/screens/`) עם פרויקטי הדוגמה Nova, Orbit ו־Pulse.
- נשאר: לרנדר מחדש את הסרטונים והפרסומות עם ווידג'ט הזכוכית (סרטון ה־explainer כבר בזכוכית).
