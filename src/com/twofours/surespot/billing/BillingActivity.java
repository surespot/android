package com.twofours.surespot.billing;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.ui.UIUtils;

@SuppressLint("InlinedApi")
public class BillingActivity extends SherlockFragmentActivity {

	protected static final String TAG = "BillingActivity";

	private BillingController mBillingController;
	private IAsyncCallback<Integer> mBillingResponseHandler;
	private ImageView mHomeImageView;
	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_billing);
		Utils.configureActionBar(this, getString(R.string.pay), getString(R.string.what_you_like), true);

		TextView tvPwyl = (TextView) findViewById(R.id.tvPwyl);
		UIUtils.setHtml(this, tvPwyl, R.string.pwyl_text);

		mHomeImageView = (ImageView) findViewById(android.R.id.home);
		if (mHomeImageView == null) {
			mHomeImageView = (ImageView) findViewById(R.id.abs__home);
		}

		mBillingController = SurespotApplication.getBillingController();
		mBillingResponseHandler = new IAsyncCallback<Integer>() {

			@Override
			public void handleResponse(Integer response) {
				hideProgress();
				ViewGroup layout = null;

				switch (response) {
				case BillingController.BILLING_QUERYING_INVENTORY:
					Utils.makeToast(BillingActivity.this, getString(R.string.billing_getting_inventory));

					break;
				case IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE:
					Utils.makeToast(BillingActivity.this, getString(R.string.billing_unavailable_title));
					layout = (ViewGroup) BillingActivity.this.findViewById(R.id.inAppButtons1);
					UIUtils.disableImmediateChildren(layout);
					layout = (ViewGroup) BillingActivity.this.findViewById(R.id.inAppButtons2);
					UIUtils.disableImmediateChildren(layout);

					break;
				case IabHelper.BILLING_RESPONSE_RESULT_ERROR:
				case IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR:
					Utils.makeToast(BillingActivity.this, getString(R.string.billing_error));

					layout = (ViewGroup) BillingActivity.this.findViewById(R.id.inAppButtons1);
					UIUtils.disableImmediateChildren(layout);
					layout = (ViewGroup) BillingActivity.this.findViewById(R.id.inAppButtons2);
					UIUtils.disableImmediateChildren(layout);

					break;

				}

			}
		};

		showProgress();
		mBillingController.setup(getApplicationContext(), false, mBillingResponseHandler);

	}

	private void showProgress() {
		// mMpd.show();
		setProgress(true);

	}

	private void hideProgress() {
		// mMpd.hide();
		setProgress(false);
	}

	public void onPurchase(View arg0) {
		String denom = (String) arg0.getTag();
		// try {
		showProgress();
		// we don't care about purcases on this screen so don't query
		mBillingController.purchase(this, SurespotConstants.Products.PWYL_PREFIX + denom, false, mBillingResponseHandler);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		SurespotLog.d(TAG, "onActivityResult(%s, $s, $s)", requestCode, resultCode, data);

		// Pass on the activity result to the helper for handling
		if (!mBillingController.getIabHelper().handleActivityResult(requestCode, resultCode, data)) {
			// not handled, so handle it ourselves (here's where you'd
			// perform any handling of activity results not related to in-app
			// billing...
			super.onActivityResult(requestCode, resultCode, data);
		}
		else {
			SurespotLog.d(TAG, "onActivityResult handled by IABUtil.");
		}
	}

	private Uri getPayPalUri() {
		Uri.Builder uriBuilder = new Uri.Builder();
		uriBuilder.scheme("https").authority("www.paypal.com").path("cgi-bin/webscr");
		uriBuilder.appendQueryParameter("cmd", "_donations");

		uriBuilder.appendQueryParameter("business", Utils.getResourceString(this, "donations__paypal_user"));
		uriBuilder.appendQueryParameter("lc", "US");
		uriBuilder.appendQueryParameter("item_name", Utils.getResourceString(this, "donations__paypal_item_name"));
		uriBuilder.appendQueryParameter("no_note", "1");
		// uriBuilder.appendQueryParameter("no_note", "0");
		// uriBuilder.appendQueryParameter("cn", "Note to the developer");
		uriBuilder.appendQueryParameter("no_shipping", "1");
		uriBuilder.appendQueryParameter("currency_code", Utils.getResourceString(this, "donations__paypal_currency_code"));
		// uriBuilder.appendQueryParameter("bn", "PP-DonationsBF:btn_donate_LG.gif:NonHosted");
		return uriBuilder.build();

	}

	public void onPaypalBrowser(View arg0) {

		Uri payPalUri = getPayPalUri();
		SurespotLog.d(TAG, "Opening browser with url: %s", payPalUri);

		// Start your favorite browser
		Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
		startActivity(viewIntent);
	}

	public void onPaypalEmail(View arg0) {
		Uri payPalUri = getPayPalUri();
		SurespotLog.d(TAG, "sending email with url: %s", payPalUri);

		final String subject = getString(R.string.billing_paypal_link_email_subject);
		final String body = getString(R.string.billing_paypal_link_email_body) + "\n\n" + payPalUri;

		final ArrayList<String> toEmails = Utils.getToEmails(this);

		toEmails.add(getString(R.string.let_me_select));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.pwyl_send_to_dialog_title).setItems(toEmails.toArray(new String[toEmails.size()]), new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				String choice = toEmails.get(which);
				String email = "";
				if (!choice.equals(getString(R.string.let_me_select))) {
					email = choice;
				}

				Intent intent = new Intent(Intent.ACTION_SENDTO);

				// SurespotLog.v(TAG,"toEmail: " + toEmail);
				// intent.setType("text/plain");
				intent.setData(Uri.parse("mailto:" + email));
				// intent.putExtra(Intent.EXTRA_EMAIL, new String[] { });

				intent.putExtra(Intent.EXTRA_SUBJECT, subject);
				intent.putExtra(Intent.EXTRA_TEXT, body);
				startActivity(intent);
			}

		});

		mDialog = builder.create();
		mDialog.show();
	}

	public void onBitcoinClipboard(View arg0) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

		String bitcoinAddy = Utils.getResourceString(this, "donations__bitcoin");
		clipboard.setText(bitcoinAddy);
		Utils.makeToast(this, getString(R.string.billing_bitcoin_copied_to_clipboard, bitcoinAddy));

	}

	public void onBitcoinEmail(View arg0) {

		String bitcoinAddy = Utils.getResourceString(this, "donations__bitcoin");

		SurespotLog.d(TAG, "sending email with bitcoin");

		final String subject = getString(R.string.billing_bitcoin_email_subject);
		final String body = getString(R.string.billing_bitcoin_email_body_address, bitcoinAddy)
				+ "\n\n"
				+ getString(R.string.billing_bitcoin_email_body_qr, "https://chart.googleapis.com/chart?cht=qr&chl=bitcoin%3A" + bitcoinAddy
						+ "&choe=UTF-8&chs=300x300");

		final ArrayList<String> toEmails = Utils.getToEmails(this);

		toEmails.add(getString(R.string.let_me_select));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.pwyl_send_to_dialog_title).setItems(toEmails.toArray(new String[toEmails.size()]), new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				String choice = toEmails.get(which);
				String email = "";
				if (!choice.equals(getString(R.string.let_me_select))) {
					email = choice;
				}

				Intent intent = new Intent(Intent.ACTION_SENDTO);

				// SurespotLog.v(TAG,"toEmail: " + toEmail);
				// intent.setType("text/plain");
				intent.setData(Uri.parse("mailto:" + email));
				// intent.putExtra(Intent.EXTRA_EMAIL, new String[] { });

				intent.putExtra(Intent.EXTRA_SUBJECT, subject);
				intent.putExtra(Intent.EXTRA_TEXT, body);
				startActivity(intent);
			}

		});

		mDialog = builder.create();
		mDialog.show();

	}

	public void onBitcoinWallet(View arg0) {
		String bitcoinAddy = Utils.getResourceString(this, "donations__bitcoin");

		Uri uri = Uri.parse("bitcoin:" + bitcoinAddy);
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		try {
			startActivity(intent);
		}
		catch (ActivityNotFoundException anfe) {
			Utils.makeToast(this, getString(R.string.could_not_open_bitcoin_wallet));
		}

	}

	// @Override
	// protected void onSaveInstanceState(Bundle outState) {
	//
	// super.onSaveInstanceState(outState);
	//
	// outState.putBoolean("queried", mQueried);
	// }
	//
	// @Override
	// protected void onDestroy() {
	// super.onDestroy();
	// if (mIabHelper != null && mQueried) {
	// try {
	// mIabHelper.dispose();
	// } catch (Exception e) {
	// }
	// }
	//
	// mIabHelper = null;
	// }

	// TODO Factor this out with main activity...
	private void setProgress(boolean inProgress) {
		if (mHomeImageView == null) {
			return;
		}

		SurespotLog.v(TAG, "progress status changed to: %b", inProgress);
		if (inProgress) {

			Animation a = AnimationUtils.loadAnimation(this, R.anim.progress_anim);
			a.setDuration(1000);
			mHomeImageView.clearAnimation();
			mHomeImageView.startAnimation(a);
		}
		else {
			mHomeImageView.clearAnimation();
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

	@Override
	protected void onDestroy() {
		SurespotLog.v(TAG, "onDestroy");
		super.onPause();
		BillingController bc = SurespotApplication.getBillingController();
		if (bc != null) {
			bc.dispose();
		}
	}
	
	@Override
	public void onPause() {		
		super.onPause();
		if (mDialog != null && mDialog.isShowing()) {
			mDialog.dismiss();		
		}
	}
}
