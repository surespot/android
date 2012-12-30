package com.twofours.surespot.fragments;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.actionbarsherlock.app.SherlockListFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
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

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.friend_list_fragment, container, false);
	}
	
	public void inviteClicked(String username, String action) {
		if (action.equals("accept")) {
			if (friendAdapter == null) {
				friendAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, new ArrayList<String>());
				setListAdapter(friendAdapter);
			}
			
			friendAdapter.add(username);
			friendAdapter.notifyDataSetChanged();
		}
	}
}
