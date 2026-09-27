package com.physiquiz.student;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PdfActivity extends Activity {
    public static final String EXTRA_URL = "pdf_url";
    public static final String EXTRA_SECURE = "secure";
    public static final String EXTRA_FULLSCREEN = "fullscreen";
    public static final String EXTRA_AUTH = "auth";

    private ImageView imageView;
    private TextView pageText;
    private TextView statusText;
    private Button prevButton;
    private Button nextButton;
    private ProgressBar progressBar;
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private PdfRenderer.Page currentPage;
    private Bitmap currentBitmap;
    private int pageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean secure = getIntent().getBooleanExtra(EXTRA_SECURE, false);
        boolean fullscreen = getIntent().getBooleanExtra(EXTRA_FULLSCREEN, false);
        if (secure) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (fullscreen) getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildUi();
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.trim().isEmpty()) {
            statusText.setText("آدرس PDF معتبر نیست.");
            return;
        }
        if (!url.toLowerCase(java.util.Locale.US).startsWith("https://")) {
            statusText.setText("برای امنیت آزمون فقط فایل PDF با HTTPS مجاز است.");
            return;
        }
        downloadAndOpen(url);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(247, 249, 252));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(10));
        Button close = button("بازگشت");
        close.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("مشاهده فایل سؤال PDF");
        title.setTextSize(17);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER);
        top.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this);
        root.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("در حال دریافت فایل سؤال…");
        statusText.setTextColor(Color.rgb(100, 116, 139));
        statusText.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout imageFrame = new FrameLayout(this);
        imageFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.WHITE);
        imageFrame.addView(imageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(imageFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(10), dp(10), dp(14));
        prevButton = button("صفحه قبل");
        nextButton = button("صفحه بعد");
        pageText = new TextView(this);
        pageText.setGravity(Gravity.CENTER);
        pageText.setTextColor(Color.rgb(15, 23, 42));
        pageText.setTextSize(15);
        pageText.setPadding(dp(18), 0, dp(18), 0);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        prevButton.setOnClickListener(v -> showPage(pageIndex - 1));
        nextButton.setOnClickListener(v -> showPage(pageIndex + 1));
        nav.addView(prevButton);
        nav.addView(pageText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nav.addView(nextButton);
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        return button;
    }

    private void downloadAndOpen(String urlString) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            FileOutputStream output = null;
            InputStream input = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "PhysiQuizAndroid/1.0");
                String auth = getIntent().getStringExtra(EXTRA_AUTH);
                if (auth != null && !auth.trim().isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + auth);
                    connection.setRequestProperty("X-PhysiQuiz-Auth", auth);
                }
                String cookie = CookieManager.getInstance().getCookie(urlString);
                if (cookie != null && !cookie.trim().isEmpty()) connection.setRequestProperty("Cookie", cookie);
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                File file = new File(getCacheDir(), "physiquiz-question-" + Math.abs(urlString.hashCode()) + ".pdf");
                input = connection.getInputStream();
                output = new FileOutputStream(file);
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.flush();
                File finalFile = file;
                runOnUiThread(() -> openPdf(finalFile));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("فایل PDF باز نشد. اتصال یا دسترسی فایل را بررسی کنید.");
                    Toast.makeText(this, "خطا در دریافت PDF", Toast.LENGTH_LONG).show();
                });
            } finally {
                try { if (input != null) input.close(); } catch (Exception ignored) {}
                try { if (output != null) output.close(); } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void openPdf(File file) {
        try {
            closeRenderer();
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            progressBar.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            showPage(0);
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            statusText.setText("ساختار فایل PDF قابل خواندن نیست.");
            statusText.setVisibility(View.VISIBLE);
        }
    }

    private void showPage(int index) {
        if (renderer == null || renderer.getPageCount() == 0) return;
        index = Math.max(0, Math.min(index, renderer.getPageCount() - 1));
        closeCurrentPage();
        pageIndex = index;
        currentPage = renderer.openPage(pageIndex);

        int screenWidth = getResources().getDisplayMetrics().widthPixels - dp(24);
        float ratio = (float) currentPage.getHeight() / (float) currentPage.getWidth();
        int width = Math.max(1, screenWidth);
        int height = Math.max(1, Math.round(width * ratio));
        int maxHeight = Math.max(getResources().getDisplayMetrics().heightPixels * 3, 2048);
        if (height > maxHeight) {
            float scale = (float) maxHeight / (float) height;
            width = Math.max(1, Math.round(width * scale));
            height = maxHeight;
        }
        currentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        currentBitmap.eraseColor(Color.WHITE);
        currentPage.render(currentBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        imageView.setImageBitmap(currentBitmap);
        pageText.setText("صفحه " + (pageIndex + 1) + " از " + renderer.getPageCount());
        prevButton.setEnabled(pageIndex > 0);
        nextButton.setEnabled(pageIndex < renderer.getPageCount() - 1);
    }

    private void closeCurrentPage() {
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }

    private void closeRenderer() {
        closeCurrentPage();
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        try {
            if (descriptor != null) descriptor.close();
        } catch (Exception ignored) {}
        descriptor = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        closeRenderer();
        super.onDestroy();
    }
}
