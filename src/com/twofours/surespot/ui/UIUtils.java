package com.twofours.surespot.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.InputFilter;
import android.widget.EditText;

import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.network.IAsyncCallback;

public class UIUtils {

	public static void passwordDialog(Context context, String title, String message, final IAsyncCallback<String> callback) {
		AlertDialog.Builder alert = new AlertDialog.Builder(context);
		alert.setTitle(title);
		alert.setMessage(message);
		final EditText editText = new EditText(context);
		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });

		alert.setView(editText);

		alert.setPositiveButton("ok", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(editText.getText().toString());

			}
		});

		alert.setNegativeButton("cancel", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(null);

			}
		});

		alert.show();
	}
}
