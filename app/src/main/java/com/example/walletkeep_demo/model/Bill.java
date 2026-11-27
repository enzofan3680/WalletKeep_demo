package com.example.walletkeep_demo.model;

import com.example.walletkeep_demo.util.StringUtils;

/**
 * 账单明细实体。
 */
public class Bill {
    private long id;
    private double amount;
    private int type;
    private long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String note;
    private String billDate;
    private String receiptPath;
    private String createdAt;

    public Bill() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryIcon() {
        return categoryIcon;
    }

    public void setCategoryIcon(String categoryIcon) {
        this.categoryIcon = categoryIcon;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getBillDate() {
        return billDate;
    }

    public void setBillDate(String billDate) {
        this.billDate = billDate;
    }

    public String getReceiptPath() {
        return receiptPath;
    }

    public void setReceiptPath(String receiptPath) {
        this.receiptPath = receiptPath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 分类名来自 LEFT JOIN，可能为 null 或空白；统一在此兜底，避免各页面重复判断。
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
