/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.android.spear.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.io.File;

import me.xiaopan.android.spear.Spear;
import me.xiaopan.android.spear.request.DisplayListener;
import me.xiaopan.android.spear.request.DisplayOptions;
import me.xiaopan.android.spear.request.ProgressListener;
import me.xiaopan.android.spear.request.RequestFuture;
import me.xiaopan.android.spear.util.FailureCause;
import me.xiaopan.android.spear.util.RecyclingBitmapDrawable;
import me.xiaopan.android.spear.util.Scheme;

/**
 * SpearImageView
 */
public class SpearImageView extends ImageView{
    private static final int NONE = -1;
    private static final int DEFAULT_DEBUG_COLOR_MEMORY = 0x8800FF00;
    private static final int DEFAULT_DEBUG_COLOR_DISK = 0x88FFFF00;
    private static final int DEFAULT_DEBUG_COLOR_NETWORK = 0x88FF0000;
    private static final int DEFAULT_PROGRESS_COLOR = 0x22000000;
    private static final int DEFAULT_PRESSED_COLOR = 0x33000000;

    private RequestFuture requestFuture;
    private DisplayOptions displayOptions;
    private DisplayListener displayListener;
    private ProgressListener progressListener;

    private Paint paint;
    private Path path;
    private int debugColor = NONE;
    private float progress = NONE;
    private int progressColor = DEFAULT_PROGRESS_COLOR;
    private int pressedColor = DEFAULT_PRESSED_COLOR;
    private DebugDisplayListener debugDisplayListener;
    private UpdateProgressListener updateProgressListener;
    private ProgressDisplayListener progressDisplayListener;
    private boolean debugMode;
    private boolean pressed;
    private boolean enableShowPressed;
    private boolean enableShowProgress;

    public SpearImageView(Context context) {
        super(context);
    }

    public SpearImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // 重新计算三角形的位置
        if(path != null){
            path.reset();
            int x = getWidth()/10;
            int y = getWidth()/10;
            path.moveTo(getPaddingLeft(), getPaddingTop());
            path.lineTo(getPaddingLeft()+x, getPaddingTop());
            path.lineTo(getPaddingLeft(), getPaddingTop()+y);
            path.close();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制按下状态
        if(enableShowPressed && pressed){
            canvas.drawColor(pressedColor);
        }

        // 绘制进度
        if(enableShowProgress && progress != NONE){
            if(paint == null){
                paint = new Paint();
            }
            paint.setColor(progressColor);
            canvas.drawRect(getPaddingLeft(), getPaddingTop() + (progress * getHeight()), getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom(), paint);
        }

        // 绘制三角形
        if(debugColor != NONE){
            if(paint == null){
                paint = new Paint();
            }
            paint.setColor(debugColor);
            if(path == null){
                path = new Path();
                int x = getWidth()/10;
                int y = getWidth()/10;
                path.moveTo(getPaddingLeft(), getPaddingTop());
                path.lineTo(getPaddingLeft()+x, getPaddingTop());
                path.lineTo(getPaddingLeft(), getPaddingTop()+y);
                path.close();
            }
            canvas.drawPath(path, paint);
        }
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        if(enableShowPressed && this.pressed != pressed){
            this.pressed = pressed;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1){
            final Drawable previousDrawable = getDrawable();
            if(previousDrawable != null){
                notifyDrawable(previousDrawable, false);
            }
        }
        super.onDetachedFromWindow();
    }

    /**
     * @see android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)
     */
    @Override
    public void setImageDrawable(Drawable drawable) {
        // Keep hold of previous Drawable
        final Drawable previousDrawable = getDrawable();

        // Call super to set new Drawable
        super.setImageDrawable(drawable);

        // Notify new Drawable that it is being displayed
        if(drawable != null){
            notifyDrawable(drawable, true);
        }

        // Notify old Drawable so it is no longer being displayed
        if(previousDrawable != null){
            notifyDrawable(previousDrawable, false);
        }
    }

