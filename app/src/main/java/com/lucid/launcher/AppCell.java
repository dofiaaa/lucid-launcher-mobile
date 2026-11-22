package com.lucid.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

public class AppCell extends View {
    private final TextPaint labelPaint;
    private final Paint iconPaint;
    private final float iconSize;
    private final float gap;
    private Bitmap icon;
    private String label;

    public AppCell(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        iconSize = 52f * d;
        gap = 6f * d;
        labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        labelPaint.setColor(0xF2FFFFFF);
        labelPaint.setTextSize(11f * d);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setShadowLayer(1.2f * d, 0f, 1f * d, 0x66000000);
        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        setWillNotDraw(false);
    }

    public void bind(String label, Bitmap icon) {
        this.label = label;
        this.icon = icon;
        setContentDescription(label);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w <= 0) w = (int) (iconSize + gap * 2f);
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float labelHeight = fm.descent - fm.ascent;
        int h = (int) (gap + iconSize + gap + labelHeight + gap);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float alpha = isPressed() ? 0.55f : 1f;
        float top = gap;
        if (icon != null) {
            float left = cx - iconSize / 2f;
            RectF dst = new RectF(left, top, left + iconSize, top + iconSize);
            iconPaint.setAlpha((int) (255 * alpha));
            canvas.drawBitmap(icon, null, dst, iconPaint);
        }
        if (label != null && label.length() > 0) {
            CharSequence t = TextUtils.ellipsize(label, labelPaint, getWidth() - gap * 2f, TextUtils.TruncateAt.END);
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            float baseline = top + iconSize + gap - fm.ascent;
            labelPaint.setAlpha((int) (255 * alpha));
            canvas.drawText(t, 0, t.length(), cx, baseline, labelPaint);
        }
    }
}
