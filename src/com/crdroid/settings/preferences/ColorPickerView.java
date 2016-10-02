package com.crdroid.settings.preferences;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.android.settings.R;

// Displays color picker and alpha slider
public class ColorPickerView extends View {

    private final static int PANEL_SAT_VAL = 0;
    private final static int PANEL_HUE = 1;
    private final static int PANEL_ALPHA = 2;

    // Width of border surrounding color panels in pixels
    private final static float BORDER_WIDTH_PX = 1;

    // Width in dp of the hue panel
    private float HUE_PANEL_WIDTH;
    // Height in dp of the alpha panel
    private float ALPHA_PANEL_HEIGHT;
    // Height in dp of the alpha panel
    private float PANEL_SPACING = 10f;
    // Distance in dp between color panels
    private float PALETTE_CIRCLE_TRACKER_RADIUS;
    // Radius in dp of the color palette tracker circle
    private float RECTANGLE_TRACKER_OFFSET = 2f;

    private float mDensity = 1f;

    private OnColorChangedListener mListener;

    private Paint mSatValPaint;
    private Paint mSatValTrackerPaint;

    private Paint mHuePaint;
    private Paint mHueTrackerPaint;

    private Paint mAlphaPaint;
    private Paint mAlphaTextPaint;

    private Paint mBorderPaint;

    private Shader mValShader;
    private Shader mSatShader;
    private Shader mHueShader;
    private Shader mAlphaShader;

    private int mAlpha = 0xff;
    private float mHue = 360f;
    private float mSat = 0f;
    private float mVal = 0f;

    private String mAlphaSliderText = "";
    private int mSliderTrackerColor = 0xff1c1c1c;
    private int mBorderColor = 0xff6E6E6E;
    private boolean mShowAlphaPanel = false;

    // Records which panel has "focus" when processing hardware button data
    private int mLastTouchedPanel = PANEL_SAT_VAL;

    // Edge offset so finger tracker doesn't get clipped when drawn outside of the view
    private float mDrawingOffset;

    // Allowed drawable distance from edges of the view
    private RectF mDrawingRect;

    private RectF mSatValRect;
    private RectF mHueRect;
    private RectF mAlphaRect;

    private AlphaPatternDrawable mAlphaPattern;

    private Point mStartTouchPoint = null;
    // A record of what first feeds in to here... maybe it can visually make inaccuracies a nonissue
    private int OrigColor;

    public interface OnColorChangedListener {
        public void onColorChanged(int color);
    }

