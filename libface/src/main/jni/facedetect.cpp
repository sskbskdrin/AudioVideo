#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sstream>
#include <FaceDetector/FaceDetector.h>
#include <FaceLandmarker/FaceLandmarker.h>
#include <Seeta/Struct.h>
#include <array>
#include <map>
#include <iostream>
#include <sys/time.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , "Seeta", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN , "Seeta", __VA_ARGS__)

extern "C"
JNIEXPORT jlong JNICALL
Java_cn_sskbskdrin_lib_face_FaceDetector_nativeInitDetection(JNIEnv *env, jclass clazz, jstring detect_model_file) {
    const char *detectModelFile = env->GetStringUTFChars(detect_model_file, 0);
    seeta::ModelSetting::Device device = seeta::ModelSetting::AUTO;

    int id = 0;
    seeta::ModelSetting FD_model(detectModelFile, device, id);

    seeta::FaceDetector *FD = new seeta::FaceDetector(FD_model);

    FD->set(seeta::FaceDetector::PROPERTY_VIDEO_STABLE, 1);
    FD->set(seeta::FaceDetector::PROPERTY_THRESHOLD1, 0.65f);

    env->ReleaseStringUTFChars(detect_model_file, detectModelFile);
    return (jlong) FD;
}

extern "C"
JNIEXPORT jint JNICALL
Java_cn_sskbskdrin_lib_face_FaceDetector_nativeDetection(JNIEnv *env, jclass clazz, jlong id, jbyteArray data_,
                                                         jint width, jint height, jintArray rect_, jint
                                                         max_face_count) {
    if (!id) {
        return 0;
    }
    seeta::FaceDetector *engine = (seeta::FaceDetector *) id;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(data_, NULL));
    int *rect = nullptr;
    if (rect_) {
        rect = env->GetIntArrayElements(rect_, NULL);
    }

    seeta::ImageData image(width, height, 3);
    image.data = src;

    SeetaFaceInfoArray faces = engine->detect(image);

    int rectIndex = 0;
    for (int i = 0; i < faces.size && i < max_face_count; ++i) {
        SeetaFaceInfo &face = faces.data[i];
        if (rect) {
            rect[rectIndex++] = face.pos.x;
            rect[rectIndex++] = face.pos.y;
            rect[rectIndex++] = face.pos.width;
            rect[rectIndex++] = face.pos.height;
        }
    }
    if (rect) {
        env->ReleaseIntArrayElements(rect_, rect, 0);
    }
    env->ReleaseByteArrayElements(data_, reinterpret_cast<jbyte *>(src), 0);
    return rectIndex / 4;
}

extern "C"
JNIEXPORT void JNICALL
Java_cn_sskbskdrin_lib_face_FaceDetector_nativeReleaseDetection(JNIEnv *env, jclass clazz, jlong id) {
    if (id) {
        delete (seeta::FaceDetector *) id;
    }
}
