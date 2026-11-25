package com.example.walletkeep_demo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.walletkeep_demo.model.Category;

/**
 * 记账本 SQLite 数据库帮助类（线程安全单例，开启外键约束并初始化默认分类）。
 */
public class AccountBookDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "account_book.db";
    public static final int DB_VERSION = 1;
    public static final String TABLE_CATEGORY = "category";
    public static final String TABLE_BILL = "bill";

    private static volatile AccountBookDbHelper instance;

    private AccountBookDbHelper(Context context) {
        this(context, DB_NAME);
    }

    /**
     * 允许设备端测试注入独立数据库名，避免读写目标应用的真实账本数据库。
     */
    public AccountBookDbHelper(Context context, String dbName) {
        super(
                context.getApplicationContext() != null ? context.getApplicationContext() : context,
                (dbName == null || dbName.trim().isEmpty()) ? DB_NAME : dbName.trim(),
                null,
                DB_VERSION
        );
    }

    public static AccountBookDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (AccountBookDbHelper.class) {
                if (instance == null) {
                    instance = new AccountBookDbHelper(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CATEGORY + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "type INTEGER NOT NULL, "
                + "icon TEXT, "
                + "is_default INTEGER DEFAULT 0"
                + ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BILL + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "amount REAL NOT NULL, "
                + "type INTEGER NOT NULL, "
                + "category_id INTEGER NOT NULL, "
                + "note TEXT, "
                + "bill_date TEXT NOT NULL, "
                + "receipt_path TEXT, "
                + "created_at TEXT NOT NULL, "
                + "FOREIGN KEY(category_id) REFERENCES " + TABLE_CATEGORY + "(id)"
                + ")");
        seedDefaultCategories(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 无损增量升级框架：禁止 DROP TABLE 清空用户历史账单与自定义分类
        if (oldVersion < 1) {
            onCreate(db);
        }
        // 后续若 DB_VERSION 升级到 2+，在此按版本号顺序执行 ALTER TABLE / 增量建表迁移
    }

    private void seedDefaultCategories(SQLiteDatabase db) {
        String[][] expenseDefaults = new String[][]{
                {"餐饮", "餐"},
                {"交通", "交"},
                {"购物", "购"},
                {"学习", "学"},
                {"娱乐", "娱"},
                {"医疗", "医"},
                {"居家", "居"},
                {"其他支出", "支"}
        };
        for (String[] item : expenseDefaults) {
            insertDefaultCategory(db, item[0], Category.TYPE_EXPENSE, item[1]);
        }

        String[][] incomeDefaults = new String[][]{
                {"工资", "薪"},
                {"奖金", "奖"},
                {"兼职", "兼"},
                {"理财", "财"},
                {"生活费", "费"},
                {"其他收入", "收"}
        };
        for (String[] item : incomeDefaults) {
            insertDefaultCategory(db, item[0], Category.TYPE_INCOME, item[1]);
        }
    }

    private void insertDefaultCategory(SQLiteDatabase db, String name, int type, String icon) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("icon", icon);
        values.put("is_default", 1);
        db.insert(TABLE_CATEGORY, null, values);
    }
}
