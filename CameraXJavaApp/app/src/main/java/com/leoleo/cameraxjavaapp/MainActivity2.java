package com.leoleo.cameraxjavaapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "CameraXApp";
    // OS version 10以降の権限リスト
    private static final String[] REQUIRED_PERMISSIONS_API29 = new String[]{android.Manifest.permission.CAMERA};
    // OS version 9までの権限リスト
    private static final String[] REQUIRED_PERMISSIONS = new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private CameraHandler cameraHandler;
    private PreviewView viewFinder;

    private final ActivityResultLauncher<String[]> requestPermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> grantStates) -> {
        final boolean isPermissionAllGranted = grantStates.entrySet().stream().allMatch(Map.Entry::getValue);
        if (!isPermissionAllGranted) {
            showSnackBar("Runtime Permissionを全て許可しないとアプリが正常に動作しません");
        } else {
            cameraHandler.startCamera(viewFinder, this, s -> Log.e(TAG, s), ContextCompat.getMainExecutor(this));
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);

        cameraHandler = new CameraHandler(this);
        getLifecycle().addObserver(cameraHandler);

        // Request camera permissions
        if (allPermissionsGranted(this)) {
            cameraHandler.startCamera(viewFinder, this, s -> Log.e(TAG, s), ContextCompat.getMainExecutor(this));
        } else {
            requestPermissions.launch(getRequiredPermissions());
        }

        // Set up the listeners for take photo and video capture buttons
        findViewById(R.id.image_capture_button).setOnClickListener(v -> {
            cameraHandler.takePhoto(new Consumer<ImageCapture.OutputFileResults>() {
                @Override
                public void accept(ImageCapture.OutputFileResults output) {
                    final String msg = "Photo capture succeeded: " + output.getSavedUri();
                    showSnackBar(msg);
                }
            }, new Consumer<ImageCaptureException>() {
                @Override
                public void accept(ImageCaptureException e) {
                    Log.e(TAG, "error", e);
                }
            });
        });
    }

    /**
     * Runtime Permissionの許可チェック
     *
     * @param context コンテキスト
     * @return 全ての権限付与が行われていれば true
     */
    public boolean allPermissionsGranted(final @NonNull Context context) {
        final String[] permissions = getRequiredPermissions();
        return Arrays.stream(permissions).allMatch(permission -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED);
    }

    private String[] getRequiredPermissions() {
        final String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // 実行デバイスが、OS 10以上の場合
            permissions = REQUIRED_PERMISSIONS_API29;
        } else {
            permissions = REQUIRED_PERMISSIONS;
        }
        return permissions;
    }

    private void showSnackBar(final @NonNull String message) {
        Snackbar.make(getWindow().getDecorView(), message, Snackbar.LENGTH_LONG).show();
        Log.d(TAG, message);
    }
}