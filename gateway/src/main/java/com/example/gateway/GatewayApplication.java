package com.example.gateway;

import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.hystrix.HystrixCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class GatewayApplication {

		@Bean
		MapReactiveUserDetailsService authentication() {
				UserDetails userDetails = User.withDefaultPasswordEncoder()
					.username("user")
					.password("password")
					.roles("USER")
					.build();
				return new MapReactiveUserDetailsService(userDetails);
		}

		@Bean
		SecurityWebFilterChain authorization(ServerHttpSecurity httpSecurity) {
				//@formatter:off
				return
					httpSecurity
					.csrf().disable()
					.httpBasic()
					.and()
					.authorizeExchange()
								.pathMatchers("/rl").authenticated()
								.anyExchange().permitAll()
					.and()
					.build();
				//@formatter:on
		}

		@Bean
		RedisRateLimiter redisRateLimiter() {
				return new RedisRateLimiter(5, 7);
		}

		@Bean
		RouteLocator gateway(RouteLocatorBuilder rlb) {
				return rlb
					.routes()
					.route(rs ->
						rs
							.host("*.gw.sc").and().path("/rl")
							.filters(fs -> fs
								.setPath("/greetings")
								.requestRateLimiter(config -> config.setRateLimiter(redisRateLimiter())
								)
							)
							.uri("lb://greetings-service")
					)
					.build();
		}

/*
		@Bean
		LoadBalancerExchangeFilterFunction loadBalancerExchangeFilterFunction(
			LoadBalancerClient client) {
				return new LoadBalancerExchangeFilterFunction(client);
		}
*/

		@Bean
		WebClient client(LoadBalancerExchangeFilterFunction exchangeFilterFunction) {
				return WebClient
					.builder()
					.filter(exchangeFilterFunction)
					.build();
		}

		@Bean
		RouterFunction<ServerResponse> routes(WebClient client) {
				return route(GET("/hi"), serverRequest -> {

						ParameterizedTypeReference<Map<String, String>> ptr =
							new ParameterizedTypeReference<Map<String, String>>() {
							};

						Flux<String> greetings = client
							.get()
							.uri("http://greetings-service/greetings")
							.retrieve()
							.bodyToFlux(ptr)
							.map(map -> map.get("value"));

						Publisher<String> circuitBreakerPublisher = HystrixCommands
							.from(greetings)
							.fallback(Flux.just("EEEK"))
							.commandName("greetings")
							.eager()
							.build();

						return ServerResponse.ok().body(circuitBreakerPublisher, String.class);
				});
		}

		public static void main(String[] args) {
				SpringApplication.run(GatewayApplication.class, args);
		}
}
