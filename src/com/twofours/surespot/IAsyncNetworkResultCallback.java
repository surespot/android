package com.twofours.surespot;

public interface IAsyncNetworkResultCallback<T> {
	void handleResponse(T result);
}
