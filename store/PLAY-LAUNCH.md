# YDeck ב־Google Play: מדריך ההעלאה (מעודכן ל־0.9.7, 22 באוקטובר 2026)

שלב אחרי שלב ב־Play Console, עם הטקסט המוכן לכל שדה. הקבצים:

| מה | איפה |
|---|---|
| ה־AAB להעלאה | `dist/YDeck-0.9.7.aab` (versionCode 16, חתום במפתח ההעלאה) |
| אייקון 512 | `store/play/icon-512.png` |
| תמונה ראשית (Feature graphic) 1024×500 | `store/play/feature-graphic-he.png` (ולדף האנגלי `feature-graphic-en.png`) |
| צילומי מסך לטלפון, 4 | `store/play/screenshots/YDeck-01.png` … `YDeck-04.png` (1080×1920) |
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

1. **קבוצת גוגל** ב־groups.google.com: שם `yonidev-testers`. בהגדרות הקבוצה: מי יכול להצטרף ← **Anyone on the web can join** ("כל אחד באינטרנט"), ומי יכול לראות את הקבוצה ← **Anyone on the web**. בלי זה הקישור באתר (`/beta`) מראה לבודקים שגיאת גישה.
2. Test and release ← Testing ← **Closed testing** ← Create track (או Alpha) ← **Testers**: מוסיפים את `yonidev-testers@googlegroups.com`.
3. **Create new release** ← מעלים את `dist/YDeck-0.9.7.aab`.
   - Release name: `0.9.7`
   - Release notes (עברית):
     ```
     <iw-IL>
     הזיכרון של הפרויקטים, בטלפון: איפה כל פרויקט עומד, מה Claude עשה ומה הבא. מתחבר ל־YDeck במחשב.
     </iw-IL>
     ```
4. Next ← Save ← **Send for review**. הבדיקה הראשונה לוקחת בדרך כלל כמה ימים.
5. אחרי האישור: שולחים לבודקים את **https://ydeck-app.pages.dev/beta**. שם שני הצעדים: הצטרפות לקבוצה, ואז קישור ההצטרפות (`play.google.com/apps/testing/com.yonidev.ydeck`).
6. אחרי 14 יום עם 12 בודקים לפחות: Dashboard ← **Apply for production**.

## 4. דף החנות (Grow users ← Store presence ← Main store listing)

מעודכן ל־0.9: המיתוג החדש (כהה, הטבעת עם הנקודה הכתומה, "שום דבר לא נשאר פתוח"), והאפליקציה היא עכשיו הזיכרון של הפרויקטים, מחוברת ל־YDeck במחשב. **הטקסט הישן (עורך בטלפון, "בלי שרת", 161 שירותים) כבר לא נכון, וצריך להחליף אותו.**

**App name (30):** `YDeck: זיכרון הפרויקטים`

**Short description (80):**
```
הזיכרון של הפרויקטים שלך, בכיס: איפה כל פרויקט עומד ומה הבא.
```

**Full description:**
```
שום דבר לא נשאר פתוח.

הסוכנים כותבים את הקוד, ו־YDeck זוכר. בטלפון, YDeck הוא הזיכרון של הפרויקטים שלך: איפה כל פרויקט עומד, מה הסשן האחרון של הסוכן עשה, ומה הצעד הבא.

מה יש בטלפון
• הבא: הצעד הבא של כל פרויקט, ונקודה כתומה כשמשהו עוד פתוח.
• הזיכרון: מה קורה עכשיו, מה Claude או Codex עשו בסשן האחרון, תקציר, החלטות וגרסה אחרונה.
• כותבים מהדרך: החלטה או צעד הבא שעלו לך מגיעים ל־YDeck במחשב.
• קישורים: כל קונסולה של הפרויקט (Firebase, Play Console, Vercel, GitHub) נפתחת בחשבון הנכון.
• ווידג'ט למסך הבית.

איך זה עובד
YDeck רץ במחשב (Windows, חינם וקוד פתוח) ועוקב אחרי הפרויקטים והסוכנים. בטלפון סורקים קוד אחד מההגדרות שלו, ומאז הזיכרון מגיע לטלפון. אפשר גם לנסות קודם עם פרויקט לדוגמה, בלי מחשב.

פרטיות
בלי חשבון, בלי פרסומות ובלי אנליטיקס. בין הטלפון למחשב עובר רק מידע מוצפן מקצה לקצה, במפתח שנמצא רק בקוד שסרקת. גם אנחנו לא יכולים לקרוא אותו.
```

