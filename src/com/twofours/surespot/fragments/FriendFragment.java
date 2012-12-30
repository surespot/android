package com.twofours.surespot.fragments;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class FriendFragment extends SherlockFragment {

	private ArrayAdapter<String> friendAdapter;
	private static final String TAG = "FriendFragment";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.friend_fragment, container, false);
		final ListView listView = (ListView) view.findViewById(R.id.friend_list);
		//listView.setEmptyView(view.findViewById(R.id.friend_list_empty));

		// get the list of friends
		SurespotApplication.getNetworkController().getFriends(new IAsyncNetworkResultCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null) {
					return;
				}

				friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, result);
				listView.setAdapter(friendAdapter);

			}
		});

		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				String friend = ((EditText) view.findViewById(R.id.etFriend)).getText().toString();

				SurespotApplication.getNetworkController().invite(friend, new IAsyncNetworkResultCallback<Boolean>() {

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
		return view;
	}

	public void inviteClicked(String username, String action) {
		if (action.equals("accept")) {
			ensureFriendAdapter();

			friendAdapter.add(username);
		}
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
