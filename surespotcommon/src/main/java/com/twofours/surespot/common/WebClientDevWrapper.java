package com.twofours.surespot.common;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.net.SSLCertificateSocketFactory;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.scheme.PlainSocketFactory;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;

public class WebClientDevWrapper {
	private static final String TAG = "WebClientDevWrapper";
	private static SSLContext mSSLContext;

	public static void wrapClient(AbstractHttpClient base) {
		// wrap client so we can use self signed cert in dev

		SSLSocketFactory ssf = new SSLSocketFactory(getSSLContext());
		// TODO Compile out for prod
		if (SurespotConfiguration.isSslCheckingStrict()) {
			ssf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
		}
		else {
			ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		}
		ClientConnectionManager ccm = base.getConnectionManager();
		SchemeRegistry sr = ccm.getSchemeRegistry();
		sr.register(new Scheme("https", ssf, 443));
		sr.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 8080));
	}

	public static javax.net.SocketFactory getWebSocketFactory() {

		if (SurespotConfiguration.isSslCheckingStrict()) {
			return SSLCertificateSocketFactory.getDefault();
		}
		else {

			return SSLCertificateSocketFactory.getInsecure(0, null);
		}
	}

	private static SSLContext getSSLContext() {
		if (mSSLContext == null) {
			try {

				mSSLContext = SSLContext.getInstance("TLS");
				if (SurespotConfiguration.isSslCheckingStrict()) {
					mSSLContext.init(null, null, null);
				}
				else {

					X509TrustManager tm = new X509TrustManager() {

						public X509Certificate[] getAcceptedIssuers() {
							return null;
						}

						@Override
						public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
							// TODO Auto-generated method stub

						}

						@Override
						public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
							// TODO Auto-generated method stub

						}
					};
					mSSLContext.init(null, new TrustManager[] { tm }, null);
				}
			}
			catch (Exception ex) {
				SurespotLog.w(TAG, "could not initialize sslcontext", ex);
				return null;
			}
		}
		return mSSLContext;
	}

}
