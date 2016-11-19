package com.twofours.surespot.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.twofours.surespot.R;
import com.twofours.surespot.activities.MainActivity;
import com.twofours.surespot.backup.ExportIdentityActivity;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.common.SurespotConfiguration;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.identity.KeyFingerprintDialogFragment;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkController;
import com.twofours.surespot.qr.QRCodeEncoder;
import com.twofours.surespot.qr.WriterException;

import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class UIUtils {

    private static final String TAG = "UIUtils";

    public static AlertDialog passwordDialog(Context context, String title, String message, final IAsyncCallback<String> callback) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(title);
        alert.setMessage(message);
        final EditText editText = new EditText(context);
        editText.setImeActionLabel(context.getString(R.string.done), EditorInfo.IME_ACTION_DONE);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConstants.MAX_PASSWORD_LENGTH)});

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
        return ad;
    }

    public static AlertDialog createAndShowConfirmationDialog(Context context, String message, String title, String positiveButtonText,
                                                              String negativeButtonText, final IAsyncCallback<Boolean> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message).setTitle(title).setPositiveButton(positiveButtonText, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.handleResponse(true);
                }

            }
        });

        if (!TextUtils.isEmpty(negativeButtonText)) {
            builder.setNegativeButton(negativeButtonText, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (callback != null) {
                        callback.handleResponse(false);
                    }

                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        return dialog;

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
        d.getSize(size);
        return size;
    }

    public static void setHtml(Context context, TextView tv, int stringId) {
        tv.setText(Html.fromHtml(context.getString(stringId)));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setLinkTextColor(context.getResources().getColor(R.color.surespotBlue));
    }

    public static void setHtml(Context context, TextView tv, String html) {
        tv.setText(Html.fromHtml(html));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setLinkTextColor(context.getResources().getColor(R.color.surespotBlue));
    }

    public static void setHtml(Context context, TextView tv, Spanned html) {
        tv.setText(Html.toHtml(html));
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setLinkTextColor(context.getResources().getColor(R.color.surespotBlue));
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

    public static void sendInvitation(final Activity context, NetworkController networkController, String username) {
        final String longUrl = buildExternalInviteUrl(username);
        if (longUrl == null) {
            Utils.makeLongToast(context, context.getString(R.string.invite_no_application_found));
            return;
        }

        SurespotLog.v(TAG, "auto invite url length %d:, url: %s ", longUrl.length(), longUrl);

        final SingleProgressDialog progressDialog = new SingleProgressDialog(context, context.getString(R.string.invite_progress_text), 750);

        progressDialog.show();
        networkController.getShortUrl(longUrl, new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                SurespotLog.i(TAG, e, "getShortUrl error");
                launchInviteApp(context, progressDialog, longUrl);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    JSONObject json = null;
                    try {
                        json = new JSONObject(response.body().string());
                    }
                    catch (Exception e) {
                        SurespotLog.i(TAG, e, "getShortUrl error");
                        launchInviteApp(context, progressDialog, longUrl);
                        return;
                    }

                    String sUrl = json.optString("id", null);
                    if (!TextUtils.isEmpty(sUrl)) {
                        launchInviteApp(context, progressDialog, sUrl);
                    }
                    else {
                        launchInviteApp(context, progressDialog, longUrl);
                    }
                }
                else {
                    launchInviteApp(context, progressDialog, longUrl);
                }
            }
        });
    }

    private static void launchInviteApp(Activity context, SingleProgressDialog progressDialog, String url) {
        try {
            Intent intent = null;
            String message = context.getString(R.string.external_invite_message, url);

            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, message);

            if (intent != null) {
                context.startActivity(intent);
            }

            progressDialog.hide();
        }
        catch (ActivityNotFoundException e) {
            progressDialog.hide();
            Utils.makeToast(context, context.getString(R.string.invite_no_application_found));
        }
    }

    private static String buildExternalInviteUrl(String username) {
        try {
            return "https://server.surespot.me/autoinvite/" + URLEncoder.encode(username, "UTF-8") + "/social";
        }
        catch (UnsupportedEncodingException e) {
            SurespotLog.w(TAG, e, "error encoding auto invite url");

        }
        return null;
    }

    public static AlertDialog showQRDialog(Activity activity, String user) {
        LayoutInflater inflator = activity.getLayoutInflater();
        View dialogLayout = inflator.inflate(R.layout.qr_invite_layout, null, false);
        TextView tvQrInviteText = (TextView) dialogLayout.findViewById(R.id.tvQrInviteText);
        ImageView ivQr = (ImageView) dialogLayout.findViewById(R.id.ivQr);

        Spannable s1 = new SpannableString(user);
        s1.setSpan(new ForegroundColorSpan(Color.RED), 0, s1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        String inviteUrl = null;
        try {
            inviteUrl = "https://server.surespot.me/autoinvite/" + URLEncoder.encode(user, "UTF-8") + "/qr_droid";
        }
        catch (UnsupportedEncodingException e) {
            SurespotLog.w(TAG, e, "error encoding auto invite url");
            Utils.makeLongToast(activity, activity.getString(R.string.invite_no_application_found));
            return null;
        }

        tvQrInviteText.setText(TextUtils.concat(activity.getString(R.string.qr_pre_username_help), " ", s1, " ",
                activity.getString(R.string.qr_post_username_help)));

        Bitmap bitmap;
        try {
            bitmap = QRCodeEncoder.encodeAsBitmap(inviteUrl, SurespotConfiguration.getQRDisplaySize());
            ivQr.setImageBitmap(bitmap);
        }
        catch (WriterException e) {
            SurespotLog.w(TAG, e, "generate invite QR");
            return null;

        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(null);
        AlertDialog dialog = builder.create();
        dialog.setView(dialogLayout, 0, 0, 0, 0);
        dialog.show();
        return dialog;
    }

    public static AlertDialog showHelpDialog(final Activity activity, int titleStringId, View view, final boolean firstTime) {
        // show help dialog
        AlertDialog.Builder b = new Builder(activity);
        b.setIcon(R.drawable.surespot_logo).setTitle(activity.getString(titleStringId));
        b.setPositiveButton(R.string.ok, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                // when they click ok we won't nag them again
                Utils.putSharedPrefsBoolean(activity, "helpShownAgain", true);

                // if it's first time show the backup activity
                if (firstTime) {
                    new AsyncTask<Void, Void, Void>() {
                        protected Void doInBackground(Void... params) {

                            Intent intent = new Intent(activity, ExportIdentityActivity.class);
                            activity.startActivity(intent);
                            return null;
                        }
                    }.execute();
                }

            }
        });

        AlertDialog ad = b.create();
        ad.setView(view, 0, 0, 0, 0);
        ad.show();
        return ad;
    }

    public static void showKeyFingerprintsDialog(MainActivity activity, String ourUsername, String theirUsername, String alias) {

        // Create the fragment and show it as a dialog.
        KeyFingerprintDialogFragment newFragment = KeyFingerprintDialogFragment.newInstance(ourUsername, theirUsername, alias);
        newFragment.show(activity.getFragmentManager(), "dialog");

    }

    public static String getFormattedDate(Context context, Date date) {
        return DateFormat.getDateFormat(context).format(date) + " " + DateFormat.getTimeFormat(context).format(date);
    }

    public static String[] getFingerprintArray(String fingerprint) {
        String[] fp = new String[16];

        if (fingerprint.length() == 31) {
            fingerprint = "0" + fingerprint;
        }

        for (int i = 0; i < 16; i++) {
            fp[i] = fingerprint.substring(i * 2, i * 2 + 2);
        }

        return fp;
    }

    public static String md5(byte[] s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = null;
            digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s);
            byte[] messageDigest = digest.digest();

            // Create Hex String
            return new String(Hex.encode(messageDigest));
        }
        catch (NoSuchAlgorithmException e) {
            SurespotLog.i(TAG, e, "md5");
        }
        return "";
    }

    public static AlertDialog showHelpDialog(Activity context, boolean firstTime) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_help, null);
        if (firstTime) {
            TextView helpAgreement = (TextView) view.findViewById(R.id.helpAgreement);
            UIUtils.setHtml(context, helpAgreement, R.string.help_agreement);
            helpAgreement.setVisibility(View.VISIBLE);
        }

        UIUtils.setHelpLinks(context, view);
        return showHelpDialog(context, R.string.surespot_help, view, firstTime);
    }

    public static void updateDateAndSize(Context context, SurespotMessage message, View parentView) {
        if (message.getDateTime() != null) {
            TextView tvTime = (TextView) parentView.findViewById(R.id.messageTime);
            tvTime.setText(DateFormat.getDateFormat(context).format(message.getDateTime()) + " "
                    + DateFormat.getTimeFormat(context).format(message.getDateTime()));

        }

        if (message.getDataSize() != null && message.getDataSize() > 0) {
            TextView voiceTime = (TextView) parentView.findViewById(R.id.messageSize);
            voiceTime.setVisibility(View.VISIBLE);

            // use base 10 definition of kB: http://en.wikipedia.org/wiki/Kilobyte
            float kb = (float) message.getDataSize() / 1000;
            voiceTime.setText(String.format("%d KB", (int) Math.ceil(kb)));
        }
        else {
            TextView voiceTime = (TextView) parentView.findViewById(R.id.messageSize);
            voiceTime.setVisibility(View.GONE);
        }
    }

    public static Notification generateNotification(NotificationCompat.Builder builder, PendingIntent contentIntent, String packageName, String title,
                                                    String message) {
        return generateNotification(builder, contentIntent, packageName, R.drawable.surespot_logo, title, message);
    }

    public static Notification generateNotification(NotificationCompat.Builder builder, PendingIntent contentIntent, String packageName, int iconResId,
                                                    String title, String message) {
        RemoteViews contentView = new RemoteViews(packageName, R.layout.notification);
        contentView.setImageViewResource(R.id.notification_image, iconResId);
        contentView.setTextViewText(R.id.notification_title, title);
        contentView.setTextViewText(R.id.notification_text, message);

        builder.setContentIntent(contentIntent);

        builder.setSmallIcon(iconResId);
        builder.setContentTitle(title);
        builder.setContentText(message);
        Notification notification = builder.build();

        // use big style if supported
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
			notification.contentView = contentView;
		}
		else {
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
		}

        return notification;
    }

    public static void showProgressAnimation(Context context, View view) {
        Animation a = AnimationUtils.loadAnimation(context, R.anim.progress_anim);
        a.setDuration(1000);
        view.clearAnimation();
        view.startAnimation(a);
    }

    // thanks to http://stackoverflow.com/questions/3611457/android-temporarily-disable-orientation-changes-in-an-activity
    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    public static void lockOrientation(Activity activity) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        int height;
        int width;

        Point size = new Point();
        display.getSize(size);
        height = size.y;
        width = size.x;

        switch (rotation) {
            case Surface.ROTATION_90:
                if (width > height) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
                else {
                    activity.setRequestedOrientation(9/* reversePortait */);
                }
                break;
            case Surface.ROTATION_180:
                if (height > width) {
                    activity.setRequestedOrientation(9/* reversePortait */);
                }
                else {
                    activity.setRequestedOrientation(8/* reverseLandscape */);
                }
                break;
            case Surface.ROTATION_270:
                if (width > height) {
                    activity.setRequestedOrientation(8/* reverseLandscape */);
                }
                else {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                break;
            default:
                if (height > width) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
                else {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
        }
    }

    public static void showWhatsNewDialog(Activity context, boolean firstTime) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_help, null);
        if (firstTime) {
            TextView helpAgreement = (TextView) view.findViewById(R.id.helpAgreement);
            UIUtils.setHtml(context, helpAgreement, R.string.help_agreement);
            helpAgreement.setVisibility(View.VISIBLE);
        }

        UIUtils.setHelpLinks(context, view);
        showHelpDialog(context, R.string.surespot_help, view, firstTime);
    }

    public static AlertDialog aliasDialog(Context context, String name, String title, String message, final IAsyncCallback<String> callback) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(title);
        alert.setMessage(message);
        final EditText editText = new EditText(context);
        editText.setImeActionLabel(context.getString(R.string.done), EditorInfo.IME_ACTION_DONE);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(name);
        editText.setSelection(name.length(), name.length());
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SurespotConstants.MAX_USERNAME_LENGTH), new LetterOrDigitOrSpaceInputFilter()});

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
        return ad;
    }

    public static String buildAliasString(String username, String alias) {
        return TextUtils.isEmpty(alias) ? username : alias + " (" + username + ")";
    }

    public static void setTheme(Activity activity) {
        boolean black = Utils.getSharedPrefsBoolean(activity, SurespotConstants.PrefNames.BLACK);
        activity.setTheme(black ? R.style.BlackTheme : R.style.DefaultTheme);
    }

    @SuppressWarnings("deprecation")
    public static Point getDisplaySize(Context context) {
        Point displaySize = null;
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        if (display != null) {
            displaySize = new Point();
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
                displaySize.set(display.getWidth(), display.getHeight());
            }
            else {
                display.getSize(displaySize);
            }
        }

        return displaySize;
    }


}
