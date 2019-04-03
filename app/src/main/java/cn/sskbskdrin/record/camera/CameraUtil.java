package cn.sskbskdrin.record.camera;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.media.Image;
import android.os.Build;
import android.support.annotation.RequiresApi;

import java.nio.ByteBuffer;

/**
 * @author sskbskdrin
 * @date 2019/April/1
 */
public class CameraUtil {

    public static Point findBestSurfacePoint(Point previewSize, Point maxPoint) {
        if (previewSize == null || maxPoint == null || maxPoint.x == 0 || maxPoint.y == 0) {
            return maxPoint;
        }
        double scaleX, scaleY, scale;
        if (maxPoint.x < maxPoint.y) {
            scaleX = previewSize.x * 1.0f / maxPoint.y;
            scaleY = previewSize.y * 1.0f / maxPoint.x;
        } else {
            scaleX = previewSize.x * 1.0f / maxPoint.x;
            scaleY = previewSize.y * 1.0f / maxPoint.y;
        }
        scale = scaleX > scaleY ? scaleX : scaleY;
        Point result = new Point();
        if (maxPoint.x < maxPoint.y) {
            result.x = (int) (previewSize.y / scale);
            result.y = (int) (previewSize.x / scale);
        } else {
            result.x = (int) (previewSize.x / scale);
            result.y = (int) (previewSize.y / scale);
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static byte[] getBytesFromImage(Image image, int format) {
        //获取源数据，如果是YUV格式的数据planes.length = 3
        //plane[i]里面的实际数据可能存在byte[].length <= capacity (缓冲区总大小)
        final Image.Plane[] planes = image.getPlanes();

        //数据有效宽度，一般的，图片width <= rowStride，这也是导致byte[].length <= capacity的原因
        // 所以我们只取width部分
        int width = image.getWidth();
        int height = image.getHeight();

        //此处用来装填最终的YUV数据，需要1.5倍的图片大小，因为Y U V 比例为 4:1:1
        byte[] yuvBytes = new byte[width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        //目标数组的装填到的位置
        int dstIndex = 0;

        //临时存储uv数据的
        byte uBytes[] = new byte[width * height / 4];
        byte vBytes[] = new byte[width * height / 4];
        int uIndex = 0;
        int vIndex = 0;

        int pixelsStride, rowStride;
        for (int i = 0; i < planes.length; i++) {
            pixelsStride = planes[i].getPixelStride();
            rowStride = planes[i].getRowStride();

            ByteBuffer buffer = planes[i].getBuffer();

            //如果pixelsStride==2，一般的Y的buffer长度=640*480，UV的长度=640*480/2-1
            //源数据的索引，y的数据是byte中连续的，u的数据是v向左移以为生成的，两者都是偶数位为有效数据
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);

            int srcIndex = 0;
            if (i == 0) {
                //直接取出来所有Y的有效区域，也可以存储成一个临时的bytes，到下一步再copy
                for (int j = 0; j < height; j++) {
                    System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width);
                    srcIndex += rowStride;
                    dstIndex += width;
                }
            } else if (i == 1) {
                //根据pixelsStride取相应的数据
                for (int j = 0; j < height / 2; j++) {
                    for (int k = 0; k < width / 2; k++) {
                        uBytes[uIndex++] = bytes[srcIndex];
                        srcIndex += pixelsStride;
                    }
                    if (pixelsStride == 2) {
                        srcIndex += rowStride - width;
                    } else if (pixelsStride == 1) {
                        srcIndex += rowStride - width / 2;
                    }
                }
            } else if (i == 2) {
                //根据pixelsStride取相应的数据
                for (int j = 0; j < height / 2; j++) {
                    for (int k = 0; k < width / 2; k++) {
                        vBytes[vIndex++] = bytes[srcIndex];
                        srcIndex += pixelsStride;
                    }
                    if (pixelsStride == 2) {
                        srcIndex += rowStride - width;
                    } else if (pixelsStride == 1) {
                        srcIndex += rowStride - width / 2;
                    }
                }
            }
        }

        //根据要求的结果类型进行填充
        switch (format) {
            case ImageFormat.YUV_420_888:
                System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.length);
                System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.length, vBytes.length);
                break;
            case ImageFormat.YV12:
                System.arraycopy(vBytes, 0, yuvBytes, dstIndex, vBytes.length);
                System.arraycopy(uBytes, 0, yuvBytes, dstIndex + vBytes.length, uBytes.length);
                break;
            case ImageFormat.NV21:
                for (int i = 0; i < vBytes.length; i++) {
                    yuvBytes[dstIndex++] = vBytes[i];
                    yuvBytes[dstIndex++] = uBytes[i];
                }
                break;
            default:
                for (int i = 0; i < vBytes.length; i++) {
                    yuvBytes[dstIndex++] = uBytes[i];
                    yuvBytes[dstIndex++] = vBytes[i];
                }
                break;
        }
        return yuvBytes;
    }
}
