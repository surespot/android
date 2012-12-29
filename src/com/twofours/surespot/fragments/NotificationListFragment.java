package com.twofours.surespot.fragments;

import java.util.List;

import org.json.JSONObject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.SherlockListFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.layout.NotificationArrayAdapter;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class NotificationListFragment extends SherlockListFragment {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get the list of friends
		SurespotApplication.getNetworkController().getNotifications(
				new IAsyncNetworkResultCallback<List<JSONObject>>() {

					@Override
					public void handleResponse(List<JSONObject> result) {
						if (result == null)
							return;

						setListAdapter(new NotificationArrayAdapter(getActivity(), result));

					}
				});

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.notification_list_fragment, container, false);
	}
}
