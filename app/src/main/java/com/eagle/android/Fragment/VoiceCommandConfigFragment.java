package com.eagle.android.Fragment;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.eagle.android.R;
import com.eagle.android.util.PreferenceListStyler;

public class VoiceCommandConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName("a11y_prefs");
        requireActivity().setTitle(R.string.title_activity_voice_command_config);
        setPreferencesFromResource(R.xml.prefs_voice_command, rootKey);

        setupCommandPreference("voice_command_prev_focus", 20);
        setupCommandPreference("voice_command_next_focus", 20);
        setupCommandPreference("voice_command_repeat", 20);
        setupCommandPreference("voice_command_summary", 20);
        setupCommandPreference("voice_command_auto", 20);
        setupCommandPreference("voice_command_exit", 20);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreferenceListStyler.apply(this);
    }

    private void setupCommandPreference(String key, int maxLen) {
        EditTextPreference pref = findPreference(key);
        if (pref == null) return;

        pref.setOnBindEditTextListener(et -> {
            et.setHint("请输入单个口令");
            et.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(maxLen),
                    (source, start, end, dest, dstart, dend) -> {
                        String s = source.subSequence(start, end).toString();
                        s = s.replace("\n", "").replace("\r", "");
                        s = s.replace("、", "").replace(",", "");
                        return s;
                    }
            });
        });

        pref.setOnPreferenceChangeListener((p, v) -> {
            if (v == null) return false;
            String text = String.valueOf(v).trim();
            return !text.isEmpty() && !text.contains("、") && !text.contains(",");
        });

        pref.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
    }
}
