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

	public static int getResumePosition(int currentPos, int currentSize) {
		// if we have less messages total than the minimum, just return the current position
		if (currentSize <= SurespotConstants.SAVE_MESSAGE_MINIMUM) {
			return currentPos;
		}

		// more messages than minimum meaning we've loaded some
		if (currentPos < SurespotConstants.SAVE_MESSAGE_BUFFER) {
			return currentPos;
		}
		else {
			return SurespotConstants.SAVE_MESSAGE_BUFFER;
		}
		// saveSize += SurespotConstants.SAVE_MESSAGE_BUFFER;
		// int newPos = currentSize - saveSize - SurespotConstants.SAVE_MESSAGE_BUFFER;
		// if (newPos < 0) {
		// newPos = currentPos;
		// }
		// return newPos;

		//
		//
		// int posFromEnd = currentSize - currentPos;
		//
		// // if the relative position is not within minumum messages of the last message
		// if (currentPos > SurespotConstants.SAVE_MESSAGE_BUFFER) {
		//
		// // we'll save messages buffer messages past it (if we can) so come back to this point
		// return currentPos < SurespotConstants.SAVE_MESSAGE_BUFFER ? currentPos : SurespotConstants.SAVE_MESSAGE_BUFFER;
		// }
		// else {
		// // we're inside the minimum so we'll only be saving minimum messages, so reset the position relative to the minumum that will be
		// // loaded
		// return currentPos;
		// }
	}
}
