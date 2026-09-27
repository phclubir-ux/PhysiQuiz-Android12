# PhysiQuiz Android PRO 2.3.0 — WordPress Controlled

این نسخه فقط بر مبنای دو کلاس داخل خود افزونه PhysiQuiz طراحی شده است:

- `PhysiQuiz_Mobile_App`
- `PhysiQuiz_Mobile_API`

## منبع کنترل

### تنظیمات عمومی Native App
`GET /wp-json/physiquiz/v1/app-config`

از پنل PhysiQuiz کنترل می‌شود و شامل:
- enabled
- app_name
- accent_color
- background_color
- banner_url
- cards
- maintenance_mode
- maintenance_message
- support_url
- allow_external_links
- block_screenshots
- force_fullscreen
- latest_version_name
- minimum_version_code
- update_message
- update_url

### API کاربر
همان endpointهای موجود در `PhysiQuiz_Mobile_API`:
- POST `/mobile/login`
- POST `/mobile/logout`
- GET `/mobile/me`
- PATCH/PUT `/mobile/profile`
- GET `/mobile/home`
- GET `/mobile/exams`
- GET `/mobile/exams/{id}`
- GET `/mobile/results`
- GET `/mobile/files`
- POST `/mobile/forgot-password`

## قانون مهم
این پروژه نباید endpoint جدیدی برای جایگزینی API فعلی بسازد. اتصال اصلی فقط همین PhysiQuiz است.

## GitHub
کل محتویات این پروژه را در Repository قبلی جایگزین کن و Commit/Push کن.
