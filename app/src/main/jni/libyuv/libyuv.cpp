//
// Created by sskbskdrin on 2019/April/9.
//

#include "include/libyuv.h"

#include <jni.h>
#include <string>

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_record_YUVLib_nativeToArgb(JNIEnv *env, jclass type, jbyteArray bytes_, jbyteArray dest_, jint width,
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
    memcpy(buf,dest,width*height*4);
    if (needBuff) {
        int w = rotate % 180 == 0 ? width : height;
        libyuv::ARGBRotate(buf, width * 4, reinterpret_cast<uint8_t *>(dest), w * 4,
                           width, height, rotationMode);
        free(buf);
    }

    env->ReleaseByteArrayElements(bytes_, bytes, 0);
    env->ReleaseByteArrayElements(dest_, dest, 0);
}