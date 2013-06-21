package com.twofours.surespot.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.ui.ExpandableHeightListView;
import com.twofours.surespot.ui.UIUtils;

public class KeyFingerprintDialogFragment extends SherlockDialogFragment {
	private String mUsername;

	public static KeyFingerprintDialogFragment newInstance(String username) {
		KeyFingerprintDialogFragment f = new KeyFingerprintDialogFragment();

		// Supply num input as an argument.
		Bundle args = new Bundle();
		args.putString("username", username);
		f.setArguments(args);

		return f;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		//
		super.onCreate(savedInstanceState);
		mUsername = getArguments().getString("username");
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.fingerprint_layout, container, false);

		// generate fingerprints for my public keys

		final List<HashMap<String, String>> myItems = new ArrayList<HashMap<String, String>>();
		final SurespotIdentity identity = IdentityController.getIdentity();
		final boolean meFirst = ComparisonChain.start().compare(identity.getUsername().toLowerCase(), mUsername, Ordering.natural()).result() < 0;

		for (PrivateKeyPairs pkp : identity.getKeyPairs()) {
			String version = pkp.getVersion();
			byte[] encodedDHPubKey = pkp.getKeyPairDH().getPublic().getEncoded();
			byte[] encodedDSAPubKey = pkp.getKeyPairDSA().getPublic().getEncoded();

			HashMap<String, String> map = new HashMap<String, String>();
			map.put("version", version);
			map.put("DHFingerprint", Utils.md5(new String(encodedDHPubKey)));
			map.put("DSAFingerprint", Utils.md5(new String(encodedDSAPubKey)));
			myItems.add(map);

		}
		
		Collections.sort(myItems, new Comparator<HashMap<String, String>>() {
			@Override
			public int compare(HashMap<String, String> lhs, HashMap<String, String> rhs) {
				return rhs.get("version").compareTo(lhs.get("version"));
			}
		});

		// generate fingerprints for their public keys

		// do this in background as may have to call network
		new AsyncTask<Void, Void, List<HashMap<String, String>>>() {

			@Override
			protected List<HashMap<String, String>> doInBackground(Void... params) {
				String latestVersion = SurespotApplication.getCachingService().getLatestVersion(mUsername);
				int maxVersion = Integer.parseInt(latestVersion);
				List<HashMap<String, String>> items = new ArrayList<HashMap<String, String>>();
				if (maxVersion > 0) {
					for (int ver = 1; ver <= maxVersion; ver++) {
						String sVer = String.valueOf(ver);
						PublicKeys pubkeys = IdentityController.getPublicKeyPair(mUsername, sVer);

						byte[] encodedDHPubKey = pubkeys.getDHKey().getEncoded();
						byte[] encodedDSAPubKey = pubkeys.getDSAKey().getEncoded();

						HashMap<String, String> map = new HashMap<String, String>();
						map.put("version", sVer);
						map.put("lastVerified", UIUtils.getFormattedDate(getActivity(), new Date(pubkeys.getLastModified())));
						map.put("DHFingerprint", Utils.md5(new String(encodedDHPubKey)));
						map.put("DSAFingerprint", Utils.md5(new String(encodedDSAPubKey)));
						items.add(map);
						
						Collections.sort(items, new Comparator<HashMap<String, String>>() {
							@Override
							public int compare(HashMap<String, String> lhs, HashMap<String, String> rhs) {
								return rhs.get("version").compareTo(lhs.get("version"));
							}
						});
					}

				}
				return items;
			}

			protected void onPostExecute(List<HashMap<String, String>> theirItems) {

				KeyFingerprintAdapter myAdapter = new KeyFingerprintAdapter(getActivity(),  R.layout.fingerprint_item_us, myItems);
				KeyFingerprintAdapter theirAdapter = new KeyFingerprintAdapter(getActivity(), R.layout.fingerprint_item_them, theirItems);

				// order alphabetically to make comparison easier as it will be showing in the same order on both devices

				ExpandableHeightListView lvA = (ExpandableHeightListView) v.findViewById(R.id.aFingerprints);
				lvA.setExpanded(true);
				ExpandableHeightListView lvB = (ExpandableHeightListView) v.findViewById(R.id.bFingerprints);
				lvB.setExpanded(true);

				if (meFirst) {
					lvA.setAdapter(myAdapter);
					lvB.setAdapter(theirAdapter);
				}
				else {
					lvA.setAdapter(theirAdapter);
					lvB.setAdapter(myAdapter);
				}

			};

		}.execute();

		getDialog().setTitle("key fingerprints");

		TextView tvALabel = (TextView) v.findViewById(R.id.aFingerprintsLabel);
		TextView tvBLabel = (TextView) v.findViewById(R.id.bFingerprintsLabel);

		if (meFirst) {
			tvALabel.setText(identity.getUsername() + "'s fingerprints");
			tvBLabel.setText(mUsername + "'s fingerprints");
		}
		else {
			tvALabel.setText(mUsername + "'s fingerprints");
			tvBLabel.setText(identity.getUsername() + "'s fingerprints");

		}

		return v;
	}
}
