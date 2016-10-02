package com.crdroid.settings.preferences;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import com.android.settings.R;

public class AlphaPatternDrawable extends Drawable {

    private int mRectangleSize = 10;

    private Paint mPaint = new Paint();
    private Paint mPaintWhite = new Paint();
    private Paint mPaintGray = new Paint();

    private int numRectanglesHorizontal;
    private int numRectanglesVertical;

    // where the pattern gets dumped/cached
    private Bitmap mBitmap;

    public AlphaPatternDrawable(int rectangleSize, Context context) {
        mRectangleSize = rectangleSize;
        mPaintWhite.setColor(context.getResources().getColor(R.color.alphawhite));
        mPaintGray.setColor(context.getResources().getColor(R.color.alphagray));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(mBitmap, null, getBounds(), mPaint);
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public void setAlpha(int alpha) {
        throw new UnsupportedOperationException("Alpha is not supported by this drawable.");
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        throw new UnsupportedOperationException("ColorFilter is not supported by this drawable.");
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        int height = bounds.height();
        int width = bounds.width();

        numRectanglesHorizontal = (int) Math.ceil((width / mRectangleSize));
        numRectanglesVertical = (int) Math.ceil(height / mRectangleSize);

        generatePatternBitmap();
    }

    // Generates bitmap cache with pattern as big as allowed to avoid recreating on draw()
    private void generatePatternBitmap(){
        if(getBounds().width() <= 0 || getBounds().height() <= 0){
            return;
        }

        mBitmap = Bitmap.createBitmap(getBounds().width(), getBounds().height(), Config.ARGB_8888);
        Canvas canvas = new Canvas(mBitmap);

        Rect r = new Rect();
        boolean verticalStartWhite = true;
        for (int i = 0; i <= numRectanglesVertical; i++) {

            boolean isWhite = verticalStartWhite;
            for (int j = 0; j <= numRectanglesHorizontal; j++) {

                r.top = i * mRectangleSize;
                r.left = j * mRectangleSize;
                r.bottom = r.top + mRectangleSize;
                r.right = r.left + mRectangleSize;

                if (isWhite) {
                    canvas.drawRect(r, mPaintWhite);
                } else {
                    canvas.drawRect(r, mPaintGray);
                }

                isWhite = !isWhite;
            }

            verticalStartWhite = !verticalStartWhite;
        }
    }
}
