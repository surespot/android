package com.twofours.surespot.ui;

import java.util.List;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.twofours.surespot.R;
import com.twofours.surespot.activities.ExternalInviteActivity;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.qr.QRCodeEncoder;
import com.twofours.surespot.qr.WriterException;

public class UIUtils {

	private static final String TAG = "UIUtils";

	public static void passwordDialog(Context context, String title, String message, final IAsyncCallback<String> callback) {
		AlertDialog.Builder alert = new AlertDialog.Builder(context);
		alert.setTitle(title);
		alert.setMessage(message);		
		final EditText editText = new EditText(context);
		editText.setImeActionLabel(context.getString(R.string.done), EditorInfo.IME_ACTION_DONE);
		editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
		editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

		editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH) });

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

		AlertDialog ad = alert.create();
		ad.setCanceledOnTouchOutside(false);		
		ad.setView(editText, 0, 0, 0, 0);
		ad.show();
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

	public static void sendInvitation(final Activity context, NetworkController networkController, final int type,
			final List<String> contacts, final boolean finish) {
		final String longUrl = buildExternalInviteUrl(IdentityController.getLoggedInUser(), type, true);
		SurespotLog.v(TAG, "auto invite url length %d:, url: %s ", longUrl.length(), longUrl);

		final SingleProgressDialog progressDialog = new SingleProgressDialog(context, context.getString(R.string.invite_progress_text), 750);

		progressDialog.show();
		networkController.getShortUrl(longUrl, new JsonHttpResponseHandler() {
			public void onSuccess(int statusCode, JSONObject response) {
				String sUrl = response.optString("id", null);
				if (!TextUtils.isEmpty(sUrl)) {
					launchInviteApp(context, progressDialog, type, sUrl, contacts, finish);
				}
				else {
					launchInviteApp(context, progressDialog, type, longUrl, contacts, finish);
				}
			};

			public void onFailure(Throwable e, JSONObject errorResponse) {
				SurespotLog.v(TAG, e, "getShortUrl, error: " + errorResponse.toString());
				launchInviteApp(context, progressDialog, type, longUrl, contacts, finish);
			};
		});

	}

	private static void launchInviteApp(Activity context, SingleProgressDialog progressDialog, int type, String shortUrl,
			List<String> contacts, boolean finish) {
		try {
			Intent intent = null;
			String message = context.getString(R.string.external_invite_message) + shortUrl;
			switch (type) {

			case ExternalInviteActivity.SHARE_EMAIL:
				intent = new Intent(Intent.ACTION_SENDTO);
				// intent.setType("text/plain");
				intent.setData(Uri.parse("mailto:"));
				// intent.putExtra(Intent.EXTRA_EMAIL, new String[] { });
				intent.putExtra(Intent.EXTRA_EMAIL, contacts.toArray(new String[contacts.size()]));
				intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.invitation_email_subject));
				intent.putExtra(Intent.EXTRA_TEXT, message);

				break;
			case ExternalInviteActivity.SHARE_SMS:
				intent = new Intent(Intent.ACTION_VIEW);
				intent.setType("vnd.android-dir/mms-sms");

				// some devices (samsung) sms app don't like semi-colon delimiter
				// http://stackoverflow.com/questions/9721714/android-passing-multiple-numbers-to-sms-intent
				SharedPreferences sp = context.getSharedPreferences(IdentityController.getLoggedInUser(), Context.MODE_PRIVATE);
				boolean altDelimiter = sp.getBoolean("pref_alternate_text_delimiter", false);
				String delimiter = altDelimiter ? "," : ";";

				StringBuilder addressString = new StringBuilder();
				for (String address : contacts) {
					addressString.append(address + delimiter);
				}
				intent.putExtra("address", addressString.toString());
				intent.putExtra("sms_body", message);

				break;
			case ExternalInviteActivity.SHARE_SOCIAL:
				intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, message);

				break;
			}

			if (intent != null) {
				context.startActivity(intent);
			}
			if (finish) {
				context.finish();
			}
			progressDialog.hide();

		}
		catch (ActivityNotFoundException e) {
			progressDialog.hide();
			Utils.makeToast(context, context.getString(R.string.invite_no_application_found));
		}
	}

	private static String buildExternalInviteUrl(String username, int type, boolean autoInvite) {
		String url = "https://server.surespot.me/autoinvite/" + username + "/" + typeToString(type);
		return url;
	}

	private static String typeToString(int type) {
		switch (type) {
		case ExternalInviteActivity.SHARE_EMAIL:
			return "email";
		case ExternalInviteActivity.SHARE_SMS:
			return "sms";

		case ExternalInviteActivity.SHARE_SOCIAL:
			return "social";
		default:
			return "unknown";
		}
	}

	public static void showQRDialog(Activity activity) {
		LayoutInflater inflator = activity.getLayoutInflater();
		View dialogLayout = inflator.inflate(R.layout.qr_invite_layout, null, false);
		TextView tvQrInviteText = (TextView) dialogLayout.findViewById(R.id.tvQrInviteText);
		ImageView ivQr = (ImageView) dialogLayout.findViewById(R.id.ivQr);

		String user = IdentityController.getLoggedInUser();

		Spannable s1 = new SpannableString(user);
		s1.setSpan(new ForegroundColorSpan(Color.RED), 0, s1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		String inviteUrl = "https://server.surespot.me/autoinvite/" + user + "/qr_droid";
		// String qrImageUrl = "https://chart.googleapis.com/chart?cht=qr&chl=" + inviteUrl + "&chs=300x300&chld=Q|0";

		tvQrInviteText.setText(TextUtils.concat(activity.getString(R.string.qr_pre_username_help), " ", s1, " ",
				activity.getString(R.string.qr_post_username_help)));


		Bitmap bitmap;
		try {
			bitmap = QRCodeEncoder.encodeAsBitmap(inviteUrl, 300);
			ivQr.setImageBitmap(bitmap);
		}
		catch (WriterException e) {
			SurespotLog.w(TAG, e, "generate invite QR");
			return;

		}

		AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(null);
		AlertDialog dialog = builder.create();
		dialog.setView(dialogLayout, 0, 0, 0, 0);
		dialog.show();
	}
	
	public static void showHelpDialog(Activity activity, int titleStringId, View view) {
		// show help dialog
		AlertDialog.Builder b = new Builder(activity);
		b.setIcon(R.drawable.surespot_logo).setTitle(activity.getString(titleStringId));
		b.setPositiveButton(R.string.ok, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});

		AlertDialog ad = b.create();		
		ad.setView(view, 0, 0, 0, 0);
		ad.show();

	}
}
