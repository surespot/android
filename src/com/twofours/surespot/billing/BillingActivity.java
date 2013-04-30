package com.twofours.surespot.billing;

import java.util.List;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.twofours.surespot.R;
import com.twofours.surespot.billing.IabHelper.OnConsumeFinishedListener;
import com.twofours.surespot.billing.IabHelper.OnIabPurchaseFinishedListener;
import com.twofours.surespot.billing.IabHelper.OnIabSetupFinishedListener;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

public class BillingActivity extends SherlockFragmentActivity {

	protected static final String TAG = "BillingActivity";

	// (arbitrary) request code for the purchase flow
	static final int RC_REQUEST = 10001;
	private IabHelper mIabHelper;
	private ProgressDialog mMpd;
	private boolean mQueried;
	private ImageView mHomeImageView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_billing);
		Utils.configureActionBar(this, "pay", "what you like", false);

		mHomeImageView = (ImageView) findViewById(android.R.id.home);
		if (mHomeImageView == null) {
			mHomeImageView = (ImageView) findViewById(R.id.abs__home);
		}

		mMpd = new ProgressDialog(this);
		mMpd.setIndeterminate(true);
		// progressDialog.setTitle("loading");
		mMpd.setMessage("hooking it up");

		mIabHelper = new IabHelper(this, SurespotConstants.GOOGLE_APP_LICENSE_KEY);

		if (savedInstanceState != null) {
			mQueried = savedInstanceState.getBoolean("queried");
		}

		showProgress();
		mIabHelper.startSetup(new OnIabSetupFinishedListener() {

			@Override
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					// Oh noes, there was a problem.
					SurespotLog.v(TAG, "Problem setting up In-app Billing: " + result);
					hideProgress();
					return;
				}

				if (!mQueried) {
					mQueried = true;

					SurespotLog.v(TAG, "In-app Billing is a go, querying inventory");
					mIabHelper.queryInventoryAsync(mGotInventoryListener);
				}
				else {
					hideProgress();
					SurespotLog.v(TAG, "already queried");
				}

			}
		});

	}

	private void showProgress() {
		// mMpd.show();
		setProgress(true);

	}

	private void hideProgress() {
		// mMpd.hide();
		setProgress(false);
	}

	// Listener that's called when we finish querying the items and subscriptions we own
	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
			SurespotLog.d(TAG, "Query inventory finished.");
			if (result.isFailure()) {
				// complain("Failed to query inventory: " + result);
				hideProgress();
				return;
			}

			SurespotLog.d(TAG, "Query inventory was successful.");

			// consume owned items
			List<Purchase> owned = inventory.getAllPurchases();

			if (owned.size() > 0) {
				SurespotLog.d(TAG, "consuming purchases");

				mIabHelper.consumeAsync(owned, new IabHelper.OnConsumeMultiFinishedListener() {

					@Override
					public void onConsumeMultiFinished(List<Purchase> purchases, List<IabResult> results) {
						SurespotLog.d(TAG, "consumed purchases: %s", results);
						hideProgress();
					}
				});
			}
			else {
				SurespotLog.d(TAG, "no purchases to consume");
				hideProgress();
			}

			SurespotLog.d(TAG, "Initial inventory query finished.");

		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.billing, menu);

		return true;
	}

	public void onPurchase(View arg0) {
		String denom = (String) arg0.getTag();
		showProgress();
		try {
			mIabHelper.launchPurchaseFlow(this, "pwyl_" + denom, RC_REQUEST, new OnIabPurchaseFinishedListener() {

				@Override
				public void onIabPurchaseFinished(IabResult result, Purchase info) {
					if (result.isFailure()) {
						Utils.makeToast(BillingActivity.this, "error purchasing: " + result);
						hideProgress();
						return;
					}
					// Utils.makeToast(BillingActivity.this, "thank you");
					SurespotLog.v(TAG, "purchase successful, consuming");
					mIabHelper.consumeAsync(info, new OnConsumeFinishedListener() {

						@Override
						public void onConsumeFinished(Purchase purchase, IabResult result) {
							SurespotLog.v(TAG, "consumption result: " + result.isSuccess());
							hideProgress();

						}
					});
				}
			});
		}
		catch (IllegalStateException ise) {
			hideProgress();
			Utils.makeToast(this, "pay what you want already in progress");
			SurespotLog.v(TAG, ise, "onPurchase error");
		}
	}

	public void onPaypal(View arg0) {

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
		Uri payPalUri = uriBuilder.build();

		SurespotLog.d(TAG, "Opening browser with url: %s", payPalUri);

		// Start your favorite browser
		Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
		startActivity(viewIntent);

	}

	public void onBitcoin(View arg0) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		clipboard.setText("this is the bitcoin address");
		Utils.makeToast(this, "bitcoin address copied to clipboard");
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {

		super.onSaveInstanceState(outState);

		outState.putBoolean("queried", mQueried);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mIabHelper != null)
			mIabHelper.dispose();
		mIabHelper = null;
	}

	// TODO Factor this out
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
}
