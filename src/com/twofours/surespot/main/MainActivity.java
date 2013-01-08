package com.twofours.surespot.main;

import java.util.ArrayList;

import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputFilter;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.LetterOrDigitInputFilter;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.IConnectCallback;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.network.NetworkController;

public class MainActivity extends SherlockActivity {
	private MainAdapter mMainAdapter;
	private static final String TAG = "MainActivity";
	private boolean mDisconnectSocket = true;
	private Toast mToast;

	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.v(TAG, "onCreateView");
		setContentView(R.layout.activity_main);
		//final View view = inflater.inflate(R.layout.friend_fragment, container, false);
		
		
		final ListView listView = (ListView) findViewById(R.id.main_list);
		mMainAdapter = new MainAdapter(this);
		listView.setAdapter(mMainAdapter);
		listView.setEmptyView(findViewById(R.id.main_list_empty));
		// click on friend to join chat
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// start chat activity

				// don't disconnect the socket io
				mDisconnectSocket = false;

				Intent intent = new Intent(MainActivity.this, ChatActivity.class);

				intent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, ((Friend) mMainAdapter.getItem(position)).getName());
				MainActivity.this.startActivity(intent);
				LocalBroadcastManager.getInstance(SurespotApplication.getAppContext()).sendBroadcast(intent);

			}
		});

		Button addFriendButton = (Button) findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		// register for friend aded
		LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mMainAdapter.addFriend(intent.getStringExtra(SurespotConstants.ExtraNames.FRIEND_ADDED), Friend.NEW_FRIEND);
			}
		}, new IntentFilter(SurespotConstants.EventFilters.FRIEND_ADDED_EVENT));

		// register for notifications
		LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {

				mMainAdapter.addFriendInvite(intent.getStringExtra(SurespotConstants.ExtraNames.INVITATION));

			}
		}, new IntentFilter(SurespotConstants.EventFilters.INVITATION_INTENT));

		EditText editText = (EditText) findViewById(R.id.etFriend);
		editText.setFilters(new InputFilter[] { new LetterOrDigitInputFilter() });
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				boolean handled = false;
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					//
					inviteFriend();
					handled = true;
				}
				return handled;
			}
		});
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		ChatController.disconnect();
	}

	@Override
	public void onResume() {
		super.onResume();
		
		ChatController.connect(new IConnectCallback() {

			@Override
			public void connectStatus(boolean status) {
				if (!status) {
					Log.e(TAG, "Could not connect to chat server.");
				}
			}
		});

		//TODO combine into 1 web service call
		// get the list of notifications
		NetworkController.getNotifications(new JsonHttpResponseHandler() {
			public void onSuccess(JSONArray jsonArray) {
				
					for (int i = 0; i < jsonArray.length(); i++) {
						try {
							JSONObject json = jsonArray.getJSONObject(i);
							mMainAdapter.addFriendInvite(json.getString("data"));
						}
						catch (JSONException e) {
							Log.e(TAG, e.toString());
						}

					

				}
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				Log.e(TAG,"getNotifications: " + content);
			//	Toast.makeText(FriendFragment.this.getActivity(), "Error getting notifications.", Toast.LENGTH_SHORT).show();
			}
		});
		// get the list of friends
		NetworkController.getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				

					if (jsonArray.length() > 0) {
						ArrayList<String> friends = null;
						try {
							friends = new ArrayList<String>(jsonArray.length());
							for (int i = 0; i < jsonArray.length(); i++) {
								friends.add(jsonArray.getString(i));
							}
						}
						catch (JSONException e) {
							Log.e(TAG, e.toString());
						}

						mMainAdapter.clearFriends(false);
						mMainAdapter.addFriends(friends, Friend.NEW_FRIEND);
					}
					//

				
			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				Log.e(TAG,"getFriends: " + content);
			//	Toast.makeText(FriendFragment.this.getActivity(), "Error getting friends.", Toast.LENGTH_SHORT).show();
			}
		});

	}

	private void inviteFriend() {
		final EditText etFriend = ((EditText) findViewById(R.id.etFriend));
		final String friend = etFriend.getText().toString();
		if (friend.length() > 0 && !friend.equals(EncryptionController.getIdentityUsername())) {
			NetworkController.invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String arg0) { // TODO
																		// indicate
																		// in
																		// the
																		// UI
					// that the request is
					// pending somehow
					TextKeyListener.clear(etFriend.getText());
					Toast.makeText(MainActivity.this, friend + " has been invited to be your friend.", Toast.LENGTH_SHORT)
							.show();
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						switch (statusCode) {
							case 404:
								Toast.makeText(MainActivity.this, "User does not exist.", Toast.LENGTH_SHORT).show();
								break;
							case 409:
								Toast.makeText(MainActivity.this, "You are already friends.", Toast.LENGTH_SHORT).show();
								break;
							case 403:
								Toast.makeText(MainActivity.this, "You have already invited this user.", Toast.LENGTH_SHORT)
										.show();
								break;
							default:
								Log.e(TAG, "inviteFriend: " + error.getMessage());
							//	Toast.makeText(FriendFragment.this.getActivity(), "Error inviting friend.", Toast.LENGTH_SHORT).show();
						}
					}
					else {
						Log.e(TAG, "inviteFriend: " + content);
					//	Toast.makeText(FriendFragment.this.getActivity(), "Error inviting friend.", Toast.LENGTH_SHORT).show();
					}
				}

			});
		}
	}
	
	private void makeToast(String toast) {
		if (mToast != null) {
			mToast.cancel();
		}
		mToast = Toast.makeText(MainActivity.this, "Error inviting friend.", Toast.LENGTH_SHORT);
		mToast.show();
	}

}
