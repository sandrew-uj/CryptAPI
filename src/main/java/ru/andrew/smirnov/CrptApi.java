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
    /**
     * Endpoint for sending requests
     */
    private static final URI CRYPTA_ENDPOINT = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");

    /**
     * Maximum thread count
     */
    private static final int MAX_THREAD_COUNT = 10000;

    /**
     * Secret token for api, in future will be replaced for valid token
     */
    private static final String API_TOKEN = "Bearer secret_token";

    /**
     * TimeUnit in which will be limited rate
     */
    private final TimeUnit timeUnit;

    /**
     * Semaphore for rate limiting
     */
    private final Semaphore semaphore;


    /**
     * Executor service for submitting actions to release semaphore
     */
    private final ExecutorService executorService;


    /**
     * Logger for logging errors
     */
    private final Logger LOG = Logger.getLogger(CrptApi.class.getName());


    /**
     * Creates instance of CrptApi
     * Initializes semaphore with maximum rate of requestLimit
     * @param timeUnit timeUnit in which will be limited rate
     * @param requestLimit limit of rate in timeUnit
     */
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


    /**
     *
     * @param document document to add in "Честный знак"
     * @return ApiResponse that implements HttpResponse
     */
    public ApiResponse createDocument(Document document) {
        int currentThreadCount = ((ThreadPoolExecutor) executorService).getPoolSize();
        if (currentThreadCount > MAX_THREAD_COUNT) {
            return new ApiResponse(429, "Too many requests");   // return 429 if too many threads
        }
        try {
            semaphore.acquire();    // try to acquire semaphore
        } catch (InterruptedException e) {
            LOG.warning("Interruption while semaphore.acquire():\n" + Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
        executorService.submit(() -> {      // submitting actions to release semaphore in future
            try {
                Thread.sleep(timeUnit.toMillis(1));
            } catch (InterruptedException e) {
                LOG.warning("Interruption while Thread.sleep():\n" + Arrays.toString(e.getStackTrace()));
                throw new RuntimeException(e);
            }
            semaphore.release();
        });

        if (Objects.isNull(document)) {     // validating document
            return new ApiResponse(500, "Provided document is null!");
        }

        HttpRequest request = HttpRequest.newBuilder()  //building request
                .version(HttpClient.Version.HTTP_1_1)
                .uri(CRYPTA_ENDPOINT)
                .header("Content-Type", "application/json")
                .header("pg", "clothes")
                .header("Authorization", API_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(Objects.requireNonNull(document.toString())))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            var response = client.send(request,             //sending to api
                    HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(200, response);
        } catch (IOException | InterruptedException e) {
            String message = "Failed to send document to api: " + Arrays.toString(e.getStackTrace());
            LOG.warning(message);
            return new ApiResponse(500, message);
        }
    }

    /**
     * Checks if CrptApi is busy
     * Used mostly for debugging and test purpose
     * @return true if we can't send request to "Честный знак", false otherwise
     */
    public boolean isBusy() {
        return semaphore.availablePermits() == 0;
    }

    /**
     * My own class that implements HttpResponse
     * @param statusCode
     * @param body
     * @param response
     */
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

        /**
         * Returns status code
         * @return statusCode
         */
        @Override
        public int statusCode() {
            return statusCode;
        }

        /**
         * Returns HttpRequest
         * @return request if exists else returns null
         */
        @Override
        public HttpRequest request() {
            return response.map(HttpResponse::request).orElse(null);
        }

        /**
         * Returns HttpResponse
         * @return Optional.empty()
         */
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        /**
         * Returns headers of this response
         * @return headers of HttpResponse if it's present, null otherwise
         */
        @Override
        public HttpHeaders headers() {
            return response.map(HttpResponse::headers).orElse(null);
        }

        /**
         * Returns body of response
         * @return body of response if it's present, else body of ApiResponse
         */
        @Override
        public String body() {
            return response.map(HttpResponse::body).orElse(body);
        }

        /**
         * Returns ssl session
         * @return Optional.empty()
         */
        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        /**
         * Return URI of response
         * @return CRYPTA_ENDPOINT
         */
        @Override
        public URI uri() {
            return CRYPTA_ENDPOINT;
        }

        /**
         * Returns version of HttpResponse
         * @return HTTP_1_1 version
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    /**
     * Class for document in Api "Честный знак"
     * @param participantInn
     * @param docId
     * @param docStatus
     * @param docType
     * @param importRequest
     * @param ownerInn
     * @param producerInn
     * @param productionDate
     * @param productionType
     * @param products
     * @param regDate
     * @param regNumber
     */
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
        /**
         * Class Product, which used in Document class
         * @param certificateDocument
         * @param certificateDocumentDate
         * @param certificateDocumentNumber
         * @param ownerInn
         * @param producerInn
         * @param productionDate
         * @param tnvedCode
         * @param uitCode
         * @param uituCode
         */
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
            /**
             * Method, that returns json object of this Product
             * @return Map<String, String>, which represents json object
             */
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

        /**
         * Methods for retrieving json representation of Document
         * Using jackson for serialization
         * @return String that represents json of Document
         */
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
