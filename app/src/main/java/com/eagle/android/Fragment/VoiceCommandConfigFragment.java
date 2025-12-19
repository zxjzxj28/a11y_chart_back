package com.eagle.android.Fragment;

import android.os.Bundle;
import android.text.InputFilter;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.eagle.android.R;

public class VoiceCommandConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName("a11y_prefs");
        requireActivity().setTitle(R.string.title_activity_voice_command_config);
        setPreferencesFromResource(R.xml.prefs_voice_command, rootKey);

        setupCommandPreference("voice_command_navigation", 30);
        setupCommandPreference("voice_command_playback", 40);
        setupCommandPreference("voice_command_exit", 20);
    }

    private void setupCommandPreference(String key, int maxLen) {
        EditTextPreference pref = findPreference(key);
        if (pref == null) return;

        pref.setOnBindEditTextListener(et -> {
            et.setHint("用、或逗号分隔多条口令");
            et.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(maxLen),
                    (source, start, end, dest, dstart, dend) -> {
                        // 去除换行并裁剪首尾空格，保留中间的顿号/逗号
                        String s = source.subSequence(start, end).toString();
                        return s.replace("\n", "").replace("\r", "");
                    }
            });
        });

        pref.setOnPreferenceChangeListener((p, v) -> {
            if (v == null) return false;
            String text = String.valueOf(v).trim();
            return !text.isEmpty();
        });

        pref.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
    }
}
