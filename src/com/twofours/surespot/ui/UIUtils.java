package com.twofours.surespot.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.twofours.surespot.R;
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
		editText.setImeActionLabel(context.getString(R.string.done), EditorInfo.IME_ACTION_DONE);
		editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });

		alert.setView(editText);

		alert.setPositiveButton(R.string.ok, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(editText.getText().toString());

			}
		});

		alert.setNegativeButton(R.string.cancel, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				callback.handleResponse(null);

			}
		});

		alert.show();
	}

	public static void createAndShowConfirmationDialog(Context context, String message, String title, String positiveButtonText,
			String negativeButtonText, final IAsyncCallback<Boolean> callback) {
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

	public static void setMessageErrorText(Context context, TextView textView, SurespotMessage message) {
		String statusText = null;
		switch (message.getErrorStatus()) {
		case 400:
			statusText = context.getString(R.string.message_error_invalid);
			break;

		case 403:
			statusText = context.getString(R.string.message_error_unauthorized);
			break;
		case 404:
			statusText = context.getString(R.string.message_error_unauthorized);
			break;
		case 429:
			statusText = context.getString(R.string.error_message_throttled);
			break;
		case 500:
			if (message.getMimeType().equals(SurespotConstants.MimeTypes.TEXT)) {
				statusText = context.getString(R.string.error_message_generic);
			}
			else {
				statusText = context.getString(R.string.error_message_resend);
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

	public static void launchMainActivityDeleted(Context context) {
		Intent finalIntent = new Intent(context, MainActivity.class);
		finalIntent.putExtra("deleted", true);
		finalIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(finalIntent);
	}

	@SuppressLint("NewApi")
	public static Point getScreenSize(Activity a) {
		Point size = new Point();
		Display d = a.getWindowManager().getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			d.getSize(size);
		}
		else {
			size.x = d.getWidth();
			size.y = d.getHeight();
		}
		return size;
	}

	public static void setHtml(Context context, TextView tv, int stringId) {
		tv.setText(Html.fromHtml(context.getString(stringId)));
		tv.setMovementMethod(LinkMovementMethod.getInstance());
	}

	public static void setHtml(Context context, TextView tv, String html) {
		tv.setText(Html.fromHtml(html));
		tv.setMovementMethod(LinkMovementMethod.getInstance());
	}
	
	public static void setHtml(Context context, TextView tv, Spanned html) {
		tv.setText(Html.toHtml(html));
		tv.setMovementMethod(LinkMovementMethod.getInstance());
	}

	public static void disableImmediateChildren(ViewGroup layout) {
		for (int i = 0; i < layout.getChildCount(); i++) {
			View child = layout.getChildAt(i);
			child.setEnabled(false);
		}

	}

	public static Spannable createColoredSpannable(String text, int color) {
		Spannable s1 = new SpannableString(text);
		s1.setSpan(new ForegroundColorSpan(color), 0, s1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		return s1;
		
	}
	
	public static void setHelpLinks(Context context, View view) {
		TextView tvBackupWarning = (TextView) view.findViewById(R.id.backupIdentitiesWarning);		
		Spannable s1 = new SpannableString(context.getString(R.string.help_backupIdentities1));
	    s1.setSpan(new ForegroundColorSpan(Color.RED), 0, s1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	    
	    tvBackupWarning.setText(s1);
	    
	    TextView tvWelcome = (TextView) view.findViewById(R.id.tvWelcome);
		UIUtils.setHtml(context, tvWelcome, R.string.welcome_to_surespot);
	
	}

}
