package com.twofours.surespot.fragments;

import java.util.List;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class FriendListFragment extends ListFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		// get the list of friends
		SurespotApplication.getNetworkController().getFriends(new IAsyncNetworkResultCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null) {
					return;
				}
				setListAdapter((new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, result)));
			}
		});

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.friend_list_fragment, container, false);
	}
}
