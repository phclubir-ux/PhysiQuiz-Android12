# ساخت APK فیزیکوییز فقط با مرورگر GitHub

این پروژه از قبل برای GitHub Actions آماده شده است. نیازی به CMD، Colab یا Android Studio ندارید.

## مراحل

1. وارد GitHub شوید و یک Repository جدید بسازید، مثلاً `physiquiz-android`.
2. Repository می‌تواند Private باشد.
3. فایل‌ها و پوشه‌های داخل این پروژه را با گزینه **Add file → Upload files** آپلود کنید و **Commit changes** را بزنید.
4. وارد تب **Actions** شوید.
5. Workflow با نام **Build PhysiQuiz APK** را انتخاب کنید.
6. دکمه **Run workflow** را بزنید.
7. بعد از سبز شدن Build، همان Run را باز کنید.
8. در بخش **Artifacts** روی **PhysiQuiz-v2.0.0-APK** بزنید.
9. فایل Artifact را دانلود و باز کنید؛ داخل آن `PhysiQuiz-v2.0.0-debug.apk` قرار دارد.

## مشخصات پروژه

- App ID: `com.physiquiz.student`
- Version: `2.0.0`
- Min SDK: 29 (Android 10)
- Target/Compile SDK: 35
- Java: 17
- Gradle: 8.9
- Android Gradle Plugin: 8.7.3
