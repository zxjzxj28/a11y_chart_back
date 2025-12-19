package com.eagle.android.Fragment;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.eagle.android.R;
import com.eagle.android.util.PreferenceListStyler;

public class GestureConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName("a11y_prefs");
        requireActivity().setTitle(R.string.title_activity_gesture_config);
        setPreferencesFromResource(R.xml.prefs_gesture, rootKey);

        bindSummary("gesture_close_action");
        bindSummary("gesture_repeat_action");
        bindSummary("gesture_auto_broadcast_action");
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreferenceListStyler.apply(this);
    }

    private void bindSummary(String key) {
        ListPreference lp = findPreference(key);
        if (lp != null) {
            lp.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
