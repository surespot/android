package com.twofours.surespot.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.ui.UIUtils;

public class AboutActivity extends SherlockActivity {

	private static final String TAG = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		Utils.configureActionBar(this, "surespot", "about", true);

		// set version
		PackageManager manager = this.getPackageManager();
		PackageInfo info = null;
		try {
			info = manager.getPackageInfo(this.getPackageName(), 0);
			((TextView) findViewById(R.id.tvAboutVersion)).setText("version: " + info.versionName);

		}
		catch (NameNotFoundException e) {
			SurespotLog.w(TAG, "onCreate", e);
		}

		UIUtils.setHtml(this, (TextView) findViewById(R.id.tvAboutAbout), R.string.about_about);
		UIUtils.setHtml(this, (TextView) findViewById(R.id.tvAboutOpenSource), R.string.about_opensource);
		UIUtils.setHtml(this, (TextView) findViewById(R.id.tvAboutTech), R.string.about_tech);
		UIUtils.setHtml(this, (TextView) findViewById(R.id.tvAboutWebsite), R.string.about_website);
		UIUtils.setHtml(this, (TextView) findViewById(R.id.tvAboutEmail), R.string.about_support);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}

	}
}
