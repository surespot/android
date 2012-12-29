package com.twofours.surespot;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

public class FriendsActivity extends Activity {

	private Button addFriendButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_friends);

		this.addFriendButton = (Button) this.findViewById(R.id.bAddFriend);
		this.addFriendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				String friend = ((EditText) FriendsActivity.this.findViewById(R.id.etFriend)).getText().toString();

				SurespotApplication.getNetworkController().invite(friend, new IAsyncNetworkResultCallback<Boolean>() {

					@Override
					public void handleResponse(Boolean result) {
						if (result) {
							//TODO indicate in the UI that the request is pending somehow

						}
					}
				});

			}
		});

		// get the list of friends
		SurespotApplication.getNetworkController().getFriends(new IAsyncNetworkResultCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null)
					return;
				((ListView) findViewById(R.id.friendsList)).setAdapter(new ArrayAdapter<String>(FriendsActivity.this,
						android.R.layout.simple_list_item_1, result));
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
