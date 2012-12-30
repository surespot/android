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
import android.widget.ArrayAdapter;

import com.actionbarsherlock.app.SherlockListFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class FriendListFragment extends SherlockListFragment {
	private ArrayAdapter<String> friendAdapter;
	private static final String TAG = "FriendListFragment";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// get the list of friends
		SurespotApplication.getNetworkController().getFriends(new IAsyncNetworkResultCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null) {
					return;
				}

				friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, result);
				setListAdapter(friendAdapter);

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

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.friend_list_fragment, container, false);
	}

	public void inviteClicked(String username, String action) {
		if (action.equals("accept")) {
			ensureFriendAdapter();

			friendAdapter.add(username);
			//friendAdapter.notifyDataSetChanged();
		}
	}

	private void ensureFriendAdapter() {
		if (friendAdapter == null) {
			friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
					new ArrayList<String>());
			setListAdapter(friendAdapter);
		}
	}
}
