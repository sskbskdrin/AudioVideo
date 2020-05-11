//
// Created by sskbskdrin on 2019/April/9.
//

#include "libyuv.h"
#include "../log.h"

#include <string.h>
#include <jni.h>

inline void swapP(uint8_t *&a, uint8_t *&b) {
    uint8_t *t = a;
    a = b;
    b = t;
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_record_YUVLib_nToArgb(JNIEnv *env, jclass type, jbyteArray bytes_, jbyteArray dest_, jint width,
                                         jint height, jint format, jint rotate) {
    jbyte *bytes = env->GetByteArrayElements(bytes_, NULL);
    jbyte *dest = env->GetByteArrayElements(dest_, NULL);

    libyuv::RotationMode rotationMode = libyuv::RotationMode::kRotate0;
    if (rotate == 90) {
        rotationMode = libyuv::RotationMode::kRotate90;
    } else if (rotate == 180) {
        rotationMode = libyuv::RotationMode::kRotate180;
    } else if (rotate == 270) {
        rotationMode = libyuv::RotationMode::kRotate270;
    }
    LIBYUV_BOOL needBuff = rotationMode != libyuv::RotationMode::kRotate0;
    uint8_t *buf = nullptr;
    if (needBuff) {
        buf = static_cast<uint8_t *>(malloc(static_cast<size_t>(width * height * 4)));
    }

    uint8_t *y = reinterpret_cast<uint8_t *>(bytes);
    uint8_t *v = nullptr;
    uint8_t *u = nullptr;
    int src_stride_uv = width;
    int src_pixel_stride_uv = 2;
    if (format == libyuv::FOURCC_NV21) {
        v = y + width * height;
        u = v + 1;
    } else if (format == libyuv::FOURCC_NV12) {
        v = y + width * height + 1;
        u = v - 1;
    } else { //YV12
        src_stride_uv = width / 2;
        src_pixel_stride_uv = 1;
        v = y + width * height;
        u = v + width * height / 4;
    }

    libyuv::Android420ToABGR(y, width, u, src_stride_uv, v, src_stride_uv, src_pixel_stride_uv,
                             needBuff ? buf : reinterpret_cast<uint8_t *>(dest),
                             width * 4, width, height);
    libyuv::ARGBMirror(reinterpret_cast<const uint8_t *>(buf), width * 4, reinterpret_cast<uint8_t *>(dest), width * 4,
                       width, height);
    memcpy(buf, dest, width * height * 4);
    if (needBuff) {
        int w = rotate % 180 == 0 ? width : height;
        libyuv::ARGBRotate(buf, width * 4, reinterpret_cast<uint8_t *>(dest), w * 4,
                           width, height, rotationMode);
        free(buf);
    }

    env->ReleaseByteArrayElements(bytes_, bytes, 0);
    env->ReleaseByteArrayElements(dest_, dest, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_record_YUVLib_nativeByteToBGRA(JNIEnv *env, jclass type, jbyteArray src_, jbyteArray dest_,
                                                  jbyteArray
                                                  cache_, jintArray clip_, jint width, jint height, jint format,
                                                  jint rotate, jboolean m) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    uint8_t *dest = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(dest_, NULL));
    uint8_t *cache = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(cache_, NULL));

    int *clip = nullptr;
    if (clip_) {
        clip = env->GetIntArrayElements(clip_, NULL);
    }

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

    uint8_t *y = reinterpret_cast<uint8_t *>(src);
    uint8_t *v = nullptr;
    uint8_t *u = nullptr;
    int src_stride_uv = width;
    int src_pixel_stride_uv = 2;
    if (format == libyuv::FOURCC_NV21) {
        v = y + width * height;
        u = v + 1;
    } else if (format == libyuv::FOURCC_NV12) {
        u = y + width * height;
        v = u + 1;
    } else { //YV12
        src_stride_uv = width / 2;
        src_pixel_stride_uv = 1;
        v = y + width * height;
        u = v + width * height / 4;
    }
    // [b,g,r,a]
    libyuv::Android420ToABGR(y, width, u, src_stride_uv, v, src_stride_uv, src_pixel_stride_uv,
                             dest, width * 4, width, height);
//    libyuv::NV21ToARGB(y,width,u,src_stride_uv,dest,width*4,width,height);
    // [r,g,b,a]
//    libyuv::Android420ToARGB(y, width, u, src_stride_uv, v, src_stride_uv, src_pixel_stride_uv,
//                             dest, width * 4, width, height);
    uint8_t *s = dest;
    uint8_t *d = cache;
    if (m) {
        libyuv::ARGBMirror(s, width * 4, d, width * 4, width, height);
        swapP(s, d);
    }
    if (rotate != 0) {
        int w = rotate % 180 == 0 ? width : height;
        libyuv::ARGBRotate(s, width * 4, d, w * 4, width, height, rotationMode);
        height = height == w ? width : height;
        width = w;
        swapP(s, d);
    }
    if (clip) {
        libyuv::CopyPlane(s + clip[0] + clip[1] * width * 4, width * 4, d, clip[2] * 4, width * 4, clip[3]);
        width = clip[2];
        height = clip[3];
        swapP(s, d);
    }
    if (s != dest) {
        libyuv::CopyPlane(s, width * height * 4, d, width * height * 4, width * height * 4, 1);
    }
    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseByteArrayElements(dest_, (jbyte *) dest, 0);
    env->ReleaseByteArrayElements(cache_, (jbyte *) cache, 0);
    if (clip)
        env->ReleaseIntArrayElements(clip_, clip, 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_record_YUVLib_nativeBGRAToColor(JNIEnv *env, jclass clazz, jbyteArray src_, jintArray colors_, jint
size) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jint *dest = env->GetIntArrayElements(colors_, NULL);
    size *= 4;
    uint32_t temp = 0xff;
    for (int i = 0; i < size; i += 4) {
        uint32_t color = (temp & src[i]) << 16;// B
        color |= (temp & src[i + 1]) << 8;// G
        color |= (temp & src[i + 2]);// R
        color |= (temp & src[i + 3]) << 24;// A
        dest[i / 4] = color;
    }
    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseIntArrayElements(colors_, dest, 0);
}