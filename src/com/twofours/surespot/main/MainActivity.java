package com.twofours.surespot.main;

import android.os.Bundle;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.IConnectCallback;

public class MainActivity extends SherlockFragmentActivity {
	public static final String TAG = "MainActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreate");
		setContentView(R.layout.activity_main);
	}

	@Override
	protected void onPause() {		
		super.onPause();
		ChatController.disconnect();
	}
	
	@Override
	protected void onResume() {

		super.onResume();
		// reconnect to socket io
		Log.v(TAG, "onResume");
		ChatController.connect(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (!status) {
					Log.e(TAG, "Could not connect to chat server.");
				}
			}
		});
	}

}
