package com.leoleo.cameraxjavaapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activityで直接 CameraX APIを呼び出す作り
 */
public class MainActivity extends AppCompatActivity {
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private static final String TAG = "CameraXApp";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    // OS version 10以降の権限リスト
    private static final String[] REQUIRED_PERMISSIONS_API29 = new String[]{android.Manifest.permission.CAMERA};
    // OS version 9までの権限リスト
    private static final String[] REQUIRED_PERMISSIONS = new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private final ActivityResultLauncher<String[]> requestPermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> grantStates) -> {
        final boolean isPermissionAllGranted = grantStates.entrySet().stream().allMatch(Map.Entry::getValue);
        if (!isPermissionAllGranted) {
            showSnackBar("Runtime Permissionを全て許可しないとアプリが正常に動作しません");
        } else {
            startCamera();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request camera permissions
        if (allPermissionsGranted(this)) {
            startCamera();
        } else {
            requestPermissions.launch(getRequiredPermissions());
        }

        // Set up the listeners for take photo and video capture buttons
        findViewById(R.id.image_capture_button).setOnClickListener(v -> takePhoto());
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    private void takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        if (imageCapture == null) return;

        // Create time stamped name and MediaStore entry.
        final String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        final ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");
        }
        final Uri imageCollection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // 共有ストレージのPicturesディレクトリのパスを取得する
            imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        // Create output options object which contains file + metadata
        final ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        imageCollection,
                        contentValues
                ).build();

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                final String msg = "Photo capture succeeded: " + output.getSavedUri();
                showSnackBar(msg);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exception);
            }
        });
    }

    /**
     * imageCaptureを使う場合こちらのstartCameraメソッドを使う.
     * すべてのデバイスでサポート: エミュレータでも動作確認可能 (OS11以上 推奨)
     */
    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            final ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = cameraProviderFuture.get();

                // Preview
                final PreviewView viewFinder = findViewById(R.id.viewFinder);
                final Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                // Select back camera as a default
                final CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException | IllegalArgumentException |
                     IllegalStateException e) {
                Log.e(TAG, "error: ", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Runtime Permissionの許可チェック
     *
     * @param context コンテキスト
     * @return 全ての権限付与が行われていれば true
     */
    private boolean allPermissionsGranted(Context context) {
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

    private void showSnackBar(String message) {
        Snackbar.make(getWindow().getDecorView(), message, Snackbar.LENGTH_LONG).show();
        Log.d(TAG, message);
    }
}