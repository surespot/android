package com.twofours.surespot.activities;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.twofours.surespot.IAsyncNetworkResultCallback;
import com.twofours.surespot.NotificationArrayAdapter;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.R.id;
import com.twofours.surespot.R.layout;
import com.twofours.surespot.R.menu;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

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
