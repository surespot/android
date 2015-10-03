package com.twofours.surespot.billing;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.billing.IabHelper.OnConsumeFinishedListener;
import com.twofours.surespot.billing.IabHelper.OnIabPurchaseFinishedListener;
import com.twofours.surespot.billing.IabHelper.OnIabSetupFinishedListener;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;

public class BillingController {
	protected static final String TAG = "BillingController";

	private IabHelper mIabHelper;
	private boolean mQueried;
	private boolean mQuerying;

	private boolean mHasVoiceMessagingCapability;
	//private String mVoiceMessagePurchaseToken;
	//private boolean mJustPurchasedVoice;

	public static final int BILLING_QUERYING_INVENTORY = 100;
	private Context mContext;

	public BillingController(Context context) {
		mContext = context;
		setup(context, true, null);

	}

	public synchronized void setup(Context context, final boolean query, final IAsyncCallback<Integer> callback) {

		if (!mQuerying) {
			if (mIabHelper == null) {
				mIabHelper = new IabHelper(context, SurespotConfiguration.getGoogleApiLicenseKey());
			}
			try {
				mIabHelper.startSetup(new OnIabSetupFinishedListener() {

					@Override
					public void onIabSetupFinished(IabResult result) {
						if (!result.isSuccess()) {
							// bollocks
							SurespotLog.v(TAG, "Problem setting up In-app Billing: " + result);
					//		revokeVoiceMessaging();
							dispose();
							if (callback != null) {
								callback.handleResponse(result.getResponse());
							}
							
							return;
						}

						if (query) {
							// if we haven't queried and we didn't just buy it (it takes a while for google to update their
							// shit and during this time the query says it's not purchased), query now
							if (!mQuerying && !mQueried) {
								SurespotLog.v(TAG, "In-app Billing is a go, querying inventory");
								
								try {
									mIabHelper.queryInventoryAsync(false, mGotInventoryListener);
									synchronized (BillingController.this) {
										mQuerying = true;
									}
								}
								catch (IllegalStateException ise) {
									if (callback != null) {
										callback.handleResponse(BILLING_QUERYING_INVENTORY);
									}
								}
								catch (Exception e) {
									if (callback != null) {
										callback.handleResponse(IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR);
									}
								}

							}
							else {
								SurespotLog.v(TAG, "already queried or querying");
							}
						}

						if (callback != null) {
							callback.handleResponse(result.getResponse());
						}
					}
				});
			}
			// will be thrown if it's already setup
			catch (IllegalStateException ise) {
				if (callback != null) {
					callback.handleResponse(IabHelper.BILLING_RESPONSE_RESULT_OK);
				}
			}
			catch (Exception e) {
				if (callback != null) {
					callback.handleResponse(IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR);
				}
			}
		}
		else {
			SurespotLog.v(TAG, "In-app Billing already setup");
			if (callback != null) {
				callback.handleResponse(IabHelper.BILLING_RESPONSE_RESULT_OK);
			}
		}
	}

	public synchronized IabHelper getIabHelper() {
		return mIabHelper;
	}

	public synchronized boolean hasVoiceMessaging() {
		//free now!
		return true;
		// if we just purchased it, or the purchase query said we have it then we can voice message
		//mHasVoiceMessagingCapability || mJustPurchasedVoice;
	}

	IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
		public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
			SurespotLog.d(TAG, "Query inventory finished.");
			synchronized (BillingController.this) {
				mQuerying = false;
				mQueried = true;
			}

			if (result.isFailure()) {
				SurespotLog.d(TAG, "Query inventory was a failure: %s",result);
				return;
			}

			SurespotLog.d(TAG, "Query inventory was successful.");

