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

        ListPreference lp = findPreference("a11y_gesture_choice");
        if (lp != null) {
            lp.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
