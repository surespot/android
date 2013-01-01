package com.twofours.surespot.network;

import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.util.Log;

public class AsyncHttpPost extends AsyncTask<Void, Void, HttpResponse> {
	private String _url;
	private IAsyncCallback<HttpResponse> _callback;
	private Map<String, String> _params;
	private HttpClient _httpClient;

	public AsyncHttpPost(HttpClient httpClient, String url, Map<String, String> params, IAsyncCallback<HttpResponse> callback) {
		_httpClient = httpClient;
		_url = url;
		_callback = callback;
		_params = params;

	}

	@Override
	protected HttpResponse doInBackground(Void... paramsInConstructorNotHere) {
		// DefaultHttpClient client = new DefaultHttpClient();
		// HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); // Timeout
		// Limit
		JSONObject json = new JSONObject();
		try {
			HttpPost post = new HttpPost(_url);

			if (_params != null && !_params.isEmpty()) {
				for (Map.Entry<String, String> param : _params.entrySet()) {
					json.putOpt(param.getKey(), param.getValue());
				}
				StringEntity se = new StringEntity(json.toString());
				se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
				post.setEntity(se);
			}
			HttpResponse response = _httpClient.execute(post);
			// if (response.getEntity().getContent() != null && response.getEntity().getContent().available() > 0)
			// response.getEntity().getContent().close();
			return response;

		} catch (Exception e) {
			Log.e("network", "error", e);
			// e.printStackTrace();
			// createDialog("Error", "Cannot Establish Connection");
		}

		return null;
	}

	@Override
	protected void onPostExecute(HttpResponse response) {
		_callback.handleResponse(response);
	}

}
