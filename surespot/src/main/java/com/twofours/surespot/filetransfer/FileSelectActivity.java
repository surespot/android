package com.twofours.surespot.filetransfer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.twofours.surespot.R;
import com.twofours.surespot.SurespotConstants;
import com.twofours.surespot.SurespotLog;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.utils.Utils;

import java.io.File;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

public class FileSelectActivity extends Activity {
    private static final String TAG = "FileSelectActivity";
    public static final int SOURCE_EXISTING_IMAGE = 1;
    public static final int IMAGE_SIZE_LARGE = 0;
    public static final int IMAGE_SIZE_SMALL = 1;
    private static final String COMPRESS_SUFFIX = "compress";
    private ImageViewTouch mImageView;
    private Button mSendButton;
    private Button mCancelButton;
    private String mPath;
    private String mTo;
    private String mFrom;
    private String mToAlias;

    private RelativeLayout mFrame;
    private LinearLayout mButtonFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean black = Utils.getSharedPrefsBoolean(this, SurespotConstants.PrefNames.BLACK);
        this.setTheme(black ? R.style.TranslucentBlack : R.style.TranslucentDefault);
        SurespotLog.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_select);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        mImageView = (ImageViewTouch) this.findViewById(R.id.imageViewer);
        mSendButton = (Button) this.findViewById(R.id.send);
        mCancelButton = (Button) this.findViewById(R.id.cancel);
        mButtonFrame = (LinearLayout) this.findViewById(R.id.buttonFrame);
        mFrame = (RelativeLayout) this.findViewById(R.id.frame);
        mButtonFrame.setVisibility(View.GONE);

        getActionBar().hide();

        mSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendImage();
                finish();
            }
        });

        mCancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        if (savedInstanceState != null) {
            mTo = savedInstanceState.getString("to");
            mToAlias = savedInstanceState.getString("toAlias");
            mFrom = savedInstanceState.getString("from");

            mPath = savedInstanceState.getString("path");

            setTitle();
            setButtonText();
        }

        boolean start = getIntent().getBooleanExtra("start", false);
        if (start) {
            getIntent().putExtra("start", false);
            mTo = getIntent().getStringExtra("to");
            mToAlias = getIntent().getStringExtra("toAlias");
            mFrom = getIntent().getStringExtra("from");

            setTitle();
            setButtonText();
            String plural = "";
            Intent intent = new Intent();
            intent.setType("*/*");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                plural = "s";
            }

            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.setAction(Intent.ACTION_GET_CONTENT);
            SurespotLog.d(TAG, "startActivityForResult");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_file) + plural), SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FILE);
        }

    }

    private void setTitle() {


        Utils.configureActionBar(this, getString(R.string.select_file), mToAlias, false);


    }

    private void setButtonText() {
        mSendButton.setText(R.string.send);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        SurespotLog.d(TAG, "onActivityResult, requestCode: %d", requestCode);
        if (resultCode == RESULT_OK) {
            if (requestCode == SurespotConstants.IntentRequestCodes.REQUEST_SELECT_FILE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && data.getClipData() != null) {
                    handleMultipleImageSelection(data);
                }
                else if (data.getData() != null) {

                    //String realPath = ChatUtils.getRealPathFromURI(this, data.getData());
                    //SurespotLog.d(TAG, "chos, realPath: %s, uri: %s", realPath, data.getData());
                    mPath = data.getDataString();
                    sendImage();
                    finish();
                }
                else {
                    SurespotLog.i(TAG, "Not able to support multiple file selection and no appropriate data returned from file picker");
                    Utils.makeLongToast(FileSelectActivity.this, getString(R.string.could_not_select_image));
                    finish();
                }
            }
            else {
                finish();
            }
        }
        else {
            finish();
        }
    }

    private void sendImage() {

        ChatController cc = ChatManager.getChatController(mFrom);
        if (cc == null) {
            //TODO notify user?
            return;
        }

        FileTransferUtils.uploadFileAsync(FileSelectActivity.this, cc,mPath, mFrom, mTo);

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    //returns true if the images were sent
    private void handleMultipleImageSelection(final Intent data) {
        final ClipData clipData = data.getClipData();
        final int itemCount = clipData.getItemCount();


        new AsyncTask<Void, Void, Integer>() {
            //returns < 0 if finish
            //0 if handled
            //or > 1 for images not handled
            @Override
            protected Integer doInBackground(Void... params) {
                int errorCount = 0;

                ChatController cc = ChatManager.getChatController(mFrom);
                if (cc == null) {
                    errorCount = itemCount;
                    return errorCount;
                }

                for (int n = 0; n < itemCount; n++) {
                    Uri uri = clipData.getItemAt(n).getUri();
                    // scale, compress and save the image

                    FileTransferUtils.uploadFileAsync(FileSelectActivity.this, cc, new File(uri.getPath()).getAbsolutePath(), mFrom, mTo);

                }
                return errorCount;
            }

            @Override
            protected void onPostExecute(Integer errorCount) {
                if (errorCount > 0) {
                    Utils.makeLongToast(FileSelectActivity.this, String.format(getString(R.string.did_not_send_x_images), errorCount, itemCount));
                }
                finish();
            }
        }.execute();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPath != null) {
            outState.putString("path", mPath);
        }
        outState.putString("to", mTo);
        outState.putString("toAlias", mToAlias);
        outState.putString("from", mFrom);

    }
}
