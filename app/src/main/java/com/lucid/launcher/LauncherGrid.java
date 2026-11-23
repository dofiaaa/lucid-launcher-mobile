package com.lucid.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Сетка рабочего стола: иконки приложений (1x1 ячейка) и виджеты (NxM ячеек),
 * размещаются вперемешку и прокручиваются внутри ScrollView.
 */
public class LauncherGrid extends ViewGroup {

    public static class GridItem {
        public final View view;
        public final int spanX;
        public final int spanY;

        public GridItem(View view, int spanX, int spanY) {
            this.view = view;
            this.spanX = Math.max(1, spanX);
            this.spanY = Math.max(1, spanY);
        }
    }

    private static final int COLUMNS = 4;

    private float cellHeightPx;
    private float hSpacingPx;
    private float vSpacingPx;
    private float padLeftPx;
    private float padTopPx;
    private float padRightPx;
    private float padBottomPx;

    private final List<GridItem> items = new ArrayList<>();

    public LauncherGrid(Context context) {
        super(context);
        init(context);
    }

    public LauncherGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context ctx) {
        float d = ctx.getResources().getDisplayMetrics().density;
        cellHeightPx = 88f * d;
        hSpacingPx = 4f * d;
        vSpacingPx = 10f * d;
        padLeftPx = padRightPx = 14f * d;
        padTopPx = padBottomPx = 8f * d;
    }

    public void setItems(List<GridItem> list) {
        removeAllViews();
        items.clear();
        if (list != null) {
            items.addAll(list);
            for (GridItem gi : items) addView(gi.view);
        }
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float cellW = cellWidth(width);

        int row = 0;
        int col = 0;
        for (GridItem gi : items) {
            if (col > 0 && col + gi.spanX > COLUMNS) {
                row++;
                col = 0;
            }
            int itemW = Math.round(gi.spanX * cellW + (gi.spanX - 1) * hSpacingPx);
            int itemH = Math.round(gi.spanY * cellHeightPx + (gi.spanY - 1) * vSpacingPx);
            measureChild(gi.view,
                    MeasureSpec.makeMeasureSpec(itemW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(itemH, MeasureSpec.EXACTLY));
            col += gi.spanX;
        }

        int totalRows = items.isEmpty() ? 0 : row + 1;
        int totalH = Math.round(padTopPx + totalRows * cellHeightPx
                + Math.max(0, totalRows - 1) * vSpacingPx + padBottomPx);
        setMeasuredDimension(width, resolveSize(totalH, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float cellW = cellWidth(getWidth());
        int row = 0;
        int col = 0;
        for (GridItem gi : items) {
            if (col > 0 && col + gi.spanX > COLUMNS) {
                row++;
                col = 0;
            }
            int itemW = Math.round(gi.spanX * cellW + (gi.spanX - 1) * hSpacingPx);
            int itemH = Math.round(gi.spanY * cellHeightPx + (gi.spanY - 1) * vSpacingPx);
            int left = Math.round(padLeftPx + col * (cellW + hSpacingPx));
            int top = Math.round(padTopPx + row * (cellHeightPx + vSpacingPx));
            gi.view.layout(left, top, left + itemW, top + itemH);
            col += gi.spanX;
        }
    }

    private float cellWidth(int width) {
        float avail = width - padLeftPx - padRightPx - (COLUMNS - 1) * hSpacingPx;
        return Math.max(1f, avail / COLUMNS);
    }
}
