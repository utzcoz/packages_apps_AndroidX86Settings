package com.androidx86.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;

public class AndroidX86AboutSettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.about_preference, rootKey);
    }
}
