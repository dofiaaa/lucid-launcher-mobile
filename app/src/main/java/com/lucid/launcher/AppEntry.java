package com.lucid.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class AppEntry {
    public final String label;
    public final String packageName;
    public final String activityName;
    public final Drawable icon;
    public final Intent roleIntent;
    public Bitmap iconBitmap;

    public AppEntry(String label, String packageName, String activityName, Drawable icon, Intent roleIntent) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
        this.roleIntent = roleIntent;
    }

    public Intent launchIntent() {
        if (roleIntent != null) {
            Intent i = new Intent(roleIntent);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setClassName(packageName, activityName);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
}
