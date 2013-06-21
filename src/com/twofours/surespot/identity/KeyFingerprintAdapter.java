package com.twofours.surespot.identity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.ui.ExpandableHeightGridView;
import com.twofours.surespot.ui.UIUtils;

public class KeyFingerprintAdapter extends BaseAdapter {

	List<HashMap<String, String>> mItems;
	int mLayoutId;
	Context mContext;

	public KeyFingerprintAdapter(Context context, int layoutId, List<HashMap<String, String>> myItems) {
		mContext = context;
		mLayoutId = layoutId;
		mItems = myItems;
	}

	// public KeyFingerprintAdapter(Context context, List<? extends Map<String, String>> data, int resource, String[] from, int[] to) {
	// super(context, data, resource, from, to);
	// mContext = context;
	// mItems = data;
	//
	//
	// }

	@Override
	public View getView(int position, View view, ViewGroup parent) {

		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		view = inflater.inflate(mLayoutId, parent, false);

		Map<String, String> items = mItems.get(position);
		TextView tvVersion = (TextView) view.findViewById(R.id.keyVersion);
		TextView tvTime = (TextView) view.findViewById(R.id.lastVerifiedDate);
		ExpandableHeightGridView gvDH = (ExpandableHeightGridView) view.findViewById(R.id.dhFingerprint);

		ExpandableHeightGridView gvDSA = (ExpandableHeightGridView) view.findViewById(R.id.dsaFingerprint);

		tvVersion.setText(items.get("version"));

		if (tvTime != null) {
			tvTime.setText(items.get("lastVerified"));
		}

		ArrayAdapter<String> dhAdapter = new ArrayAdapter<String>(mContext, R.layout.fingerprint_cell, UIUtils.getFingerprintArray(items.get("DHFingerprint")));

		gvDH.setAdapter(dhAdapter);
		gvDH.setExpanded(true);

		ArrayAdapter<String> dsaAdapter = new ArrayAdapter<String>(mContext, R.layout.fingerprint_cell,
				UIUtils.getFingerprintArray(items.get("DSAFingerprint")));

		gvDSA.setAdapter(dsaAdapter);
		gvDSA.setExpanded(true);
		return view;

	}

	@Override
	public int getCount() {
		return mItems.size();
	}

	@Override
	public Object getItem(int position) {

		return mItems.get(position);
	}

	@Override
	public long getItemId(int position) {

		return position;
	}
}
