package com.example.walletkeep_demo.model;

/**
 * 账单收支分类实体。
 */
public class Category {
    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;

    private long id;
    private String name;
    private int type;
    private String icon;
    private boolean isDefault;

    public Category() {
    }

    public Category(long id, String name, int type, String icon, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.icon = icon;
        this.isDefault = isDefault;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
