package ru.agentche;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Aleksey Anikeev aka AgentChe
 * Date of creation: 04.12.2022
 */

public class WebServer {
    private final HashMap<String, HashMap<String, Handler>> handlers;
    private final ExecutorService threadPool;

    public WebServer() {
        threadPool = Executors.newFixedThreadPool(64);
        handlers = App.handlers;

    }


    @SuppressWarnings("InfiniteLoopStatement")
    public void serverOn(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.submit(new Client(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        handlers
                .computeIfAbsent(method, k -> new HashMap<>())
                .computeIfAbsent(path, k -> handler);
    }

    private static class Client extends Thread {
        Socket clientSocket;
        InputStream in;
        BufferedOutputStream out;

        public Client(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            this.in = clientSocket.getInputStream();
            this.out = new BufferedOutputStream(clientSocket.getOutputStream());
        }


        private void badRequest(Request request, BufferedOutputStream out) throws IOException {
            try {

                Path filePath = Path.of(".", "public", "/bad-request.html");
                String mimeType = "html";

                String template = Files.readString(filePath);
                String contentReplaceTime = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                );

                byte[] content = contentReplaceTime
                        .replace("{response}", request.getPath())
                        .getBytes(StandardCharsets.UTF_8);
                out.write((
                        "HTTP/1.1 404 Bad request\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void defaultCase(Path filePath, String mimeType) throws IOException {
            long length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
        }

        @Override
        public void run() {
            try {

                Request request = Request.requestFromInputStream(in);
                System.out.println(request.getMethod() + " - МЕТОД");
                System.out.println(request + "\n");


                if (App.handlers.getOrDefault(request.getMethod(), null)
                        .getOrDefault(request.getCleanPath(), null) != null) {

                    App.handlers.get(request.getMethod()).get(request.getCleanPath())
                            .handle(request, out);
                } else if (App.validPaths.contains(request.getCleanPath())) {
                    Path filePath = Path.of(".", "public", request.getPath());
                    String  mimeType = Files.probeContentType(filePath);
                    defaultCase(filePath, mimeType);

                } else badRequest(request, out);


                clientSocket.close();
                in.close();
                out.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}