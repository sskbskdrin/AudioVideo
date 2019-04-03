#include <jni.h>
#include <malloc.h>
#include "log.h"

#define TAG "native-jni"

int clamp(int v) {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
}

int color(char Y, char U, char V) {
    int y = 0xff & Y;
    int u = 0xff & U;
    int v = 0xff & V;
    int r = y + (int) (1.370705f * (v - 128));
    int g = y - (int) (0.698001f * (v - 128) + 0.337633f * (u - 128));
    int b = y + (int) (1.732446f * (u - 128));
    r = clamp(r);
    g = clamp(g);
    b = clamp(b);
    return 0xff000000 | r << 16 | g << 8 | b;
}

JNIEXPORT jintArray JNICALL
Java_cn_sskbskdrin_record_NativeUtils_nativeNV21toARGB(JNIEnv *env, jclass type, jbyteArray bytes_, jint width,
                                                       jint height) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytes_, NULL);

    int size = width * height;
    jint *pixels = (int *) malloc(size * sizeof(int));
    char y, u, v;
    int index;
    for (int h = 0; h < height; h++) {
        int line = width * (h / 2);
        for (int w = 0; w < width; w++) {
            index = (w % 2 == 0 ? w : w - 1) + line;

            y = bytes[width * h + w];
            u = bytes[size + index + 1];
            v = bytes[size + index];

            pixels[width * h + w] = color(y, u, v);
        }
    }
    (*env)->ReleaseByteArrayElements(env, bytes_, bytes, 0);
    jintArray array = (*env)->NewIntArray(env, size);
    (*env)->SetIntArrayRegion(env, array, 0, size, pixels);
    return array;
}


JNIEXPORT jintArray JNICALL
Java_cn_sskbskdrin_record_NativeUtils_nativeNV12toARGB(JNIEnv *env, jclass type, jbyteArray bytes_, jint width,
                                                       jint height) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytes_, NULL);

    int size = width * height;
    jint *pixels = (int *) malloc(size * sizeof(int));
    char y = 0, u = 0, v = 0;
    int index;
    for (int h = 0; h < height; h++) {
        int line = width * (h / 2);
        for (int w = 0; w < width; w++) {
            index = (w % 2 == 0 ? w : w - 1) + line;

            y = bytes[width * h + w];
            v = bytes[size + index + 1];
            u = bytes[size + index];

            pixels[width * h + w] = color(y, u, v);
        }
    }
    (*env)->ReleaseByteArrayElements(env, bytes_, bytes, 0);
    jintArray array = (*env)->NewIntArray(env, size);
    (*env)->SetIntArrayRegion(env, array, 0, size, pixels);
    return array;
}

JNIEXPORT jintArray JNICALL
Java_cn_sskbskdrin_record_NativeUtils_nativeYV12toARGB(JNIEnv *env, jclass type, jbyteArray bytes_, jint width,
                                                       jint height) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytes_, NULL);

    int size = width * height;
    int indexU = size + size / 4;
    jint *pixels = (int *) malloc(size * sizeof(int));
    char y = 0, u = 0, v = 0;
    int index;
    for (int h = 0; h < height; h++) {
        int line = (width / 2) * (h / 2);
        for (int w = 0; w < width; w++) {
            index = w / 2 + line;
            y = bytes[width * h + w];
            v = bytes[size + index];
            u = bytes[indexU + index];
            pixels[width * h + w] = color(y, u, v);
        }
    }
    (*env)->ReleaseByteArrayElements(env, bytes_, bytes, 0);
    jintArray array = (*env)->NewIntArray(env, size);
    (*env)->SetIntArrayRegion(env, array, 0, size, pixels);
    return array;
}

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
Java_cn_sskbskdrin_record_NativeUtils_nativeRotateNV(JNIEnv *env, jclass type, jbyteArray bytes_, jint width,
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