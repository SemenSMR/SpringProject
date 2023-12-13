package org.example;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectListener implements Runnable {
    private final Socket socket;
    private final Map<String, Map<String, Handler>> handlers;

    public ConnectListener(Socket socket, Map<String, Map<String, Handler>> handlers) {
        this.socket = socket;
        this.handlers = handlers;
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Override
    public void run() {
        try (
                final var in = new BufferedInputStream(socket.getInputStream());
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            final int limit = 4096;

            in.mark(limit);
            final byte[] buffer = new byte[limit];
            final int read = in.read(buffer);

            final byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
            final int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
            if (requestLineEnd == -1) {
                ResponseUtils.badRequest(out);
                return;
            }

            final String[] parts = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
            if (parts.length != 3) {
                ResponseUtils.badRequest(out);
                return;
            }

            if (!parts[1].startsWith("/")) {
                ResponseUtils.badRequest(out);
                return;
            }

            RequestLine requestLine = new RequestLine(parts[0], parts[1], parts[2]);

            final byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final int headersStart = requestLineEnd + requestLineDelimiter.length;
            final int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
            if (headersEnd == -1) {
                ResponseUtils.badRequest(out);
                return;
            }

            in.reset();

            in.skip(headersStart);

            final byte[] headersBytes = in.readNBytes(headersEnd - headersStart);
            final List<String> headers = Arrays.asList(new String(headersBytes).split("\r\n"));

            Request request = new Request(requestLine, headers);

            if (!requestLine.getMethod().equals("GET")) {
                in.skip(headersDelimiter.length);
                final Optional<String> contentLength = extractHeader(headers, "Content-Length");
                if (contentLength.isPresent()) {
                    final int length = Integer.parseInt(contentLength.get());
                    final byte[] bodyBytes = in.readNBytes(length);

                    final String body = new String(bodyBytes);
                    request.setBody(body);
                }
            }

            Handler handler = handlers.get(request.getRequestLine().getMethod())
                    .get(request.getRequestLine().getPath());
            handler.handle(request, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
