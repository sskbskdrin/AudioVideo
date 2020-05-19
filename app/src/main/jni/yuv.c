#include <jni.h>
#include <malloc.h>
#include "log.h"

#define TAG "native-yuv"

jbyte *rotate270(jbyte *data, jbyte *dst, int width, int height) {
    int size = width * height;
    int index = 0;
    for (int i = width - 1; i >= 0; i--) {
        for (int j = 0; j < height; j++) {
            dst[index++] = data[j * width + i];
        }
    }

    for (int i = width - 1; i >= 0; i -= 2) {
        for (int j = 0; j < height / 2; j++) {
            dst[index++] = data[size + j * width + i - 1];
            dst[index++] = data[size + j * width + i];
        }
    }
    LOGD(TAG, "rotate90 size=%d", index);
    return dst;
}

jbyte *rotate180(jbyte *data, jbyte *dst, int width, int height) {
    int size = width * height;
    int length = width * height * 3 / 2;
    int index = 0;
    for (int i = height - 1; i >= 0; i--) {
        for (int j = width - 1; j >= 0; j--) {
            dst[index++] = data[i * width + j];
        }
    }
    for (int i = length - 1; i >= size; i -= 2) {
        dst[index++] = data[i - 1];
        dst[index++] = data[i];
    }
    LOGD(TAG, "rotate180 size=%d", index);
    return dst;
}

jbyte *rotate90(jbyte *data, jbyte *dst, int width, int height) {
    int size = width * height;
    int index = 0;
    for (int i = 0; i < width; i++) {
        for (int j = height - 1; j >= 0; j--) {
            dst[index++] = data[j * width + i];
        }
    }

    for (int i = 0; i < width; i += 2) {
        for (int j = height / 2 - 1; j >= 0; j--) {
            dst[index++] = data[size + j * width + i];
            dst[index++] = data[size + j * width + i + 1];
        }
    }
    LOGD(TAG, "rotate270 size=%d", index);
    return dst;
}

JNIEXPORT jbyteArray JNICALL
Java_cn_sskbskdrin_record_YUV_nativeRotateNV(JNIEnv *env, jclass type, jbyteArray bytes_, jint width,
                                             jint height, jint degree) {
    if (degree == 0) {
        return bytes_;
    }
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytes_, NULL);
    int size = width * height * 3 / 2;
    jbyteArray array = (*env)->NewByteArray(env, size);
    jbyte *dst = (*env)->GetByteArrayElements(env, array, NULL);
    if (degree == 90) {
        rotate90(bytes, dst, width, height);
    } else if (degree == 180) {
        rotate180(bytes, dst, width, height);
    } else if (degree == 270) {
        rotate270(bytes, dst, width, height);
    } else {
        LOGW(TAG, "rotate fail, degree=%d", degree);
    }

    (*env)->ReleaseByteArrayElements(env, bytes_, bytes, 0);
    (*env)->SetByteArrayRegion(env, array, 0, size, dst);
    return array;
}

inline uint8_t clamp(int v) {
    return (uint8_t) (v < 0 ? 0 : (v > 255 ? 255 : v));
}

void color(uint8_t Y, uint8_t U, uint8_t V, uint8_t *dest) {
    int y = 0xff & Y;
    int u = (0xff & U) - 128;
    int v = (0xff & V) - 128;
    int y65535 = y << 16;// 0xffff=65535;
    dest[2] = clamp((y65535 + 89829 * v) / 65535);
    dest[1] = clamp((y65535 - 45743 * v - 22127 * u) / 65535);
    dest[0] = clamp((y65535 + 113535 * u) / 65535);
    dest[3] = 0xff;
}

void NV21ToArgb(uint8_t *src, uint8_t *dest, int width, int height, BOOL isNV12) {
    int index;

    for (int h = 0; h < height; h++) {
        int line = width * h;
        int uv = (h >> 1) * width;
        for (int w = 0; w < width; w++) {
            index = line + w;
            int uvIndex = uv + (h >> 1) * width + (w & 0xfffffffe);

            color(src[index], src[uvIndex + 1], src[uvIndex], dest + (index << 2));
        }
    }
}

