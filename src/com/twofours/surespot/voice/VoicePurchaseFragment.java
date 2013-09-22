package com.twofours.surespot.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.billing.BillingController;
import com.twofours.surespot.billing.IabHelper;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;

public class VoicePurchaseFragment extends SherlockDialogFragment implements OnClickListener, OnCheckedChangeListener {
	private BillingController mBillingController;
	private IAsyncCallback<Integer> mBillingResponseHandler;
	

	public static SherlockDialogFragment newInstance(boolean comingFromButton) {
		VoicePurchaseFragment f = new VoicePurchaseFragment();
	
		Bundle args = new Bundle();
		args.putBoolean("cameFromButton", comingFromButton);
		f.setArguments(args);

		return f;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		//super.onCreateView(inflater, container, savedInstanceState);
		boolean cameFromButton = getArguments().getBoolean("cameFromButton");

		View view = inflater.inflate(R.layout.voice_purchase_fragment, container, false);
		Button bPurchase = (Button) view.findViewById(R.id.bPurchaseVoice);
		bPurchase.setOnClickListener(this);

		if (cameFromButton) {
			CheckBox cbDontShow = (CheckBox) view.findViewById(R.id.cbDontShow);
			cbDontShow.setVisibility(View.VISIBLE);
			cbDontShow.setOnCheckedChangeListener(this);
		}

		getDialog().setTitle(R.string.purchase_voice_title);

		setupBilling();

		return view;
	}

	private void setupBilling() {
		mBillingController = SurespotApplication.getBillingController();
		mBillingResponseHandler = new IAsyncCallback<Integer>() {

			@Override
			public void handleResponse(Integer response) {
				switch (response) {
				case BillingController.BILLING_QUERYING_INVENTORY:
					Utils.makeToast(getActivity(), getString(R.string.billing_getting_inventory));
					break;
				case IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE:
					Utils.makeToast(getActivity(), getString(R.string.billing_unavailable));
					break;
				case IabHelper.BILLING_RESPONSE_RESULT_ERROR:
				case IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR:
					Utils.makeToast(getActivity(), getString(R.string.billing_error));
					break;

				}

			}
		};

		if (!mBillingController.hasBeenQueried()) {
			mBillingController.setup(getActivity(), true, mBillingResponseHandler);
		}

	}

	@Override
	public void onClick(View v) {
		SurespotApplication.getBillingController().purchase(getActivity(), SurespotConstants.Products.VOICE_MESSAGING, true, mBillingResponseHandler);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		SharedPreferences sp = getActivity().getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
		Editor e = sp.edit();
		e.putBoolean("pref_suppress_voice_purchase_ask", isChecked);
		e.commit();
	}
}
