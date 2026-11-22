package com.lucid.launcher;

import android.graphics.Bitmap;

public final class StackBlur {
    private StackBlur() {}

    public static Bitmap blur(Bitmap src, int radius) {
        if (src == null || radius < 1) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            box(px, w, h, radius);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static void box(int[] src, int w, int h, int r) {
        int[] tmp = src.clone();
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int ca = 0, cr = 0, cg = 0, cb = 0, cnt = 0;
                for (int dx = -r; dx <= r; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= w) continue;
                    int p = tmp[row + xx];
                    ca += (p >>> 24) & 0xFF;
                    cr += (p >>> 16) & 0xFF;
                    cg += (p >>> 8) & 0xFF;
                    cb += p & 0xFF;
                    cnt++;
                }
                src[row + x] = (clamp(ca / cnt) << 24) | (clamp(cr / cnt) << 16)
                        | (clamp(cg / cnt) << 8) | clamp(cb / cnt);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ca = 0, cr = 0, cg = 0, cb = 0, cnt = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    int p = src[yy * w + x];
                    ca += (p >>> 24) & 0xFF;
                    cr += (p >>> 16) & 0xFF;
                    cg += (p >>> 8) & 0xFF;
                    cb += p & 0xFF;
                    cnt++;
                }
                tmp[y * w + x] = (clamp(ca / cnt) << 24) | (clamp(cr / cnt) << 16)
                        | (clamp(cg / cnt) << 8) | clamp(cb / cnt);
            }
        }
        System.arraycopy(tmp, 0, src, 0, src.length);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
