package com.twofours.surespot.network;

public interface IAsyncNetworkResultCallback<T> {
	void handleResponse(T result);
}
