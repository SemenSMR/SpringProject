package org.example;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
   static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html",
            "/events.html", "/events.js");
   private static final int PORT = 9999;
   private static final int THREAD_POOL_SIZE = 64;
   private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
   public void start(int port) {
      try (ServerSocket serverSocket = new ServerSocket(port)) {
         while (true) {
            Socket socket = serverSocket.accept();
            executorService.submit(() -> handleConnection(socket));
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   private void handleConnection(Socket socket) {
      try (
              BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())
      ) {
         String requestLine = in.readLine();
         String[] parts = requestLine.split(" ");

         if (parts.length != 3) {
            ResponseUtils.sendBadRequestResponse(out);
            return;
         }

         String path = parts[1];
         if (!validPaths.contains(path)) {
            ResponseUtils.badRequest(out);
            return;
         }

         Path filePath = Path.of(".", "public", path);
         String mimeType = Files.probeContentType(filePath);

         if ("/classic.html".equals(path)) {
           ResponseUtils.responseOk(out, filePath);
         } else {
            ResponseUtils.classicHtml(out, filePath, mimeType);
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
