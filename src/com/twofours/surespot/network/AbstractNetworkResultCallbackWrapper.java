package com.twofours.surespot.network;

public abstract class AbstractNetworkResultCallbackWrapper<T,U> implements IAsyncCallback<T> {

	protected U state;

	public AbstractNetworkResultCallbackWrapper(U state) {
		this.state = state;
	}
	
	public AbstractNetworkResultCallbackWrapper() {
		state = null;
	}
	

}
