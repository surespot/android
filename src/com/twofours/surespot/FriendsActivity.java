package com.twofours.surespot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.app.Activity;
import android.app.ListActivity;
import android.view.Menu;
import android.widget.ArrayAdapter;

public class FriendsActivity extends ListActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_friends);
		
		//get the list of friends
		SurespotApplication.getNetworkController().getFriends(new IAsyncNetworkResultCallback<String>() {
			
			@Override
			public void handleResponse(String result) {
				if (result == null) return;
				String[] friends = null;
				try {
					JSONArray jsonArray = new JSONArray(result);
					friends = new String[jsonArray.length()];
					for (int i =0;i<jsonArray.length();i++) {
						friends[i] = jsonArray.getString(i);
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				setListAdapter(new ArrayAdapter<String>(FriendsActivity.this, android.R.layout.simple_list_item_1, friends));
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
