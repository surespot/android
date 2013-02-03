package com.twofours.surespot.network;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.content.Context;
import android.util.Log;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.scheme.PlainSocketFactory;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.impl.conn.SingleClientConnManager;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;

public class SurespotHttpClient extends DefaultHttpClient {
	protected static final String TAG = null;
	final Context context;
	SSLContext mSSLContext;

	public SurespotHttpClient(SSLContext sslContext, Context context) {
		this.context = context;
		mSSLContext = sslContext;
	}

	@Override
	protected ClientConnectionManager createClientConnectionManager() {
		SchemeRegistry registry = new SchemeRegistry();
		registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 3000));
		// Register for port 443 our SSLSocketFactory with our keystore
		// to the ConnectionManager
		SSLSocketFactory factory = newSslSocketFactory();
		registry.register(new Scheme("https", factory, 443));
		return new SingleClientConnManager(getParams(), registry);
	}

	private SSLSocketFactory newSslSocketFactory() {

		KeyStore keyStore;
		try {
			keyStore = KeyStore.getInstance("BKS");
			keyStore.load(SurespotApplication.getAppContext().getResources().openRawResource(R.raw.dev_keystore), "wanker".toCharArray());

			final KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManager.init(keyStore, null);

			final TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustFactory.init(keyStore);

			SSLContext sslContext = SSLContext.getInstance("TLS", "HarmonyJSSE");
			sslContext.init(keyManager.getKeyManagers(), trustFactory.getTrustManagers(), null);

			// Pass the keystore to the SSLSocketFactory. The factory is responsible
			// for the verification of the server certificate.
			// SSLSocketFactory sf = new SSLSocketFactory(mSSLContext);
			SSLSocketFactory sf = new SSLSocketFactory(sslContext);
			// sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
			return sf;

			// Hostname verification from certificate
			// http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d4e506
			// sf.setHostnameVerifier(new X509HostnameVerifier() {
			//
			// @Override
			// public boolean verify(String hostname, SSLSession session) {
			// // TODO Auto-generated method stub
			// return true;
			// }
			//
			// @Override
			// public void verify(String host, SSLSocket ssl) throws IOException {
			// // TODO Auto-generated method stub
			//
			// }
			//
			// @Override
			// public void verify(String host, X509Certificate cert) throws SSLException {
			// // TODO Auto-generated method stub
			//
			// }
			//
			// @Override
			// public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
			// // TODO Auto-generated method stub
			//
			// }
			// });
			// return sf;

		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		//
		// private SSLSocketFactory acceptAll() {
		// SSLContext sc;
		// try {
		// sc = SSLContext.getInstance("TLS");
		// sc.init(null, getTrustingManager(), null);
		// SSLSocketFactory socketFactory = new SSLSocketFactory(sc);
		// socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		//
		// return socketFactory;
		//
		// }
		// catch (NoSuchAlgorithmException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// catch (KeyManagementException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// // catch (NoSuchProviderException e) {
		// // // TODO Auto-generated catch block
		// // e.printStackTrace();
		// // }
		// return null;
	}

	public static TrustManager[] getTrustingManager() {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
				// Do nothing
				Log.v(TAG, "checkClientTrusted");
			}

			@Override
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
				// Do nothing
				Log.v(TAG, "checkServerTrusted");
			}

		} };
		return trustAllCerts;
	}

}
