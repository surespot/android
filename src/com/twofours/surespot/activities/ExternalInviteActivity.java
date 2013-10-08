package com.twofours.surespot.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.contacts.ContactPickerActivity;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.ui.UIUtils;

public class ExternalInviteActivity extends SherlockActivity {

	public static final String TAG = "ExternalInviteActivity";

	private RadioButton mRbEmail;
	private RadioButton mRbSms;
	private RadioButton mRbSocial;
	public static final int SHARE_EMAIL = 0;
	public static final int SHARE_SMS = 1;
	public static final int SHARE_SOCIAL = 2;
	private int mSelectedType;
	private View mbInvite;
	private View mbNext;

	/**
	 * Called when the activity is first created. Responsible for initializing the UI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		SurespotLog.v(TAG, "Activity State: onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_external_invite);

		Utils.configureActionBar(this, getString(R.string.invite), IdentityController.getLoggedInUser(), true);

		mRbSms = (RadioButton) findViewById(R.id.rbInviteSMS);
		mRbSms.setTag(SHARE_SMS);
		mRbEmail = (RadioButton) findViewById(R.id.rbEmail);
		mRbEmail.setTag(SHARE_EMAIL);
		mRbSocial = (RadioButton) findViewById(R.id.rbSocial);
		mRbSocial.setTag(SHARE_SOCIAL);

		findViewById(R.id.bFrame).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mSelectedType != SHARE_SOCIAL) {

					Intent intent = new Intent(ExternalInviteActivity.this, ContactPickerActivity.class);
					intent.putExtra("type", mSelectedType);
					startActivity(intent);

				}
				else {
					NetworkController networkController = MainActivity.getNetworkController();
					if (networkController == null) {
						networkController = new NetworkController(ExternalInviteActivity.this, null);
					}
					UIUtils.sendInvitation(ExternalInviteActivity.this, networkController, mSelectedType, null, false);
				}

			}
		});

		mbInvite = findViewById(R.id.bInvite);
		mbNext = findViewById(R.id.bNext);

		mRbEmail.setChecked(true);

		OnClickListener rbClickListener = new OnClickListener() {

			@Override
			public void onClick(View view) {
				// Is the button now checked?
				boolean checked = ((RadioButton) view).isChecked();

				if (checked) {

					setButtonText((Integer) view.getTag());

				}
			}
		};

		mRbEmail.setOnClickListener(rbClickListener);
		mRbSms.setOnClickListener(rbClickListener);
		mRbSocial.setOnClickListener(rbClickListener);

		// give user option to fix sms contact population
		// http://stackoverflow.com/questions/9584136/how-to-click-or-tap-on-a-textview-text-on-different-words

		TextView tvInviteText = (TextView) findViewById(R.id.tvInviteText);
		tvInviteText.setMovementMethod(LinkMovementMethod.getInstance());
		String inviteText = getString(R.string.invite_text);
		tvInviteText.setText(setClickHereListener(inviteText), BufferType.SPANNABLE);

		ImageView qrButton = (ImageView) findViewById(R.id.bQR);
		qrButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				UIUtils.showQRDialog(ExternalInviteActivity.this);
			}
		});

	}

	private void setButtonText(int type) {
		mSelectedType = type;
		switch (type) {
		case SHARE_EMAIL:
		case SHARE_SMS:
			mbNext.setVisibility(View.VISIBLE);
			mbInvite.setVisibility(View.GONE);
			break;

		case SHARE_SOCIAL:
			mbNext.setVisibility(View.GONE);
			mbInvite.setVisibility(View.VISIBLE);

			break;
		}
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
					SharedPreferences sp = ExternalInviteActivity.this.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}

	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);
		outState.putInt("type", mSelectedType);
	}
};
