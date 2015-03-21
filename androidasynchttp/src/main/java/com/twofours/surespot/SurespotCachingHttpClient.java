package com.twofours.surespot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import android.content.Context;
import ch.boye.httpclientandroidlib.HttpRequestInterceptor;
import ch.boye.httpclientandroidlib.HttpResponseInterceptor;
import ch.boye.httpclientandroidlib.HttpVersion;
import ch.boye.httpclientandroidlib.client.CookieStore;
import ch.boye.httpclientandroidlib.client.CredentialsProvider;
import ch.boye.httpclientandroidlib.client.HttpRequestRetryHandler;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheEntry;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheStorage;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateCallback;
import ch.boye.httpclientandroidlib.client.cache.HttpCacheUpdateException;
import ch.boye.httpclientandroidlib.conn.params.ConnManagerParams;
import ch.boye.httpclientandroidlib.conn.params.ConnPerRouteBean;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;
import ch.boye.httpclientandroidlib.impl.client.DecompressingHttpClient;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.impl.client.cache.CacheConfig;
import ch.boye.httpclientandroidlib.impl.client.cache.CachingHttpClient;
import ch.boye.httpclientandroidlib.impl.conn.PoolingClientConnectionManager;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpConnectionParams;
import ch.boye.httpclientandroidlib.params.HttpProtocolParams;

import com.jakewharton.disklrucache.DiskLruCache;
import com.jakewharton.disklrucache.DiskLruCache.Snapshot;
import com.loopj.android.http.RetryHandler;
import com.twofours.surespot.common.FileUtils;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.common.WebClientDevWrapper;

public class SurespotCachingHttpClient extends CachingHttpClient {

	private static final int DEFAULT_MAX_CONNECTIONS = 200;
	private static final int DEFAULT_SOCKET_TIMEOUT = 15 * 1000;
	private static final int DEFAULT_MAX_RETRIES = 5;
	private static final int DEFAULT_SOCKET_BUFFER_SIZE = 8192;
	private static final String TAG = "SurespotCachingHttpClient";
	private static int maxConnections = DEFAULT_MAX_CONNECTIONS;
	private static int socketTimeout = DEFAULT_SOCKET_TIMEOUT;

	private AbstractHttpClient mAbstractHttpClient;
	private static SurespotHttpCacheStorage mCacheStorage;

	/**
	 * Use disk cache only
	 * 
	 * @param context
	 * @param abstractHttpClient
	 * @throws IOException
	 */
	public SurespotCachingHttpClient(Context context, AbstractHttpClient abstractHttpClient) throws IOException {
		super(new DecompressingHttpClient(abstractHttpClient), getHttpCacheStorage(context), getDiskCacheConfig());
		log.enableDebug(SurespotConstants.LOGGING);
		log.enableError(SurespotConstants.LOGGING);
		log.enableInfo(SurespotConstants.LOGGING);
		log.enableTrace(SurespotConstants.LOGGING);
		log.enableWarn(SurespotConstants.LOGGING);

		mAbstractHttpClient = abstractHttpClient;

	}

	private static HttpCacheStorage getHttpCacheStorage(Context context) throws IOException {
		if (mCacheStorage == null) {
			mCacheStorage = new SurespotHttpCacheStorage(context);
		}
		return mCacheStorage;
	}

	public static SurespotCachingHttpClient createSurespotDiskCachingHttpClient(Context context) throws IOException {

		BasicHttpParams httpParams = new BasicHttpParams();

		ConnManagerParams.setTimeout(httpParams, socketTimeout);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(maxConnections));
		ConnManagerParams.setMaxTotalConnections(httpParams, DEFAULT_MAX_CONNECTIONS);

