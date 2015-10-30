package com.twofours.surespot.common;

import android.net.SSLCertificateSocketFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.conn.scheme.PlainSocketFactory;
import ch.boye.httpclientandroidlib.conn.scheme.Scheme;
import ch.boye.httpclientandroidlib.conn.scheme.SchemeRegistry;
import ch.boye.httpclientandroidlib.conn.ssl.SSLSocketFactory;
import ch.boye.httpclientandroidlib.impl.client.AbstractHttpClient;

public class WebClientDevWrapper {
	private static final String TAG = "WebClientDevWrapper";
    private static final String CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFXTCCA0WgAwIBAgIJALXeCaIeYDmkMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQwHhcNMTUxMDMwMDEyMjQ1WhcNMTYxMDI5MDEyMjQ1WjBF\n" +
            "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
            "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIIC\n" +
            "CgKCAgEAr2+9u33Sb/eQuJymv8NJSfGKIayenDUMeWfQZ5X0KaKaEnjJb/x8U0Ej\n" +
            "XyBOWe4LuvCK1QsN8KpfCgt/Q0uN49gvoaXo9fsr2XNN/asmc6rOsKviLMWWeCJ9\n" +
            "ug4OL9o/5IRhWN2JB8K2xziuAnvr55ZaMtbH6hXlBZTxOEH5ZDFRO0uu0NTC8jcC\n" +
            "yrfRpCXB2NZurlswog6y5VkQbYS6Gn5uNod2+ZAL2m/WBV1LiVNH5oAxW8tjpL8x\n" +
            "gPF99NmmES8lQkTxs1fyrRPl5X2YIKMfe+MrPYdnKRjUsjHky2fSX7ZpDjPxpSZl\n" +
            "6Yt3hUTa/Kd7ZbIlCHGj8nIlmFUjJbbJMUDh7sU4EYNZQ2DUHaI/LLqu64AKN9Qw\n" +
            "Rr8cA/wkl2wqFyqxddzYOrdkTeQkO5Dkj+YtBpO3TFmlZQUS+6VOYrpKW6NmKxM8\n" +
            "hTU8++PcjW9yZu7YXe3zEgr3E9NOzRiluuzInb5832TGPxpkt42BKMbO2eIUfrDR\n" +
            "QNo7r18dVZK1VnWnotZCIr7tERcR3qoB+/3uVLH6ydB7QMn3l1pDcoDlipOA/+VQ\n" +
            "qrDGy+9PJqdgwT7v0TnEbRK5uRKShrUzSVEbnTzA9QZrNiymh7L/gTQ1vTbFwh4E\n" +
            "xd1LQGCi+myNUbWxxGtzJVM7MS+t8b5a2I8GR00eZlkb8vtEud8CAwEAAaNQME4w\n" +
            "HQYDVR0OBBYEFD0PpXhdgkJzJYSnxmhQ2+hy9Kd/MB8GA1UdIwQYMBaAFD0PpXhd\n" +
            "gkJzJYSnxmhQ2+hy9Kd/MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIB\n" +
            "AIovWoiqUrPflIcSrxf14VioMQ9XN1kGnNEuxw8Ru5UZ5mmX9/88kGo8bV+L/tor\n" +
            "r657oJypiRQJSSv6nfX8ZbEK8LSPgcKeLpYpYUWGRfEGY79bpIAekkXW1pFBcgrF\n" +
            "Fg+6K4yZQPCtg8Oe1yK96Xo7+INyfiD2xRLwiaACAwenTO/4JWlJIWLQ+mesRj/X\n" +
            "IvSgaBNT4qqYYsauGkPIG0uU2NBMfPMkKBa/DOq9m64r8GRGHnR3wvzznvopW+yE\n" +
            "EiVGUJjFKsjWTfwNKzyzPFi0KeCG616mVQRSvYO+iG5r5+v1MIz89wTwLdfdk2Wi\n" +
            "Ssv3v/s+vrlU2zFKZN4sVsQSWPxzIW2UjCiGq/T4P3m+XwPgucyBP5iXEdgcn007\n" +
            "vmNOIOm+yRkkCBAYTYcey8IxMQqWy0zS68qN1aLVLp2hcqeL0Y6GtBu5aRpw3Ec6\n" +
            "PQcnzBTenaZ23n0bdHVLAkEF7HdgL6n21cjXklbdQTgI/Ybh8a/6NYJSmqv88jFA\n" +
            "TB08QmsaPbWvSdW4S719BJx/HdYnrlrrNru4DNFLh6J2Vir/6C59EAXvMUaBKnMZ\n" +
            "2xpBnJP2pd6gDwkoWrTIoLq8M3bNsoJgUGmu2nerXzjc1yiDrjZdol6IbIp/Uqqo\n" +
            "ak5IW/RGWyKvsGE/ZCmKwWLnvCeiYt+Xsio0gbMnnQbH\n" +
            "-----END CERTIFICATE-----\n";
	private static SSLContext mSSLContext;

	public static void wrapClient(AbstractHttpClient base) {
		// wrap client so we can use self signed cert in dev

		SSLSocketFactory ssf = new SSLSocketFactory(getSSLContext());
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

	public static SSLContext getSSLContext() {
		if (mSSLContext == null) {
			try {

				mSSLContext = SSLContext.getInstance("TLS");
				if (SurespotConfiguration.isSslCheckingStrict()) {
                    //self signed
                    InputStream caInput = new ByteArrayInputStream(CERT.getBytes("UTF-8"));
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    Certificate ca = cf.generateCertificate(caInput);
                    //System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());

                    // Create a KeyStore containing our trusted CAs
                    String keyStoreType = KeyStore.getDefaultType();
                    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                    keyStore.load(null, null);
                    keyStore.setCertificateEntry("ca", ca);

                    // Create a TrustManager that trusts the CAs in our KeyStore
                    String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                    tmf.init(keyStore);

                    // Create an SSLContext that uses our TrustManager
					mSSLContext.init(null, tmf.getTrustManagers(), null);
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
