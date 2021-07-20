package com.jeffmony.cameraapp.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class ImageUtils {

    //将Bitmap旋转一定的角度
    public static Bitmap getRotatedBitmap(Bitmap bitmap, int ratation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(ratation);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }
}
