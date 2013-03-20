package com.twofours.surespot.chat;

import java.io.File;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.images.ImageSelectActivity;
import com.twofours.surespot.activities.SettingsActivity;
import com.twofours.surespot.activities.StartupActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.IAsyncCallback;
import com.viewpagerindicator.TitlePageIndicator;

public class ChatActivity extends SherlockFragmentActivity {
	public static final String TAG = "ChatActivity";

	private ChatPagerAdapter mPagerAdapter;
	private ViewPager mViewPager;
	private BroadcastReceiver mMessageBroadcastReceiver;
	private ChatController mChatController;

	private TitlePageIndicator mIndicator;

	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
		super.onNewIntent(intent);
		SurespotLog.v(TAG, "on new intent");
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SurespotLog.v(TAG, "onCreate");

		Utils.logIntent(TAG, getIntent());

		mChatController = MainActivity.getChatController();

		setContentView(R.layout.activity_chat);

		String name = getIntent().getStringExtra(SurespotConstants.ExtraNames.MESSAGE_FROM);
		SurespotLog.v(TAG, "Intent contained name: " + name);

		// if we don't have an intent, see if we have saved chat
		if (name == null) {
			name = Utils.getSharedPrefsString(getApplicationContext(), SurespotConstants.PrefNames.LAST_CHAT);
		}

		mPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager());

		// get the tabs

		boolean foundChat = false;
		ArrayList<String> chatNames = SurespotApplication.getStateController().loadActiveChats();

		if (name != null) {
			for (String chatName : chatNames) {

				if (chatName.equals(name)) {
					foundChat = true;
				}
			}

			if (!foundChat) {
				chatNames.add(name);
				foundChat = true;
			}
		}

		mPagerAdapter.addChatNames(chatNames);

		Utils.configureActionBar(this, "spots", IdentityController.getLoggedInUser(), true);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		mIndicator = (TitlePageIndicator) findViewById(R.id.indicator);
		mIndicator.setViewPager(mViewPager);

		mIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				SurespotLog.v(TAG, "onPageSelected, position: " + position);
				String name = mPagerAdapter.getChatName(position);
				mChatController.setCurrentChat(name, false);

			}
		});
		mViewPager.setOffscreenPageLimit(2);

		if (name != null) {
			int wantedPosition = mPagerAdapter.getChatFragmentPosition(name);
			if (wantedPosition != mViewPager.getCurrentItem()) {
				mViewPager.setCurrentItem(wantedPosition, true);
			}
			mChatController.setCurrentChat(name, true);
		}
		else {
			mChatController.setCurrentChat(getCurrentChatName(), true);
		}

		// register for notifications
		mMessageBroadcastReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				SurespotLog.v(TAG, "onReceiveMessage");
				String sMessage = intent.getExtras().getString(SurespotConstants.ExtraNames.MESSAGE);
				try {
					JSONObject messageJson = new JSONObject(sMessage);
					SurespotMessage message = SurespotMessage.toSurespotMessage(messageJson);
					String otherUser = ChatUtils.getOtherUser(message.getFrom(), message.getTo());
					mPagerAdapter.addChatName(otherUser);
					mIndicator.notifyDataSetChanged();
				}
				catch (JSONException e) {
					SurespotLog.w(TAG, "onReceive", e);
				}
			}
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		SurespotLog.v(TAG, "onResume");

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageBroadcastReceiver,
				new IntentFilter(SurespotConstants.IntentFilters.MESSAGE_RECEIVED));

	//	mChatController.onResume(true);
	}

	@Override
	protected void onPause() {

		super.onPause();
		SurespotLog.v(TAG, "onPause");
		mChatController.onPause();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageBroadcastReceiver);

		// save open tabs
		SurespotApplication.getStateController().saveActiveChats(mPagerAdapter.getChatNames());

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = null;
		switch (item.getItemId()) {
		case android.R.id.home:
			// This is called when the Home (Up) button is pressed
			// in the Action Bar.
			showMain();
			return true;
		case R.id.menu_close_bar:
		case R.id.menu_close:
			closeTab(mViewPager.getCurrentItem());
			return true;

		case R.id.menu_send_image_bar:
		case R.id.menu_send_image:
			intent = new Intent();
			// TODO paid version allows any file
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			Utils.configureActionBar(this, getString(R.string.select_image), getCurrentChatName(), true);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
					SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE);
			return true;
		case R.id.menu_capture_image_bar:
		case R.id.menu_capture_image:
			// case R.id.menu_capture_image_menu:
			intent = new Intent(this, ImageSelectActivity.class);
			intent.putExtra("source", ImageSelectActivity.SOURCE_CAPTURE_IMAGE);
			intent.putExtra("to", getCurrentChatName());
			startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);

			return true;
		case R.id.menu_settings_bar:
		case R.id.menu_settings:
			intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SETTINGS);
			return true;
		case R.id.menu_logout:
		case R.id.menu_logout_bar:			
			IdentityController.logout();
			intent = new Intent(ChatActivity.this, StartupActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
			ChatActivity.this.startActivity(intent);
			finish();

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Uri selectedImageUri = null;
		if (resultCode == RESULT_OK) {
			switch (requestCode) {

			case SurespotConstants.IntentRequestCodes.REQUEST_EXISTING_IMAGE:
				Intent intent = new Intent(this, ImageSelectActivity.class);
				intent.putExtra("source", ImageSelectActivity.SOURCE_EXISTING_IMAGE);
				intent.putExtra("to", getCurrentChatName());
				intent.setData(data.getData());
				startActivityForResult(intent, SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE);

				break;

			case SurespotConstants.IntentRequestCodes.REQUEST_SELECT_IMAGE:
				selectedImageUri = data.getData();
				String to = data.getStringExtra("to");
				final String filename = data.getStringExtra("filename");
				if (selectedImageUri != null) {

					Utils.makeToast(this, getString(R.string.uploading_image));
					ChatUtils.uploadPictureMessageAsync(this, selectedImageUri, to, false, filename, new IAsyncCallback<Boolean>() {
						@Override
						public void handleResponse(Boolean result) {
							if (result) {
								Utils.makeToast(ChatActivity.this, getString(R.string.image_successfully_uploaded));

							}
							else {
								Utils.makeToast(ChatActivity.this, getString(R.string.could_not_upload_image));
							}

							new File(filename).delete();
						}
					});
					break;
				}
			}
		}
	}

	public void closeChat(String username) {
		closeTab(mPagerAdapter.getChatFragmentPosition(username));
	}

	public void closeTab(int position) {
		// TODO remove saved messages

		if (mPagerAdapter.getCount() == 1) {
			mPagerAdapter.removeChat(0, false);
			showMain();
		}
		else {

			mPagerAdapter.removeChat(position, true);
			// when removing the 0 tab, onPageSelected is not fired for some reason so we need to set this stuff
			String name = mPagerAdapter.getChatName(mViewPager.getCurrentItem());
			// mChatController.setCurrentChat(name);

			// if they explicitly close the tab, remove the adapter
			mChatController.destroyChatAdapter(name);
			mIndicator.notifyDataSetChanged();
		}
	}

	private void showMain() {
		Intent parentActivityIntent = new Intent(this, FriendActivity.class);
		parentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(parentActivityIntent);
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.activity_chat, menu);

		if (Camera.getNumberOfCameras() == 0) {
			SurespotLog.v(TAG, "hiding capture image menu option");
			menu.findItem(R.id.menu_capture_image_bar).setVisible(false);
		}

		return true;
	}

	public String getCurrentChatName() {
		if (mPagerAdapter.getCount() > 0) {
			int pos = mViewPager.getCurrentItem();
			String name = mPagerAdapter.getChatName(pos);
			return name;
		}
		else {
			return null;
		}
	}

	@Override
	protected void onDestroy() {

		super.onDestroy();
		SurespotLog.v(TAG, "onDestroy");
	}

}
