# 🍺 Beer Client – RestClient (Spring Boot 4)


This module demonstrates a modern synchronous HTTP client built using RestClient (Spring Framework 6+ / Boot 4).
It consumes the beer-store REST API and forwards responses (including RFC 7807 ProblemDetail errors) in a clean and structured way.

## 🚀 Purpose

This project demonstrates:
- Usage of RestClient (modern synchronous HTTP client)	
- Root URI configuration	
- Custom request logging via interceptor	
- Pagination handling with ParameterizedTypeReference	
- Downstream error mapping (404, 409, etc.)	
- RFC 7807 ProblemDetail propagation	
- Clean layered architecture (Controller → Service → HTTP Client)	
- Resilience patterns (CircuitBreaker + Retry via Resilience4j)
- Docker-based service communication

```
beer-client-restclient
│
├── controller/
│   └── BeerController
│
├── service/
│   └── BeerRestClientService
│
├── config/
│   └── RestClientConfig
│
├── dto/
│   ├── BeerCreateRequestDTO
│   ├── BeerUpdateRequestDTO
│   ├── BeerResponseDTO
│   ├── CategoriesDTO
│   └── PagedResponse
│
└── exceptions/
    ├── ResourceAlreadyExistsException
    ├── ResourceNotFoundException
    ├── BeerServiceUnavailableException
    └── GlobalExceptionHandler
```

## 🧩 Technology Stack
- Java 25	
- Spring Boot 4	
- Spring Web MVC	
- RestClient (Spring 6+)	
- Jackson 3 (tools.jackson)	
- RFC 7807 ProblemDetail	
- Resilience4j (CircuitBreaker, Retry, RateLimiter)	
- Docker Compose

## 🏁 Why RestClient?

RestClient is the modern replacement for RestTemplate introduced in Spring Framework 6.

This module demonstrates:
- Fluent API for HTTP calls 
- Better integration with ProblemDetail	
- Cleaner status handling via .retrieve()	
- Type-safe pagination handling	
- Annotation-based resilience (@CircuitBreaker, @Retry)	
- Interceptor-based request logging	
- Container-to-container communication (Docker networking)

## 🛡 Resilience Strategy

This client uses:	
- @Retry for transient failures	
- @CircuitBreaker to prevent cascading failures	
- Fallback methods for graceful degradation	
- Custom exception mapping:	
- 404 → ResourceNotFoundException	
- 409 → ResourceAlreadyExistsException	
- I/O errors → BeerServiceUnavailableException

## 🔮 Next Steps
- beer-client-httpinterface – Declarative HTTP client using @HttpExchange

### 👤 Author

Ing. Martin Baliak
Junior Backend Developer – Spring Boot
