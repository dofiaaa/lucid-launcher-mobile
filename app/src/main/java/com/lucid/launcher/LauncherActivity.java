package com.lucid.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherActivity extends Activity {

    private static final int APPWIDGET_HOST_ID = 1001;
    private static final int REQUEST_PICK_WIDGET = 7;
    private static final String ACTION_APPWIDGET_PICK = "android.appwidget.action.APPWIDGET_PICK";

    private static class WidgetHolder {
        final int id;
        final int spanX;
        final int spanY;
        final AppWidgetHostView view;

        WidgetHolder(int id, int spanX, int spanY, AppWidgetHostView view) {
            this.id = id;
            this.spanX = spanX;
            this.spanY = spanY;
            this.view = view;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private Prefs prefs;
    private AppRepo repo;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;

    private FrameLayout root;
    private ImageView wallpaper;
    private LauncherGrid grid;
    private LinearLayout dock;
    private View header;
    private TextView clock;
    private TextView dateView;
    private TextView emptyView;
    private EditText search;
    private ImageButton settingsBtn;
    private FrameLayout searchPill;

    private List<AppEntry> allApps = new ArrayList<>();
    private List<AppEntry> currentApps = new ArrayList<>();
    private List<AppEntry> dockApps = new ArrayList<>();
    private final List<WidgetHolder> widgets = new ArrayList<>();
    private final Map<String, AppCell> appCells = new HashMap<>();
    private boolean searching = false;

    private boolean pendingInvalidate = false;

    private final Runnable invalidateGrid = new Runnable() {
        @Override public void run() {
            pendingInvalidate = false;
            refreshIcons();
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        repo = new AppRepo(this, dp(52), dp(28));
        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);

        // Без полноэкранного режима: обычный статус-бар, контент не уходит под системные панели.
        setContentView(R.layout.activity_main);
        bindViews();
        applyInsets();
        applyGlassTheme();
        restoreWidgets();

        settingsBtn.setOnClickListener(v -> showSettings());
        setupSearch();

        updateClock();
        handler.postDelayed(clockTick, 30000);
        loadWallpaper();
        reloadApps();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);

        IntentFilter wf = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        registerReceiver(wallpaperReceiver, wf);

        root.requestFocus();
        search.clearFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (appWidgetHost != null) appWidgetHost.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (appWidgetHost != null) appWidgetHost.stopListening();
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        wallpaper = findViewById(R.id.wallpaper);
        grid = findViewById(R.id.grid);
        dock = findViewById(R.id.dock);
        header = findViewById(R.id.header);
        clock = findViewById(R.id.clock);
        dateView = findViewById(R.id.date);
        emptyView = findViewById(R.id.empty);
        search = findViewById(R.id.search);
        searchPill = findViewById(R.id.search_pill);
        settingsBtn = findViewById(R.id.settings);
    }

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            header.setPadding(header.getPaddingLeft(), top + dp(10),
                    header.getPaddingRight(), header.getPaddingBottom());
            dock.setPadding(dock.getPaddingLeft(), dock.getPaddingTop(),
                    dock.getPaddingRight(), bottom + dp(8));
            FrameLayout.LayoutParams slp = (FrameLayout.LayoutParams) settingsBtn.getLayoutParams();
            slp.topMargin = top + dp(6);
            slp.rightMargin = dp(6);
            settingsBtn.setLayoutParams(slp);
            return insets;
        });
    }

    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (!currentApps.isEmpty()) launch(currentApps.get(0));
                return true;
            }
            return false;
        });
    }

    private void applyFilter(String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        if (query.isEmpty()) {
            searching = false;
            currentApps = allApps;
            dock.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
        } else {
            searching = true;
            dock.setVisibility(View.GONE);
            List<AppEntry> filtered = new ArrayList<>();
            for (AppEntry e : allApps) {
                if (e.label.toLowerCase(Locale.getDefault()).contains(query)
                        || e.packageName.toLowerCase(Locale.getDefault()).contains(query)) {
                    filtered.add(e);
                }
            }
            currentApps = filtered;
            if (filtered.isEmpty()) {
                grid.setVisibility(View.INVISIBLE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                grid.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        }
        refreshGrid();
    }

    private void reloadApps() {
        executor.execute(() -> {
            final List<AppEntry> raw = repo.allLaunchable();
            final List<AppEntry> dockList = repo.resolveDock();
            final Set<String> hidden = prefs.hidden();
            final Set<String> dockPkgs = new HashSet<>();
            for (AppEntry e : dockList) dockPkgs.add(e.packageName);
            final List<AppEntry> apps = new ArrayList<>();
            for (AppEntry e : raw) {
                if (hidden.contains(e.packageName)) continue;
                if (dockPkgs.contains(e.packageName)) continue;
                apps.add(e);
            }
            handler.post(() -> {
                dockApps = dockList;
                allApps = apps;
                buildDock();
                pruneWidgets();
                applyFilter(search.getText().toString());
            });
            for (AppEntry e : apps) {
                if (e.iconBitmap == null) {
                    e.iconBitmap = repo.iconFor(e, repo.gridIconPx());
                    requestInvalidate();
                }
            }
        });
    }

    private void requestInvalidate() {
        if (!pendingInvalidate) {
            pendingInvalidate = true;
            handler.postDelayed(invalidateGrid, 100);
        }
    }

    private void refreshIcons() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View v = grid.getChildAt(i);
            if (v instanceof AppCell) {
                AppCell c = (AppCell) v;
                Object tag = c.getTag();
                if (tag instanceof AppEntry) {
                    AppEntry e = (AppEntry) tag;
                    c.bind(e.label, e.iconBitmap);
                }
            }
        }
    }

    private void refreshGrid() {
        List<LauncherGrid.GridItem> items = new ArrayList<>();
        if (!searching) {
            for (WidgetHolder wh : widgets) {
                items.add(new LauncherGrid.GridItem(wh.view, wh.spanX, wh.spanY));
            }
        }
        for (AppEntry e : currentApps) {
            AppCell cell = appCells.get(e.packageName);
            if (cell == null) {
                cell = new AppCell(this);
                cell.setOnClickListener(v -> launch(e));
                cell.setOnLongClickListener(v -> { showEntryMenu(e); return true; });
                appCells.put(e.packageName, cell);
            }
            cell.bind(e.label, e.iconBitmap);
            cell.setTag(e);
            items.add(new LauncherGrid.GridItem(cell, 1, 1));
        }
        grid.setItems(items);
        grid.post(() -> {
            for (WidgetHolder wh : widgets) {
                int w = wh.view.getWidth();
                int h = wh.view.getHeight();
                if (w > 0 && h > 0) {
                    wh.view.updateAppWidgetSize(null, w, h, w, h);
                }
            }
        });
    }

    private void buildDock() {
        dock.removeAllViews();
        for (final AppEntry e : dockApps) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(4), dp(8), dp(4));
            item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            item.addView(iv);

            TextView tv = new TextView(this);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(0xF0FFFFFF);
            tv.setTextSize(10);
            tv.setShadowLayer(dp(1), 0, dp(1), 0x66000000);
            tv.setText(e.label);
            item.addView(tv);

            item.setContentDescription(e.label);
            item.setOnClickListener(v -> launch(e));
            item.setOnLongClickListener(v -> { showEntryMenu(e); return true; });

            dock.addView(item);

            executor.execute(() -> {
                Bitmap b = repo.iconFor(e, repo.dockIconPx());
                handler.post(() -> iv.setImageBitmap(b));
            });
        }
    }

    private void launch(AppEntry e) {
        try {
            hideKeyboard();
            startActivity(e.launchIntent());
        } catch (Throwable t) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private void showEntryMenu(final AppEntry e) {
        boolean isDock = false;
        for (AppEntry d : dockApps) if (d.packageName.equals(e.packageName)) { isDock = true; break; }
        if (isDock) {
            new AlertDialog.Builder(this)
                    .setTitle(e.label)
                    .setItems(new String[]{ getString(R.string.open), getString(R.string.app_info) },
                            (d, which) -> {
                                if (which == 0) launch(e);
                                else openAppInfo(e.packageName);
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(e.label)
                    .setItems(new String[]{ getString(R.string.open), getString(R.string.app_info), getString(R.string.hide) },
                            (d, which) -> {
                                if (which == 0) launch(e);
                                else if (which == 1) openAppInfo(e.packageName);
                                else { prefs.hide(e.packageName); reloadApps(); }
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void openAppInfo(String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettings() {
        String glassLabel = getString(R.string.glass_panels) + ": "
                + (prefs.glass() ? getString(R.string.on) : getString(R.string.off));
        final String[] items = {
                getString(R.string.hidden_apps),
                glassLabel,
                getString(R.string.add_widget),
                getString(R.string.about),
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setItems(items, (d, which) -> {
                    if (which == 0) showHiddenApps();
                    else if (which == 1) { d.dismiss(); toggleGlass(); showSettings(); }
                    else if (which == 2) pickWidget();
                    else showAbout();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showHiddenApps() {
        final List<AppEntry> hidden = hiddenApps();
        final String[] labels = new String[hidden.size()];
        for (int i = 0; i < hidden.size(); i++) labels[i] = hidden.get(i).label;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.hidden_apps);
        if (hidden.isEmpty()) {
            b.setMessage(R.string.no_hidden);
        } else {
            b.setItems(labels, (d, which) -> {
                AppEntry e = hidden.get(which);
                prefs.unhide(e.packageName);
                reloadApps();
                Toast.makeText(this, getString(R.string.unhide) + ": " + e.label, Toast.LENGTH_SHORT).show();
            });
        }
        b.setNegativeButton(R.string.close, null);
        b.show();
    }

    private void toggleGlass() {
        boolean g = !prefs.glass();
        prefs.setGlass(g);
        applyGlassTheme();
        Toast.makeText(this, g ? R.string.glass_on : R.string.glass_off, Toast.LENGTH_SHORT).show();
    }

    private void applyGlassTheme() {
        boolean glass = prefs.glass();
        int pill = glass ? R.drawable.glass_pill : R.drawable.panel_opaque;
        int dockBg = glass ? R.drawable.glass_dock : R.drawable.dock_opaque;
        if (searchPill != null) searchPill.setBackgroundResource(pill);
        if (dock != null) dock.setBackgroundResource(dockBg);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private List<AppEntry> hiddenApps() {
        Set<String> hidden = prefs.hidden();
        List<AppEntry> raw = repo.allLaunchable();
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : raw) if (hidden.contains(e.packageName)) out.add(e);
        return out;
    }

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(this);
        SimpleDateFormat t = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault());
        SimpleDateFormat d = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
        clock.setText(t.format(cal.getTime()));
        dateView.setText(capitalize(d.format(cal.getTime())));
    }

    private void loadWallpaper() {
        executor.execute(() -> {
            final Bitmap b = WallpaperBlur.create(this);
            handler.post(() -> {
                if (b != null) wallpaper.setImageBitmap(b);
            });
        });
    }

    // ---- Widgets ----

    private void restoreWidgets() {
        widgets.clear();
        for (String s : prefs.widgetIds()) {
            try {
                int id = Integer.parseInt(s);
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
                if (info != null) {
                    AppWidgetHostView hv = appWidgetHost.createView(this, id, info);
                    hv.setTag(id);
                    hv.setOnLongClickListener(v -> { showWidgetMenu(id); return true; });
                    widgets.add(new WidgetHolder(id, widgetSpanX(info), widgetSpanY(info), hv));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void pickWidget() {
        int id = appWidgetHost.allocateAppWidgetId();
        Intent pick = new Intent(ACTION_APPWIDGET_PICK);
        pick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        try {
            startActivityForResult(pick, REQUEST_PICK_WIDGET);
        } catch (Throwable t) {
            appWidgetHost.deleteAppWidgetId(id);
            Toast.makeText(this, R.string.widgets_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WIDGET && resultCode == RESULT_OK && data != null) {
            int id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
                if (info != null) {
                    AppWidgetHostView hv = appWidgetHost.createView(this, id, info);
                    hv.setTag(id);
                    hv.setOnLongClickListener(v -> { showWidgetMenu(id); return true; });
                    widgets.add(new WidgetHolder(id, widgetSpanX(info), widgetSpanY(info), hv));
                    syncWidgetPrefs();
                    refreshGrid();
                }
            }
        }
    }

    private void showWidgetMenu(final int id) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(id);
        final boolean configurable = info != null && info.configure != null;
        List<String> items = new ArrayList<>();
        if (configurable) items.add(getString(R.string.widget_configure));
        items.add(getString(R.string.widget_remove));
        String[] arr = items.toArray(new String[0]);
        CharSequence title = info != null ? info.loadLabel(getPackageManager()) : "";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(arr, (d, which) -> {
                    if (configurable && which == 0) configureWidget(id, info);
                    else removeWidget(id);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void configureWidget(int id, AppWidgetProviderInfo info) {
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        i.setComponent(info.configure);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        try {
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void removeWidget(int id) {
        appWidgetHost.deleteAppWidgetId(id);
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).id == id) widgets.remove(i);
        }
        syncWidgetPrefs();
        refreshGrid();
    }

    private void pruneWidgets() {
        boolean changed = false;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            WidgetHolder wh = widgets.get(i);
            if (appWidgetManager.getAppWidgetInfo(wh.id) == null) {
                widgets.remove(i);
                changed = true;
            }
        }
        if (changed) {
            syncWidgetPrefs();
            refreshGrid();
        }
    }

    private void syncWidgetPrefs() {
        Set<String> ids = new HashSet<>();
        for (WidgetHolder wh : widgets) ids.add(String.valueOf(wh.id));
        prefs.setWidgetIds(ids);
    }

    private int widgetSpanX(AppWidgetProviderInfo info) {
        return Math.min(Math.max(info.minWidth, 1), 4);
    }

    private int widgetSpanY(AppWidgetProviderInfo info) {
        return Math.min(Math.max(info.minHeight, 1), 3);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reloadApps(); }
    };

    private final BroadcastReceiver wallpaperReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadWallpaper(); }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(invalidateGrid);
        try { unregisterReceiver(packageReceiver); } catch (Throwable ignored) {}
        try { unregisterReceiver(wallpaperReceiver); } catch (Throwable ignored) {}
        executor.shutdownNow();
    }
}