void I420ToARGB(uint8_t *y, uint8_t *u, uint8_t *v, uint8_t *dest, int width, int height) {
    for (int i = 0; i < height; i++) {
        int line = i * width;
        int uvIndex = (i >> 1) * (width >> 1);
        for (int j = 0; j < width; j++) {
            int index = line + j;
            int uv = uvIndex + (j >> 1);
            color(y[index], u[uv], v[uv], dest + (index << 2));
        }
    }
}

void YV12ToArgb(uint8_t *src, uint8_t *dest, int width, int height) {
    int size = width * height;
    int indexU = size + (size >> 2);
    for (int h = 0; h < height; h++) {
        int line = h * width;
        int uvIndex = (h >> 1) * (width >> 1);
        for (int w = 0; w < width; w++) {
            int index = line + w;
            int uv = uvIndex + (w >> 1);
            color(src[index], src[size + uv], src[indexU + uv], dest + index);
        }
    }
}

inline void copyArgb(const uint8_t *src, uint8_t *dest) {
//    (uint32_t *) dest[0] = (uint32_t *) src[0];
    dest[0] = src[0];
    dest[1] = src[1];
    dest[2] = src[2];
    dest[3] = src[3];
}

void rotateArgb90(const uint8_t *src, uint8_t *dest, int srcWidth, int srcHeight) {
    int index = 0;
    for (int i = 0; i < srcWidth; i++) {
        for (int j = srcHeight - 1; j >= 0; j--) {
            copyArgb(src + (j * srcWidth + i) * 4, dest + index++ * 4);
        }
    }
}

void rotateArgb180(const uint8_t *src, uint8_t *dest, int srcWidth, int srcHeight) {
    int index = 0;
    for (int i = srcHeight - 1; i >= 0; i--) {
        for (int j = srcWidth - 1; j >= 0; j--) {
            copyArgb(src + (i * srcWidth + j) * 4, dest + index++ * 4);
        }
    }
}

void rotateArgb270(const uint8_t *src, uint8_t *dest, int srcWidth, int srcHeight) {
    int index = 0;
    for (int i = srcWidth - 1; i >= 0; i--) {
        for (int j = 0; j < srcHeight; j++) {
            copyArgb(src + (j * srcWidth + i) * 4, dest + index++ * 4);
        }
    }
}

#define NV21 ((uint32_t)'N'|((uint32_t)'V'<<8)|((uint32_t)'2'<<16)|((uint32_t)'1'<<24))
#define NV12 ((uint32_t)'N'|((uint32_t)'V'<<8)|((uint32_t)'1'<<16)|((uint32_t)'2'<<24))
#define YV12 ((uint32_t)'Y'|((uint32_t)'V'<<8)|((uint32_t)'1'<<16)|((uint32_t)'2'<<24))

JNIEXPORT void JNICALL
Java_cn_sskbskdrin_record_YUV_nativeToArgb(JNIEnv *env, jclass type, jbyteArray src_, jbyteArray dest_,
                                           jint width, jint height, jint format, jint rotate) {
    jbyte *src = (*env)->GetByteArrayElements(env, src_, NULL);
    jbyte *dest = (*env)->GetByteArrayElements(env, dest_, NULL);

    BOOL isRotate = rotate != 0;
    uint8_t *buff = NULL;
    if (isRotate) {
        buff = (uint8_t *) malloc((size_t) (width * height * 4));
    }

//    YV12ToArgb((uint8_t *) src, isRotate ? buff : (uint8_t *) dest, width, height);
    I420ToARGB(src, src + width * height * 5 / 4, src + width * height, dest, width, height);
    if (format == YV12) {
    } else {
//        NV21ToArgb((uint8_t *) src, isRotate ? buff : (uint8_t *) dest, width, height, format == NV12);
    }

    if (isRotate) {
        rotate %= 360;
        if (rotate == 90) {
            rotateArgb90(buff, (uint8_t *) dest, width, height);
        } else if (rotate == 180) {
            rotateArgb180(buff, (uint8_t *) dest, width, height);
        } else if (rotate == 270) {
            rotateArgb270(buff, (uint8_t *) dest, width, height);
        }
        free(buff);
    }

    (*env)->ReleaseByteArrayElements(env, src_, src, 0);
    (*env)->ReleaseByteArrayElements(env, dest_, dest, 0);
}