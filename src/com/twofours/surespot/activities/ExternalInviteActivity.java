package com.twofours.surespot.activities;

import java.net.URLEncoder;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.content.CursorLoader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;

public class ExternalInviteActivity extends SherlockActivity {

	public static final String TAG = "ExternalInviteActivity";

	private ListView mContactList;
	private boolean mShowInvisible;
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
				Intent intent = new Intent(Intent.ACTION_PICK);
				if (rbEmail.isChecked()) {
					intent.setData(Email.CONTENT_URI);
				}
				else {
					intent.setData(Phone.CONTENT_URI);
				}
				startActivityForResult(intent, SurespotConstants.IntentRequestCodes.PICK_CONTACT);

			}
		});

		bSendInvitation.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				final String contactData = ExternalInviteActivity.this.mEtInviteeData.getText().toString();
				final String message = mEtInviteMessage.getText().toString();

				if (contactData != null && !contactData.isEmpty()) {
					final boolean email = rbEmail.isChecked();

					// create link
					MainActivity.getNetworkController().getAutoInviteUrl((email ? "email" : "sms"), new AsyncHttpResponseHandler() {
						public void onSuccess(int statusCode, String content) {
							// TODO persist somewhere
							String autoinviteurl = content;
							Intent intent = new Intent(Intent.ACTION_SENDTO);

							if (email) {
								intent.setData(Uri.parse("mailto:" + contactData));
								intent.putExtra(Intent.EXTRA_SUBJECT, "surespot invitation");
								intent.putExtra(Intent.EXTRA_TEXT, message + "\n\nPlease click\n\n" + autoinviteurl
										+ "\n\non your android device to install surespot.");

							}
							else {
								intent.setData(Uri.parse("smsto:" + contactData));
								intent.putExtra("sms_body", message + " download surespot here: " + autoinviteurl);
							}
							startActivity(intent);

						};
					});

				}

			}
		});

		// Populate the contact list
		// populateContactList();
	}

	private String buildExternalInviteUrl(String autoAddToken, boolean isEmail) {
		String url = "https://play.google.com/store/apps/details?id=com.twofours.surespot&referrer=";
		String query = "utm_source=surespot_android&utm_medium=" + (isEmail ? "email" : "sms");
		query += "&utm_content=" + autoAddToken;

		String eUrl = url + URLEncoder.encode(query);
		SurespotLog.v(TAG, "play store url length: " + eUrl.length() + ", url: " + eUrl);
		return eUrl;

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case SurespotConstants.IntentRequestCodes.PICK_CONTACT:

				Utils.logIntent(TAG, data);

				Uri result = data.getData();
				SurespotLog.v(TAG, "contact url: " + result.toString());
				String id,
				name,
				phone = null,
				hasPhone,
				email = null;
				int idx,
				colIdx;
				Cursor cursor = getContentResolver().query(result, null, null, null, null);
				cursor.moveToFirst();
				String columns[] = cursor.getColumnNames();
				for (String column : columns) {
					int index = cursor.getColumnIndex(column);
					SurespotLog.v(TAG, "Column: " + column + " == [" + cursor.getString(index) + "]");

				}

				idx = cursor.getColumnIndex(ContactsContract.Contacts.Data.DATA1);
				String data1 = cursor.getString(idx);

				// idx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
				// id = cursor.getString(idx);
				//
				// idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
				// name = cursor.getString(idx);
				//
				// idx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
				// hasPhone = cursor.getString(idx);

				// SurespotLog.v(TAG, "id: " + id + ", name: " + name + ", phone: " + phone + " email: " + email);

				mEtInviteeData.setText(data1);
			}
		}
	}

	/**
	 * Populate the contact list based on account currently selected in the account spinner.
	 */
	private void populateContactList() {
		// Build adapter with contact entries
		Cursor cursor = getContacts();
		String[] fields = new String[] { ContactsContract.Data.DISPLAY_NAME };
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.contact_entry, cursor, fields,
				new int[] { R.id.contactEntryText });
		mContactList.setAdapter(adapter);
	}

	/**
	 * Obtains the contact list for the currently selected account.
	 * 
	 * @return A cursor for for accessing the contact list.
	 */
	private Cursor getContacts() {
		// Run query
		Uri uri = ContactsContract.Contacts.CONTENT_URI;
		String[] projection = new String[] { ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME };
		String selection = ContactsContract.Contacts.IN_VISIBLE_GROUP + " = '" + (mShowInvisible ? "0" : "1") + "'";
		String[] selectionArgs = null;
		String sortOrder = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

		CursorLoader cl = new CursorLoader(this, uri, projection, selection, selectionArgs, sortOrder);
		return cl.loadInBackground();
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
