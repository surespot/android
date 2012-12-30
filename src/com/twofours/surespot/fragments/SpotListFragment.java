package com.twofours.surespot.fragments;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.actionbarsherlock.app.SherlockListFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

public class SpotListFragment extends SherlockListFragment {
	private ArrayAdapter<String> spotAdapter;
	private static final String TAG = "SpotListFragment";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// get the list of friends
		SurespotApplication.getNetworkController().getSpots(new IAsyncNetworkResultCallback<List<String>>() {

			@Override
			public void handleResponse(List<String> result) {
				if (result == null) {
					return;
				}

				spotAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, result);
				setListAdapter(spotAdapter);

			}
		});

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.spot_list_fragment, container, false);
	}

	private void ensureSpotAdapter() {
		if (spotAdapter == null) {
			spotAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
					new ArrayList<String>());
			setListAdapter(spotAdapter);
		}
	}
}
