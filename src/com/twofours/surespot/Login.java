package com.twofours.surespot;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.interfaces.ECPrivateKey;
import org.spongycastle.jce.interfaces.ECPublicKey;
import org.spongycastle.jce.spec.ECParameterSpec;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.jce.spec.ECPublicKeySpec;
import org.spongycastle.util.encoders.Hex;

import com.twofours.surespot.R;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class Login extends Activity {

	static {
		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
	}

	private Button loginButton;
	private Button sayHelloButton;
	private SocketIO socket;
	// TODO put this behind a factory or singleton or something
	private AbstractHttpClient _httpClient;
	private static String ASYMKEYPAIR_PREFKEY = "asymKeyPair";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		//use brainpool curve - fuck NIST! Reopen 9/11 investigation now!
		ECParameterSpec curve = ECNamedCurveTable
				.getParameterSpec("secp521r1");
	
		
		// attempt to load key pair
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		String asymKeyPair = settings.getString(ASYMKEYPAIR_PREFKEY, null);

		// if no keypair, generate one
		// TODO move to thread
		if (asymKeyPair == null) {
			ECPublicKey ecpk = null; 

			// begin key generation
			KeyPairGenerator g = null;
			String generatedPrivateKeyHex = null, generatedPrivDHex = null;
			try {
				g = KeyPairGenerator.getInstance("ECDH", "SC");
				g.initialize(curve, new SecureRandom());
				KeyPair pair = g.generateKeyPair();
				ecpk = (ECPublicKey) pair.getPublic();
				ECPrivateKey ecprik = (ECPrivateKey) pair.getPrivate();

				// Log.d("ke","encoded public key: " +
				// ecpk.getEncoded().toString());
				// pair.getPublic().
				// ecpk.getW().;
				// ecprik.getD().toByteArray();
				generatedPrivDHex = new String(Hex.encode(ecprik.getD()
						.toByteArray()));

				generatedPrivateKeyHex = new String(Hex.encode(ecprik
						.getEncoded()));
				String publicKey = new String(Hex.encode(ecpk.getQ().getEncoded())); 
				Log.d("ke",
						"generated public key:"
								+ publicKey);

				//Log.d("ke", "generated private key:" + generatedPrivateKeyHex);
				Log.d("ke", "generated private key d:" + generatedPrivDHex);
				
				//save keypair in shared prefs json format (hex for now) TODO use something other than hex
				JSONObject json = new JSONObject();
				json.putOpt("private_key", generatedPrivDHex);
				json.putOpt("public_key", publicKey);
				settings.edit().putString(ASYMKEYPAIR_PREFKEY, json.toString()).commit();
				
				
				
				
			} catch (NoSuchAlgorithmException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			} catch (NoSuchProviderException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else {
			//we have a keypair, load the fuckers up and reconstruct the keys
			try {
				JSONObject json = new JSONObject(asymKeyPair);
				String sPrivateKey = (String) json.get("private_key");
				String sPublicKey = (String) json.get("public_key");
				//recreate key from hex string
				ECPrivateKeySpec priKeySpec = new ECPrivateKeySpec(new BigInteger(
				
						Hex.decode(sPrivateKey)), curve);
				ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(
						//new ECPoint(new BigInteger(px,16), new BigInteger(px,16)),
						curve.getCurve().decodePoint(Hex.decode(sPublicKey)),
						curve);

				ECPrivateKey privKey = null;
				ECPublicKey pubKey = null;

				try {
					KeyFactory fact =  KeyFactory.getInstance("ECDH", "SC");
				
					privKey = (ECPrivateKey) fact.generatePrivate(priKeySpec);
					pubKey = (ECPublicKey) fact.generatePublic(pubKeySpec);
				} catch (InvalidKeySpecException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		//TODO use HttpURLConnection (http://android-developers.blogspot.com/2011/09/androids-http-clients.html)
		// create thread safe http client
		// (http://foo.jasonhudgins.com/2010/03/http-connections-revisited.html)
		_httpClient = new DefaultHttpClient();
		ClientConnectionManager mgr = _httpClient.getConnectionManager();
		HttpParams params = _httpClient.getParams();
		_httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(
				params, mgr.getSchemeRegistry()), params);
		HttpConnectionParams.setConnectionTimeout(_httpClient.getParams(),
				10000); // Timeout
						// Limit

		this.loginButton = (Button) this.findViewById(R.id.bLogin);
		this.loginButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				Map<String, String> params = new HashMap<String, String>();
				params.put("username", ((EditText) Login.this
						.findViewById(R.id.etUsername)).getText().toString());
				params.put("password", ((EditText) Login.this
						.findViewById(R.id.etPassword)).getText().toString());

				AsyncHttpPost post = new AsyncHttpPost(_httpClient,
						"http://192.168.10.68:3000/login", params,
						new IAsyncHttpCallback() {

							@Override
							public void handleResponse(HttpResponse response) {

								/* Checking response */
								if (response.getStatusLine().getStatusCode() == 204) {
									Cookie cookie = null;
									for (Cookie c : (_httpClient)
											.getCookieStore().getCookies()) {
										System.out.println("Cookie name: "
												+ c.getName() + " value: "
												+ c.getValue());
										if (c.getName().equals("connect.sid")) {
											cookie = c;
											break;
										}
									}

									if (cookie == null) {
										System.out
												.println("did not get cookie from login");
										return;
									}
									try {
										socket = new SocketIO(
												"http://192.168.10.68:3000");
										socket.addHeader(
												"cookie",
												cookie.getName() + "="
														+ cookie.getValue());
									} catch (MalformedURLException e1) {
										// Auto-generated
										e1.printStackTrace();
									}

									socket.connect(new IOCallback() {

										@Override
										public void onMessage(JSONObject json,
												IOAcknowledge ack) {
											try {
												System.out.println("Server said:"
														+ json.toString(2));
											} catch (JSONException e) {
												e.printStackTrace();
											}
										}

										@Override
										public void onMessage(String data,
												IOAcknowledge ack) {
											System.out.println("Server said: "
													+ data);
										}

										@Override
										public void onError(
												SocketIOException socketIOException) {
											System.out
													.println("an Error occured");
											socketIOException.printStackTrace();
										}

										@Override
										public void onDisconnect() {
											System.out
													.println("Connection terminated.");
										}

										@Override
										public void onConnect() {
											System.out
													.println("socket.io connection established");

										}

										@Override
										public void on(String event,
												IOAcknowledge ack,
												Object... args) {
											System.out
													.println("Server triggered event '"
															+ event + "'");
										}
									});

									// JSONObject j = new JSONObject();
									// //j.putOpt(name,
									// value)
									// socket.send()

								}

							}
						});
				post.execute();

			}
		});

		/*
		 * this.sayHelloButton = (Button) this.findViewById(R.id.bSayHello);
		 * this.sayHelloButton.setOnClickListener(new View.OnClickListener() {
		 * 
		 * @Override public void onClick(View v) { // send a message JSONObject
		 * json = new JSONObject();
		 * 
		 * try { json.putOpt("room", "adam_cherie"); json.putOpt("text",
		 * "hello from android"); socket.emit("message", json.toString()); }
		 * catch (JSONException e) { // TODO Auto-generated catch block
		 * e.printStackTrace(); }
		 * 
		 * } });
		 */
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_sure_spot, menu);
		return true;
	}

}
