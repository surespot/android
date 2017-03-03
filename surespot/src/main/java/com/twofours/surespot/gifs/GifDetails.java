package com.twofours.surespot.gifs;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by adam on 2/26/17.
 */

public class GifDetails {
    private int height;
    private int width;
    private String url;

    public GifDetails(String url, int width, int height) {
        setHeight(height);
        setWidth(width);
        setUrl(url);
    }

    public GifDetails(JSONObject o) throws JSONException {
        setUrl(o.getString("url"));
        setHeight(o.getInt("height"));
        setWidth(o.getInt("width"));
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = new JSONObject();

        o.put("url", url);
        o.put("width", width);
        o.put("height", height);

        return o;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GifDetails that = (GifDetails) o;

        return url != null ? url.equals(that.url) : that.url == null;
    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}
