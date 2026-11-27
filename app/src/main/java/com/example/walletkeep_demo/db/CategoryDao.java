package com.example.walletkeep_demo.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.walletkeep_demo.model.Category;
import com.example.walletkeep_demo.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类数据访问对象（DAO）。
 */
public class CategoryDao {
    public static final int DELETE_OK = 0;
    public static final int DELETE_ERR_DEFAULT = 1;
    public static final int DELETE_ERR_IN_USE = 2;
    public static final int DELETE_ERR_NOT_FOUND = 3;

    private final AccountBookDbHelper dbHelper;

    public CategoryDao(Context context) {
        this(AccountBookDbHelper.getInstance(context));
    }

    public CategoryDao(AccountBookDbHelper dbHelper) {
        if (dbHelper == null) {
            throw new IllegalArgumentException("AccountBookDbHelper must not be null");
        }
        this.dbHelper = dbHelper;
    }

    public List<Category> getByType(int type) {
        List<Category> list = new ArrayList<Category>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    AccountBookDbHelper.TABLE_CATEGORY,
                    null,
                    "type = ?",
                    new String[]{String.valueOf(type)},
                    null,
                    null,
                    "is_default DESC, id ASC"
            );
            while (cursor.moveToNext()) {
                list.add(fromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public List<Category> getAll() {
        List<Category> list = new ArrayList<Category>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    AccountBookDbHelper.TABLE_CATEGORY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "type ASC, is_default DESC, id ASC"
            );
            while (cursor.moveToNext()) {
                list.add(fromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public Category getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    AccountBookDbHelper.TABLE_CATEGORY,
                    null,
                    "id = ?",
                    new String[]{String.valueOf(id)},
                    null,
                    null,
                    null
            );
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public Category findByNameAndType(String name, int type) {
        return findByNameAndType(dbHelper.getReadableDatabase(), name, type);
    }

    public long findOrCreateCategory(SQLiteDatabase db, String name, int type) {
        String trimmed = (name == null) ? "" : name.trim();
        if (trimmed.isEmpty()) {
            trimmed = (type == Category.TYPE_INCOME) ? "其他收入" : "其他支出";
        }
        Category existing = findByNameAndType(db, trimmed, type);
        if (existing != null) {
            return existing.getId();
        }
        ContentValues values = new ContentValues();
        values.put("name", trimmed);
        values.put("type", type);
        values.put("icon", StringUtils.firstCodePointOr(trimmed, "类"));
        values.put("is_default", 0);
        return db.insert(AccountBookDbHelper.TABLE_CATEGORY, null, values);
    }

    public long addCustomCategory(String name, int type) {
        if (name == null) {
            return -1;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        if (findByNameAndType(trimmed, type) != null) {
            return -1;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", trimmed);
        values.put("type", type);
        values.put("icon", StringUtils.firstCodePointOr(trimmed, "类"));
        values.put("is_default", 0);
        return db.insert(AccountBookDbHelper.TABLE_CATEGORY, null, values);
    }

    public int deleteCustomCategory(long categoryId) {
        Category category = getById(categoryId);
        if (category == null) {
            return DELETE_ERR_NOT_FOUND;
        }
        if (category.isDefault()) {
            return DELETE_ERR_DEFAULT;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + AccountBookDbHelper.TABLE_BILL + " WHERE category_id = ?",
                    new String[]{String.valueOf(categoryId)}
            );
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                return DELETE_ERR_IN_USE;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        try {
            int deleted = db.delete(
                    AccountBookDbHelper.TABLE_CATEGORY,
                    "id = ?",
                    new String[]{String.valueOf(categoryId)}
            );
            return deleted > 0 ? DELETE_OK : DELETE_ERR_NOT_FOUND;
        } catch (SQLiteConstraintException e) {
            // count 与 delete 之间被并发插入了引用该分类的账单时，外键会拒绝删除
            return DELETE_ERR_IN_USE;
        }
    }

    private Category findByNameAndType(SQLiteDatabase db, String name, int type) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = db.query(
                    AccountBookDbHelper.TABLE_CATEGORY,
                    null,
                    "name = ? AND type = ?",
                    new String[]{trimmed, String.valueOf(type)},
                    null,
                    null,
                    null
            );
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Category fromCursor(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        int type = cursor.getInt(cursor.getColumnIndexOrThrow("type"));
        String icon = cursor.getString(cursor.getColumnIndexOrThrow("icon"));
        boolean isDefault = cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1;
        return new Category(id, name, type, icon, isDefault);
    }
}
