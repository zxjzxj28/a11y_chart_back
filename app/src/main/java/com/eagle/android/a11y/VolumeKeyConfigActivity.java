package com.eagle.android.a11y;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.eagle.android.Fragment.VolumeKeyConfigFragment;

public class VolumeKeyConfigActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new VolumeKeyConfigFragment())
                .commit();
    }
}
