package com.lucid.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

public class AppGridAdapter extends BaseAdapter {
    private final Context ctx;
    private List<AppEntry> apps = new ArrayList<>();

    public AppGridAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setApps(List<AppEntry> a) {
        apps = a == null ? new ArrayList<AppEntry>() : a;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return apps.size(); }
    @Override public Object getItem(int p) { return apps.get(p); }
    @Override public long getItemId(int p) { return p; }

    @Override
    public View getView(int pos, View convert, ViewGroup parent) {
        AppCell cell;
        if (convert instanceof AppCell) cell = (AppCell) convert;
        else cell = new AppCell(ctx);
        AppEntry e = apps.get(pos);
        cell.bind(e.label, e.iconBitmap);
        cell.setTag(e);
        return cell;
    }
}
