package com.physiquiz.student;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Typed view of {site}/wp-json/physiquiz/v1/app-config
 * Defaults here mirror PhysiQuiz_Mobile_App::defaults() on the WordPress side,
 * so the app behaves sensibly even if the request fails (fail-open, never
 * fail-closed on a network error).
 */
public class AppConfig {
    public boolean enabled = true;
    public String appName = "فیزیکوییز";
    public String accentColor = "#2563eb";
    public String backgroundColor = "#f7f9fc";
    public String bannerUrl = "";
    /** Each element: {"title":"","image":"","text":"","link":"","layout":"image_top|image_side|text_only","bg_color":"","text_color":""} — managed entirely from wp-admin, no app update needed to add/remove cards or restyle them. */
    public JSONArray cards = new JSONArray();
    /** Each element: {"title":"","cards":[...]} — the same cards grouped into up to 3 horizontally-scrolling carousel rows, each with an optional row heading. Preferred over the flat `cards` for rendering; `cards` stays around as a flat fallback. */
    public JSONArray cardRows = new JSONArray();
    public boolean maintenanceMode = false;
    public String maintenanceMessage = "سامانه برای مدت کوتاهی در حال بروزرسانی است. لطفاً کمی بعد دوباره تلاش کنید.";
    public String supportUrl = "";
    public boolean allowExternalLinks = true;
    public boolean blockScreenshots = false;
    public boolean forceFullscreen = false;
    public String latestVersionName = "";
    public int minimumVersionCode = 1;
    public String updateMessage = "";
    public String updateUrl = "";

    public boolean loaded = false;

    public static AppConfig fromJson(JSONObject j) {
        AppConfig c = new AppConfig();
        if (j == null || j.length() == 0) return c;
        c.loaded = true;
        c.enabled = j.optBoolean("enabled", c.enabled);
        c.appName = j.optString("app_name", c.appName);
        c.accentColor = safeHex(j.optString("accent_color", c.accentColor), c.accentColor);
        c.backgroundColor = safeHex(j.optString("background_color", c.backgroundColor), c.backgroundColor);
        c.bannerUrl = j.optString("banner_url", c.bannerUrl);
        JSONArray cards = j.optJSONArray("cards");
        c.cards = cards != null ? cards : new JSONArray();
        JSONArray cardRows = j.optJSONArray("card_rows");
        c.cardRows = cardRows != null ? cardRows : new JSONArray();
        c.maintenanceMode = j.optBoolean("maintenance_mode", c.maintenanceMode);
        c.maintenanceMessage = j.optString("maintenance_message", c.maintenanceMessage);
        c.supportUrl = j.optString("support_url", c.supportUrl);
        c.allowExternalLinks = j.optBoolean("allow_external_links", c.allowExternalLinks);
        c.blockScreenshots = j.optBoolean("block_screenshots", c.blockScreenshots);
        c.forceFullscreen = j.optBoolean("force_fullscreen", c.forceFullscreen);
        c.latestVersionName = j.optString("latest_version_name", c.latestVersionName);
        c.minimumVersionCode = j.optInt("minimum_version_code", c.minimumVersionCode);
        c.updateMessage = j.optString("update_message", c.updateMessage);
        c.updateUrl = j.optString("update_url", c.updateUrl);
        return c;
    }

    private static String safeHex(String v, String fallback) {
        if (v == null || !v.matches("^#[0-9a-fA-F]{6}$")) return fallback;
        return v;
    }
}
