package com.eagle.android.util;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.eagle.android.R;

/**
 * Helper to align preference items with tighter padding and visible dividers across all settings
 * screens.
 */
public final class PreferenceListStyler {

    private PreferenceListStyler() {
        // no-op
    }

    public static void apply(@NonNull PreferenceFragmentCompat fragment) {
        RecyclerView recyclerView = fragment.getListView();
        Context context = recyclerView.getContext();

        int horizontalPadding = context.getResources()
                .getDimensionPixelOffset(R.dimen.preference_list_padding_horizontal);
        recyclerView.setPaddingRelative(
                horizontalPadding,
                recyclerView.getPaddingTop(),
                horizontalPadding,
                recyclerView.getPaddingBottom());
        recyclerView.setClipToPadding(false);

        if (recyclerView.getItemDecorationCount() == 0) {
            DividerItemDecoration decoration =
                    new DividerItemDecoration(context, DividerItemDecoration.VERTICAL);
            Drawable divider = AppCompatResources.getDrawable(context, R.drawable.preference_divider);
            if (divider != null) {
                decoration.setDrawable(divider);
            }
            recyclerView.addItemDecoration(decoration);
        }
    }
}
