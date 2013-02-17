package com.twofours.surespot.network;


public interface IAsyncCallback<T> {
	void handleResponse(T result);
}
