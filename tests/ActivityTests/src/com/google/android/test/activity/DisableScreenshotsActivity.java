/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.google.android.test.activity;

import android.annotation.Nullable;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Activity for which screenshotting is disabled.
 */
public class DisableScreenshotsActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRecentsScreenshotEnabled(false);
        getWindow().getDecorView().setBackgroundColor(Color.RED);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // This is to simulate slowness over resuming the app, such that we have plenty of time to
        // see the starting window.
        SystemClock.sleep(500);
    }
}
