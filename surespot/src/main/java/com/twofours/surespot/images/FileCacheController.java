package com.twofours.surespot.images;

import android.content.Context;

import com.google.common.io.ByteStreams;
import com.jakewharton.disklrucache.DiskLruCache;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by adam on 4/28/16.
 * <p>
 * caches file data on disk to save http request for sent files
 */
public class FileCacheController {
    private static final String TAG = "FileCacheController";
    private DiskLruCache mCache;
    private File mCacheDir;


    public FileCacheController(Context context) throws IOException {
        mCacheDir = FileUtils.getFileCacheDir(context);
        SurespotLog.v(TAG, "file cache dir: %s", mCacheDir);
        mCache = DiskLruCache.open(mCacheDir, 100, 1, 50*1024*1024);
    }


    public InputStream getEntry(String key) throws IOException {
        SurespotLog.v(TAG, "getting file cache entry for url: " + key);
        try {
            DiskLruCache.Snapshot snapshot = null;

            String gkey = generateKey(key);
            snapshot = mCache.get(gkey);

            if (snapshot == null) {
                return null;
            }
            InputStream is = snapshot.getInputStream(0);
       //     snapshot.close();

            SurespotLog.v(TAG, "file cache entry exists for: %s, resource available: %d", key, is.available());
            return is;
        }
        catch (Exception e) {
            throw new IOException("Error retrieving cache entry: " + key,e );
        }
    }

    public void putEntry(String key, InputStream inputStream) throws IOException {
        try {

            String gKey = generateKey(key);

            InputStream existing = getEntry(key);
            if (existing != null) {
                SurespotLog.v(TAG, "putEntry: cache entry already exists for key: " + key);
                existing.close();
                inputStream.close();
                return;
            }

            SurespotLog.v(TAG, "putEntry: putting file cache entry, key: " + key);
            DiskLruCache.Editor edit = mCache.edit(gKey);
            if (edit != null) {
                OutputStream outputStream = edit.newOutputStream(0);
                ByteStreams.copy(inputStream, outputStream);
                inputStream.close();
                outputStream.close();
                edit.commit();
            }
        }
        catch (Exception e) {
            SurespotLog.w(TAG, e, "error putting file caching entry for key: %s", key);
        }

    }

    public void removeEntry(String arg0) throws IOException {
        SurespotLog.v(TAG, "removing cache entry, key: " + arg0);
        String gKey = generateKey(arg0);
        mCache.remove(gKey);
    }


    public void clearCache() {
        clearCache(mCacheDir);
    }

    public void close() {
        try {
            mCache.flush();
            // mCache.close();
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "close");
        }
    }

    /**
     * Removes all disk cache entries from the given directory.
     */
    private synchronized void clearCache(File cacheDir) {
        final File[] files = cacheDir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                files[i].delete();
            }
        }
    }

    private static String generateKey(String key) {
        return Utils.md5(key);
    }

    public void moveCacheEntry(String localUri, String remoteUri) {
        try {
            InputStream is = getEntry(localUri);
            if (is != null) {
                putEntry(remoteUri, is);
                removeEntry(localUri);
            }
        }
        catch (IOException e) {
            SurespotLog.w(TAG, e, "could not move file cache entry from %s to %s", localUri, remoteUri);

        }
    }
}
