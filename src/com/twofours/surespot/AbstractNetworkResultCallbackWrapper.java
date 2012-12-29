package com.twofours.surespot;

public abstract class AbstractNetworkResultCallbackWrapper<T,U> implements IAsyncNetworkResultCallback<T> {

	protected U state;

	public AbstractNetworkResultCallbackWrapper(U state) {
		this.state = state;
	}
	
	public AbstractNetworkResultCallbackWrapper() {
		state = null;
	}
	

}