**Graphics:** App icon ← `icon-512.png` · Feature graphic ← `feature-graphic-he.png` · Phone screenshots: **מוחקים את 8 הישנים** ומעלים את 4 החדשים מ־`store/play/screenshots/` לפי הסדר (נבנים מ־`store/play/source/store.html`, עם מסכים אמיתיים מהאפליקציה ב־`source/screens-0.9/`).

**Store settings:** App category: **Productivity** · Contact: `yonidev0101@gmail.com` · Website: `https://ydeck-app.pages.dev`

**דף באנגלית (English):**
- Name: `YDeck: Project memory`
- Short: `Your projects' memory, in your pocket: where each one stands and what's next.`
- Full:
```
Nothing left open.

Your agents write the code; YDeck remembers. On your phone, YDeck is the memory of your projects: where each one stands, what the agent's last session did, and what comes next.

On the phone
• Next: each project's next step, with an amber dot while something is still open.
• Memory: what's happening now, what Claude or Codex did in the last session, the summary, decisions and latest release.
• Write on the go: a decision or next step you think of reaches YDeck on your computer.
• Links: every console of the project (Firebase, Play Console, Vercel, GitHub) opens in the right account.
• A home-screen widget.

How it works
YDeck runs on your computer (Windows, free and open source) and keeps track of your projects and agents. Scan one code from its settings and the memory reaches your phone. You can also try a sample project first, without a computer.

Privacy
No account, no ads, no analytics. Only end-to-end encrypted data passes between phone and computer, with a key that lives only in the code you scanned. Not even we can read it.

The app is in Hebrew.
```
- Feature graphic ← `feature-graphic-en.png`. אותם צילומי מסך.

## 5. תוכן האפליקציה (Policy and programs ← App content)

מה השתנה מ־0.8, ומה לעדכן:

| שאלה | תשובה | שינוי? |
|---|---|---|
| Privacy policy | `https://ydeck-app.pages.dev/privacy` (כבר מעודכנת לממסר ולמצלמה) | לא |
| App access | **All or some functionality is restricted** ← Add instructions: הוראות למטה | **כן** |
| Ads | **No, my app does not contain ads** | לא |
| Content rating | IARC, **All Other App Types**, הכול **No**. ההערות שנכתבות בטלפון הולכות רק למחשב של אותו משתמש, אז זו לא תקשורת בין משתמשים | לא |
| Target audience | **18 and over** | לא |
| Data safety | הטבלה למטה | **לבדוק** |

### App access: ההוראות לבודקים
האפליקציה נפתחת בלי התחברות, אבל ההצמדה למחשב דורשת את YDeck ל־Windows. כדי שהבודק לא ייתקע מול מסך "חיבור למחשב", נותנים הוראות:
- Name: `Sample project (no login)`
- Username / Password: משאירים ריק (או `none`)
- Any other information:
```
No account or login. On the first screen tap "לנסות עם פרויקט לדוגמה" (Try a sample project): it loads the sample project Nova with its full memory, links and widget, everything a paired phone shows.
Pairing with a computer needs the free YDeck for Windows (https://ydeck-app.pages.dev/download): Settings → Phone shows a QR code to scan.
```

### Data safety
הממסר לא משנה את ההצהרה: לפי Google, מידע שיוצא מהטלפון **מוצפן מקצה לקצה, כך שרק השולח והנמען יכולים לקרוא אותו (גם לא המפתח)**, לא נחשב "נאסף" ולא מצהירים עליו. זה בדיוק הממסר: AES-256-GCM, והמפתח רק ב־QR.
- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **No** (אין אצלנו מידע: הכול בטלפון ונמחק בהסרה. בממסר יש רק תיבה מוצפנת)

סוג המידע היחיד: **App activity → Other user-generated content** (שמות אתרים לאייקונים)
- Collected: **Yes** · Shared: **Yes** (Google ו־DuckDuckGo מקבלים את שם האתר כדי להחזיר אייקון)
- Processed ephemerally: **Yes**
- Required (users can't turn it off)
- Purpose: **App functionality**

כל שאר הסוגים: **לא**. כולל **Photos and videos**: הסורק עובד בטלפון בלבד והתמונה לא נשמרת ולא יוצאת. ההרשאה CAMERA בקובץ ההתקנה מוצדקת בזה, ואין לה טופס הצהרה נפרד.

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
