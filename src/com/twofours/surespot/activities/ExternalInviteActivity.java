package com.twofours.surespot.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
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
import com.loopj.android.http.AsyncHttpResponseHandler;
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

	/**
	 * Called when the activity is first created. Responsible for initializing the UI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		SurespotLog.v(TAG, "Activity State: onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_external_invite);

		Utils.configureActionBar(this, "invite", IdentityController.getLoggedInUser(), true);

		RadioButton rbInviteSMS = (RadioButton) findViewById(R.id.rbInviteSMS);
		final RadioButton rbEmail = (RadioButton) findViewById(R.id.rbEmail);
		mEtInviteMessage = (EditText) findViewById(R.id.inviteMessage);
		mEtInviteMessage
				.setText("Dude! Check out this sick app! It allows for encrypted end to end communication. Take your privacy back!");

		mEtInviteeData = (EditText) findViewById(R.id.invitee);
		mEtInviteeData.setFilters(new InputFilter[] { new InputFilter.LengthFilter(80) });
		Button bSelectContact = (Button) findViewById(R.id.bSelectContact);
		Button bSendInvitation = (Button) findViewById(R.id.bSendInvitation);
		final TextView tvInviteViaLabel = (TextView) findViewById(R.id.tbInviteViaLabel);

		OnClickListener rbClickListener = new OnClickListener() {

			@Override
			public void onClick(View view) {
				// Is the button now checked?
				boolean checked = ((RadioButton) view).isChecked();

				// Check which radio button was clicked
				switch (view.getId()) {
				case R.id.rbEmail:
					if (checked) {
						tvInviteViaLabel.setText("enter email address:");
						mEtInviteeData.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
					}
					break;
				case R.id.rbInviteSMS:
					if (checked) {
						tvInviteViaLabel.setText("enter phone number:");
						mEtInviteeData.setInputType(InputType.TYPE_CLASS_PHONE);

					}
					break;
				}

				mEtInviteeData.setText("");
			}
		};
		rbEmail.setOnClickListener(rbClickListener);
		rbInviteSMS.setOnClickListener(rbClickListener);
		rbEmail.setChecked(true);
		tvInviteViaLabel.setText("enter email address:");
		mEtInviteeData.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

		bSelectContact.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(ExternalInviteActivity.this,ContactPickerActivity.class);		
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
				final String contactData = ExternalInviteActivity.this.mEtInviteeData.getText().toString();
				final String message = mEtInviteMessage.getText().toString();

				if (!TextUtils.isEmpty(contactData)) {
					final boolean email = rbEmail.isChecked();

					// create link
					MainActivity.getNetworkController().getAutoInviteUrl((email ? "email" : "sms"), new AsyncHttpResponseHandler() {
						public void onSuccess(int statusCode, String content) {
							// TODO persist somewhere
							String autoinviteurl = content;

							if (email) {
								Intent intent = new Intent(Intent.ACTION_SENDTO);
								intent.setData(Uri.parse("mailto:" + contactData));
								intent.putExtra(Intent.EXTRA_SUBJECT, "surespot invitation");
								intent.putExtra(Intent.EXTRA_TEXT, message + "\n\nPlease click\n\n" + autoinviteurl
										+ "\n\non your android device to install surespot.");
								startActivity(intent);
							}
							else {
								Intent intent = new Intent(Intent.ACTION_VIEW);
								intent.setType("vnd.android-dir/mms-sms");
								intent.putExtra("address", contactData);
								intent.putExtra("sms_body", message + " download surespot here: " + autoinviteurl);
								startActivity(intent);
							}
						};
					});
				}
			}
		});

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case SurespotConstants.IntentRequestCodes.PICK_CONTACT:

				Utils.logIntent(TAG, data);

				Uri result = data.getData();
				// SurespotLog.v(TAG, "contact url: " + result.toString());

				Cursor cursor = getContentResolver().query(result, null, null, null, null);
				if (cursor.moveToFirst()) {

					int idx = cursor.getColumnIndex(ContactsContract.Contacts.Data.DATA1);
					String data1 = cursor.getString(idx);

					mEtInviteeData.setText(data1);
				}
				else {
					// TODO tell user
					Utils.makeToast(this, "could not get contact data");
				}
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
};
