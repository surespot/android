package com.twofours.surespot;

import android.content.Context;
import android.os.AsyncTask;

import com.twofours.surespot.chat.ChatManager;
import com.twofours.surespot.chat.SurespotMessage;
import com.twofours.surespot.encryption.EncryptionController;
import com.twofours.surespot.friends.Friend;
import com.twofours.surespot.identity.IdentityController;
import com.twofours.surespot.images.FileCacheController;
import com.twofours.surespot.network.IAsyncCallback;
import com.twofours.surespot.network.NetworkManager;
import com.twofours.surespot.services.CredentialCachingService;
import com.twofours.surespot.services.CredentialCachingService.SharedSecretKey;
import com.twofours.surespot.services.CredentialCachingService.VersionMap;
import com.twofours.surespot.utils.ChatUtils;
import com.twofours.surespot.utils.FileUtils;
import com.twofours.surespot.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class StateController {
    private static final String MESSAGES_PREFIX = "messages_";
    private static final String UNSENT_MESSAGES = "unsentMessages";
    private static final String FRIENDS = "friends";
    private static final String COOKIE = "cookie";
    private static final String STATE_EXTENSION = ".sss";
    private static final String SECRETS = "secrets";
    private static final String TAG = "StateController";
    private Context mContext;

    public class FriendState {
        public int userControlId;
        public List<Friend> friends;
    }

    public StateController(Context context) {
        mContext = context;
    }

    public synchronized FriendState loadFriends(String username) {
        String filename = getFilename(username, FRIENDS);
        ArrayList<Friend> friends = new ArrayList<Friend>();
        if (filename != null) {
            String sFriendsJson = null;

            try {

                sFriendsJson = new String(FileUtils.readFile(filename));

            }
            catch (FileNotFoundException f) {
                SurespotLog.v(TAG, "loadFriends, no friends file found");
            }
            catch (IOException e1) {
                SurespotLog.w(TAG, e1, "loadFriends");
            }

            if (sFriendsJson != null) {
                SurespotLog.v(TAG, "Loaded friends: %s", sFriendsJson);

                try {
                    JSONObject jsonFriendState = new JSONObject(sFriendsJson);

                    int userControlId = jsonFriendState.getInt("userControlId");
                    JSONArray friendsJson = jsonFriendState.getJSONArray("friends");
                    for (int i = 0; i < friendsJson.length(); i++) {
                        Friend friend = Friend.toFriend(friendsJson.getJSONObject(i));
                        friends.add(friend);
                    }

                    FriendState friendState = new FriendState();
                    friendState.userControlId = userControlId;
                    friendState.friends = friends;
                    return friendState;

                }
                catch (JSONException e) {
                    SurespotLog.w(TAG, e, "loadFriends");
                }
            }
        }
        return null;
    }

    public synchronized void saveFriends(String username, int latestUserControlId, List<Friend> friends) {
        String filename = getFilename(username, FRIENDS);
        if (filename != null) {
            if (friends != null && friends.size() > 0) {

                JSONArray jsonArray = new JSONArray();
                synchronized (friends) {
                    ListIterator<Friend> iterator = friends.listIterator();

                    while (iterator.hasNext()) {
                        Friend friend = iterator.next();
                        jsonArray.put(friend.toJSONObject());
                    }

                    JSONObject jsonFriendState = new JSONObject();
                    try {
                        jsonFriendState.put("userControlId", latestUserControlId);
                        jsonFriendState.put("friends", jsonArray);
                        String sFriends = jsonFriendState.toString();
                        FileUtils.writeFile(filename, sFriends);
                        SurespotLog.v(TAG, "Saved friends: %s", sFriends);
                    }
                    catch (JSONException e) {
                        SurespotLog.w(TAG, e, "saveFriends");
                    }
                    catch (IOException e) {
                        SurespotLog.w(TAG, e, "saveFriends");
                    }
                }
            }
            else {
                new File(filename).delete();
            }
        }
    }

    public synchronized void saveUnsentMessages(String username, Collection<SurespotMessage> messages) {
        String filename = getFilename(username, UNSENT_MESSAGES);
        if (filename != null) {
            if (messages != null && messages.size() > 0) {
                if (messages.size() > 0) {

                    String messageString = ChatUtils.chatMessagesToJson(messages, true).toString();

                    try {
                        FileUtils.writeFile(filename, messageString);
                    }
                    catch (IOException e) {
                        SurespotLog.w(TAG, e, "saveMessageQueue");
                    }
                }
                else {
                    try {
                        new File(filename).delete();
                    }
                    catch (Exception ex) {
                        SurespotLog.w(TAG, ex, "saveMessageQueue");
                    }
                }
            }
            else {
                try {
                    new File(filename).delete();
                }
                catch (Exception ex) {
                    SurespotLog.w(TAG, ex, "saveMessageQueue");
                }
            }
        }

    }

    public synchronized List<SurespotMessage> loadUnsentMessages(String username) {
        String filename = getFilename(username, UNSENT_MESSAGES);
        ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
        if (filename != null) {
            String sUnsentMessages = null;

            try {
                sUnsentMessages = new String(FileUtils.readFile(filename));
            }
            catch (FileNotFoundException f) {
                SurespotLog.v(TAG, "loadUnsentMessages, no unsent messages file found");
            }
            catch (IOException e) {
                SurespotLog.w(TAG, e, "loadUnsentMessages");
            }
            if (sUnsentMessages != null) {
                Iterator<SurespotMessage> iterator = ChatUtils.jsonStringToChatMessages(sUnsentMessages).iterator();

                while (iterator.hasNext()) {
                    messages.add(iterator.next());
                }
                SurespotLog.v(TAG, "loaded: %d unsent messages.", messages.size());
            }
        }
        return messages;

    }

    public synchronized void saveMessages(String user, String spot, List<SurespotMessage> messages) {
        SurespotLog.v(TAG, "saveMessages, spot %s", spot);
        String filename = getFilename(user, MESSAGES_PREFIX + spot);

        if (filename != null) {
            if (messages != null) {
                synchronized (messages) {
                    int messagesSize = messages.size();
                    int saveCount = Math.min(SurespotConfiguration.SAVE_MESSAGE_MINIMUM, messagesSize);

                    SurespotLog.v(TAG, "saving %d messages for spot %s", saveCount, spot);
                    String sMessages = ChatUtils.chatMessagesToJson(
                            messages.subList(messagesSize - saveCount, messagesSize), true)
                            .toString();

                    try {
                        FileUtils.writeFile(filename, sMessages);
                    }
                    catch (IOException e) {
                        SurespotLog.w(TAG, e, "saveMessages");
                    }
                }
            }
            else {
                try {
                    new File(filename).delete();
                }
                catch (Exception ex) {
                    SurespotLog.w(TAG, ex, "saveMessages");
                }
            }
        }
    }

    public synchronized ArrayList<SurespotMessage> loadMessages(String user, String spot) {
        String filename = getFilename(user, MESSAGES_PREFIX + spot);
        ArrayList<SurespotMessage> messages = new ArrayList<SurespotMessage>();
        if (filename != null) {
            String sMessages = null;

            try {
                sMessages = new String(FileUtils.readFile(filename));
            }
            catch (FileNotFoundException f) {
                SurespotLog.v(TAG, "loadMessages, no messages file found for: %s", spot);
            }
            catch (IOException e) {
                SurespotLog.w(TAG, e, "loadMessages");
            }
            if (sMessages != null) {
                Iterator<SurespotMessage> iterator = ChatUtils.jsonStringToChatMessages(sMessages).iterator();
                while (iterator.hasNext()) {
                    SurespotMessage message = iterator.next();
                    messages.add(message);
                }
                SurespotLog.v(TAG, "loaded: %d messages for spot %s", messages.size(), spot);
            }
        }
        return messages;
    }

    private String getFilename(String user, String filename) {

        if (user != null) {
            String dir = FileUtils.getStateDir(mContext) + File.separator + user;
            if (FileUtils.ensureDir(dir)) {
                return dir + File.separator + filename + STATE_EXTENSION;
            }

        }
        return null;
    }

    public static synchronized void wipeAllState(Context context) {
        FileUtils.deleteRecursive(new File(FileUtils.getStateDir(context)));
        FileUtils.deleteRecursive(new File(FileUtils.getPublicKeyDir(context)));
    }

    public static synchronized void wipeState(Context context, String identityName) {
        FileUtils.deleteRecursive(new File(FileUtils.getStateDir(context) + File.separator + identityName));
    }

    public synchronized static void clearCache(final Context context, final IAsyncCallback<Void> callback) {
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                // clear out some shiznit
                SurespotLog.v(TAG, "clearing local cache");

                // state
                wipeAllState(context);

                for (String name : IdentityController.getIdentityNames(context)) {
                    // last chat and user we had open
                    Utils.putUserSharedPrefsString(context, name, SurespotConstants.PrefNames.LAST_CHAT, null);
                    Utils.putUserSharedPrefsString(context, name, SurespotConstants.PrefNames.GCM_ID_SENT, null);
                    Utils.putUserSharedPrefsString(context, name, SurespotConstants.PrefNames.GCM_ID_RECEIVED, null);
                    Utils.putUserSharedPrefsString(context, name, SurespotConstants.PrefNames.RECENTLY_USED_GIFS, null);
                }
                Utils.putSharedPrefsString(context, SurespotConstants.PrefNames.LAST_USER, null);


                // clear network caches
                NetworkManager.clearCaches();

                //clear chat manager state
                ChatManager.resetState(context);

                FileCacheController fcc = SurespotApplication.getFileCacheController();
                if (fcc != null) {
                    fcc.clearCache();
                }

                // captured image dir
                FileUtils.wipeImageCaptureDir(context);

                // uploaded images dir
                String localImageDir = FileUtils.getFileUploadDir(context);
                FileUtils.deleteRecursive(new File(localImageDir));

                CredentialCachingService ccs = SurespotApplication.getCachingService(context);
                if (ccs != null) {
                    ccs.clear();
                }


                return null;
            }

            protected void onPostExecute(Void result) {
                callback.handleResponse(null);
            }
        }.execute();
    }

    public synchronized static void wipeUserState(Context context, String username, String otherUsername) {
        String publicKeyDir = FileUtils.getPublicKeyDir(context) + File.separator + otherUsername;
        FileUtils.deleteRecursive(new File(publicKeyDir));

        String room = ChatUtils.getSpot(username, otherUsername);
        String messageFile = FileUtils.getStateDir(context) + File.separator + username + File.separator + "messages_" + room + STATE_EXTENSION;
        try {
            File file = new File(messageFile);
            file.delete();
        }
        catch (Exception ex) {
            SurespotLog.w(TAG, ex, "wipeUserState");
        }


    }

    public synchronized void saveSharedSecrets(final String username, final String password, final Map<SharedSecretKey, byte[]> secrets) {
        if (username == null || password == null || secrets == null) {
            return;
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                String filename = getFilename(username, SECRETS);
                Map<String, byte[]> map = new HashMap<String, byte[]>();

                for (SharedSecretKey key : secrets.keySet()) {
                    // save only secrets for this user
                    if (key.getOurUsername().equals(username)) {
                        String skey = key.getOurUsername() + ":" + key.getOurVersion() + ":" + key.getTheirUsername() + ":" + key.getTheirVersion() + ":" + (key.getHashed() ? "1" : "0");

                        byte[] value = secrets.get(key);
                        if (value != null) {
                            map.put(skey, value);
                        }
                    }
                }

                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(map);
                    oos.close();
                    baos.close();

                    byte[] encryptedSecrets = EncryptionController.encryptData(password, baos.toByteArray());

                    FileOutputStream fos = new FileOutputStream(filename);
                    fos.write(encryptedSecrets);
                    fos.close();

                    SurespotLog.d(TAG, "saved shared secrets for: %s", username);
                }
                catch (IOException e) {
                    SurespotLog.e(TAG, e, "error saving shared secrets for %s", username);
                }

            }
        };

        SurespotApplication.THREAD_POOL_EXECUTOR.execute(runnable);

    }

    @SuppressWarnings("unchecked")
    public synchronized Map<SharedSecretKey, byte[]> loadSharedSecrets(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        String filename = getFilename(username, SECRETS);

        File file = new File(filename);
        if (!file.exists()) {
            return null;
        }

        Map<String, byte[]> loadedMap = null;
        try {
            byte[] encryptedSecretData = FileUtils.readFileNoGzip(filename);
            byte[] secretData = EncryptionController.decryptData(password, encryptedSecretData);

            if (secretData == null) {
                return null;
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(secretData);
            ObjectInputStream ois = new ObjectInputStream(bais);
            loadedMap = (Map<String, byte[]>) ois.readObject();
            ois.close();
        }
        catch (IOException e) {
            SurespotLog.e(TAG, e, "error loading shared secrets for %s", username);
            return null;
        }
        catch (ClassNotFoundException e) {
            SurespotLog.e(TAG, e, "error loading shared secrets for %s", username);
            return null;
        }

        Map<SharedSecretKey, byte[]> map = new HashMap<SharedSecretKey, byte[]>();

        for (String key : loadedMap.keySet()) {
            String[] split = key.split(":");

            SharedSecretKey ssk = new SharedSecretKey(new VersionMap(split[0], split[1]), new VersionMap(split[2], split[3]), (Integer.parseInt(split[4]) == 0 ? false : true));
            byte[] value = loadedMap.get(key);
            if (value != null) {
                map.put(ssk, value);
            }
        }

        SurespotLog.d(TAG, "loaded shared secrets for: %s", username);
        return map;
    }

    public synchronized Cookie loadCookie(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        String filename = getFilename(username, COOKIE);

        if (!new File(filename).exists()) {
            return null;
        }

        Cookie cookie = null;
        try {
            byte[] encryptedCookieData = FileUtils.readFileNoGzip(filename);
            byte[] cookieData = EncryptionController.decryptData(password, encryptedCookieData);

            if (cookieData == null) {
                return null;
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(cookieData);
            ObjectInputStream ois = new ObjectInputStream(bais);
            String cookieString = (String) ois.readObject();

            cookie = Cookie.parse(HttpUrl.parse(SurespotConfiguration.getBaseUrl()), cookieString);

            ois.close();
            SurespotLog.d(TAG, "loaded cookie for username: %s", username);
            if (cookie != null) {
                return cookie;
            }
        }
        catch (ClassCastException e) {
            SurespotLog.e(TAG, e, "error loading cookie for %s", username);
        }
        catch (IOException e) {
            SurespotLog.e(TAG, e, "error loading cookie for %s", username);
        }
        catch (ClassNotFoundException e) {
            SurespotLog.e(TAG, e, "error loading cookie for %s", username);
        }
        return null;
    }

    public synchronized void saveCookie(final String username, final String password, final Cookie cookie) {
        if (username == null || password == null || cookie == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                String filename = getFilename(username, COOKIE);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(cookie.toString());
                    oos.close();
                    baos.close();

                    byte[] encryptedCookie = EncryptionController.encryptData(password, baos.toByteArray());

                    FileOutputStream fos = new FileOutputStream(filename);
                    fos.write(encryptedCookie);
                    fos.close();
                    SurespotLog.d(TAG, "saved cookie for username: %s, cookie: %s", username, cookie);
                }
                catch (IOException e) {
                    SurespotLog.e(TAG, e, "error saving cookie for %s", username);
                }
                return null;
            }
        }.execute();
    }
}
