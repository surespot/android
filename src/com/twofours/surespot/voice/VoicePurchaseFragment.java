package com.twofours.surespot.voice;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.billing.BillingController;
import com.twofours.surespot.billing.IabHelper;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;

public class VoicePurchaseFragment extends SherlockDialogFragment implements OnClickListener, OnCheckedChangeListener {
	private BillingController mBillingController;
	private IAsyncCallback<Integer> mBillingSetupResponseHandler;
	private IAsyncCallback<Integer> mBillingPurchaseResponseHandler;
	private Dialog mDialog;

	public static SherlockDialogFragment newInstance(boolean comingFromButton) {
		VoicePurchaseFragment f = new VoicePurchaseFragment();

		Bundle args = new Bundle();
		args.putBoolean("cameFromButton", comingFromButton);
		f.setArguments(args);

		return f;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mDialog = getDialog();
		final boolean cameFromButton = getArguments().getBoolean("cameFromButton");

		final View view = inflater.inflate(R.layout.voice_purchase_fragment, container, false);
		final Button bPurchase = (Button) view.findViewById(R.id.bPurchaseVoice);
		final Button bOK = (Button) view.findViewById(R.id.bClose);
		bOK.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				dismissAllowingStateLoss();
				
			}
		});

		final CheckBox cbDontShow = (CheckBox) view.findViewById(R.id.cbDontShow);

		SharedPreferences sp = getActivity().getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
		boolean dontShow = sp.getBoolean("pref_suppress_voice_purchase_ask", false);
		cbDontShow.setChecked(dontShow);

		final TextView tvPurchase = (TextView) view.findViewById(R.id.tvPurchase);

		mBillingController = SurespotApplication.getBillingController();

		mBillingPurchaseResponseHandler = new IAsyncCallback<Integer>() {

			@Override
			public void handleResponse(Integer response) {
				switch (response) {
				case IabHelper.BILLING_RESPONSE_RESULT_OK:
					dismissAllowingStateLoss();
					break;

				case BillingController.BILLING_QUERYING_INVENTORY:
					Utils.makeToast(getActivity(), getString(R.string.billing_getting_inventory));
					break;
				case IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE:
					mDialog.setTitle(getString(R.string.billing_unavailable_title));
					tvPurchase.setText(getString(R.string.billing_unavailable_message));
					bPurchase.setVisibility(View.GONE);
					if (cameFromButton) {
						cbDontShow.setVisibility(View.VISIBLE);
						cbDontShow.setOnCheckedChangeListener(VoicePurchaseFragment.this);
					}
					break;
				case IabHelper.BILLING_RESPONSE_RESULT_ERROR:
				case IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR:
					Utils.makeToast(getActivity(), getString(R.string.billing_error));
					dismissAllowingStateLoss();
					break;

				}

			}
		};

		mBillingSetupResponseHandler = new IAsyncCallback<Integer>() {

			@Override
			public void handleResponse(Integer response) {
				switch (response) {
				case IabHelper.BILLING_RESPONSE_RESULT_OK:
					mDialog.setTitle(R.string.purchase_voice_title);
					tvPurchase.setText(getString(R.string.voice_messaging_purchase_1));
					bPurchase.setOnClickListener(VoicePurchaseFragment.this);
					if (cameFromButton) {
						cbDontShow.setVisibility(View.VISIBLE);
						cbDontShow.setOnCheckedChangeListener(VoicePurchaseFragment.this);
					}
					break;

				case BillingController.BILLING_QUERYING_INVENTORY:
					Utils.makeToast(getActivity(), getString(R.string.billing_getting_inventory));
					break;
				case IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE:
					mDialog.setTitle(getString(R.string.billing_unavailable_title));
					tvPurchase.setText(getString(R.string.billing_unavailable_message));
					bPurchase.setVisibility(View.GONE);
					if (cameFromButton) {
						cbDontShow.setVisibility(View.VISIBLE);
						cbDontShow.setOnCheckedChangeListener(VoicePurchaseFragment.this);
					}
					break;
				case IabHelper.BILLING_RESPONSE_RESULT_ERROR:
				case IabHelper.BILLING_RESPONSE_RESULT_DEVELOPER_ERROR:
					Utils.makeToast(getActivity(), getString(R.string.billing_error));
					dismissAllowingStateLoss();
					break;

				}

			}
		};

		mBillingController.setup(getActivity(), true, mBillingSetupResponseHandler);

		return view;

	}

	//
	@Override
	public void onClick(View v) {
		SurespotApplication.getBillingController().purchase(getActivity(), SurespotConstants.Products.VOICE_MESSAGING, true, mBillingPurchaseResponseHandler);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		SharedPreferences sp = getActivity().getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
		Editor e = sp.edit();
		e.putBoolean("pref_suppress_voice_purchase_ask", isChecked);
		e.commit();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		Activity activity = getActivity();
		if (activity != null) {
			MainActivity mactivity = (MainActivity) activity;
			mactivity.setButtonText();
			ChatController cc = MainActivity.getChatController();
			if (cc != null) {
				cc.enableMenuItems(null);
			}
		}
	}
}
