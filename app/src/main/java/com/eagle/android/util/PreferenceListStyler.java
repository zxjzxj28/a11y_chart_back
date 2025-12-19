package com.eagle.android.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.eagle.android.R;

/**
 * Helper to align preference items with tighter padding and visible dividers across all settings
 * screens. Mimics the standard Android settings layout with left-aligned items and dividers.
 */
public final class PreferenceListStyler {

    private PreferenceListStyler() {
        // no-op
    }

    public static void apply(@NonNull PreferenceFragmentCompat fragment) {
        RecyclerView recyclerView = fragment.getListView();
        Context context = recyclerView.getContext();

        // Remove list padding - let individual items handle their own padding
        recyclerView.setPaddingRelative(0, 0, 0, 0);
        recyclerView.setClipToPadding(false);

        // Remove existing decorations and add our divider
        while (recyclerView.getItemDecorationCount() > 0) {
            recyclerView.removeItemDecorationAt(0);
        }
        int marginStart = context.getResources()
                .getDimensionPixelOffset(R.dimen.preference_divider_margin_start);
        recyclerView.addItemDecoration(new InsetDividerDecoration(context, marginStart));
    }

    /**
     * Custom divider decoration that draws dividers with a left margin,
     * similar to the standard Android settings style.
     */
    private static class InsetDividerDecoration extends RecyclerView.ItemDecoration {
        private final Drawable divider;
        private final int marginStart;
        private final int dividerHeight;

        InsetDividerDecoration(Context context, int marginStart) {
            this.divider = AppCompatResources.getDrawable(context, R.drawable.preference_divider);
            this.marginStart = marginStart;
            this.dividerHeight = divider != null ? divider.getIntrinsicHeight() : 1;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int itemCount = state.getItemCount();
            // Add space below each item except the last one
            if (position < itemCount - 1) {
                outRect.bottom = dividerHeight;
            }
        }

        @Override
        public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                          @NonNull RecyclerView.State state) {
            if (divider == null) return;

            int childCount = parent.getChildCount();
            int right = parent.getWidth() - parent.getPaddingRight();
            int left = parent.getPaddingLeft() + marginStart;

            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                int position = parent.getChildAdapterPosition(child);
                // Don't draw divider after last item
                if (position >= state.getItemCount() - 1) continue;

                RecyclerView.LayoutParams params =
                        (RecyclerView.LayoutParams) child.getLayoutParams();
                int top = child.getBottom() + params.bottomMargin;
                int bottom = top + dividerHeight;
                divider.setBounds(left, top, right, bottom);
                divider.draw(canvas);
            }
        }
    }
}
