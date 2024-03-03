package ru.andrew.smirnov;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class TestCrptApi {

    private final Date date = Date.from(Instant.now());
    private final CrptApi.Document document = new CrptApi.Document(
            "participantInnValue",
            "docIdValue",
            "docStatusValue",
            "LP_INTRODUCE_GOODS",
            true,
            "ownerInnValue",
            "producerInnValue",
            date,
            "productionTypeValue",
            List.of(new CrptApi.Document.Product(
                    "certificateDocumentValue",
                    date,
                    "certificateDocumentNumberValue",
                    "ownerInnValue",
                    "producerInnValue",
                    date,
                    "tnvedCodeValue",
                    "uitCodeValue",
                    "uituCodeValue"
            )),
            date,
            "regNumberValue"
    );

    final List<List<Integer>> testCases = List.of(
            List.of(5, 4),
            List.of(10, 15),
            List.of(10, 10),
            List.of(10, 20),
            List.of(10, 100)
    );

    @Test
    public void testCrptApi() throws InterruptedException {
        for(var testCase: testCases) {
            int requestLimit = testCase.get(0);
            int requestSize = testCase.get(1);
            System.out.printf("requestLimit=%d, requestSize=%d\n", requestLimit, requestSize);

            var crptApi = new CrptApi(TimeUnit.SECONDS, requestLimit);
            var executorService = Executors.newFixedThreadPool(requestSize);
            List<Future<?>> futures = new ArrayList<>();
            IntStream.range(0, requestSize).forEach(i ->  {
                final Future<?> future = executorService.submit(() ->  {
                    var res = crptApi.createDocument(document);
                    assert res.statusCode() == 200;
                });
                futures.add(future);
            });

            int coef = (requestSize - 1) / requestLimit;
            for (int i = 0; i < coef; i++) {
                Thread.sleep(3000L);
            }
            assert !crptApi.isBusy();

            Thread.sleep(4000L);
            futures.forEach(future -> {
                assert future.isDone();
            });
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch(InterruptedException e) {
                System.err.println("Termination of thread pool was interrupted");
            }
        }
    }
}
