/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.slice.widget;

import static android.app.slice.Slice.EXTRA_TOGGLE_STATE;
import static android.app.slice.Slice.HINT_LIST_ITEM;
import static android.app.slice.Slice.HINT_NO_TINT;
import static android.app.slice.Slice.HINT_PARTIAL;
import static android.app.slice.Slice.HINT_SHORTCUT;
import static android.app.slice.Slice.SUBTYPE_TOGGLE;
import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_IMAGE;
import static android.app.slice.SliceItem.FORMAT_INT;
import static android.app.slice.SliceItem.FORMAT_SLICE;
import static android.app.slice.SliceItem.FORMAT_TIMESTAMP;

import static androidx.slice.core.SliceHints.EXTRA_RANGE_VALUE;
import static androidx.slice.core.SliceHints.ICON_IMAGE;
import static androidx.slice.core.SliceHints.SMALL_IMAGE;
import static androidx.slice.core.SliceHints.SUBTYPE_MAX;
import static androidx.slice.core.SliceHints.SUBTYPE_VALUE;
import static androidx.slice.widget.SliceView.MODE_SMALL;

import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.ColorInt;
import androidx.annotation.RestrictTo;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.core.SliceActionImpl;
import androidx.slice.core.SliceQuery;
import androidx.slice.view.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Row item is in small template format and can be used to construct list items for use
 * with {@link LargeTemplateView}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RowView extends SliceChildView implements View.OnClickListener {

    private static final String TAG = "RowView";

    // The number of items that fit on the right hand side of a small slice
    private static final int MAX_END_ITEMS = 3;

    private LinearLayout mRootView;
    private LinearLayout mStartContainer;
    private LinearLayout mContent;
    private TextView mPrimaryText;
    private TextView mSecondaryText;
    private TextView mLastUpdatedText;
    private View mDivider;
    private ArrayList<CompoundButton> mToggles = new ArrayList<>();
    private LinearLayout mEndContainer;
    private SeekBar mSeekBar;
    private ProgressBar mProgressBar;
    private View mSeeMoreView;

    private int mRowIndex;
    private RowContent mRowContent;
    private SliceActionImpl mRowAction;
    private boolean mIsHeader;
    private List<SliceItem> mHeaderActions;

    private int mImageSize;
    private int mIconSize;
    private int mPadding;

    public RowView(Context context) {
        super(context);
        mIconSize = getContext().getResources().getDimensionPixelSize(R.dimen.abc_slice_icon_size);
        mImageSize = getContext().getResources().getDimensionPixelSize(
                R.dimen.abc_slice_small_image_size);
        mPadding = getContext().getResources().getDimensionPixelSize(R.dimen.abc_slice_padding);
        inflate(context, R.layout.abc_slice_small_template, this);
        mRootView = findViewById(R.id.row_view);

        mStartContainer = (LinearLayout) findViewById(R.id.icon_frame);
        mContent = (LinearLayout) findViewById(android.R.id.content);
        mPrimaryText = (TextView) findViewById(android.R.id.title);
        mSecondaryText = (TextView) findViewById(android.R.id.summary);
        mLastUpdatedText = (TextView) findViewById(R.id.last_updated);
        mDivider = findViewById(R.id.divider);
        mEndContainer = (LinearLayout) findViewById(android.R.id.widget_frame);
        mSeekBar = (SeekBar) findViewById(R.id.seek_bar);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
    }


    @Override
    public int getSmallHeight() {
        // RowView is in small format when it is the header of a list and displays at max height.
        return mRowContent != null && mRowContent.isValid() ? mRowContent.getSmallHeight() : 0;
    }

    @Override
    public int getActualHeight() {
        return mRowContent != null && mRowContent.isValid() ? mRowContent.getActualHeight() : 0;
    }

    @Override
    public void setTint(@ColorInt int tintColor) {
        super.setTint(tintColor);
        if (mRowContent != null) {
            // TODO -- can be smarter about this
            populateViews();
        }
    }

    @Override
    public void setSliceActions(List<SliceItem> actions) {
        mHeaderActions = actions;
        if (mRowContent != null) {
            populateViews();
        }
    }

    @Override
    public void setShowLastUpdated(boolean showLastUpdated) {
        super.setShowLastUpdated(showLastUpdated);
        if (mRowContent != null) {
            populateViews();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = getMode() == MODE_SMALL ? getSmallHeight() : getActualHeight();
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setSlice(Slice slice) {
        // Nothing to do
    }

    /**
     * This is called when RowView is being used as a component in a large template.
     */
    @Override
    public void setSliceItem(SliceItem slice, boolean isHeader, int index,
            SliceView.OnSliceActionListener observer) {
        setSliceActionListener(observer);
        mRowIndex = index;
        mIsHeader = !slice.hasHint(HINT_LIST_ITEM);
        mHeaderActions = null;
        mRowContent = new RowContent(getContext(), slice, mIsHeader);
        populateViews();
    }

    private void populateViews() {
        resetView();
        if (mRowContent.isDefaultSeeMore()) {
            showSeeMore();
            return;
        }
        CharSequence contentDescr = mRowContent.getContentDescription();
        if (contentDescr != null) {
            mContent.setContentDescription(contentDescr);
        }
        boolean showStart = false;
        final SliceItem startItem = mRowContent.getStartItem();
        if (startItem != null) {
            final EventInfo info = new EventInfo(getMode(),
                    EventInfo.ACTION_TYPE_BUTTON,
                    EventInfo.ROW_TYPE_LIST, mRowIndex);
            info.setPosition(EventInfo.POSITION_START, 0, 1);
            showStart = addItem(startItem, mTintColor, true /* isStart */, 0 /* padding */, info);
        }
        mStartContainer.setVisibility(showStart ? View.VISIBLE : View.GONE);

        final SliceItem titleItem = mRowContent.getTitleItem();
        if (titleItem != null) {
            mPrimaryText.setText(titleItem.getText());
        }
        mPrimaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mIsHeader
                ? mHeaderTitleSize
                : mTitleSize);
        mPrimaryText.setTextColor(mTitleColor);
        mPrimaryText.setVisibility(titleItem != null ? View.VISIBLE : View.GONE);

        final SliceItem subtitleItem = getMode() == MODE_SMALL
                ? mRowContent.getSummaryItem()
                : mRowContent.getSubtitleItem();
        addSubtitle(subtitleItem);

        SliceItem primaryAction = mRowContent.getPrimaryAction();
        if (primaryAction != null) {
            mRowAction = new SliceActionImpl(primaryAction);
            if (mRowAction.isToggle()) {
                // If primary action is a toggle, add it and we're done
                addToggle(mRowAction, mTintColor, mEndContainer);
                setViewClickable(mRootView, true);
                return;
            }
        }

        final SliceItem range = mRowContent.getRange();
        if (range != null) {
            if (mRowAction != null) {
                setViewClickable(mRootView, true);
            }
            addRange(range);
            return;
        }

        // If we're here we can can show end items; check for top level actions first
        List<SliceItem> endItems = mRowContent.getEndItems();
        if (mIsHeader && mHeaderActions != null && mHeaderActions.size() > 0) {
            // Use these if we have them instead
            endItems = mHeaderActions;
        }
        boolean hasRowAction = mRowAction != null;
        if (endItems.isEmpty()) {
            if (hasRowAction) setViewClickable(mRootView, true);
            return;
        }

        // If we're here we might be able to show end items
        int itemCount = 0;
        boolean firstItemIsADefaultToggle = false;
        boolean hasEndItemAction = false;
        for (int i = 0; i < endItems.size(); i++) {
            final SliceItem endItem = endItems.get(i);
            if (itemCount < MAX_END_ITEMS) {
                final EventInfo info = new EventInfo(getMode(),
                        EventInfo.ACTION_TYPE_BUTTON,
                        EventInfo.ROW_TYPE_LIST, mRowIndex);
                info.setPosition(EventInfo.POSITION_END, i,
                        Math.min(endItems.size(), MAX_END_ITEMS));
                if (addItem(endItem, mTintColor, false /* isStart */, mPadding, info)) {
                    if (FORMAT_ACTION.equals(endItem.getFormat())) {
                        hasEndItemAction = true;
                    }
                    itemCount++;
                    if (itemCount == 1) {
                        firstItemIsADefaultToggle = !mToggles.isEmpty()
                                && SliceQuery.find(endItem.getSlice(), FORMAT_IMAGE) == null;
                    }
                }
            }
        }

        // If there is a row action and the first end item is a default toggle, show the divider.
        mDivider.setVisibility(hasRowAction && firstItemIsADefaultToggle
                ? View.VISIBLE : View.GONE);
        if (hasRowAction) {
            if (itemCount > 0 && hasEndItemAction) {
                setViewClickable(mContent, true);
            } else {
                setViewClickable(mRootView, true);
            }
        } else if (mRowContent.endItemsContainAction() && itemCount == 1) {
            // If the only end item is an action, make the whole row clickable.
            SliceItem unwrappedActionItem = endItems.get(0).getSlice().getItems().get(0);
            if (!SUBTYPE_TOGGLE.equals(unwrappedActionItem.getSubType())) {
                mRowAction = new SliceActionImpl(endItems.get(0));
            }
            setViewClickable(mRootView, true);
        }
    }

    private void addSubtitle(final SliceItem subtitleItem) {
        CharSequence subtitleTimeString = null;
        if (mShowLastUpdated) {
            subtitleTimeString = getResources().getString(R.string.abc_slice_updated,
                    SliceViewUtil.getRelativeTimeString(mLastUpdated));
        }
        CharSequence subtitle = subtitleItem != null ? subtitleItem.getText() : null;
        boolean subtitleExists = !TextUtils.isEmpty(subtitle)
                        || (subtitleItem != null && subtitleItem.hasHint(HINT_PARTIAL));
        if (subtitleExists) {
            mSecondaryText.setText(subtitle);
            mSecondaryText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mIsHeader
                    ? mHeaderSubtitleSize
                    : mSubtitleSize);
            mSecondaryText.setTextColor(mSubtitleColor);
        }
        if (subtitleTimeString != null) {
            if (!TextUtils.isEmpty(subtitle)) {
                subtitleTimeString = " \u00B7 " + subtitleTimeString;
            }
            SpannableString sp = new SpannableString(subtitleTimeString);
            sp.setSpan(new StyleSpan(Typeface.ITALIC), 0, subtitleTimeString.length(), 0);
            mLastUpdatedText.setText(sp);
            mLastUpdatedText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mIsHeader ? mHeaderSubtitleSize : mSubtitleSize);
            mLastUpdatedText.setTextColor(mSubtitleColor);
        }
        mLastUpdatedText.setVisibility(TextUtils.isEmpty(subtitleTimeString) ? GONE : VISIBLE);
        mSecondaryText.setVisibility(subtitleExists ? VISIBLE : GONE);
    }

    private void addRange(final SliceItem range) {
        final boolean isSeekBar = FORMAT_ACTION.equals(range.getFormat());
        final ProgressBar progressBar = isSeekBar ? mSeekBar : mProgressBar;
        SliceItem max = SliceQuery.findSubtype(range, FORMAT_INT, SUBTYPE_MAX);
        if (max != null) {
            progressBar.setMax(max.getInt());
        }
        SliceItem progress = SliceQuery.findSubtype(range, FORMAT_INT, SUBTYPE_VALUE);
        if (progress != null) {
            progressBar.setProgress(progress.getInt());
        }
        progressBar.setVisibility(View.VISIBLE);
        if (mTintColor != -1) {
            Drawable drawable = DrawableCompat.wrap(progressBar.getProgressDrawable());
            DrawableCompat.setTint(drawable, mTintColor);
            mProgressBar.setProgressDrawable(drawable);
        }
        if (isSeekBar) {
            SliceItem thumb = SliceQuery.find(range, FORMAT_IMAGE);
            if (thumb != null) {
                mSeekBar.setThumb(thumb.getIcon().loadDrawable(getContext()));
            }
            if (mTintColor != -1) {
                Drawable drawable = DrawableCompat.wrap(mSeekBar.getThumb());
                DrawableCompat.setTint(drawable, mTintColor);
                mSeekBar.setThumb(drawable);
            }
            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    try {
                        // TODO: sending this PendingIntent should be rate limited.
                        range.fireAction(getContext(),
                                new Intent().putExtra(EXTRA_RANGE_VALUE, progress));
                    } catch (CanceledException e) { }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { }
            });
        }
    }

    /**
     * Add a toggle view to container.
     */
    private void addToggle(final SliceActionImpl actionContent, int color, ViewGroup container) {
        // Check if this is a custom toggle
        final CompoundButton toggle;
        if (actionContent.isToggle() && !actionContent.isDefaultToggle()) {
            IconCompat checkedIcon = actionContent.getIcon();
            if (color != -1) {
                // TODO - Should custom toggle buttons be tinted? What if the app wants diff
                // colors per state?
                checkedIcon.setTint(color);
            }
            toggle = new ToggleButton(getContext());
            ((ToggleButton) toggle).setTextOff("");
            ((ToggleButton) toggle).setTextOn("");
            toggle.setBackground(checkedIcon.loadDrawable(getContext()));
            container.addView(toggle);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) toggle.getLayoutParams();
            lp.width = mIconSize;
            lp.height = mIconSize;
        } else {
            toggle = new Switch(getContext());
            container.addView(toggle);
        }
        CharSequence contentDesc = actionContent.getContentDescription();
        if (contentDesc != null) {
            toggle.setContentDescription(contentDesc);
        }
        toggle.setChecked(actionContent.isChecked());
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    Intent i = new Intent().putExtra(EXTRA_TOGGLE_STATE, isChecked);
                    actionContent.getActionItem().fireAction(getContext(), i);
                    if (mObserver != null) {
                        final EventInfo info = new EventInfo(getMode(),
                                EventInfo.ACTION_TYPE_TOGGLE,
                                EventInfo.ROW_TYPE_TOGGLE, mRowIndex);
                        info.state = isChecked ? EventInfo.STATE_ON : EventInfo.STATE_OFF;
                        mObserver.onSliceAction(info, actionContent.getSliceItem());
                    }
                } catch (CanceledException e) {
                    toggle.setSelected(!isChecked);
                }
            }
        });
        mToggles.add(toggle);
    }

    /**
     * Adds simple items to a container. Simple items include actions with icons, images, or
     * timestamps.
     */
    private boolean addItem(SliceItem sliceItem, int color, boolean isStart, int padding,
            final EventInfo info) {
        IconCompat icon = null;
        int imageMode = 0;
        SliceItem timeStamp = null;
        SliceActionImpl actionContent = null;
        ViewGroup container = isStart ? mStartContainer : mEndContainer;
        if (FORMAT_SLICE.equals(sliceItem.getFormat())) {
            // It's an action.... let's make it so
            if (sliceItem.hasHint(HINT_SHORTCUT)) {
                actionContent = new SliceActionImpl(sliceItem);
            } else {
                sliceItem = sliceItem.getSlice().getItems().get(0);
            }
        }
        if (actionContent != null) {
            if (actionContent.isToggle()) {
                addToggle(actionContent, color, container);
                return true;
            }
            icon = actionContent.getIcon();
            if (icon != null) {
                imageMode = actionContent.getImageMode();
            }
        }
        if (FORMAT_IMAGE.equals(sliceItem.getFormat())) {
            icon = sliceItem.getIcon();
            imageMode = sliceItem.hasHint(HINT_NO_TINT) ? SMALL_IMAGE : ICON_IMAGE;
        } else if (FORMAT_TIMESTAMP.equals(sliceItem.getFormat())) {
            timeStamp = sliceItem;
        }
        View addedView = null;
        if (icon != null) {
            ImageView iv = new ImageView(getContext());
            iv.setImageDrawable(icon.loadDrawable(getContext()));
            int size = mImageSize;
            if (imageMode == ICON_IMAGE) {
                if (color != -1) {
                    iv.setColorFilter(color);
                }
                size = mIconSize;
            }
            if (actionContent != null && actionContent.getContentDescription() != null) {
                iv.setContentDescription(actionContent.getContentDescription());
            }
            container.addView(iv);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMarginStart(padding);
            addedView = iv;
        } else if (timeStamp != null) {
            TextView tv = new TextView(getContext());
            tv.setText(SliceViewUtil.getRelativeTimeString(sliceItem.getTimestamp()));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mSubtitleSize);
            tv.setTextColor(mSubtitleColor);
            container.addView(tv);
            addedView = tv;
        }
        if (actionContent != null && addedView != null) {
            final SliceActionImpl finalAction = actionContent;
            addedView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        finalAction.getActionItem().fireAction(null, null);
                        if (mObserver != null) {
                            mObserver.onSliceAction(info, finalAction.getSliceItem());
                        }
                    } catch (CanceledException e) {
                        e.printStackTrace();
                    }
                }
            });
            addedView.setBackground(SliceViewUtil.getDrawable(getContext(),
                    android.R.attr.selectableItemBackground));
        }
        return addedView != null;
    }

    private void showSeeMore() {
        Button b = (Button) LayoutInflater.from(getContext()).inflate(
                R.layout.abc_slice_row_show_more, this, false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (mObserver != null) {
                        EventInfo info = new EventInfo(getMode(), EventInfo.ACTION_TYPE_SEE_MORE,
                                EventInfo.ROW_TYPE_LIST, mRowIndex);
                        mObserver.onSliceAction(info, mRowContent.getSlice());
                    }
                    mRowContent.getSlice().fireAction(null, null);
                } catch (CanceledException e) {
                    Log.w(TAG, "PendingIntent for slice cannot be sent", e);
                }
            }
        });
        if (mTintColor != -1) {
            b.setTextColor(mTintColor);
        }
        mSeeMoreView = b;
        addView(mSeeMoreView);
    }

    @Override
    public void onClick(View view) {
        if (mRowAction != null && mRowAction.getActionItem() != null && !mRowAction.isToggle()) {
            // Check for a row action
            try {
                mRowAction.getActionItem().fireAction(null, null);
                if (mObserver != null) {
                    EventInfo info = new EventInfo(getMode(), EventInfo.ACTION_TYPE_CONTENT,
                            EventInfo.ROW_TYPE_LIST, mRowIndex);
                    mObserver.onSliceAction(info, mRowAction.getSliceItem());
                }
            } catch (CanceledException e) {
                Log.w(TAG, "PendingIntent for slice cannot be sent", e);
            }
        } else if (mToggles.size() == 1) {
            // If there is only one toggle and no row action, just toggle it.
            mToggles.get(0).toggle();
        }
    }

    private void setViewClickable(View layout, boolean isClickable) {
        layout.setOnClickListener(isClickable ? this : null);
        layout.setBackground(isClickable
                ? SliceViewUtil.getDrawable(getContext(), android.R.attr.selectableItemBackground)
                : null);
        layout.setClickable(isClickable);
    }

    @Override
    public void resetView() {
        setViewClickable(mRootView, false);
        setViewClickable(mContent, false);
        mStartContainer.removeAllViews();
        mEndContainer.removeAllViews();
        mPrimaryText.setText(null);
        mSecondaryText.setText(null);
        mToggles.clear();
        mRowAction = null;
        mDivider.setVisibility(View.GONE);
        mSeekBar.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.GONE);
        if (mSeeMoreView != null) {
            removeView(mSeeMoreView);
        }
    }
}