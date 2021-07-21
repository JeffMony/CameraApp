package com.jeffmony.cameraapp.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class Camera2Preview extends TextureView {

    private static final String TAG = "Camera2Preview";

    public Camera2Preview(Context context) {
        this(context, null);
    }

    public Camera2Preview(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public Camera2Preview(Context context, AttributeSet attributeSet, int style) {
        super(context, attributeSet, style);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int)(width * 1.0f / 3 * 4);
        setMeasuredDimension(width, height);
    }
}
