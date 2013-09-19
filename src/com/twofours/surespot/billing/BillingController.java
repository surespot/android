package com.twofours.surespot.billing;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.billing.IabHelper.OnConsumeFinishedListener;
import com.twofours.surespot.billing.IabHelper.OnIabPurchaseFinishedListener;
import com.twofours.surespot.billing.IabHelper.OnIabSetupFinishedListener;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class BillingController {
	protected static final String TAG = "BillingController";
	private Context mContext;
	private IabHelper mIabHelper;
	private boolean mQueried;
	private boolean mSetup;
	private boolean mHasVoiceMessagingCapability;
	private String mVoiceMessagePurchaseToken;
	private NetworkController mNetworkController;

	public BillingController(Context context) {
		mContext = context;

		mIabHelper = new IabHelper(context, SurespotConstants.GOOGLE_APP_LICENSE_KEY);
		startSetup(null);

	}

	public void startSetup(final IAsyncCallback<Boolean> callback) {

		if (!mSetup) {
			mIabHelper.startSetup(new OnIabSetupFinishedListener() {

				@Override
				public void onIabSetupFinished(IabResult result) {
					if (!result.isSuccess()) {
						// bollocks
						SurespotLog.v(TAG, "Problem setting up In-app Billing: " + result);
						if (callback != null) {
							callback.handleResponse(false);
						}
						return;
					}

					mSetup = true;

					if (!hasBeenQueried()) {
						SurespotLog.v(TAG, "In-app Billing is a go, querying inventory");
						mIabHelper.queryInventoryAsync(mGotInventoryListener);
					}
					else {
						SurespotLog.v(TAG, "already queried");
					}

					if (callback != null) {
						callback.handleResponse(true);
					}
				}
			});
		}
		else {
			SurespotLog.v(TAG, "In-app Billing already setup");
		}
	}

	public IabHelper getIabHelper() {
		return mIabHelper;
	}

	public boolean hasBeenQueried() {
		return mQueried;
	}

	public boolean hasVoiceMessaging() {
		return mHasVoiceMessagingCapability;
	}

	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
			SurespotLog.d(TAG, "Query inventory finished.");
			if (result.isFailure()) {
				// complain("Failed to query inventory: " + result);
				// hideProgress();
				return;
			}

			SurespotLog.d(TAG, "Query inventory was successful.");

			// consume owned items
			List<Purchase> owned = inventory.getAllPurchases();

			if (owned.size() > 0) {
				SurespotLog.d(TAG, "consuming pwyl purchases");

				List<Purchase> consumables = new ArrayList<Purchase>(owned.size());

				for (Purchase purchase : owned) {
					SurespotLog.v(TAG, "has purchased sku: %s, state: %d, token: %s", purchase.getSku(), purchase.getPurchaseState(), purchase.getToken());

					if (purchase.getSku().equals(SurespotConstants.Products.VOICE_MESSAGING)) {

						if (purchase.getPurchaseState() == 0) {
							setVoiceMessagingToken(purchase.getToken(), null);
						}
					}

					if (isConsumable(purchase.getSku())) {
						consumables.add(purchase);
					}
				}

				mIabHelper.consumeAsync(consumables, new IabHelper.OnConsumeMultiFinishedListener() {

					@Override
					public void onConsumeMultiFinished(List<Purchase> purchases, List<IabResult> results) {
						SurespotLog.d(TAG, "consumed purchases: %s", results);
						// hideProgress();
					}
				});

			}
			else {
				SurespotLog.d(TAG, "no purchases to consume");
				// hideProgress();
			}

			SurespotLog.d(TAG, "Initial inventory query finished.");
			mQueried = true;
		}
	};

	public boolean isSetup() {
		return mSetup;
	}

	public void purchase(Activity activity, final String sku) {
		if (isSetup()) {
			// showProgress();
			getIabHelper().launchPurchaseFlow(activity, sku, SurespotConstants.IntentRequestCodes.PURCHASE, new OnIabPurchaseFinishedListener() {

				@Override
				public void onIabPurchaseFinished(IabResult result, Purchase info) {
					if (result.isFailure()) {
						// hideProgress();
						return;
					}
					SurespotLog.v(TAG, "purchase successful");

					String returnedSku = info.getSku();

					if (returnedSku.equals(SurespotConstants.Products.VOICE_MESSAGING)) {
						setVoiceMessagingToken(info.getToken(), new IAsyncCallback<Boolean>() {
							@Override
							public void handleResponse(Boolean result) {

							}
						});
					}

					if (isConsumable(returnedSku)) {
						getIabHelper().consumeAsync(info, new OnConsumeFinishedListener() {

							@Override
							public void onConsumeFinished(Purchase purchase, IabResult result) {
								SurespotLog.v(TAG, "consumption result: " + result.isSuccess());

							}
						});
					}
				}
			});

		}

	}

	public void setVoiceMessagingToken(String token, IAsyncCallback<Boolean> updateServerCallback) {
		mVoiceMessagePurchaseToken = token;
		mHasVoiceMessagingCapability = true;
		
		if (updateServerCallback != null) {

			// upload to server
			NetworkController networkController = MainActivity.getNetworkController();
			//TODO tell user if we can't update the token on the server tell them to login
			if (networkController != null) {
				networkController.updateVoiceMessagingPurchaseToken(token, new AsyncHttpResponseHandler() {
					@Override
					public void onSuccess(int statusCode, String content) {
						SurespotLog.v(TAG, "successfully updated voice messaging token");
					}
				});
			}

		}
	}

	public String getVoiceMessagingPurchaseToken() {
		return mVoiceMessagePurchaseToken;
	}

	private boolean isConsumable(String sku) {
		if (sku.equals(SurespotConstants.Products.VOICE_MESSAGING)) {
			return false;
		}
		else {
			if (sku.startsWith(SurespotConstants.Products.PWYL_PREFIX)) {
				return true;
			}
			else {

				return false;
			}
		}
	}

}