			// consume owned items
			List<Purchase> owned = inventory.getAllPurchases();
		//	revokeVoiceMessaging();
			if (owned.size() > 0) {
				SurespotLog.d(TAG, "consuming pwyl purchases");

				List<Purchase> consumables = new ArrayList<Purchase>(owned.size());

				for (Purchase purchase : owned) {
					SurespotLog.d(TAG, "has purchased sku: %s, state: %d, token: %s", purchase.getSku(), purchase.getPurchaseState(), purchase.getToken());

//					if (purchase.getSku().equals(SurespotConstants.Products.VOICE_MESSAGING)) {
//						if (purchase.getPurchaseState() == 0) {
//							setVoiceMessagingToken(purchase.getToken(), false, null);
//						}
//					}

					if (isConsumable(purchase.getSku())) {
						consumables.add(purchase);
					}
				}

				if (consumables.size() > 0) {
					mIabHelper.consumeAsync(consumables, new IabHelper.OnConsumeMultiFinishedListener() {

						@Override
						public void onConsumeMultiFinished(List<Purchase> purchases, List<IabResult> results) {
							SurespotLog.d(TAG, "consumed purchases: %s", results);
						}
					});
				}
			}
			else {
				SurespotLog.d(TAG, "no purchases to consume");
			}

		}
	};

	public void purchase(final Activity activity, final String sku, final boolean query, final IAsyncCallback<Integer> callback) {
		if (!mQuerying) {
			setup(activity, query, new IAsyncCallback<Integer>() {

				@Override
				public void handleResponse(Integer response) {
					if (response != IabHelper.BILLING_RESPONSE_RESULT_OK) {
						callback.handleResponse(response);
					}
					else {
						purchaseInternal(activity, sku, callback);
					}

				}
			});
		}
		else {
			callback.handleResponse(BILLING_QUERYING_INVENTORY);
		}

	}

	private void purchaseInternal(final Activity activity, final String sku, final IAsyncCallback<Integer> callback) {
		try {
			// showProgress();
			getIabHelper().launchPurchaseFlow(activity, sku, SurespotConstants.IntentRequestCodes.PURCHASE, new OnIabPurchaseFinishedListener() {

				@Override
				public void onIabPurchaseFinished(IabResult result, Purchase info) {
					if (result.isFailure()) {
						callback.handleResponse(result.getResponse());
						return;
					}
					SurespotLog.v(TAG, "purchase successful");

					String returnedSku = info.getSku();
//
//					if (returnedSku.equals(SurespotConstants.Products.VOICE_MESSAGING)) {
//						//clear the don't show me again flag
//						SharedPreferences sp = activity.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
//						Editor editor = sp.edit();
//						editor.putBoolean("pref_suppress_voice_purchase_ask", false);
//						editor.commit();
//
//						setVoiceMessagingToken(info.getToken(), true, new IAsyncCallback<Boolean>() {
//							@Override
//							public void handleResponse(Boolean result) {
//								if (result) {
//									SurespotLog.v(TAG, " set server purchase token result: %b", result);
//								}
//
//							}
//						});
//					}

					if (isConsumable(returnedSku)) {
						getIabHelper().consumeAsync(info, new OnConsumeFinishedListener() {

							@Override
							public void onConsumeFinished(Purchase purchase, IabResult result) {
								SurespotLog.v(TAG, "consumption result: %b", result.isSuccess());
								callback.handleResponse(result.getResponse());
							}
						});
					}
					else {
						callback.handleResponse(result.getResponse());
					}

				}
			});
		}
		// handle something else going on
		catch (IllegalStateException e) {
			SurespotLog.w(TAG, e, "could not purchase");
			callback.handleResponse(BILLING_QUERYING_INVENTORY);
		}
		catch (Exception e) {
			callback.handleResponse(IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR);
		}
	}

//	public void setVoiceMessagingToken(String token, boolean justPurchased, IAsyncCallback<Boolean> updateServerCallback) {
//		SurespotLog.v(TAG, "setVoiceMessagingToken: %s, justPurchased: %b", token, justPurchased);
//		synchronized (this) {
//			mJustPurchasedVoice = justPurchased;
//			mVoiceMessagePurchaseToken = token;
//			mHasVoiceMessagingCapability = true;
//		}
//
//		if (updateServerCallback != null) {
//
//			// upload to server
//			NetworkController networkController = SurespotApplication.getNetworkControllerNoThrow();
//			// TODO tell user if we can't update the token on the server tell them to login
//			if (networkController != null) {
//				networkController.updateVoiceMessagingPurchaseToken(new AsyncHttpResponseHandler() {
//					@Override
//					public void onSuccess(int statusCode, String content) {
//						SurespotLog.v(TAG, "successfully updated voice messaging token");
//					}
//				});
//			}
//
//		}
//	}

//	public synchronized String getVoiceMessagingPurchaseToken() {
//		SurespotLog.v(TAG, "getting purchase token: %s", mVoiceMessagePurchaseToken);
//		return mVoiceMessagePurchaseToken;
//	}

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

//	public synchronized void revokeVoiceMessaging() {
//		SurespotLog.v(TAG,"revokeVoiceMessaging");
//		// Will probably have to kill surespot process to re-query after this but oh well
//		mHasVoiceMessagingCapability = false;
//		mVoiceMessagePurchaseToken = null;
//		clearJustPurchased();
//	}

	public synchronized void dispose() {
		SurespotLog.v(TAG, "dispose");
		if (mIabHelper != null && !mIabHelper.mAsyncInProgress) {
			mIabHelper.dispose();
			mIabHelper = null;
		}

		mQueried = false;
		mQuerying = false;
	}

//	public synchronized void clearJustPurchased() {
//		mJustPurchasedVoice = false;
//	}
}
