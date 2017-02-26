package com.twofours.surespot.gifs;

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
}
