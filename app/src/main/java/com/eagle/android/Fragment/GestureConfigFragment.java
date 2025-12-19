package com.eagle.android.Fragment;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.eagle.android.R;

public class GestureConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName("a11y_prefs");
        setPreferencesFromResource(R.xml.prefs_gesture, rootKey);

        bindSummary("gesture_close_action");
        bindSummary("gesture_repeat_action");
        bindSummary("gesture_auto_broadcast_action");
    }

    private void bindSummary(String key) {
        ListPreference lp = findPreference(key);
        if (lp != null) {
            lp.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
