package com.twofours.surespot;

import java.security.Security;

import android.app.Application;
import android.content.Context;

import com.twofours.surespot.chat.ChatController;

public class SurespotApplication extends Application {
	private static Context context;
	
	private static ChatController chatController;
	
	public void onCreate() {
		super.onCreate();

		
		Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());

		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
		
		SurespotApplication.context = getApplicationContext();

		chatController = new ChatController();
	

	}

	public static Context getAppContext() {
		return SurespotApplication.context;
	}

	
	public static ChatController getChatController() {
		return chatController;
	}



}
