package com.twofours.surespot.network;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * Created by adam on 3/30/16.
 */
public class SurespotCookieJar implements CookieJar {

    private List<Cookie> cookies;

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        this.cookies = cookies;
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        return getCookies();
    }

    public List<Cookie> getCookies() {
        if (cookies == null) {
            cookies = new ArrayList<Cookie>();
        }
        return cookies;
    }

    public void setCookie(Cookie cookie) {
        getCookies().add(cookie);
    }

    public void clear() {
        if (cookies != null) {
            cookies.clear();
        }
    }
}
