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

void toRGBA(uint8_t *y, uint8_t *u, uint8_t *v, uint8_t *dest, uint8_t *cache, int *clip, int width, int height, int
rotate, bool m) {
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
        clip[2] = w;
        clip[3] = h;
        uint8_t *dy = d;
        uint8_t *du = dy + clip[2] * clip[3];
        uint8_t *dv = dy + clip[2] * clip[3] * 3 / 2;

        int uvw = (clip[2] + 1) >> 1;
        int offset = clip[0] + clip[1] * width;
        libyuv::I420Copy(y + offset, width, u + clip[0] / 2 + clip[1] * width / 4, (width + 1) >> 1,
                         v + clip[0] / 2 + clip[1] * width / 4,
                         (width + 1) >> 1, dy, clip[2], du, uvw, dv, uvw, w, h);
        width = clip[2];
        height = clip[3];
        y = dy;
        u = du;
        v = dv;
        swapP(s, d);
    }

    //[r,g,b,a]
    libyuv::I420ToARGB(y, width, u, width / 2, v, width / 2, d, width * 4, width, height);
//    libyuv::Android420ToARGB(y, width, u, width / 2, v, width / 2, 1, d, width * 4, width, height);
    swapP(s, d);

    if (rotate != 0) {
        int w = rotate % 180 == 0 ? width : height;
        libyuv::ARGBRotate(s, width * 4, d, w * 4, width, height, rotationMode);
        height = height == w ? width : height;
        width = w;
        swapP(s, d);
    }
    if (m) {
        libyuv::ARGBMirror(s, width * 4, d, width * 4, width, height);
        swapP(s, d);
    }
    if (s != dest) {
        libyuv::CopyPlane(s, width * height * 4, d, width * height * 4, width * height * 4, 1);
    }
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
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(src_, NULL));
    uint8_t *dest = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(dest_, NULL));
    uint8_t *cache = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(cache_, NULL));

    uint8_t *s = dest;
    uint8_t *d = cache;

    uint8_t *y = src;
    uint8_t *v = nullptr;
    uint8_t *u = nullptr;
    LOGD("libyuv", "src=%ld dest=%ld cache=%ld", (unsigned long) src, (unsigned long) dest, (unsigned long) cache);
    LOGD("libyuv", "  y=%ld    s=%ld     d=%ld", (unsigned long) y, (unsigned long) s, (unsigned long) d);
    LOGD("libyuv", " format=%d", format);

    int src_stride_uv = width;
    int src_pixel_stride_uv = 2;
    if (format == libyuv::FOURCC_NV21) {
        v = y + width * height;
        u = v + 1;
    } else if (format == libyuv::FOURCC_NV12) {
        u = y + width * height;
        v = u + 1;
    } else if (format == libyuv::FOURCC_YV12) { //YV12
        src_stride_uv = width / 2;
        src_pixel_stride_uv = 1;
        v = y + width * height;
        u = v + width * height / 4;
    } else if (format == libyuv::FOURCC_I420) {
        src_stride_uv = width / 2;
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
//            swapP(du, dv);
        }
        libyuv::SplitUVPlane(y + width * height, width, du, width / 2, dv, width / 2, width, height / 2);
//        libyuv::SplitUVPlane(y + width * height, len << 1, du, len, dv, len, len << 1, 1);
        LOGD("libyuv", "split uv len=%d", len);
        uint8_t *sy = src;
        uint8_t *su = sy + width * height;
        uint8_t *sv = sy + width * height + len;
//        libyuv::CopyPlane(du, width/2, su, width/2, width/2, height/2);
//        libyuv::CopyPlane(dv, width/2, sv, width/2, width/2, height/2);
        libyuv::CopyPlane(du, len, su, len, len, 1);
        libyuv::CopyPlane(dv, len, sv, len, len, 1);
        LOGD("libyuv", "copy");
        y = sy;
        u = su;
        v = sv;
        LOGD("libyuv", "to argb  y=%ld    u=%ld     v=%ld", (unsigned long) y, (unsigned long) u, (unsigned long) v);
//        libyuv::Android420ToABGR(y, width, u, src_stride_uv, v, src_stride_uv, src_pixel_stride_uv,
//                                 dest, width * 4, width, height);
//        libyuv::Android420ToABGR(y, width, u, width / 2, v, width / 2, 1, dest, width * 4, width, height);
//        libyuv::I420ToARGB(y, width, u, width / 2, v, width / 2, dest, width * 4, width, height);
    }

    toRGBA(y, u, v, dest, cache, clip, width, height, rotate, m);
//    env->ReleaseByteArrayElements(src_, (jbyte *) src, 0);
//    env->ReleaseByteArrayElements(dest_, (jbyte *) dest, 0);
//    env->ReleaseByteArrayElements(cache_, (jbyte *) cache, 0);
//    if (clip)
//        env->ReleaseIntArrayElements(clip_, clip, 0);
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