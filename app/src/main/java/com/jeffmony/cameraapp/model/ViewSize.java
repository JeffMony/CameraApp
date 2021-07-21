package com.jeffmony.cameraapp.model;

public class ViewSize {
    private int mWidth;
    private int mHeight;

    public ViewSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public String toString() {
        return "ViewSize[width="+getWidth()+", height="+getHeight()+"]";
    }
}
