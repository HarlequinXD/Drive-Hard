package com.harlequinxd.drivehard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import com.harlequinxd.drivehard.gl.GameRenderer;
import com.harlequinxd.drivehard.input.TouchControls;

public final class GameView extends GLSurfaceView {
    private final TouchControls controls;

    public GameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        controls = new TouchControls(context.getResources().getDisplayMetrics().density);
        setRenderer(new GameRenderer(controls));
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        controls.onTouch(event);
        return true;
    }
}
