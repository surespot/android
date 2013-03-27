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
	private Button mBSelectContacts;
	private ArrayList<String> mSelectedContacts;

	/**
	 * Called when the activity is first created. Responsible for initializing the UI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		SurespotLog.v(TAG, "Activity State: onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_external_invite);

		Utils.configureActionBar(this, "invite", IdentityController.getLoggedInUser(), true);

		mRbSms = (RadioButton) findViewById(R.id.rbInviteSMS);
		mRbEmail = (RadioButton) findViewById(R.id.rbEmail);
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
					setInviteType(view.getId() == R.id.rbEmail);
					mEtInviteeData.setText("");
					clearSelectedContacts();
				}
			}
		};
		
		mRbEmail.setOnClickListener(rbClickListener);
		mRbSms.setOnClickListener(rbClickListener);
		mRbEmail.setChecked(true);

		if (savedInstanceState != null) {
			setSelectedContacts(savedInstanceState.getStringArrayList("data"));
			boolean isEmail = savedInstanceState.getBoolean("email");
			setInviteType(isEmail);
		}
		else {
			setInviteType(true);
		}

		mBSelectContacts.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ExternalInviteActivity.this, ContactPickerActivity.class);
				intent.putExtra("type", ExternalInviteActivity.this.mRbEmail.isChecked() ? "email" : "sms");
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

				if (mSelectedContacts != null && mSelectedContacts.size() > 0) {

					final boolean email = mRbEmail.isChecked();
					final String longUrl = buildExternalInviteUrl(IdentityController.getLoggedInUser(), email);

					MainActivity.getNetworkController().getShortUrl(longUrl, new JsonHttpResponseHandler() {
						public void onSuccess(int statusCode, JSONObject response) {
							String shortUrl = response.optString("id", null);
							if (TextUtils.isEmpty(shortUrl)) {
								shortUrl = longUrl;
							}
							sendInvitation(message, shortUrl, email);
						};
						
						public void onFailure(Throwable e, JSONObject errorResponse) {
							SurespotLog.v(TAG, "getShortUrl, error: " + errorResponse.toString(), e);
							sendInvitation(message, longUrl, email);
						};
					});
				}
			}			
		});
	}

	private void sendInvitation(String message, String shortUrl, boolean email) {
		if (email) {
			Intent intent = new Intent(Intent.ACTION_SENDTO);
			// intent.setType("text/plain");
			intent.setData(Uri.parse("mailto:"));
			// intent.putExtra(Intent.EXTRA_EMAIL, new String[] { });
			intent.putExtra(Intent.EXTRA_BCC, mSelectedContacts.toArray(new String[mSelectedContacts.size()]));
			intent.putExtra(Intent.EXTRA_SUBJECT, "surespot invitation");
			intent.putExtra(Intent.EXTRA_TEXT, message + "\n\nPlease click\n\n" + shortUrl
					+ "\n\non your android device to install surespot.");
			startActivity(intent);
		}
		else {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setType("vnd.android-dir/mms-sms");

			StringBuilder addressString = new StringBuilder();
			for (String address : mSelectedContacts) {
				addressString.append(address + ";");
			}
			intent.putExtra("address", addressString.toString());
			intent.putExtra("sms_body", message + " download surespot here: " + shortUrl);
			startActivity(intent);
		}
	}

	private String buildExternalInviteUrl(String username, boolean isEmail) {
		String url = "https://play.google.com/store/apps/details?id=com.twofours.surespot&referrer=";
		String query = "utm_source=surespot_android&utm_medium=" + (isEmail ? "email" : "sms");
		query += "&utm_content=" + username;

		String eUrl = url + URLEncoder.encode(query);
		SurespotLog.v(TAG, "play store url length: " + eUrl.length() + ", url: " + eUrl);
		return eUrl;
	}

	private void setInviteType(boolean isEmail) {
		if (isEmail) {
			mTvInviteViaLabel.setText("bcc to (comma separated):");
			mEtInviteeData.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
		}
		else {
			mTvInviteViaLabel.setText("send to (comma separated):");
			mEtInviteeData.setInputType(InputType.TYPE_CLASS_PHONE);
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
		outState.putBoolean("email", mRbEmail.isChecked());
		outState.putStringArrayList("data", mSelectedContacts);
	}
};
