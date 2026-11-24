package com.example.walletkeep_demo.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.walletkeep_demo.R;
import com.example.walletkeep_demo.model.CategoryStat;
import com.example.walletkeep_demo.util.DateAmountUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自定义 2D Canvas 分类统计图控件（兼容 API 17+）。
 * 顶部绘制 100% 堆叠比例条，下方绘制最多前 6 个分类的横向对比条。
 */
public class CategoryChartView extends View {

    private static final int MAX_BAR_COUNT = 6;
    private static final String EMPTY_TEXT = "暂无本月分类统计数据";
    private static final int[] PALETTE = new int[] {
            0xFF1E88E5,
            0xFFFB8C00,
            0xFF43A047,
            0xFFE53935,
            0xFF8E24AA,
            0xFF00ACC1
    };

    private final List<CategoryStat> stats = new ArrayList<>();
    private String currency = "¥";

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private final RectF trackRect = new RectF();
    private final RectF segmentRect = new RectF();
    private final RectF barRect = new RectF();

    // 预计算的渲染缓存，确保 onDraw 零对象分配
    private float[] stackedFractions = new float[0];
    private int[] stackedColors = new int[0];
    private int stackedActiveCount = 0;

    private String[] barRawNames = new String[0];
    private String[] barDisplayNames = new String[0];
    private String[] barValueTexts = new String[0];
    private float[] barRatios = new float[0];
    private int[] barColors = new int[0];
    private int barCount = 0;
    private float maxValueTextWidth = 0f;
    private float cachedContentWidth = -1f;

    private float density;
    private float scaledDensity;
    private float stackedBarHeightPx;
    private float gapAfterStackedPx;
    private float rowHeightPx;
    private float topPaddingDefaultPx;
    private float bottomPaddingDefaultPx;
    private float sidePaddingDefaultPx;
    private float emptyHeightPx;
    private float dotRadiusPx;
    private float stackedRadiusPx;
    private float rowBarHeightPx;

    public CategoryChartView(Context context) {
        super(context);
        init(context);
    }

    public CategoryChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CategoryChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;

        stackedBarHeightPx = dp(22);
        gapAfterStackedPx = dp(14);
        rowHeightPx = dp(34);
        topPaddingDefaultPx = dp(12);
        bottomPaddingDefaultPx = dp(8);
        sidePaddingDefaultPx = dp(12);
        emptyHeightPx = dp(120);
        dotRadiusPx = dp(4);
        stackedRadiusPx = dp(6);
        rowBarHeightPx = dp(10);

