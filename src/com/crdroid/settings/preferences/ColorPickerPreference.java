package com.crdroid.settings.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.crdroid.settings.crDroidSettings;
import com.crdroid.settings.preferences.ColorPickerDialog.OnColorChangedListener;

import java.util.Locale;


public class ColorPickerPreference extends Preference implements OnPreferenceClickListener, OnColorChangedListener {

    View mView;
    ColorPickerDialog mDialog;
    LinearLayout widgetFrameView;
    private int mValue = Color.BLACK;
    private float mDensity = 1f;
    private boolean mAlphaSliderEnabled = false;
    private boolean mAlphaSliderText = false;
    private boolean mPrefEnabled = true;

    private static final String androidns = "http://schemas.android.com/apk/res/android";
    private String mKey;
    private String mSummary;
    private String mTitle;
    private String mSpecialFlag = "";
    private int mDefault;

    public ColorPickerPreference(Context context) {
        super(context);
        init(context, null);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        onColorChanged(Settings.Global.getInt(getContext().getContentResolver(), mKey, mDefault));
    }

    private void init(Context context, AttributeSet attrs) {
        mDensity = context.getResources().getDisplayMetrics().density;
        setOnPreferenceClickListener(this);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickPref, 0, 0);
            mAlphaSliderEnabled = a.getBoolean(R.styleable.ColorPickPref_alphaSlider, false);

            mAlphaSliderText = a.getBoolean(R.styleable.ColorPickPref_alphaText, false);

            mSpecialFlag = a.getString(R.styleable.ColorPickPref_colorgroupname);

            int mKeyId = attrs.getAttributeResourceValue(androidns, "key", 0);
            if (mKeyId == 0) {
                mKey = attrs.getAttributeValue(androidns, "key");
            } else {
                mKey = context.getString(mKeyId);
            }

            int mSummId = attrs.getAttributeResourceValue(androidns, "summary", 0);
            if (mSummId == 0) {
                mSummary = attrs.getAttributeValue(androidns, "summary");
            } else {
                mSummary = context.getString(mSummId);
            }

            int mTitleId = attrs.getAttributeResourceValue(androidns, "dialogTitle", 0);
            if (mTitleId == 0) {
                mTitle = attrs.getAttributeValue(androidns, "dialogTitle");
            } else {
                mTitle = context.getString(mTitleId);
            }

