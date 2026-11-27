package com.example.walletkeep_demo.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 应用私有目录文件存储管理器（CSV 备份导出/导入与凭证图片快照管理）。
 */
public final class FileStorageManager {
    public static final String DIR_EXPORTS = "exports";
    public static final String DIR_RECEIPTS = "receipts";

    private static final String TAG = "FileStorageManager";
    private static final String BACKUP_PREFIX = "bill_backup_";
    private static final String BACKUP_SUFFIX = ".csv";
    private static final String RECEIPT_SNAPSHOT_PREFIX = "receipts_";
    private static final int COPY_BUFFER_SIZE = 8192;

    /** 单个备份 CSV 最大 10 MB，防止一次性读入导致 OOM。 */
    public static final long MAX_BACKUP_BYTES = 10L * 1024 * 1024;
    /** 单张票据图片最大 25 MB，防止恶意 ContentProvider 占满磁盘。 */
    public static final long MAX_RECEIPT_BYTES = 25L * 1024 * 1024;
    /** CSV 预览最多读取 64 KB。 */
    public static final int PREVIEW_MAX_BYTES = 64 * 1024;

    public static final class ExportResult {
        public final File csvFile;
        public final int backedUpReceipts;

        public ExportResult(File csvFile, int backedUpReceipts) {
            this.csvFile = csvFile;
            this.backedUpReceipts = backedUpReceipts;
        }
    }

    public static final class RestoreResult {
        public final int importedCount;
        public final int skippedCount;
        public final int updatedReceiptCount;
        public final int restoredReceipts;
        public final int missingReceipts;

