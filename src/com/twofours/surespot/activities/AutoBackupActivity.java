package com.twofours.surespot.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.twofours.surespot.R;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.ui.UIUtils;

public class AutoBackupActivity extends SherlockActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_auto_backup);
		
		Utils.configureActionBar(this, getString(R.string.identity), getString(R.string.auto_backup_action_bar_right), true);

		String user = IdentityController.getLoggedInUser();	

				
		TextView t1 = (TextView) findViewById(R.id.helpAutoBackup1);		
		Spanned pre = Html.fromHtml(getString(R.string.help_auto_backup_warning_pre));
		Spannable warning = UIUtils.createColoredSpannable(getString(R.string.help_auto_backup_warning), Color.RED);
						
		t1.setText(TextUtils.concat(pre, " ", warning));
		t1.setMovementMethod(LinkMovementMethod.getInstance());

		TextView t2 = (TextView) findViewById(R.id.helpAutoBackup2);
		UIUtils.setHtml(this, t2, R.string.help_auto_backup2);

		final SharedPreferences sp = getSharedPreferences(user, Context.MODE_PRIVATE);
		boolean abEnabled = sp.getBoolean("pref_auto_android_backup_enabled", false);

		CheckBox cb = (CheckBox) findViewById(R.id.cbAutoBackup);
		cb.setChecked(abEnabled);
		cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Editor editor = sp.edit();
				editor.putBoolean("pref_auto_android_backup_enabled", isChecked);
				editor.commit();

				SurespotApplication.mBackupManager.dataChanged();

			}
		});
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
