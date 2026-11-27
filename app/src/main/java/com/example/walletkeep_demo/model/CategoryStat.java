package com.example.walletkeep_demo.model;

import com.example.walletkeep_demo.util.StringUtils;

/**
 * 分类统计聚合实体。
 */
public class CategoryStat {
    private long categoryId;
    private String categoryName;
    private String categoryIcon;
    private int billCount;
    private double totalAmount;
    private double percentage;

    public CategoryStat(long categoryId, String categoryName, String categoryIcon,
                        int billCount, double totalAmount, double percentage) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryIcon = categoryIcon;
        this.billCount = billCount;
        this.totalAmount = totalAmount;
        this.percentage = percentage;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryIcon() {
        return categoryIcon;
    }

    public int getBillCount() {
        return billCount;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    /**
     * 分类名可能为 null 或空白；统一在此兜底，避免各页面重复判断。
     */
    public String getCategoryNameOr(String fallback) {
        if (categoryName == null) {
            return fallback;
        }
        String trimmed = categoryName.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public String getCategoryIconOr(String fallback) {
        if (categoryIcon != null) {
            String trimmed = categoryIcon.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return StringUtils.firstCodePointOr(categoryName, fallback);
    }
}