        public RestoreResult(int importedCount, int skippedCount, int updatedReceiptCount,
                             int restoredReceipts, int missingReceipts) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
            this.updatedReceiptCount = updatedReceiptCount;
            this.restoredReceipts = restoredReceipts;
            this.missingReceipts = missingReceipts;
        }
    }

    private FileStorageManager() {
    }

    public static File getExportsDir(Context context) {
        return ensureSubDir(context, DIR_EXPORTS);
    }

    public static File getReceiptsDir(Context context) {
        return ensureSubDir(context, DIR_RECEIPTS);
    }

    public static File exportBillsToCsv(Context context, List<Bill> bills) throws IOException {
        return exportBillsBackup(context, bills).csvFile;
    }

    public static ExportResult exportBillsBackup(Context context, List<Bill> bills) throws IOException {
        File exportsDir = getExportsDir(context);
        String base = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.CHINA).format(new Date());
        File file;
        File snapshotDir;
        int attempt = 0;
        do {
            String suffix = (attempt == 0) ? base : base + "_" + attempt;
            file = new File(exportsDir, BACKUP_PREFIX + suffix + BACKUP_SUFFIX);
            snapshotDir = new File(exportsDir, RECEIPT_SNAPSHOT_PREFIX + suffix);
            attempt++;
        } while (file.exists() || snapshotDir.exists());

        File tempFile = new File(exportsDir, file.getName() + ".tmp");
        int backedUpReceipts = 0;
        boolean success = false;
        try {
            FileOutputStream fos = null;
            Writer writer = null;
            try {
                fos = new FileOutputStream(tempFile);
                writer = new OutputStreamWriter(fos, "UTF-8");
                writer.write(CsvCodec.formatBills(bills));
                writer.flush();
                fos.getFD().sync();
            } finally {
                if (writer != null) {
                    closeQuietly(writer);
                } else {
                    closeQuietly(fos);
                }
            }

            if (bills != null) {
                Set<String> copiedNames = new HashSet<String>();
                for (Bill bill : bills) {
                    if (bill == null || bill.getReceiptPath() == null) {
                        continue;
                    }
                    String path = bill.getReceiptPath().trim();
                    if (!isReceiptPathSafe(context, path)) {
                        continue;
                    }
                    File src = new File(path);
                    if (!src.isFile()) {
                        continue;
                    }
                    String name = src.getName();
                    if (copiedNames.contains(name)) {
                        continue;
                    }
                    if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
                        throw new IOException("无法创建票据备份目录");
                    }
                    copyFile(src, new File(snapshotDir, name));
                    copiedNames.add(name);
                    backedUpReceipts++;
                }
            }

            // 所有内容写完后才原子重命名，避免半截 CSV 出现在备份列表中
            if (!tempFile.renameTo(file)) {
                throw new IOException("无法完成备份文件写入");
            }
            success = true;
            return new ExportResult(file, backedUpReceipts);
        } finally {
            if (!success) {
                if (tempFile.exists() && !tempFile.delete()) {
                    Log.w(TAG, "Failed to delete temp backup: " + tempFile.getAbsolutePath());
                }
                deleteRecursively(snapshotDir);
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
        if (file.length() > MAX_BACKUP_BYTES) {
            throw new IOException("备份文件超过 10 MB 限制");
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

    /**
     * 预览用：最多读取 maxChars 个字符，避免超大文件撑爆 Dialog 的 TextView。
     */
    public static String readFileTextLimited(File file, int maxChars) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("文件不存在");
        }
        FileInputStream fis = null;
        BufferedReader reader = null;
        try {
            fis = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder(Math.min(maxChars, 8192));
            int c;
            while (sb.length() < maxChars && (c = reader.read()) != -1) {
                sb.append((char) c);
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
        return importBillsFromCsv(context, csvFile, billDao, categoryDao, BillDao.IMPORT_MODE_REPLACE).importedCount;
    }

    public static RestoreResult importBillsFromCsv(
            Context context,
            File csvFile,
            BillDao billDao,
            CategoryDao categoryDao,
            int importMode
    ) throws IOException {
        if (billDao == null) {
            throw new IllegalArgumentException("BillDao must not be null");
        }
        String text = readFileText(csvFile);
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("备份文件为空，已拒绝恢复操作");
        }
        List<Bill> bills = CsvCodec.parseBills(text);
        if (bills.isEmpty()) {
            throw new IllegalArgumentException("备份文件中不包含有效账单记录，已拒绝恢复操作");
        }
        CategoryDao resolver = categoryDao != null
                ? categoryDao
                : (context != null ? new CategoryDao(context) : null);

        File snapshotDir = resolveReceiptSnapshotDir(csvFile);
        File receiptsDir = context != null ? getReceiptsDir(context) : null;
        List<File> newlyRestoredFiles = new ArrayList<File>();
        Set<String> snapshotRestoredPaths = new HashSet<String>();
        int missingReceipts = 0;

        try {
            for (Bill bill : bills) {
                if (bill == null || bill.getReceiptPath() == null) {
                    continue;
                }
                String rawPath = bill.getReceiptPath().trim();
                if (rawPath.isEmpty()) {
                    bill.setReceiptPath("");
                    continue;
                }
                File existingFile = new File(rawPath);
                if (isReceiptPathSafe(context, rawPath) && existingFile.isFile()) {
                    continue;
                }
                String fileName = existingFile.getName();
                File snapshotFile = (snapshotDir != null && !fileName.isEmpty())
                        ? new File(snapshotDir, fileName)
                        : null;
                if (snapshotFile != null && snapshotFile.isFile() && receiptsDir != null) {
                    File restoredDest = new File(receiptsDir, fileName);
                    if (restoredDest.isFile() && !sameContent(snapshotFile, restoredDest)) {
                        // 同名但内容不同：生成唯一文件名，避免把备份图片错误关联到别的账单图片
                        restoredDest = uniqueFile(receiptsDir, fileName);
                    }
                    if (!restoredDest.isFile()) {
                        copyFile(snapshotFile, restoredDest);
                        newlyRestoredFiles.add(restoredDest);
                    }
                    String restoredPath = restoredDest.getAbsolutePath();
                    bill.setReceiptPath(restoredPath);
                    snapshotRestoredPaths.add(restoredPath);
                } else {
                    bill.setReceiptPath("");
                    missingReceipts++;
                }
            }

            BillDao.ImportResult dbResult = billDao.importBillsInTransaction(bills, resolver, importMode);

            Set<String> normalizedAttached = new HashSet<String>();
            for (String attached : dbResult.attachedReceiptPaths) {
                normalizedAttached.add(normalizePath(attached));
            }

            // 清理本次新建但未被任何已入库/已保留/已补齐账单引用的票据文件，避免磁盘残留孤立图片
            for (File restoredFile : newlyRestoredFiles) {
                if (restoredFile != null
                        && restoredFile.exists()
                        && !normalizedAttached.contains(normalizePath(restoredFile.getAbsolutePath()))) {
                    restoredFile.delete();
                }
            }

            // 仅统计实际成功关联到账单的恢复票据数量
            int actualRestoredReceipts = 0;
            for (String path : snapshotRestoredPaths) {
                if (normalizedAttached.contains(normalizePath(path))) {
                    actualRestoredReceipts++;
                }
            }

            if (importMode == BillDao.IMPORT_MODE_REPLACE && receiptsDir != null) {
                cleanupUnreferencedReceipts(receiptsDir, normalizedAttached);
            }
            return new RestoreResult(
                    dbResult.importedCount,
                    dbResult.skippedCount,
                    dbResult.updatedReceiptCount,
                    actualRestoredReceipts,
                    missingReceipts
            );
        } catch (IOException e) {
            rollbackRestoredFiles(newlyRestoredFiles);
            throw e;
        } catch (RuntimeException e) {
            rollbackRestoredFiles(newlyRestoredFiles);
            throw e;
        }
    }

    public static String copyReceiptImage(Context context, Uri sourceUri) throws IOException {
        if (context == null || sourceUri == null) {
            throw new IOException("无法读取选中的图片");
        }
        File receiptsDir = getReceiptsDir(context);
        File destFile = new File(receiptsDir, "IMG_" + System.currentTimeMillis() + ".jpg");
        InputStream in;
        try {
            in = context.getContentResolver().openInputStream(sourceUri);
        } catch (SecurityException e) {
            throw new IOException("无权限读取选中的图片", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("无法读取选中的图片", e);
        }
        if (in == null) {
            throw new IOException("无法读取选中的图片");
        }
        OutputStream out = null;
        boolean success = false;
        try {
            out = new FileOutputStream(destFile);
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int bytesRead;
            long totalBytes = 0L;
            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_RECEIPT_BYTES) {
                    throw new IOException("图片超过 25 MB 限制");
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("图片保存已取消");
                }
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

    /**
     * 只允许删除 receipts 目录内的文件，防止恶意 CSV 的 receipt_path 指向数据库/偏好设置。
     */
    public static boolean deleteReceiptFile(Context context, String path) {
        if (!isReceiptPathSafe(context, path)) {
            return false;
        }
        File file = new File(path.trim());
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        boolean deleted = file.delete();
        if (!deleted) {
            Log.w(TAG, "Failed to delete receipt file: " + path);
        }
        return deleted;
    }

    public static boolean isReceiptPathSafe(Context context, String path) {
        if (context == null || path == null || path.trim().isEmpty()) {
            return false;
        }
        try {
            File receiptsDir = getReceiptsDir(context).getCanonicalFile();
            File target = new File(path.trim()).getCanonicalFile();
            return target.getPath().startsWith(receiptsDir.getPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    public static int clearAllReceipts(Context context) {
        File receiptsDir = getReceiptsDir(context);
        File[] files = receiptsDir.listFiles();
        if (files == null) {
            return 0;
        }
        int deleted = 0;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            if (file.delete()) {
                deleted++;
            } else {
                Log.w(TAG, "Failed to delete receipt: " + file.getAbsolutePath());
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

    private static boolean sameContent(File first, File second) throws IOException {
        if (first.length() != second.length()) {
            return false;
        }
        InputStream in1 = null;
        InputStream in2 = null;
        try {
            in1 = new FileInputStream(first);
            in2 = new FileInputStream(second);
            byte[] buffer1 = new byte[COPY_BUFFER_SIZE];
            byte[] buffer2 = new byte[COPY_BUFFER_SIZE];
            int read1;
            while ((read1 = in1.read(buffer1)) != -1) {
                int read2 = 0;
                while (read2 < read1) {
                    int r = in2.read(buffer2, read2, read1 - read2);
                    if (r < 0) {
                        return false;
                    }
                    read2 += r;
                }
                for (int i = 0; i < read1; i++) {
                    if (buffer1[i] != buffer2[i]) {
                        return false;
                    }
                }
            }
            return in2.read() == -1;
        } finally {
            closeQuietly(in1);
            closeQuietly(in2);
        }
    }

    private static File uniqueFile(File dir, String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int index = 1;
        File candidate;
        do {
            candidate = new File(dir, base + "_" + index + ext);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private static File resolveReceiptSnapshotDir(File csvFile) {
        if (csvFile == null) {
            return null;
        }
        File parent = csvFile.getParentFile();
        String name = csvFile.getName();
        if (parent == null || name == null) {
            return null;
        }
        if (name.startsWith(BACKUP_PREFIX) && name.toLowerCase(Locale.US).endsWith(BACKUP_SUFFIX)) {
            String suffix = name.substring(BACKUP_PREFIX.length(), name.length() - BACKUP_SUFFIX.length());
            if (!suffix.isEmpty()) {
                File dir = new File(parent, RECEIPT_SNAPSHOT_PREFIX + suffix);
                if (dir.isDirectory()) {
                    return dir;
                }
            }
        }
        return null;
    }

    private static void copyFile(File src, File dst) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        boolean success = false;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            success = true;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (!success && dst.exists()) {
                dst.delete();
            }
        }
    }

    private static void cleanupUnreferencedReceipts(File receiptsDir, Set<String> normalizedReferencedPaths) {
        File[] files = receiptsDir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f != null && f.isFile() && !normalizedReferencedPaths.contains(normalizePath(f.getAbsolutePath()))) {
                f.delete();
            }
        }
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        File file = new File(path);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private static void rollbackRestoredFiles(List<File> files) {
        for (File f : files) {
            if (f != null && f.exists() && !f.delete()) {
                Log.w(TAG, "Failed to roll back restored file: " + f.getAbsolutePath());
            }
        }
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!target.delete()) {
            Log.w(TAG, "Failed to delete: " + target.getAbsolutePath());
        }
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
