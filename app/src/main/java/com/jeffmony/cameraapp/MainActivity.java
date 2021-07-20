package com.jeffmony.cameraapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button mCameraInfoBtn;
    private Button mCamera1Btn;
    private Button mCamera2Btn;
    private Button mCameraXBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCameraInfoBtn = findViewById(R.id.camera_info_btn);
        mCamera1Btn = findViewById(R.id.camera1_btn);
        mCamera2Btn = findViewById(R.id.camera2_btn);
        mCameraXBtn = findViewById(R.id.camerax_btn);

        mCameraInfoBtn.setOnClickListener(this);
        mCamera1Btn.setOnClickListener(this);
        mCamera2Btn.setOnClickListener(this);
        mCameraXBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mCameraInfoBtn) {
            Intent intent = new Intent(MainActivity.this, CameraInfoActivity.class);
            startActivity(intent);
        } else if (v == mCamera1Btn) {
            Intent intent = new Intent(MainActivity.this, Camera1Activity.class);
            startActivity(intent);
        } else if (v == mCamera2Btn) {
            Intent intent = new Intent(MainActivity.this, Camera2Activity.class);
            startActivity(intent);
        } else if (v == mCameraXBtn) {
            Intent intent = new Intent(MainActivity.this, CameraXActivity.class);
            startActivity(intent);
        }
    }
}