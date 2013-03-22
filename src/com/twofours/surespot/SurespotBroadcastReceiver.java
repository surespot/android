package com.twofours.surespot;

import java.net.URLDecoder;
import java.util.HashMap;

import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;

// test with this: 
// adb -s 192.168.10.137:5555 shell 'am broadcast -a com.android.vending.INSTALL_REFERRER -n com.twofours.surespot/.SurespotBroadcastReceiver --es "referrer" "utm_source=test_source&utm_medium=test_medium&utm_term=test_term&utm_content=ePhrlkCtjf&utm_campaign=test_name"' 

public class SurespotBroadcastReceiver extends BroadcastReceiver {

	private static final String TAG = "SurespotBroadcastReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		Utils.logIntent(TAG, intent);

		HashMap<String, String> values = new HashMap<String, String>();
		try {
			if (intent.hasExtra("referrer")) {
				String referrers[] = intent.getStringExtra("referrer").split("&");
				for (String referrerValue : referrers) {
					String keyValue[] = referrerValue.split("=");
					values.put(URLDecoder.decode(keyValue[0]), URLDecoder.decode(keyValue[1]));

				}
			}
		}
		catch (Exception e) {
		}

		JSONObject jReferrer = new JSONObject(values);
		SurespotLog.v(TAG, "onReceive, referrer: " + values);

		Utils.putSharedPrefsString(context, "referrer", jReferrer.toString());

	}

}
