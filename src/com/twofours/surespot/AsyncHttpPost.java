package com.twofours.surespot;

import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import android.os.AsyncTask;

public class AsyncHttpPost extends AsyncTask<Void, Void, HttpResponse> {
	private String _url;
	private IAsyncHttpCallback _callback;
	private Map<String,String> _params;
	private HttpClient _httpClient;

	public AsyncHttpPost(HttpClient httpClient,String url, Map<String, String> params, IAsyncHttpCallback callback) {
		_httpClient = httpClient;
		_url = url;
		_callback = callback;
		_params = params;

	}

	@Override
	protected HttpResponse doInBackground(Void... paramsInConstructorNotHere) {
		//DefaultHttpClient client = new DefaultHttpClient();
		//HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); // Timeout
																				// Limit
		JSONObject json = new JSONObject();
		try {
			HttpPost post = new HttpPost(_url);
			
			for (Map.Entry<String, String> param : _params.entrySet()) {			
				json.putOpt(param.getKey(), param.getValue());				
			}
			StringEntity se = new StringEntity(json.toString());
			se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE,
					"application/json"));
			post.setEntity(se);
			return _httpClient.execute(post);

		} catch (Exception e) {
			e.printStackTrace();
			// createDialog("Error", "Cannot Estabilish Connection");
		}

		return null;
	}

	@Override
	protected void onPostExecute(HttpResponse response) {
		_callback.handleResponse(response);
	}

}
