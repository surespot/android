package com.twofours.surespot.common;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

public class FileUtils {
	private static final String STATE_DIR = "state";
	private final static String HTTP = "http";
	public final static String IDENTITIES_DIR = "identities";
	private final static String PUBLICKEYS_DIR = "publicKeys";
	private static final String TAG = "FileUtils";

	public static File getHttpCacheDir(Context context) {

		return getCacheDir(context, HTTP);
	}

	private static File getCacheDir(Context context, String unique) {

		// Check if media is mounted or storage is built-in, if so, try and use external cache dir
		// otherwise use internal cache dir
		String cachePath = null;

		// see if we can write to the "external" storage

		String fCacheDir = getExternalCacheDir(context);
		if (fCacheDir != null) {
			String cacheDir = fCacheDir + File.separator + unique;

			if (ensureDir(cacheDir)) {
				cachePath = cacheDir;
			}
		}

		if (cachePath == null) {
			cachePath = context.getCacheDir().getPath() + File.separator + unique;
		}

		// SurespotLog.w(TAG,"cachePath", new Exception(cachePath));
		return new File(cachePath);

	}

	private static String getExternalCacheDir(Context context) {
		if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			return null;
		}

		String cacheDir = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			File fCacheDir = context.getExternalCacheDir();
			if (fCacheDir != null) {
				cacheDir = fCacheDir.getPath();
			}
		}
		else {

			String baseDir = Environment.getExternalStorageDirectory().getPath();
			cacheDir = baseDir + "/Android/data/com.twofours.surespot/cache/";
		}
		return cacheDir;

	}

	public static boolean ensureDir(String dir) {
		File file = new File(dir);
		return ensureDir(file);
	}

	public static boolean ensureDir(File file) {
		file.mkdirs();
		return file.isDirectory();
	}

	public static File getIdentityExportDir() {
		// http://stackoverflow.com/questions/5694933/find-an-external-sd-card-location/5695129#5695129
		File exportDir = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "surespot" + File.separator + "identities");
		exportDir.mkdirs();
		return exportDir;
	}

	public static File getImageCaptureDir(Context context) {
		return getCacheDir(context, ".image_capture");
	}

	public static String getIdentityDir(Context context) {
		return context.getFilesDir().getPath() + File.separator + IDENTITIES_DIR;
	}

	public static String getPublicKeyDir(Context context) {
		return context.getFilesDir().getPath() + File.separator + PUBLICKEYS_DIR;
	}

	public static String getStateDir(Context context) {
		return context.getFilesDir().getPath() + File.separator + STATE_DIR;
	}

	public static void wipeImageCaptureDir(Context context) {
		File dir = getImageCaptureDir(context);
		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				file.delete();
			}
		}
	}

	public static File createGalleryImageFile(String suffix) throws IOException {

		// Create a unique image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = "image" + "_" + timeStamp + suffix;

		File dir = getPublicImageStorageDir();
		if (FileUtils.ensureDir(dir)) {
			File file = new File(dir.getPath(), imageFileName);
			file.createNewFile();
			// file.setWritable(true, false);
			// SurespotLog.v(TAG, "createdFile: " + file.getPath());
			return file;
		}
		else {
			throw new IOException("Could not create image temp file dir: " + dir.getPath());
		}

	}

	public static String getImageUploadDir(Context context) {
		return getCacheDir(context, "uploadedImages").getPath();
	}

	private static File getPublicImageStorageDir() {
		File imageDir;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			imageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "surespot");
		}
		else {
			imageDir = new File(Environment.getExternalStorageDirectory().getPath() + "/Pictures/surespot");

		}
		return imageDir;
	}

	public static void galleryAddPic(Activity activity, String path) {
		if (activity == null || TextUtils.isEmpty(path)) {
			return;
		}

		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		File f = new File(path);
		Uri contentUri = Uri.fromFile(f);
		mediaScanIntent.setData(contentUri);
		activity.sendBroadcast(mediaScanIntent);
	}

	public static boolean isExternalStorageMounted() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	public static void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] files = fileOrDirectory.listFiles();
			if (files != null) {
				for (File child : files)
					deleteRecursive(child);
			}
		}
		fileOrDirectory.delete();
	}

	public static void writeFile(String filename, String data) throws IOException {
		SurespotLog.v(TAG, "writeFile, %s: %s", filename, data.substring(0, data.length() > 100 ? 100 : data.length()));
		writeFile(filename, data.getBytes());
	}

	public static void writeFile(String filename, byte[] data) throws IOException {
		SurespotLog.v(TAG, "writeFile, %s", filename);

		GZIPOutputStream fos = new GZIPOutputStream(new FileOutputStream(filename));
		fos.write(data);
		fos.close();

	}

	public static byte[] readFile(String filename) throws IOException {
		GZIPInputStream zis = new GZIPInputStream(new FileInputStream(filename));
		byte[] input = Utils.inputStreamToBytes(zis);
		return input;
	}

	public static byte[] readFileNoGzip(String filename) throws IOException {
		FileInputStream fis = new FileInputStream(filename);
		byte[] input = Utils.inputStreamToBytes(fis);
		return input;
	}

	public static boolean isGzipCompressed(byte[] bytes) {
		if ((bytes == null) || (bytes.length < 2)) {
			return false;
		}
		else {
			return ((bytes[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (bytes[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8)));
		}
	}

	public static byte[] gunzipIfNecessary(byte[] identityBytes) {
		// see if it's gzipped - RM#260 doh
		// TODO take this code out one day
		if (FileUtils.isGzipCompressed(identityBytes)) {
			SurespotLog.v(TAG, "gzipped, gunzipping");
			ByteArrayInputStream in = new ByteArrayInputStream(identityBytes);
			try {
				GZIPInputStream gzin = new GZIPInputStream(in);
				return Utils.inputStreamToBytes(gzin);
			}
			catch (IOException e) {
				SurespotLog.w(TAG, e, "error gunzipping identity");
			}

		}
		else {
			SurespotLog.v(TAG, "not gzipped, not gunzipping");
		}
		return identityBytes;
	}
}
