package cn.sskbskdrin.record.screen;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Random;

public final class HttpServer {
    private static final String TAG = "HttpServer";
    private static final int SEVER_SOCKET_TIMEOUT = 50;
    private static final String DEFAULT_ADDRESS = "/";
    private static final String DEFAULT_STREAM_ADDRESS = "/screen_stream.mjpeg";
    private static final String DEFAULT_ICO_ADDRESS = "/favicon.ico";
    private static final String DEFAULT_PIN_ADDRESS = "/?pin=";

    private final Object mLock = new Object();

    private ServerSocket mServerSocket;
    private HttpServerThread mHttpServerThread;
    //    private ImageDispatcher mImageDispatcher;

    private String mCurrentPinUri = DEFAULT_PIN_ADDRESS;
    private String mCurrentStreamAddress = DEFAULT_STREAM_ADDRESS;

    private class HttpServerThread extends Thread {

        HttpServerThread() {
            super(HttpServerThread.class.getSimpleName());
        }

        public void run() {
            while (!isInterrupted()) {
                synchronized (mLock) {
                    try {
                        Socket clientSocket = HttpServer.this.mServerSocket.accept();
                        BufferedReader bufferedReaderFromClient =
                            new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF8"));

                        final String requestLine = bufferedReaderFromClient.readLine();
                        Log.d(TAG, "requestLine: " + requestLine);
                        if (requestLine == null || !requestLine.startsWith("GET")) {
                            sendNotFound(clientSocket);
                            continue;
                        }

                        final String[] requestUriArray = requestLine.split(" ");
                        String requestUri;
                        if (requestUriArray.length >= 2) {
                            requestUri = requestUriArray[1];
                        } else {
                            sendNotFound(clientSocket);
                            continue;
                        }

                        if (mCurrentStreamAddress.equals(requestUri)) {
                            //                            HttpServer.this.mImageDispatcher.addClient(clientSocket);
                            continue;
                        }

                        if (DEFAULT_ICO_ADDRESS.equals(requestUri)) {
                            sendFavicon(clientSocket);
                            continue;
                        }

                        sendNotFound(clientSocket);
                    } catch (SocketException | SocketTimeoutException ignored) {
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void sendPinRequestPage(final Socket socket, final boolean pinError) throws IOException {
            try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), "UTF8"
            )) {
                outputStreamWriter.write("HTTP/1.1 200 OK\r\n");
                outputStreamWriter.write("Content-Type: text/html\r\n");
                outputStreamWriter.write("Connection: close\r\n");
                outputStreamWriter.write("\r\n");
                //                outputStreamWriter.write(getAppData().getPinRequestHtml(pinError));
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();
            }
        }

        private void sendMainPage(final Socket socket, final String streamAddress) throws IOException {
            try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), "UTF8"
            )) {
                outputStreamWriter.write("HTTP/1.1 200 OK\r\n");
                outputStreamWriter.write("Content-Type: text/html\r\n");
                outputStreamWriter.write("Connection: close\r\n");
                outputStreamWriter.write("\r\n");
                //                outputStreamWriter.write(getAppData().getIndexHtml(streamAddress));
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();
            }
        }

        private void sendFavicon(Socket socket) throws IOException {
            try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), "UTF8"
            )) {
                outputStreamWriter.write("HTTP/1.1 200 OK\r\n");
                outputStreamWriter.write("Content-Type: image/png\r\n");
                outputStreamWriter.write("Connection: close\r\n");
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();
                                socket.getOutputStream().write(icon);
                socket.getOutputStream().flush();
            }
        }

        private void sendNotFound(final Socket socket) throws IOException {
            try (final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), "UTF8"
            )) {
                outputStreamWriter.write("HTTP/1.1 301 Moved Permanently\r\n");
                //                outputStreamWriter.write("Location: " + getAppData().getServerAddress() + "\r\n");
                outputStreamWriter.write("Connection: close\r\n");
                outputStreamWriter.write("\r\n");
                outputStreamWriter.flush();
            }
        }

    }

    private static byte[] icon;

    public static byte[] getFavicon(final Context context) {
        try (final InputStream inputStream = context.getAssets().open("favicon.png")) {
            final byte[] iconBytes = new byte[inputStream.available()];
            int count = inputStream.read(iconBytes);
            icon = iconBytes;
            return iconBytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public HttpServer() {
        mHttpServerThread = new HttpServerThread();
    }

    public void start() {
        if (mHttpServerThread.isAlive()) return;

        mCurrentStreamAddress = DEFAULT_STREAM_ADDRESS;
        mCurrentPinUri = DEFAULT_PIN_ADDRESS;

        try {
            mServerSocket = new ServerSocket(8080);
            mServerSocket.setSoTimeout(SEVER_SOCKET_TIMEOUT);

            mHttpServerThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop(final byte[] clientNotifyImage) {
        if (!mHttpServerThread.isAlive()) return;
        mHttpServerThread.interrupt();
        synchronized (mLock) {
            //            mImageDispatcher.stop(clientNotifyImage);
//            mImageDispatcher = null;
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mServerSocket = null;
            mHttpServerThread = new HttpServerThread();
        }
    }


    private String getRandomStreamAddress(final String pin) {
        final String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        final int randomLength = 10;
        final Random random = new Random(Long.parseLong(pin));
        final char[] randomPart = new char[randomLength];
        for (int i = 0; i < randomLength; i++) {
            randomPart[i] = alphabet.charAt(random.nextInt(alphabet.length()));
        }
        return "/screen_stream_" + String.valueOf(randomPart) + ".mjpeg";
    }
}