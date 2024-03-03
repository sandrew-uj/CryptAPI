package ru.andrew.smirnov;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLSession;

public class CrptApi {
    private static final URI CRYPTA_ENDPOINT = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private static final String API_TOKEN = "Bearer secret_token";
    private final TimeUnit timeUnit;

    private final Semaphore semaphore;

    private final ExecutorService executorService;

    private final Logger LOG = Logger.getLogger(CrptApi.class.getName());

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (Objects.isNull(timeUnit)) {
            throw new IllegalArgumentException("Provided TimeUnit is null!");
        }
        this.timeUnit = timeUnit;
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit should be positive non-zero integer!");
        }
        this.semaphore = new Semaphore(requestLimit);
        this.executorService = Executors.newCachedThreadPool();
    }

    public ApiResponse createDocument(Document document) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            LOG.warning("Interruption while semaphore.acquire():\n" + Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
        executorService.submit(() -> {
            try {
                Thread.sleep(timeUnit.toMillis(1));
            } catch (InterruptedException e) {
                LOG.warning("Interruption while Thread.sleep():\n" + Arrays.toString(e.getStackTrace()));
                throw new RuntimeException(e);
            }
            semaphore.release();
        });

        if (Objects.isNull(document)) {
            return new ApiResponse(500, "Provided document is null!");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(CRYPTA_ENDPOINT)
                .header("Content-Type", "application/json")
                .header("pg", "clothes")
                .header("Authorization", API_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(Objects.requireNonNull(document.toString())))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            var response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(200, response);
        } catch (IOException | InterruptedException e) {
            String message = "Failed to send document to api: " + Arrays.toString(e.getStackTrace());
            LOG.warning(message);
            return new ApiResponse(500, message);
        }
    }

    public boolean isBusy() {
        return semaphore.availablePermits() == 0;
    }

    record ApiResponse(
            int statusCode,
            String body,
            Optional<HttpResponse<String>> response
    ) implements HttpResponse<String> {

        ApiResponse (int statusCode, String body) {
            this(statusCode, body, Optional.empty());
        }

        ApiResponse (int statusCode, HttpResponse<String> response) {
            this(statusCode, "", Optional.of(response));
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return response.map(HttpResponse::request).orElse(null);
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return response.map(HttpResponse::headers).orElse(null);
        }

        @Override
        public String body() {
            return response.map(HttpResponse::body).orElse(body);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return CRYPTA_ENDPOINT;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    record Document(
            String participantInn,
            String docId,
            String docStatus,
            String docType,
            boolean importRequest,
            String ownerInn,
            String producerInn,
            Date productionDate,
            String productionType,
            List<Document.Product> products,
            Date regDate,
            String regNumber
    ) {
        record Product(
                String certificateDocument,
                Date certificateDocumentDate,
                String certificateDocumentNumber,
                String ownerInn,
                String producerInn,
                Date productionDate,
                String tnvedCode,
                String uitCode,
                String uituCode
        ) {
            public Map<String, String> toJson() {
                return Map.of(
                        "certificate_document", certificateDocument,
                        "certificate_document_date", certificateDocumentDate.toString(),
                        "certificate_document_number", certificateDocumentNumber,
                        "owner_inn", ownerInn,
                        "producer_inn", producerInn,
                        "production_date", productionDate.toString(),
                        "tnved_code", tnvedCode,
                        "uit_code", uitCode,
                        "uitu_code", uituCode
                );
            }
        }

        @Override
        public String toString() {
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.writeValueAsString(Map.ofEntries(
                        Map.entry("description", Map.of("participantInn", participantInn)),
                        Map.entry("doc_id", docId),
                        Map.entry("doc_status", docStatus),
                        Map.entry("doc_type", docType),
                        Map.entry("importRequest", importRequest),
                        Map.entry("owner_inn", ownerInn),
                        Map.entry("producer_inn", producerInn),
                        Map.entry("production_date", productionDate.toString()),
                        Map.entry("production_type", productionType),
                        Map.entry("products", products.stream().map(Product::toJson).toList()),
                        Map.entry("reg_date", regDate),
                        Map.entry("reg_number", regNumber)

                ));
            } catch (JsonProcessingException e) {
                System.err.println("Error while serializing JSON");
            }
            return null;
        }
    }
}
