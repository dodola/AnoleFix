/*
 * Copyright (C) 2015 The Android Open Source Project
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


package dodola.anole.lib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.ColdswapMode;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Strings;

import java.util.Locale;

/**
 * Patching policy for delivering incremental code changes and triggering a cold start (application
 * restart).
 */
public enum InstantRunPatchingPolicy {

    /**
     * For Dalvik, a patch dex file will be generated with the incremental changes from the last
     * non incremental build or the last build that contained changes identified by the verifier as
     * incompatible.
     */
    PRE_LOLLIPOP,

    /**
     * For Lollipop and above, the application will be split in shards of dex files upon initial
     * build and packaged as a native multi dex application. Each incremental changes will trigger
     * rebuilding the affected shard dex files. Such dex files will be pushed on the device using
     * the embedded micro-server and installed by it.
     */
    MULTI_DEX,

    /**
     * For Lollipop and above, each shard dex file described above will be packaged in a single pure
     * split APK that will be pushed and installed on the device using adb install-multiple
     * commands.
     */
    MULTI_APK;

    /**
     * Returns the patching policy following the {@link AndroidProject#PROPERTY_BUILD_API} value
     * passed by Android Studio.
     * @param version the {@link AndroidVersion}
     * @param coldswapMode desired coldswap mode optionally provided.
     * @param targetArchitecture the targeted architecture.
     * @return a {@link InstantRunPatchingPolicy} instance.
     */
    @NonNull
    public static InstantRunPatchingPolicy getPatchingPolicy(
            @NonNull AndroidVersion version,
            @Nullable String coldswapMode,
            @Nullable String targetArchitecture) {

        if (version.compareTo(AndroidVersion.ART_RUNTIME) < 0) {
            return PRE_LOLLIPOP;
        } else {
            // whe dealing with Lollipop and above, by default, we use MULTI_DEX.
            InstantRunPatchingPolicy defaultModeForArchitecture = MULTI_DEX;

            if (Strings.isNullOrEmpty(coldswapMode)) {
                return defaultModeForArchitecture;
            }
            // coldswap mode was provided, it trumps everything
            ColdswapMode coldswap = ColdswapMode.valueOf(coldswapMode.toUpperCase(Locale.US));
            switch(coldswap) {
                case MULTIAPK: return MULTI_APK;
                case MULTIDEX: return MULTI_DEX;
                case AUTO:
                    if (version.getApiLevel() < 23) {
                        return MULTI_DEX;
                    } else {
                        return MULTI_APK;
                    }
                case DEFAULT:
                    return MULTI_DEX;
                default:
                    throw new RuntimeException("Cold-swap case not handled " + coldswap);
            }
        }
    }

}
