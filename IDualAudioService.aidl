package com.example.dualaudiorouter;

interface IDualAudioService {
    // Required by Shizuku framework — must use this exact transaction code
    void destroy() = 16777114;

    // Set dual output: route USAGE_MEDIA to both given devices
    void setDualOutput(int speakerType, int btType) = 1;

    // Clear dual routing, revert to default
    void clearDualOutput() = 2;

    // Check if dual routing is currently active
    boolean isDualActive() = 3;
}
