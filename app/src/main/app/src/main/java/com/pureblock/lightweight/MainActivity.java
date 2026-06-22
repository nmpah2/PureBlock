package com.pureblock.lightweight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 101;
    private static final int REQ_CAPTURE = 102;
    private MediaProjectionManager mpManager;

    @Override
    protected void Bundle) {
        super.onCreate(savedInstanceState);
        mpManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        checkOverlayPermission();
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
        } else {
            startScreenCaptureIntent();
        }
    }

    private void startScreenCaptureIntent() {
        startActivityForResult(mpManager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_OVERLAY) {
            checkOverlayPermission();
        } else if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK) {
            ScreenScannerService.mediaProjection = mpManager.getMediaProjection(resultCode, data);
            Intent serviceIntent = new Intent(this, ScreenScannerService.class);
            startService(serviceIntent);
            finish(); // Exit setup screen immediately to save system RAM
        }
    }
            }