            mDefault = convertToColorInt(attrs.getAttributeValue(androidns, "defaultValue"));
			a.recycle();
        }
    }

    @Override
    protected void onBindView(View view) {
        mView = view;
        super.onBindView(view);

        widgetFrameView = ((LinearLayout) view.findViewById(android.R.id.widget_frame));

        setPreviewColor(mPrefEnabled);
    }

    private void setPreviewColor(boolean enable) {
        int apicheck = Build.VERSION.SDK_INT;
        int rightpadsdk;

        if (mView == null)
            return;

        ImageView iView = new ImageView(getContext());
        LinearLayout widgetFrameView = ((LinearLayout) mView.findViewById(android.R.id.widget_frame));
        if (widgetFrameView == null) return;

        widgetFrameView.setVisibility(View.VISIBLE);

        if (apicheck < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            rightpadsdk = 5;
        } else {
            rightpadsdk = 8;
        }

        widgetFrameView.setPadding(
                widgetFrameView.getPaddingLeft(),
                widgetFrameView.getPaddingTop(),
                (int) (mDensity * rightpadsdk),
                widgetFrameView.getPaddingBottom()
        );
        // remove already create preview image
        int count = widgetFrameView.getChildCount();
        if (count > 0) {
            widgetFrameView.removeViews(0, count);
        }
        widgetFrameView.addView(iView);
        widgetFrameView.setMinimumWidth(0);

        if (apicheck < Build.VERSION_CODES.JELLY_BEAN) {
            iView.setBackgroundDrawable(new AlphaPatternDrawable((int) (5 * mDensity), getContext()));
        } else {
            iView.setBackground(new AlphaPatternDrawable((int) (5 * mDensity), getContext()));
        }

        iView.setImageBitmap(getPreviewBitmap(enable));
    }

    private Bitmap getPreviewBitmap(boolean enable) {
        int d = (int) (mDensity * 31); // 30dip - but why show 31 there then?
        Bitmap bm = Bitmap.createBitmap(d, d, Config.ARGB_8888);
        int w = bm.getWidth();
        int h = bm.getHeight();
        int c;
        for (int i = 0; i < w; i++) {
            for (int j = i; j < h; j++) {
                if (i <= 1 || j <= 1 || i >= w - 2 || j >= h - 2) {
                    c = Color.GRAY;
                } else {
                    c = (enable) ? mValue : Color.DKGRAY;
                }

                bm.setPixel(i, j, c);
                if (i != j) {
                    bm.setPixel(j, i, c);
                }
            }
        }
        return bm;
    }

    public int getPrefDefault() {
        return mDefault;
    }

    public String getPrefFlag() {
        return mSpecialFlag;
    }

    public String getSummaryText() {
        return ((getContext().getResources().getBoolean(R.bool.showFullColorPickSummary)) && !TextUtils.isEmpty(mSummary)) ? mSummary + " - " : "";
    }

    public void setInitialColor(int color) {
        mValue = mDefault;
    }

    @Override
    public void onColorChanged(int color) {
        mValue = color;
        setPreviewColor(mPrefEnabled);
        try {
            getOnPreferenceChangeListener().onPreferenceChange(this, color);
        } catch (NullPointerException e) {
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        showDialog(null);
        return false;
    }

    protected void showDialog(Bundle state) {
        if (mDialog == null || !mDialog.isShowing()) {
            // force orientation to stay while dialog is open
            crDroidSettings.lockCurrentOrientation((SettingsActivity) getContext());
            mDialog = new ColorPickerDialog(getContext(), mValue, mDefault, mKey, mTitle);
            mDialog.setOnColorChangedListener(this);
            // undo orientation fixing on dismiss of dialog
            mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    ((SettingsActivity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                }
            });
            if (mAlphaSliderEnabled) {
                mDialog.setAlphaSliderVisible(true);
                mDialog.setAlphaSliderText(mAlphaSliderText);
            }
            if (state != null) {
                mDialog.onRestoreInstanceState(state);
            }
            mDialog.show();
        }
    }

    // Toggle Alpha Slider visibility (by default it's disabled)
    public void setAlphaSliderEnabled(boolean enable) {
        mAlphaSliderEnabled = enable;
    }

    // Set Alpha Slider text (if slider is visible)
    public void setAlphaSliderText(boolean enable) {
        mAlphaSliderText = enable;
    }

    // Lets color get set external to picker
    public void setNewInitialColor(int color) {
        onColorChanged(color);
    }

    // Sets up some preference specific stuff like colorgroupname
    public void setPrefFlag(String specialflag) {
        mSpecialFlag = specialflag;
    }

    // For custom purposes. Not used by ColorPickerPreferrence
    public void setNewPreviewColor(int color) {
        onColorChanged(color);
    }

    public void setPreviewDim(boolean enable) {
        mPrefEnabled = enable;
        setPreviewColor(mPrefEnabled);
    }

    // For custom purposes. Not used by ColorPickerPreferrence
    public static String convertToARGB(int color) {
        String alpha = Integer.toHexString(Color.alpha(color));
        String red = Integer.toHexString(Color.red(color));
        String green = Integer.toHexString(Color.green(color));
        String blue = Integer.toHexString(Color.blue(color));

        if (alpha.length() == 1) {
            alpha = "0" + alpha;
        }

        if (red.length() == 1) {
            red = "0" + red;
        }

        if (green.length() == 1) {
            green = "0" + green;
        }

        if (blue.length() == 1) {
            blue = "0" + blue;
        }

        return ("#" + alpha + red + green + blue).toUpperCase(Locale.getDefault());
    }

    // For custom purposes. Not used by ColorPickerPreferrence
    public static int convertToColorInt(String argb) throws NumberFormatException {
        if (TextUtils.isEmpty(argb)) { return Color.BLACK; }

        if (argb.startsWith("#")) {
            argb = argb.replace("#", "");
        }

        if (argb.startsWith("0x")) {
            argb = argb.replace("0x", "");
        }

        int alpha, red, green, blue;

        if (argb.length() == 8) {
            alpha = Integer.parseInt(argb.substring(0, 2), 16);
            red = Integer.parseInt(argb.substring(2, 4), 16);
            green = Integer.parseInt(argb.substring(4, 6), 16);
            blue = Integer.parseInt(argb.substring(6, 8), 16);
        } else if (argb.length() == 6) {
            alpha = 255;
            red = Integer.parseInt(argb.substring(0, 2), 16);
            green = Integer.parseInt(argb.substring(2, 4), 16);
            blue = Integer.parseInt(argb.substring(4, 6), 16);
        } else { return Color.BLACK; }

        return Color.argb(alpha, red, green, blue);
    }
}
