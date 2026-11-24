package com.example.walletkeep_demo.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.example.walletkeep_demo.db.BillDao;
import com.example.walletkeep_demo.db.CategoryDao;
import com.example.walletkeep_demo.model.Bill;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 应用私有目录文件存储管理器（CSV 备份导出/导入与凭证图片管理）。
 */
public final class FileStorageManager {
    public static final String DIR_EXPORTS = "exports";
    public static final String DIR_RECEIPTS = "receipts";

    private static final int COPY_BUFFER_SIZE = 8192;

    private FileStorageManager() {
    }

    public static File getExportsDir(Context context) {
        return ensureSubDir(context, DIR_EXPORTS);
    }

    public static File getReceiptsDir(Context context) {
        return ensureSubDir(context, DIR_RECEIPTS);
    }

    public static File exportBillsToCsv(Context context, List<Bill> bills) throws IOException {
        File exportsDir = getExportsDir(context);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        File file = new File(exportsDir, "bill_backup_" + timestamp + ".csv");
        FileOutputStream fos = null;
        Writer writer = null;
        try {
            fos = new FileOutputStream(file);
            writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(CsvCodec.formatBills(bills));
            writer.flush();
            return file;
        } finally {
            if (writer != null) {
                closeQuietly(writer);
            } else {
                closeQuietly(fos);
            }
        }
    }

    public static List<File> listBackupFiles(Context context) {
        List<File> result = new ArrayList<File>();
        File exportsDir = getExportsDir(context);
        File[] files = exportsDir.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".csv")) {
                result.add(file);
            }
        }
        Collections.sort(result, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                long t1 = f1.lastModified();
                long t2 = f2.lastModified();
                if (t2 > t1) {
                    return 1;
                }
                if (t2 < t1) {
                    return -1;
                }
                return f2.getName().compareTo(f1.getName());
            }
        });
        return result;
    }

    public static String readFileText(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("文件不存在");
        }
        FileInputStream fis = null;
        BufferedReader reader = null;
        try {
            fis = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    sb.append('\n');
                }
                sb.append(line);
                first = false;
            }
            return sb.toString();
        } finally {
            if (reader != null) {
                closeQuietly(reader);
            } else {
                closeQuietly(fis);
            }
        }
    }

    public static int importBillsFromCsv(Context context, File csvFile, BillDao billDao, CategoryDao categoryDao)
            throws IOException {
        if (billDao == null) {
            throw new IllegalArgumentException("BillDao must not be null");
        }
        String text = readFileText(csvFile);
        List<Bill> bills = CsvCodec.parseBills(text);
        CategoryDao resolver = categoryDao != null
                ? categoryDao
                : (context != null ? new CategoryDao(context) : null);
        return billDao.insertBillsInTransaction(bills, resolver);
    }

    public static String copyReceiptImage(Context context, Uri sourceUri) throws IOException {
        if (context == null || sourceUri == null) {
            throw new IOException("无法读取选中的图片");
        }
        InputStream in = context.getContentResolver().openInputStream(sourceUri);
        if (in == null) {
            throw new IOException("无法读取选中的图片");
        }
        File destFile = new File(getReceiptsDir(context), "IMG_" + System.currentTimeMillis() + ".jpg");
        OutputStream out = null;
        boolean success = false;
        try {
            out = new FileOutputStream(destFile);
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            success = true;
            return destFile.getAbsolutePath();
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (!success && destFile.exists()) {
                destFile.delete();
            }
        }
    }

    public static Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (OutOfMemoryError e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean deleteReceiptFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        return file.delete();
    }

    public static int clearAllReceipts(Context context) {
        File receiptsDir = getReceiptsDir(context);
        File[] files = receiptsDir.listFiles();
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (file != null && file.isFile() && file.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    private static File ensureSubDir(Context context, String dirName) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        File dir = new File(context.getFilesDir(), dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (reqHeight > 0 && reqWidth > 0 && (height > reqHeight || width > reqWidth)) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
