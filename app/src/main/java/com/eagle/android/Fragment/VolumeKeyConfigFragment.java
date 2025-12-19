package com.eagle.android.Fragment;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.eagle.android.R;

public class VolumeKeyConfigFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName("a11y_prefs");
        setPreferencesFromResource(R.xml.prefs_volume_key, rootKey);

        // 给“时间窗口 ms”做个输入限制（可选，简单校验）
        EditTextPreference win = findPreference("volume_combo_window_ms");
        if (win != null) {
            win.setOnBindEditTextListener(et -> et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER));
            win.setOnPreferenceChangeListener((p, v) -> {
                try {
                    int ms = Integer.parseInt(String.valueOf(v));
                    return ms >= 100 && ms <= 2000; // 100~2000ms
                } catch (Exception e) {
                    return false;
                }
            });
            win.setSummaryProvider(preference -> {
                String val = preference.getText();
                if (val == null || val.isEmpty()) return "未设置";
                return val + " 毫秒";
            });
        }

        // pattern 下拉（不做额外处理，这里只存值）
        ListPreference pattern = findPreference("volume_combo_pattern");
        if (pattern != null) {
            pattern.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        }
    }
}
