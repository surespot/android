package com.twofours.surespot.network;

public interface IAsyncCallbackTuple<T,U> {
	void handleResponse(T result, U result2);
}