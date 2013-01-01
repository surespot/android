package com.twofours.surespot.network;

public abstract class AsyncCallbackWrapper<T,U> implements IAsyncCallback<T> {

	protected U state;

	public AsyncCallbackWrapper(U state) {
		this.state = state;
	}
	
	public AsyncCallbackWrapper() {
		state = null;
	}
	

}
