package com.twofours.surespot;

import junit.framework.Assert;
import android.app.Application;
import android.content.Context;

public class SurespotApplication extends Application {
    private static Context context;
    private static EncryptionController encryptionController;

    public void onCreate(){
        super.onCreate();
        SurespotApplication.context = getApplicationContext();
        
        //create controllers
        encryptionController = new EncryptionController();
    }

    public static Context getAppContext() {
        return SurespotApplication.context;
    }
	
    public static EncryptionController getEncryptionController() { 
    	return encryptionController;
    }
	
}
