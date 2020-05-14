//
// Created by sskbskdrin on 2019/April/9.
//

#include "libyuv.h"
#include "log.h"

#include <string.h>
#include <jni.h>

inline void swapP(uint8_t *&a, uint8_t *&b) {
    uint8_t *t = a;
    a = b;
    b = t;
}

inline void setP(uint8_t *&s, uint8_t *&y, uint8_t *&u, uint8_t *&v, int w, int h) {
    y = s;
    u = y + w * h;
    v = y + w * h * 5 / 4;
}

void toRGBA(uint8_t *y, uint8_t *u, uint8_t *v, uint8_t *dest, uint8_t *cache, int *clip, int width, int height, int
rotate, bool mirror, bool hasAlpha, bool NV21 = false) {
    libyuv::RotationMode rotationMode = libyuv::RotationMode::kRotate0;
    rotate = (rotate % 360 + 360) % 360;
    if (rotate == 90) {
        rotationMode = libyuv::RotationMode::kRotate90;
    } else if (rotate == 180) {
        rotationMode = libyuv::RotationMode::kRotate180;
    } else if (rotate == 270) {
        rotationMode = libyuv::RotationMode::kRotate270;
    } else {
        rotate = 0;
    }
    uint8_t *s = cache;
    uint8_t *d = dest;
    if (clip) {
        if (rotate == 90) {
            int x = clip[1];
            clip[1] = height - clip[2] - clip[0];
            clip[0] = x;
        }
        if (rotate == 180) {
            clip[0] = width - clip[0] - clip[2];
            clip[1] = height - clip[1] - clip[3];
        }
        if (rotate == 270) {
            int x = clip[0];
            clip[0] = width - clip[3] - clip[1];
            clip[1] = x;
        }
        LOGD("libyuv", "rotate=%d %d,%d", rotate, clip[0], clip[1]);
        int w = clip[2];
        int h = clip[3];
        if (rotate % 180) {
            h = clip[2];
            w = clip[3];
        }

        uint8_t *dy = d;
        uint8_t *du = dy + w * h;
        uint8_t *dv = du + w * h / 4;

        y += clip[0] + clip[1] * width;
        u += clip[0] / 2 + clip[1] * width / 4;
        v += clip[0] / 2 + clip[1] * width / 4;

        int uvw = (w + 1) >> 1;
        libyuv::I420Copy(y, width, u, (width + 1) >> 1, v, (width + 1) >> 1,
                         dy, w, du, uvw, dv, uvw, w, h);
        LOGD("libyuv", "I420Copy");
        width = w;
        height = h;
        swapP(s, d);
        setP(s, y, u, v, width, height);
    }

    uint8_t *dy = d;
    uint8_t *du = d;
    uint8_t *dv = d;
    setP(d, dy, du, dv, width, height);
    if (rotate) {
        int w = rotate % 180 == 0 ? width : height;
        libyuv::I420Rotate(y, width, u, width / 2, v, width / 2, dy, w, du, w / 2, dv, w / 2, width, height,
                           rotationMode);
        height = height == w ? width : height;
        width = w;
        swapP(s, d);
        setP(s, y, u, v, width, height);
        setP(d, dy, du, dv, width, height);
    }

    if (mirror) {
        int w = width >> 1;
        libyuv::I420Mirror(y, width, u, w, v, w, dy, width, du, w, dv, w, width, height);
        swapP(s, d);
        setP(s, y, u, v, width, height);
        setP(d, dy, du, dv, width, height);
    }

    if (hasAlpha) {
        if (NV21) {
            libyuv::I420ToARGB(y, width, u, width / 2, v, width / 2, dy, width * 4, width, height);
        } else {
            libyuv::I420ToABGR(y, width, u, width / 2, v, width / 2, dy, width * 4, width, height);
        }
    } else {
        libyuv::I420ToRGB24(y, width, u, width / 2, v, width / 2, dy, width * 3, width, height);
    }

    if (dy != dest) {
        int mul = hasAlpha ? 4 : 3;
        libyuv::CopyPlane(dy, width * height * mul, dest, width * height * mul, width * height * mul, 1);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_yuv_YUVLib_nativeByteToRGBA(JNIEnv *env, jclass type, jbyteArray src_, jbyteArray dest_,
                                                   jbyteArray cache_, jintArray clip_, jint width, jint height,
                                                   jint format, jint rotate, jboolean m,
                                                   jboolean hasAlpha) {
    if (!src_ || !dest_) {
        return;
    }
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_, NULL));
    uint8_t *dest = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(dest_, NULL));
    uint8_t *cache;
    if (cache_) {
        cache = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(cache_, NULL));
    } else {
        cache = new uint8_t[width * height * 4];
    }

    uint8_t *y = src;
    uint8_t *v = nullptr;
    uint8_t *u = nullptr;
    int src_pixel_stride_uv = 2;
    if (format == libyuv::FOURCC_NV21) {
        v = y + width * height;
        u = v + 1;
    } else if (format == libyuv::FOURCC_NV12) {
        u = y + width * height;
        v = u + 1;
    } else if (format == libyuv::FOURCC_YV12) { //YV12
        src_pixel_stride_uv = 1;
        v = y + width * height;
        u = v + width * height / 4;
    } else if (format == libyuv::FOURCC_I420) {
        src_pixel_stride_uv = 1;
        u = y + width * height;
        v = u + width * height / 4;
    } else {
        return;
    }

    int *clip = nullptr;
    if (clip_) {
        clip = env->GetIntArrayElements(clip_, NULL);
    }
    if (src_pixel_stride_uv == 2) {
        int len = width * height / 4;
        uint8_t *du = dest + width * height;
        uint8_t *dv = dest + width * height + len;
        if (u - v < 0) {
            swapP(du, dv);
        }
        libyuv::SplitUVPlane(y + width * height, 0, du, 0, dv, 0, len, 1);
        LOGD("libyuv", "split");
        uint8_t *sy = src;
        uint8_t *su = sy + width * height;
        uint8_t *sv = sy + width * height + len;
        libyuv::CopyPlane(du, 0, su, 0, len, 1);
        libyuv::CopyPlane(dv, 0, sv, 0, len, 1);
        y = sy;
        u = su;
        v = sv;
    }

    toRGBA(y, u, v, dest, cache, clip, width, height, rotate, m, hasAlpha, src_pixel_stride_uv == 2);

    env->ReleaseByteArrayElements(src_, (jbyte *) src, 0);
    env->ReleaseByteArrayElements(dest_, (jbyte *) dest, 0);
    if (cache_) {
        env->ReleaseByteArrayElements(cache_, (jbyte *) cache, 0);
    } else {
        free(cache);
    }
    if (clip)
        env->ReleaseIntArrayElements(clip_, clip, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_yuv_YUVLib_nativeYUVToRGBA(JNIEnv *env, jclass clazz, jbyteArray src_y, jbyteArray src_u,
                                                  jbyteArray src_v, jbyteArray dest_, jbyteArray cache_,
                                                  jintArray clip_,
                                                  jint width, jint height, jint format, jint rotate, jboolean mirror,
                                                  jboolean hasAlpha) {
    uint8_t *srcY = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_y, NULL));
    uint8_t *srcU = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_u, NULL));
    uint8_t *srcV = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_v, NULL));
    uint8_t *dest = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(dest_, NULL));
    uint8_t *cache = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(cache_, NULL));

    int *clip = nullptr;
    if (clip_) {
        clip = env->GetIntArrayElements(clip_, NULL);
    }

    toRGBA(srcY, srcU, srcV, dest, cache, clip, width, height, rotate, mirror, hasAlpha);

    env->ReleaseByteArrayElements(src_y, (jbyte *) srcY, 0);
    env->ReleaseByteArrayElements(src_u, (jbyte *) srcU, 0);
    env->ReleaseByteArrayElements(src_v, (jbyte *) srcV, 0);
    env->ReleaseByteArrayElements(dest_, (jbyte *) dest, 0);
    env->ReleaseByteArrayElements(cache_, (jbyte *) cache, 0);

    if (clip)
        env->ReleaseIntArrayElements(clip_, clip, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_yuv_YUVLib_nativeSplitRGBA(JNIEnv *env, jclass clazz, jbyteArray rgba_, jbyteArray r_,
                                                  jbyteArray g_, jbyteArray b_, jbyteArray a_, jint size, jboolean
                                                  has_alpha) {
    uint8_t *rgba = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(rgba_, NULL));
    uint8_t *r = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(r_, NULL));
    uint8_t *g = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(g_, NULL));
    uint8_t *b = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(b_, NULL));
    uint8_t *a = nullptr;
    if (a_) {
        a = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(a_, NULL));
    }
    if (has_alpha) {
        if (a)
            libyuv::ARGBExtractAlpha(rgba, size, a, size / 4, size / 4, 1);
        libyuv::SplitRGBPlane(rgba, 4, r, 1, g, 1, b, 1, 1, size / 4);
    } else {
        libyuv::SplitRGBPlane(rgba, size, r, 1, g, 1, b, 1, size / 3, 1);
        if (a) {
            libyuv::SetPlane(a, size / 3, size / 3, 1, 255);
        }
    }

    env->ReleaseByteArrayElements(rgba_, (jbyte *) rgba, 0);
    env->ReleaseByteArrayElements(r_, (jbyte *) r, 0);
    env->ReleaseByteArrayElements(g_, (jbyte *) g, 0);
    env->ReleaseByteArrayElements(b_, (jbyte *) b, 0);
    if (a)
        env->ReleaseByteArrayElements(a_, (jbyte *) a, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_yuv_YUVLib_nativeScaleRGBA(JNIEnv *env, jclass clazz, jbyteArray src_, jint width, jint height,
                                                  jbyteArray dest_, jint d_width, jint d_height, jint quality) {
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_, NULL));
    uint8_t *dest = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(dest_, NULL));
    libyuv::FilterMode mode;
    if (quality == 1) {
        mode = libyuv::FilterMode::kFilterLinear;
    } else if (quality == 2) {
        mode = libyuv::FilterMode::kFilterBilinear;
    } else if (quality == 3) {
        mode = libyuv::FilterMode::kFilterBox;
    } else {
        mode = libyuv::FilterMode::kFilterNone;
    }
    libyuv::ARGBScale(src, width * 4, width, height, dest, d_width * 4, d_width, d_height, mode);
    env->ReleaseByteArrayElements(src_, (jbyte *) src, 0);
    env->ReleaseByteArrayElements(dest_, (jbyte *) dest, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_yuv_YUVLib_nativeBGRAToColor(JNIEnv *env, jclass clazz, jbyteArray src_, jintArray colors_, jint
size) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jint *dest = env->GetIntArrayElements(colors_, NULL);
    libyuv::ARGBCopy(reinterpret_cast<const uint8_t *>(src), size, reinterpret_cast<uint8_t *>(dest), size, size, 1);
    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseIntArrayElements(colors_, dest, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_yuv_YUVLib_nativeRGBToRGBA(JNIEnv *env, jclass clazz, jbyteArray rgb_, jbyteArray rgba_, jint
width, jboolean reverse) {
    uint8_t *rgb = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(rgb_, NULL));
    uint8_t *rgba = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(rgba_, NULL));
    if (reverse) {
        libyuv::ARGBToRGB24(rgba, width * 4, rgb, width * 3, width, 1);
    } else {
        libyuv::RGB24ToARGB(rgb, width * 3, rgba, width * 4, width, 1);
    }
    env->ReleaseByteArrayElements(rgb_, (jbyte *) rgb, 0);
    env->ReleaseByteArrayElements(rgba_, (jbyte *) rgba, 0);
}