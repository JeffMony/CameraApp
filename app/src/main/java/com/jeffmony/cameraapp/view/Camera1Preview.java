package com.jeffmony.cameraapp.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class Camera1Preview extends SurfaceView {

    private static final String TAG = "Camera1Preview";

    public Camera1Preview(Context context) {
        this(context, null);
    }

    public Camera1Preview(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public Camera1Preview(Context context, AttributeSet attributeSet, int style) {
        super(context, attributeSet, style);
    }


    //预览的界面设置为4:3比例

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int)(width * 1.0f / 3 * 4);
        setMeasuredDimension(width, height);
    }
}
