package com.example.greetingsservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class GreetingsServiceApplication {

		@Bean
		RouterFunction<ServerResponse> greetings() {
				return route(GET("/hi"), request -> ServerResponse.ok().body(Flux.just(new Greeting("Hello, world")), Greeting.class));
		}

		public static void main(String[] args) {
				SpringApplication.run(GreetingsServiceApplication.class, args);
		}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Greeting {
		private String text;
}