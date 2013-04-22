package com.twofours.surespot.network;

public interface IAsyncCallbackTriplet<T,U, V> {
	void handleResponse(T result, U result2, V result3);
}