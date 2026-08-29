/**
 * JNI surface for dev.vclient.core.runtime.NativeBridge (Kotlin object).
 *
 * Everything funnels through vc::Runtime, which keeps all writes inside the
 * isolated root passed to nativeInit(). See docs/ARCHITECTURE.md.
 */
#include <jni.h>

#include <android/log.h>
#include <cmath>
#include <cstring>
#include <string>

#include "vclient/Bridge.hpp"
#include "vclient/CrashHandler.hpp"
#include "vclient/FpsStats.hpp"
#include "vclient/Logger.hpp"
#include "vclient/MotionBlur.hpp"
#include "vclient/Runtime.hpp"
#include "vclient/WaypointMath.hpp"

#define LOG_TAG "VClient/Native"
#define ALOG(level, ...) __android_log_print(level, LOG_TAG, __VA_ARGS__)

using vc::Logger;
using vc::Runtime;

namespace {

MotionBlurModel g_motionBlur;

jstring toJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string toStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeInit(JNIEnv* env, jobject, jstring rootDir) {
    const std::string root = toStdString(env, rootDir);
    return Runtime::get().initialize(root) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeVersion(JNIEnv* env, jobject) {
    return toJavaString(env, Runtime::get().version());
}

JNIEXPORT void JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeLog(JNIEnv* env, jobject, jint level, jstring tag, jstring message) {
    if (!Runtime::get().initialized()) return;
    const std::string tagStr = toStdString(env, tag);
    const std::string msg = toStdString(env, message);
    const auto lvl = static_cast<Logger::Level>(level);
    Runtime::get().logger().write(lvl, tagStr, msg);
    const android_LogPriority priority = lvl == Logger::Level::Error   ? ANDROID_LOG_ERROR
                                       : lvl == Logger::Level::Warn    ? ANDROID_LOG_WARN
                                       : lvl == Logger::Level::Info    ? ANDROID_LOG_INFO
                                                                       : ANDROID_LOG_DEBUG;
    ALOG(priority, "[%s] %s", tagStr.c_str(), msg.c_str());
}

JNIEXPORT jboolean JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeInstallCrashGuard(JNIEnv* env, jobject) {
    if (!Runtime::get().initialized()) return JNI_FALSE;
    // The guard is installed during initialize(); report its presence.
    return JNI_TRUE;
}

JNIEXPORT jdoubleArray JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeFrame(JNIEnv* env, jobject, jdouble dtMs) {
    auto& runtime = Runtime::get();
    if (!runtime.initialized()) return nullptr;

    const int64_t nowMs = static_cast<int64_t>(::clock() / (CLOCKS_PER_SEC / 1000));
    runtime.fps().push(dtMs, nowMs);

    double snapshot[5] = {};
    runtime.fps().snapshot(snapshot);

    jdoubleArray out = env->NewDoubleArray(5);
    if (out == nullptr) return nullptr;
    env->SetDoubleArrayRegion(out, 0, 5, snapshot);
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeWaypoint(
    JNIEnv* env, jobject,
    jdouble px, jdouble py, jdouble pz,
    jdouble yawDeg, jdouble pitchDeg,
    jdouble tx, jdouble ty, jdouble tz,
    jdouble fovHDeg, jdouble screenW, jdouble screenH) {

    const vc::WaypointResult r = vc::projectWaypoint(
        px, py, pz, yawDeg, pitchDeg, tx, ty, tz, fovHDeg, screenW, screenH);

    const jfloat values[5] = {
        r.onScreen ? 1.f : 0.f,
        r.screenX,
        r.screenY,
        static_cast<jfloat>(r.distance),
        r.bearingDeg,
    };
    jfloatArray out = env->NewFloatArray(5);
    if (out == nullptr) return nullptr;
    env->SetFloatArrayRegion(out, 0, 5, values);
    return out;
}

JNIEXPORT void JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeMotionBlurConfigure(JNIEnv*, jobject, jfloat sensitivity, jfloat maxRadiusDp) {
    g_motionBlur.configure(sensitivity, maxRadiusDp);
}

JNIEXPORT jfloat JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeMotionBlurSample(JNIEnv*, jobject, jdouble gyroX, jdouble gyroY, jdouble dtMs) {
    return g_motionBlur.push(gyroX, gyroY, dtMs);
}

JNIEXPORT jstring JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeBridgeStatus(JNIEnv* env, jobject) {
    return toJavaString(env, vc::BridgeRuntime::status().detail);
}

JNIEXPORT void JNICALL
Java_dev_vclient_core_runtime_NativeBridge_nativeShutdown(JNIEnv*, jobject) {
    g_motionBlur.reset();
    Runtime::get().shutdown();
}

} // extern "C"
