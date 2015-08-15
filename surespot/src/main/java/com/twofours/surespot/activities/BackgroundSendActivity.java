package com.twofours.surespot.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.twofours.surespot.R;
import com.twofours.surespot.chat.ChatController;
import com.twofours.surespot.common.SurespotConstants;

public class BackgroundSendActivity extends Activity {

    public final static String USER_NAME_KEY = "com.twofours.surespot.USER_NAME_KEY";
    public final static String MIME_TYPE_KEY = "com.twofours.surespot.MIME_TYPE_KEY";
    public final static String MESSAGE_KEY = "com.twofours.surespot.MESSAGE_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.Intent intent = getIntent();
        String userName = intent.getStringExtra(BackgroundSendActivity.USER_NAME_KEY);
        String mimeType = intent.getStringExtra(BackgroundSendActivity.MIME_TYPE_KEY);
        String message = intent.getStringExtra(BackgroundSendActivity.MESSAGE_KEY);
        MainActivity.getChatController().sendMessage(userName, message, mimeType);

        finish();
    }
}