        barPaint.setStyle(Paint.Style.FILL);

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.bgTag));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.textPrimary));
        labelPaint.setTextSize(sp(12));
        labelPaint.setTextAlign(Paint.Align.LEFT);

        valuePaint.setColor(ContextCompat.getColor(context, R.color.textSecondary));
        valuePaint.setTextSize(sp(11));
        valuePaint.setTextAlign(Paint.Align.RIGHT);

        emptyPaint.setColor(ContextCompat.getColor(context, R.color.textSecondary));
        emptyPaint.setTextSize(sp(13));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 更新统计数据与币种符号并重绘图表。
     */
    public void setStats(List<CategoryStat> newStats, String newCurrency) {
        this.stats.clear();
        if (newStats != null) {
            for (int i = 0; i < newStats.size(); i++) {
                CategoryStat item = newStats.get(i);
                if (item != null) {
                    this.stats.add(item);
                }
            }
        }
        this.currency = (newCurrency == null || newCurrency.trim().isEmpty()) ? "¥" : newCurrency.trim();
        rebuildRenderCache();
        requestLayout();
        invalidate();
    }

    private void rebuildRenderCache() {
        int n = stats.size();
        if (n == 0) {
            stackedActiveCount = 0;
            barCount = 0;
            maxValueTextWidth = 0f;
            return;
        }

        double totalAmountSum = 0.0;
        double totalPctSum = 0.0;
        for (int i = 0; i < n; i++) {
            CategoryStat s = stats.get(i);
            totalAmountSum += Math.max(0.0, s.getTotalAmount());
            totalPctSum += Math.max(0.0, s.getPercentage());
        }

        if (stackedFractions.length < n) {
            stackedFractions = new float[n];
            stackedColors = new int[n];
        }
        stackedActiveCount = 0;
        for (int i = 0; i < n; i++) {
            CategoryStat s = stats.get(i);
            double weight = totalAmountSum > 0.0
                    ? Math.max(0.0, s.getTotalAmount()) / totalAmountSum
                    : (totalPctSum > 0.0 ? Math.max(0.0, s.getPercentage()) / totalPctSum : 0.0);
            if (weight > 0.0) {
                stackedFractions[stackedActiveCount] = (float) weight;
                stackedColors[stackedActiveCount] = PALETTE[i % PALETTE.length];
                stackedActiveCount++;
            }
        }

        barCount = Math.min(n, MAX_BAR_COUNT);
        if (barRawNames.length < barCount) {
            barRawNames = new String[barCount];
            barDisplayNames = new String[barCount];
            barValueTexts = new String[barCount];
            barRatios = new float[barCount];
            barColors = new int[barCount];
        }

        double maxAmount = 0.0;
        for (int i = 0; i < barCount; i++) {
            double amt = Math.max(0.0, stats.get(i).getTotalAmount());
            if (amt > maxAmount) {
                maxAmount = amt;
            }
        }

        maxValueTextWidth = 0f;
        for (int i = 0; i < barCount; i++) {
            CategoryStat s = stats.get(i);
            String name = s.getCategoryName() != null ? s.getCategoryName().trim() : "";
            barRawNames[i] = name.isEmpty() ? "未分类" : name;

            double amt = Math.max(0.0, s.getTotalAmount());
            double pct = Math.max(0.0, Math.min(100.0, s.getPercentage()));
            barValueTexts[i] = String.format(
                    Locale.CHINA,
                    "%s (%.1f%%)",
                    DateAmountUtils.formatAmount(currency, amt),
                    pct
            );
            float w = valuePaint.measureText(barValueTexts[i]);
            if (w > maxValueTextWidth) {
                maxValueTextWidth = w;
            }

            barRatios[i] = maxAmount > 0.0 ? (float) (amt / maxAmount) : 0f;
            barColors[i] = PALETTE[i % PALETTE.length];
        }

        cachedContentWidth = -1f;
    }

    private void updateDisplayNamesForWidth(float contentWidth) {
        if (Math.abs(contentWidth - cachedContentWidth) < 0.5f) {
            return;
        }
        cachedContentWidth = contentWidth;
        float labelMaxWidth = computeLabelColumnWidth(contentWidth);
        for (int i = 0; i < barCount; i++) {
            barDisplayNames[i] = truncateLabel(barRawNames[i], labelMaxWidth);
        }
    }

    private String truncateLabel(String text, float maxWidth) {
        if (maxWidth <= 0f || labelPaint.measureText(text) <= maxWidth) {
            return text;
        }
        float ellipsisW = labelPaint.measureText("…");
        float avail = maxWidth - ellipsisW;
        if (avail <= 0f) {
            return "…";
        }
        int count = labelPaint.breakText(text, true, avail, null);
        return count > 0 ? text.substring(0, count) + "…" : "…";
    }

    private float computeLabelColumnWidth(float contentWidth) {
        return Math.min(dp(68), contentWidth * 0.24f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(dp(300)) + getPaddingLeft() + getPaddingRight();
        int desiredHeight;
        if (stats.isEmpty()) {
            desiredHeight = Math.round(emptyHeightPx);
        } else {
            int visibleBars = Math.min(stats.size(), MAX_BAR_COUNT);
            float topPad = Math.max(getPaddingTop(), topPaddingDefaultPx);
            float bottomPad = Math.max(getPaddingBottom(), bottomPaddingDefaultPx);
            desiredHeight = Math.round(
                    topPad + stackedBarHeightPx + gapAfterStackedPx + visibleBars * rowHeightPx + bottomPad
            );
        }
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        if (stats.isEmpty()) {
            emptyPaint.getFontMetrics(fontMetrics);
            float centerY = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(EMPTY_TEXT, width / 2f, centerY, emptyPaint);
            return;
        }

        float left = Math.max(getPaddingLeft(), sidePaddingDefaultPx);
        float right = width - Math.max(getPaddingRight(), sidePaddingDefaultPx);
        float contentWidth = right - left;
        if (contentWidth <= 0f) {
            return;
        }

        updateDisplayNamesForWidth(contentWidth);

        float top = Math.max(getPaddingTop(), topPaddingDefaultPx);
        drawStackedBar(canvas, left, top, right, top + stackedBarHeightPx, contentWidth);

        float rowsStartY = top + stackedBarHeightPx + gapAfterStackedPx;
        drawComparisonRows(canvas, left, right, rowsStartY, contentWidth);
    }

    private void drawStackedBar(Canvas canvas, float left, float top, float right, float bottom, float contentWidth) {
        trackRect.set(left, top, right, bottom);
        canvas.drawRoundRect(trackRect, stackedRadiusPx, stackedRadiusPx, trackPaint);

        if (stackedActiveCount <= 0) {
            return;
        }

        float curX = left;
        float cumulative = 0f;
        for (int i = 0; i < stackedActiveCount; i++) {
            cumulative += stackedFractions[i];
            float segRight = (i == stackedActiveCount - 1)
                    ? right
                    : Math.min(right, left + contentWidth * cumulative);
            if (segRight <= curX) {
                continue;
            }

            barPaint.setColor(stackedColors[i]);
            float segWidth = segRight - curX;

            if (stackedActiveCount == 1) {
                segmentRect.set(curX, top, segRight, bottom);
                float r = Math.min(stackedRadiusPx, segWidth / 2f);
                canvas.drawRoundRect(segmentRect, r, r, barPaint);
            } else if (i == 0) {
                segmentRect.set(curX, top, segRight, bottom);
                float r = Math.min(stackedRadiusPx, segWidth / 2f);
                canvas.drawRoundRect(segmentRect, r, r, barPaint);
                float flatLeft = Math.max(curX + segWidth / 2f, segRight - stackedRadiusPx);
                if (segRight > flatLeft) {
                    segmentRect.set(flatLeft, top, segRight, bottom);
                    canvas.drawRect(segmentRect, barPaint);
                }
            } else if (i == stackedActiveCount - 1) {
                segmentRect.set(curX, top, segRight, bottom);
                float r = Math.min(stackedRadiusPx, segWidth / 2f);
                canvas.drawRoundRect(segmentRect, r, r, barPaint);
                float flatRight = Math.min(segRight - segWidth / 2f, curX + stackedRadiusPx);
                if (flatRight > curX) {
                    segmentRect.set(curX, top, flatRight, bottom);
                    canvas.drawRect(segmentRect, barPaint);
                }
            } else {
                segmentRect.set(curX, top, segRight, bottom);
                canvas.drawRect(segmentRect, barPaint);
            }

            curX = segRight;
        }
    }

    private void drawComparisonRows(Canvas canvas, float left, float right, float startY, float contentWidth) {
        float dotCx = left + dotRadiusPx;
        float labelStartX = left + dp(14);
        float labelColW = computeLabelColumnWidth(contentWidth);
        float valueColW = Math.min(maxValueTextWidth, contentWidth * 0.42f);

        float barLeft = labelStartX + labelColW + dp(8);
        float barMaxRight = right - valueColW - dp(8);
        float barAreaW = barMaxRight - barLeft;
        float barRadius = rowBarHeightPx / 2f;

        labelPaint.getFontMetrics(fontMetrics);
        float labelBaselineOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f;
        valuePaint.getFontMetrics(fontMetrics);
        float valueBaselineOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f;

        for (int i = 0; i < barCount; i++) {
            float rowTop = startY + i * rowHeightPx;
            float rowCenterY = rowTop + rowHeightPx / 2f;

            // 1. 左侧色点 + 分类名称
            barPaint.setColor(barColors[i]);
            canvas.drawCircle(dotCx, rowCenterY, dotRadiusPx, barPaint);
            canvas.drawText(barDisplayNames[i], labelStartX, rowCenterY + labelBaselineOffset, labelPaint);

            // 2. 中间比例圆角横条
            if (barAreaW > dp(12)) {
                float bTop = rowCenterY - rowBarHeightPx / 2f;
                float bBottom = rowCenterY + rowBarHeightPx / 2f;
                barRect.set(barLeft, bTop, barMaxRight, bBottom);
                canvas.drawRoundRect(barRect, barRadius, barRadius, trackPaint);

                if (barRatios[i] > 0f) {
                    float fillW = Math.min(barAreaW, Math.max(dp(4), barAreaW * barRatios[i]));
                    barRect.set(barLeft, bTop, barLeft + fillW, bBottom);
                    float r = Math.min(barRadius, fillW / 2f);
                    canvas.drawRoundRect(barRect, r, r, barPaint);
                }
            }

            // 3. 右侧金额与百分比
            canvas.drawText(barValueTexts[i], right, rowCenterY + valueBaselineOffset, valuePaint);
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }
}