    /**
     * 根据URI设置图片
     * @param uri 支持以下6种Uri
     * <blockquote>String imageUri = "http://site.com/image.png"; // from Web
     * <br>String imageUri = "https://site.com/image.png"; // from Web
     * <br>String imageUri = "file:///mnt/sdcard/image.png"; // from SD card
     * <br>String imageUri = "content://media/external/audio/albumart/13"; // from content provider
     * <br>String imageUri = "assets://image.png"; // from assets
     * <br>String imageUri = "drawable://" + R.drawable.image; // from drawables (only images, non-9patch)
     * </blockquote>
     * @return RequestFuture 你可以通过RequestFuture查看请求是否完成或主动取消请求
     */
    public RequestFuture setImageByUri(String uri){
        // 重置角标和进度
        if(debugColor != NONE || progress != NONE){
            debugColor = NONE;
            progress = NONE;
            invalidate();
        }

        return requestFuture = Spear.with(getContext()).display(uri, this).options(displayOptions).listener(getDisplayListener()).progressListener(getProgressListener()).fire();
    }

    private DisplayListener getDisplayListener(){
        if(debugMode){
            if(debugDisplayListener == null){
                debugDisplayListener = new DebugDisplayListener();
            }
            return debugDisplayListener;
        }else if(enableShowProgress){
            if(progressDisplayListener == null){
                progressDisplayListener = new ProgressDisplayListener();
            }
            return progressDisplayListener;
        }else{
            return displayListener;
        }
    }

    private ProgressListener getProgressListener(){
        if(enableShowProgress){
            if(updateProgressListener == null){
                updateProgressListener = new UpdateProgressListener();
            }
            return updateProgressListener;
        }else{
            return progressListener;
        }
    }

    /**
     * 是否显示按下状态
     * @return 是否显示按下状态
     */
    public boolean isEnableShowPressed() {
        return enableShowPressed;
    }

    /**
     * 设置是否显示按下状态，开启后按下的时候会在ImageView表面显示一个黑色半透明层
     * @param enableShowPressed 是否显示按下状态
     */
    public void setEnableShowPressed(boolean enableShowPressed) {
        this.enableShowPressed = enableShowPressed;
    }

    /**
     * 是否显示进度
     * @return 是否显示进度
     */
    public boolean isEnableShowProgress() {
        return enableShowProgress;
    }

    /**
     * 设置是否显示进度
     * @param enableShowProgress 是否显示进度
     */
    public void setEnableShowProgress(boolean enableShowProgress) {
        this.enableShowProgress = enableShowProgress;
    }

    /**
     * 设置按下时的颜色
     * @param pressedColor 按下时的颜色
     */
    public void setPressedColor(int pressedColor) {
        this.pressedColor = pressedColor;
    }

    /**
     * 设置进度的颜色
     * @param progressColor 进度的颜色
     */
    public void setProgressColor(int progressColor) {
        this.progressColor = progressColor;
    }

    /**
     * 根据文件设置图片
     * @param imageFile SD卡上的图片文件
     * @return RequestFuture 你可以通过RequestFuture查看请求是否完成或主动取消请求
     */
    public RequestFuture setImageByFile(File imageFile){
        return setImageByUri(Scheme.FILE.createUri(imageFile.getPath()));
    }

    /**
     * 根据Drawable ID设置图片
     * @param drawableResId Drawable ID
     * @return RequestFuture 你可以通过RequestFuture查看请求是否完成或主动取消请求
     */
    public RequestFuture setImageByResource(int drawableResId){
        return setImageByUri(Scheme.DRAWABLE.createUri(String.valueOf(drawableResId)));
    }

    /**
     * 根据assets文件名称设置图片
     * @param imageFileName ASSETS文件加下的图片文件的名称
     * @return RequestFuture 你可以通过RequestFuture查看请求是否完成或主动取消请求
     */
    public RequestFuture setImageByAssets(String imageFileName){
        return setImageByUri(Scheme.ASSETS.createUri(imageFileName));
    }

    /**
     * 根据Content Uri设置图片
     * @param uri Content Uri 这个URI是其它Content Provider返回的
     * @return RequestFuture 你可以通过RequestFuture查看请求是否完成或主动取消请求
     */
    public RequestFuture setImageByContent(Uri uri){
        return setImageByUri(uri.toString());
    }

    /**
     * 设置显示参数
     * @param displayOptions 显示参数
     */
    public void setDisplayOptions(DisplayOptions displayOptions) {
        this.displayOptions = displayOptions;
    }

    /**
     * 设置显示参数的名称
     * @param optionsName 显示参数的名称
     */
    public void setDisplayOptions(Enum<?> optionsName) {
        this.displayOptions = (DisplayOptions) Spear.getOptions(optionsName);
    }

