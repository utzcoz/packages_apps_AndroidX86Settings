/*
 * Copyright (C) 2020 The Android-x86 Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidx86.settings;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.io.File;

import dalvik.system.VMRuntime;

public class AndroidX86OptionsSettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "Dl-NB";

    private static final String PROPERTY_NATIVEBRIDGE = "persist.sys.nativebridge";
    private static final String PROPERTY_HW_INFO = "persist.sys.hw_statistics";
    private static final String PROPERTY_APPS_USAGE = "persist.sys.apps_statistics";
    private static final String NB_LIBRARIES = "Native bridge libraries ";
    private static long sDownloadId = -1;
    private static int sDownloadStatus = -1;

    private DownloadManager mDownloadManager;
    private SwitchPreferenceCompat mNativeBridgePreference;
    private SwitchPreferenceCompat mHwInfoPreference;
    private SwitchPreferenceCompat mAppsUsagePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.options_preference, rootKey);

        if (getContext() != null) {
            mDownloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        }

        mNativeBridgePreference =
                (SwitchPreferenceCompat) findPreference(
                        getString(R.string.key_android_x86_enable_native_bridge)
                );
        mHwInfoPreference =
                (SwitchPreferenceCompat) findPreference(
                        getString(R.string.key_android_x86_collect_anonymous_hw_info)
                );
        mAppsUsagePreference =
                (SwitchPreferenceCompat) findPreference(
                        getString(R.string.key_android_x86_collect_anonymous_app_usage)
                );

        checkNativeBridgeStatus();
        mHwInfoPreference.setChecked(SystemProperties.getBoolean(PROPERTY_HW_INFO, true));
        mAppsUsagePreference.setChecked(SystemProperties.getBoolean(PROPERTY_APPS_USAGE, false));
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mNativeBridgePreference) {
            setNativeBridge(mNativeBridgePreference.isChecked());
        } else if (preference == mHwInfoPreference) {
            SystemProperties.set(PROPERTY_HW_INFO, Boolean.toString(mHwInfoPreference.isChecked()));
        } else if (preference == mAppsUsagePreference) {
            SystemProperties.set(PROPERTY_APPS_USAGE, Boolean.toString(mAppsUsagePreference.isChecked()));
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean isNativeBridgeAvailable() {
        File file = new File("/system/lib/libhoudini.so");
        return file.length() > 0;
    }

    private void checkNativeBridgeStatus() {
        boolean nb = SystemProperties.getBoolean(PROPERTY_NATIVEBRIDGE, false);
        if (!isNativeBridgeAvailable()) {
            queryDownloading(mDownloadManager);
            if (sDownloadStatus == DownloadManager.STATUS_RUNNING) {
                nb = true;
            } else if (nb) {
                downloadNativeBridge();
            }
        }
        mNativeBridgePreference.setChecked(nb);
    }

    private void setNativeBridge(boolean enabled) {
        Log.d(TAG, "setNativeBridge: " + enabled);
        if (isNativeBridgeAvailable()) {
            setNativeBridgeProperty(enabled);
        } else if (enabled) {
            downloadNativeBridge();
        } else if (sDownloadId != -1) {
            mDownloadManager.remove(sDownloadId);
            sDownloadId = -1;
        }
    }

    private void downloadNativeBridge() {
        String url, file;
        if (VMRuntime.getRuntime().is64Bit()) {
            url = "https://t.cn/EJrmYMH";
            file = "houdini9_y.sfs";
        } else {
            url = "https://t.cn/EJrmzZv";
            file = "houdini9_x.sfs";
        }

        File path = Environment.getExternalStoragePublicDirectory("arm");
        path.mkdirs();
        File nb = new File(path, file);
        if (nb.exists()) {
            if (sDownloadId != -1) {
                switch (sDownloadStatus) {
                    case DownloadManager.STATUS_SUCCESSFUL:
                        setNativeBridgeProperty(true); // fall through
                    case DownloadManager.STATUS_RUNNING:
                        return;
                    default:
                        break;
                }
            }
            nb.delete();
        }
        mDownloadManager.remove(sDownloadId);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url)).
                setDestinationUri(Uri.fromFile(nb)).
                setTitle(NB_LIBRARIES + file.substring(7, 10));

        sDownloadId = mDownloadManager.enqueue(request);
        Log.i(TAG, "downloading " + url);
    }

    private static void queryDownloading(DownloadManager dm) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterByString(NB_LIBRARIES);
        Cursor c = null;
        try {
            c = dm.query(query);
            if (c.moveToFirst()) {
                sDownloadId = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_ID));
                sDownloadStatus = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                Log.i(TAG, "id: " + sDownloadId + " status: " + sDownloadStatus);
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception: " + e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private static void setNativeBridgeProperty(boolean enabled) {
        SystemProperties.set(PROPERTY_NATIVEBRIDGE, enabled ? "1" : "0");
    }

    public static void onDownloadComplete(DownloadManager dm) {
        queryDownloading(dm);
        boolean success = sDownloadStatus == DownloadManager.STATUS_SUCCESSFUL;
        if (success) {
            Log.i(TAG, "download success, native bridge enabled");
        } else {
            Log.w(TAG, "download failed: " + sDownloadStatus);
        }
        setNativeBridgeProperty(success);
    }
}
