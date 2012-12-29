package com.twofours.surespot.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.Utils;

public class NetworkController {
	// TODO put this behind a factory or singleton or something
	private AbstractHttpClient httpClient;
	private Cookie cookie;
	private String baseUrl = "http://192.168.10.68:3000";

	public NetworkController() {

		// TODO use HttpURLConnection (http://android-developers.blogspot.com/2011/09/androids-http-clients.html)
		// create thread safe http client
		// (http://foo.jasonhudgins.com/2010/03/http-connections-revisited.html)
		httpClient = new DefaultHttpClient();
		ClientConnectionManager mgr = httpClient.getConnectionManager();
		HttpParams params = httpClient.getParams();
		httpClient = new DefaultHttpClient(new ThreadSafeClientConnManager(params, mgr.getSchemeRegistry()), params);
		HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), 10000); // Timeout
		// Limit
	}

	public Cookie getCookie() {
		return cookie;
	}

	public void addUser(String username, String password, String publicKey,
			final IAsyncNetworkResultCallback<Boolean> callback) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);
		params.put("publickey", publicKey);

		AsyncHttpPost post = new AsyncHttpPost(httpClient, baseUrl + "/users", params, new IAsyncHttpCallback() {

			@Override
			public void handleResponse(HttpResponse response) {
				Boolean result = false;
				/* Checking response */
				if (response != null && response.getStatusLine().getStatusCode() == 201) {
					for (Cookie c : (httpClient).getCookieStore().getCookies()) {
						System.out.println("Cookie name: " + c.getName() + " value: " + c.getValue());
						if (c.getName().equals("connect.sid")) {
							cookie = c;
							result = true;
							break;
						}
					}

					if (cookie == null) {
						System.out.println("did not get cookie from signup");
					}
				}
				// pass the callback in?
				callback.handleResponse(result);

			}
		});
		post.execute();
	}

	public void login(String username, String password, final IAsyncNetworkResultCallback<Boolean> callback) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("username", username);
		params.put("password", password);

		AsyncHttpPost post = new AsyncHttpPost(httpClient, baseUrl + "/login", params, new IAsyncHttpCallback() {

			@Override
			public void handleResponse(HttpResponse response) {
				Boolean result = false;
				/* Checking response */
				if (response != null && response.getStatusLine().getStatusCode() == 204) {

					for (Cookie c : (httpClient).getCookieStore().getCookies()) {
						System.out.println("Cookie name: " + c.getName() + " value: " + c.getValue());
						if (c.getName().equals("connect.sid")) {
							cookie = c;
							result = true;
							break;
						}
					}

					if (cookie == null) {
						System.out.println("did not get cookie from login");
					}

				}
				// pass the callback in?
				callback.handleResponse(result);

			}
		});
		post.execute();

	}

	public void getFriends(final IAsyncNetworkResultCallback<List<String>> callback) {
		AsyncHttpGet get = new AsyncHttpGet(httpClient, baseUrl + "/friends",
				new IAsyncNetworkResultCallback<HttpResponse>() {

					@Override
					public void handleResponse(HttpResponse response) {

						/* Checking response */
						if (response != null && response.getStatusLine().getStatusCode() == 200) {

							// pass the callback in?
							try {
								String friendsJson = Utils.inputStreamToString(response.getEntity().getContent());
								List<String> friends = null;
								try {
									JSONArray jsonArray = new JSONArray(friendsJson);
									if (jsonArray.length() > 0) {
										friends = new ArrayList<String>(jsonArray.length());
										for (int i = 0; i < jsonArray.length(); i++) {
											friends.add(jsonArray.getString(i));
										}
									}
								} catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								callback.handleResponse(friends);
							} catch (IllegalStateException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

					}
				});
		get.execute();

	}
	
	public void getNotifications(final IAsyncNetworkResultCallback<List<JSONObject>> callback) {
		AsyncHttpGet get = new AsyncHttpGet(httpClient, baseUrl + "/notifications",
				new IAsyncNetworkResultCallback<HttpResponse>() {

					@Override
					public void handleResponse(HttpResponse response) {

						/* Checking response */
						if (response != null && response.getStatusLine().getStatusCode() == 200) {

							// pass the callback in?
							try {
								String notificationsJson = Utils.inputStreamToString(response.getEntity().getContent());
								List<JSONObject> notifications = null;
								try {
									JSONArray jsonArray = new JSONArray(notificationsJson);
									if (jsonArray.length() > 0) {
										notifications = new ArrayList<JSONObject>(jsonArray.length());
										for (int i = 0; i < jsonArray.length(); i++) {
											notifications.add(jsonArray.getJSONObject(i));
										}
									}
								} catch (JSONException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								callback.handleResponse(notifications);
							} catch (IllegalStateException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

					}
				});
		get.execute();

	}
	
	public void invite(String friendname, final IAsyncNetworkResultCallback<Boolean> callback) { 
		

		AsyncHttpPost post = new AsyncHttpPost(httpClient, baseUrl + "/invite/" + friendname, null, new IAsyncHttpCallback() {

			@Override
			public void handleResponse(HttpResponse response) {
				
				/* Checking response */
				if (response != null && (response.getStatusLine().getStatusCode() == 204 || response.getStatusLine().getStatusCode() == 202)) {
					callback.handleResponse(true);
					
				}
				else {
					callback.handleResponse(false);
				}
				

			}
		});
		post.execute();

	}
}