    /**
     * 设置显示监听器
     * @param displayListener 显示监听器
     */
    public void setDisplayListener(DisplayListener displayListener) {
        this.displayListener = displayListener;
    }

    /**
     * 设置显示进度监听器
     * @param progressListener 进度监听器
     */
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * 获取RequestFuture，你需要对此方法返回的对象进行非null验证，如果你没有调用过setImageBy***系列方法设置图片，那么此方法将一直返回null
     * @return RequestFuture，你可以通过RequestFuture查看请求是否完成或主动取消请求
     */
    public RequestFuture getRequestFuture() {
        return requestFuture;
    }

    /**
     * 设置是否开启调试模式，开启后会在View的左上角显示一个纯色三角形，红色代表本次是从网络加载的，黄色代表本次是从本地加载的，绿色代表本次是从内存加载的
     * @param debugMode 是否开启调试模式
     */
    public void setDebugMode(boolean debugMode) {
        boolean oldDebugMode = this.debugMode;
        this.debugMode = debugMode;
        if(oldDebugMode){
            debugColor = NONE;
            invalidate();
        }
    }

    /**
     * Notifies the drawable that it's displayed state has changed.
     * @param drawable Drawable
     * @param isDisplayed 是否已显示
     */
    private static void notifyDrawable(Drawable drawable, final boolean isDisplayed) {
        if (drawable instanceof RecyclingBitmapDrawable) {
            // The drawable is a CountingBitmapDrawable, so notify it
            ((RecyclingBitmapDrawable) drawable).setIsDisplayed(isDisplayed);
        } else if (drawable instanceof LayerDrawable) {
            // The drawable is a LayerDrawable, so recurse on each layer
            LayerDrawable layerDrawable = (LayerDrawable) drawable;
            for (int i = 0, z = layerDrawable.getNumberOfLayers(); i < z; i++) {
                notifyDrawable(layerDrawable.getDrawable(i), isDisplayed);
            }
        }
    }

    private class DebugDisplayListener implements DisplayListener{
        @Override
        public void onStarted() {
            debugColor = NONE;
            progress = enableShowProgress?0:NONE;
            invalidate();
            if(displayListener != null){
                displayListener.onStarted();
            }
        }

        @Override
        public void onCompleted(String uri, ImageView imageView, BitmapDrawable drawable, From from) {
            if(from != null){
                switch (from){
                    case MEMORY: debugColor = DEFAULT_DEBUG_COLOR_MEMORY; break;
                    case DISK: debugColor = DEFAULT_DEBUG_COLOR_DISK; break;
                    case NETWORK: debugColor = DEFAULT_DEBUG_COLOR_NETWORK; break;
                }
            }else{
                debugColor = NONE;
            }
            progress = NONE;
            invalidate();
            if(displayListener != null){
                displayListener.onCompleted(uri, imageView, drawable, from);
            }
        }

        @Override
        public void onFailed(FailureCause failureCause) {
            debugColor = NONE;
            progress = NONE;
            invalidate();
            if(displayListener != null){
                displayListener.onFailed(failureCause);
            }
        }

        @Override
        public void onCanceled() {
            if(displayListener != null){
                displayListener.onCanceled();
            }
        }
    }

    private class UpdateProgressListener implements ProgressListener{
        @Override
        public void onUpdateProgress(int totalLength, int completedLength) {
            progress = (float) completedLength/totalLength;
            invalidate();
            if(progressListener != null){
                progressListener.onUpdateProgress(totalLength, completedLength);
            }
        }
    }

    private class ProgressDisplayListener implements DisplayListener{

        @Override
        public void onStarted() {
            progress = 0;
            invalidate();
            if(displayListener != null){
                displayListener.onStarted();
            }
        }

        @Override
        public void onCompleted(String uri, ImageView imageView, BitmapDrawable drawable, From from) {
            progress = NONE;
            invalidate();
            if(displayListener != null){
                displayListener.onCompleted(uri, imageView, drawable, from);
            }
        }

        @Override
        public void onFailed(FailureCause failureCause) {
            progress = NONE;
            invalidate();
            if(displayListener != null){
                displayListener.onFailed(failureCause);
            }
        }

        @Override
        public void onCanceled() {
            if(displayListener != null){
                displayListener.onCanceled();
            }
        }
    }
}
