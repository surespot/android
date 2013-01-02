package com.twofours.surespot.ui.fragments;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.SherlockListFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.adapters.NotificationArrayAdapter;

public class NotificationListFragment extends SherlockListFragment {
	private NotificationArrayAdapter notificationAdapter;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		// register for notifications
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				ensureNotificationAdapter();
				try {
					notificationAdapter.add(new JSONObject(intent
							.getStringExtra(SurespotConstants.ExtraNames.NOTIFICATION)));

				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, new IntentFilter(SurespotConstants.EventFilters.NOTIFICATION_EVENT));

		return inflater.inflate(R.layout.notification_list_fragment, container, false);
	}

	@Override
	public void onStart() {
		super.onStart();
		// get the list of friends
		SurespotApplication.getNetworkController().getNotifications(new IAsyncCallback<List<JSONObject>>() {

			@Override
			public void handleResponse(List<JSONObject> result) {
				if (result == null)
					return;

				notificationAdapter = new NotificationArrayAdapter(getActivity(), result);
				setListAdapter(notificationAdapter);

			}
		});
	}

	public void notificationReceived(JSONObject notification) {
		ensureNotificationAdapter();

		notificationAdapter.add(notification);
	}

	private void ensureNotificationAdapter() {
		if (notificationAdapter == null) {

			notificationAdapter = new NotificationArrayAdapter(getActivity(), new ArrayList<JSONObject>());
			setListAdapter(notificationAdapter);
		}
	}

}
