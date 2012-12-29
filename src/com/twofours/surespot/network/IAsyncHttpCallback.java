package com.twofours.surespot.network;

import org.apache.http.HttpResponse;

public interface IAsyncHttpCallback {
	void handleResponse(HttpResponse response);
}
