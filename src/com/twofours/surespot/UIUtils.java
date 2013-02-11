package com.twofours.surespot;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.widget.EditText;

import com.twofours.surespot.network.IAsyncCallback;

public class UIUtils {

	public static void passwordDialog(Context context, String username, final IAsyncCallback<String> callback) {
		AlertDialog.Builder alert = new AlertDialog.Builder(context);
		alert.setTitle("export " + username + " identity");
		alert.setMessage("enter password for " + username);
		final EditText editText = new EditText(context);
		alert.setView(editText);

		alert.setPositiveButton("OK", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(editText.getText().toString());

			}
		});

		alert.setNegativeButton("Cancel", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(null);

			}
		});

		alert.show();
	}
}
