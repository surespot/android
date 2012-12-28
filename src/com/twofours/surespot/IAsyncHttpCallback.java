package com.twofours.surespot;

import org.apache.http.HttpResponse;

public interface IAsyncHttpCallback {
	void handleResponse(HttpResponse response);
}
