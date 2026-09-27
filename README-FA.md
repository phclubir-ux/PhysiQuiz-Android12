# PhysiQuiz Android Native 2.0.0

این نسخه دیگر WebView نیست. ورود، خانه، آزمون‌ها، سؤال‌ها، ثبت پاسخ، نتیجه، فایل‌ها و پروفایل با رابط Native اندروید نمایش داده می‌شوند و وردپرس فقط REST API و داده را تأمین می‌کند.

## مشخصات
- Android 10+ (`minSdk 29`)
- Java 17
- `compileSdk 35` / `targetSdk 35`
- بدون کتابخانه Runtime خارجی
- ورود با Bearer Token اختصاصی PhysiQuiz
- ارسال همزمان `Authorization` و `X-PhysiQuiz-Auth` برای سازگاری با هاست‌های اشتراکی
- پشتیبانی از سؤال تک‌گزینه‌ای، چندگزینه‌ای، صحیح/غلط، کوتاه، عددی و تشریحی
- ذخیره پاسخ در REST API
- ثبت نهایی و نمایش نتیجه داخل اپ
- نمایش PDF با `PdfRenderer`
- جلوگیری از Screenshot در آزمون Anti-Cheat
- رابط RTL فارسی

## آدرس سایت
نسخه حاضر به‌صورت پیش‌فرض برای این سایت تنظیم شده است:

`https://physicstory.ir`

کاربر در صفحه ورود می‌تواند آدرس سایت را نیز تغییر دهد. فقط HTTPS پذیرفته می‌شود.

## افزونه لازم
قبل از اجرای این اپ باید افزونه `PhysiQuiz 1.17.0` موجود در همین Bundle روی وردپرس نصب/جایگزین شود. نسخه 1.16.0 APIهای Native Login/Home/Exams/Results/Files را ندارد.

## Build
پروژه با AGP 8.7.3 و Gradle 8.9 سازگار شده است. فایل `PhysiQuiz_Android_APK_Builder.ipynb` در بسته اصلی می‌تواند در Google Colab پروژه را Build کند.
