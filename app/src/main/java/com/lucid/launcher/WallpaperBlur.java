package com.lucid.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

public final class WallpaperBlur {
    private WallpaperBlur() {}

    public static Bitmap create(Context ctx) {
        try {
            Drawable d = WallpaperManager.getInstance(ctx).getDrawable();
            if (d == null) return null;
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            if (w <= 0 || h <= 0) return null;
            int maxDim = 320;
            float scale = Math.min(1f, maxDim / (float) Math.max(w, h));
            int tw = Math.max(1, (int) (w * scale));
            int th = Math.max(1, (int) (h * scale));
            Bitmap small = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            d.setBounds(0, 0, tw, th);
            d.draw(c);
            return StackBlur.blur(small, 6);
        } catch (Throwable t) {
            return null;
        }
    }
}
