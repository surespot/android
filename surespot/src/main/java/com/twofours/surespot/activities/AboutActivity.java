package com.twofours.surespot.activities;

import android.os.Bundle;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.ui.UIUtils;

public class AboutActivity extends SherlockActivity {

	private static final String TAG = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		Utils.configureActionBar(this, getString(R.string.surespot), getString(R.string.about_action_bar_right), true);

		// set version
		((TextView) findViewById(R.id.tvAboutVersion)).setText(getString(R.string.about_version, SurespotApplication.getVersion()));

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
