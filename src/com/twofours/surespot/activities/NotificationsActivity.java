package com.twofours.surespot.activities;

import java.util.List;

import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.widget.ListView;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.layout.NotificationArrayAdapter;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class NotificationsActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_notifications);

		// get the list of friends
		SurespotApplication.getNetworkController().getNotifications(
				new IAsyncNetworkResultCallback<List<JSONObject>>() {

					@Override
					public void handleResponse(List<JSONObject> result) {
						if (result == null)
							return;

						((ListView) findViewById(R.id.notificationsList)).setAdapter(new NotificationArrayAdapter(
								getBaseContext(), result));

					}
				});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_friends, menu);
		return true;
	}

}
