package com.twofours.surespot.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
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
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.encryption.PrivateKeyPairs;
import com.twofours.surespot.encryption.PublicKeys;
import com.twofours.surespot.ui.ExpandableHeightListView;
import com.twofours.surespot.ui.UIUtils;

public class KeyFingerprintDialogFragment extends SherlockDialogFragment {
	private static final String TAG = "KeyFingerprintDialogFragment";
	private String mUsername;
	private String mAlias;

	public static KeyFingerprintDialogFragment newInstance(String username, String alias) {
		KeyFingerprintDialogFragment f = new KeyFingerprintDialogFragment();

		// Supply num input as an argument.
		Bundle args = new Bundle();
		args.putString("username", username);
		args.putString("alias", alias);
		f.setArguments(args);

		return f;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		//
		super.onCreate(savedInstanceState);
		setStyle(STYLE_NO_TITLE, 0);
		mUsername = getArguments().getString("username");
		mAlias = getArguments().getString("alias");
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View v = inflater.inflate(R.layout.fingerprint_layout, container, false);

		// generate fingerprints for my public keys
		View loadingA = v.findViewById(R.id.loadingA);
		View loadingB = v.findViewById(R.id.loadingB);
		final ExpandableHeightListView lvA = (ExpandableHeightListView) v.findViewById(R.id.aFingerprints);
		lvA.setExpanded(true);
		lvA.setEmptyView(loadingA);
		final ExpandableHeightListView lvB = (ExpandableHeightListView) v.findViewById(R.id.bFingerprints);
		lvB.setExpanded(true);
		lvB.setEmptyView(loadingB);


		final List<HashMap<String, String>> myItems = new ArrayList<HashMap<String, String>>();
		final SurespotIdentity identity = IdentityController.getIdentity(getActivity());
		final boolean meFirst = ComparisonChain.start().compare(identity.getUsername().toLowerCase(), mUsername.toLowerCase(), Ordering.natural()).result() < 0;

		for (PrivateKeyPairs pkp : identity.getKeyPairs()) {
			String version = pkp.getVersion();
			byte[] encodedDHPubKey = pkp.getKeyPairDH().getPublic().getEncoded();
			byte[] encodedDSAPubKey = pkp.getKeyPairDSA().getPublic().getEncoded();

			HashMap<String, String> map = new HashMap<String, String>();
			map.put("version", version);			
			map.put("DHFingerprint", UIUtils.md5(encodedDHPubKey));
			map.put("DSAFingerprint", UIUtils.md5(encodedDSAPubKey));
			myItems.add(map);

		}

		Collections.sort(myItems, new Comparator<HashMap<String, String>>() {
			@Override
			public int compare(HashMap<String, String> lhs, HashMap<String, String> rhs) {
				return (Integer.parseInt(rhs.get("version")) > Integer.parseInt(lhs.get("version")) ? 1 : -1);
			}
		});

		// generate fingerprints for their public keys
		

		// do this in background as may have to call network
		new AsyncTask<Void, Void, List<HashMap<String, String>>>() {

			@Override
			protected List<HashMap<String, String>> doInBackground(Void... params) {
				Activity activity = getActivity();
				if (activity == null) {
					return null;
				}
				
				//get latest version from server							
				String latestVersion = MainActivity.getNetworkController().getKeyVersionSync(mUsername);
				if (latestVersion == null) {
					activity = getActivity();
					if (activity == null) {
						return null;
					}
					
					Utils.makeToast(activity, activity.getString(R.string.could_not_load_public_keys));
					return null;
				}
				
				int maxVersion = Integer.parseInt(latestVersion);
				List<HashMap<String, String>> items = new ArrayList<HashMap<String, String>>();
				if (maxVersion > 0) {
					for (int ver = maxVersion; ver > 0; ver--) {
						String sVer = String.valueOf(ver);
						PublicKeys pubkeys = IdentityController.getPublicKeyPair2(mUsername, sVer);

						if (pubkeys == null) {
							Utils.makeToast(activity, activity.getString(R.string.could_not_load_public_keys));
							return null;
						}
						
						byte[] encodedDHPubKey = pubkeys.getDHKey().getEncoded();
						byte[] encodedDSAPubKey = pubkeys.getDSAKey().getEncoded();

						HashMap<String, String> map = new HashMap<String, String>();
						map.put("version", sVer);
						map.put("lastVerified", UIUtils.getFormattedDate(activity, new Date(pubkeys.getLastModified())));																
						map.put("DHFingerprint", UIUtils.md5(encodedDHPubKey));
						map.put("DSAFingerprint", UIUtils.md5(encodedDSAPubKey));
						items.add(map);

						Collections.sort(items, new Comparator<HashMap<String, String>>() {
							@Override
							public int compare(HashMap<String, String> lhs, HashMap<String, String> rhs) {
								return Integer.parseInt(rhs.get("version")) > Integer.parseInt(lhs.get("version")) ? 1 : -1;
							}
						});
					}

				}
				return items;
			}

			protected void onPostExecute(List<HashMap<String, String>> theirItems) {
				if (theirItems == null) {
					dismiss();
					return;
				}

				KeyFingerprintAdapter myAdapter = new KeyFingerprintAdapter(getActivity(), R.layout.fingerprint_item_us, myItems);
				KeyFingerprintAdapter theirAdapter = new KeyFingerprintAdapter(getActivity(), R.layout.fingerprint_item_them, theirItems);

				// order alphabetically to make comparison easier as it will be showing in the same order on both devices

				
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

		TextView tvALabel = (TextView) v.findViewById(R.id.aFingerprintsLabel);
		TextView tvBLabel = (TextView) v.findViewById(R.id.bFingerprintsLabel);

		if (meFirst) {
			tvALabel.setText(identity.getUsername());
			String bLabelText = UIUtils.buildAliasString(mUsername, mAlias);
			tvBLabel.setText(bLabelText);
		}
		else {
			String aLabelText = UIUtils.buildAliasString(mUsername, mAlias);
			tvALabel.setText(aLabelText);
			tvBLabel.setText(identity.getUsername());

		}

		return v;
	}
}
