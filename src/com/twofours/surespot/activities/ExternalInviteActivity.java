package com.twofours.surespot.activities;

import java.net.URLEncoder;
import java.util.ArrayList;

import org.json.JSONObject;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.ContactPickerActivity;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

public class ExternalInviteActivity extends SherlockActivity {

	public static final String TAG = "ExternalInviteActivity";

	private EditText mEtInviteeData;
	private EditText mEtInviteMessage;
	private TextView mTvInviteViaLabel;
	private RadioButton mRbEmail;
	private RadioButton mRbSms;
	private RadioButton mRbSocial;
	// private CheckBox mCbAutoInvite;
	private Button mBSelectContacts;
	private ArrayList<String> mSelectedContacts;
	public static final int SHARE_EMAIL = 0;
	public static final int SHARE_SMS = 1;
	public static final int SHARE_SOCIAL = 2;
	private int mSelectedType;

	/**
	 * Called when the activity is first created. Responsible for initializing the UI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		SurespotLog.v(TAG, "Activity State: onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_external_invite);

		Utils.configureActionBar(this, "share", IdentityController.getLoggedInUser(), true);

		mRbSms = (RadioButton) findViewById(R.id.rbInviteSMS);
		mRbSms.setTag(SHARE_SMS);
		mRbEmail = (RadioButton) findViewById(R.id.rbEmail);
		mRbEmail.setTag(SHARE_EMAIL);
		mRbSocial = (RadioButton) findViewById(R.id.rbSocial);
		mRbSocial.setTag(SHARE_SOCIAL);
		// mCbAutoInvite = (CheckBox) findViewById(R.id.cbAutoInvite);
		mEtInviteMessage = (EditText) findViewById(R.id.inviteMessage);
		mEtInviteMessage
				.setText("Dude! Check out this sick app! It allows for encrypted end to end communication. Take your privacy back!");

		mEtInviteeData = (EditText) findViewById(R.id.invitee);
		mEtInviteeData.setFilters(new InputFilter[] { new InputFilter.LengthFilter(80) });
		mBSelectContacts = (Button) findViewById(R.id.bSelectContact);
		Button bSendInvitation = (Button) findViewById(R.id.bSendInvitation);
		mTvInviteViaLabel = (TextView) findViewById(R.id.tbInviteViaLabel);

		OnClickListener rbClickListener = new OnClickListener() {

			@Override
			public void onClick(View view) {
				// Is the button now checked?
				boolean checked = ((RadioButton) view).isChecked();

				if (checked) {
					setInviteType((Integer) view.getTag());
					mEtInviteeData.setText("");
					clearSelectedContacts();
				}
			}
		};

		mRbEmail.setOnClickListener(rbClickListener);
		mRbSms.setOnClickListener(rbClickListener);
		mRbSocial.setOnClickListener(rbClickListener);
		mRbEmail.setChecked(true);

		if (savedInstanceState != null) {
			setSelectedContacts(savedInstanceState.getStringArrayList("data"));
			int type = savedInstanceState.getInt("type");
			setInviteType(type);
		}
		else {
			setInviteType(SHARE_EMAIL);
		}

		mBSelectContacts.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ExternalInviteActivity.this, ContactPickerActivity.class);
				intent.putExtra("type", mSelectedType);
				if (mSelectedContacts != null && mSelectedContacts.size() > 0) {
					intent.putStringArrayListExtra("data", mSelectedContacts);
				}
				try {
					startActivityForResult(intent, SurespotConstants.IntentRequestCodes.PICK_CONTACT);
				}
				catch (ActivityNotFoundException e) {
					SurespotLog.w(TAG, "pick contact", e);
				}
			}
		});

		bSendInvitation.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String contactData = ExternalInviteActivity.this.mEtInviteeData.getText().toString();

				String[] splits = contactData.split(",");
				for (String data : splits) {
					String trimmedData = data.trim();
					if (trimmedData.length() > 0) {
						if (mSelectedContacts == null) {
							mSelectedContacts = new ArrayList<String>();
						}

						if (!mSelectedContacts.contains(trimmedData)) {
							mSelectedContacts.add(0, trimmedData);
						}
					}
				}

				final String message = mEtInviteMessage.getText().toString();

				if (mSelectedType == SHARE_SOCIAL || (mSelectedContacts != null && mSelectedContacts.size() > 0)) {

					final String longUrl = buildExternalInviteUrl(IdentityController.getLoggedInUser(), mSelectedType, true);

					MainActivity.getNetworkController().getShortUrl(longUrl, new JsonHttpResponseHandler() {
						public void onSuccess(int statusCode, JSONObject response) {
							String shortUrl = response.optString("id", null);
							if (TextUtils.isEmpty(shortUrl)) {
								shortUrl = longUrl;
							}
							sendInvitation(message, shortUrl);
						};

						public void onFailure(Throwable e, JSONObject errorResponse) {
							SurespotLog.v(TAG, "getShortUrl, error: " + errorResponse.toString(), e);
							sendInvitation(message, longUrl);
						};
					});
				}
			}
		});
	}

	private void sendInvitation(String message, String shortUrl) {
		Intent intent;
		switch (mSelectedType) {
		case SHARE_EMAIL:
			intent = new Intent(Intent.ACTION_SENDTO);
			// intent.setType("text/plain");
			intent.setData(Uri.parse("mailto:"));
			// intent.putExtra(Intent.EXTRA_EMAIL, new String[] { });
			intent.putExtra(Intent.EXTRA_BCC, mSelectedContacts.toArray(new String[mSelectedContacts.size()]));
			intent.putExtra(Intent.EXTRA_SUBJECT, "surespot invitation");
			intent.putExtra(Intent.EXTRA_TEXT, message + "\n\nPlease click\n\n" + shortUrl
					+ "\n\non your android device to install surespot.");
			startActivity(intent);
			break;
		case SHARE_SMS:
			intent = new Intent(Intent.ACTION_VIEW);
			intent.setType("vnd.android-dir/mms-sms");

			StringBuilder addressString = new StringBuilder();
			for (String address : mSelectedContacts) {
				addressString.append(address + ";");
			}
			intent.putExtra("address", addressString.toString());
			intent.putExtra("sms_body", message + " download surespot here: " + shortUrl);
			startActivity(intent);
			break;
		case SHARE_SOCIAL:
			intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, message + " download surespot here: " + shortUrl);
			startActivity(intent);
			break;
		}
	}

	private String buildExternalInviteUrl(String username, int type, boolean autoInvite) {
		String url = "https://play.google.com/store/apps/details?id=com.twofours.surespot&referrer=";
		String query = "utm_source=surespot_android&utm_medium=" + typeToString(type);

		if (autoInvite) {
			query += "&utm_content=" + username;
		}

		String eUrl = url + URLEncoder.encode(query);		
		SurespotLog.v(TAG, "play store url length %d:, url: %s ", eUrl.length(), eUrl);
		return eUrl;
	}

	public static String typeToString(int type) {
		switch (type) {
		case SHARE_EMAIL:
			return "email";
		case SHARE_SMS:
			return "sms";

		case SHARE_SOCIAL:
			return "social";
		default:
			return "unknown";
		}
	}

	private void setInviteType(int type) {
		mSelectedType = type;
		switch (type) {
		case SHARE_EMAIL:
			mTvInviteViaLabel.setEnabled(true);
			mEtInviteeData.setEnabled(true);
			mBSelectContacts.setEnabled(true);
			mTvInviteViaLabel.setText("bcc to (comma separated):");
			mEtInviteeData.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
			break;
		case SHARE_SMS:
			mTvInviteViaLabel.setEnabled(true);
			mEtInviteeData.setEnabled(true);
			mBSelectContacts.setEnabled(true);
			mTvInviteViaLabel.setText("send to (comma separated):");
			mEtInviteeData.setInputType(InputType.TYPE_CLASS_PHONE);
			break;
		case SHARE_SOCIAL:
			mTvInviteViaLabel.setEnabled(false);
			mEtInviteeData.setEnabled(false);
			mBSelectContacts.setEnabled(false);
			break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case SurespotConstants.IntentRequestCodes.PICK_CONTACT:

				Utils.logIntent(TAG, dataIntent);

				setSelectedContacts(dataIntent.getStringArrayListExtra("data"));

			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}

	private void clearSelectedContacts() {

		if (mSelectedContacts != null) {
			mSelectedContacts.clear();
			mBSelectContacts.setText(String.valueOf(mSelectedContacts.size()));
		}
	}

	private void setSelectedContacts(ArrayList<String> selectedContacts) {
		mSelectedContacts = selectedContacts;
		if (mSelectedContacts != null) {
			mBSelectContacts.setText(String.valueOf(mSelectedContacts.size()));
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);
		outState.putInt("type", mSelectedType);
		outState.putStringArrayList("data", mSelectedContacts);
	}
};
