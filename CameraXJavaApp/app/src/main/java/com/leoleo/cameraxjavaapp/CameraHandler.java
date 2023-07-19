package com.leoleo.cameraxjavaapp;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class CameraHandler implements DefaultLifecycleObserver {
    @NonNull
    private final Context context;
    private ImageCapture imageCapture;
    @NonNull
    private final ExecutorService cameraExecutor;
    private static final String TAG = CameraHandler.class.getSimpleName();
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    public CameraHandler(final @NonNull Context context) {
        cameraExecutor = Executors.newSingleThreadExecutor();
        this.context = context;
    }

    // 画面表示時に呼ばれる
    @Override
    public void onResume(@NonNull LifecycleOwner owner) {

    }

    // 画面非表示時に呼ばれる
    @Override
    public void onPause(@NonNull LifecycleOwner owner) {

    }

    // 画面終了時に呼ばれる
    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        cameraExecutor.shutdown();
    }

    public void takePhoto(
            final @NonNull Consumer<ImageCapture.OutputFileResults> onImageSaved,
            final @NonNull Consumer<ImageCaptureException> onError) {
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
                        context.getContentResolver(),
                        imageCollection,
                        contentValues
                ).build();

        takePhoto(outputOptions, onImageSaved, onError, ContextCompat.getMainExecutor(context));
    }

    /**
     * 写真撮影を行う.
     *
     * @param outputOptions ファイル出力先の情報
     * @param executor      OnImageSavedCallbackの実行スレッド
     * @param onImageSaved  撮影成功ようコールバック
     * @param onError       撮影失敗ようコールバック
     */
    public void takePhoto(
            final @NonNull ImageCapture.OutputFileOptions outputOptions,
            final @NonNull Consumer<ImageCapture.OutputFileResults> onImageSaved,
            final @NonNull Consumer<ImageCaptureException> onError,
            final @NonNull Executor executor) {
        // Get a stable reference of the modifiable image capture use case
        if (imageCapture == null) return;

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(outputOptions, executor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                final String msg = "Photo capture succeeded: " + output.getSavedUri();
                Log.d(TAG, msg);
                onImageSaved.accept(output);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exception);
                onError.accept(exception);
            }
        });
    }

    /**
     * imageCaptureを使う場合こちらのstartCameraメソッドを使う.
     * すべてのデバイスでサポート: エミュレータでも動作確認可能 (OS11以上 推奨)
     *
     * @param viewFinder     カメラプレビュー
     * @param lifecycleOwner ユースケースのライフサイクル遷移を制御する lifecycleOwner
     * @param onError        カメラプレビュー起動時のエラーメッセージ
     * @param executor       リスナーの実行スレッド
     */
    public void startCamera(
            final @NonNull PreviewView viewFinder,
            final @NonNull LifecycleOwner lifecycleOwner,
            final @NonNull Consumer<String> onError,
            final @NonNull Executor executor) {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            final ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = cameraProviderFuture.get();

                // Preview
                final Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                // Select back camera as a default
                final CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException | IllegalArgumentException |
                     IllegalStateException e) {
                onError.accept(e.getLocalizedMessage());
            }
        }, executor);
    }
}