package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
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
		WebClient client(LoadBalancerExchangeFilterFunction eff) {
				return WebClient
					.builder()
					.filter(eff)
					.build();
		}

		@Bean
		RedisRateLimiter redisRateLimiter() {
				return new RedisRateLimiter(5, 7);
		}

		@Bean
		RouterFunction<ServerResponse> routes(WebClient client) {

				ParameterizedTypeReference<Map<String, String>> ptr =
					new ParameterizedTypeReference<Map<String, String>>() {
					};

				return route(GET("/hello"), request -> {
						Flux<String> greetings = client
							.get()
							.uri("http://greetings-service/hi")
							.retrieve()
							.bodyToFlux(ptr)
							.map(m -> m.get("text"))
							.map(String::toUpperCase);
						return ServerResponse.ok().body(greetings, String.class);
				});
		}

		@Bean
		RouteLocator routeLocator(RouteLocatorBuilder rlb, RedisRateLimiter rl) {
				return
					rlb
						.routes()
						.route(r ->
							r.path("/greetings")
								.filters(fs ->
									fs
										.setPath("/hi")
										.requestRateLimiter(c -> c.setRateLimiter(rl))
								)
								.uri("lb://greetings-service")
						)
						.route(r -> r
							.host("*.gw.sc").and().path("/hi")  // curl -H"Host: bar.gw.sc" http://localhost:8010/hi
							.filters(fs -> fs
								.setPath("/hi")
							)
							.uri("lb://greetings-service")
						)
						.build();
		}


		@Bean
		SecurityWebFilterChain authorization(ServerHttpSecurity http) {
				return http
					.csrf().disable()
					.httpBasic()
					.and()
					.authorizeExchange()
					.pathMatchers("/greetings").authenticated()
					.anyExchange().permitAll()
					.and()
					.build();
		}

		@Bean
		MapReactiveUserDetailsService authentication() {
				UserDetails userDetails = User.withDefaultPasswordEncoder()
					.roles("USER")
					.username("user")
					.password("pw")
					.build();
				return new MapReactiveUserDetailsService(userDetails);
		}

		public static void main(String[] args) {
				SpringApplication.run(GatewayApplication.class, args);
		}
}
