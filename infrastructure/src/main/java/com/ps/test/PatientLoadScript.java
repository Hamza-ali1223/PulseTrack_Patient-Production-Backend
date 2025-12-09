package com.ps.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatientLoadScript {

    // *************** CONFIG – EDIT THESE ONLY *************** //
    private static final String SIGNUP_URL           = "http://lb-2643176f.elb.localhost.localstack.cloud:4004/auth/signup";
    private static final String LOGIN_URL            = "http://lb-2643176f.elb.localhost.localstack.cloud:4004/auth/login";
    private static final String CREATE_PATIENT_URL   = "http://lb-2643176f.elb.localhost.localstack.cloud:4004/api/patients";
    private static final String GET_ALL_PATIENTS_URL = "http://lb-2643176f.elb.localhost.localstack.cloud:4004/api/patients";

    // How many requests in each heavy phase
    private static final int CREATE_PATIENT_REQUESTS    = 400;
    private static final int GET_ALL_PATIENTS_REQUESTS  = 600;

    // Concurrency (number of parallel requests) for each heavy phase
    private static final int CREATE_PATIENT_CONCURRENCY   = 100;
    private static final int GET_ALL_PATIENTS_CONCURRENCY = 120;

    // Small pause used only in the tiny phases (signup/login), keep at 0 if you want raw speed
    private static final long PAUSE_MS = 0;

    // Simple holder for user creds
    private record UserAccount(String email, String username, String password, String role) {}

    // Result of one HTTP call
    private record RequestResult(String phase, int index, long durationMs, int statusCode, String error) {}

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        long scriptStart = System.nanoTime();

        System.out.println("==================================================");
        System.out.println(" Patient Management API Load Script (Concurrent)");
        System.out.println("==================================================\n");

        System.out.printf("CONFIG:%n");
        System.out.printf("  CREATE_PATIENT_REQUESTS    = %d%n", CREATE_PATIENT_REQUESTS);
        System.out.printf("  GET_ALL_PATIENTS_REQUESTS  = %d%n", GET_ALL_PATIENTS_REQUESTS);
        System.out.printf("  CREATE_PATIENT_CONCURRENCY = %d%n", CREATE_PATIENT_CONCURRENCY);
        System.out.printf("  GET_ALL_PATIENTS_CONCURRENCY = %d%n%n", GET_ALL_PATIENTS_CONCURRENCY);

        List<UserAccount> accounts = List.of(
                new UserAccount("user1@example.com", "User One", "12345678", "USER"),
                new UserAccount("user2@example.com", "User Two", "12345678", "USER")
        );

        // ---------- 1) SIGNUP (sequential, small) ----------
        System.out.println("1) SIGNUP (create 2 accounts)\n");
        List<RequestResult> signupResults = new ArrayList<>();

        int index = 1;
        for (UserAccount acc : accounts) {
            String body = signupBody(acc);
            HttpRequest req = HttpRequest.newBuilder(URI.create(SIGNUP_URL))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(body))
                    .build();

            long start = System.nanoTime();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            signupResults.add(new RequestResult(
                    "SIGNUP", index, durationMs, resp.statusCode(),
                    resp.statusCode() / 100 == 2 ? null : summarize(resp.body())
            ));

            System.out.printf("  SIGNUP #%d -> status=%d, time=%d ms%n",
                    index, resp.statusCode(), durationMs);

            index++;
            pause();
        }

        // ---------- 2) LOGIN (sequential, small) ----------
        System.out.println("\n2) LOGIN (2 accounts, JWT from first)\n");
        List<RequestResult> loginResults = new ArrayList<>();
        String bearerToken = null;

        index = 1;
        for (UserAccount acc : accounts) {
            String body = loginBody(acc);
            HttpRequest req = HttpRequest.newBuilder(URI.create(LOGIN_URL))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(body))
                    .build();

            long start = System.nanoTime();
            HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            String error = null;
            if (resp.statusCode() / 100 != 2) {
                error = summarize(resp.body());
            }

            loginResults.add(new RequestResult(
                    "LOGIN", index, durationMs, resp.statusCode(), error
            ));

            System.out.printf("  LOGIN  #%d -> status=%d, time=%d ms%n",
                    index, resp.statusCode(), durationMs);

            if (index == 1) {
                if (resp.statusCode() / 100 == 2) {
                    bearerToken = extractToken(resp.body());
                    System.out.println("    -> Extracted JWT token from LOGIN #1");
                } else {
                    System.err.println("    !! Cannot extract token: LOGIN #1 is not 2xx");
                    return;
                }
            }

            index++;
            pause();
        }

        if (bearerToken == null) {
            System.err.println("No token extracted, aborting.");
            return;
        }

        // ---------- 3) CREATE PATIENTS (concurrent) ----------
        System.out.println("\n3) CREATE PATIENTS (concurrent)\n");
        System.out.printf("   Requests   : %d%n", CREATE_PATIENT_REQUESTS);
        System.out.printf("   Concurrency: %d%n%n", CREATE_PATIENT_CONCURRENCY);

        List<RequestResult> createPatientResults =
                runConcurrentPostPhase(
                        client,
                        CREATE_PATIENT_URL,
                        "CREATE_PATIENT",
                        CREATE_PATIENT_REQUESTS,
                        CREATE_PATIENT_CONCURRENCY,
                        PatientLoadScript::patientBody,
                        bearerToken
                );

        // ---------- 4) GET ALL PATIENTS (concurrent) ----------
        System.out.println("\n4) GET ALL PATIENTS (concurrent)\n");
        System.out.printf("   Requests   : %d%n", GET_ALL_PATIENTS_REQUESTS);
        System.out.printf("   Concurrency: %d%n%n", GET_ALL_PATIENTS_CONCURRENCY);

        List<RequestResult> getAllResults =
                runConcurrentGetPhase(
                        client,
                        GET_ALL_PATIENTS_URL,
                        "GET_ALL_PATIENTS",
                        GET_ALL_PATIENTS_REQUESTS,
                        GET_ALL_PATIENTS_CONCURRENCY,
                        bearerToken
                );

        long scriptDurationMs = Duration.ofNanos(System.nanoTime() - scriptStart).toMillis();

        // ---------- SUMMARY ----------
        System.out.println("\n==================================================");
        System.out.println(" SUMMARY");
        System.out.println("==================================================");

        printPhaseSummary("SIGNUP           ", signupResults);
        printPhaseSummary("LOGIN            ", loginResults);
        printPhaseSummary("CREATE PATIENT   ", createPatientResults);
        printPhaseSummary("GET ALL PATIENTS ", getAllResults);

        System.out.printf("%nTotal script time: %d ms%n", scriptDurationMs);

        System.out.println("\nHigh-level flow:");
        System.out.println(" - Signed up 2 accounts (existing users may return 4xx, still counted)");
        System.out.println(" - Logged in 2 accounts, extracted JWT from the first login response");
        System.out.println(" - Created " + CREATE_PATIENT_REQUESTS + " patients with unique emails using that JWT");
        System.out.println(" - Called GET all patients " + GET_ALL_PATIENTS_REQUESTS +
                " times using concurrent requests");
        System.out.println(" - Printed aggregated timings and error counts per phase");
    }

    // ---------- Concurrent helpers ----------

    private static List<RequestResult> runConcurrentPostPhase(
            HttpClient client,
            String url,
            String phaseName,
            int totalRequests,
            int concurrency,
            java.util.function.IntFunction<String> bodySupplier,
            String bearerToken
    ) throws InterruptedException {

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Callable<RequestResult>> tasks = new ArrayList<>();

        for (int i = 1; i <= totalRequests; i++) {
            final int idx = i;
            tasks.add(() -> {
                String jsonBody = bodySupplier.apply(idx);

                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(jsonBody));

                if (bearerToken != null) {
                    builder.header("Authorization", "Bearer " + bearerToken);
                }

                HttpRequest req = builder.build();

                long start = System.nanoTime();
                try {
                    HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
                    long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    String error = resp.statusCode() / 100 == 2 ? null : summarize(resp.body());
                    return new RequestResult(phaseName, idx, durationMs, resp.statusCode(), error);
                } catch (Exception e) {
                    long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    return new RequestResult(phaseName, idx, durationMs, -1, e.toString());
                }
            });
        }

        List<Future<RequestResult>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<RequestResult> results = new ArrayList<>(totalRequests);
        for (Future<RequestResult> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException e) {
                results.add(new RequestResult(
                        phaseName, -1, 0, -1, "Future failed: " + e.getCause()
                ));
            }
        }

        return results;
    }

    private static List<RequestResult> runConcurrentGetPhase(
            HttpClient client,
            String url,
            String phaseName,
            int totalRequests,
            int concurrency,
            String bearerToken
    ) throws InterruptedException {

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Callable<RequestResult>> tasks = new ArrayList<>();

        for (int i = 1; i <= totalRequests; i++) {
            final int idx = i;
            tasks.add(() -> {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .GET();
                if (bearerToken != null) {
                    builder.header("Authorization", "Bearer " + bearerToken);
                }
                HttpRequest req = builder.build();

                long start = System.nanoTime();
                try {
                    HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
                    long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    String error = resp.statusCode() / 100 == 2 ? null : summarize(resp.body());
                    return new RequestResult(phaseName, idx, durationMs, resp.statusCode(), error);
                } catch (Exception e) {
                    long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
                    return new RequestResult(phaseName, idx, durationMs, -1, e.toString());
                }
            });
        }

        List<Future<RequestResult>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<RequestResult> results = new ArrayList<>(totalRequests);
        for (Future<RequestResult> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException e) {
                results.add(new RequestResult(
                        phaseName, -1, 0, -1, "Future failed: " + e.getCause()
                ));
            }
        }

        return results;
    }

    // ---------- Payload builders ----------

    private static String signupBody(UserAccount acc) {
        return """
                {
                  "email": "%s",
                  "username": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(acc.email(), acc.username(), acc.password(), acc.role());
    }

    private static String loginBody(UserAccount acc) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(acc.email(), acc.password(), acc.role());
    }

    private static String patientBody(int index) {
        String email = "loadtest+" + index + "@example.com";
        String name = "Load Test Patient " + index;

        return """
                {
                  "name": "%s",
                  "address": "Sindh, PK",
                  "dateofBirth": "2000-01-01",
                  "email": "%s",
                  "registeredDate": "2025-01-01"
                }
                """.formatted(name, email);
    }

    // ---------- Small helpers ----------

    private static String extractToken(String json) {
        Pattern pattern = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Could not find 'token' field in login response: " + json);
    }

    private static void pause() {
        if (PAUSE_MS <= 0) return;
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String summarize(String body) {
        if (body == null) return "";
        String singleLine = body.replaceAll("\\s+", " ").trim();
        if (singleLine.isEmpty()) return "<empty>";
        if (singleLine.length() <= 120) return singleLine;
        return singleLine.substring(0, 117) + "...";
    }

    private static void printPhaseSummary(String label, List<RequestResult> results) {
        if (results == null || results.isEmpty()) {
            System.out.printf("%s : no data%n", label);
            return;
        }

        int total = results.size();
        int success = 0;
        int clientError = 0;
        int serverError = 0;
        int otherError = 0;

        List<Long> successDurations = new ArrayList<>();

        for (RequestResult r : results) {
            int code = r.statusCode();
            if (code >= 200 && code < 300) {
                success++;
                successDurations.add(r.durationMs());
            } else if (code >= 400 && code < 500) {
                clientError++;
            } else if (code >= 500 && code < 600) {
                serverError++;
            } else {
                otherError++;
            }
        }

        double avg = 0.0;
        double p50 = 0.0;
        double p95 = 0.0;
        long max = 0;

        if (!successDurations.isEmpty()) {
            long sum = 0;
            for (long t : successDurations) sum += t;
            avg = sum / (double) successDurations.size();

            Collections.sort(successDurations);
            p50 = percentile(successDurations, 0.50);
            p95 = percentile(successDurations, 0.95);
            max = successDurations.get(successDurations.size() - 1);
        }

        System.out.printf(
                "%s : total=%4d, ok=%4d, 4xx=%3d, 5xx=%3d, other=%3d, avg=%.2f ms, p50=%.2f ms, p95=%.2f ms, max=%d ms%n",
                label, total, success, clientError, serverError, otherError, avg, p50, p95, max
        );

        // show up to 3 sample errors for debugging
        long errorCount = results.stream().filter(r -> r.error() != null).count();
        if (errorCount > 0) {
            System.out.printf("  Sample errors (%d total):%n", errorCount);
            results.stream()
                    .filter(r -> r.error() != null)
                    .limit(3)
                    .forEach(r ->
                            System.out.printf("    #%d code=%d error=%s%n",
                                    r.index(), r.statusCode(), r.error())
                    );
        }
    }

    private static double percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return Double.NaN;
        if (p <= 0) return sorted.get(0);
        if (p >= 1) return sorted.get(sorted.size() - 1);

        double pos = p * (sorted.size() - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);

        if (lower == upper) {
            return sorted.get(lower);
        }

        double weight = pos - lower;
        return sorted.get(lower) * (1.0 - weight) + sorted.get(upper) * weight;
    }
}
