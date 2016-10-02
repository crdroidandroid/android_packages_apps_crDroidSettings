package com.crdroid.settings.preferences;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.settings.R;
import com.crdroid.settings.preferences.ColorPickerView.OnColorChangedListener;

import java.util.Locale;

public class ColorPickerDialog extends Dialog implements OnColorChangedListener, OnClickListener {

    public static String GLOBAL_COLOR_USER = "global_color_user";

    private ColorPickerView mColorPicker;

    private ColorPickerPanelView mNewColor;

    private EditText mHex;

    private boolean mAlphaEnabled;
    private boolean mAlphaTextEnabled;

    private String mKey;

    private int mUserBorder;

    private OnColorChangedListener mListener;

    public interface OnColorChangedListener {
        public void onColorChanged(int color);
    }

    public ColorPickerDialog(Context context, int initialColor, int defaultColor, String initKey, String itemTitle) {
        super(context);

        init(initialColor, defaultColor, initKey, itemTitle);
    }

    private void init(int color, int defaultColor, String initKey, String itemTitle) {
        // To fight color branding
        getWindow().setFormat(PixelFormat.RGBA_8888);
        // Hopefully makes it so softkeyboard doesn't show until edittext is clicked
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        // Removes title, making room for hex entry (on portrait at least)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setUp(color, defaultColor, initKey, itemTitle);
    }

    private void setUp(int color, final int defaultColor, String initKey, String itemTitle) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // null root seems fine here
        View layout = inflater.inflate(R.layout.dialog_color_picker, null);

        setContentView(layout);
        mKey = initKey;

        mColorPicker = (ColorPickerView) layout.findViewById(R.id.color_picker_view);
        ColorPickerPanelView mOldColor = (ColorPickerPanelView) layout.findViewById(R.id.old_color_panel);
        mNewColor = (ColorPickerPanelView) layout.findViewById(R.id.new_color_panel);

        TextView mTitleText = (TextView) layout.findViewById(R.id.colorpick_title);
        mTitleText.setText(itemTitle);

        mUserBorder = getContext().getResources().getColor(R.color.userpanel_border);

        ColorPickerPanelView mUserSet1 = (ColorPickerPanelView) layout.findViewById(R.id.userset1_panel);
        ColorPickerPanelView mUserSet2 = (ColorPickerPanelView) layout.findViewById(R.id.userset2_panel);
        ColorPickerPanelView mUserSet3 = (ColorPickerPanelView) layout.findViewById(R.id.userset3_panel);
        ColorPickerPanelView mUserSet4 = (ColorPickerPanelView) layout.findViewById(R.id.userset4_panel);
        ColorPickerPanelView mUserSet5 = (ColorPickerPanelView) layout.findViewById(R.id.userset5_panel);
        ColorPickerPanelView mUserSet6 = (ColorPickerPanelView) layout.findViewById(R.id.userset6_panel);

        mHex = (EditText) layout.findViewById(R.id.hex);
        Button mSetButton = (Button) layout.findViewById(R.id.enter);
        Button mResetButton = (Button) layout.findViewById(R.id.reset);

        ((LinearLayout) mOldColor.getParent()).setPadding(Math.round(mColorPicker.getDrawingOffset()), 0, Math.round(mColorPicker.getDrawingOffset()), 0);

        mOldColor.setOnClickListener(this);
        mNewColor.setOnClickListener(this);
        mColorPicker.setOnColorChangedListener(this);
        mOldColor.setColor(color);
        mColorPicker.setColor(color, true);

        setColorAndClickActionCustom(mUserSet1, "user1", getContext().getResources().getColor(R.color.userpanel_default1));
        setColorAndClickActionCustom(mUserSet2, "user2", getContext().getResources().getColor(R.color.userpanel_default2));
        setColorAndClickActionCustom(mUserSet3, "user3", getContext().getResources().getColor(R.color.userpanel_default3));
        setColorAndClickActionCustom(mUserSet4, "user4", getContext().getResources().getColor(R.color.userpanel_default4));
        setColorAndClickActionCustom(mUserSet5, "user5", getContext().getResources().getColor(R.color.userpanel_default5));
        setColorAndClickActionCustom(mUserSet6, "user6", getContext().getResources().getColor(R.color.userpanel_default6));


        if (mHex != null) {
            mHex.setText(ColorPickerPreference.convertToARGB(color).toUpperCase(Locale.getDefault()));
        }
        if (mSetButton != null) {
           mSetButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    String text = mHex.getText().toString();
                    try {
                        int newColor = ColorPickerPreference.convertToColorInt(text);
                        mColorPicker.setColor(newColor, true);
                    } catch (Exception e) {
                    }
                }
            });
        }
        if (mResetButton != null) {
            mResetButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    try {
                        mColorPicker.setColor(defaultColor, true);
                    } catch (Exception e) {
                    }
                }
            });
        }

    }

    @Override
    public void onColorChanged(int color) {
        mNewColor.setColor(color);
        try {
            if (mHex != null) {
                mHex.setText(ColorPickerPreference.convertToARGB(color).toUpperCase(Locale.getDefault()));
            }
            if (mAlphaTextEnabled && mAlphaEnabled) {
                mColorPicker.updateText();
            }
        } catch (Exception e) {

        }
    }

    public void setAlphaSliderVisible(boolean visible) {
        mColorPicker.setAlphaSliderVisible(visible);
        mAlphaEnabled = visible;
    }

    public void setAlphaSliderText(boolean enabletext) {
        mColorPicker.setAlphaSliderText(enabletext);
        mAlphaTextEnabled = enabletext;
    }

    public void setColorAndClickAction(final ColorPickerPanelView previewRect, final int color) {
        if (previewRect != null) {
            previewRect.setColor(color);
            previewRect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        mColorPicker.setColor(color, true);
                    } catch (Exception e) {
                    }
                }
            });
        }
    }

/* Maybe add in a pass for xml resource default color when method is called to pass through to default for setColor... */
    public void setColorAndClickActionCustom(final ColorPickerPanelView previewRect, final String extraKey, final int color) {
        if (previewRect != null) {
            final String customKey = (Settings.Global.getInt(getContext().getContentResolver(), GLOBAL_COLOR_USER, 0) == 0) ? "globalcolor" : mKey;
            previewRect.setColor(Settings.Global.getInt(getContext().getContentResolver(), customKey + "_" + extraKey, color));
            previewRect.setBorderColor(mUserBorder);
            previewRect.setBorderWidth(3.0f);
            previewRect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        mColorPicker.setColor(previewRect.getColor(), true);
                    } catch (Exception e) {
                    }
                }
            });

            previewRect.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    try {
                        Settings.Global.putInt(getContext().getContentResolver(), customKey + "_" + extraKey, mNewColor.getColor());
                        previewRect.setColor(mNewColor.getColor());
                    } catch (Exception e) {
                    }
                    return true;
                }
            });
        }
    }

    // Set OnColorChangedListener to get notified when user selected color changes
    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    public int getColor() {
        return mColorPicker.getColor();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.new_color_panel) {
            if (mListener != null) {
                mListener.onColorChanged(mNewColor.getColor());
            }
        }
        dismiss();
    }
}
