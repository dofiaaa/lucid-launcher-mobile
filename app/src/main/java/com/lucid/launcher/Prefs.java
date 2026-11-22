package com.lucid.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class Prefs {
    private static final String NAME = "lucid_prefs";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public Set<String> hidden() {
        return sp.getStringSet("hidden", new HashSet<String>());
    }

    public void hide(String pkg) {
        Set<String> s = new HashSet<>(hidden());
        s.add(pkg);
        sp.edit().putStringSet("hidden", s).apply();
    }

    public void unhide(String pkg) {
        Set<String> s = new HashSet<>(hidden());
        s.remove(pkg);
        sp.edit().putStringSet("hidden", s).apply();
    }

    public Set<String> widgetIds() {
        return sp.getStringSet("widget_ids", new HashSet<String>());
    }

    public void setWidgetIds(Set<String> ids) {
        sp.edit().putStringSet("widget_ids", ids).apply();
    }

    public boolean glass() {
        return sp.getBoolean("glass", true);
    }

    public void setGlass(boolean g) {
        sp.edit().putBoolean("glass", g).apply();
    }
}
