package com.twofours.surespot.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.text.InputFilter;
import android.view.Display;
import android.widget.EditText;
import android.widget.TextView;

import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;
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
	
	public static void createAndShowConfirmationDialog(Context context, String message, String title, String positiveButtonText, String negativeButtonText, final IAsyncCallback<Boolean> callback) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(message).setTitle(title).setPositiveButton(positiveButtonText, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(true);

			}
		}).setNegativeButton(negativeButtonText, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(false);

			}
		});

		builder.create().show();

	}

	
	public static void setMessageErrorText(TextView textView, SurespotMessage message) {
		String statusText = null;
		switch (message.getErrorStatus()) {
		case 400:
			statusText = "error sending message: invalid message";
			break;
		
		case 403:
			statusText = "error sending message: unauthorized";
			break;
		case 404:
			statusText = "error sending message: unauthorized";
			break;
		case 429:
			statusText = "error sending message: throttled";
			break;			
		case 500:
			if (message.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {
				statusText = "error sending message";	
			}
			else {
				statusText = "sending failed - long press to resend";
			}
			
			break;
		}

		textView.setText(statusText);
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
	
	public static void launchMainActivity(Context context) {
		Intent finalIntent = new Intent(context, MainActivity.class);
		finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);		
		context.startActivity(finalIntent);
	}
	
	@SuppressLint("NewApi")
	public static Point getScreenSize(Activity a) {
	    Point size = new Point();
	    Display d = a.getWindowManager().getDefaultDisplay();
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
	        d.getSize(size);
	    } else {
	        size.x = d.getWidth();
	        size.y = d.getHeight();
	    }
	    return size;
	}
}
