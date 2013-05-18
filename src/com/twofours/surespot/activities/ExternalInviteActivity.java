package com.twofours.surespot.activities;

import java.util.ArrayList;

import org.json.JSONObject;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.contacts.ContactPickerActivity;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.ui.SingleProgressDialog;

public class ExternalInviteActivity extends SherlockActivity {

	public static final String TAG = "ExternalInviteActivity";

	private EditText mEtInviteeData;
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
	private boolean mScrolled;
	private SingleProgressDialog mMpd;

	/**
	 * Called when the activity is first created. Responsible for initializing the UI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		SurespotLog.v(TAG, "Activity State: onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_external_invite);

		Utils.configureActionBar(this, getString(R.string.invite), IdentityController.getLoggedInUser(), true);

		mMpd = new SingleProgressDialog(this, getString(R.string.invite_progress_text), 750);

		mRbSms = (RadioButton) findViewById(R.id.rbInviteSMS);
		mRbSms.setTag(SHARE_SMS);
		mRbEmail = (RadioButton) findViewById(R.id.rbEmail);
		mRbEmail.setTag(SHARE_EMAIL);
		mRbSocial = (RadioButton) findViewById(R.id.rbSocial);
		mRbSocial.setTag(SHARE_SOCIAL);
		// mCbAutoInvite = (CheckBox) findViewById(R.id.cbAutoInvite);

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

				if (mSelectedType == SHARE_SOCIAL || (mSelectedContacts != null && mSelectedContacts.size() > 0)) {

					mMpd.show();
					final String longUrl = buildExternalInviteUrl(IdentityController.getLoggedInUser(), mSelectedType, true);

					MainActivity.getNetworkController().getShortUrl(longUrl, new JsonHttpResponseHandler() {
						public void onSuccess(int statusCode, JSONObject response) {
							String shortUrl = response.optString("id", null);
							if (TextUtils.isEmpty(shortUrl)) {
								shortUrl = longUrl;
							}
							sendInvitation(shortUrl);
							mMpd.hide();
						};

						public void onFailure(Throwable e, JSONObject errorResponse) {
							SurespotLog.v(TAG, e, "getShortUrl, error: " + errorResponse.toString());
							sendInvitation(longUrl);
							mMpd.hide();
						};
					});
				}
				else {
					if (mSelectedContacts == null || mSelectedContacts.size() == 0) {
						Utils.makeToast(ExternalInviteActivity.this, getString(R.string.no_contact_data_selected_or_entered));
					}
				}
			}
		});

		// give user option to fix sms contact population
		// http://stackoverflow.com/questions/9584136/how-to-click-or-tap-on-a-textview-text-on-different-words

		TextView tvInviteText = (TextView) findViewById(R.id.tvInviteText);
		tvInviteText.setMovementMethod(LinkMovementMethod.getInstance());
		String inviteText = getString(R.string.invite_text);
		tvInviteText.setText(setClickHereListener(inviteText), BufferType.SPANNABLE);

	}

	private SpannableStringBuilder setClickHereListener(String str) {

		int idx1 = str.indexOf("[");
		int idx2 = str.indexOf("]");

		if (idx1 < idx2) {

			String preString = str.substring(0, idx1);
			String linkString = str.substring(idx1 + 1, idx2);
			String endString = str.substring(idx2 + 1, str.length());

			SpannableStringBuilder ssb = new SpannableStringBuilder(preString + linkString + endString);

			ssb.setSpan(new ClickableSpan() {

				@Override
				public void onClick(View widget) {
					SharedPreferences sp = ExternalInviteActivity.this.getSharedPreferences(IdentityController.getLoggedInUser(),
							Context.MODE_PRIVATE);
					boolean altDelimiter = sp.getBoolean("pref_alternate_text_delimiter", false);

					Editor editor = sp.edit();
					editor.putBoolean("pref_alternate_text_delimiter", !altDelimiter);
					editor.commit();

					Utils.makeToast(ExternalInviteActivity.this, getString(R.string.toggled_sms_contact_population_mechanism));
				}
			}, idx1, idx2 - 1, 0);

			return ssb;
		}

		return new SpannableStringBuilder(str);

	}

	@Override
	protected void onResume() {

		super.onResume();

		if (!mScrolled) {
			final ScrollView sv = (ScrollView) findViewById(R.id.svInviteExternal);
			sv.post(new Runnable() {

				@Override
				public void run() {
					sv.smoothScrollTo(0, 0);
					mScrolled = true;
				}
			});

		}
	}

	private void sendInvitation(String shortUrl) {
		try {
			Intent intent;
			String message = getString(R.string.external_invite_message) + shortUrl;
			switch (mSelectedType) {

			case SHARE_EMAIL:
				intent = new Intent(Intent.ACTION_SENDTO);
				// intent.setType("text/plain");
				intent.setData(Uri.parse("mailto:"));
				// intent.putExtra(Intent.EXTRA_EMAIL, new String[] { });
				intent.putExtra(Intent.EXTRA_EMAIL, mSelectedContacts.toArray(new String[mSelectedContacts.size()]));
				intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.invitation_email_subject));
				intent.putExtra(Intent.EXTRA_TEXT, message);
				startActivity(intent);

				break;
			case SHARE_SMS:
				intent = new Intent(Intent.ACTION_VIEW);
				intent.setType("vnd.android-dir/mms-sms");

				// some devices (samsung) sms app don't like semi-colon delimiter
				// http://stackoverflow.com/questions/9721714/android-passing-multiple-numbers-to-sms-intent
				SharedPreferences sp = this.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
				boolean altDelimiter = sp.getBoolean("pref_alternate_text_delimiter", false);
				String delimiter = altDelimiter ? "," : ";";

				StringBuilder addressString = new StringBuilder();
				for (String address : mSelectedContacts) {
					addressString.append(address + delimiter);
				}
				intent.putExtra("address", addressString.toString());
				intent.putExtra("sms_body", message);
				startActivity(intent);
				break;
			case SHARE_SOCIAL:
				intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, message);
				startActivity(intent);
				break;
			}
			finish();
		}
		catch (ActivityNotFoundException e) {
			Utils.makeToast(this, getString(R.string.invite_no_application_found));
		}
	}

	private String buildExternalInviteUrl(String username, int type, boolean autoInvite) {
		String url = "https://server.surespot.me/autoinvite/" + username + "/" + typeToString(type);
		SurespotLog.v(TAG, "auto invite url length %d:, url: %s ", url.length(), url);
		return url;
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
			mTvInviteViaLabel.setText(R.string.invite_also_send_to);
			mEtInviteeData.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
			break;
		case SHARE_SMS:
			mTvInviteViaLabel.setEnabled(true);
			mEtInviteeData.setEnabled(true);
			mBSelectContacts.setEnabled(true);
			mTvInviteViaLabel.setText(R.string.invite_also_send_to);
			mEtInviteeData.setInputType(InputType.TYPE_CLASS_PHONE);
			break;
		case SHARE_SOCIAL:
			mTvInviteViaLabel.setText(R.string.invite_not_used);
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
			mBSelectContacts.setText(getString(R.string.invite_select_contacts) + " (" + String.valueOf(mSelectedContacts.size()) + ")");
		}
	}

	private void setSelectedContacts(ArrayList<String> selectedContacts) {
		mSelectedContacts = selectedContacts;
		if (mSelectedContacts != null) {
			mBSelectContacts.setText(getString(R.string.invite_select_contacts) + " (" + String.valueOf(mSelectedContacts.size()) + ")");
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);
		outState.putInt("type", mSelectedType);
		outState.putStringArrayList("data", mSelectedContacts);
	}
};
