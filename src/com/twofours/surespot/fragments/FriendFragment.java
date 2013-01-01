package com.twofours.surespot.fragments;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.Utils;
import com.twofours.surespot.activities.ChatActivity;
import com.twofours.surespot.network.IAsyncCallback;

public class FriendFragment extends SherlockFragment {

	private ArrayAdapter<String> friendAdapter;
	private static final String TAG = "FriendFragment";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Log.v(TAG, "onCreateView");
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);
		final ListView listView = (ListView) view.findViewById(R.id.friend_list);
		// listView.setEmptyView(view.findViewById(R.id.friend_list_empty));

		// get the list of friends
		SurespotApplication.getNetworkController().getFriends(new IAsyncCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null) {
					return;
				}

				friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, result);
				listView.setAdapter(friendAdapter);

			}
		});

		// click on friend to join chat
		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// see if we have chat activity
				// TODO move logic somewhere more appropriate
				View chatActivity = getView().findViewById(R.id.chat_activity);
				if (chatActivity == null) {

					// create chat room
					Intent intent = new Intent(getActivity(), ChatActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
					intent.putExtra("username", friendAdapter.getItem(position));
					getActivity().startActivity(intent);
				} else {
					// send show chat event
					Intent intent = new Intent(SurespotConstants.EventFilters.SHOW_CHAT_EVENT);
					intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, friendAdapter.getItem(position));
					LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

				}
			}
		});

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				String friend = ((EditText) view.findViewById(R.id.etFriend)).getText().toString();

				SurespotApplication.getNetworkController().invite(friend, new IAsyncCallback<Boolean>() {

					@Override
					public void handleResponse(Boolean result) {
						if (result) {
							// TODO indicate in the UI that the request is pending somehow

						}
					}
				});

			}
		});

		// register for notifications
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				ensureFriendAdapter();

				friendAdapter.add(intent.getStringExtra(SurespotConstants.ExtraNames.FRIEND_ADDED));

			}
		}, new IntentFilter(SurespotConstants.EventFilters.FRIEND_ADDED_EVENT));

		// register for notifications
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				String friendname = intent.getExtras().getString(SurespotConstants.ExtraNames.FRIEND_INVITE_NAME);
				String action = intent.getExtras().getString(SurespotConstants.ExtraNames.FRIEND_INVITE_ACTION);
				// TODO show pending and delete when ignored?
				if (action.equals("accept")) {
					ensureFriendAdapter();

					friendAdapter.add(friendname);
				}

			}
		}, new IntentFilter(SurespotConstants.EventFilters.FRIEND_INVITE_EVENT));

		return view;

	}

	private void ensureFriendAdapter() {
		if (friendAdapter == null) {
			friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
					new ArrayList<String>());
			ListView listView = (ListView) getView().findViewById(R.id.friend_list);
			listView.setAdapter(friendAdapter);
		}
	}
}
