package com.eagle.android.Fragment;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.eagle.android.R;

public class VoiceCommandConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName("a11y_prefs");
        setPreferencesFromResource(R.xml.prefs_voice_command, rootKey);

        EditTextPreference phrase = findPreference("voice_trigger_phrase");
        if (phrase != null) {
            phrase.setOnBindEditTextListener(et -> {
                et.setHint("不超过 4 个字");
                // 限制“字符数”而非字节数；这里简单按 code unit 截断，够用
                et.setFilters(new InputFilter[]{
                        new InputFilter.LengthFilter(4),
                        (source, start, end, dest, dstart, dend) -> {
                            // 去除空白/换行（可选）
                            String s = source.subSequence(start, end).toString();
                            return s.replaceAll("\\s+", "");
                        }
                });
            });
            phrase.setOnPreferenceChangeListener((p, v) -> {
                if (v == null) return false;
                String s = String.valueOf(v).trim();
                // 再保险：长度校验 1~4
                return s.length() >= 1 && s.length() <= 4;
            });
            phrase.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
