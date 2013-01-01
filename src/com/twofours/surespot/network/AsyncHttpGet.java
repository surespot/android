package com.twofours.surespot.network;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.os.AsyncTask;
import android.util.Log;

public class AsyncHttpGet extends AsyncTask<Void, Void, HttpResponse> {
	private String _url;
	private IAsyncCallback<HttpResponse> _callback;	
	private HttpClient _httpClient;

	public AsyncHttpGet(HttpClient httpClient,String url, IAsyncCallback<HttpResponse> callback) {
		_httpClient = httpClient;
		_url = url;
		_callback = callback;
	}

	@Override
	protected HttpResponse doInBackground(Void... noParams) {
		//DefaultHttpClient client = new DefaultHttpClient();
		//HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); // Timeout
																				// Limit

		try {
			HttpGet get = new HttpGet(_url);
			
			
			
			HttpResponse response = _httpClient.execute(get);
		//	if (response.getEntity().getContent() != null && response.getEntity().getContent().available() > 0)
		//		response.getEntity().getContent().close();
			return response;

		} catch (Exception e) {
			Log.e("network", "error",e);
			//e.printStackTrace();
			// createDialog("Error", "Cannot Establish Connection");
		}

		return null;
	}

	@Override
	protected void onPostExecute(HttpResponse response) {
		_callback.handleResponse(response);
	}

}
