/*
 * Copyright 2020 Vijay Ram
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.circuitbreaker.monitoring.events;


import io.github.resilience4j.common.circuitbreaker.monitoring.endpoint.CircuitBreakerEventsEndpointResponse;
import io.github.resilience4j.service.test.ReactiveDummyService;
import io.github.resilience4j.service.test.TestApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author vijayram
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApplication.class)
public class CircuitBreakerHystrixStreamEventsTest {

    public static final String ACTUATOR_STREAM_CIRCUITBREAKER_EVENTS = "/actuator/hystrix-stream-circuitbreaker-events";
    public static final String ACTUATOR_CIRCUITBREAKEREVENTS = "/actuator/circuitbreakerevents";

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    int randomServerPort;

    @Autowired
    ReactiveDummyService dummyService;

    private WebClient webStreamClient;

    @Before
    public void setup(){
        webStreamClient = WebClient.create("http://localhost:" + randomServerPort);
    }

    private final ParameterizedTypeReference<ServerSentEvent<String>> type
        = new ParameterizedTypeReference<ServerSentEvent<String>>() {
    };

    @Test
    public void streamAllEvents() throws IOException, InterruptedException {
        List<ServerSentEvent<String>> events = getServerSentEvents(ACTUATOR_STREAM_CIRCUITBREAKER_EVENTS);
        CircuitBreakerEventsEndpointResponse circuitBreakerEventsBefore = circuitBreakerEvents(ACTUATOR_CIRCUITBREAKEREVENTS);
        publishEvents();
        CircuitBreakerEventsEndpointResponse circuitBreakerEventsAfter = circuitBreakerEvents(ACTUATOR_CIRCUITBREAKEREVENTS);
        assertThat (circuitBreakerEventsBefore.getCircuitBreakerEvents().size()).isLessThan(circuitBreakerEventsAfter.getCircuitBreakerEvents().size());
        Thread.sleep(1000); // for webClient to complete the subscribe operation
        assertThat (events.size()).isEqualTo(2);
    }


    @Test
    public void streamEventsbyName() throws IOException, InterruptedException {
        List<ServerSentEvent<String>> events = getServerSentEvents(ACTUATOR_STREAM_CIRCUITBREAKER_EVENTS + "/backendB");
        CircuitBreakerEventsEndpointResponse circuitBreakerEventsBefore = circuitBreakerEvents(ACTUATOR_CIRCUITBREAKEREVENTS + "/backendB");
        publishEvents();
        CircuitBreakerEventsEndpointResponse circuitBreakerEventsAfter = circuitBreakerEvents(ACTUATOR_CIRCUITBREAKEREVENTS + "/backendB");
        Thread.sleep(1000); // for webClient to complete the subscribe operation
        assertThat (circuitBreakerEventsBefore.getCircuitBreakerEvents().size()).isLessThan(circuitBreakerEventsAfter.getCircuitBreakerEvents().size());
        assertThat (events.size()).isEqualTo(2);
    }

    @Test
    public void streamEventsbyNameAndType() throws IOException, InterruptedException {
        List<ServerSentEvent<String>> events = getServerSentEvents(ACTUATOR_STREAM_CIRCUITBREAKER_EVENTS+ "/backendB/SUCCESS");
        CircuitBreakerEventsEndpointResponse circuitBreakerEventsBefore = circuitBreakerEvents(ACTUATOR_CIRCUITBREAKEREVENTS + "/backendB");
        publishEvents();
        CircuitBreakerEventsEndpointResponse circuitBreakerEventsAfter = circuitBreakerEvents(ACTUATOR_CIRCUITBREAKEREVENTS + "/backendB");
        Thread.sleep(1000); // for webClient to complete the subscribe operation
        assertThat (circuitBreakerEventsBefore.getCircuitBreakerEvents().size()).isLessThan(circuitBreakerEventsAfter.getCircuitBreakerEvents().size());
        assertThat (events.size()).isEqualTo(1);
    }

    private List<ServerSentEvent<String>> getServerSentEvents(String s) {
        Flux<ServerSentEvent<String>> circuitBreakerStreamEventsForAfter = circuitBreakerStreamEvents(s);
        List<ServerSentEvent<String>> events = new ArrayList<>();

        circuitBreakerStreamEventsForAfter.subscribe(
            content -> events.add(content),
            error -> System.out.println("Error receiving SSE: {}" + error),
            () -> System.out.println("Completed!!!"));
        return events;
    }

    private CircuitBreakerEventsEndpointResponse circuitBreakerEvents(String s) {
        return this.webTestClient.get().uri(s).exchange()
            .expectStatus().isOk()
            .expectBody(CircuitBreakerEventsEndpointResponse.class)
            .returnResult()
            .getResponseBody();
    }

    private Flux<ServerSentEvent<String>> circuitBreakerStreamEvents(String s) {
        Flux<ServerSentEvent<String>> eventStream = webStreamClient.get()
            .uri(s)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(type)
            .take(3);
        return eventStream;
    }

    private void publishEvents() throws IOException {
        try {
            dummyService.doSomethingCompletable(true).blockingGet();
        } catch (Exception ex) {
            // Do nothing. The IOException is recorded by the CircuitBreaker as part of the recordFailurePredicate as a failure.
        }
        //The invocation is recorded by the CircuitBreaker as a success.
        dummyService.doSomethingCompletable(false).blockingGet();
    }
}