    public ColorPickerView(Context context){
        this(context, null);
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init(){
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mDensity = getContext().getResources().getDisplayMetrics().density;

        PALETTE_CIRCLE_TRACKER_RADIUS = getContext().getResources().getDimension(R.dimen.colorpicker_tracker_radius);
        ALPHA_PANEL_HEIGHT = getContext().getResources().getDimension(R.dimen.colorpicker_alpha_height);
        HUE_PANEL_WIDTH = getContext().getResources().getDimension(R.dimen.colorpicker_hue_width);

        // a *= b is the same as a = a * b
        RECTANGLE_TRACKER_OFFSET *= mDensity;
        PANEL_SPACING = PANEL_SPACING * mDensity;

        mDrawingOffset = calculateRequiredOffset();

        initPaintTools();

        //Needed for receiving trackball motion events.
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private void initPaintTools(){
        mSatValPaint = new Paint();
        mSatValTrackerPaint = new Paint();
        mHuePaint = new Paint();
        mHueTrackerPaint = new Paint();
        mAlphaPaint = new Paint();
        mAlphaTextPaint = new Paint();
        mBorderPaint = new Paint();


        mSatValTrackerPaint.setStyle(Style.STROKE);
        // I wonder if 2f is arbitrary... same as the 14f...
        mSatValTrackerPaint.setStrokeWidth(2f * mDensity);
        mSatValTrackerPaint.setAntiAlias(true);

        mHueTrackerPaint.setColor(mSliderTrackerColor);
        mHueTrackerPaint.setStyle(Style.STROKE);
        mHueTrackerPaint.setStrokeWidth(2f * mDensity);
        mHueTrackerPaint.setAntiAlias(true);

        // should this 0xff1c1c1c be a int or color resource?
        mAlphaTextPaint.setColor(0xff1c1c1c);
        mAlphaTextPaint.setTextSize(14f * mDensity);
        mAlphaTextPaint.setAntiAlias(true);
        mAlphaTextPaint.setTextAlign(Align.CENTER);
        mAlphaTextPaint.setFakeBoldText(true);
    }

    private float calculateRequiredOffset(){
        float offset = Math.max(5.0f * mDensity, RECTANGLE_TRACKER_OFFSET);
        offset = Math.max(offset, BORDER_WIDTH_PX * mDensity);
        // is 1.5f arbitrary?
        return offset * 1.5f;
    }

    private int[] buildHueColorArray(){
        // I wonder if the fact that it is 361 items in the array holds special significance... perhaps degrees?
        int[] hue = new int[361];

        int count = 0;
        for(int i = hue.length -1; i >= 0; i--, count++){
            hue[count] = Color.HSVToColor(new float[]{i, 1f, 1f});
        }

        return hue;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if(mDrawingRect.width() <= 0 || mDrawingRect.height() <= 0) return;

        drawSatValPanel(canvas);
        drawHuePanel(canvas);
        drawAlphaPanel(canvas);
    }

    private void drawSatValPanel(Canvas canvas){
        final RectF    rect = mSatValRect;

        if(BORDER_WIDTH_PX > 0){
            mBorderPaint.setColor(mBorderColor);
            canvas.drawRect(mDrawingRect.left,
                mDrawingRect.top, rect.right + BORDER_WIDTH_PX,
                rect.bottom + BORDER_WIDTH_PX, mBorderPaint);
        }

        if (mValShader == null) {
            mValShader = new LinearGradient(rect.left, rect.top, rect.left, rect.bottom, Color.WHITE, Color.BLACK, TileMode.CLAMP);
        }

        int rgb = Color.HSVToColor(new float[]{mHue,1f,1f});

        mSatShader = new LinearGradient(rect.left, rect.top, rect.right, rect.top,
                Color.WHITE, rgb, TileMode.CLAMP);
        ComposeShader mShader = new ComposeShader(
            mValShader, mSatShader, PorterDuff.Mode.MULTIPLY);
        mSatValPaint.setShader(mShader);

        canvas.drawRect(rect, mSatValPaint);

        Point p = satValToPoint(mSat, mVal);

        mSatValTrackerPaint.setColor(Color.BLACK);
        canvas.drawCircle(
            p.x, p.y, PALETTE_CIRCLE_TRACKER_RADIUS - 1f * mDensity, mSatValTrackerPaint);

        // What is the sig of this ffdddddd call... should it be a resource?
        mSatValTrackerPaint.setColor(0xffdddddd);
        canvas.drawCircle(p.x, p.y, PALETTE_CIRCLE_TRACKER_RADIUS, mSatValTrackerPaint);
    }

    private void drawHuePanel(Canvas canvas){
        final RectF rect = mHueRect;

        if(BORDER_WIDTH_PX > 0){
            mBorderPaint.setColor(mBorderColor);
            canvas.drawRect(rect.left - BORDER_WIDTH_PX,
                    rect.top - BORDER_WIDTH_PX,
                    rect.right + BORDER_WIDTH_PX,
                    rect.bottom + BORDER_WIDTH_PX,
                    mBorderPaint);
        }

        if (mHueShader == null) {
            mHueShader = new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                buildHueColorArray(), null, TileMode.CLAMP);
            mHuePaint.setShader(mHueShader);
        }

        canvas.drawRect(rect, mHuePaint);

        float rectHeight = 4 * mDensity / 2;

        Point p = hueToPoint(mHue);

        RectF r = new RectF();
        r.left = rect.left - RECTANGLE_TRACKER_OFFSET;
        r.right = rect.right + RECTANGLE_TRACKER_OFFSET;
        r.top = p.y - rectHeight;
        r.bottom = p.y + rectHeight;

        canvas.drawRoundRect(r, 2, 2, mHueTrackerPaint);
    }

    private void drawAlphaPanel(Canvas canvas){
        if(!mShowAlphaPanel || mAlphaRect == null || mAlphaPattern == null) return;

        final RectF rect = mAlphaRect;

        if(BORDER_WIDTH_PX > 0){
            mBorderPaint.setColor(mBorderColor);
            canvas.drawRect(rect.left - BORDER_WIDTH_PX,
                    rect.top - BORDER_WIDTH_PX,
                    rect.right + BORDER_WIDTH_PX,
                    rect.bottom + BORDER_WIDTH_PX,
                    mBorderPaint);
        }

        mAlphaPattern.draw(canvas);

        float[] hsv = new float[]{mHue,mSat,mVal};
        int color = Color.HSVToColor(hsv);
        int acolor = Color.HSVToColor(0, hsv);

        mAlphaShader = new LinearGradient(rect.left, rect.top, rect.right, rect.top,
                color, acolor, TileMode.CLAMP);


        mAlphaPaint.setShader(mAlphaShader);

        canvas.drawRect(rect, mAlphaPaint);

        if(!TextUtils.isEmpty(mAlphaSliderText)){
            canvas.drawText(mAlphaSliderText, rect.centerX(),
                rect.centerY() + 4 * mDensity, mAlphaTextPaint);
        }

        float rectWidth = 4 * mDensity / 2;

        Point p = alphaToPoint(mAlpha);

        RectF r = new RectF();
        r.left = p.x - rectWidth;
        r.right = p.x + rectWidth;
        r.top = rect.top - RECTANGLE_TRACKER_OFFSET;
        r.bottom = rect.bottom + RECTANGLE_TRACKER_OFFSET;

        canvas.drawRoundRect(r, 2, 2, mHueTrackerPaint);
    }


    private Point hueToPoint(float hue){
        final RectF rect = mHueRect;
        final float height = rect.height();

        Point p = new Point();

        p.y = (int) (height - (hue * height / 360f) + rect.top);
        p.x = (int) rect.left;

        return p;
    }

    private Point satValToPoint(float sat, float val){
        final RectF rect = mSatValRect;
        final float height = rect.height();
        final float width = rect.width();

        Point p = new Point();

        p.x = (int) (sat * width + rect.left);
        p.y = (int) ((1f - val) * height + rect.top);

        return p;
    }

    private Point alphaToPoint(int alpha){
        final RectF rect = mAlphaRect;
        final float width = rect.width();

        Point p = new Point();

        p.x = (int) (width - (alpha * width / 0xff) + rect.left);
        p.y = (int) rect.top;

        return p;
    }

    private float[] pointToSatVal(float x, float y){
        final RectF rect = mSatValRect;
        float[] result = new float[2];

        float width = rect.width();
        float height = rect.height();

        if (x < rect.left){
            x = 0f;
        }
        else if(x > rect.right){
            x = width;
        }
        else{
            x = x - rect.left;
        }

        if (y < rect.top){
            y = 0f;
        }
        else if(y > rect.bottom){
            y = height;
        }
        else{
            y = y - rect.top;
        }

        result[0] = 1.f / width * x;
        result[1] = 1.f - (1.f / height * y);

        return result;
    }

    private float pointToHue(float y){
        final RectF rect = mHueRect;

        float height = rect.height();

        if (y < rect.top){
            y = 0f;
        }
        else if(y > rect.bottom){
            y = height;
        }
        else{
            y = y - rect.top;
        }

        return 360f - (y * 360f / height);
    }

    private int pointToAlpha(int x){
        final RectF rect = mAlphaRect;
        final int width = (int) rect.width();

        if(x < rect.left){
            x = 0;
        }
        else if(x > rect.right){
            x = width;
        }
        else{
            x = x - (int)rect.left;
        }

        return 0xff - (x * 0xff / width);
    }


    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean update = false;

        if(event.getAction() == MotionEvent.ACTION_MOVE){

            switch(mLastTouchedPanel){

            case PANEL_SAT_VAL:

                float sat, val;

                sat = mSat + x/50f;
                val = mVal - y/50f;

                if(sat < 0f){
                    sat = 0f;
                }
                else if(sat > 1f){
                    sat = 1f;
                }

                if(val < 0f){
                    val = 0f;
                }
                else if(val > 1f){
                    val = 1f;
                }

                mSat = sat;
                mVal = val;

                update = true;

                break;

            case PANEL_HUE:

                float hue = mHue - y * 10f;

                if(hue < 0f){
                    hue = 0f;
                }
                else if(hue > 360f){
                    hue = 360f;
                }

                mHue = hue;

                update = true;

                break;

            case PANEL_ALPHA:

                if(!mShowAlphaPanel || mAlphaRect == null){
                    update = false;
                }
                else{

                    int alpha = (int) (mAlpha - x*10);

                    if(alpha < 0){
                        alpha = 0;
                    }
                    else if(alpha > 0xff){
                        alpha = 0xff;
                    }

                    mAlpha = alpha;


                    update = true;
                }
                break;
            }
        }

        if(update){
            if(mListener != null){
                mListener.onColorChanged(Color.HSVToColor(mAlpha, new float[]{mHue, mSat, mVal}));
            }
            invalidate();
            return true;
        }
        return super.onTrackballEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean update = false;

        switch(event.getAction()){

        case MotionEvent.ACTION_DOWN:

            mStartTouchPoint = new Point((int)event.getX(), (int)event.getY());

            update = moveTrackersIfNeeded(event);

            break;

        case MotionEvent.ACTION_MOVE:

            update = moveTrackersIfNeeded(event);

            break;

        case MotionEvent.ACTION_UP:

            mStartTouchPoint = null;

            update = moveTrackersIfNeeded(event);

            break;
        }
        if(update){
            if(mListener != null){
                mListener.onColorChanged(Color.HSVToColor(mAlpha, new float[]{mHue, mSat, mVal}));
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean moveTrackersIfNeeded(MotionEvent event){
        if(mStartTouchPoint == null) return false;

        boolean update = false;

        int startX = mStartTouchPoint.x;
        int startY = mStartTouchPoint.y;


        if(mHueRect.contains(startX, startY)){
            mLastTouchedPanel = PANEL_HUE;

            mHue = pointToHue(event.getY());

            update = true;
        } else if(mSatValRect.contains(startX, startY)){

            mLastTouchedPanel = PANEL_SAT_VAL;

            float[] result = pointToSatVal(event.getX(), event.getY());

            mSat = result[0];
            mVal = result[1];

            update = true;
        } else if(mAlphaRect != null && mAlphaRect.contains(startX, startY)){

            mLastTouchedPanel = PANEL_ALPHA;

            mAlpha = pointToAlpha((int)event.getX());

            update = true;
        }
        return update;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 0;
        int height = 0;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int widthAllowed = MeasureSpec.getSize(widthMeasureSpec);
        int heightAllowed = MeasureSpec.getSize(heightMeasureSpec);

        widthAllowed = chooseWidth(widthMode, widthAllowed);
        heightAllowed = chooseHeight(heightMode, heightAllowed);

        if(!mShowAlphaPanel){

            height = (int) (widthAllowed - PANEL_SPACING - HUE_PANEL_WIDTH);

            // Checks if calculated height (based on the width) is more than the allowed height
            if(height > heightAllowed) {
                height = heightAllowed;
                width = (int) (height + PANEL_SPACING + HUE_PANEL_WIDTH);
            }
            else{
                width = widthAllowed;
            }
        }
        else{

            width = (int) (heightAllowed - ALPHA_PANEL_HEIGHT + HUE_PANEL_WIDTH);

            if(width > widthAllowed){
                width = widthAllowed;
                height = (int) (widthAllowed - HUE_PANEL_WIDTH + ALPHA_PANEL_HEIGHT);
            }
            else{
                height = heightAllowed;
            }

        }
        setMeasuredDimension(width, height);
    }

    private int chooseWidth(int mode, int size){
        if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
            return size;
        } else { // (mode == MeasureSpec.UNSPECIFIED)
            return getPrefferedWidth();
        }
    }

    private int chooseHeight(int mode, int size){
        if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) {
            return size;
        } else { // (mode == MeasureSpec.UNSPECIFIED)
            return getPrefferedHeight();
        }
    }

    private int getPrefferedWidth(){
        int width = getPrefferedHeight();

        if(mShowAlphaPanel){
            width -= (PANEL_SPACING + ALPHA_PANEL_HEIGHT);
        }

        return (int) (width + HUE_PANEL_WIDTH + PANEL_SPACING);
    }

    private int getPrefferedHeight(){
        int height = (int)(200 * mDensity);

        if(mShowAlphaPanel){
            height += PANEL_SPACING + ALPHA_PANEL_HEIGHT;
        }

        return height;
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mDrawingRect = new RectF();
        mDrawingRect.left = mDrawingOffset + getPaddingLeft();
        mDrawingRect.right  = w - mDrawingOffset - getPaddingRight();
        mDrawingRect.top = mDrawingOffset + getPaddingTop();
        mDrawingRect.bottom = h - mDrawingOffset - getPaddingBottom();

        setUpSatValRect();
        setUpHueRect();
        setUpAlphaRect();
    }

    private void setUpSatValRect(){

        final RectF    dRect = mDrawingRect;
        float panelSide = dRect.height() - BORDER_WIDTH_PX * 2;

        if(mShowAlphaPanel){
            panelSide -= PANEL_SPACING + ALPHA_PANEL_HEIGHT;
        }

        float left = dRect.left + BORDER_WIDTH_PX;
        float top = dRect.top + BORDER_WIDTH_PX;
        float bottom = top + panelSide;
        float right = left + panelSide;

        mSatValRect = new RectF(left,top, right, bottom);
    }

    private void setUpHueRect(){
        final RectF    dRect = mDrawingRect;

        float left = dRect.right - HUE_PANEL_WIDTH + BORDER_WIDTH_PX;
        float top = dRect.top + BORDER_WIDTH_PX;
        float bottom = dRect.bottom - BORDER_WIDTH_PX - (mShowAlphaPanel ? (PANEL_SPACING + ALPHA_PANEL_HEIGHT) : 0);
        float right = dRect.right - BORDER_WIDTH_PX;

        mHueRect = new RectF(left, top, right, bottom);
    }

    private void setUpAlphaRect() {
        if(!mShowAlphaPanel) return;

        final RectF dRect = mDrawingRect;

        float left = dRect.left + BORDER_WIDTH_PX;
        float top = dRect.bottom - ALPHA_PANEL_HEIGHT + BORDER_WIDTH_PX;
        float bottom = dRect.bottom - BORDER_WIDTH_PX;
        float right = dRect.right - BORDER_WIDTH_PX;

        mAlphaRect = new RectF(left, top, right, bottom);

        mAlphaPattern = new AlphaPatternDrawable((int) (5 * mDensity), getContext());
        mAlphaPattern.setBounds(Math.round(mAlphaRect.left), Math.round(mAlphaRect.top), Math.round(mAlphaRect.right), Math.round(mAlphaRect.bottom)
        );
    }

    // Set OnColorChangedListener to get notified when selected user color changes
    public void setOnColorChangedListener(OnColorChangedListener listener){
        mListener = listener;
    }

    // Set the surrounding panel border color
    public void setBorderColor(int color){
        mBorderColor = color;
        invalidate();
    }

    // Get the surrounding panel border color
    public int getBorderColor(){
        return mBorderColor;
    }

    // Get the current color this view is showing
    public int getColor(){
        return OrigColor;
    }

    // Set the color the view should show
    public void setColor(int color){
        setColor(color, false);
    }

    // Set the color this view should show and flag if you want oncolorchangelistener callback
    public void setColor(int color, boolean callback){
        // Store this to avoid changes that can happen when converting int-to-HSV-to-int
        OrigColor = color;

        int alpha = Color.alpha(color);
        int red = Color.red(color);
        int blue = Color.blue(color);
        int green = Color.green(color);

        float[] hsv = new float[3];

        Color.RGBToHSV(red, green, blue, hsv);

        mAlpha = alpha;
        mHue = hsv[0];
        mSat = hsv[1];
        mVal = hsv[2];

        if(callback && mListener != null){
            mListener.onColorChanged(color);
        }

        invalidate();
    }

    /* Get the drawing offset of the color picker view.
     * This is the distance from the side of a panel to the side of the view
     * minus the padding. Good for aligning things */
    public float getDrawingOffset(){
        return mDrawingOffset;
    }

    // Set if the user is allowed to adjust the alpha panel - if not, no alpha will be set
    public void setAlphaSliderVisible(boolean visible){
        if(mShowAlphaPanel != visible){
            mShowAlphaPanel = visible;

            // Reset shaders to force recreation
            mValShader = null;
            mSatShader = null;
            mHueShader = null;
            mAlphaShader = null;;

            requestLayout();
        }
    }

    public void setSliderTrackerColor(int color){
        mSliderTrackerColor = color;

        mHueTrackerPaint.setColor(mSliderTrackerColor);

        invalidate();
    }

    public int getSliderTrackerColor(){
        return mSliderTrackerColor;
    }

    // Set % text shown in the alpha slider
    public void setAlphaSliderText (boolean enable) {
        if (enable && mShowAlphaPanel) {
            int f = (int) ((((double) mAlpha) * 100) / ((double) 255));
            mAlphaSliderText = f + "%";
        } else {
            mAlphaSliderText = "";
        }
        invalidate();
    }

    // Get current value of text shown in the alpha
    public String getAlphaSliderText(){
        return mAlphaSliderText;
    }

    public synchronized void updateText() {
        int f = (int)((((double)mAlpha)*100)/((double)255));
        mAlphaSliderText = f + "%";
        drawableStateChanged();
    }

}
