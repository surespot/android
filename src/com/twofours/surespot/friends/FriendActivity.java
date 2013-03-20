package com.twofours.surespot.friends;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputFilter;
import android.text.method.TextKeyListener;
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
import ch.boye.httpclientandroidlib.client.HttpResponseException;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.ui.LetterOrDigitInputFilter;
import com.twofours.surespot.ui.MultiProgressDialog;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.SettingsActivity;
import com.twofours.surespot.activities.StartupActivity;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class FriendActivity extends SherlockActivity {
	private FriendAdapter mMainAdapter;
	private static final String TAG = "FriendActivity";
	private static final int REQUEST_SETTINGS = 1;
	private MultiProgressDialog mMpdInviteFriend;
	private BroadcastReceiver mInvitationReceiver;
	private BroadcastReceiver InviteResponseReceiver;

	private ChatController mChatController;
	private ListView mListView;
	private NotificationManager mNotificationManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurespotLog.v(TAG, "onCreate");

		Utils.logIntent(TAG, getIntent());
		Utils.configureActionBar(this, null, null, false);

		mChatController = MainActivity.getChatController();
		setContentView(R.layout.activity_friend);

		mMpdInviteFriend = new MultiProgressDialog(this, "inviting friend", 750);

		mListView = (ListView) findViewById(R.id.main_list);
		mListView.setEmptyView(findViewById(R.id.progressBar));
		// findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
	
		// click on friend to join chat
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Friend friend = (Friend) mMainAdapter.getItem(position);
				if (friend.isFriend()) {
					// start chat activity
					Intent newIntent = new Intent(FriendActivity.this, ChatActivity.class);
					newIntent.putExtra(SurespotConstants.ExtraNames.MESSAGE_FROM, friend.getName());
					newIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
					// if we have a send intent, when we pick a user, propogate it
					// Get intent, action and MIME type
					Intent intent = getIntent();
					String action = intent.getAction();
					String type = intent.getType();

					if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
						newIntent.setAction(action);
						newIntent.setType(type);
						newIntent.putExtras(intent);

						// remove intent data so we don't ask user to select a user again
						intent.setAction(null);
						intent.setType(null);
					}

					FriendActivity.this.startActivity(newIntent);
				}
			}
		});

		Button addFriendButton = (Button) findViewById(R.id.bAddFriend);
		addFriendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				inviteFriend();
			}
		});

		InviteResponseReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String jsonStringInviteResponse = intent.getStringExtra(SurespotConstants.ExtraNames.INVITE_RESPONSE);
				JSONObject jsonInviteResponse;
				try {
					jsonInviteResponse = new JSONObject(jsonStringInviteResponse);
					String name = jsonInviteResponse.getString("user");
					if (jsonInviteResponse.getString("response").equals("accept")) {
						mMainAdapter.addNewFriend(name);
					}
					else {
						mMainAdapter.removeFriend(name);
					}
				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "onReceive (inviteResponse)", e);
				}

			}
		};

		// register for invites
		mInvitationReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				mMainAdapter.addFriendInviter(intent.getStringExtra(SurespotConstants.ExtraNames.NAME));
			}
		};

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

		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume");

		Utils.logIntent(TAG, getIntent());

		LocalBroadcastManager.getInstance(this).registerReceiver(InviteResponseReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.INVITE_RESPONSE));
		LocalBroadcastManager.getInstance(this).registerReceiver(mInvitationReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.INVITE_REQUEST));

		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();

		if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
			// set the title
			Utils.setActionBarTitles(this, "send", "select recipient");
		}
		else {
			Utils.setActionBarTitles(this, "home", IdentityController.getLoggedInUser());
		}

	//	mChatController.onResume(false);

		// get the list of friends
		MainActivity.getNetworkController().getFriends(new JsonHttpResponseHandler() {
			@Override
			public void onSuccess(JSONArray jsonArray) {
				SurespotLog.v(TAG, "getFriends success.");

				if (jsonArray.length() > 0) {

					mMainAdapter.refreshActiveChats();
					mMainAdapter.clearFriends(false);
					mMainAdapter.addFriends(jsonArray);
					mMainAdapter.sort();
				}

				((ListView) findViewById(R.id.main_list)).setEmptyView(findViewById(R.id.main_list_empty));
				mListView.setAdapter(mMainAdapter);
				findViewById(R.id.progressBar).setVisibility(View.GONE);

			}

			@Override
			public void onFailure(Throwable arg0, String content) {
				// if we didn't get a 401
				if (!MainActivity.getNetworkController().isUnauthorized()) {

					SurespotLog.w(TAG, "getFriends: " + content, arg0);

					Utils.makeToast(FriendActivity.this, "Could not load friends, please try again later.");
					// TODO show error / go back to login

					((ListView) findViewById(R.id.main_list)).setEmptyView(findViewById(R.id.main_list_empty));
					findViewById(R.id.progressBar).setVisibility(View.GONE);
					mListView.setAdapter(mMainAdapter);
				}
			}
		});

		// clear invite response notifications
		mNotificationManager
				.cancel(IdentityController.getLoggedInUser(), SurespotConstants.IntentRequestCodes.INVITE_RESPONSE_NOTIFICATION);
	}

	@Override
	protected void onPause() {
		super.onPause();
		SurespotLog.v(TAG, "onPause");

		LocalBroadcastManager.getInstance(this).unregisterReceiver(InviteResponseReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mInvitationReceiver);

		Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.LAST_CHAT, null);

		// put the active chats in if we've fucked with them
		SurespotApplication.getStateController().saveActiveChats(mMainAdapter.getActiveChats());
		// Utils.putSharedPrefsString(this, SurespotConstants.PrefNames.PREFS_ACTIVE_CHATS, jsonArray.toString());

		mChatController.onPause();
	}

	private void inviteFriend() {
		final EditText etFriend = ((EditText) findViewById(R.id.etFriend));
		final String friend = etFriend.getText().toString();

		if (friend.length() > 0) {
			if (friend.equals(IdentityController.getLoggedInUser())) {
				// TODO let them be friends with themselves?
				Utils.makeToast(this, "You can't be friends with yourself, bro.");
				return;
			}

			mMpdInviteFriend.incrProgress();
			MainActivity.getNetworkController().invite(friend, new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(int statusCode, String arg0) { // TODO
																		// indicate
																		// in
																		// the
																		// UI
					// that the request is
					// pending somehow
					TextKeyListener.clear(etFriend.getText());
					mMainAdapter.addFriendInvited(friend);
					Utils.makeToast(FriendActivity.this, friend + " has been invited to be your friend.");
				}

				@Override
				public void onFailure(Throwable arg0, String content) {
					if (arg0 instanceof HttpResponseException) {
						HttpResponseException error = (HttpResponseException) arg0;
						int statusCode = error.getStatusCode();
						switch (statusCode) {
						case 404:
							Utils.makeToast(FriendActivity.this, "User does not exist.");
							break;
						case 409:
							Utils.makeToast(FriendActivity.this, "You are already friends.");
							break;
						case 403:
							Utils.makeToast(FriendActivity.this, "You have already invited this user.");
							break;
						default:
							SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
							Utils.makeToast(FriendActivity.this, "Could not invite friend, please try again later.");
						}
					}
					else {
						SurespotLog.w(TAG, "inviteFriend: " + content, arg0);
						Utils.makeToast(FriendActivity.this, "Could not invite friend, please try again later.");
					}
				}

				@Override
				public void onFinish() {
					mMpdInviteFriend.decrProgress();
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_friend, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;
		switch (item.getItemId()) {
		case R.id.menu_logout:
			IdentityController.logout();
			intent = new Intent(FriendActivity.this, StartupActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			FriendActivity.this.startActivity(intent);
			Utils.putSharedPrefsString(FriendActivity.this, SurespotConstants.PrefNames.LAST_CHAT, null);
			finish();
			return true;
		case R.id.menu_settings:
			intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, REQUEST_SETTINGS);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		SurespotLog.v(TAG, "onDestroy");
	}
}
