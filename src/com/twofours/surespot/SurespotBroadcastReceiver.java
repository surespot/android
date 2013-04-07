package com.twofours.surespot;

import java.net.URLDecoder;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

// test with this: 
// adb -s 192.168.10.137:5555 shell 'am broadcast -a com.android.vending.INSTALL_REFERRER -n com.twofours.surespot/.SurespotBroadcastReceiver --es "referrer" "utm_source=test_source&utm_medium=test_medium&utm_term=test_term&utm_content=ePhrlkCtjf&utm_campaign=test_name"' 

public class SurespotBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = "SurespotBroadcastReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Utils.logIntent(TAG, intent);

		JSONArray referrers = null;
		String sReferrers = Utils.getSharedPrefsString(context, SurespotConstants.PrefNames.REFERRERS);
		if (!TextUtils.isEmpty(sReferrers)) {
			try {
				referrers = new JSONArray(sReferrers);				
			}
			catch (JSONException e) {
				SurespotLog.w(TAG, "onReceive", e);
			}
		}
		
		if (referrers == null) {
			referrers = new JSONArray();
		}

		HashMap<String, String> values = new HashMap<String, String>();
		try {
			if (intent.hasExtra("referrer")) {
				String referrer[] = intent.getStringExtra("referrer").split("&");
				for (String referrerValue : referrer) {
					String keyValue[] = referrerValue.split("=");
					values.put(URLDecoder.decode(keyValue[0]), URLDecoder.decode(keyValue[1]));

				}
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "onReceive", e);
		}

		JSONObject jReferrer = new JSONObject(values);
		SurespotLog.v(TAG, "onReceive, referrer: " + values);
		referrers.put(jReferrer);
		
		Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.REFERRERS, referrers.toString());

	}

}
