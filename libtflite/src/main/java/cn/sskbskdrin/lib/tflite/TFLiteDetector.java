package cn.sskbskdrin.lib.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by keayuan on 2020/5/14.
 *
 * @author keayuan
 */
public class TFLiteDetector {
    private static final String TAG = "TFLiteDetector";

    // Only return this many results.
    private static final int NUM_DETECTIONS = 10;
    // Float model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    private Interpreter tfLite;
    private String[] labels;

    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;

    private ByteBuffer imgData;
    private boolean isModelQuantized;

    private TFLiteDetector() {}

    /**
     * Memory-map the model file in Assets.
     */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static TFLiteDetector create(final InputStream modelInput, final InputStream labelInput) throws IOException {
        final TFLiteDetector detector = new TFLiteDetector();
        BufferedReader br = new BufferedReader(new InputStreamReader(labelInput));
        String line;
        List<String> list = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            list.add(line);
        }
        detector.labels = list.toArray(new String[0]);
        br.close();

        byte[] buff = new byte[modelInput.available()];
        modelInput.read(buff);
        modelInput.close();

        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            detector.tfLite = new Interpreter(ByteBuffer.allocateDirect(buff.length).put(buff), options);
            int count = detector.tfLite.getInputTensorCount();
            Log.d(TAG, "create: tensor count=" + count);
            Tensor tensor = detector.tfLite.getInputTensor(0);
            //            detector.tfLite.resizeInput(0, new int[]{1, 480, 480, 3});
            StringBuilder builder = new StringBuilder();
            builder.append("input").append('\n');
            tensorToString(builder, tensor);
            builder.append("output").append('\n');
            count = detector.tfLite.getOutputTensorCount();
            for (int i = 0; i < count; i++) {
                tensor = detector.tfLite.getOutputTensor(i);
                tensorToString(builder, tensor);
                builder.append('\n');
            }
            Log.d(TAG, "create: " + builder.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        detector.outputLocations = new float[1][NUM_DETECTIONS][4];
        detector.outputClasses = new float[1][NUM_DETECTIONS];
        detector.outputScores = new float[1][NUM_DETECTIONS];
        detector.numDetections = new float[1];
        return detector;
    }

    private static void tensorToString(StringBuilder builder, Tensor tensor) {
        builder.append("tensor dataType=").append(tensor.dataType()).append('\n');
        builder.append("tensor index=").append(tensor.index()).append('\n');
        builder.append("tensor name=").append(tensor.name()).append('\n');
        builder.append("tensor numBytes=").append(tensor.numBytes()).append('\n');
        builder.append("tensor numDimensions=").append(tensor.numDimensions()).append('\n');
        builder.append("tensor numElements=").append(tensor.numElements()).append('\n');
        builder.append("tensor quantizationParams=").append(tensor.quantizationParams().getScale()).append('\n');
        builder.append("tensor shape=").append(Arrays.toString(tensor.shape())).append('\n');
        builder.append("tensor shapeSignature=").append(Arrays.toString(tensor.shapeSignature())).append('\n');
    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     */
    public static TFLiteDetector create(final AssetManager assetManager, final String modelFilename,
                                        final String labelFilename) throws IOException {
        InputStream modelInput = assetManager.open(modelFilename);
        InputStream labelsInput = assetManager.open(labelFilename);
        return create(modelInput, labelsInput);
    }

    public static TFLiteDetector create(final String modelFilename, final String labelFilePath) throws IOException {
        InputStream modelInput = new FileInputStream(modelFilename);
        InputStream labelInput = new FileInputStream(labelFilePath);
        return create(modelInput, labelInput);
    }

    public List<Recognition> recognize(byte[] rgb) {
        long start = System.currentTimeMillis();
        if (imgData == null || imgData.capacity() != rgb.length) {
            imgData = ByteBuffer.allocateDirect(rgb.length);
            imgData.order(ByteOrder.nativeOrder());
        }
        imgData.rewind();
        imgData.put(rgb);

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);

        // Run the inference call.
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        //        tfLite.run(imgData, outputClasses);

        // Show the best detections.
        // after scaling them back to the input size.

        // You need to use the number of detections from the output and not the NUM_DETECTONS variable declared on top
        // because on some models, they don't always output the same total number of detections
        // For example, your model's NUM_DETECTIONS = 20, but sometimes it only outputs 16 predictions
        // If you don't use the output's numDetections, you'll get nonsensical data
        int numDetectionsOutput = Math.min(NUM_DETECTIONS, (int) numDetections[0]); // cast from float to integer,
        // use min for safety

        final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
        for (int i = 0; i < numDetectionsOutput; ++i) {
            if (outputScores[0][i] < 0.6f) {
                continue;
            }
            int labelOffset = (int) outputClasses[0][i] + 1;
            final RectF detection = new RectF(outputLocations[0][i][1], outputLocations[0][i][0],
                outputLocations[0][i][3], outputLocations[0][i][2]);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            recognitions.add(new Recognition(labelOffset, labels[labelOffset], outputScores[0][i], detection));
        }
        Log.d(TAG, "recognize: time=" + (System.currentTimeMillis() - start));
        return recognitions;
    }

}
