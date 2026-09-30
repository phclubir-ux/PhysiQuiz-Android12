package com.physiquiz.student;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.util.LruCache;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "physiquiz_native";
    private static final String PREF_SITE = "site_url";
    private static final String PREF_AUTH = "auth_token";
    private static final String PREF_CONFIG_JSON = "wp_app_config_json";
    private static final String PREF_CONFIG_TIME = "wp_app_config_time";
    private static final String PREF_DARK_MODE = "dark_mode";
    private static final String PREF_ATTEMPT_ID = "resume_attempt_id";
    private static final String PREF_ATTEMPT_TOKEN = "resume_attempt_token";
    private static final String PREF_ATTEMPT_EXAM_ID = "resume_attempt_exam_id";

    private SharedPreferences prefs;
    private ApiClient api;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppRemoteConfig remoteConfig;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private AppConfig config = new AppConfig();
    private int accent = Color.rgb(255, 122, 0);
    private int background = Color.rgb(246, 248, 252);

    private Typeface fontRegular = Typeface.DEFAULT;
    private Typeface fontBold = Typeface.DEFAULT_BOLD;
    private final LruCache<String, Bitmap> imageCache = new LruCache<>(20);
    private final java.util.Map<String, LinearLayout> navItems = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> navIcons = new java.util.HashMap<>();
    private final java.util.Map<String, TextView> navLabels = new java.util.HashMap<>();
    private final java.util.Map<String, View> navIconBadges = new java.util.HashMap<>();

    // تم تیره دیگر دستی نیست: از روشنایی پس‌زمینه‌ای که وردپرس فرستاده به‌صورت خودکار استخراج
    // می‌شود — پس پریست «نارنجی روی مشکی» در پنل، بی‌درنگ اپ را به حالت نئون لاکچری می‌برد.
    private boolean darkMode = false;
    private int cBg, cSurface, cBorder, cAccentBorder, cAccentTint, cTextPrimary, cTextSecondary, cTextStrong, cHint, cPlaceholder;

    private FrameLayout content;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private LabGridBackground labGrid;
    private TextView topTitle;
    private ProgressBar loading;

    private long currentAttemptId = 0;
    private String currentAttemptToken = "";
    private JSONArray currentQuestions = new JSONArray();
    private JSONObject currentAnswers = new JSONObject();
    private JSONObject currentExam = new JSONObject();
    private int currentQuestionIndex = 0;
    private View answerInputView;
    private boolean currentAntiCheat = false;
    private long remainingSeconds = 0;
    private TextView timerText;
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadFonts();
        computeThemeTokens();
        getWindow().setStatusBarColor(cSurface);
        setLightStatusBarIcons(!darkMode);
        String bundled = getString(R.string.default_site_url).trim();
        String site = prefs.getString(PREF_SITE, bundled);
        String token = prefs.getString(PREF_AUTH, "");
        api = new ApiClient(site, token);
        remoteConfig = new AppRemoteConfig(site);

        showBootSplash();
        remoteConfig.load(result -> ui.post(() -> handleRemoteConfig(result, true)));
    }

    private void handleRemoteConfig(AppRemoteConfig.Result result, boolean firstBoot) {
        if (result.success) {
            config = AppConfig.fromJson(result.json);
            prefs.edit().putString(PREF_CONFIG_JSON, result.json.toString()).putLong(PREF_CONFIG_TIME, System.currentTimeMillis()).apply();
            applyTheme();
            bootWithConfig();
            return;
        }
        String cached = prefs.getString(PREF_CONFIG_JSON, "");
        if (!cached.isEmpty()) {
            try {
                config = AppConfig.fromJson(new JSONObject(cached));
                applyTheme();
                bootWithConfig();
                toast("تنظیمات جدید وردپرس دریافت نشد؛ آخرین تنظیمات ذخیره‌شده نمایش داده شد.");
                return;
            } catch (Exception ignored) { }
        }
        showConfigError(result.error, result.status);
    }

    private void setFullScreenContent(View box) {
        FrameLayout root = new FrameLayout(this);
        if (darkMode) {
            LabGridBackground grid = new LabGridBackground(this);
            grid.setTint(accent);
            root.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.setBackgroundColor(cBg);
        }
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void showConfigError(String error, int status) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView h = text("اتصال به تنظیمات PhysiQuiz برقرار نشد", 22, cTextPrimary, true);
        h.setGravity(Gravity.CENTER); box.addView(h, matchWrap());
        String detail = "اپلیکیشن نتوانست تنظیمات را از وردپرس دریافت کند." + (status > 0 ? " (HTTP " + status + ")" : "");
        TextView p = bodyText(detail); p.setGravity(Gravity.CENTER); p.setPadding(0,dp(12),0,dp(20)); box.addView(p, matchWrap());
        Button retry = primaryButton("تلاش دوباره"); retry.setOnClickListener(v -> retryBoot()); box.addView(retry, new LinearLayout.LayoutParams(dp(220), dp(58)));
        setFullScreenContent(box);
    }

    /** اسپلش متحرک: لوگو با محو ظاهر شدن + خط برند که از وسط به دو طرف کشیده می‌شود. */
    private void showBootSplash() {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView logo = text("PHYSIQUIZ", 32, accent, true);
        logo.setGravity(Gravity.CENTER);
        logo.setLetterSpacing(0.22f);
        logo.setAlpha(0f);
        box.addView(logo, matchWrap());

        FrameLayout lineWrap = new FrameLayout(this);
        View line = new View(this);
        line.setBackground(roundRect(accent, 4, Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(0), dp(4));
        llp.gravity = Gravity.CENTER;
        lineWrap.addView(line, llp);
        LinearLayout.LayoutParams lw = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        lw.topMargin = dp(16);
        box.addView(lineWrap, lw);

        TextView title = text(config.appName == null || config.appName.trim().isEmpty() ? "فیزیکوییز" : config.appName, 19, cTextPrimary, true);
        title.setGravity(Gravity.CENTER); title.setPadding(0, dp(14), 0, dp(6));
        title.setAlpha(0f);
        box.addView(title, matchWrap());
        TextView sub = text("سامانه هوشمند آزمون و یادگیری", 13, cTextSecondary, false);
        sub.setGravity(Gravity.CENTER); sub.setAlpha(0f);
        box.addView(sub, matchWrap());
        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42)); pp.topMargin = dp(30);
        pb.setAlpha(0f);
        box.addView(pb, pp);
        setFullScreenContent(box);

        logo.animate().alpha(1f).scaleX(1.05f).scaleY(1.05f).setDuration(520).setInterpolator(new DecelerateInterpolator()).start();
        title.animate().alpha(1f).setDuration(420).setStartDelay(220).start();
        sub.animate().alpha(1f).setDuration(420).setStartDelay(340).start();
        pb.animate().alpha(1f).setDuration(420).setStartDelay(460).start();
        ValueAnimator la = ValueAnimator.ofInt(0, dp(130));
        la.setDuration(720); la.setStartDelay(160);
        la.setInterpolator(new DecelerateInterpolator());
        la.addUpdateListener(a -> { llp.width = (int) a.getAnimatedValue(); line.setLayoutParams(llp); });
        la.start();
    }

    private void applyTheme() {
        try { accent = Color.parseColor(config.accentColor); } catch (Exception ignored) {}
        try { background = Color.parseColor(config.backgroundColor); } catch (Exception ignored) {}
        darkMode = luminance(background) < 0.5f;
        computeThemeTokens();
        getWindow().setStatusBarColor(darkMode ? cBg : cSurface);
        setLightStatusBarIcons(!darkMode);
        if (config.forceFullscreen) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (config.blockScreenshots) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private float luminance(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    private void bootWithConfig() {
        if (config.loaded && !config.enabled) {
            showBlockingScreen("اپلیکیشن غیرفعال است", "دسترسی به این اپلیکیشن موقتاً از سمت مدیر غیرفعال شده است.", null);
            return;
        }
        if (config.loaded && config.maintenanceMode) {
            showBlockingScreen("در حال بروزرسانی", config.maintenanceMessage, this::retryBoot);
            return;
        }
        if (config.loaded && config.minimumVersionCode > BuildConfig.VERSION_CODE) {
            String msg = config.updateMessage != null && !config.updateMessage.trim().isEmpty()
                    ? config.updateMessage
                    : "نسخه جدیدتری از اپلیکیشن منتشر شده است. لطفاً بروزرسانی کنید.";
            showBlockingScreen("بروزرسانی لازم است", msg, config.updateUrl == null || config.updateUrl.trim().isEmpty()
                    ? null
                    : () -> openExternal(config.updateUrl));
            return;
        }
        buildShell();
        String token = prefs.getString(PREF_AUTH, "");
        if (token.isEmpty()) showLogin(); else showHome();
    }

    private void retryBoot() {
        showBootSplash();
        remoteConfig.load(result -> ui.post(() -> handleRemoteConfig(result, false)));
    }

    private void showBlockingScreen(String title, String message, Runnable onAction) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView h = text(title, 22, cTextPrimary, true);
        h.setGravity(Gravity.CENTER);
        box.addView(h, matchWrap());
        TextView p = bodyText(message == null ? "" : message);
        p.setGravity(Gravity.CENTER);
        p.setPadding(0, dp(10), 0, dp(20));
        box.addView(p, matchWrap());
        if (onAction != null) {
            Button b = primaryButton(config.updateUrl != null && !config.updateUrl.isEmpty() && "بروزرسانی لازم است".equals(title) ? "دریافت نسخه جدید" : "تلاش دوباره");
            b.setOnClickListener(v -> onAction.run());
            box.addView(b, new LinearLayout.LayoutParams(dp(220), dp(58)));
        }
        if (config.supportUrl != null && !config.supportUrl.trim().isEmpty()) {
            Button support = secondaryButton("تماس با پشتیبانی");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), dp(50));
            lp.topMargin = dp(10);
            support.setOnClickListener(v -> openExternal(config.supportUrl));
            box.addView(support, lp);
        }
        setFullScreenContent(box);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(cBg);

        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        topBar.setBackgroundColor(darkMode ? cSurface : accent);
        topBar.setElevation(dp(2));
        topTitle = text(config.appName + "  •", 19, darkMode ? cTextPrimary : Color.WHITE, true);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button refresh = smallButton("🔄");
        refresh.setBackground(withRipple(null, 20, darkMode ? 0x1F000000 : 0x33FFFFFF));
        refresh.setTextSize(16);
        refresh.setOnClickListener(v -> refreshCurrent());
        topBar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        FrameLayout contentWrap = new FrameLayout(this);
        labGrid = new LabGridBackground(this);
        labGrid.setTint(darkMode ? accent : 0);
        contentWrap.addView(labGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentWrap.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(contentWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(6), dp(8), dp(6), dp(10));
        bottomBar.setBackgroundColor(cSurface);
        bottomBar.setElevation(dp(6));
        addNav("🏠", "خانه", this::showHome);
        addNav("📝", "آزمون‌ها", this::showExams);
        addNav("📊", "نتایج", this::showResults);
        addNav("📁", "فایل‌ها", this::showFiles);
        addNav("👤", "پروفایل", this::showProfile);
        root.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout overlay = new FrameLayout(this);
        overlay.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(50), dp(50));
        lp.gravity = Gravity.CENTER;
        overlay.addView(loading, lp);
        setContentView(overlay);
    }

    private void addNav(String emoji, String label, Runnable action) {
        LinearLayout col = column();
        col.setGravity(Gravity.CENTER);
        TextView iconView = text(emoji, 21, cTextSecondary, false);
        iconView.setGravity(Gravity.CENTER);
        iconView.setPadding(dp(5), dp(5), dp(5), dp(5));
        iconView.setBackground(roundRect(Color.TRANSPARENT, 16, Color.TRANSPARENT, 0));
        col.addView(iconView, new LinearLayout.LayoutParams(dp(44), dp(34)));
        TextView labelView = text(label, 11, cTextSecondary, false);
        labelView.setGravity(Gravity.CENTER);
        col.addView(labelView, matchWrapMargin(4, 0));
        col.setOnClickListener(v -> { addPressScale(col, 0.92f); action.run(); });
        col.setPadding(dp(2), dp(6), dp(2), dp(6));
        addRippleFeedback(col, 16);
        bottomBar.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        navItems.put(label, col);
        navIcons.put(label, iconView);
        navLabels.put(label, labelView);
        navIconBadges.put(label, iconView);
    }

    private void updateActiveNav(String title) {
        for (String key : navIcons.keySet()) {
            boolean active = key.equals(title);
            int color = active ? accent : cTextSecondary;
            TextView lbl = navLabels.get(key);
            lbl.setTextColor(color);
            lbl.setTypeface(active ? fontBold : fontRegular);
            View badge = navIconBadges.get(key);
            if (badge != null) badge.setBackground(roundRect(active ? adjustAlpha(accent, darkMode ? 0x33 : 0x26) : Color.TRANSPARENT, 16, Color.TRANSPARENT, 0));
        }
    }

    private void refreshCurrent() {
        remoteConfig.load(result -> ui.post(() -> {
            if (result.success) {
                config = AppConfig.fromJson(result.json);
                prefs.edit().putString(PREF_CONFIG_JSON, result.json.toString()).putLong(PREF_CONFIG_TIME, System.currentTimeMillis()).apply();
                applyTheme();
                toast("تنظیمات جدید از وردپرس اعمال شد.");
            } else {
                toast("دریافت تنظیمات وردپرس ناموفق بود.");
            }
            refreshSectionOnly();
        }));
    }

    private void refreshSectionOnly() {
        CharSequence t = topTitle.getText();
        if ("آزمون‌ها".contentEquals(t)) showExams();
        else if ("نتایج".contentEquals(t)) showResults();
        else if ("فایل‌ها".contentEquals(t)) showFiles();
        else if ("پروفایل".contentEquals(t)) showProfile();
        else showHome();
    }

    private void showLogin() {
        stopTimer();
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (config.blockScreenshots) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        page.setPadding(dp(22), dp(30), dp(22), dp(30));
        page.setBackgroundColor(darkMode ? Color.TRANSPARENT : cBg);

        // کارت گرادیان برند (لاکچری)
        LinearLayout brand = column();
        brand.setPadding(dp(24), dp(28), dp(24), dp(28));
        GradientDrawable brandBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{accent, darken(accent, 0.62f)});
        brandBg.setCornerRadius(dp(28));
        brand.setBackground(brandBg);
        brand.setElevation(dp(5));
        applyGlow(brand, 5);
        TextView badge = text("PHYSIQUIZ", 26, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setLetterSpacing(0.18f);
        brand.addView(badge, matchWrap());
        TextView brandSub = text("آموزش • آزمون • پیشرفت", 13, adjustAlpha(Color.WHITE, 0xE6), false);
        brandSub.setGravity(Gravity.CENTER); brandSub.setPadding(0, dp(9), 0, 0);
        brand.addView(brandSub, matchWrap());
        page.addView(brand, matchWrapMargin(0, 20));

        TextView h = text("خوش آمدی 👋", 28, cTextPrimary, true);
        page.addView(h, matchWrap());
        TextView p = text("برای ورود به داشبورد آموزشی و آزمون‌های خود، اطلاعات حسابت را وارد کن.", 14, cTextSecondary, false);
        p.setPadding(0, dp(8), 0, dp(18)); page.addView(p, matchWrap());

        LinearLayout form = card(); form.setPadding(dp(18), dp(20), dp(18), dp(20));
        TextView ulabel = text("نام کاربری یا ایمیل", 13, cTextStrong, true); form.addView(ulabel, matchWrap());
        EditText username = input("مثلاً student@example.com"); username.setSingleLine(true);
        attachFocusGlow(username);
        form.addView(username, matchWrapMargin(0, 8));
        TextView plabel = text("رمز عبور", 13, cTextStrong, true); plabel.setPadding(0, dp(14), 0, 0); form.addView(plabel, matchWrap());
        EditText password = input("رمز عبور خود را وارد کنید");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);
        password.setPadding(dp(14), dp(11), dp(64), dp(11));
        attachFocusGlow(password);
        FrameLayout passWrap = new FrameLayout(this);
        passWrap.addView(password, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button eye = new Button(this);
        eye.setText("نمایش");
        eye.setAllCaps(false);
        eye.setTextSize(12);
        eye.setTypeface(fontBold);
        eye.setTextColor(accent);
        eye.setBackground(withRipple(null, 12, adjustAlpha(accent, 0x26)));
        eye.setPadding(dp(10), 0, dp(10), 0);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT);
        elp.leftMargin = dp(6);
        passWrap.addView(eye, elp);
        final boolean[] shown = {false};
        eye.setOnClickListener(v -> {
            shown[0] = !shown[0];
            int sel = password.getSelectionEnd();
            password.setInputType(InputType.TYPE_CLASS_TEXT | (shown[0] ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            password.setTypeface(fontRegular);
            if (sel >= 0) password.setSelection(Math.min(sel, password.length()));
            eye.setText(shown[0] ? "پنهان" : "نمایش");
        });
        form.addView(passWrap, matchWrapMargin(0, 8));
        TextView forgot = text("رمز عبور را فراموش کرده‌ام", 13, accent, true);
        forgot.setGravity(Gravity.RIGHT); forgot.setPadding(0, dp(10), 0, dp(10));
        forgot.setOnClickListener(v -> showForgotPasswordDialog(username.getText().toString().trim()));
        form.addView(forgot, matchWrap());
        Button login = primaryButton("ورود به حساب ←");
        form.addView(login, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        page.addView(form, matchWrapMargin(0, 18));
        TextView foot = text("حساب کاربری شما مستقیماً از " + config.appName + " مدیریت می‌شود.", 12, cTextSecondary, false);
        foot.setGravity(Gravity.CENTER); page.addView(foot, matchWrap());

        login.setOnClickListener(v -> {
            if(username.getText().toString().trim().isEmpty()||password.getText().toString().isEmpty()){toast("نام کاربری و رمز عبور را وارد کنید.");return;}
            login.setEnabled(false); login.setText("در حال ورود...");
            runApi(() -> { JSONObject body=new JSONObject(); body.put("username",username.getText().toString().trim()); body.put("password",password.getText().toString()); body.put("device","Android "+android.os.Build.VERSION.RELEASE+" / "+android.os.Build.MODEL); return api.post("/wp-json/physiquiz/v1/mobile/login",body); }, json -> {
                login.setEnabled(true); login.setText("ورود به حساب ←");
                String token=json.optString("token",""); if(token.isEmpty()){toast("توکن ورود از سرور دریافت نشد.");return;}
                prefs.edit().putString(PREF_SITE,api.getBaseUrl()).putString(PREF_AUTH,token).apply(); api.setAuthToken(token); topBar.setVisibility(View.VISIBLE); bottomBar.setVisibility(View.VISIBLE); showHome();
            });
        });
        scroll.addView(page); setScreen(scroll);
    }

    /** اینپوت هنگام فوکوس حاشیه نارنجی پررنگ می‌گیرد (مثل فرم ورود سایت). */
    private void attachFocusGlow(EditText e) {
        e.setOnFocusChangeListener((v, has) -> e.setBackground(roundRect(cSurface, 14, has ? accent : cBorder, has ? 2 : 1)));
    }

    private void showForgotPasswordDialog(String prefill) {
        EditText field = input("نام کاربری یا ایمیل");
        if (prefill != null && !prefill.isEmpty()) field.setText(prefill);
        field.setSingleLine(true);
        LinearLayout wrap = column();
        wrap.setPadding(dp(20), dp(10), dp(20), dp(0));
        wrap.addView(field, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("بازیابی رمز عبور")
                .setMessage("نام کاربری یا ایمیل حسابت را وارد کن؛ لینک تعیین رمز جدید برایت ایمیل می‌شود.")
                .setView(wrap)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ارسال لینک", (d, w) -> {
                    String loginValue = field.getText().toString().trim();
                    if (loginValue.isEmpty()) { toast("نام کاربری یا ایمیل را وارد کن."); return; }
                    runApi(() -> {
                        JSONObject body = new JSONObject();
                        body.put("login", loginValue);
                        return api.post("/wp-json/physiquiz/v1/mobile/forgot-password", body);
                    }, json -> showMessage("بازیابی رمز عبور", json.optString("message", "اگر این حساب وجود داشته باشد، ایمیل بازیابی ارسال شد.")));
                })
                .show();
    }

    private void showHome() {
        prepareSection("خانه");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/home"), json -> {
            LinearLayout page = pageColumn();
            JSONObject user = json.optJSONObject("user");
            JSONObject stats = json.optJSONObject("stats");
            String name = user == null ? "دانش‌آموز" : user.optString("display_name", "دانش‌آموز");

            if (config.bannerUrl != null && !config.bannerUrl.trim().isEmpty()) {
                page.addView(bannerImage(config.bannerUrl), matchWrapMargin(0, 14));
            }

            page.addView(hero("سلام " + name + " 👋", "امروز یک قدم دیگر به تسلط بر فیزیک نزدیک‌تر شو.", name), matchWrapMargin(0, 14));

            if (config.cardRows != null && config.cardRows.length() > 0) {
                renderCarouselsFor(page, "home");
            } else if (config.cards != null && config.cards.length() > 0) {
                for (int i = 0; i < config.cards.length(); i++) {
                    addContentCard(page, config.cards.optJSONObject(i));
                }
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(statCard("تلاش‌ها", stats == null ? 0 : stats.optInt("attempts"), ""), weighted());
            row.addView(statCard("بهترین", stats == null ? 0 : stats.optDouble("best_percent"), "%"), weightedMargin(8));
            page.addView(row, matchWrapMargin(0, 8));
            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.addView(statCard("سطح", stats == null ? 1 : stats.optInt("level", 1), ""), weighted());
            row2.addView(statCard("XP", stats == null ? 0 : stats.optInt("xp"), ""), weightedMargin(8));
            page.addView(row2, matchWrapMargin(0, 18));

            if (stats != null && stats.optInt("attempts", 0) > 0) {
                page.addView(progressCard("نرخ قبولی", stats.optInt("success_rate", 0)), matchWrapMargin(0, 18));
            }

            page.addView(sectionTitle("آزمون‌های فعال"), matchWrap());
            JSONArray active = json.optJSONArray("active_exams");
            if (active == null || active.length() == 0) page.addView(empty("در حال حاضر آزمون فعالی نداری."), matchWrapMargin(0, 10));
            else for (int i = 0; i < active.length(); i++) addExamCard(page, active.optJSONObject(i));

            page.addView(sectionTitle("آخرین نتایج"), matchWrapMargin(0, 8));
            JSONArray results = json.optJSONArray("recent_results");
            if (results == null || results.length() == 0) page.addView(empty("هنوز نتیجه‌ای ثبت نشده است."), matchWrapMargin(0, 10));
            else for (int i = 0; i < results.length(); i++) addResultCard(page, results.optJSONObject(i));

            Button files = secondaryButton("مشاهده فایل‌ها و منابع");
            files.setOnClickListener(v -> showFiles());
            page.addView(files, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
            setScrollable(page);
        });
    }

    private void renderCarouselsFor(LinearLayout page, String pageKey) {
        if (config.cardRows == null) return;
        for (int i = 0; i < config.cardRows.length(); i++) {
            JSONObject row = config.cardRows.optJSONObject(i);
            if (row == null) continue;
            JSONArray pages = row.optJSONArray("pages");
            boolean matches;
            if (pages != null) {
                matches = false;
                for (int p = 0; p < pages.length(); p++) {
                    if (pageKey.equals(pages.optString(p))) { matches = true; break; }
                }
            } else {
                matches = "home".equals(pageKey);
            }
            if (matches) addCarouselRow(page, row);
        }
    }

    private void addCarouselRow(LinearLayout page, JSONObject rowJson) {
        if (rowJson == null) return;
        JSONArray cardsArr = rowJson.optJSONArray("cards");
        if (cardsArr == null || cardsArr.length() == 0) return;
        String title = rowJson.optString("title", "");
        if (!title.isEmpty()) page.addView(text(title, 16, cTextPrimary, true), matchWrapMargin(0, 10));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(2), 0, dp(2), dp(4));
        boolean any = false;
        for (int i = 0; i < cardsArr.length(); i++) {
            View card = buildCarouselCard(cardsArr.optJSONObject(i));
            if (card == null) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(10);
            row.addView(card, lp);
            any = true;
        }
        if (!any) return;
        scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, matchWrapMargin(0, 14));
    }

    private View buildCarouselCard(JSONObject cardJson) {
        if (cardJson == null) return null;
        String title = cardJson.optString("title", "");
        String imageUrl = cardJson.optString("image", "");
        String cardText = cardJson.optString("text", "");
        String link = cardJson.optString("link", "");
        String layout = cardJson.optString("layout", "image_top");
        if (title.isEmpty() && imageUrl.isEmpty() && cardText.isEmpty()) return null;

        String bgHex = cardJson.optString("bg_color", "");
        String fgHex = cardJson.optString("text_color", "");
        int fg = cTextPrimary, fgSecondary = cTextStrong;
        try { if (!fgHex.isEmpty()) { fg = Color.parseColor(fgHex); fgSecondary = adjustAlpha(fg, 0xCC); } } catch (Exception ignored) { }
        boolean showImage = !imageUrl.isEmpty() && !"text_only".equals(layout);

        LinearLayout card = card();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        if (!bgHex.isEmpty()) {
            try { card.setBackground(roundRect(Color.parseColor(bgHex), 20, darkMode ? adjustAlpha(accent, 0xB0) : cBorder, darkMode ? 2 : 1)); } catch (Exception ignored) { }
        }
        if (showImage) card.addView(contentCardImage(imageUrl, 90), matchWrapHeightMargin(90, 0, 8));
        if (!title.isEmpty()) {
            TextView t = text(title, 14, fg, true);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(t, matchWrap());
        }
        if (!cardText.isEmpty()) {
            TextView t = text(cardText, 12, fgSecondary, false);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, dp(4), 0, 0);
            card.addView(t, matchWrap());
        }
        if (!link.isEmpty()) {
            card.setOnClickListener(v -> openExternal(link));
            addRippleFeedback(card, 20);
        }
        return card;
    }

    private void addContentCard(LinearLayout page, JSONObject cardJson) {
        if (cardJson == null) return;
        String title = cardJson.optString("title", "");
        String imageUrl = cardJson.optString("image", "");
        String cardText = cardJson.optString("text", "");
        String link = cardJson.optString("link", "");
        String layout = cardJson.optString("layout", "image_top");
        if (title.isEmpty() && imageUrl.isEmpty() && cardText.isEmpty()) return;

        String bgHex = cardJson.optString("bg_color", "");
        String fgHex = cardJson.optString("text_color", "");
        int fg = cTextPrimary, fgSecondary = cTextStrong;
        try { if (!fgHex.isEmpty()) { fg = Color.parseColor(fgHex); fgSecondary = adjustAlpha(fg, 0xCC); } } catch (Exception ignored) { }

        boolean showImage = !imageUrl.isEmpty() && !"text_only".equals(layout);

        LinearLayout card = card();
        if (!bgHex.isEmpty()) {
            try {
                int bg = Color.parseColor(bgHex);
                card.setBackground(roundRect(bg, 20, darkMode ? adjustAlpha(accent, 0xB0) : cBorder, darkMode ? 2 : 1));
            } catch (Exception ignored) { }
        }

        if ("image_side".equals(layout) && showImage) {
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(contentCardImage(imageUrl, 12), new LinearLayout.LayoutParams(dp(64), dp(64)));
            LinearLayout textCol = column();
            if (!title.isEmpty()) textCol.addView(text(title, 15, fg, true), matchWrap());
            if (!cardText.isEmpty()) {
                TextView t = bodyText(cardText);
                t.setTextColor(fgSecondary);
                t.setPadding(0, dp(4), 0, 0);
                textCol.addView(t, matchWrap());
            }
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tlp.rightMargin = dp(12);
            card.addView(textCol, tlp);
        } else {
            if (showImage) card.addView(contentCardImage(imageUrl, 110), matchWrapHeightMargin(110, 0, 10));
            if (!title.isEmpty()) card.addView(text(title, 16, fg, true), matchWrap());
            if (!cardText.isEmpty()) {
                TextView t = bodyText(cardText);
                t.setTextColor(fgSecondary);
                t.setPadding(0, dp(4), 0, 0);
                card.addView(t, matchWrap());
            }
        }
        if (!link.isEmpty()) {
            card.setOnClickListener(v -> openExternal(link));
            addRippleFeedback(card, 20);
        }
        page.addView(card, matchWrapMargin(0, 10));
    }

    private View contentCardImage(String imageUrl, int heightDp) {
        FrameLayout imgWrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cPlaceholder);
        bg.setCornerRadius(dp(heightDp <= 64 ? 10 : 12));
        imgWrap.setBackground(bg);
        imgWrap.setClipToOutline(true);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgWrap.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadImageInto(imageUrl, iv);
        return imgWrap;
    }

    private void showExams() {
        prepareSection("آزمون‌ها");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/exams"), json -> {
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("آزمون‌های من", "فهرست آزمون‌های مجاز بر اساس پایه، گروه، خرید و محدودیت‌های تعریف‌شده در وردپرس."), matchWrapMargin(0, 16));
            renderCarouselsFor(page, "exams");
            JSONArray exams = json.optJSONArray("exams");
            if (exams == null || exams.length() == 0) page.addView(empty("آزمونی برای حساب شما در دسترس نیست."), matchWrap());
            else for (int i = 0; i < exams.length(); i++) addExamCard(page, exams.optJSONObject(i));
            setScrollable(page);
        });
    }

    private void addExamCard(LinearLayout page, JSONObject exam) {
        if (exam == null) return;
        LinearLayout card = card();
        String state = exam.optString("state", "active");
        boolean ended = "ended".equals(state);
        boolean upcoming = "upcoming".equals(state);
        String stateFa = ended ? "پایان‌یافته" : (upcoming ? "به‌زودی" : "آماده شروع");
        int stateColor = ended ? cTextSecondary : (upcoming ? Color.rgb(217, 119, 6) : Color.rgb(22, 163, 74));
        card.addView(cardHeader(ended ? "📕" : (upcoming ? "⏳" : "📝"), ended ? cTextSecondary : accent, exam.optString("title", "آزمون"), pill(stateFa, stateColor)), matchWrap());
        TextView meta = text(exam.optInt("duration_minutes", 0) + " دقیقه  •  حدنصاب " + fmt(exam.optDouble("pass_percent")) + "%", 13, cTextSecondary, false);
        meta.setPadding(0, dp(10), 0, dp(6));
        card.addView(meta, matchWrap());
        if (!exam.optString("available_from", "").isEmpty()) card.addView(text("شروع: " + exam.optString("available_from"), 12, cTextSecondary, false), matchWrap());
        if (!exam.optString("available_until", "").isEmpty()) card.addView(text("پایان: " + exam.optString("available_until"), 12, cTextSecondary, false), matchWrap());
        Button open = ended ? secondaryButton("مشاهده جزئیات") : primaryButton("باز کردن آزمون");
        open.setEnabled(!ended);
        open.setOnClickListener(v -> showExamDetail(exam.optInt("id")));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bp.topMargin = dp(12);
        card.addView(open, bp);
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void saveAttemptPrefs(int examId) {
        prefs.edit()
                .putLong(PREF_ATTEMPT_ID, currentAttemptId)
                .putString(PREF_ATTEMPT_TOKEN, currentAttemptToken)
                .putInt(PREF_ATTEMPT_EXAM_ID, examId)
                .apply();
    }

    private void clearAttemptPrefs() {
        prefs.edit().remove(PREF_ATTEMPT_ID).remove(PREF_ATTEMPT_TOKEN).remove(PREF_ATTEMPT_EXAM_ID).apply();
    }

    private void showExamDetail(int examId) {
        long savedId = prefs.getLong(PREF_ATTEMPT_ID, 0);
        String savedToken = prefs.getString(PREF_ATTEMPT_TOKEN, "");
        int savedExamId = prefs.getInt(PREF_ATTEMPT_EXAM_ID, 0);
        if (savedId > 0 && savedExamId == examId && !savedToken.isEmpty()) {
            resumeAttempt(savedId, savedToken, examId);
            return;
        }
        showExamIntro(examId);
    }

    private void resumeAttempt(long attemptId, String token, int examId) {
        prepareSection("جزئیات آزمون");
        runApi(() -> api.getAttempt("/wp-json/physiquiz/v1/attempts/" + attemptId, token), json -> {
            if (json.optBoolean("submitted", false)) {
                clearAttemptPrefs();
                JSONObject result = json.optJSONObject("result");
                if (result != null) { showFinalResult(result); return; }
                showExamIntro(examId);
                return;
            }
            currentAttemptId = attemptId;
            currentAttemptToken = token;
            currentExam = json.optJSONObject("exam") == null ? new JSONObject() : json.optJSONObject("exam");
            currentQuestions = json.optJSONArray("questions") == null ? new JSONArray() : json.optJSONArray("questions");
            currentAnswers = json.optJSONObject("answers") == null ? new JSONObject() : json.optJSONObject("answers");
            JSONObject state = json.optJSONObject("state");
            currentQuestionIndex = state != null ? state.optInt("current_index", 0) : 0;
            currentAntiCheat = currentExam.optBoolean("anti_cheat", false);
            remainingSeconds = Math.max(0, json.optLong("remaining", currentExam.optInt("duration_minutes", 0) * 60L));
            if (currentAntiCheat || config.blockScreenshots) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            toast("آزمون نیمه‌کاره‌ی قبلی ادامه پیدا کرد.");
            showQuestion();
            startTimer();
        }, error -> {
            clearAttemptPrefs();
            showExamIntro(examId);
        });
    }

    private void showExamIntro(int examId) {
        prepareSection("جزئیات آزمون");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/exams/" + examId), json -> {
            JSONObject exam = json.optJSONObject("exam");
            if (exam == null) { showErrorScreen("اطلاعات آزمون دریافت نشد."); return; }
            LinearLayout page = pageColumn();
            page.addView(sectionIntro(exam.optString("title", "آزمون"), exam.optString("description", "")), matchWrapMargin(0, 12));
            page.addView(infoLine("زمان آزمون", exam.optInt("duration_minutes") + " دقیقه"), matchWrapMargin(0, 6));
            page.addView(infoLine("حدنصاب قبولی", fmt(exam.optDouble("pass_percent")) + "%"), matchWrapMargin(0, 6));
            page.addView(infoLine("نوع آزمون", "pdf".equals(exam.optString("mode")) ? "PDF + پاسخ‌برگ" : "بانک سؤال"), matchWrapMargin(0, 12));

            JSONObject warm = exam.optJSONObject("warmup");
            CheckBox ack = new CheckBox(this);
            ack.setButtonTintList(ColorStateList.valueOf(accent));
            if (warm != null && warm.optBoolean("enabled")) {
                page.addView(sectionTitle(warm.optString("title", "گرم‌کن آزمون")), matchWrapMargin(0, 6));
                String message = warm.optString("message", "");
                if (!message.isEmpty()) page.addView(bodyText(message), matchWrapMargin(0, 8));
                String advisor = warm.optString("advisor", "");
                if (!advisor.isEmpty()) page.addView(note(warm.optString("advisor_title", "توصیه") + "\n" + advisor), matchWrapMargin(0, 8));
                JSONArray rules = warm.optJSONArray("rules");
                if (rules != null && rules.length() > 0) {
                    StringBuilder rb = new StringBuilder();
                    for (int i = 0; i < rules.length(); i++) rb.append("• ").append(rules.optString(i)).append('\n');
                    page.addView(note(rb.toString().trim()), matchWrapMargin(0, 8));
                }
                JSONArray wf = warm.optJSONArray("files");
                if (wf != null) for (int i = 0; i < wf.length(); i++) addFileRow(page, wf.optJSONObject(i));
                String video = warm.optString("video_url", "");
                if (!video.isEmpty()) {
                    Button vb = secondaryButton("باز کردن ویدیوی آماده‌سازی");
                    vb.setOnClickListener(v -> openExternal(video));
                    page.addView(vb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
                }
                if (warm.optBoolean("require_ack")) {
                    ack.setText(warm.optString("ack_text", "قوانین را خواندم و آماده‌ام."));
                    ack.setTextSize(13);
                    page.addView(ack, matchWrapMargin(0, 10));
                } else ack.setChecked(true);
            } else ack.setChecked(true);

            EditText accessCode = input("کد دسترسی (اگر آزمون کد دارد)");
            attachFocusGlow(accessCode);
            if (exam.optBoolean("requires_access_code")) page.addView(accessCode, matchWrapMargin(0, 10));

            Button start = primaryButton("شروع آزمون");
            start.setEnabled("active".equals(exam.optString("state", "active")));
            start.setOnClickListener(v -> {
                if (!ack.isChecked()) { toast("ابتدا تأیید مطالعه قوانین را فعال کنید."); return; }
                startExam(examId, accessCode.getText().toString().trim());
            });
            page.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
            setScrollable(page);
        });
    }

    private void startExam(int examId, String accessCode) {
        runApi(() -> {
            JSONObject body = new JSONObject();
            if (!accessCode.isEmpty()) body.put("access_code", accessCode);
            body.put("surface", "android_native");
            return api.post("/wp-json/physiquiz/v1/exams/" + examId + "/start", body);
        }, json -> {
            currentAttemptId = json.optLong("attempt_id", 0);
            currentAttemptToken = json.optString("token", "");
            currentExam = json.optJSONObject("exam") == null ? new JSONObject() : json.optJSONObject("exam");
            currentQuestions = json.optJSONArray("questions") == null ? new JSONArray() : json.optJSONArray("questions");
            currentAnswers = new JSONObject();
            currentQuestionIndex = 0;
            currentAntiCheat = currentExam.optBoolean("anti_cheat", false);
            remainingSeconds = Math.max(0, currentExam.optInt("duration_minutes", 0) * 60L);
            saveAttemptPrefs(examId);
            if (currentAntiCheat || config.blockScreenshots) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            showQuestion();
            startTimer();
        });
    }

    private void showQuestion() {
        prepareSection("در حال آزمون");
        if (currentQuestions.length() == 0) { showErrorScreen("سؤالی برای این آزمون دریافت نشد."); return; }
        currentQuestionIndex = Math.max(0, Math.min(currentQuestionIndex, currentQuestions.length() - 1));
        JSONObject q = currentQuestions.optJSONObject(currentQuestionIndex);
        if (q == null) return;

        LinearLayout page = pageColumn();
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView pos = text("سؤال " + (currentQuestionIndex + 1) + " از " + currentQuestions.length(), 14, cTextSecondary, true);
        timerText = text(timeText(remainingSeconds), 14, Color.rgb(220, 38, 38), true);
        timerText.setGravity(Gravity.CENTER);
        timerText.setBackground(roundRect(adjustAlpha(Color.rgb(220, 38, 38), 0x1A), 20, Color.TRANSPARENT, 0));
        timerText.setPadding(dp(14), dp(5), dp(14), dp(5));
        status.addView(pos, weighted());
        status.addView(timerText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(status, matchWrapMargin(0, 10));

        // نوار پیشرفت متحرک نارنجی
        FrameLayout track = new FrameLayout(this);
        track.setBackground(roundRect(cPlaceholder, 6, Color.TRANSPARENT, 0));
        View fill = new View(this);
        fill.setBackground(roundRect(accent, 6, Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        track.addView(fill, fillLp);
        page.addView(track, matchWrapMargin(0, 12));
        track.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                track.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int fullWidth = track.getWidth();
                if (fullWidth <= 0) return;
                int target = Math.round(fullWidth * ((currentQuestionIndex + 1) / (float) currentQuestions.length()));
                ValueAnimator a = ValueAnimator.ofInt(0, target);
                a.setDuration(500);
                a.setInterpolator(new DecelerateInterpolator());
                a.addUpdateListener(an -> { fillLp.width = (int) an.getAnimatedValue(); fill.setLayoutParams(fillLp); });
                a.start();
            }
        });

        if ("pdf".equals(currentExam.optString("mode")) && !currentExam.optString("pdf_url", "").isEmpty()) {
            Button pdf = secondaryButton("مشاهده فایل PDF سؤال‌ها");
            pdf.setOnClickListener(v -> openPdf(currentExam.optString("pdf_url")));
            page.addView(pdf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }

        LinearLayout qCard = card();
        String title = q.optString("title", "");
        if (!title.isEmpty()) qCard.addView(text(title, 16, cTextPrimary, true), matchWrapMargin(0, 6));
        TextView contentText = bodyText(htmlToText(q.optString("content", "")));
        qCard.addView(contentText, matchWrapMargin(0, 12));
        qCard.addView(text("امتیاز: " + fmt(q.optDouble("points", 0)), 12, cTextSecondary, false), matchWrapMargin(0, 8));

        String type = q.optString("type", "single");
        Object saved = currentAnswers.opt(String.valueOf(q.optInt("id")));
        if ("single".equals(type) || "true_false".equals(type)) {
            RadioGroup group = new RadioGroup(this);
            group.setOrientation(LinearLayout.VERTICAL);
            JSONArray options = q.optJSONArray("options");
            if (options != null) for (int i = 0; i < options.length(); i++) {
                String value = options.optString(i);
                RadioButton rb = new RadioButton(this);
                rb.setText(value);
                rb.setTextSize(15);
                rb.setTag(value);
                rb.setButtonTintList(ColorStateList.valueOf(accent));
                rb.setPadding(dp(6), dp(9), dp(6), dp(9));
                if (saved instanceof String && value.equals(saved)) rb.setChecked(true);
                group.addView(rb, matchWrap());
            }
            answerInputView = group;
            qCard.addView(group, matchWrap());
        } else if ("multiple".equals(type)) {
            LinearLayout group = column();
            JSONArray options = q.optJSONArray("options");
            JSONArray savedArray = saved instanceof JSONArray ? (JSONArray) saved : null;
            if (options != null) for (int i = 0; i < options.length(); i++) {
                String value = options.optString(i);
                CheckBox cb = new CheckBox(this);
                cb.setText(value);
                cb.setTextSize(15);
                cb.setTag(value);
                cb.setButtonTintList(ColorStateList.valueOf(accent));
                cb.setPadding(dp(6), dp(9), dp(6), dp(9));
                cb.setChecked(arrayContains(savedArray, value));
                group.addView(cb, matchWrap());
            }
            answerInputView = group;
            qCard.addView(group, matchWrap());
        } else {
            EditText answer = input("پاسخ شما");
            attachFocusGlow(answer);
            if ("number".equals(type)) answer.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            else if ("essay".equals(type)) { answer.setMinLines(5); answer.setGravity(Gravity.TOP | Gravity.RIGHT); }
            if (saved instanceof String) answer.setText((String) saved);
            answerInputView = answer;
            qCard.addView(answer, matchWrap());
        }
        page.addView(qCard, matchWrapMargin(0, 12));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = secondaryButton("قبلی");
        prev.setEnabled(currentQuestionIndex > 0);
        prev.setOnClickListener(v -> saveCurrentAnswerThen(() -> { currentQuestionIndex--; showQuestion(); }));
        Button next = primaryButton(currentQuestionIndex == currentQuestions.length() - 1 ? "ثبت نهایی" : "بعدی");
        next.setOnClickListener(v -> saveCurrentAnswerThen(() -> {
            if (currentQuestionIndex == currentQuestions.length() - 1) confirmSubmit();
            else { currentQuestionIndex++; showQuestion(); }
        }));
        nav.addView(prev, weighted());
        nav.addView(next, weightedMargin(8));
        page.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        setScrollable(page);
    }

    private void saveCurrentAnswerThen(Runnable done) {
        if (currentAttemptId <= 0 || currentQuestions.length() == 0) { done.run(); return; }
        JSONObject q = currentQuestions.optJSONObject(currentQuestionIndex);
        if (q == null) { done.run(); return; }
        Object value = collectAnswer(q.optString("type", "single"));
        try { currentAnswers.put(String.valueOf(q.optInt("id")), value); } catch (Exception ignored) {}
        runApi(() -> {
            JSONObject body = new JSONObject();
            body.put("question_id", q.optInt("id"));
            body.put("answer", value);
            return api.postAttempt("/wp-json/physiquiz/v1/attempts/" + currentAttemptId + "/answer", body, currentAttemptToken);
        }, json -> done.run());
    }

    private Object collectAnswer(String type) {
        if (answerInputView instanceof RadioGroup) {
            RadioGroup g = (RadioGroup) answerInputView;
            int id = g.getCheckedRadioButtonId();
            if (id == -1) return "";
            View v = g.findViewById(id);
            return v != null && v.getTag() != null ? v.getTag().toString() : "";
        }
        if ("multiple".equals(type) && answerInputView instanceof LinearLayout) {
            JSONArray a = new JSONArray();
            LinearLayout g = (LinearLayout) answerInputView;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (v instanceof CheckBox && ((CheckBox) v).isChecked() && v.getTag() != null) a.put(v.getTag().toString());
            }
            return a;
        }
        if (answerInputView instanceof EditText) return ((EditText) answerInputView).getText().toString().trim();
        return "";
    }

    private void confirmSubmit() {
        new AlertDialog.Builder(this)
                .setTitle("ثبت نهایی آزمون")
                .setMessage("بعد از ثبت نهایی امکان تغییر پاسخ‌ها وجود ندارد. آزمون ثبت شود؟")
                .setNegativeButton("ادامه آزمون", null)
                .setPositiveButton("ثبت نهایی", (d, w) -> submitAttempt())
                .show();
    }

    private void submitAttempt() {
        stopTimer();
        runApi(() -> api.postAttempt("/wp-json/physiquiz/v1/attempts/" + currentAttemptId + "/submit", new JSONObject(), currentAttemptToken), this::showFinalResult);
    }

    private void showFinalResult(JSONObject result) {
        stopTimer();
        if (!config.blockScreenshots) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        currentAttemptId = 0;
        currentAttemptToken = "";
        clearAttemptPrefs();
        prepareSection("نتیجه آزمون");
        LinearLayout page = pageColumn();
        if (result.optBoolean("results_pending", false)) {
            page.addView(hero("پاسخ‌برگ ثبت شد", result.optString("message", "نتیجه توسط مدیر اعلام خواهد شد.")), matchWrapMargin(0, 14));
            Button results = primaryButton("رفتن به کارنامه‌ها");
            results.setOnClickListener(v -> showResults());
            page.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            setScrollable(page);
            return;
        }
        boolean passed = result.optBoolean("passed");
        float percent = (float) result.optDouble("percent");
        int stateColor = passed ? Color.rgb(22, 163, 74) : Color.rgb(220, 38, 38);

        // حلقه درصد متحرک
        LinearLayout ringRow = new LinearLayout(this);
        ringRow.setGravity(Gravity.CENTER);
        ProgressRingView ring = new ProgressRingView(this);
        ring.setColors(accent, cTextPrimary);
        ring.setPercent(percent);
        ringRow.addView(ring, new LinearLayout.LayoutParams(dp(150), dp(150)));
        LinearLayout ringCard = card();
        ringCard.setGravity(Gravity.CENTER);
        ringCard.addView(ringRow, matchWrap());
        TextView verdict = text(passed ? "قبول شدی 🎉" : "نتیجه ثبت شد", 18, stateColor, true);
        verdict.setGravity(Gravity.CENTER); verdict.setPadding(0, dp(10), 0, 0);
        ringCard.addView(verdict, matchWrap());
        page.addView(ringCard, matchWrapMargin(0, 14));

        page.addView(infoLine("امتیاز", fmt(result.optDouble("score")) + " از " + fmt(result.optDouble("max_score"))), matchWrapMargin(0, 6));
        page.addView(infoLine("صحیح", String.valueOf(result.optInt("correct_count"))), matchWrapMargin(0, 6));
        page.addView(infoLine("غلط", String.valueOf(result.optInt("wrong_count"))), matchWrapMargin(0, 6));
        page.addView(infoLine("بی‌پاسخ", String.valueOf(result.optInt("unanswered_count"))), matchWrapMargin(0, 12));
        Button results = primaryButton("رفتن به کارنامه‌ها");
        results.setOnClickListener(v -> showResults());
        page.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        setScrollable(page);
    }

    private void showResults() {
        prepareSection("نتایج");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/results"), json -> {
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("کارنامه‌های من", "نتایج ثبت‌شده مستقیماً از داده‌های " + config.appName + " دریافت می‌شوند."), matchWrapMargin(0, 16));
            renderCarouselsFor(page, "results");
            JSONArray rows = json.optJSONArray("results");
            if (rows == null || rows.length() == 0) page.addView(empty("هنوز نتیجه‌ای ثبت نشده است."), matchWrap());
            else for (int i = 0; i < rows.length(); i++) addResultCard(page, rows.optJSONObject(i));
            setScrollable(page);
        });
    }

    private void addResultCard(LinearLayout page, JSONObject r) {
        if (r == null) return;
        LinearLayout card = card();
        if (r.optBoolean("results_pending", false)) {
            card.addView(cardHeader("⏳", cTextSecondary, r.optString("exam_title", "آزمون"), pill("در انتظار اعلام", cTextSecondary)), matchWrap());
            TextView msg = bodyText(r.optString("message", "نتیجه هنوز اعلام نشده است."));
            msg.setPadding(0, dp(8), 0, dp(2));
            card.addView(msg, matchWrap());
            card.addView(text(r.optString("submitted_at", ""), 12, cTextSecondary, false), matchWrap());
            page.addView(card, matchWrapMargin(0, 10));
            return;
        }
        boolean passed = r.optBoolean("passed");
        int stateColor = passed ? Color.rgb(22, 163, 74) : cTextSecondary;
        card.addView(cardHeader(passed ? "🏅" : "📋", passed ? Color.rgb(22, 163, 74) : cTextSecondary, r.optString("exam_title", "آزمون"), pill(fmt(r.optDouble("percent")) + "%", stateColor)), matchWrap());
        TextView resultLine = text(passed ? "قبول شده" : "ثبت‌شده", 13, stateColor, true);
        resultLine.setPadding(0, dp(8), 0, dp(2));
        card.addView(resultLine, matchWrap());
        card.addView(text(r.optString("submitted_at", ""), 12, cTextSecondary, false), matchWrap());
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void showFiles() {
        prepareSection("فایل‌ها");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/files"), json -> {
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("منابع و فایل‌ها", "فایل‌هایی که مدیر در اتاق آزمون وردپرس قرار داده است."), matchWrapMargin(0, 16));
            renderCarouselsFor(page, "files");
            JSONArray files = json.optJSONArray("files");
            if (files == null || files.length() == 0) page.addView(empty("هنوز فایلی برای این بخش ثبت نشده است."), matchWrap());
            else for (int i = 0; i < files.length(); i++) addFileRow(page, files.optJSONObject(i));
            setScrollable(page);
        });
    }

    private void addFileRow(LinearLayout page, JSONObject file) {
        if (file == null) return;
        String url = file.optString("url", "");
        String type = file.optString("type", "LINK");
        String lower = url.toLowerCase(Locale.US);
        String glyph = lower.contains(".pdf") ? "📄" : (type.toUpperCase(Locale.US).contains("VIDEO") ? "🎬" : "🔗");
        LinearLayout card = card();
        card.addView(cardHeader(glyph, accent, file.optString("label", "فایل"), pill(type, accent)), matchWrap());
        Button open = secondaryButton("مشاهده / دانلود");
        open.setOnClickListener(v -> { if (lower.contains(".pdf")) openPdf(url); else openExternal(url); });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        olp.topMargin = dp(12);
        card.addView(open, olp);
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void showProfile() {
        prepareSection("پروفایل");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/me"), json -> {
            JSONObject user = json.optJSONObject("user");
            if (user == null) { showErrorScreen("اطلاعات پروفایل دریافت نشد."); return; }
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("اطلاعات دانش‌آموز", user.optString("email", "")), matchWrapMargin(0, 16));
            renderCarouselsFor(page, "profile");
            EditText first = input("نام"); attachFocusGlow(first); first.setText(user.optString("first_name", "")); page.addView(first, matchWrapMargin(0, 8));
            EditText last = input("نام خانوادگی"); attachFocusGlow(last); last.setText(user.optString("last_name", "")); page.addView(last, matchWrapMargin(0, 8));
            EditText display = input("نام نمایشی"); attachFocusGlow(display); display.setText(user.optString("display_name", "")); page.addView(display, matchWrapMargin(0, 8));
            EditText mobile = input("شماره تماس"); attachFocusGlow(mobile); mobile.setText(user.optString("mobile", "")); mobile.setInputType(InputType.TYPE_CLASS_PHONE); page.addView(mobile, matchWrapMargin(0, 12));
            JSONObject ac = user.optJSONObject("academic");
            if (ac != null) {
                page.addView(infoLine("پایه تحصیلی", ac.optString("grade_label", "ثبت نشده")), matchWrapMargin(0, 6));
                page.addView(infoLine("کلاس", ac.optString("class_name", "ثبت نشده")), matchWrapMargin(0, 6));
                page.addView(infoLine("مدرسه", ac.optString("school_name", "ثبت نشده")), matchWrapMargin(0, 12));
            }
            Button save = primaryButton("ذخیره تغییرات");
            save.setOnClickListener(v -> runApi(() -> {
                JSONObject body = new JSONObject();
                body.put("first_name", first.getText().toString().trim());
                body.put("last_name", last.getText().toString().trim());
                body.put("display_name", display.getText().toString().trim());
                body.put("mobile", mobile.getText().toString().trim());
                return api.post("/wp-json/physiquiz/v1/mobile/profile", body);
            }, out -> toast("پروفایل ذخیره شد.")));
            page.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            if (config.supportUrl != null && !config.supportUrl.trim().isEmpty()) {
                Button support = secondaryButton("تماس با پشتیبانی");
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
                slp.topMargin = dp(12);
                support.setOnClickListener(v -> openExternal(config.supportUrl));
                page.addView(support, slp);
            }
            Button logout = secondaryButton("خروج از حساب");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)); lp.topMargin = dp(12);
            page.addView(logout, lp);
            logout.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("خروج از حساب").setMessage("از حساب " + config.appName + " خارج می‌شوی؟").setNegativeButton("خیر", null).setPositiveButton("خروج", (d, w) -> doLogout()).show());
            setScrollable(page);
        });
    }

    private void doLogout() {
        runApi(() -> api.post("/wp-json/physiquiz/v1/mobile/logout", new JSONObject()), json -> clearSessionAndLogin());
    }

    private void clearSessionAndLogin() {
        prefs.edit().remove(PREF_AUTH).apply();
        api.setAuthToken("");
        currentAttemptId = 0;
        currentAttemptToken = "";
        clearAttemptPrefs();
        stopTimer();
        if (!config.blockScreenshots) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        showLogin();
    }

    private void prepareSection(String title) {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        topTitle.setText(title);
        updateActiveNav(title);
        setScreen(empty("در حال دریافت اطلاعات…"));
    }

    private void showErrorScreen(String message) {
        LinearLayout page = pageColumn();
        page.addView(empty(message), matchWrapMargin(0, 12));
        Button retry = secondaryButton("تلاش دوباره");
        retry.setOnClickListener(v -> refreshCurrent());
        page.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        setScrollable(page);
    }

    private interface JsonCall { JSONObject run() throws Exception; }
    private interface JsonSuccess { void run(JSONObject json); }
    private interface JsonError { void run(Exception e); }

    private void runApi(JsonCall call, JsonSuccess success) {
        runApi(call, success, e -> showMessage(
                e instanceof ApiClient.ApiException ? "خطا" : "خطای اتصال",
                e instanceof ApiClient.ApiException ? e.getMessage() : "ارتباط با REST API برقرار نشد. اینترنت، SSL و فعال بودن افزونه را بررسی کنید."
        ));
    }

    private void runApi(JsonCall call, JsonSuccess success, JsonError error) {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                JSONObject json = call.run();
                ui.post(() -> { loading.setVisibility(View.GONE); success.run(json); });
            } catch (ApiClient.ApiException e) {
                ui.post(() -> {
                    loading.setVisibility(View.GONE);
                    if (e.status == 401 && !prefs.getString(PREF_AUTH, "").isEmpty()) {
                        toast("نشست ورود منقضی شده است. دوباره وارد شوید.");
                        clearSessionAndLogin();
                    } else error.run(e);
                });
            } catch (Exception e) {
                ui.post(() -> { loading.setVisibility(View.GONE); error.run(e); });
            }
        });
    }

    private void openPdf(String url) {
        if (url == null || !url.startsWith("https://")) { toast("آدرس PDF معتبر نیست."); return; }
        Intent intent = new Intent(this, PdfActivity.class);
        intent.putExtra(PdfActivity.EXTRA_URL, url);
        intent.putExtra(PdfActivity.EXTRA_SECURE, currentAntiCheat || config.blockScreenshots);
        intent.putExtra(PdfActivity.EXTRA_FULLSCREEN, false);
        intent.putExtra(PdfActivity.EXTRA_AUTH, prefs.getString(PREF_AUTH, ""));
        startActivity(intent);
    }

    private void openExternal(String url) {
        if (!config.allowExternalLinks) { toast("باز کردن لینک‌های خارجی توسط مدیر غیرفعال شده است."); return; }
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) { toast("فقط لینک HTTPS مجاز است."); return; }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) { toast("این لینک قابل باز شدن نیست."); }
    }

    private void startTimer() {
        stopTimer();
        if (currentExam.optInt("duration_minutes", 0) <= 0) return;
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (remainingSeconds > 0) remainingSeconds--;
                if (timerText != null) {
                    timerText.setText(timeText(remainingSeconds));
                    if (remainingSeconds <= 60) {
                        // زیر یک دقیقه: چشمک هشدار
                        timerText.setAlpha(timerText.getAlpha() > 0.65f ? 0.45f : 1f);
                    }
                }
                if (remainingSeconds <= 0) {
                    stopTimer();
                    toast("زمان آزمون تمام شد؛ پاسخ‌ها ثبت نهایی می‌شوند.");
                    submitAttempt();
                    return;
                }
                ui.postDelayed(this, 1000);
            }
        };
        ui.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        if (timerRunnable != null) ui.removeCallbacks(timerRunnable);
        timerRunnable = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentAttemptId > 0 && currentAntiCheat) {
            io.execute(() -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("type", "visibility_change");
                    JSONObject data = new JSONObject(); data.put("state", "background"); b.put("data", data);
                    api.postAttempt("/wp-json/physiquiz/v1/attempts/" + currentAttemptId + "/log", b, currentAttemptToken);
                } catch (Exception ignored) {}
            });
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        io.shutdownNow();
        super.onDestroy();
    }

    private void setScrollable(LinearLayout page) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setScreen(scroll);
    }

    private void setScreen(View view) {
        content.removeAllViews();
        view.setAlpha(0f);
        view.setTranslationY(dp(14));
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
    }

    private LinearLayout pageColumn() {
        LinearLayout page = column();
        page.setPadding(dp(16), dp(16), dp(16), dp(24));
        return page;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(cSurface, 20, darkMode ? adjustAlpha(accent, 0xB0) : cBorder, darkMode ? 2 : 1));
        card.setElevation(dp(2));
        applyGlow(card, 2);
        return card;
    }

    private void applyGlow(View v, int elevationDp) {
        if (!darkMode) return;
        v.setOutlineAmbientShadowColor(adjustAlpha(accent, 0x90));
        v.setOutlineSpotShadowColor(accent);
        v.setElevation(dp(elevationDp + 7));
    }

    private View progressCard(String label, int percent) {
        LinearLayout c = card();
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = text(label, 14, cTextPrimary, true);
        headRow.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView pctView = text("0%", 14, accent, true);
        headRow.addView(pctView, matchWrap());
        c.addView(headRow, matchWrap());

        FrameLayout track = new FrameLayout(this);
        track.setBackground(roundRect(cPlaceholder, 8, Color.TRANSPARENT, 0));
        View fill = new View(this);
        fill.setBackground(roundRect(accent, 8, Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        track.addView(fill, fillLp);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        trackLp.topMargin = dp(10);
        c.addView(track, trackLp);

        track.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                track.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int fullWidth = track.getWidth();
                if (fullWidth <= 0) return;
                int targetWidth = Math.round(fullWidth * (percent / 100f));
                ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
                anim.setDuration(800);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.addUpdateListener(a -> {
                    int w = (int) a.getAnimatedValue();
                    fillLp.width = w;
                    fill.setLayoutParams(fillLp);
                    pctView.setText(Math.round((w / (float) fullWidth) * percent) + "%");
                });
                anim.start();
            }
        });
        return c;
    }

    private View statCard(String label, double targetValue, String suffix) {
        LinearLayout c = card();
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{cSurface, darkMode ? adjustAlpha(accent, 0x18) : tintTowardWhite(accent, 0.88f)});
        grad.setCornerRadius(dp(20));
        if (!darkMode) grad.setStroke(dp(1), cBorder);
        c.setBackground(grad);
        TextView big = text("0" + suffix, 24, accent, true);
        if (darkMode) applyTextGlow(big, accent);
        c.addView(big, matchWrap());
        c.addView(text(label, 12, cTextSecondary, false), matchWrapMargin(0, 3));
        animateCountUp(big, targetValue, suffix);
        return c;
    }

    private void animateCountUp(TextView tv, double target, String suffix) {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, (float) target);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> tv.setText(fmt((float) a.getAnimatedValue()) + suffix));
        anim.start();
    }

    private void applyTextGlow(TextView t, int color) {
        if (!darkMode) return;
        t.setShadowLayer(dp(12), 0, 0, color);
    }

    private View roundIcon(String glyph, int bgColor, int fgColor, int sizeDp) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextColor(fgColor);
        t.setTextSize(sizeDp >= 40 ? 18 : 14);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(bgColor);
        t.setBackground(d);
        return t;
    }

    private TextView pill(String label, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(color);
        t.setTypeface(fontBold);
        t.setTextSize(11);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(roundRect(adjustAlpha(color, darkMode ? 0x33 : 0x22), 20, Color.TRANSPARENT, 0));
        return t;
    }

    private LinearLayout cardHeader(String glyph, int tint, String title, View trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(roundIcon(glyph, adjustAlpha(tint, darkMode ? 0x33 : 0x26), tint, 34), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = text(title, 16, cTextPrimary, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.leftMargin = dp(10);
        tlp.rightMargin = dp(8);
        row.addView(t, tlp);
        if (trailing != null) row.addView(trailing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View bannerImage(String url) {
        FrameLayout wrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cPlaceholder);
        bg.setCornerRadius(dp(20));
        wrap.setBackground(bg);
        wrap.setClipToOutline(true);
        wrap.setElevation(dp(3));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wrap.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadImageInto(url, iv);
        FrameLayout outer = new FrameLayout(this);
        outer.addView(wrap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        return outer;
    }

    private void loadImageInto(String url, ImageView iv) {
        Bitmap cached = imageCache.get(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        iv.setAlpha(0f);
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bmp != null) {
                    imageCache.put(url, bmp);
                    ui.post(() -> { iv.setImageBitmap(bmp); iv.animate().alpha(1f).setDuration(220).start(); });
                }
            } catch (Exception ignored) {
                ui.post(() -> iv.animate().alpha(1f).setDuration(150).start());
            }
        });
    }

    private View hero(String title, String subtitle, String avatarName) {
        LinearLayout h = column();
        h.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{accent, darken(accent, 0.55f)});
        g.setCornerRadius(dp(26));
        h.setBackground(g);
        h.setElevation(dp(5));
        applyGlow(h, 5);

        if (avatarName != null && !avatarName.trim().isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(avatarBadge(avatarName.trim().substring(0, 1).toUpperCase(Locale.getDefault())), new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = text(title, 21, Color.WHITE, true);
            LinearLayout.LayoutParams tlp = matchWrap();
            tlp.leftMargin = dp(10);
            row.addView(t, tlp);
            h.addView(row, matchWrap());
        } else {
            h.addView(text(title, 22, Color.WHITE, true), matchWrap());
        }

        TextView s = text(subtitle, 13, adjustAlpha(Color.WHITE, 0xDA), false);
        s.setPadding(0, dp(8), 0, 0);
        h.addView(s, matchWrap());
        return h;
    }

    private View hero(String title, String subtitle) { return hero(title, subtitle, null); }

    private View avatarBadge(String letter) {
        TextView t = new TextView(this);
        t.setText(letter);
        t.setTextColor(accent);
        t.setTextSize(18);
        t.setTypeface(fontBold);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.WHITE);
        t.setBackground(d);
        return t;
    }

    private int darken(int color, float factor) {
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.rgb(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));
    }

    private int tintTowardWhite(int color, float factor) {
        int r = Math.round(Color.red(color) + (255 - Color.red(color)) * factor);
        int g = Math.round(Color.green(color) + (255 - Color.green(color)) * factor);
        int b = Math.round(Color.blue(color) + (255 - Color.blue(color)) * factor);
        return Color.rgb(Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    private View sectionIntro(String title, String subtitle) {
        LinearLayout c = card();
        c.addView(text(title, 21, cTextPrimary, true), matchWrap());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView p = bodyText(subtitle);
            p.setPadding(0, dp(7), 0, 0);
            c.addView(p, matchWrap());
        }
        return c;
    }

    private TextView sectionTitle(String t) { return text(t, 18, cTextPrimary, true); }

    private TextView bodyText(String t) {
        TextView v = text(t == null ? "" : t, 14, cTextStrong, false);
        v.setLineSpacing(0, 1.25f);
        return v;
    }

    private View note(String t) {
        TextView v = bodyText(t);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setBackground(roundRect(cAccentTint, 14, cAccentBorder, 1));
        return v;
    }

    private View infoLine(String label, String value) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        l.setBackground(roundRect(cSurface, 14, cBorder, 1));
        TextView a = text(label, 13, cTextSecondary, false);
        TextView b = text(value == null || value.isEmpty() ? "ثبت نشده" : value, 14, cTextPrimary, true);
        b.setGravity(Gravity.LEFT);
        l.addView(a, weighted());
        l.addView(b, weighted());
        return l;
    }

    private TextView empty(String t) {
        TextView v = text(t, 14, cTextSecondary, false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(20), dp(28), dp(20), dp(28));
        return v;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        t.setTypeface(bold ? fontBold : fontRegular);
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTypeface(fontRegular);
        e.setTextColor(cTextPrimary);
        e.setHintTextColor(cHint);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(roundRect(cSurface, 14, cBorder, 1));
        e.setSingleLine(false);
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(fontBold);
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{accent, darken(accent, 0.82f)});
        grad.setCornerRadius(dp(16));
        b.setBackground(withRipple(grad, 16, 0x40FFFFFF));
        b.setElevation(dp(3));
        applyGlow(b, 3);
        b.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start(); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); break;
            }
            return false;
        });
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(accent);
        b.setTypeface(fontBold);
        b.setBackground(withRipple(roundRect(cSurface, 16, cAccentBorder, 1), 16, adjustAlpha(accent, 0x33)));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(cTextStrong);
        b.setTypeface(fontRegular);
        b.setBackground(withRipple(null, 20, 0x1F000000));
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private GradientDrawable roundRect(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private void loadFonts() {
        try { fontRegular = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Regular.ttf"); } catch (Exception ignored) { }
        try { fontBold = Typeface.createFromAsset(getAssets(), "fonts/Vazirmatn-Bold.ttf"); } catch (Exception ignored) { }
    }

    private void computeThemeTokens() {
        if (darkMode) {
            cBg = darken(accent, 0.10f);
            cSurface = darken(accent, 0.17f);
            cBorder = darken(accent, 0.32f);
            cTextPrimary = Color.rgb(237, 240, 246);
            cTextSecondary = Color.rgb(148, 163, 184);
            cTextStrong = Color.rgb(203, 213, 225);
            cHint = Color.rgb(100, 111, 130);
            cPlaceholder = Color.rgb(38, 43, 56);
            cAccentTint = adjustAlpha(accent, 0x30);
        } else {
            cBg = background;
            cSurface = Color.WHITE;
            cBorder = Color.rgb(226, 232, 240);
            cTextPrimary = Color.rgb(15, 23, 42);
            cTextSecondary = Color.rgb(100, 116, 139);
            cTextStrong = Color.rgb(51, 65, 85);
            cHint = Color.rgb(148, 163, 184);
            cPlaceholder = Color.rgb(230, 233, 240);
            cAccentTint = adjustAlpha(accent, 0x14);
        }
        cAccentBorder = adjustAlpha(accent, darkMode ? 0x66 : 0x40);
    }

    private void setLightStatusBarIcons(boolean light) {
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
    }

    private void setDarkMode(boolean enabled) {
        prefs.edit().putBoolean(PREF_DARK_MODE, enabled).apply();
        recreate();
    }

    private Drawable withRipple(Drawable content, int radiusDp, int rippleColor) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private void addRippleFeedback(View v, int radiusDp) {
        v.setForeground(withRipple(null, radiusDp, adjustAlpha(accent, 0x26)));
    }

    private void addPressScale(View v, float scale) {
        v.animate().scaleX(scale).scaleY(scale).setDuration(90).start();
        v.postDelayed(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(), 110);
    }

    private int adjustAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrapMargin(int top, int bottom) { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(top); p.bottomMargin = dp(bottom); return p; }
    private LinearLayout.LayoutParams matchWrapHeightMargin(int heightDp, int top, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)); p.topMargin = dp(top); p.bottomMargin = dp(bottom); return p; }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private LinearLayout.LayoutParams weightedMargin(int leftDp) { LinearLayout.LayoutParams p = weighted(); p.leftMargin = dp(leftDp); return p; }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String htmlToText(String html) {
        if (html == null) return "";
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private boolean arrayContains(JSONArray a, String value) {
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (value.equals(a.optString(i))) return true;
        return false;
    }

    private String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.0001) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.1f", v);
    }

    private String timeText(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void showMessage(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("باشه", null).show(); }

    @Override
    public void onBackPressed() {
        if (currentAttemptId > 0) {
            new AlertDialog.Builder(this).setTitle("خروج از آزمون").setMessage("آزمون هنوز در حال اجراست. برای جلوگیری از از دست رفتن پاسخ‌ها در همین صفحه بمانید.").setPositiveButton("ادامه آزمون", null).show();
            return;
        }
        super.onBackPressed();
    }
}