		HttpConnectionParams.setSoTimeout(httpParams, socketTimeout);
		HttpConnectionParams.setConnectionTimeout(httpParams, socketTimeout);
		HttpConnectionParams.setTcpNoDelay(httpParams, true);
		HttpConnectionParams.setSocketBufferSize(httpParams, DEFAULT_SOCKET_BUFFER_SIZE);

		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);

		SchemeRegistry schemeRegistry = new SchemeRegistry();

		PoolingClientConnectionManager pm = new PoolingClientConnectionManager(schemeRegistry);
		pm.setMaxTotal(DEFAULT_MAX_CONNECTIONS);
		pm.setDefaultMaxPerRoute(100);
		
		DefaultHttpClient defaultHttpClient = new DefaultHttpClient(pm, httpParams);
		defaultHttpClient.setHttpRequestRetryHandler(new RetryHandler(DEFAULT_MAX_RETRIES));
		WebClientDevWrapper.wrapClient(defaultHttpClient);
		return new SurespotCachingHttpClient(context, defaultHttpClient);
	}

	private static String generateKey(String key) {
		return Utils.md5(key);
	}

	public static class SurespotHttpCacheStorage implements HttpCacheStorage {
		private static final String TAG = "SurespotHttpCacheStorage";
		private DiskLruCache mCache;
		private File mCacheDir;

		public SurespotHttpCacheStorage(Context context) throws IOException {
			mCacheDir = FileUtils.getHttpCacheDir(context);
			SurespotLog.v(TAG, "storage cache dir: %s", mCacheDir);
			mCache = DiskLruCache.open(mCacheDir, 500, 1, Integer.MAX_VALUE);
		}

		@Override
		public HttpCacheEntry getEntry(String arg0) throws IOException {
			SurespotLog.v(TAG, "getting entry, url: " + arg0);
			HttpCacheEntry entry = null;
			try {
				Snapshot snapshot = null;

				String key = generateKey(arg0);
				snapshot = mCache.get(key);

				if (snapshot == null) {
					return null;
				}
				InputStream is = snapshot.getInputStream(0);
				ObjectInputStream ois = new ObjectInputStream(is);

				entry = (HttpCacheEntry) ois.readObject();
				snapshot.close();
				ois.close();

				SurespotLog.v(TAG, "read cache entry, resource length: %d", entry.getResource().length());
			}
			catch (Exception e) {
				throw new IOException("Error retrieving cache entry: " + arg0);
			}

			return entry;
		}

		@Override
		public void putEntry(String key, HttpCacheEntry entry) throws IOException {
			try {
				SurespotLog.v(TAG, "putting cache entry, url: %s, resource length: %d", key, entry.getResource().length());
				String gKey = generateKey(key);
				// SurespotLog.v(TAG, "putting cache entry, key: " + gKey);

				DiskLruCache.Editor edit = mCache.edit(gKey);

				if (edit != null) {
					OutputStream outputStream = edit.newOutputStream(0);
					ObjectOutputStream os = new ObjectOutputStream(outputStream);
					os.writeObject(entry);
					os.close();

					edit.commit();
				}
			}
			catch (Exception e) {
				SurespotLog.w(TAG, e, "putEntry");
			}

		}

		@Override
		public void removeEntry(String arg0) throws IOException {
			// SurespotLog.v(TAG, "removing cache entry, key: " + arg0);
			String gKey = generateKey(arg0);
			mCache.remove(gKey);
		}

		@Override
		public void updateEntry(String arg0, HttpCacheUpdateCallback arg1) throws IOException, HttpCacheUpdateException {
			try {
				// SurespotLog.v(TAG, "updating entry, url: " + arg0);
				HttpCacheEntry entry = getEntry(arg0);
				if (entry != null) {
					putEntry(arg0, arg1.update(entry));
				}
				else {
					throw new HttpCacheUpdateException("key not found: " + arg0);
				}
			}
			catch (Exception e) {
				SurespotLog.w(TAG, e, "updateEntry");
			}

		}

		/**
		 * Removes all disk cache entries from the application cache directory in the uniqueName sub-directory.
		 * 
		 * @param context
		 *            The context to use
		 * @param uniqueName
		 *            A unique cache directory name to append to the app cache directory
		 */
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
		 * Removes all disk cache entries from the given directory. This should not be called directly, call {@link DiskLruCache#clearCache(Context, String)} or
		 * {@link DiskLruCache#clearCache()} instead.
		 * 
		 * @param cacheDir
		 *            The directory to remove the cache files from
		 */
		private void clearCache(File cacheDir) {
			final File[] files = cacheDir.listFiles();
			if (files != null) {
				for (int i = 0; i < files.length; i++) {
					files[i].delete();
				}
			}
		}

	}

	public void clearCache() {
		mCacheStorage.clearCache();
	}

	public static CacheConfig getDiskCacheConfig() {

		CacheConfig cacheConfig = new CacheConfig();
		cacheConfig.setMaxCacheEntries(500);
		cacheConfig.setMaxObjectSizeBytes(250000);
		return cacheConfig;
	}

	public HttpRequestRetryHandler getHttpRequestRetryHandler() {
		return mAbstractHttpClient.getHttpRequestRetryHandler();
	}

	public CredentialsProvider getCredentialsProvider() {
		return mAbstractHttpClient.getCredentialsProvider();
	}

	public void addRequestInterceptor(HttpRequestInterceptor httpRequestInterceptor) {
		mAbstractHttpClient.addRequestInterceptor(httpRequestInterceptor);
	}

	public void addResponseInterceptor(HttpResponseInterceptor httpResponseInterceptor) {
		mAbstractHttpClient.addResponseInterceptor(httpResponseInterceptor);

	}

	public void setHttpRequestRetryHandler(RetryHandler retryHandler) {
		mAbstractHttpClient.setHttpRequestRetryHandler(retryHandler);

	}

	public void setCookieStore(CookieStore cookieStore) {
		mAbstractHttpClient.setCookieStore(cookieStore);
	}

	public AbstractHttpClient getAbstractHttpClient() {
		return mAbstractHttpClient;
	}

	@Override
	public boolean isSharedCache() {
		return true;
	}

	public void removeEntry(String key) {
		try {
			SurespotLog.v(TAG, "removing cache entry, key: %s", key);
			mCacheStorage.removeEntry(key);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, e, "removeEntry");
		}
	}

	public void addCacheEntry(String key, HttpCacheEntry entry) {
		try {
			mCacheStorage.putEntry(key, entry);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, e, "addToCache");
		}
	}

	public HttpCacheEntry getCacheEntry(String key) {
		try {
			return mCacheStorage.getEntry(key);
		}
		catch (IOException e) {
			SurespotLog.w(TAG, e, "getCacheEntry");

		}

		return null;
	}
	
	public void destroy() {
		mAbstractHttpClient.getConnectionManager().shutdown();
	}
	
    public void setUserAgent(String userAgent) {
        HttpProtocolParams.setUserAgent(this.mAbstractHttpClient.getParams(), userAgent);
    }

}
