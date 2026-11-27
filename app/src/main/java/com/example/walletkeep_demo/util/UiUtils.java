package com.example.walletkeep_demo.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 通用 UI 工具：dp 换算、ScrollView 内列表/网格测高、预算进度条着色与日期金额格式化。
 */
public final class UiUtils {
    private static final int GRID_SPACING_DP = 8;

    private UiUtils() {
    }

    public static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static void applyBudgetProgressColors(ProgressBar progressBar, int progressColor) {
        Drawable drawable = progressBar.getProgressDrawable();
        if (drawable == null) {
            return;
        }
        Drawable mutated = drawable.mutate();
        if (mutated instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) mutated;
            Drawable bgLayer = layers.findDrawableByLayerId(android.R.id.background);
            if (bgLayer != null) {
                bgLayer.setColorFilter(0x4DFFFFFF, PorterDuff.Mode.SRC_IN);
            }
            Drawable progressLayer = layers.findDrawableByLayerId(android.R.id.progress);
            if (progressLayer != null) {
                progressLayer.setColorFilter(progressColor, PorterDuff.Mode.SRC_IN);
            }
        } else {
            mutated.setColorFilter(progressColor, PorterDuff.Mode.SRC_IN);
        }
        progressBar.setProgressDrawable(mutated);
    }

    /**
     * 在 ScrollView 中测量 ListView 总高度（原生 ListView 无法直接 wrap_content）。
     */
    public static void setListViewHeightBasedOnChildren(final ListView listView) {
        ListAdapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        int count = adapter.getCount();
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        if (count == 0) {
            params.height = 0;
            listView.setLayoutParams(params);
            return;
        }

        final int listWidth = listView.getWidth();
        int widthSpec;
        if (listWidth > 0) {
            int contentWidth = Math.max(1, listWidth - listView.getPaddingLeft() - listView.getPaddingRight());
            widthSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY);
        } else {
            widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        int totalHeight = listView.getPaddingTop() + listView.getPaddingBottom();
        for (int i = 0; i < count; i++) {
            View itemView = adapter.getView(i, null, listView);
            if (itemView.getLayoutParams() == null) {
                itemView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
            itemView.measure(widthSpec, heightSpec);
            totalHeight += itemView.getMeasuredHeight();
        }
        if (count > 1) {
            totalHeight += listView.getDividerHeight() * (count - 1);
        }

        params.height = totalHeight;
        listView.setLayoutParams(params);

        if (listWidth == 0) {
            listView.post(new Runnable() {
                @Override
                public void run() {
                    if (!isDetached(listView) && listView.getWidth() > 0) {
                        setListViewHeightBasedOnChildren(listView);
                    }
                }
            });
        }
    }

    /**
     * 在 ScrollView 中测量 GridView 总高度（按行取最高项）。
     */
    public static void setGridViewHeightBasedOnChildren(final GridView gridView, final int columns) {
        ListAdapter adapter = gridView.getAdapter();
        if (adapter == null || columns <= 0) {
            return;
        }
        int count = adapter.getCount();
        ViewGroup.LayoutParams params = gridView.getLayoutParams();
        if (count == 0) {
            params.height = 0;
            gridView.setLayoutParams(params);
            return;
        }

        Context context = gridView.getContext();
        final int gridWidth = gridView.getWidth();
        int hSpacing = dpToPx(context, GRID_SPACING_DP);
        int vSpacing = dpToPx(context, GRID_SPACING_DP);

        int itemWidthSpec;
        if (gridWidth > 0) {
            int contentWidth = gridWidth - gridView.getPaddingLeft() - gridView.getPaddingRight()
                    - hSpacing * Math.max(0, columns - 1);
            int colWidth = Math.max(1, contentWidth / columns);
            itemWidthSpec = View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY);
        } else {
            itemWidthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        int itemHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        int rows = (count + columns - 1) / columns;
        int totalHeight = gridView.getPaddingTop() + gridView.getPaddingBottom();
        for (int row = 0; row < rows; row++) {
            int rowMaxHeight = 0;
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= count) {
                    break;
                }
                View itemView = adapter.getView(index, null, gridView);
                itemView.measure(itemWidthSpec, itemHeightSpec);
                int measuredHeight = itemView.getMeasuredHeight();
                if (measuredHeight > rowMaxHeight) {
                    rowMaxHeight = measuredHeight;
                }
            }
            totalHeight += rowMaxHeight;
        }
        if (rows > 1) {
            totalHeight += vSpacing * (rows - 1);
        }

        params.height = totalHeight;
        gridView.setLayoutParams(params);

        if (gridWidth == 0) {
            gridView.post(new Runnable() {
                @Override
                public void run() {
                    if (!isDetached(gridView) && gridView.getWidth() > 0) {
                        setGridViewHeightBasedOnChildren(gridView, columns);
                    }
                }
            });
        }
    }

    public static String formatMonthLabel(String yyyyMM) {
        if (yyyyMM == null || yyyyMM.trim().isEmpty()) {
            return "";
        }
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM", Locale.CHINA);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy年MM月", Locale.CHINA);
        try {
            Date date = parser.parse(yyyyMM.trim());
            if (date != null) {
                return formatter.format(date);
            }
        } catch (ParseException ignored) {
        }
        return yyyyMM;
    }

    public static String formatBalanceAmount(String currency, double balance) {
        if (balance < 0.0) {
            return "-" + DateAmountUtils.formatAmount(currency, Math.abs(balance));
        }
        return DateAmountUtils.formatAmount(currency, balance);
    }

    private static boolean isDetached(View view) {
        Context context = view.getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return activity.isFinishing() || activity.isDestroyed();
        }
        return false;
    }
}
