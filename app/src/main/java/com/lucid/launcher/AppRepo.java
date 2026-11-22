package com.lucid.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.util.LruCache;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppRepo {
    private final Context ctx;
    private final int gridIconPx;
    private final int dockIconPx;
    private final LruCache<String, Bitmap> cache = new LruCache<>(96);

    public AppRepo(Context ctx, int gridIconPx, int dockIconPx) {
        this.ctx = ctx;
        this.gridIconPx = gridIconPx;
        this.dockIconPx = dockIconPx;
    }

    public int gridIconPx() { return gridIconPx; }
    public int dockIconPx() { return dockIconPx; }

    public List<AppEntry> allLaunchable() {
        PackageManager pm = ctx.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(i, 0);
        List<AppEntry> list = new ArrayList<>();
        String self = ctx.getPackageName();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            if (self.equals(pkg)) continue;
            if (!seen.add(pkg)) continue;
            list.add(new AppEntry(ri.loadLabel(pm).toString(), pkg, ri.activityInfo.name, ri.loadIcon(pm), null));
        }
        Collator c = Collator.getInstance();
        c.setStrength(Collator.PRIMARY);
        Collections.sort(list, (a, b) -> c.compare(a.label, b.label));
        return list;
    }

    public List<AppEntry> resolveDock() {
        PackageManager pm = ctx.getPackageManager();
        List<AppEntry> dock = new ArrayList<>();
        addDock(dock, pm, new Intent(Intent.ACTION_DIAL));
        addDock(dock, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
        Intent cam = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        if (pm.resolveActivity(cam, 0) == null) cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        addDock(dock, pm, cam);
        addDock(dock, pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER));
        return dock;
    }

    private void addDock(List<AppEntry> dock, PackageManager pm, Intent intent) {
        ResolveInfo ri = pm.resolveActivity(intent, 0);
        if (ri == null || ri.activityInfo == null) return;
        String pkg = ri.activityInfo.packageName;
        String self = ctx.getPackageName();
        if (self.equals(pkg)) return;
        for (AppEntry e : dock) if (e.packageName.equals(pkg)) return;
        dock.add(new AppEntry(ri.loadLabel(pm).toString(), pkg, ri.activityInfo.name, ri.loadIcon(pm), intent));
    }

    public Bitmap iconFor(AppEntry e, int px) {
        String key = e.packageName + "#" + px;
        Bitmap b = cache.get(key);
        if (b != null) return b;
        Drawable d = e.icon;
        if (d == null) return null;
        b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, px, px);
        d.draw(c);
        cache.put(key, b);
        return b;
    }
}
