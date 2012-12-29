package com.twofours.surespot.fragments;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.network.IAsyncNetworkResultCallback;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

public class FriendAddFragment extends Fragment {


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.friend_list_fragment, container, false);
		Button addFriendButton = (Button) view.findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				String friend = ((EditText) view.findViewById(R.id.etFriend)).getText().toString();

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
		return view;
		
		
	}
}
